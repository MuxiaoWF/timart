package com.muxiao.timart.ui.detail

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import com.muxiao.timart.ui.components.visual.rememberRevealClock
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

    /** 回信转新胶囊（N5）：交接预填草稿后导航创建页（null = 不展示入口） */
    onCreateCapsule: (() -> Unit)? = null,
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

    // 揭封 hold 期与 CONTENT 卡片共享的显现时钟（每次进入详情新建，效果重抽；
    // hold 期起跑 → 交接零跳变续走，hold 前跳过则由 CONTENT 侧起跑）
    val unsealRevealClock = rememberRevealClock()

    // ---- 海报生成与分享（T15：PosterComposer + FileProvider）----
    val posterComposer = remember { PosterComposer(context) }
    val posterScope = rememberCoroutineScope()
    var posterBusy by remember { mutableStateOf(false) }

    /** 海报分享（信笺纪念页与尘迹纪念页同路：FileProvider + ACTION_SEND） */
    fun sharePosterFile(poster: java.io.File?) {
        if (poster == null) {
            Toast.makeText(context, L.posterFail, Toast.LENGTH_SHORT).show()
            return
        }
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
                sharePosterFile(poster)
            }
        }
    }

    /**
     * 尘迹纪念页（留存与输出）：销毁态无正文无图片（内容已物理删除），
     * 海报 = 标题 + 归尘日期 + 纪念文案 + 封存备注/标签——销毁不被庆祝但可被纪念。
     */
    fun generateAndShareDustPoster() {
        val capsule = state.capsule ?: return
        if (posterBusy) return
        posterBusy = true
        posterScope.launch(Dispatchers.IO) {
            val paragraphs = buildList {
                state.destroyedAt?.let {
                    add(L.dustPosterLineFmt.format(TimeFormatter.dateTime(it)))
                }
                add(L.destroyedBody)
            }
            val poster = posterComposer.compose(
                PosterComposer.PosterContent(
                    title = capsule.title,
                    paragraphs = paragraphs,
                    createdLine = TimeFormatter.dateTime(capsule.createTimestamp),
                    tagsLine = capsule.tags.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                    note = capsule.createNote.takeIf { it.isNotBlank() },
                ),
            )
            withContext(Dispatchers.Main) {
                posterBusy = false
                sharePosterFile(poster)
            }
        }
    }

    // ---- 明文导出（N13）：仅已解锁内容；SAF CreateDocument 写 .txt，导出即脱离加密保护 ----

    /** 导出文本组装：标题 + 正文 + 标签 + 封存/开启时刻 + 回信（信笺当前呈现口径的纯文本投影） */
    fun exportPlainText(content: com.muxiao.timart.domain.usecase.ReadCapsuleUseCase.CapsuleContent): String =
        buildString {
            appendLine(content.title)
            appendLine()
            content.paragraphs.forEach { paragraph ->
                appendLine(paragraph)
                appendLine()
            }
            content.tags.takeIf { it.isNotEmpty() }?.let {
                appendLine(it.joinToString(" · "))
                appendLine()
            }
            appendLine(L.letterExportMetaCreatedFmt.format(TimeFormatter.dateTime(content.createdAt)))
            state.unlockedAt?.let {
                appendLine(L.letterExportMetaUnlockedFmt.format(TimeFormatter.dateTime(it)))
            }
            state.reply?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("${L.replyLabel}: $it")
            }
        }

    var showExportTextConfirm by remember { mutableStateOf(false) }
    val exportTextLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val content = state.content
        if (uri == null || content == null) return@rememberLauncherForActivityResult
        posterScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(exportPlainText(content).toByteArray(Charsets.UTF_8))
                    true
                } ?: false
            }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    if (ok) L.letterExportDone else L.letterExportFail,
                    Toast.LENGTH_SHORT,
                ).show()
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
    // 临近解锁提醒设置弹窗（N1）
    var showRemindDialog by remember { mutableStateOf(false) }

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
                        remindLeadDays = state.remindLeadDays,
                        onRemind = { showRemindDialog = true },
                        earliestUnlockAt = state.earliestUnlockAt,
                        chain = state.chain,
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
                    paperStyle = state.paperStyle,
                    title = state.capsule?.title.orEmpty(),
                    revealClock = unsealRevealClock,
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
                        onExportText = { showExportTextConfirm = true },
                        // 左上返回按钮与系统返回同路：autoDestroy 未决策先弹「销毁/保留」
                        onBack = { if (!vm.onBackPressed()) onBack() },
                        // 揭封路径：信笺上不显文字，交接后由随机文字显现效果接管
                        // （重读路径同样播放；loading/未就绪时组件内部停在骨架）
                        animateText = true,
                        titleHint = state.capsule?.title,
                        // 揭封交接重叠：复用揭封 hold 期起跑的共享时钟，标题进度零跳变续走
                        revealClock = unsealRevealClock,
                        parallax = tiltSensor,
                        voiceAvailable = state.voiceAvailable,
                        voicePlaying = state.voicePlaying,
                        onToggleVoice = vm::toggleVoice,
                        paperStyle = state.paperStyle,
                        reply = state.reply,
                        chapterHint = state.chapterHint,
                        onRevealNextChapter = if (state.canRevealNextChapter) vm::revealNextChapter else null,
                        onWriteReply = vm::openReplyDialog,
                        sealStyle = state.sealStyle,
                        onReplyToCapsule = onCreateCapsule?.let { handler ->
                            {
                                // 交接预填草稿（一次性消费位）：标题沿用原胶囊，正文即回信
                                container.pendingCapsulePrefill = AppContainer.CapsulePrefill(
                                    title = state.capsule?.title.orEmpty().ifBlank {
                                        state.reply.orEmpty().take(20)
                                    },
                                    body = state.reply.orEmpty(),
                                    replySourceId = capsuleId,
                                )
                                handler()
                            }
                        },
                        puzzleStatus = vm.puzzleStatusText(L),
                        puzzleReady = state.puzzleReady,
                        onOpenPuzzle = vm::openPuzzleView,
                        onCardHeightChanged = { measuredCardHeight = it },
                        )
                    }
                    if (!state.shardGate && showRereadVeil) {
                        RereadVeilOverlay(
                            engine = engine,
                            onDone = { showRereadVeil = false },
                            cardHeightPx = measuredCardHeight,
                        )
                    }
                }
            }

            DetailViewModel.Phase.DISSOLVE -> {
                // 信笺淡出上浮（视觉层）；粒子散逸由 DissolveSequence 承担
                // （LOW 档经引擎同拍缩放，与散逸时序保持同一比例）
                val fade = remember { Animatable(1f) }
                LaunchedEffect(Unit) {
                    fade.animateTo(0.12f, animationSpec = tween(durationMillis = 700 * engine.sequenceTimePct / 100))
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
                DissolveSequence(
                    engine = engine,
                    onFinished = vm::onDissolveFinished,
                    cardHeightPx = measuredCardHeight,
                )
            }

            DetailViewModel.Phase.ARCHIVED -> {
                DestroyedStateView(
                    title = state.capsule?.title,
                    destroyedAt = state.destroyedAt,
                    onPoster = { generateAndShareDustPoster() },
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
                                    // 待答之问（N20）：封存时写过问题则以其为引导占位
                                    Text(
                                        text = state.question?.let { L.replyQuestionFmt.format(it) }
                                            ?: L.replyPlaceholder,
                                        style = TimartType.body,
                                        color = InkSecondary,
                                    )
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

    // 明文导出确认（N13）：正文一旦导出即脱离加密保护，必须显式确认（与销毁同级的谨慎口径）
    if (showExportTextConfirm) {
        AlertDialog(
            onDismissRequest = { showExportTextConfirm = false },
            containerColor = com.muxiao.timart.ui.theme.SurfaceRaise,
            title = { Text(text = L.letterExportText, style = TimartType.titleSerif) },
            text = {
                Text(
                    text = L.letterExportTextDesc,
                    style = TimartType.caption,
                    color = InkSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExportTextConfirm = false
                        val rawTitle = state.capsule?.title ?: state.content?.title ?: "capsule"
                        val safe = rawTitle
                            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                            .take(60)
                            .ifBlank { "capsule" }
                        runCatching { exportTextLauncher.launch("$safe.txt") }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = TimeGold),
                ) {
                    Text(text = L.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportTextConfirm = false }) {
                    Text(text = L.cancel, color = InkSecondary)
                }
            },
        )
    }

    // 临近解锁提醒设置（N1）：提前量档位单选；仅对固定日期/时刻/每年纪念日类条件生效
    if (showRemindDialog) {
        val options = listOf(null to L.remindOff, 1 to L.remindLeadFmt.format(1), 3 to L.remindLeadFmt.format(3), 7 to L.remindLeadFmt.format(7))
        AlertDialog(
            onDismissRequest = { showRemindDialog = false },
            containerColor = com.muxiao.timart.ui.theme.SurfaceRaise,
            title = { Text(text = L.remindLabel, style = TimartType.titleSerif) },
            text = {
                Column {
                    Text(
                        text = L.remindDesc,
                        style = TimartType.caption,
                        color = InkSecondary,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    options.forEach { (days, label) ->
                        val selected = state.remindLeadDays == days
                        Text(
                            text = (if (selected) "●  " else "○  ") + label,
                            style = TimartType.body,
                            color = if (selected) TimeGold else InkPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.setRemindLeadDays(days)
                                    if (days == null) showRemindDialog = false
                                }
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRemindDialog = false }) {
                    Text(text = L.confirm)
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
            hintText = state.pwHint,
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
