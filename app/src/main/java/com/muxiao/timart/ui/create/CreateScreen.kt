package com.muxiao.timart.ui.create

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartType
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muxiao.timart.AppContainer
import com.muxiao.timart.ui.components.particle.ParticleCanvas
import com.muxiao.timart.ui.create.rules.RulesStep
import com.muxiao.timart.ui.create.seal.AssembleOverlay
import com.muxiao.timart.ui.create.seal.SealStep
import com.muxiao.timart.ui.create.write.WriteStep
import com.muxiao.timart.ui.theme.DeepCharcoal
import kotlin.time.Duration.Companion.milliseconds

/** 步进转场：方向性水平位移（前进左移 / 后退右移）+ 淡入（280/180ms，统一走 TimartMotion，禁 spring） */
private const val ENTER_MILLIS = com.muxiao.timart.ui.theme.TimartMotion.ENTER_MILLIS
private const val EXIT_MILLIS = com.muxiao.timart.ui.theme.TimartMotion.EXIT_MILLIS

/**
 * 三步封存容器（架构 §2.15）：写下 → 开启方式 → 确认封存，步内 AnimatedContent 转场。
 * - 草稿三步共享状态由 [CreateViewModel] 持有；
 * - 首次无口令：submit 回调 [onNeedPasswordSetup] 导航 PASSWORD_SETUP，
 *   返回（ON_RESUME）后 vm.onPasswordReady() 续跑封存；
 * - 落库成功 → ASSEMBLE 全屏 overlay（1.5s 兜底）→ 完成后 [onBack] 返回时轨。
 */
@Composable
fun CreateScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onNeedPasswordSetup: () -> Unit,
) {
    val vm: CreateViewModel = viewModel { CreateViewModel(container) }
    val engine = container.particleEngine
    val step by vm.step.collectAsStateWithLifecycle()
    val assembling by vm.assembling.collectAsStateWithLifecycle()

    // 输入飘粒：全屏粒子画布 + 光标坐标发射（画布与内容同根，坐标经 localToRoot 对齐）
    var sparkCanvasOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val fireInputSpark: (Float, Float) -> Unit = { x, y ->
        if (com.muxiao.timart.utils.RuntimeSettings.inputSparkEnabled) {
            engine.fire(
                com.muxiao.timart.ui.components.particle.ParticlePreset.INPUT_SPARK,
                x - sparkCanvasOrigin.x,
                y - sparkCanvasOrigin.y,
                0f,
                com.muxiao.timart.ui.components.particle.ParticleEngine.GLOW_GOLD,
            )
        }
    }

    // 城市码表预热（首次进入时冷加载 assets）
    androidx.compose.runtime.LaunchedEffect(Unit) {
        vm.preloadCities()
    }

    // 从 PASSWORD_SETUP 返回后续跑封存（VM 内 pendingAfterPassword 幂等守卫）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.onPasswordReady()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .background(DeepCharcoal),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 页级返回（仅第 1 步显示：后两步已有步内「‹ 上一步」；系统返回手势随时可退出）。
            // 进场样式：页面主体落位（~200ms）后单独淡入，不与路由转场抢戏
            val L = LocalStrings.current
            if (step == 0 && !assembling) {
                var backVisible by remember { mutableStateOf(false) }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(200.milliseconds)
                    backVisible = true
                }
                AnimatedVisibility(
                    visible = backVisible,
                    enter = fadeIn(tween(com.muxiao.timart.ui.theme.TimartMotion.CONTENT_MILLIS, easing = LinearOutSlowInEasing)),
                ) {
                    TextButton(
                        onClick = onBack,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = com.muxiao.timart.ui.theme.InkSecondary,
                        ),
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        Text(
                            text = L.exitCreate,
                            style = TimartType.caption,
                        )
                    }
                }
            }
            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    // 方向感：前进左移入 / 后退右移入（轻微位移 1/6 宽 + 淡入），时长仍 280/180ms
                    val forward = targetState > initialState
                    val enterOffset = if (forward) 1 else -1
                    val exitOffset = if (forward) -1 else 1
                    (slideInHorizontally(
                        animationSpec = tween(ENTER_MILLIS, easing = LinearOutSlowInEasing),
                        initialOffsetX = { enterOffset * it / 6 },
                    ) + fadeIn(tween(ENTER_MILLIS, easing = LinearOutSlowInEasing)))
                        .togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(EXIT_MILLIS, easing = LinearOutSlowInEasing),
                                targetOffsetX = { exitOffset * it / 8 },
                            ) + fadeOut(tween(EXIT_MILLIS)),
                        )
                },
                label = "CreateStepTransition",
            ) { currentStep ->
                when (currentStep) {
                    0 -> WriteStep(
                        vm = vm,
                        onSpark = fireInputSpark,
                        onNext = vm::next,
                    )

                    1 -> RulesStep(
                        vm = vm,
                        geocodeResolver = container.geocodeResolver,
                        onNext = vm::next,
                        onBack = vm::back,
                    )

                    else -> SealStep(
                        vm = vm,
                        onBack = vm::back,
                        onNeedPasswordSetup = onNeedPasswordSetup,
                    )
                }
            }
        }

        // 输入飘粒画布层（无输入事件，覆盖于步骤内容之上；ASSEMBLE 序列期间让位给专用画布）
        if (!assembling) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { sparkCanvasOrigin = it.localToRoot(androidx.compose.ui.geometry.Offset.Zero) },
            ) {
                ParticleCanvas(engine = engine, modifier = Modifier.fillMaxSize())
            }
        }
    }

    // ASSEMBLE 全屏 overlay：保存已完成，动画结束（或兜底）后返回时轨。
    // 分片胶囊例外：动画结束后先揭示一次性分片串，确认已保存才离开（见 CreateViewModel.onAssembleFinished）
    if (assembling) {
        AssembleOverlay(
            engine = engine,
            onFinished = {
                vm.onAssembleFinished()
                if (!vm.showShardShares) onBack()
            },
        )
    }

    // 分片串一次性揭示弹层（体验储备池 §7.1）：设备不留份额，确认后不可再看
    if (vm.showShardShares) {
        ShardSharesDialog(
            shares = vm.shardShares,
            onDone = {
                vm.consumeShardShares()
                onBack()
            },
        )
    }
}

/** 分片串揭示弹层：SelectionContainer 让持有人可以长按选择逐份复制/转发 */
@Composable
private fun ShardSharesDialog(
    shares: List<String>,
    onDone: () -> Unit,
) {
    val L = LocalStrings.current
    AlertDialog(
        onDismissRequest = {},
        containerColor = SurfaceRaise,
        title = { Text(text = L.shardSharesTitle, style = TimartType.titleSerif) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = L.shardSharesNote,
                    style = TimartType.caption,
                    color = TimeGold,
                )
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Column {
                        shares.forEachIndexed { index, share ->
                            Text(
                                text = "${index + 1}. $share",
                                style = TimartType.caption.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                ),
                                color = InkPrimary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDone,
                colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
            ) {
                Text(text = L.shardShareDone)
            }
        },
    )
}
