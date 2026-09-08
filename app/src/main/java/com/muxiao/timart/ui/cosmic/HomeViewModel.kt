package com.muxiao.timart.ui.cosmic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muxiao.timart.AppContainer
import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.domain.model.unlock.ConditionText
import com.muxiao.timart.l10n.currentStrings
import com.muxiao.timart.utils.RuntimeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 首页时轨 VM（架构 §2.14）：
 * - 订阅 observeAll() Flow → 球体列表与底部预览卡分页（第 1 页「最近的一颗」，
 *   第 2 页「即将达成的一颗」＝最近一颗之外达成进度最接近的锁定胶囊，左右滑动切换）；
 * - 前台判定结果透传：ON_RESUME 时对仍 LOCKED 胶囊跑纯判定（无副作用），
 *   条件新满足 → PENDING 事件；Room 中刚回写 UNLOCKED → UNSEAL 待点击态。
 *   持久化判定与通知由 MainActivity 统一负责，此处不重复。
 */
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.capsuleRepository
    private val judge = container.unlockJudgeUseCase

    /** 全量胶囊（Room Flow 驱动） */
    val capsules: StateFlow<List<Capsule>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _unsealedIds = MutableStateFlow<Set<String>>(emptySet())

    /** UNSEAL 待点击球（刚解锁、尚未进入详情；点击后 consume） */
    val unsealedIds: StateFlow<Set<String>> = _unsealedIds.asStateFlow()

    private val _pendingIds = MutableStateFlow<Set<String>>(emptySet())

    /** 刚有条件新满足的球（首页 PENDING 向心单发） */
    val pendingIds: StateFlow<Set<String>> = _pendingIds.asStateFlow()

    private val _previews = MutableStateFlow<List<LatestPreview>>(emptyList())

    /** 底部预览卡分页数据：[最近的一颗, 即将达成的一颗?]（后者无候选时仅一页） */
    val previews: StateFlow<List<LatestPreview>> = _previews.asStateFlow()

    private val satisfiedCache = HashMap<String, Int>()

    /** 最近胶囊候选缓存：预览刷新不依赖 Room 流（电量/充电等环境量不落库，不会触发 observeAll） */
    @Volatile
    private var latestCandidate: Capsule? = null

    /** 即将达成候选缓存：最近一颗之外的锁定胶囊 */
    @Volatile
    private var upcomingCandidates: List<Capsule> = emptyList()

    private var judging = false

    /** 已开启过（读过内容）的胶囊 id 集：meta `capsule.read.*` 流驱动（key 契约见 DetailViewModel.readMarkKey）。
     *  消费方：底部预览卡「已开启」态 + 时轨画布已读金球余温降档 */
    private val _readIds = MutableStateFlow<Set<String>>(emptySet())
    val readIds: StateFlow<Set<String>> = _readIds.asStateFlow()

    init {
        observeCapsules()
        // 已读标记流：详情页首次阅读写入 → 回首页即时反映到预览卡「已开启」态（不等 30s 周期）
        viewModelScope.launch(Dispatchers.Default) {
            container.database.metaDao().observeReadKeys().collect { keys ->
                _readIds.value = keys.map { it.removePrefix(READ_KEY_PREFIX) }.toSet()
                refreshPreview()
            }
        }
        // 预览卡周期刷新：环境类条件（充电/电量/网络/天气/步数）不写库，
        // Room Flow 感知不到它们的变化，须主动周期重判（与详情页 30s 循环同节奏）
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(PREVIEW_REFRESH_INTERVAL_MS.milliseconds)
                refreshPreview()
            }
        }
    }

    private fun observeCapsules() {
        viewModelScope.launch(Dispatchers.Default) {
            capsules.collect { list ->
                // UNSEAL 待点击：unlockTimestamp 落在近窗内（判定刚回写）
                val now = System.currentTimeMillis()
                val fresh = list
                    .filter { it.state == CapsuleState.UNLOCKED && it.unlockTimestamp != null }
                    .filter { now - it.unlockTimestamp!! < FRESH_UNLOCK_WINDOW_MS }
                    .map { it.id }
                    .toSet()
                _unsealedIds.update { current -> (current + fresh).filter { id -> list.any { it.id == id } }.toSet() }

                // 底部预览卡：最近一颗未销毁胶囊 + 最近一颗之外的锁定胶囊（即将达成候选）
                val alive = list.filter { it.state != CapsuleState.DESTROYED }
                latestCandidate = alive.maxByOrNull { it.createTimestamp }
                upcomingCandidates = alive
                    .filter { it.state == CapsuleState.LOCKED && it.id != latestCandidate?.id }
                refreshPreview()
            }
        }
    }

    /** 依据缓存候选重建预览卡分页（判定是同步纯逻辑，可任意时机重跑） */
    private fun refreshPreview() {
        val latest = latestCandidate ?: run {
            _previews.value = emptyList()
            return
        }
        val pages = mutableListOf(buildPreview(latest, LatestPreview.PreviewKind.LATEST))
        // 「即将达成的一颗」：达成进度（满足条件占比）最接近的一颗；
        // 环境类条件随 30s 周期重判，进度会自动演进
        val ctx = container.defaultContext(foregroundOnly = false)
        val closest = upcomingCandidates
            .map { capsule -> capsule to judge.judge(capsule, ctx, RuntimeSettings.resolvedLang) }
            .filter { (_, result) -> result.items.isNotEmpty() }
            .maxByOrNull { (_, result) ->
                val satisfied = result.items.count { item -> item.satisfied }
                satisfied.toFloat() / result.items.size
            }
        closest?.let { (capsule, _) -> pages += buildPreview(capsule, LatestPreview.PreviewKind.UPCOMING) }
        _previews.value = pages
    }

    private fun buildPreview(capsule: Capsule, kind: LatestPreview.PreviewKind): LatestPreview {
        val sentence: String?
        var satisfied: Int? = null
        var total: Int? = null
        if (capsule.state == CapsuleState.LOCKED) {
            val result = judge.judge(capsule, container.defaultContext(foregroundOnly = false), RuntimeSettings.resolvedLang)
            satisfied = result.items.count { it.satisfied }
            total = result.items.size
            // 条件句展示**第一个未满足**的条件（已满足的没有提示价值）；全部满足时兜底第一条
            sentence = (result.items.firstOrNull { !it.satisfied } ?: result.items.firstOrNull())
                ?.let { currentStrings().readyWhenFmt.format(ConditionText.conditionSentence(it.condition, RuntimeSettings.resolvedLang)) }
        } else {
            sentence = null
        }
        return LatestPreview(
            id = capsule.id,
            title = capsule.title,
            sentence = sentence,
            satisfied = satisfied,
            total = total,
            unlocked = capsule.state == CapsuleState.UNLOCKED,
            read = capsule.id in _readIds.value,
            kind = kind,
        )
    }

    /**
     * 首页 ON_RESUME：对仍 LOCKED 的胶囊跑同步纯判定（Default 协程，无副作用）。
     * 条件新满足 → PENDING 事件集合；不持久化（MainActivity 的判定链负责回写）。
     */
    fun onScreenResumed() {
        if (judging) return
        judging = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val ctx = container.defaultContext(foregroundOnly = false)
                for (capsule in repo.allLockedSync()) {
                    val result = judge.judge(capsule, ctx, RuntimeSettings.resolvedLang)
                    val satisfied = result.items.count { it.satisfied }
                    val prev = satisfiedCache[capsule.id]
                    if (prev != null && satisfied > prev && !result.overallOk) {
                        _pendingIds.update { it + capsule.id }
                    }
                    satisfiedCache[capsule.id] = satisfied
                }
                // 回前台即时刷新预览卡（不等 30s 周期）
                refreshPreview()
            } finally {
                judging = false
            }
        }
    }

    /** 点击 UNSEAL 球进入详情后消费事件 */
    fun consumeUnsealed(id: String) {
        _unsealedIds.update { it - id }
        _pendingIds.update { it - id }
    }

    /** 持久化自定义星图坐标（长按拖拽松手；仅视觉，不改变时间数据） */
    fun saveLayout(id: String, layoutX: Float, layoutY: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repo.updateLayout(id, layoutX, layoutY) }
        }
    }

    /** 底部预览卡数据 */
    data class LatestPreview(
        val id: String,
        val title: String,
        val sentence: String?,
        val satisfied: Int?,
        val total: Int?,
        val unlocked: Boolean,
        /** 已开启过（读过内容，meta `capsule.read.*` 存在）：与「刚解锁未读」区分 */
        val read: Boolean,
        val kind: PreviewKind,
    ) {
        /** 预览卡种类：最近的一颗 / 即将达成的一颗 */
        enum class PreviewKind { LATEST, UPCOMING }
    }

    companion object {
        /** 已读 meta key 前缀（与 DetailViewModel.readMarkKey 的 `capsule.read.<id>` 契约同步） */
        private const val READ_KEY_PREFIX = "capsule.read."

        /** 解锁判定回写后视为"新解锁"的时间窗 */
        private const val FRESH_UNLOCK_WINDOW_MS = 15_000L

        /** 预览卡周期刷新间隔（环境类条件不落库，Room Flow 感知不到；与详情页重判节奏一致） */
        private const val PREVIEW_REFRESH_INTERVAL_MS = 30_000L
    }
}
