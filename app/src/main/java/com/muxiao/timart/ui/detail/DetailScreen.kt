package com.muxiao.timart.ui.detail

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muxiao.timart.utils.RuntimeSettings
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.AppContainer
import com.muxiao.timart.ui.components.particle.ParticleCanvas
import com.muxiao.timart.ui.components.visual.GlowOrb
import com.muxiao.timart.ui.components.visual.SectionHeader
import com.muxiao.timart.ui.settings.PasswordUnlockDialog
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.LockedSlate
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.ui.theme.wideContentWidth
import com.muxiao.timart.utils.export.PosterComposer
import com.muxiao.timart.utils.format.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 详情页四状态路由（架构 §2.16 / PRD §2.3、§3.4.4）：
 * LOADING → LOCKED → UNSEAL → CONTENT →（可选 DISSOLVE）→ ARCHIVED / MISSING。
 * UNSEAL 期间任意点击跳过；业务状态更新永不等待动画回调。
 */
@Composable
fun DetailScreen(
    container: AppContainer,
    capsuleId: String,
    firstUnlock: Boolean,
    onBack: () -> Unit,
) {
    val L = LocalStrings.current
    val vm: DetailViewModel = viewModel { DetailViewModel(container, capsuleId, firstUnlock) }
    val state by vm.state.collectAsStateWithLifecycle()
    val engine = container.particleEngine
    val context = LocalContext.current
    // 陀螺仪 3D 倾斜（设置页开关；传感器随详情页生命周期启停）
    val tiltSensor = remember { com.muxiao.timart.utils.sensor.ParallaxSensor(context) }
    DisposableEffect(Unit) {
        if (RuntimeSettings.gyroEnabled) tiltSensor.start()
        onDispose { tiltSensor.stop() }
    }
    // 真实卡片实测高度（揭封双翼几何对齐用；内容/图片回流时自动更新）
    var measuredCardHeight by remember { mutableStateOf<Int?>(null) }

    // ---- 海报生成与分享（T15：PosterComposer + FileProvider）----
    val posterComposer = remember { PosterComposer(context) }
    val posterScope = rememberCoroutineScope()
    var posterBusy by remember { mutableStateOf(false) }

    fun generateAndSharePoster() {
        val content = state.content ?: return
        if (posterBusy) return
        posterBusy = true
        posterScope.launch(Dispatchers.IO) {
            val bitmaps = content.images.mapNotNull { bytes ->
                com.muxiao.timart.utils.format.ImageDecode.decodeOriented(bytes, maxDimension = 2048)
            }
            val poster = posterComposer.compose(
                PosterComposer.PosterContent(
                    title = content.title,
                    paragraphs = content.paragraphs,
                    images = bitmaps,
                    weatherLine = content.snapshot?.let {
                        "${it.cityName} · ${weatherNameOf(it.weatherType, RuntimeSettings.resolvedLang)} ${it.tempC.toInt()}°C"
                    },
                    createdLine = TimeFormatter.dateTime(content.createdAt),
                    unlockedLine = state.unlockedAt?.let { TimeFormatter.dateTime(it) },
                    tagsLine = content.tags.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                    note = content.note.takeIf { it.isNotBlank() },
                ),
            )
            withContext(Dispatchers.Main) {
                posterBusy = false
                if (poster == null) {
                    Toast.makeText(context, L.posterFail, Toast.LENGTH_SHORT).show()
                } else {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        poster,
                    )
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        // 分享面板的预览缩略图由系统启动器在选择目标前读取 URI 生成；
                        // 仅 EXTRA_STREAM 上的 grant 标志在部分 ROM 不随 Chooser 传播，
                        // 启动器拿不到授权 → 预览空白。ClipData 让授权随 Chooser 传递，预览即可显示
                        clipData = ClipData.newRawUri("poster", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(Intent.createChooser(send, L.posterShare)) }
                }
            }
        }
    }

    // 详情页不开背景漂移尘（单主焦点）；归属权令牌：仅当本页持有背景时才关
    DisposableEffect(Unit) {
        val owner = Any()
        engine.setBackground(false, owner)
        onDispose { }
    }

    // 重读 veil：仅非完整揭封路径播 450ms 简化重读（揭封路径由 UNSEAL 相位承担，不叠加）
    var showRereadVeil by remember { mutableStateOf(false) }
    LaunchedEffect(state.phase) {
        when (state.phase) {
            DetailViewModel.Phase.UNSEAL -> showRereadVeil = false
            DetailViewModel.Phase.CONTENT -> if (!state.playedFullUnseal) showRereadVeil = true
            else -> Unit
        }
    }

    // 环境音（体验储备池 §6）：CONTENT 淡入、离场淡出（scene 由胶囊 meta 决定）
    LaunchedEffect(state.phase) {
        if (state.phase == DetailViewModel.Phase.CONTENT) vm.startAmbient() else vm.stopAmbient()
    }

    // 粒子画布原点（窗口根坐标）：尘核锚点换算到画布局部坐标系用
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }

    // 单胶囊赠予导出弹窗（LOCKED 态入口在 LockedStateView；体验储备池 §4）
    var showGiftExport by remember { mutableStateOf(false) }

    // NFC 实体锚点写卡弹层（LOCKED 态入口在 LockedStateView；体验储备池 §4）
    var showNfcLinkWrite by remember { mutableStateOf(false) }

    // 返回键编排：UNSEAL → 跳过；autoDestroy 未决策 → 「销毁/保留」；其余直接退出
    BackHandler {
        if (!vm.onBackPressed()) onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .background(DeepCharcoal),
    ) {
        when (state.phase) {
            DetailViewModel.Phase.LOADING -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlowOrb(
                        radius = 18.dp,
                        coreColor = LockedSlate,
                        glowColor = LockedSlate.copy(alpha = 0.4f),
                        glowAlpha = 0.3f,
                    )
                }
            }

            DetailViewModel.Phase.MISSING -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 32.dp, end = 32.dp, top = 64.dp, bottom = 48.dp),
                ) {
                    SectionHeader(title = L.detailTitle, note = "CAPSULE")
                    Spacer(modifier = Modifier.height(40.dp))
                    Text(
                        text = L.detailMissing,
                        style = TimartType.body,
                        color = InkSecondary,
                    )
                }
            }

            DetailViewModel.Phase.LOCKED -> {
                val capsule = state.capsule
                if (capsule == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        GlowOrb(
                            radius = 18.dp,
                            coreColor = LockedSlate,
                            glowColor = LockedSlate.copy(alpha = 0.4f),
                            glowAlpha = 0.3f,
                        )
                    }
                } else {
                    // 粒子层（PENDING 条件满足脉冲在此画布单发；归属详情页：锁定尘核内流只在此渲染）
                    ParticleCanvas(
                        engine = engine,
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coords ->
                                overlayOrigin = coords.localToRoot(Offset.Zero)
                            },
                        owner = com.muxiao.timart.ui.components.particle.ParticleEngine.OWNER_DETAIL,
                    )
                    LockedStateView(
                        capsule = capsule,
                        timeline = state.timeline,
                        pulseCount = state.pendingPulse,
                        engine = engine,
                        onPlayPendingSound = { container.audioManager.playPending() },
                        autoDestroyAfterRead = capsule.autoDestroyAfterRead,
                        onToggleAutoDestroy = vm::setAutoDestroyAfterRead,
                        pendingChallenges = vm.pendingChallenges(),
                        onChallengeAnswer = vm::submitChallengeAnswer,
                        satisfiedChallenges = state.satisfiedChallenges,
                        regretAvailable = state.regretAvailable,
                        onOrbLongPress = vm::onOrbLongPress,
                        onGift = { showGiftExport = true },
                        onWriteNfcLink = { showNfcLinkWrite = true },
                        overlayOrigin = overlayOrigin,
                        // 宽屏限宽居中（横屏适配）：时间线行宽过长伤可读性；
                        // 尘核锚点经 positionInRoot 实测换算，居中偏移不影响粒子定位
                        modifier = Modifier.align(Alignment.TopCenter).wideContentWidth().fillMaxSize(),
                    )
                }
            }

            DetailViewModel.Phase.UNSEAL -> {
                // 隐藏实测通道：真实卡片以 0 透明度排版一次取自然高度——
                // 揭封双翼按实测高度生成，摊平交接时与正文卡片零几何差
                if (state.content != null && !state.needPassword) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0f },
                ) {
                    UnlockedLetterView(
                        content = state.content,
                        unlockedAt = state.unlockedAt,
                        autoDestroyAfterRead = state.capsule?.autoDestroyAfterRead == true,
                        loading = false,
                        onCompleteRead = null,
                        onKeep = null,
                        onDestroy = null,
                        onPoster = null,
                        onBack = null,
                        animateText = false,
                        parallax = null,
                        paperStyle = state.paperStyle,
                        onCardHeightChanged = { measuredCardHeight = it },
                    )
                }
                }
                UnsealSequence(
                    engine = engine,
                    cardHeightPx = measuredCardHeight,
                    onPlaySound = { container.audioManager.playUnseal() },
                    onDone = vm::onUnsealFinished,
                )
            }

            DetailViewModel.Phase.CONTENT -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (state.shardGate) {
                        // 口令分片门（体验储备池 §7.1）：内层仍锁，先集齐分片
                        ShardGateView(
                            capsule = state.capsule,
                            onSubmitted = vm::submitShardShares,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        UnlockedLetterView(
                        content = state.content,
                        unlockedAt = state.unlockedAt,
                        autoDestroyAfterRead = state.capsule?.autoDestroyAfterRead == true,
                        loading = state.content == null,
                        onCompleteRead = vm::requestDestroy,
                        onKeep = { vm.setAutoDestroyAfterRead(false) },
                        onDestroy = vm::requestDestroy,
                        onPoster = { generateAndSharePoster() },
                        // 左上返回按钮与系统返回同路：autoDestroy 未决策先弹「销毁/保留」
                        onBack = { if (!vm.onBackPressed()) onBack() },
                        // 揭封路径：信笺上不显文字，交接后由随机文字显现效果接管
                        // （重读路径同样播放；loading/未就绪时组件内部停在骨架）
                        animateText = true,
                        titleHint = state.capsule?.title,
                        parallax = tiltSensor,
                        voiceAvailable = state.voiceAvailable,
                        voicePlaying = state.voicePlaying,
                        onToggleVoice = vm::toggleVoice,
                        paperStyle = state.paperStyle,
                        reply = state.reply,
                        onWriteReply = vm::openReplyDialog,
                        puzzleStatus = vm.puzzleStatusText(L),
                        puzzleReady = state.puzzleReady,
                        onOpenPuzzle = vm::openPuzzleView,
                        onCardHeightChanged = { measuredCardHeight = it },
                        )
                    }
                    if (!state.shardGate && showRereadVeil) {
                        RereadVeilOverlay(engine = engine, onDone = { showRereadVeil = false })
                    }
                }
            }

            DetailViewModel.Phase.DISSOLVE -> {
                // 信笺淡出上浮（视觉层）；粒子散逸由 DissolveSequence 承担
                val fade = remember { Animatable(1f) }
                LaunchedEffect(Unit) {
                    fade.animateTo(0.12f, animationSpec = tween(durationMillis = 700))
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = fade.value
                            translationY = -24.dp.toPx() * (1f - fade.value)
                        },
                ) {
                    UnlockedLetterView(
                        content = state.content,
                        unlockedAt = state.unlockedAt,
                        autoDestroyAfterRead = false,
                        loading = false,
                        onCompleteRead = null,
                        onKeep = null,
                        onDestroy = null,
                        onPoster = null,
                        onBack = null,
                        animateText = false,
                        parallax = tiltSensor,
                        paperStyle = state.paperStyle,
                    )
                }
                DissolveSequence(engine = engine, onFinished = vm::onDissolveFinished)
            }

            DetailViewModel.Phase.ARCHIVED -> {
                DestroyedStateView(
                    title = state.capsule?.title,
                    destroyedAt = state.destroyedAt,
                )
            }
        }

        // 阅读错误提示（解密失败 / 不可读）
        state.errorText?.let { message ->
            Text(
                text = message,
                style = TimartType.caption,
                color = TimeGold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp),
            )
        }

        // 返回入口：锁定 / 不存在 / 尘迹态（阅读态由信笺自带；揭封/消散为全屏动效不设）
        if (state.phase == DetailViewModel.Phase.LOCKED ||
            state.phase == DetailViewModel.Phase.MISSING ||
            state.phase == DetailViewModel.Phase.ARCHIVED
        ) {
            DetailBackButton(
                onClick = { if (!vm.onBackPressed()) onBack() },
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }

    // ---- 对话框层 ----

    // NFC 实体锚点写卡（timart.com:link 卡贴；碰卡直达）
    if (showNfcLinkWrite) {
        NfcLinkWriteDialog(
            capsuleId = capsuleId,
            onDismiss = { showNfcLinkWrite = false },
        )
    }

    // 单胶囊赠予导出（加密赠予文件；口令由封存者另行告知接受者）
    if (showGiftExport) {
        com.muxiao.timart.ui.settings.GiftExportDialog(
            manager = container.backupManager,
            capsuleId = capsuleId,
            onDismiss = { showGiftExport = false },
        )
    }

    // 「后悔药」条件编辑（长按尘核唤出；同包 internal，无需 import）

    // 回信弹窗（体验储备池 §5）：一句附言，存 meta，销毁后随尘迹档案留存
    if (state.showReplyDialog) {
        var replyInput by remember(state.reply) { mutableStateOf(state.reply.orEmpty()) }
        AlertDialog(
            onDismissRequest = vm::dismissReplyDialog,
            containerColor = com.muxiao.timart.ui.theme.SurfaceRaise,
            title = { Text(text = L.replyWrite, style = TimartType.titleSerif) },
            text = {
                Column {
                    Text(
                        text = L.replyHint,
                        style = TimartType.caption,
                        color = InkSecondary,
                    )
                    androidx.compose.foundation.text.BasicTextField(
                        value = replyInput,
                        onValueChange = { replyInput = it.take(DetailViewModel.REPLY_MAX) },
                        singleLine = true,
                        textStyle = TimartType.body.copy(color = InkPrimary),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(TimeGold),
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth()
                            .background(DeepCharcoal.copy(alpha = 0.5f), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        decorationBox = { inner ->
                            Box {
                                if (replyInput.isEmpty()) {
                                    Text(text = L.replyPlaceholder, style = TimartType.body, color = InkSecondary)
                                }
                                inner()
                            }
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.saveReply(replyInput) },
                    enabled = replyInput.isNotBlank(),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = TimeGold),
                ) {
                    Text(text = L.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissReplyDialog) {
                    Text(text = L.cancel, color = InkSecondary)
                }
            },
        )
    }

    // 合信视图弹层（体验储备池 §3）：全部片解锁后按片序聚合阅读
    if (state.showPuzzleSheet) {
        AlertDialog(
            onDismissRequest = vm::dismissPuzzleSheet,
            containerColor = com.muxiao.timart.ui.theme.SurfaceRaise,
            title = { Text(text = L.puzzleSheetTitle, style = TimartType.titleSerif) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    val fragments = state.puzzleFragments.orEmpty()
                    if (fragments.isEmpty()) {
                        Text(
                            text = L.puzzleEmpty,
                            style = TimartType.caption,
                            color = InkSecondary,
                        )
                    }
                    fragments.forEachIndexed { i, fragment ->
                        if (i > 0) {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        Text(
                            text = "${L.puzzlePieceFmt.format(fragment.index + 1)} · ${fragment.title}",
                            style = TimartType.body.copy(fontSize = 14.sp),
                            color = TimeGold,
                        )
                        fragment.paragraphs.forEach { paragraph ->
                            Text(
                                text = paragraph,
                                style = TimartType.body.copy(fontSize = 14.sp, lineHeight = 24.sp),
                                color = InkPrimary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = vm::dismissPuzzleSheet) {
                    Text(text = L.ok, color = TimeGold)
                }
            },
        )
    }
    if (state.showRegretSheet) {
        state.capsule?.let { capsule ->
            ConditionEditSheet(
                capsule = capsule,
                geocodeResolver = container.geocodeResolver,
                onConfirm = vm::saveEditedRule,
                onDismiss = vm::dismissRegretSheet,
            )
        }
    }

    if (state.destroyConfirmVisible) {
        AlertDialog(
            onDismissRequest = vm::dismissDestroy,
            title = { Text(text = L.destroyAskTitle, style = TimartType.titleSerif) },
            text = {
                Text(
                    text = L.destroyAskBody,
                    style = TimartType.body.copy(fontSize = 14.sp),
                    color = InkSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = vm::confirmDestroy,
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = TimeGold),
                ) {
                    Text(text = L.destroy)
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissDestroy) {
                    Text(text = L.notFinishedReading, color = InkSecondary)
                }
            },
            containerColor = com.muxiao.timart.ui.theme.SurfaceRaise,
        )
    }

    if (state.backConfirmVisible) {
        AlertDialog(
            // 外部点击 / 返回键关闭 = 视同「保留」并退出（无静默销毁路径）
            onDismissRequest = {
                vm.keepAndExit()
                onBack()
            },
            title = { Text(text = L.backDestroyAskTitle, style = TimartType.titleSerif) },
            text = {
                Text(
                    text = L.backDestroyAskBody,
                    style = TimartType.body.copy(fontSize = 14.sp),
                    color = InkSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = vm::confirmDestroy,
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = TimeGold),
                ) {
                    Text(text = L.destroy)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.keepAndExit()
                    onBack()
                }) {
                    Text(text = L.keep, color = InkSecondary)
                }
            },
            containerColor = com.muxiao.timart.ui.theme.SurfaceRaise,
        )
    }

    if (state.needPassword) {
        PasswordUnlockDialog(
            errorText = state.errorText,
            // 取消口令 = 放弃本次阅读：直接返回上一页。
            // 冷启动路径此时相位停在 LOADING（仅居中尘核），现场解锁路径停在 LOCKED——
            // 两者取消后都没有可交互内容，留在原地只会卡死在「未开启」视图
            onDismiss = {
                vm.dismissPassword()
                onBack()
            },
            onConfirm = vm::onPasswordEntered,
        )
    }
}

/**
 * 口令分片收集面板（体验储备池 §7.1）：外层已解、内层待重构——
 * 持有人把 M 份分片串逐行粘贴，重构 k2 校验 verifier₂ 后才解出正文。
 * 错误分片 fail-closed：显式报错，不产生任何半开放状态。
 */
@Composable
private fun ShardGateView(
    capsule: com.muxiao.timart.domain.model.Capsule?,
    onSubmitted: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val L = LocalStrings.current
    var input by remember { mutableStateOf("") }
    val threshold = capsule?.shardThreshold ?: 0
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        modifier = modifier
            .padding(horizontal = 32.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = capsule?.title ?: L.untitled,
            style = TimartType.titleSerif,
            color = InkPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = L.shardGateHintFmt.format(threshold),
            style = TimartType.body,
            color = InkSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp),
        )
        androidx.compose.foundation.text.BasicTextField(
            value = input,
            onValueChange = { input = it },
            textStyle = TimartType.caption.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = InkPrimary,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(TimeGold),
            modifier = Modifier
                .padding(top = 18.dp)
                .fillMaxWidth()
                .height(160.dp)
                .background(com.muxiao.timart.ui.theme.SurfaceRaise, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .padding(14.dp),
            decorationBox = { inner ->
                Box {
                    if (input.isEmpty()) {
                        Text(
                            text = L.shardGateInputHint,
                            style = TimartType.caption,
                            color = InkDisabled,
                        )
                    }
                    inner()
                }
            },
        )
        TextButton(
            onClick = { onSubmitted(input) },
            enabled = input.isNotBlank(),
            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = TimeGold),
            modifier = Modifier.padding(top = 18.dp),
        ) {
            Text(text = L.shardGateSubmit)
        }
    }
}
