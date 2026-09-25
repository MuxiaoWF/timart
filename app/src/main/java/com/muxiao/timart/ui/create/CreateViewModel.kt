package com.muxiao.timart.ui.create

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muxiao.timart.utils.RuntimeSettings
import com.muxiao.timart.l10n.currentStrings
import com.muxiao.timart.AppContainer
import com.muxiao.timart.data.local.db.mapper.UnlockRuleJson
import com.muxiao.timart.data.local.crypto.KdfEngines
import com.muxiao.timart.domain.usecase.ShardSecretUseCase
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

    /** 待答之问（N20，可选）：封存后落 meta `capsule.question.<id>`，揭封后作回信引导占位 */
    var question by mutableStateOf("")
        private set

    /** 火漆印章（N19）：样式序号（0 = 无印，1..5），封存后落 meta `capsule.seal.<id>` */
    var sealStyle by mutableIntStateOf(0)
        private set

    fun updateSealStyle(style: Int) {
        sealStyle = style.coerceIn(0, com.muxiao.timart.ui.components.visual.WaxSealStyle.COUNT)
    }

    /** 已选图片字节（封存时才加密落盘；内存暂存上限 9 张） */
    val images = mutableStateListOf<ByteArray>()

    /** 声音留言（体验储备池 §1；封存时加密落 filesDir/audio/，内存暂存单条） */
    var voice by mutableStateOf<ByteArray?>(null)
        private set

    /** 录音时长（秒，仅创建页展示用；播放器运行时读媒体元数据，不持久化） */
    var voiceSeconds by mutableIntStateOf(0)
        private set

    /** 信纸样式（体验储备池 §1；PaperStyle 序号，封存成功后落 meta `capsule.paper.<id>`） */
    var paperStyle by mutableIntStateOf(0)
        private set

    fun addVoice(bytes: ByteArray, seconds: Int) {
        voice = bytes
        voiceSeconds = seconds
    }

    fun removeVoice() {
        voice = null
        voiceSeconds = 0
    }

    fun updatePaperStyle(v: Int) {
        paperStyle = v
    }

    fun updateTitle(v: String) {
        title = v.take(TITLE_MAX)
    }

    fun updateContent(v: String) {
        content = v.take(CONTENT_MAX)
    }

    fun updateNote(v: String) {
        note = v
    }

    fun updateQuestion(v: String) {
        question = v.take(80)
    }

    // ---- 多章节信件（N10）：创建时一次加密入库，meta 只记段落边界 ----

    /** 分章开关（默认关）：开启后第 2/3 章文本框出现在首章下方 */
    var chaptersEnabled by mutableStateOf(false)
        private set

    /** 第 2/3 章草稿（index 0 = 第二章）；首章即 [content] */
    val chapterTexts = mutableStateListOf("", "")

    fun updateChaptersEnabled(v: Boolean) {
        chaptersEnabled = v
        if (!v) chapterTexts.clear()
    }

    /** 追加一章（上限 3 章）；无更多章为 no-op */
    fun addChapter() {
        if (chapterTexts.size < com.muxiao.timart.domain.usecase.ChapterLetter.MAX_CHAPTERS - 1) {
            chapterTexts.add("")
        }
    }

    fun updateChapterText(index: Int, v: String) {
        if (index in chapterTexts.indices) chapterTexts[index] = v.take(CONTENT_MAX)
    }

    /**
     * 分章封存的正文与边界：各章非空段落数（与 ReadCapsuleUseCase.splitParagraphs 同口径），
     * 章文本以 "\n\n" 拼接为单一明文入库。仅 ≥2 个非空章返回 Pair(全文, 边界)（空章直接剔除）。
     */
    private fun chapterAssembly(): Pair<String, List<Int>>? {
        if (!chaptersEnabled) return null
        val chapters = (listOf(content) + chapterTexts.toList())
            .map { text -> text.trim() to text.split("\n\n").map { p -> p.trim() }.count { p -> p.isNotEmpty() } }
            .filter { (text, _) -> text.isNotEmpty() }
        if (chapters.size < 2) return null
        return chapters.joinToString("\n\n") { it.first } to chapters.map { it.second }
    }

    /** 回信转新胶囊（N5）：预填草稿的来源胶囊 id（封存成功落 meta `capsule.replyTo.<newId>`，失败静默） */
    private var replyToSourceId: String? = null

    init {
        // 一次性消费 Detail 页交接的预填草稿（回信转新胶囊；普通入口为 null 不受影响）
        container.pendingCapsulePrefill?.let { prefill ->
            container.pendingCapsulePrefill = null
            title = prefill.title.take(TITLE_MAX)
            content = prefill.body.take(CONTENT_MAX)
            replyToSourceId = prefill.replySourceId
        }
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

    /** 当前气压计海拔快照（RelativeAltitude 条件的创建期基准值；null = 无读数 / 无气压计） */
    fun currentAltitudeMeters(): Double? =
        runCatching { container.compassAltitudeReader.altitudeMeters() }.getOrNull()

    /** 当前连接路由器的 BSSID 快照（SsidBssidMatch 表单一键读取；null = 未连接 / 权限缺失） */
    fun currentWifiBssid(): String? =
        runCatching { container.wifiProvider.current().bssid }.getOrNull()

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

    /** AT_LEAST（M-of-N）的 M 值；仅 logic == AT_LEAST 时随规则写入 */
    var logicThreshold by mutableIntStateOf(2)
        private set

    fun updateLogic(v: LogicType) {
        logic = v
        if (v == LogicType.AT_LEAST) {
            // 切到任选模式：M 默认取条件数的一半（至少 1，且不超过当前条数）
            logicThreshold = (conditions.size / 2).coerceAtLeast(1)
        }
    }

    fun updateThreshold(v: Int) {
        logicThreshold = v.coerceIn(1, conditions.size.coerceAtLeast(1))
    }

    fun addCondition(condition: UnlockCondition) {
        if (conditions.size < CONDITION_MAX) conditions.add(condition)
    }

    fun removeCondition(condition: UnlockCondition) {
        conditions.remove(condition)
    }

    // ---- 沙盘推演（N2）----

    /**
     * 以当前上下文试算草稿规则（纯只读、零副作用）：构造合成 LOCKED 探针胶囊（不落库、无密文），
     * 复用 `defaultContext(foregroundOnly = true)`
     * （用户主动触发且页面在前台，与详情页当场判定同口径）。规则与依赖均为空时返回 null。
     */
    suspend fun dryRun(): com.muxiao.timart.domain.model.JudgeResult? = withContext(Dispatchers.Default) {
        if (conditions.isEmpty() && dependCapsuleId == null) return@withContext null
        val probe = Capsule(
            id = DRY_RUN_ID,
            title = "",
            contentCipher = null,
            createTimestamp = System.currentTimeMillis(),
            unlockRule = currentRule(),
            state = CapsuleState.LOCKED,
            dependCapsuleId = dependCapsuleId,
        )
        runCatching {
            container.unlockJudgeUseCase.judge(
                capsule = probe,
                ctx = container.defaultContext(foregroundOnly = true),
                lang = RuntimeSettings.resolvedLang,
            )
        }.getOrNull()
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

    // ---- 盲盒封存（连自己也保密） ----

    var blindBox by mutableStateOf(false)
        private set

    fun updateBlindBox(v: Boolean) {
        blindBox = v
    }

    // ---- 拼图分组（体验储备池 §3：一封信切 N 片各自封存，全部解锁后合信） ----

    var puzzleGroupId by mutableStateOf<String?>(null)
        private set
    var puzzleIndex by mutableIntStateOf(-1)
        private set
    var puzzleTotal by mutableIntStateOf(-1)
        private set

    /** 加入拼图组（index 由封存侧按已填片数顺延；组内片的合并阅读见 DetailViewModel 合信视图） */
    fun joinPuzzle(groupId: String, index: Int, total: Int) {
        puzzleGroupId = groupId
        puzzleIndex = index
        puzzleTotal = total
    }

    fun leavePuzzle() {
        puzzleGroupId = null
        puzzleIndex = -1
        puzzleTotal = -1
    }

    /** 可加入的拼图组（未满员且不含已销毁片；含销毁片的组已永久无法合信，不再开放） */
    data class PuzzleGroupOption(val groupId: String, val total: Int, val filledCount: Int)

    suspend fun puzzleGroups(): List<PuzzleGroupOption> = withContext(Dispatchers.IO) {
        runCatching {
            val dao = container.database.metaDao()
            val stateById = runCatching { container.capsuleRepository.allSync() }
                .getOrDefault(emptyList())
                .associate { it.id to it.state }
            val members = dao.listLike(com.muxiao.timart.data.local.db.CapsuleMetaKeys.PUZZLE_KEY_PREFIX)
                .mapNotNull { entity ->
                    val id = entity.key.removePrefix(com.muxiao.timart.data.local.db.CapsuleMetaKeys.PUZZLE_KEY_PREFIX)
                    val parsed = com.muxiao.timart.data.local.db.CapsuleMetaKeys.puzzleDecodeValue(entity.value)
                        ?: return@mapNotNull null
                    Triple(id, parsed.first, parsed.second to parsed.third)
                }
            members.groupBy { it.second }.mapNotNull { (groupId, groupMembers) ->
                val total = groupMembers.firstOrNull()?.third?.second ?: return@mapNotNull null
                val indices = groupMembers.map { it.third.first }.toSet()
                val hasDestroyed = groupMembers.any { (id, _, _) ->
                    stateById[id] == CapsuleState.DESTROYED
                }
                val missingRow = groupMembers.any { (id, _, _) -> stateById[id] == null }
                if (hasDestroyed || missingRow) return@mapNotNull null
                if (indices.size < total) {
                    PuzzleGroupOption(groupId, total, indices.size)
                } else {
                    null
                }
            }.sortedBy { it.filledCount }
        }.getOrDefault(emptyList())
    }

    // ---- 嵌套种子（体验储备池 §3：这颗藏进另一颗里，父开启后萌芽出现） ----

    var seedParentId by mutableStateOf<String?>(null)
        private set
    var seedParentTitle by mutableStateOf<String?>(null)
        private set

    fun setSeedParent(id: String?, title: String?) {
        seedParentId = id
        seedParentTitle = title
    }

    /** 可选父胶囊：非销毁、且自身不是未萌芽种子（种子套种子会让「出现时机」不可读） */
    suspend fun seedCandidates(): List<Capsule> = withContext(Dispatchers.IO) {
        runCatching {
            container.capsuleRepository.allSync().filter {
                it.state != CapsuleState.DESTROYED && !container.isSeedDormant(it.id)
            }
        }.getOrDefault(emptyList())
    }

    // ---- 触觉签名 / 环境音（体验储备池 §6；封存时绑定，meta 落 key） ----

    /** 振动纹样序号（0=无 1=双击 2=长振 3=涟漪，语义见 Haptics） */
    var hapticStyle by mutableIntStateOf(0)
        private set

    fun updateHapticStyle(v: Int) {
        hapticStyle = v
    }

    /** 环境音场景名（AmbientSoundPlayer.Scene，OFF = 不绑定） */
    var ambientScene by mutableStateOf(com.muxiao.timart.utils.audio.AmbientSoundPlayer.Scene.OFF.name)
        private set

    fun updateAmbientScene(name: String) {
        ambientScene = name
    }

    // ---- 口令分片（体验储备池 §7.1 落地） ----

    var shardEnabled by mutableStateOf(false)
        private set
    var shardTotal by mutableIntStateOf(3)
        private set
    var shardThreshold by mutableIntStateOf(2)
        private set

    fun updateShardEnabled(v: Boolean) {
        shardEnabled = v
    }

    fun updateShardTotal(v: Int) {
        shardTotal = v.coerceIn(2, ShardSecretUseCase.MAX_TOTAL)
        if (shardThreshold >= shardTotal) shardThreshold = shardTotal - 1
    }

    fun updateShardThreshold(v: Int) {
        shardThreshold = v.coerceIn(2, shardTotal - 1)
    }

    /** 分片只展示一次：封存动画结束后揭示，确认已妥善保存后才清空草稿 */
    private var pendingShares: List<String>? = null
    var showShardShares by mutableStateOf(false)
        private set

    val shardShares: List<String>
        get() = pendingShares.orEmpty()

    /** 生成分片计划并加密内层：S 拆 M-of-N → k2 = KDF(hex(S), salt₂) → verifier₂ → 内层密文。
     *  返回 (计划, 分片串)；分片串由调用方在**入库成功后**才登记（失败重试不得残留旧份额）。 */
    private fun buildShardPlan(): Pair<CapsuleCrudUseCase.ShardPlan, List<String>> {
        val useCase = container.shardSecretUseCase
        val secret = ByteArray(ShardSecretUseCase.SECRET_BYTES).also {
            java.security.SecureRandom().nextBytes(it)
        }
        val shares = useCase.split(secret, shardThreshold, shardTotal)
        val salt2 = KdfEngines.newSalt()
        val params = KdfEngines.defaultParams(salt2)
        // hex(S) 作 KDF 口令：ASCII 形态在 KDF 内部编码下字节稳定，等价于对 S 做字节级 KDF
        val k2 = KdfEngines.byAlgo(params.algo).derive(useCase.secretToPassword(secret), params)
        val verifier2 = useCase.verifierHex(k2)
        val inner = container.aesGcmCipher.encrypt(k2, content.toByteArray(Charsets.UTF_8))
        java.util.Arrays.fill(k2, 0)
        java.util.Arrays.fill(secret, 0)
        val shareStrings = shares.map { useCase.encodeShare(it, shardTotal, shardThreshold) }
        val plan = CapsuleCrudUseCase.ShardPlan(
            saltB64 = KdfEngines.encodeSalt(salt2),
            kdfParamsJson = KdfEngines.paramsToJson(params),
            verifierHex = verifier2,
            threshold = shardThreshold,
            total = shardTotal,
            innerCipher = inner,
        )
        return plan to shareStrings
    }

    // ---- 用户自存场景模板（体验储备池 §7.2：meta 序列化当前条件组合） ----

    @kotlinx.serialization.Serializable
    data class UserTemplateDto(val name: String, val ruleJson: String)

    private val userTemplateJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /** 应用一份规则组合（内置模板与用户模板同路）：清空后按序加入 + 逻辑/阈值 */
    fun applyTemplate(rule: UnlockRule) {
        conditions.clear()
        conditions.addAll(rule.conditionList)
        logic = rule.logicType
        logicThreshold = if (rule.logicType == LogicType.AT_LEAST) {
            (rule.threshold ?: (rule.conditionList.size / 2)).coerceIn(1, rule.conditionList.size.coerceAtLeast(1))
        } else {
            2
        }
    }

    suspend fun userTemplates(): List<UserTemplateDto> = withContext(Dispatchers.IO) {
        runCatching { loadUserTemplates() }.getOrDefault(emptyList())
    }

    /** 保存当前条件组合为模板（同名覆盖；空名 / 无条件静默忽略） */
    fun saveUserTemplate(name: String) {
        val trimmed = name.trim().take(USER_TEMPLATE_NAME_MAX)
        if (trimmed.isEmpty() || conditions.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val ruleJson = UnlockRuleJson.toJson(currentRule())
                val list = loadUserTemplates().toMutableList()
                list.removeAll { it.name == trimmed }
                list.add(0, UserTemplateDto(trimmed, ruleJson))
                persistUserTemplates(list)
            }
        }
    }

    fun deleteUserTemplate(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                persistUserTemplates(loadUserTemplates().filterNot { it.name == name })
            }
        }
    }

    private fun loadUserTemplates(): List<UserTemplateDto> {
        val raw = container.database.metaDao().getSync(USER_TEMPLATES_KEY) ?: return emptyList()
        return runCatching {
            userTemplateJson.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(UserTemplateDto.serializer()),
                raw,
            )
        }.getOrDefault(emptyList())
    }

    private fun persistUserTemplates(list: List<UserTemplateDto>) {
        container.database.metaDao().putSync(
            com.muxiao.timart.data.local.db.entity.MetaEntity(
                USER_TEMPLATES_KEY,
                userTemplateJson.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(UserTemplateDto.serializer()),
                    list,
                ),
            ),
        )
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
            // 分片计划在内层加密前生成：S/k2 用后即清，分片串只在揭示弹窗出现一次
            var shardPlan: CapsuleCrudUseCase.ShardPlan? = null
            var shardShareStrings: List<String> = emptyList()
            if (shardEnabled) {
                val (plan, shareStrings) = buildShardPlan()
                shardPlan = plan
                shardShareStrings = shareStrings
            }
            val draft = CapsuleCrudUseCase.CapsuleDraft(
                title = title.trim(),
                // 多章节信件（N10）：分章时全文 = 各章拼接（一次加密入库），边界另记 meta
                contentText = chapterAssembly()?.first ?: content,
                imageBytes = images.toList(),
                snapshot = snapshot,
                rule = currentRule(),
                tags = tags.toList(),
                note = note,
                dependCapsuleId = dependCapsuleId,
                autoDestroyAfterRead = autoDestroy,
                blindBox = blindBox,
                voiceBytes = voice,
                shardPlan = shardPlan,
            )
            val created = container.capsuleCrudUseCase.create(draft, RuntimeSettings.resolvedLang)
            // 拼图分组 / 嵌套种子 / 信纸样式落 meta（体验储备池 §3、§1；写失败不阻断封存，
            // 代价分别是：本片不在组内合信、本颗不休眠恒可见、解封回落原纸）
            runCatching {
                val dao = container.database.metaDao()
                dao.putSync(
                    com.muxiao.timart.data.local.db.entity.MetaEntity(
                        com.muxiao.timart.data.local.db.CapsuleMetaKeys.paper(created.id),
                        paperStyle.toString(),
                    ),
                )
                if (puzzleGroupId != null && puzzleIndex >= 0 && puzzleTotal >= 2) {
                    dao.putSync(
                        com.muxiao.timart.data.local.db.entity.MetaEntity(
                            com.muxiao.timart.data.local.db.CapsuleMetaKeys.puzzle(created.id),
                            com.muxiao.timart.data.local.db.CapsuleMetaKeys.puzzleValue(
                                puzzleGroupId!!,
                                puzzleIndex,
                                puzzleTotal,
                            ),
                        ),
                    )
                }
                if (seedParentId != null) {
                    dao.putSync(
                        com.muxiao.timart.data.local.db.entity.MetaEntity(
                            com.muxiao.timart.data.local.db.CapsuleMetaKeys.seedOf(created.id),
                            seedParentId!!,
                        ),
                    )
                }
                dao.putSync(
                    com.muxiao.timart.data.local.db.entity.MetaEntity(
                        com.muxiao.timart.data.local.db.CapsuleMetaKeys.haptic(created.id),
                        hapticStyle.toString(),
                    ),
                )
                dao.putSync(
                    com.muxiao.timart.data.local.db.entity.MetaEntity(
                        com.muxiao.timart.data.local.db.CapsuleMetaKeys.ambient(created.id),
                        ambientScene,
                    ),
                )
                // 回信转新胶囊（N5）：记录新胶囊 → 原胶囊指针（契约见 CapsuleMetaKeys.replyTo）
                replyToSourceId?.let { sourceId ->
                    dao.putSync(
                        com.muxiao.timart.data.local.db.entity.MetaEntity(
                            com.muxiao.timart.data.local.db.CapsuleMetaKeys.replyTo(created.id),
                            sourceId,
                        ),
                    )
                }
                // 待答之问（N20）：可选；空值不写键，回信占位回退默认文案
                if (question.isNotBlank()) {
                    dao.putSync(
                        com.muxiao.timart.data.local.db.entity.MetaEntity(
                            com.muxiao.timart.data.local.db.CapsuleMetaKeys.question(created.id),
                            question.trim(),
                        ),
                    )
                }
                // 火漆印章（N19）：0 = 无印不写键，省一次 meta 写入
                if (sealStyle != 0) {
                    dao.putSync(
                        com.muxiao.timart.data.local.db.entity.MetaEntity(
                            com.muxiao.timart.data.local.db.CapsuleMetaKeys.seal(created.id),
                            sealStyle.toString(),
                        ),
                    )
                }
                // 多章节信件（N10）：章节段落边界（仅 ≥2 非空章时写键；揭示进度由阅读侧另记）
                chapterAssembly()?.second
                    ?.let { counts ->
                        com.muxiao.timart.domain.usecase.ChapterLetter.encodeBoundaries(counts)
                    }
                    ?.let { encoded ->
                        dao.putSync(
                            com.muxiao.timart.data.local.db.entity.MetaEntity(
                                com.muxiao.timart.data.local.db.CapsuleMetaKeys.chapters(created.id),
                                encoded,
                            ),
                        )
                    }
            }
            // 入库成功才登记一次性分片串（失败重试不残留）
            if (shardPlan != null) pendingShares = shardShareStrings
            // 规则序列化预览（验收：JSON 正确性走 Log 核对）
            Log.d(TAG, "封存完成 id=${created.id} ruleJson=${ruleJsonPreview()}")
            // 累计创建计数（TotalCreatedCount 条件输入；含后续已删/已毁，写失败不阻断封存）
            runCatching {
                val dao = container.database.metaDao()
                val total = dao.getSync(FLAG_TOTAL_CREATED)?.toIntOrNull() ?: 0
                dao.putSync(
                    com.muxiao.timart.data.local.db.entity.MetaEntity(FLAG_TOTAL_CREATED, (total + 1).toString()),
                )
            }
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
        if (pendingShares != null) {
            // 分片只展示一次：动画结束即揭示，用户确认已保存后才清草稿离开
            showShardShares = true
        } else {
            resetDraft()
        }
    }

    /** 用户确认分片已妥善保存：清空草稿（含一次性分片串）并允许离开 */
    fun consumeShardShares() {
        pendingShares = null
        showShardShares = false
        resetDraft()
    }

    private fun resetDraft() {
        title = ""
        content = ""
        note = ""
        question = ""
        sealStyle = 0
        chaptersEnabled = false
        chapterTexts.clear()
        images.clear()
        voice = null
        voiceSeconds = 0
        paperStyle = 0
        conditions.clear()
        tags.clear()
        snapshot = null
        selectedCity = null
        cityStatus = CityStatus.IDLE
        dependCapsuleId = null
        dependTitle = null
        autoDestroy = false
        blindBox = false
        puzzleGroupId = null
        puzzleIndex = -1
        puzzleTotal = -1
        seedParentId = null
        seedParentTitle = null
        replyToSourceId = null
        hapticStyle = 0
        ambientScene = com.muxiao.timart.utils.audio.AmbientSoundPlayer.Scene.OFF.name
        shardEnabled = false
        shardTotal = 3
        shardThreshold = 2
        pendingShares = null
        showShardShares = false
        logic = LogicType.AND
        logicThreshold = 2
        step.value = 0
    }

    // ---- 内部 ----

    private fun currentRule(): UnlockRule = UnlockRule(
        logicType = logic,
        conditionList = conditions.toList(),
        threshold = if (logic == LogicType.AT_LEAST) {
            logicThreshold.coerceIn(1, conditions.size.coerceAtLeast(1))
        } else {
            null
        },
    )

    /** 规则 JSON 预览（验收日志） */
    fun ruleJsonPreview(): String = runCatching { UnlockRuleJson.toJson(currentRule()) }.getOrDefault("{}")

    companion object {
        private const val TAG = "CreateViewModel"
        private const val DRAFT_ID = "__draft__"

        /** 沙盘推演探针 id（N2；合成胶囊不入库，命名风格与 DRAFT_ID 一致防冲突） */
        private const val DRY_RUN_ID = "__dry_run__"

        /** 累计创建计数 meta key（写入点本类封存成功，读取点 AppContainer.capsuleMetaProvider.totalCreated） */
        const val FLAG_TOTAL_CREATED = "app.created.total"

        /** 用户自存场景模板 meta key（体验储备池 §7.2；JSON 数组 name+ruleJson） */
        const val USER_TEMPLATES_KEY = "app.templates.user"
        const val USER_TEMPLATE_NAME_MAX = 20
        const val TITLE_MAX = 30
        const val CONTENT_MAX = 2000
        const val IMAGE_MAX = 9
        const val TAG_MAX = 5
        const val CONDITION_MAX = 10
    }
}
