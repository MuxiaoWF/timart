package com.muxiao.timart.ui.detail

import android.Manifest
import android.content.Context
import android.hardware.SensorManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.muxiao.timart.domain.context.DependencyStatus
import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.domain.model.ConditionStatus
import com.muxiao.timart.domain.model.Lang
import com.muxiao.timart.domain.model.unlock.ConditionText
import com.muxiao.timart.domain.model.unlock.JudgeReasons
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.l10n.stringsFor
import com.muxiao.timart.ui.components.PermissionGuideDialog
import com.muxiao.timart.ui.components.condition_widgets.ConditionTimeline
import com.muxiao.timart.ui.components.condition_widgets.ConditionTimelineItem
import com.muxiao.timart.ui.components.condition_widgets.ConditionTone
import com.muxiao.timart.ui.components.particle.MotionState
import com.muxiao.timart.ui.components.particle.ParticleEngine
import com.muxiao.timart.ui.components.particle.ParticlePreset
import com.muxiao.timart.ui.components.visual.SectionHeader
import com.muxiao.timart.ui.cosmic.CapsuleOrbView
import com.muxiao.timart.ui.cosmic.anchorColorArgb
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.DustAsh
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.LockedSlate
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TrackHairline
import com.muxiao.timart.utils.RuntimeSettings
import com.muxiao.timart.utils.format.TimeFormatter
import com.muxiao.timart.utils.permission.PERM_ACTIVITY_RECOGNITION

/**
 * 状态 A：LOCKED 未解锁（PRD §3.4.4 状态 A / 设计稿 04）：
 * - 不可解读尘核：无内容轮廓，仅弱内流与呼吸光；
 * - 极简条件时间线：满足=稳定微光 / 未满足=低亮空位 / 无法满足=低亮附原因；
 * - 依赖前置尘核：小型球体 + 状态说明，已销毁明示「永久无法解锁」；
 * - 无「尝试解锁」按钮：页面核心动作是等待现实条件完成；
 * - 条件满足差集 → 局部向心尘流（PENDING 单发 + 轻音效，不解锁内容、不庆祝）。
 */
@Composable
fun LockedStateView(
    capsule: Capsule,
    timeline: DetailViewModel.TimelineUi?,
    pulseCount: Int,
    engine: ParticleEngine,
    onPlayPendingSound: () -> Unit,
    autoDestroyAfterRead: Boolean,
    onToggleAutoDestroy: (Boolean) -> Unit,
    modifier: Modifier = Modifier,

    /** 现场挑战条件列表与应答回调（完成当场挑战 → VM 携带应答复判） */
    pendingChallenges: List<UnlockCondition.ChallengeCondition> = emptyList(),
    onChallengeAnswer: (UnlockCondition.ChallengeCondition, String) -> Unit = { _, _ -> },

    /** 会话内已完成的挑战 id（挑战卡据此显示「已满足」完成态） */
    satisfiedChallenges: Set<String> = emptySet(),

    /** 「后悔药」隐藏入口：每胶囊一次的条件修改机会未消耗时，长按或连续快击尘核唤出 */
    regretAvailable: Boolean = false,
    onOrbLongPress: () -> Unit = {},

    /** 粒子画布（宿主 ParticleCanvas）在窗口根坐标中的原点：尘核锚点换算画布局部坐标用 */
    overlayOrigin: Offset = Offset.Zero,
) {
    val L = LocalStrings.current
    val context = LocalContext.current
    // 尘核中心（composition 根坐标，经 overlayOrigin 换算成粒子画布局部坐标供引擎使用）
    var orbCenter by remember { mutableStateOf(Offset.Zero) }
    var orbRadiusPx by remember { mutableStateOf(1f) }
    var lastPulse by remember { mutableStateOf(0) }

    // 条件行权限引导（T14）：被权限阻断时，说明先于系统弹窗
    var showGpsGuide by remember { mutableStateOf(false) }
    var showStepGuide by remember { mutableStateOf(false) }
    val gpsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val stepLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val gpsBlocked = timeline?.items?.any {
        it.reason == JudgeReasons.forLang(RuntimeSettings.resolvedLang).gpsNoPermission
    } == true
    // 步数条件被阻断：仅当确因活动记录权限缺失（且设备有计步硬件）才引导申请
    val judgeReasons = JudgeReasons.forLang(RuntimeSettings.resolvedLang)
    val hasStepSensor = remember {
        (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)
            ?.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER) != null
    }
    val stepPermissionMissing = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(context, PERM_ACTIVITY_RECOGNITION) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        false // API < 29 无该权限概念，读传感器不受限
    }
    val stepBlocked = hasStepSensor && stepPermissionMissing && timeline?.items?.any {
        it.reason == judgeReasons.stepUnavailable
    } == true

    // 条件满足差集 → PENDING 单发（局部反馈；锚点换算到粒子画布局部坐标系）
    LaunchedEffect(pulseCount, overlayOrigin) {
        if (pulseCount > lastPulse && orbCenter != Offset.Zero) {
            engine.fire(
                preset = ParticlePreset.PENDING,
                anchorX = orbCenter.x - overlayOrigin.x,
                anchorY = orbCenter.y - overlayOrigin.y,
                anchorRadius = orbRadiusPx,
                colorArgb = ParticleEngine.TIME_GOLD,
            )
            onPlayPendingSound()
        }
        lastPulse = pulseCount
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // 顶部 64dp：为左上返回按钮让位
            .padding(start = 32.dp, end = 32.dp, top = 64.dp, bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SectionHeader(title = L.lockedTitle, note = "CAPSULE", modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(26.dp))

        Text(
            text = capsule.title,
            style = TimartType.titleSerif,
            color = InkPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = TimeFormatter.sealDate(capsule.createTimestamp),
            style = TimartType.caption,
            color = InkDisabled,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(modifier = Modifier.height(30.dp))

        // 不可解读尘核（单主焦点；内流由引擎承担，此处静态核兜底）。
        // 隐藏入口：长按或连续快击尘核唤出「后悔药」（机会未消耗时才挂手势，已消耗则彻底无感）
        Box(
            modifier = Modifier
                .size(196.dp)
                .then(
                    if (regretAvailable) {
                        Modifier.pointerInput(capsule.id) {
                            // 快击计数持有在 pointerInput 协程局部（key 不变则协程存活、计数保持）
                            var tapCount = 0
                            var firstTapAt = 0L
                            awaitEachGesture {
                                val startMs = System.currentTimeMillis()
                                // 超时未松手 → 长按（时长 REGRET_LONG_PRESS_MS，远大于系统默认，
                                // 维持入口隐蔽性）；被父级滚动手势打断同样返回 null，
                                // 用耗时区分：不足时长的是打断，不触发
                                val up = withTimeoutOrNull(REGRET_LONG_PRESS_MS) {
                                    waitForUpOrCancellation()
                                }
                                if (up == null) {
                                    if (System.currentTimeMillis() - startMs >= REGRET_LONG_PRESS_MS) {
                                        onOrbLongPress()
                                    }
                                } else {
                                    val now = System.currentTimeMillis()
                                    if (now - firstTapAt > REGRET_TAP_WINDOW_MS) {
                                        tapCount = 1
                                        firstTapAt = now
                                    } else {
                                        tapCount++
                                    }
                                    if (tapCount >= REGRET_TAP_TRIGGER_COUNT) {
                                        tapCount = 0
                                        onOrbLongPress()
                                    }
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                )
                .onGloballyPositioned { coords ->
                    orbCenter = coords.positionInRoot() +
                        Offset(coords.size.width / 2f, coords.size.height / 2f)
                    orbRadiusPx = coords.size.width / 2f * 0.72f
                },
            contentAlignment = Alignment.Center,
        ) {
            // PENDING 视觉态（UI 层判定，不改库状态）：条件全部满足待判定/待点击 →
            // 球核转金 lerp + 封印环变进度环；未全满时进度参数传入但不起效（无视觉变化）
            val pendingTotal = timeline?.totalCount ?: capsule.unlockRule.conditionList.size
            val pendingSatisfied = timeline?.satisfiedCount ?: 0
            CapsuleOrbView(
                state = CapsuleState.LOCKED,
                radius = 88.dp,
                pending = pendingTotal > 0 && pendingSatisfied >= pendingTotal,
                satisfyProgress = if (pendingTotal > 0) {
                    pendingSatisfied.toFloat() / pendingTotal
                } else {
                    0f
                },
            )
        }

        // 锁定尘核弱内流（BREATHE，此前从未接线）：锚点 = 尘核中心（画布局部坐标），随布局重同步
        LaunchedEffect(orbCenter, overlayOrigin) {
            if (orbCenter != Offset.Zero) {
                engine.setOrbCount(1)
                engine.setOrbAnchor(
                    index = 0,
                    x = orbCenter.x - overlayOrigin.x,
                    y = orbCenter.y - overlayOrigin.y,
                    radius = orbRadiusPx,
                    colorArgb = CapsuleState.LOCKED.anchorColorArgb(),
                )
                engine.setState(
                    MotionState.BREATHE,
                    ParticleEngine.OWNER_DETAIL,
                )
            }
        }
        DisposableEffect(Unit) {
            onDispose { engine.setState(MotionState.IDLE) }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 满足进度（金色衬线，对齐设计稿 2/3）
        val total = timeline?.totalCount ?: capsule.unlockRule.conditionList.size
        val satisfied = timeline?.satisfiedCount ?: 0
        Text(
            text = "$satisfied / $total",
            style = TimartType.titleSerif,
            color = TimeGold,
        )
        Text(
            text = if (total > 0 && satisfied == total) L.condAllMet else L.condSomeMet,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )

        Spacer(modifier = Modifier.height(30.dp))

        timeline?.let { tl ->
            // 依赖前置尘核 + 状态说明（无依赖不显示）
            if (tl.dependencyStatus != null) {
                DependencyRow(timeline = tl)
                Spacer(modifier = Modifier.height(22.dp))
            }
            if (tl.items.isNotEmpty()) {
                ConditionTimeline(
                    items = tl.items.map { it.toTimelineItem(RuntimeSettings.resolvedLang, tl.stepProgress) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // 看后销毁 ↔ 保留 开关（随时可切；持久化写库，保留胶囊的销毁仍走显式确认）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = L.autoDestroyLabel,
                    style = TimartType.body,
                    color = InkPrimary,
                )
                Text(
                    text = if (autoDestroyAfterRead) {
                        L.autoDestroyOnDesc
                    } else {
                        L.autoDestroyOffDesc
                    },
                    style = TimartType.caption,
                    color = InkSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Switch(
                checked = autoDestroyAfterRead,
                onCheckedChange = onToggleAutoDestroy,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = DeepCharcoal,
                    checkedTrackColor = TimeGold,
                    uncheckedThumbColor = InkDisabled,
                    uncheckedTrackColor = SurfaceRaise,
                    uncheckedBorderColor = TrackHairline,
                ),
            )
        }

        // 条件行权限引导（T14）：权限未授予 → 去开启（拒绝不影响判定）。
        // 组容器统一与上方开关行的间距（18dp），组内两条引导间隔 12dp
        if (gpsBlocked || stepBlocked) {
            Column(modifier = Modifier.padding(top = 18.dp)) {
                if (gpsBlocked) {
                    Text(
                        text = L.gpsGuideText,
                        style = TimartType.caption,
                        color = TimeGold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showGpsGuide = true },
                    )
                }
                if (stepBlocked) {
                    Text(
                        text = L.stepGuideText,
                        style = TimartType.caption,
                        color = TimeGold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (gpsBlocked) Modifier.padding(top = 12.dp) else Modifier)
                            .clickable { showStepGuide = true },
                    )
                }
            }
        }
        if (showGpsGuide) {
            PermissionGuideDialog(
                title = L.gfPermTitle,
                body = L.permLocationDesc,
                onConfirm = {
                    gpsLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                },
                onDismiss = { showGpsGuide = false },
            )
        }
        if (showStepGuide) {
            PermissionGuideDialog(
                title = L.dfStepTitle,
                body = L.permActivityDesc,
                onConfirm = {
                    stepLauncher.launch(PERM_ACTIVITY_RECOGNITION)
                },
                onDismiss = { showStepGuide = false },
            )
        }

        // 现场挑战（问答/谜题/摇一摇/翻面静置/NFC）：不参与周期判定，打开时当场完成
        ChallengeSection(
            challenges = pendingChallenges,
            onAnswer = onChallengeAnswer,
            satisfiedIds = satisfiedChallenges,
        )

        Spacer(modifier = Modifier.weight(1f))

        // 底部叙事（对齐设计稿：还差最后一把钥匙…）
        val (narrative, narrativeNote) =
            narrativeFor(timeline, total, satisfied, RuntimeSettings.resolvedLang)
        Text(
            text = narrative,
            style = TimartType.body,
            color = InkSecondary,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = narrativeNote,
            style = TimartType.caption,
            color = InkDisabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 加密说明卡：点击「了解」直接收起条目；收起后留单行摘要，可点按再展开
        var cryptoCollapsed by remember { mutableStateOf(false) }
        if (!cryptoCollapsed) {
            Surface(
                color = SurfaceRaise,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = L.contentHidden, style = TimartType.caption, color = InkSecondary)
                        Text(
                            text = L.contentHiddenNote,
                            style = TimartType.caption,
                            color = InkDisabled,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Text(
                        text = L.gotIt,
                        style = TimartType.caption,
                        color = TimeGold,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .clickable { cryptoCollapsed = true },
                    )
                }
            }
        } else {
            Text(
                text = L.contentHidden,
                style = TimartType.caption,
                color = InkDisabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { cryptoCollapsed = false },
            )
        }
    }
}

/** 「后悔药」隐藏入口：长按触发时长（远大于系统默认 ~400ms，入口更隐蔽；中途被打断不触发） */
private const val REGRET_LONG_PRESS_MS = 3_000L

/** 「后悔药」隐藏入口的快击触发：时间窗内连续点击次数与窗口时长（长按之外的备用手势） */
private const val REGRET_TAP_TRIGGER_COUNT = 5
private const val REGRET_TAP_WINDOW_MS = 1_200L

/** 依赖前置尘核行：小型球体 + 依赖标题 + 状态文本（已销毁 → 永久无法解锁明示） */
@Composable
private fun DependencyRow(timeline: DetailViewModel.TimelineUi) {
    val L = LocalStrings.current
    val depState = when (timeline.dependencyStatus) {
        DependencyStatus.UNLOCKED -> CapsuleState.UNLOCKED
        DependencyStatus.LOCKED -> CapsuleState.LOCKED
        DependencyStatus.DESTROYED -> CapsuleState.DESTROYED
        DependencyStatus.NOT_FOUND, null -> CapsuleState.DESTROYED
    }
    val depStateText = when (timeline.dependencyStatus) {
        DependencyStatus.UNLOCKED -> L.depUnlocked
        DependencyStatus.LOCKED -> L.depLocked
        DependencyStatus.DESTROYED -> L.depDestroyedPermanent
        DependencyStatus.NOT_FOUND -> L.depNotFound
        null -> ""
    }
    val depColor = when (timeline.dependencyStatus) {
        DependencyStatus.UNLOCKED -> TimeGold
        DependencyStatus.LOCKED -> LockedSlate
        DependencyStatus.DESTROYED -> DustAsh
        DependencyStatus.NOT_FOUND, null -> InkDisabled
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            CapsuleOrbView(state = depState, radius = 11.dp)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = timeline.dependencyTitle ?: L.frontCapsuleFallback,
                style = TimartType.body.copy(fontSize = 15.sp),
                color = InkPrimary,
            )
            Text(
                text = depStateText,
                style = TimartType.caption,
                color = depColor,
                modifier = Modifier.padding(top = 2.dp),
            )
            timeline.dependencyReason?.let { reason ->
                Text(
                    text = reason,
                    style = TimartType.caption,
                    color = InkDisabled,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}

/** 底部叙事文案（随判定进度微调，保持低调不庆祝） */
private fun narrativeFor(
    timeline: DetailViewModel.TimelineUi?,
    total: Int,
    satisfied: Int,
    lang: Lang,
): Pair<String, String> {
    val L = stringsFor(lang)
    return when {
        timeline == null -> L.narrIdle1 to L.narrIdle2
        timeline.dependencyUnreachable -> L.narrDepDead1 to L.narrDepDead2
        total > 0 && satisfied == total -> L.narrReady1 to L.narrReady2
        total > 0 && satisfied == total - 1 -> L.narrOneLeft1 to L.narrOneLeft2
        else -> L.narrIdle1 to L.narrIdle2
    }
}

/** ConditionStatus → 时间线行（满足=微光 / 未满足=低亮 / 受阻=低亮附原因）；步数条件附注今日已走步数 */
private fun ConditionStatus.toTimelineItem(lang: Lang, todaySteps: Int? = null): ConditionTimelineItem {
    val tone = when {
        satisfied -> ConditionTone.SATISFIED
        skipped || reason != null -> ConditionTone.BLOCKED
        else -> ConditionTone.WAITING
    }
    val detail = when {
        satisfied -> stringsFor(lang).condSatisfiedTag
        reason != null -> reason
        else -> null
    }
    // 步数条件且可读到今日步数 → 附注实际进度 + 系统限制短注（无权限/无硬件/首帧未读到时不注）
    val stepNote = if (condition is UnlockCondition.StepCount && todaySteps != null) {
        listOf(
            stringsFor(lang).condStepProgressFmt.format(todaySteps),
            stringsFor(lang).stepRestartNote,
        ).joinToString(" · ")
    } else {
        null
    }
    val mergedDetail = listOfNotNull(detail, stepNote).joinToString(" · ")
    return ConditionTimelineItem(
        title = ConditionText.conditionSentence(condition, lang),
        detail = mergedDetail.takeIf { it.isNotBlank() },
        tone = tone,
    )
}
