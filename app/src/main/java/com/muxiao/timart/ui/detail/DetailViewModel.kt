package com.muxiao.timart.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muxiao.timart.utils.RuntimeSettings
import com.muxiao.timart.l10n.currentStrings
import com.muxiao.timart.AppContainer
import com.muxiao.timart.data.local.crypto.CryptoException
import com.muxiao.timart.domain.context.DependencyStatus
import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.domain.model.ConditionStatus
import com.muxiao.timart.domain.usecase.ReadCapsuleUseCase
import com.muxiao.timart.domain.usecase.ShardSecretUseCase
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 详情页四状态 VM（架构 §2.16 / PRD §2.3、§3.4.4）：
 *
 * - 订阅 observeById：Room 是唯一状态事实源，页面 phase 由胶囊状态 + 阅读进程推导；
 * - LOCKED 态 30s 周期重判 + 进页立即一次（前台上下文，含 GPS）；
 *   条件满足差集 → pendingPulse 计数（UI 局部向心尘流），全部满足 → 回写 UNLOCKED + 通知；
 * - UNSEAL / 450ms 简化重读是纯视觉层：**业务（内容解密、销毁）永不等待动画回调**；
 * - autoDestroy 阅读后销毁：唯一主动作 + 二次确认 + 返回「销毁/保留」，无静默销毁路径。
 */
class DetailViewModel(
    private val container: AppContainer,
    private val capsuleId: String,
    private val firstUnlock: Boolean,
) : ViewModel() {

    /** 详情页相位（业务状态机；UNSEAL/DISSOLVE 仅是动画承载相位，跳过路径随时可落终态） */
    enum class Phase { LOADING, LOCKED, UNSEAL, CONTENT, DISSOLVE, ARCHIVED, MISSING }

    /** 条件时间线 UI 数据（LOCKED 态） */
    data class TimelineUi(
        val items: List<ConditionStatus>,
        val satisfiedCount: Int,
        val totalCount: Int,
        val dependencyTitle: String?,
        val dependencyStatus: DependencyStatus?,
        val dependencyReason: String?,

        /** 依赖胶囊已销毁 → 本胶囊永久无法解锁（明示） */
        val dependencyUnreachable: Boolean,

        /** 今日步数（判定链路同一 stepProvider 读取；无计步硬件/无权限/首帧未读到 = null） */
        val stepProgress: Int? = null,
    )

    data class UiState(
        val phase: Phase = Phase.LOADING,
        val timeline: TimelineUi? = null,
        val content: ReadCapsuleUseCase.CapsuleContent? = null,
        val capsuleTitle: String? = null,

        /** 当前胶囊领域模型（autoDestroy / 天气 / 标签等元信息读取） */
        val capsule: Capsule? = null,
        val unlockedAt: Long? = null,
        val destroyedAt: Long? = null,

        /** 冷启动会话未解锁：进内容前先要口令 */
        val needPassword: Boolean = false,
        val destroyConfirmVisible: Boolean = false,
        val backConfirmVisible: Boolean = false,

        /** 用户已选「保留」，返回不再弹「销毁/保留」 */
        val keepAfterRead: Boolean = false,

        /** 条件满足差集脉冲计数（UI 据此单发 PENDING 向心尘流） */
        val pendingPulse: Int = 0,
        val errorText: String? = null,

        /** 会话内已完成的现场挑战 id（来源 = 每轮判定结果时间线，挑战卡据此显示完成态） */
        val satisfiedChallenges: Set<String> = emptySet(),

        /** 「后悔药」：条件修改机会可用（meta 一次性标记未消耗） */
        val regretAvailable: Boolean = false,
        val showRegretSheet: Boolean = false,

        /**
         * 本次进入阅读是否已播完整揭封（UNSEAL 相位）。
         * true → 落 CONTENT 时文字直接是完成态（揭封信笺已把完整正文显示过，
         * 若再跑一次 reveal 会出现"文字完整 → 消失 → 重敲"的倒退观感）。
         */
        val playedFullUnseal: Boolean = false,

        /** 信纸样式（体验储备池 §1；meta `capsule.paper.<id>`，0 = 原纸，解封信笺按此呈现） */
        val paperStyle: Int = 0,

        /** 声音留言（体验储备池 §1）：有无语音 / 播放态（播放走 VoicePlayer，明文只进 cache 临时文件） */
        val voiceAvailable: Boolean = false,
        val voicePlaying: Boolean = false,

        /** 回信（体验储备池 §5）：已写的回信文本（null = 未写过） */
        val reply: String? = null,
        val showReplyDialog: Boolean = false,

        /** 待答之问（N20）：封存时写下的问题文本；null = 未写，回信占位用默认文案 */
        val question: String? = null,

        /** 火漆印章（N19）：样式序号（0 = 无印），信笺落款处呈现 */
        val sealStyle: Int = 0,

        /** 拼图分组（体验储备池 §3）：本片序号（0 起）/ 总片数 / 已解锁片数；未分组 = null */
        val puzzleIndex: Int? = null,
        val puzzleTotal: Int? = null,
        val puzzleUnlockedCount: Int? = null,

        /** 合信视图（全部片解锁后可开）：fragments 非空 = 已解密就绪 */
        val puzzleReady: Boolean = false,
        val showPuzzleSheet: Boolean = false,
        val puzzleFragments: List<PuzzleFragment>? = null,

        /** 口令分片（体验储备池 §7.1）：外层已解、内层仍锁 → 显示集分片面板 */
        val shardGate: Boolean = false,

        /** 临近解锁提醒（N1）：提前量天数（null = 关闭）；仅对确定性时间条件生效 */
        val remindLeadDays: Int? = null,

        /** 多章节信件（N10）：下一章提示（可揭示 = 引导句 / 未到期 = 「N 天后可读」；null = 单章信或无后续章） */
        val chapterHint: String? = null,

        /** 多章节信件（N10）：下一章当前可揭示（非空 [chapterHint] 时决定按钮 vs 纯文本） */
        val canRevealNextChapter: Boolean = false,
    )

    /** 多章节信件 VM 内存态：段落边界 + 揭示进度（进度持久化在 meta，见 CapsuleMetaKeys.chapters 注） */
    private class ChapterInfo(
        val boundaries: List<Int>,
        var revealed: Int,
        var revealedAt: Long?,
    )

    /** 合信视图分片内容（全部 UNLOCKED 后按序解密聚合） */
    data class PuzzleFragment(
        val capsuleId: String,
        val index: Int,
        val title: String,
        val paragraphs: List<String>,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val repo = container.capsuleRepository
    private val judge = container.unlockJudgeUseCase
    private val crud = container.capsuleCrudUseCase
    private val reader = container.readCapsuleUseCase
    private val crypto = container.contentCryptoManager
    private val destroyedRepo = container.destroyedRepository

    /** 本次会话内是否见过 LOCKED 态（解锁发生在眼前 → 播完整 UNSEAL） */
    private var sawLocked = false

    /** 阅读标记检查结果：false = 本胶囊从未被打开阅读（UNLOCKED 后首读 → 播完整 UNSEAL） */
    private var readMarkChecked = false
    private var readMarkExists = true

    private var judgeJob: Job? = null

    /**
     * 本次详情页会话内已提交的现场挑战应答（challengeId → 应答串）。
     * 挑战是用户当场完成的一次性事实，会话内永久有效：
     * - 多挑战逐个提交时必须累积，否则后一挑战复判会丢失前一应答（AND 规则下永远差一口气）；
     * - 30s 周期重判也必须携带，否则已完成挑战会被周期快照打回「待完成」。
     * Worker / ON_RESUME 全量判定不走此处，fail-closed 语义不变。
     * 主线程提交写入与 Default 协程周期读取并发 → 用 ConcurrentHashMap。
     */
    private val sessionChallengeAnswers = java.util.concurrent.ConcurrentHashMap<String, String>()

    private var latestCapsule: Capsule? = null

    /**
     * 本会话已确认落库/尝试落库的条件达成键（`capsuleId:index`）。
     * judgeOnce 可能被 30s 周期与挑战提交并发触发，用并发集合去重避免反复读 meta。
     * 契约见 CapsuleMetaKeys（另一写入点 MainActivity 全量判定，读取点 DustRecordsViewModel）。
     */
    private val condMetRecorded = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * 分片胶囊的内层密文（体验储备池 §7.1）：外层解密产物，仅存 VM 内存，
     * 解出明文 / 离开页面即清。设备任何持久存储都不保留内层密文副本。
     */
    @Volatile
    private var pendingInnerCipher: ByteArray? = null

    /** 多章节信件（N10）：章节边界与揭示进度（null = 单章信 / 边界读取失败） */
    @Volatile
    private var chapterInfo: ChapterInfo? = null

    init {
        viewModelScope.launch(Dispatchers.Default) {
            repo.observeById(capsuleId).collect { onCapsule(it) }
        }
        // 后悔药可用性：读 meta 一次性标记（读失败按已消耗处理，绝不误开隐藏入口）
        viewModelScope.launch(Dispatchers.IO) {
            val used = runCatching {
                container.database.metaDao().get(condEditKey()) == "true"
            }.getOrDefault(true)
            _state.update { it.copy(regretAvailable = !used) }
        }
        // 凝视计数：每次进入详情页（含锁定态）计一次（meta `capsule.views.<id>`，
        // 与 read/condEdit 同属跨 VM meta 契约，判定侧 AppContainer.capsuleMetaProvider.viewCount）
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val dao = container.database.metaDao()
                val current = dao.get(viewCountKey())?.toIntOrNull() ?: 0
                dao.put(com.muxiao.timart.data.local.db.entity.MetaEntity(viewCountKey(), (current + 1).toString()))
            }
        }
        // 信纸样式（体验储备池 §1）：meta `capsule.paper.<id>`（写入点 CreateViewModel.performCreate）；
        // 读失败按原纸（0），fail-closed 不影响阅读
        viewModelScope.launch(Dispatchers.IO) {
            val style = runCatching {
                container.database.metaDao()
                    .get(com.muxiao.timart.data.local.db.CapsuleMetaKeys.paper(capsuleId))
                    ?.toIntOrNull() ?: 0
            }.getOrDefault(0)
            _state.update { it.copy(paperStyle = style) }
        }
        // 回信（体验储备池 §5）：meta `capsule.reply.<id>`（写入点本类 saveReply）
        viewModelScope.launch(Dispatchers.IO) {
            val saved = runCatching {
                container.database.metaDao()
                    .get(com.muxiao.timart.data.local.db.CapsuleMetaKeys.reply(capsuleId))
            }.getOrNull()
            if (saved != null) _state.update { it.copy(reply = saved) }
        }
        // 待答之问（N20）：meta `capsule.question.<id>`（写入点 CreateViewModel.performCreate）
        viewModelScope.launch(Dispatchers.IO) {
            val asked = runCatching {
                container.database.metaDao()
                    .get(com.muxiao.timart.data.local.db.CapsuleMetaKeys.question(capsuleId))
            }.getOrNull()
            if (asked != null) _state.update { it.copy(question = asked) }
        }
        // 火漆印章（N19）：meta `capsule.seal.<id>`（写入点 CreateViewModel.performCreate）
        viewModelScope.launch(Dispatchers.IO) {
            val seal = runCatching {
                container.database.metaDao()
                    .get(com.muxiao.timart.data.local.db.CapsuleMetaKeys.seal(capsuleId))
            }.getOrNull()?.toIntOrNull() ?: 0
            if (seal != 0) _state.update { it.copy(sealStyle = seal) }
        }
        // 临近提醒提前量（N1）：meta `settings.remind.<id>`（写入点本类 setRemindLeadDays）
        viewModelScope.launch(Dispatchers.IO) {
            val lead = runCatching {
                container.database.metaDao()
                    .get(com.muxiao.timart.data.local.db.CapsuleMetaKeys.remind(capsuleId))
                    ?.toIntOrNull()
            }.getOrNull()
            if (lead != null) _state.update { it.copy(remindLeadDays = lead.coerceIn(1, 30)) }
        }
        // 多章节信件（N10）：边界 + 揭示进度（首揭 = 第 1 章，此刻落库；读失败按单章信处理）
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val dao = container.database.metaDao()
                val boundaries = com.muxiao.timart.domain.usecase.ChapterLetter.parseBoundaries(
                    dao.get(com.muxiao.timart.data.local.db.CapsuleMetaKeys.chapters(capsuleId)),
                ) ?: return@runCatching
                val now = container.timeProvider.nowMillis()
                var revealed = dao.get(com.muxiao.timart.data.local.db.CapsuleMetaKeys.chapterRevealed(capsuleId))
                    ?.toIntOrNull() ?: 0
                var revealedAt = dao.get(com.muxiao.timart.data.local.db.CapsuleMetaKeys.chapterRevealedAt(capsuleId))
                    ?.toLongOrNull()
                if (revealed < 1) {
                    revealed = 1
                    revealedAt = now
                    dao.put(
                        com.muxiao.timart.data.local.db.entity.MetaEntity(
                            com.muxiao.timart.data.local.db.CapsuleMetaKeys.chapterRevealed(capsuleId),
                            "1",
                        ),
                    )
                    dao.put(
                        com.muxiao.timart.data.local.db.entity.MetaEntity(
                            com.muxiao.timart.data.local.db.CapsuleMetaKeys.chapterRevealedAt(capsuleId),
                            now.toString(),
                        ),
                    )
                }
                chapterInfo = ChapterInfo(boundaries, revealed, revealedAt)
                applyChapterFilter()
            }
        }
        // 拼图分组（体验储备池 §3）：本片归属 + 各片解锁进度（读失败按未分组处理）
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { loadPuzzleState() }
        }
        // 声音留言可用性（体验储备池 §1）：audio 目录在位即有语音（不落 Room，见 AudioCipherStore）
        viewModelScope.launch(Dispatchers.IO) {
            val available = runCatching {
                container.audioCipherStore.listIndexes(capsuleId).isNotEmpty()
            }.getOrDefault(false)
            _state.update { it.copy(voiceAvailable = available) }
        }
        // 凝视时长累计：详情页存活期间每 5 秒 +5（meta `capsule.watch.<id>`，
        // 判定侧 AppContainer.capsuleMetaProvider.watchSeconds；VM 销毁即停止）
        viewModelScope.launch(Dispatchers.IO) {
            val dao = container.database.metaDao()
            while (isActive) {
                delay(5000.milliseconds)
                runCatching {
                    val current = dao.get(watchKey())?.toIntOrNull() ?: 0
                    dao.put(com.muxiao.timart.data.local.db.entity.MetaEntity(watchKey(), (current + 5).toString()))
                }
            }
        }
    }

    // ================= 胶囊状态回流 =================

    private suspend fun onCapsule(capsule: Capsule?) {
        if (capsule == null) {
            _state.update { if (it.phase == Phase.LOADING) it.copy(phase = Phase.MISSING) else it }
            return
        }
        latestCapsule = capsule
        when (capsule.state) {
            CapsuleState.LOCKED -> {
                sawLocked = true
                val current = _state.value
                if (current.phase != Phase.LOADING && current.phase != Phase.LOCKED) return
                _state.update {
                    it.copy(
                        phase = Phase.LOCKED,
                        capsule = capsule,
                        timeline = it.timeline ?: emptyTimeline(capsule),
                    )
                }
                startJudgeLoop()
            }

            CapsuleState.UNLOCKED -> {
                ensureReadMarkChecked()
                onUnlocked(capsule)
            }

            CapsuleState.DESTROYED -> {
                _state.update {
                    it.copy(
                        // 消散动画播放中保持相位（销毁完成回流不改写），否则直落尘迹态
                        phase = if (it.phase == Phase.DISSOLVE) Phase.DISSOLVE else Phase.ARCHIVED,
                        capsule = capsule,
                    )
                }
                loadDestroyedAt()
            }
        }
    }

    /** 一次性读取「已阅读」标记（meta，读失败按已读处理，避免误播） */
    private suspend fun ensureReadMarkChecked() {
        if (readMarkChecked) return
        readMarkExists = runCatching {
            container.database.metaDao().get(readMarkKey()) == "true"
        }.getOrDefault(true)
        readMarkChecked = true
    }

    /** UNLOCKED 后首次进入阅读：写已读标记 + 开启时刻（之后重读走 450ms 简化过渡） */
    private fun markAsRead() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val dao = container.database.metaDao()
                dao.put(
                    com.muxiao.timart.data.local.db.entity.MetaEntity(readMarkKey(), "true"),
                )
                // 开启时刻（SameDayAsCapsuleRead / DaysSinceCapsuleRead 判定通道）
                dao.put(
                    com.muxiao.timart.data.local.db.entity.MetaEntity(
                        readAtKey(),
                        container.timeProvider.nowMillis().toString(),
                    ),
                )
                // 嵌套种子播种（体验储备池 §3）：父胶囊已读 → 写萌芽标记，休眠种子转为正常胶囊。
                // 播种是 meta 标记而非内容复制——种子在父封存时就已完整入库（独立加密、独立规则）
                runCatching {
                    dao.listLike(com.muxiao.timart.data.local.db.CapsuleMetaKeys.SEED_OF_KEY_PREFIX)
                        .filter { it.value == capsuleId }
                        .forEach { seed ->
                            dao.put(
                                com.muxiao.timart.data.local.db.entity.MetaEntity(
                                    com.muxiao.timart.data.local.db.CapsuleMetaKeys.sprout(
                                        seed.key.removePrefix(
                                            com.muxiao.timart.data.local.db.CapsuleMetaKeys.SEED_OF_KEY_PREFIX,
                                        ),
                                    ),
                                    "true",
                                ),
                            )
                        }
                }
            }
        }
    }

    private fun readMarkKey() = "capsule.read.$capsuleId"

    /** 凝视时长键（跨 VM meta 契约：写入点本类 init，读取点 AppContainer.capsuleMetaProvider.watchSeconds） */
    private fun watchKey() = "capsule.watch.$capsuleId"

    /** 开启时刻键（跨 VM meta 契约：写入点本类 markAsRead，读取点 capsuleMetaProvider.lastReadAt） */
    private fun readAtKey() = "capsule.readAt.$capsuleId"

    /** 凝视计数键（跨 VM meta 契约：写入点本类 init，读取点 AppContainer.capsuleMetaProvider.viewCount） */
    private fun viewCountKey() = "capsule.views.$capsuleId"

    /** 归尘时间：领域模型不含销毁时间戳，从尘迹档案记录一次性读取 */
    private fun loadDestroyedAt() {
        viewModelScope.launch(Dispatchers.IO) {
            val at = destroyedRepo.observeAll().first().firstOrNull { it.id == capsuleId }?.destroyedAt
            if (at != null) {
                _state.update { it.copy(destroyedAt = at) }
            }
        }
    }

    private fun emptyTimeline(capsule: Capsule): TimelineUi = TimelineUi(
        items = emptyList(),
        satisfiedCount = 0,
        totalCount = capsule.unlockRule.conditionList.size,
        dependencyTitle = null,
        dependencyStatus = null,
        dependencyReason = null,
        dependencyUnreachable = false,
    )

    // ================= 判定循环（LOCKED 态） =================

    private fun startJudgeLoop() {
        if (judgeJob?.isActive == true) return
        judgeJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                judgeOnce()
                if (_state.value.phase != Phase.LOCKED) break
                delay(REJUDGE_INTERVAL_MS.milliseconds)
            }
        }
    }

    /**
     * 单颗判定：进页一次 + 每 30s 一次；满足差集 → 脉冲；全满足 → 回写 UNLOCKED。
     * 判定始终携带会话内已累积的挑战应答（已完成挑战在时间线上保持满足态）；
     * [newAnswers] 非空 = 本轮刚提交了挑战应答（仅用于应答错误的即时反馈）
     */
    private suspend fun judgeOnce(newAnswers: Map<String, String> = emptyMap()) {
        val capsule = repo.byIdSync(capsuleId) ?: return
        if (capsule.state != CapsuleState.LOCKED) return
        val result = judge.judge(
            capsule,
            container.defaultContext(foregroundOnly = false),
            RuntimeSettings.resolvedLang,
            sessionChallengeAnswers,
        )

        // 条件达成时刻埋点（只写不覆写，先到先记；失败静默，生平页该刻度显示为「—」）
        result.items.forEachIndexed { index, item ->
            if (item.satisfied && condMetRecorded.add("$capsuleId:$index")) {
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        val dao = container.database.metaDao()
                        val key = com.muxiao.timart.data.local.db.CapsuleMetaKeys.condMet(capsuleId, index)
                        if (dao.get(key) == null) {
                            dao.put(
                                com.muxiao.timart.data.local.db.entity.MetaEntity(
                                    key,
                                    container.timeProvider.nowMillis().toString(),
                                ),
                            )
                        }
                    }
                }
            }
        }

        // 挑战应答反馈：仅「刚提交且判错」才提示（正确提交不打扰，其余未满足原因时间线行内已明示）
        if (newAnswers.isNotEmpty()) {
            val reasons = com.muxiao.timart.domain.model.unlock.JudgeReasons.forLang(RuntimeSettings.resolvedLang)
            val wrongReason = result.items.lastOrNull { it.reason == reasons.challengeWrong }?.reason
            _state.update { it.copy(errorText = wrongReason) }
        }

        val depTitle = capsule.dependCapsuleId?.let { repo.byIdSync(it)?.title }
        val timeline = TimelineUi(
            items = result.items,
            satisfiedCount = result.items.count { it.satisfied },
            totalCount = result.items.size,
            dependencyTitle = depTitle,
            dependencyStatus = if (capsule.dependCapsuleId != null) result.dependencyStatus else null,
            dependencyReason = capsule.dependCapsuleId?.let { judge.dependencyReason(result.dependencyStatus, RuntimeSettings.resolvedLang) },
            dependencyUnreachable = result.dependencyStatus == DependencyStatus.DESTROYED,
            stepProgress = container.stepProvider.todaySteps(),
        )

        // 条件满足差集 → 局部 PENDING 脉冲（不解锁内容、无庆祝反馈）
        val prev = _state.value.timeline
        var newPulses = 0
        if (prev != null && !result.overallOk) {
            result.items.forEachIndexed { index, item ->
                val wasSatisfied = prev.items.getOrNull(index)?.satisfied == true
                if (!wasSatisfied && item.satisfied) newPulses++
            }
        }

        // 已完成的现场挑战 id（权威状态源：判定结果中挑战条件的 satisfied 位）
        val satisfiedChallengeIds = result.items
            .filter { it.condition is UnlockCondition.ChallengeCondition && it.satisfied }
            .map { (it.condition as UnlockCondition.ChallengeCondition).challengeId }
            .toSet()

        if (result.overallOk) {
            // 业务先行：回写 UNLOCKED，Room Flow 回流后进入 UNSEAL/CONTENT
            repo.updateState(capsule.id, CapsuleState.UNLOCKED, container.timeProvider.nowMillis())
            container.notifier.notifyUnlock(capsule.id, capsule.title)
            container.playCapsuleHaptic(capsule.id, destroy = false)
        }

        _state.update {
            it.copy(
                timeline = timeline,
                pendingPulse = it.pendingPulse + newPulses,
                satisfiedChallenges = satisfiedChallengeIds,
            )
        }
    }

    // ================= 现场挑战（打开胶囊当场完成） =================

    /** 锁定规则中的全部挑战条件（UI 渲染挑战小件用；快照判定永不满足它们） */
    fun pendingChallenges(): List<UnlockCondition.ChallengeCondition> =
        latestCapsule?.unlockRule?.conditionList
            ?.filterIsInstance<UnlockCondition.ChallengeCondition>()
            ?: emptyList()

    /** 提交一个挑战的当场应答：累积进会话应答表后复判，全部满足即解锁 */
    fun submitChallengeAnswer(condition: UnlockCondition.ChallengeCondition, answer: String) {
        sessionChallengeAnswers[condition.challengeId] = answer
        viewModelScope.launch(Dispatchers.Default) {
            judgeOnce(mapOf(condition.challengeId to answer))
        }
    }

    // ================= 后悔药：条件修改（每胶囊一次，长按尘核唤出） =================

    private fun condEditKey() = "capsule.condEdit.$capsuleId"

    /** 长按/连续快击尘核：仅机会未消耗且处于锁定态时唤出编辑面板（已消耗则彻底无感） */
    fun onOrbLongPress() {
        val current = _state.value
        if (current.regretAvailable && current.phase == Phase.LOCKED) {
            _state.update { it.copy(showRegretSheet = true) }
        }
    }

    fun dismissRegretSheet() {
        _state.update { it.copy(showRegretSheet = false) }
    }

    /**
     * 确认修改：先写规则、成功后才写一次性标记（中途失败不消耗机会），
     * 然后立即重判刷新时间线（不必等 30s 周期）。
     */
    fun saveEditedRule(rule: com.muxiao.timart.domain.model.unlock.UnlockRule) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                repo.updateUnlockRule(capsuleId, rule)
                container.database.metaDao().put(
                    com.muxiao.timart.data.local.db.entity.MetaEntity(condEditKey(), "true"),
                )
            }
            _state.update {
                if (result.isSuccess) {
                    it.copy(regretAvailable = false, showRegretSheet = false, errorText = null)
                } else {
                    it.copy(showRegretSheet = false, errorText = currentStrings().condEditFailed)
                }
            }
            if (result.isSuccess) judgeOnce()
        }
    }

    // ================= 解锁 → 阅读 =================

    private fun onUnlocked(capsule: Capsule) {
        val current = _state.value
        if (current.phase == Phase.UNSEAL || current.phase == Phase.CONTENT ||
            current.phase == Phase.DISSOLVE || current.phase == Phase.ARCHIVED
        ) {
            return
        }
        if (!crypto.ensureUnlocked()) {
            // 冷启动会话无密钥：先尝试保险库恢复（TTL 内免口令），仍无密钥才弹口令
            viewModelScope.launch(Dispatchers.IO) {
                if (crypto.tryRestoreSession()) {
                    proceedUnlocked(capsule)
                } else {
                    _state.update {
                        it.copy(needPassword = true, capsuleTitle = capsule.title, unlockedAt = capsule.unlockTimestamp)
                    }
                }
            }
            return
        }
        proceedUnlocked(capsule)
    }

    /** 进入阅读态：内容解密立即启动（业务不等待任何动画），相位仅决定视觉呈现 */
    private fun proceedUnlocked(capsule: Capsule) {
        // 完整揭封：解锁发生在眼前（本会话见过 LOCKED / 路由标记），或 UNLOCKED 后从未阅读过
        val playFullUnseal = firstUnlock || sawLocked || !readMarkExists
        markAsRead()
        readMarkExists = true
        readContent(capsule)
        _state.update {
            it.copy(
                phase = if (playFullUnseal) Phase.UNSEAL else Phase.CONTENT,
                playedFullUnseal = playFullUnseal,
                needPassword = false,
                errorText = null,
                capsuleTitle = capsule.title,
                unlockedAt = capsule.unlockTimestamp,
            )
        }
    }

    /** 口令弹窗确认（冷启动重读路径） */
    fun onPasswordEntered(password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching { crypto.unlock(password.toCharArray()) }.getOrDefault(false)
            if (ok) {
                val capsule = latestCapsule ?: repo.byIdSync(capsuleId)
                if (capsule != null) proceedUnlocked(capsule)
            } else {
                _state.update { it.copy(errorText = currentStrings().dvPwWrong) }
            }
        }
    }

    fun dismissPassword() {
        _state.update { it.copy(needPassword = false, errorText = null) }
    }

    private fun readContent(capsule: Capsule) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val content = reader.read(capsule, RuntimeSettings.resolvedLang)
                if (content.lockedInner != null) {
                    // 分片胶囊：外层已解、内层待分片重构（体验储备池 §7.1）
                    pendingInnerCipher = content.lockedInner
                    _state.update { it.copy(content = content, shardGate = true, errorText = null) }
                } else {
                    _state.update { it.copy(content = content, errorText = null) }
                    // 多章节信件（N10）：按已揭示章数过滤可见段落（单章信 chapterInfo = null 不动）
                    applyChapterFilter()
                }
            } catch (e: CryptoException) {
                _state.update {
                    val L = currentStrings()
                    it.copy(errorText = L.dvDecryptFailedFmt.format(e.message ?: L.dvCiphertextCorrupt))
                }
            } catch (_: IllegalStateException) {
                _state.update { it.copy(errorText = currentStrings().dvContentUnreadable) }
            }
        }
    }

    // ================= 声音留言播放（体验储备池 §1） =================

    /** 播放/停止切换（VoicePlayer 解密 → cache 临时文件 → MediaPlayer；播完自动回落） */
    fun toggleVoice() {
        if (!_state.value.voiceAvailable) return
        viewModelScope.launch(Dispatchers.IO) {
            val playing = runCatching { container.voicePlayer.toggle(capsuleId) }.getOrDefault(false)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                _state.update { it.copy(voicePlaying = playing) }
            }
        }
    }

    // ================= 回信（体验储备池 §5，仅作档案附言，不触碰销毁路径） =================

    /** 仅 CONTENT 态可写回信（绑定开启后的档案） */
    fun openReplyDialog() {
        if (_state.value.phase == Phase.CONTENT) _state.update { it.copy(showReplyDialog = true) }
    }

    fun dismissReplyDialog() {
        _state.update { it.copy(showReplyDialog = false) }
    }

    // ================= 多章节信件（N10，阅读侧行为；判定引擎不感知） =================

    /** 按当前揭示进度重算可见段落与下一章提示（chapterInfo = null 或无正文时为 no-op） */
    private fun applyChapterFilter() {
        val info = chapterInfo ?: return
        val now = container.timeProvider.nowMillis()
        val revealed = info.revealed
        val total = info.boundaries.size
        _state.update { state ->
            val content = state.content ?: return@update state
            val visible = com.muxiao.timart.domain.usecase.ChapterLetter
                .visibleParagraphs(content.paragraphs, info.boundaries, revealed)
            val canReveal = com.muxiao.timart.domain.usecase.ChapterLetter
                .canRevealNext(revealed, total, info.revealedAt, now)
            val etaDays = com.muxiao.timart.domain.usecase.ChapterLetter
                .nextChapterEtaMs(revealed, total, info.revealedAt, now)
                ?.let { ms -> kotlin.math.ceil(ms.toDouble() / (24L * 60 * 60 * 1000)).toInt().coerceAtLeast(1) }
            val L = currentStrings()
            val hint = when {
                canReveal -> L.chapterNextReady
                etaDays != null -> L.chapterNextEtaFmt.format(etaDays)
                else -> null
            }
            state.copy(
                content = content.copy(paragraphs = visible),
                chapterHint = hint,
                canRevealNextChapter = canReveal,
            )
        }
    }

    /** 揭示下一章（间隔 ≥3 天校验通过后进度 +1 落库并展开；其余为 no-op） */
    fun revealNextChapter() {
        val info = chapterInfo ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val now = container.timeProvider.nowMillis()
                if (!com.muxiao.timart.domain.usecase.ChapterLetter.canRevealNext(
                        info.revealed,
                        info.boundaries.size,
                        info.revealedAt,
                        now,
                    )
                ) {
                    return@runCatching
                }
                val dao = container.database.metaDao()
                info.revealed += 1
                info.revealedAt = now
                dao.put(
                    com.muxiao.timart.data.local.db.entity.MetaEntity(
                        com.muxiao.timart.data.local.db.CapsuleMetaKeys.chapterRevealed(capsuleId),
                        info.revealed.toString(),
                    ),
                )
                dao.put(
                    com.muxiao.timart.data.local.db.entity.MetaEntity(
                        com.muxiao.timart.data.local.db.CapsuleMetaKeys.chapterRevealedAt(capsuleId),
                        now.toString(),
                    ),
                )
                applyChapterFilter()
            }
        }
    }

    // ================= 临近解锁提醒（N1，确定性时间条件；Worker 周期扫描） =================

    /** 设置/关闭提前量（null = 关闭：删键 + 清 sent 去重标记，下轮开启重新提醒） */
    fun setRemindLeadDays(days: Int?) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val dao = container.database.metaDao()
                if (days == null) {
                    dao.delete(com.muxiao.timart.data.local.db.CapsuleMetaKeys.remind(capsuleId))
                    dao.delete(com.muxiao.timart.data.local.db.CapsuleMetaKeys.remindSent(capsuleId))
                } else {
                    val coerced = days.coerceIn(1, 30)
                    dao.put(
                        com.muxiao.timart.data.local.db.entity.MetaEntity(
                            com.muxiao.timart.data.local.db.CapsuleMetaKeys.remind(capsuleId),
                            coerced.toString(),
                        ),
                    )
                }
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                _state.update { it.copy(remindLeadDays = days?.coerceIn(1, 30)) }
            }
        }
    }

    /** 回信落 meta `capsule.reply.<id>`；销毁流程不清理该 key，回信随档案留存 */
    fun saveReply(text: String) {
        val trimmed = text.trim().take(REPLY_MAX)
        if (trimmed.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                container.database.metaDao().put(
                    com.muxiao.timart.data.local.db.entity.MetaEntity(
                        com.muxiao.timart.data.local.db.CapsuleMetaKeys.reply(capsuleId),
                        trimmed,
                    ),
                )
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                _state.update { it.copy(reply = trimmed, showReplyDialog = false) }
            }
        }
    }

    // ================= 拼图分组与合信视图（体验储备池 §3，判定引擎零改动） =================

    /** 装载本片拼图归属与全组进度：扫描 `capsule.puzzle.%`（键值契约见 CapsuleMetaKeys） */
    private suspend fun loadPuzzleState() {
        val dao = container.database.metaDao()
        val selfValue = dao.get(com.muxiao.timart.data.local.db.CapsuleMetaKeys.puzzle(capsuleId))
            ?: return
        val (groupId, index, total) = com.muxiao.timart.data.local.db.CapsuleMetaKeys.puzzleDecodeValue(selfValue)
            ?: return
        val members = collectGroupMembers(dao, groupId)
        val unlockedCount = members.count { (id, _) ->
            repo.byIdSync(id)?.state == CapsuleState.UNLOCKED
        }
        _state.update {
            it.copy(
                puzzleIndex = index,
                puzzleTotal = total,
                puzzleUnlockedCount = unlockedCount,
                puzzleReady = members.size >= total && unlockedCount >= total,
            )
        }
    }

    /** 同组成员列表：(胶囊 id, 片序)；值解析失败的条目跳过 */
    private suspend fun collectGroupMembers(
        dao: com.muxiao.timart.data.local.db.MetaDao,
        groupId: String,
    ): List<Pair<String, Int>> =
        dao.listLike(com.muxiao.timart.data.local.db.CapsuleMetaKeys.PUZZLE_KEY_PREFIX)
            .mapNotNull { entity ->
                val parsed = com.muxiao.timart.data.local.db.CapsuleMetaKeys.puzzleDecodeValue(entity.value)
                    ?: return@mapNotNull null
                if (parsed.first != groupId) return@mapNotNull null
                entity.key.removePrefix(com.muxiao.timart.data.local.db.CapsuleMetaKeys.PUZZLE_KEY_PREFIX) to parsed.second
            }

    /** 打开合信视图：全部片解锁后按片序解密各片正文聚合（不解密任何未解锁片，密文边界不变） */
    fun openPuzzleView() {
        val current = _state.value
        if (!current.puzzleReady || current.showPuzzleSheet) return
        viewModelScope.launch(Dispatchers.IO) {
            val dao = container.database.metaDao()
            val selfValue = dao.get(com.muxiao.timart.data.local.db.CapsuleMetaKeys.puzzle(capsuleId))
                ?: return@launch
            val (groupId, _, _) = com.muxiao.timart.data.local.db.CapsuleMetaKeys.puzzleDecodeValue(selfValue)
                ?: return@launch
            val fragments = collectGroupMembers(dao, groupId)
                .sortedBy { it.second }
                .mapNotNull { (id, index) ->
                    val capsule = repo.byIdSync(id) ?: return@mapNotNull null
                    if (capsule.state != CapsuleState.UNLOCKED) return@mapNotNull null
                    runCatching { reader.read(capsule, RuntimeSettings.resolvedLang) }.getOrNull()
                        ?.let { content -> PuzzleFragment(id, index, content.title, content.paragraphs) }
                }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                _state.update { it.copy(showPuzzleSheet = true, puzzleFragments = fragments) }
            }
        }
    }

    fun dismissPuzzleSheet() {
        _state.update { it.copy(showPuzzleSheet = false) }
    }

    /**
     * 拼图进度短句（信笺行用）：未分组 null；就绪 = 「合信就绪」句；
     * 未就绪 = 「拼图 x/y · 还差 n 片」句。
     */
    fun puzzleStatusText(L: com.muxiao.timart.l10n.Strings): String? {
        val current = _state.value
        val total = current.puzzleTotal ?: return null
        val unlocked = current.puzzleUnlockedCount ?: return null
        return if (current.puzzleReady) {
            L.puzzleReadyFmt.format(unlocked, total)
        } else {
            L.puzzleStatusFmt.format(unlocked, total, total - unlocked)
        }
    }

    // ================= 口令分片重构（体验储备池 §7.1） =================

    /**
     * 提交分片串（每行一份）：解析 → 重构 S → 派生 k2 → 校验 verifier₂ → 解内层。
     * 任一步失败都显式报错（fail-closed），不解密任何内容、不产生半开放状态；
     * 成功后内层密文与 k2 副本全部清零。
     */
    fun submitShardShares(raw: String) {
        val capsule = latestCapsule ?: return
        val threshold = capsule.shardThreshold ?: return
        val total = capsule.shardTotal
        val inner = pendingInnerCipher ?: return
        val paramsJson = capsule.shardParams ?: return
        val expectedVerifier = capsule.shardVerifier ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val useCase = container.shardSecretUseCase
            val shares = raw.lines().mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val decoded = runCatching { useCase.decodeShare(line) }.getOrNull() ?: return@mapNotNull null
                // 参数一致性：串上 total/threshold 与胶囊列不符的分片直接忽略
                if (decoded.total != total || decoded.threshold != threshold) return@mapNotNull null
                ShardSecretUseCase.Share(decoded.index, decoded.bytes)
            }
            val secret = useCase.combine(shares, threshold)
            val plainText: String? = secret?.let { secretBytes ->
                runCatching {
                    val params = com.muxiao.timart.data.local.crypto.KdfEngines.paramsFromJson(paramsJson)
                    val k2 = com.muxiao.timart.data.local.crypto.KdfEngines
                        .byAlgo(params.algo)
                        .derive(useCase.secretToPassword(secretBytes), params)
                    val ok = java.security.MessageDigest.isEqual(
                        useCase.verifierHex(k2).toByteArray(),
                        expectedVerifier.lowercase().toByteArray(),
                    )
                    if (!ok) {
                        java.util.Arrays.fill(k2, 0)
                        null
                    } else {
                        val plain = container.aesGcmCipher.decrypt(k2, inner)
                        java.util.Arrays.fill(k2, 0)
                        plain.decodeToString()
                    }
                }.getOrNull()
            }
            java.util.Arrays.fill(secret ?: ByteArray(0), 0)
            if (plainText == null) {
                // 错分片：显式报错（verifier₂ 校验 fail-closed），已录入的串留在输入框供修正
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    _state.update { it.copy(errorText = currentStrings().shardInvalid) }
                }
                return@launch
            }
            java.util.Arrays.fill(pendingInnerCipher ?: ByteArray(0), 0)
            pendingInnerCipher = null
            val paragraphs = plainText.split("\n\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .ifEmpty { listOf("") }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                _state.update { state ->
                    state.copy(
                        content = state.content?.copy(paragraphs = paragraphs, lockedInner = null),
                        shardGate = false,
                        errorText = null,
                    )
                }
            }
        }
    }

    // ================= 环境音（体验储备池 §6：解封淡入 / 离场淡出） =================

    private var ambientStarted = false

    fun startAmbient() {
        if (ambientStarted) return
        val capsule = latestCapsule ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val sceneName = runCatching {
                container.database.metaDao().get(com.muxiao.timart.data.local.db.CapsuleMetaKeys.ambient(capsule.id))
            }.getOrNull() ?: return@launch
            val scene = com.muxiao.timart.utils.audio.AmbientSoundPlayer.Scene.entries
                .firstOrNull { it.name == sceneName } ?: return@launch
            container.ambientSoundPlayer.start(scene)
            ambientStarted = true
        }
    }

    fun stopAmbient() {
        if (!ambientStarted) return
        ambientStarted = false
        container.ambientSoundPlayer.stop()
    }

    // ================= UNSEAL 视觉层控制（跳过即落 CONTENT） =================

    /** 任意点击 / 序列完成：安全落终态，业务零等待 */
    fun onUnsealFinished() {
        _state.update { if (it.phase == Phase.UNSEAL) it.copy(phase = Phase.CONTENT) else it }
    }

    // ================= 销毁决策（无静默销毁路径） =================

    fun requestDestroy() {
        _state.update { it.copy(destroyConfirmVisible = true) }
    }

    fun dismissDestroy() {
        _state.update { it.copy(destroyConfirmVisible = false) }
    }

    /** 确认销毁：markDestroyed（物理删除 + 档案）立即启动；DISSOLVE 仅是视觉层 */
    fun confirmDestroy() {
        val current = _state.value
        if (current.phase != Phase.CONTENT) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { crud.markDestroyed(capsuleId) }
        }
        _state.update {
            it.copy(phase = Phase.DISSOLVE, destroyConfirmVisible = false, backConfirmVisible = false)
        }
        container.playCapsuleHaptic(capsuleId, destroy = true)
        container.audioManager.playDissolve()
    }

    fun onDissolveFinished() {
        _state.update { if (it.phase == Phase.DISSOLVE) it.copy(phase = Phase.ARCHIVED) else it }
    }

    /** 「保留」（返回弹窗）为持久决定：回退为普通胶囊，之后阅读/返回不再询问 */
    fun keepAndExit() {
        setAutoDestroyAfterRead(false)
        _state.update { it.copy(keepAfterRead = true, backConfirmVisible = false) }
    }

    /**
     * 切换「阅读后自动销毁」（看后销毁 ↔ 保留）：持久化写库，Room 回流刷新 UI。
     * 保留胶囊的销毁仍走显式确认弹窗（无静默销毁路径）。
     */
    fun setAutoDestroyAfterRead(value: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repo.updateAutoDestroyAfterRead(capsuleId, value) }
        }
    }

    /**
     * 返回键拦截决策：
     * UNSEAL → 跳过动画；CONTENT 且 autoDestroy 未决策 → 弹「销毁/保留」。
     * @return true = UI 应消费本次返回（不退出页面）
     */
    fun onBackPressed(): Boolean {
        val current = _state.value
        return when (current.phase) {
            Phase.UNSEAL -> {
                onUnsealFinished()
                true
            }

            Phase.CONTENT -> {
                val autoDestroy = latestCapsule?.autoDestroyAfterRead == true
                if (autoDestroy && !current.keepAfterRead && current.content != null) {
                    _state.update { it.copy(backConfirmVisible = true) }
                    true
                } else {
                    false
                }
            }

            else -> false
        }
    }

    companion object {
        /** LOCKED 态周期重判间隔（PRD：详情页 30s 周期 + 进页一次） */
        const val REJUDGE_INTERVAL_MS = 30_000L

        /** 回信长度上限（一句附言） */
        const val REPLY_MAX = 60
    }

    override fun onCleared() {
        // 停止语音播放并删除明文缓存（明文语音不留盘）；环境音淡出通道随 VM 终止
        container.voicePlayer.stop()
        container.ambientSoundPlayer.shutdown()
    }
}
