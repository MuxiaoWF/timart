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
 * - 订阅 observeAll() Flow → 球体列表与底部「最近的一颗」预览；
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

    private val _latest = MutableStateFlow<LatestPreview?>(null)

    /** 底部「最近的一颗」预览卡数据 */
    val latest: StateFlow<LatestPreview?> = _latest.asStateFlow()

    private val satisfiedCache = HashMap<String, Int>()

    /** 最近胶囊候选缓存：预览刷新不依赖 Room 流（电量/充电等环境量不落库，不会触发 observeAll） */
    @Volatile
    private var latestCandidate: Capsule? = null

    private var judging = false

    init {
        observeCapsules()
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

                // 底部预览卡：最近一颗未销毁胶囊
                latestCandidate = list
                    .filter { it.state != CapsuleState.DESTROYED }
                    .maxByOrNull { it.createTimestamp }
                refreshPreview()
            }
        }
    }

    /** 依据缓存的最近胶囊候选重建预览卡（判定是同步纯逻辑，可任意时机重跑） */
    private fun refreshPreview() {
        _latest.value = latestCandidate?.let { buildPreview(it) }
    }

    private fun buildPreview(capsule: Capsule): LatestPreview {
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
    )

    companion object {
        /** 解锁判定回写后视为"新解锁"的时间窗 */
        private const val FRESH_UNLOCK_WINDOW_MS = 15_000L

        /** 预览卡周期刷新间隔（环境类条件不落库，Room Flow 感知不到；与详情页重判节奏一致） */
        private const val PREVIEW_REFRESH_INTERVAL_MS = 30_000L
    }
}
