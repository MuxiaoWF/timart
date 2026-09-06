package com.muxiao.timart.ui.create

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muxiao.timart.utils.RuntimeSettings
import com.muxiao.timart.l10n.currentStrings
import com.muxiao.timart.AppContainer
import com.muxiao.timart.data.local.db.mapper.UnlockRuleJson
import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.domain.model.City
import com.muxiao.timart.domain.model.WeatherSnapshot
import com.muxiao.timart.domain.model.nearestTo
import com.muxiao.timart.domain.model.unlock.LogicType
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.domain.model.unlock.UnlockRule
import com.muxiao.timart.domain.usecase.CapsuleCrudUseCase
import com.muxiao.timart.domain.usecase.DependencyError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 三步封存草稿 VM（架构 §2.15）：三步共享状态 + 城市天气拉取 + 依赖校验 + 封存提交。
 * 草稿字段用 Compose mutableStateOf（编辑高频）；IO 操作全部派发 IO 协程。
 */
class CreateViewModel(private val container: AppContainer) : ViewModel() {

    // ---- 步骤：0 写下 / 1 规则 / 2 封存 ----

    val step = MutableStateFlow(0)

    fun next() {
        if (step.value < 2) step.value += 1
    }

    fun back() {
        if (step.value > 0) step.value -= 1
    }

    // ---- 写下（信笺草稿） ----

    var title by mutableStateOf("")
        private set
    var content by mutableStateOf("")
        private set
    var note by mutableStateOf("")
        private set

    /** 已选图片字节（封存时才加密落盘；内存暂存上限 9 张） */
    val images = mutableStateListOf<ByteArray>()

    fun updateTitle(v: String) {
        title = v.take(TITLE_MAX)
    }

    fun updateContent(v: String) {
        content = v.take(CONTENT_MAX)
    }

    fun updateNote(v: String) {
        note = v
    }

    fun addImages(bytes: List<ByteArray>) {
        bytes.forEach { b -> if (images.size < IMAGE_MAX) images.add(b) }
    }

    /**
     * PhotoPicker / ACTION_OPEN_DOCUMENT 回调入口：
     * IO 协程逐张读取 Uri 字节（容量不足自动截断；单张失败静默跳过）。
     */
    fun addImagesFromUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val room = (IMAGE_MAX - images.size).coerceAtLeast(0)
            if (room == 0) return@launch
            val bytes = uris.take(room).mapNotNull { uri ->
                runCatching { container.readImageBytes(uri) }.getOrNull()
            }
            withContext(Dispatchers.Main) { addImages(bytes) }
        }
    }

    fun removeImage(index: Int) {
        if (index in images.indices) images.removeAt(index)
    }

    // ---- 城市与天气快照 ----

    enum class CityStatus { IDLE, LOADING, DONE, FAILED }

    var selectedCity by mutableStateOf<City?>(null)
        private set
    var snapshot by mutableStateOf<WeatherSnapshot?>(null)
        private set
    var cityStatus by mutableStateOf(CityStatus.IDLE)
        private set

    /** 城市码表冷加载预热（首次进入创建页时） */
    fun preloadCities() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { container.cityRepository.all() }
        }
    }

    /** 城市 / 省份模糊搜索（码表常驻内存后为纯内存查询） */
    fun searchCities(keyword: String): List<City> = runCatching {
        if (keyword.isBlank()) container.cityRepository.all() else container.cityRepository.search(keyword.trim())
    }.getOrDefault(emptyList())

    /** 上次使用城市（默认选中） */
    fun lastUsedCity(): City? = runCatching { container.cityRepository.lastUsed() }.getOrNull()

    /**
     * 定位附近城市（被动定位：仅在位置权限已授予时读取最近一次定位，纯本地 haversine 计算，
     * 不发起权限请求、不联网、不存储位置）。未授权或无定位返回 null（选择器按原列表展示）。
     */
    /** 位置权限是否已授予（城市选择器据此决定展示「使用当前位置」引导条还是附近分区） */
    fun isLocationPermitted(): Boolean = runCatching {
        container.locationProvider(foregroundOnly = false).isPermitted()
    }.getOrDefault(false)

    fun nearbyCities(maxCount: Int = 3): List<City>? = runCatching {
        val provider = container.locationProvider(foregroundOnly = false)
        if (!provider.isPermitted()) return@runCatching null
        val point = provider.lastKnown() ?: return@runCatching null
        container.cityRepository.all().nearestTo(point.lat, point.lng, maxCount)
    }.getOrNull()

    /** 选定即强制拉快照（绕 30min 缓存）；失败可跳过 */
    fun selectCity(city: City) {
        selectedCity = city
        cityStatus = CityStatus.LOADING
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { container.cityRepository.rememberLastUsed(city.id) }
            val snap = runCatching { container.weatherRepositoryImpl.forceSnapshot(city.id) }.getOrNull()
            withContext(Dispatchers.Main) {
                snapshot = snap
                cityStatus = if (snap != null) CityStatus.DONE else CityStatus.FAILED
            }
        }
    }

    /** 快照拉取失败：跳过（存空快照） */
    fun skipSnapshot() {
        cityStatus = CityStatus.DONE
    }

    fun retrySnapshot() {
        selectedCity?.let { selectCity(it) }
    }

    // ---- 规则构建 ----

    val conditions = mutableStateListOf<UnlockCondition>()

    var logic by mutableStateOf(LogicType.AND)
        private set

    fun updateLogic(v: LogicType) {
        logic = v
    }

    fun addCondition(condition: UnlockCondition) {
        if (conditions.size < CONDITION_MAX) conditions.add(condition)
    }

    fun removeCondition(condition: UnlockCondition) {
        conditions.remove(condition)
    }

    // ---- 依赖另一颗胶囊 ----

    var dependCapsuleId by mutableStateOf<String?>(null)
        private set
    var dependTitle by mutableStateOf<String?>(null)
        private set

    /** 候选：全部 LOCKED 胶囊（草稿自身尚未入库，天然不在列） */
    suspend fun lockedCandidates(): List<Capsule> = withContext(Dispatchers.IO) {
        runCatching { container.capsuleRepository.allLockedSync() }.getOrDefault(emptyList())
    }

    /** 候选：全部胶囊（「某颗已解锁/已销毁」联动条件选择器用） */
    suspend fun allCapsules(): List<Capsule> = withContext(Dispatchers.IO) {
        runCatching { container.capsuleRepository.allSync() }.getOrDefault(emptyList())
    }

    /**
     * 依赖校验（DependencyGraphUseCase：环 / 死链 / 自依赖 / 自动销毁强警告）。
     * 草稿以虚拟 id 参与校验（"__draft__" 不会与其他真实 id 冲突）。
     */
    suspend fun validateDependency(targetId: String): DependencyError? = withContext(Dispatchers.IO) {
        val all = runCatching { container.capsuleRepository.allSync() }.getOrDefault(emptyList())
        val self = Capsule(
            id = DRAFT_ID,
            title = title.ifBlank { currentStrings().untitled },
            contentCipher = null,
            createTimestamp = System.currentTimeMillis(),
            unlockRule = currentRule(),
            state = CapsuleState.LOCKED,
            autoDestroyAfterRead = autoDestroy,
            dependCapsuleId = targetId,
        )
        container.dependencyGraphUseCase.validateCandidate(self, all + self, RuntimeSettings.resolvedLang)
    }

    fun setDependency(id: String?, title: String?) {
        dependCapsuleId = id
        dependTitle = title
    }

    // ---- 自动销毁 ----

    var autoDestroy by mutableStateOf(false)
        private set

    fun updateAutoDestroy(v: Boolean) {
        autoDestroy = v
    }

    // ---- 标签（1–5） ----

    val tags = mutableStateListOf<String>()

    fun addTag(raw: String) {
        val v = raw.trim()
        if (v.isNotEmpty() && v !in tags && tags.size < TAG_MAX) tags.add(v)
    }

    fun removeTag(tag: String) {
        tags.remove(tag)
    }

    // ---- 封存提交 ----

    private val _assembling = MutableStateFlow(false)

    /** ASSEMBLE 播放中（全屏 overlay 锁编辑） */
    val assembling: StateFlow<Boolean> = _assembling.asStateFlow()

    private var pendingAfterPassword = false
    private var lastOnFailed: ((String) -> Unit)? = null

    /**
     * 提交封存：无口令 → 导航 PASSWORD_SETUP（回来后 onPasswordReady 续跑）；
     * 有口令 → IO 落库，成功进入 ASSEMBLE 动画；失败回调 onFailed（保留草稿）。
     */
    fun submit(onNeedPasswordSetup: () -> Unit, onFailed: (String) -> Unit) {
        lastOnFailed = onFailed
        viewModelScope.launch(Dispatchers.IO) {
            val hasPassword = runCatching { container.contentCryptoManager.hasPasswordSetup() }.getOrDefault(false)
            if (!hasPassword) {
                pendingAfterPassword = true
                withContext(Dispatchers.Main) { onNeedPasswordSetup() }
                return@launch
            }
            performCreate()
        }
    }

    /** PASSWORD_SETUP 成功返回后由 UI 调用，续跑被挂起的封存 */
    fun onPasswordReady() {
        if (!pendingAfterPassword) return
        pendingAfterPassword = false
        viewModelScope.launch(Dispatchers.IO) { performCreate() }
    }

    private suspend fun performCreate() {
        try {
            if (!container.contentCryptoManager.ensureUnlocked()) {
                throw IllegalStateException(currentStrings().errPwNotUnlockedSeal)
            }
            val draft = CapsuleCrudUseCase.CapsuleDraft(
                title = title.trim(),
                contentText = content,
                imageBytes = images.toList(),
                snapshot = snapshot,
                rule = currentRule(),
                tags = tags.toList(),
                note = note,
                dependCapsuleId = dependCapsuleId,
                autoDestroyAfterRead = autoDestroy,
            )
            val created = container.capsuleCrudUseCase.create(draft, RuntimeSettings.resolvedLang)
            // 规则序列化预览（验收：JSON 正确性走 Log 核对）
            Log.d(TAG, "封存完成 id=${created.id} ruleJson=${ruleJsonPreview()}")
            sealError = null
            withContext(Dispatchers.Main) { _assembling.value = true }
        } catch (e: Exception) {
            val message = e.message ?: currentStrings().errSealFailed
            withContext(Dispatchers.Main) {
                lastOnFailed?.invoke(message)
            }
        }
    }

    private var sealError: String? = null

    /** ASSEMBLE 动画完成（或 1.5s 兜底）后：复位草稿与步骤，由 UI 导航回时轨 */
    fun onAssembleFinished() {
        _assembling.value = false
        resetDraft()
    }

    private fun resetDraft() {
        title = ""
        content = ""
        note = ""
        images.clear()
        conditions.clear()
        tags.clear()
        snapshot = null
        selectedCity = null
        cityStatus = CityStatus.IDLE
        dependCapsuleId = null
        dependTitle = null
        autoDestroy = false
        step.value = 0
    }

    // ---- 内部 ----

    private fun currentRule(): UnlockRule = UnlockRule(logicType = logic, conditionList = conditions.toList())

    /** 规则 JSON 预览（验收日志） */
    fun ruleJsonPreview(): String = runCatching { UnlockRuleJson.toJson(currentRule()) }.getOrDefault("{}")

    companion object {
        private const val TAG = "CreateViewModel"
        private const val DRAFT_ID = "__draft__"
        const val TITLE_MAX = 30
        const val CONTENT_MAX = 2000
        const val IMAGE_MAX = 9
        const val TAG_MAX = 5
        const val CONDITION_MAX = 10
    }
}
