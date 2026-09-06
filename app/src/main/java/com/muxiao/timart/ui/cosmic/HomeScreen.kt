package com.muxiao.timart.ui.cosmic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muxiao.timart.AppContainer
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.components.particle.MotionState
import com.muxiao.timart.ui.components.particle.ParticleCanvas
import com.muxiao.timart.ui.components.particle.ParticleEngine
import com.muxiao.timart.ui.components.particle.ParticlePreset
import com.muxiao.timart.ui.components.visual.SectionHeader
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.TimartType

/**
 * 首页时轨（架构 §2.14）：
 * Serif 页眉「时轨」+ 叙事文案 + TimeTrackCanvas（拖拽/缩放手势）+ 底部「最近的一颗」
 * 预览卡 + 右下「＋ 新建」聚合尘核入口；进页仅 300ms 尘粒复位，ON_PAUSE 背景暂停。
 * 空库显示引导文案；UNSEAL 待点击球点击进详情（首页不播完整解锁高潮）。
 */
@Composable
fun HomeScreen(
    container: AppContainer,
    pageActive: Boolean,
    /** 导航级激活态：离开本目的地（进创建/详情等）的瞬间为 false，背景粒子立即销毁（不等退场动画后的 onDispose） */
    navActive: Boolean = true,
    onOpenDetail: (capsuleId: String, firstUnlock: Boolean) -> Unit,
    onCreate: () -> Unit,
) {
    val L = LocalStrings.current
    val vm: HomeViewModel = viewModel { HomeViewModel(container) }
    val capsules by vm.capsules.collectAsStateWithLifecycle()
    val unsealed by vm.unsealedIds.collectAsStateWithLifecycle()
    val pending by vm.pendingIds.collectAsStateWithLifecycle()
    val latest by vm.latest.collectAsStateWithLifecycle()
    val engine = container.particleEngine

    // ON_RESUME 重入计数：从详情返回等场景（组合未重建）下重同步 BREATHE 状态与锚点
    var resumeTick by remember { mutableIntStateOf(0) }

    // BREATHE 归属接线：仅当本页是当前 pager 页（pageActive）且库非空时持有内流。
    // 离开本页立即交还（否则共享引擎会按残留锚点持续重生星屑，滞留到其他页面的画布上）
    // 背景尘归属权令牌：只有本页能关掉自己设置的背景（多页共享引擎，detach 不再越权清开关）
    val backgroundOwner = remember { Any() }

    LaunchedEffect(capsules.isNotEmpty(), pageActive, resumeTick) {
        if (pageActive && capsules.isNotEmpty()) {
            engine.setState(MotionState.BREATHE, ParticleEngine.OWNER_HOME)
        } else {
            engine.setState(MotionState.IDLE)
        }
    }

    // 导航离开本页（进创建/详情/口令页）的瞬间即清背景与循环粒子：
    // NavHost 退场动画期间本页仍组合 ~180ms，onDispose 要等动画结束才触发，
    // 不在这里先清的话，新页面出现的头一两百毫秒会看到 Home 的背景尘残影
    LaunchedEffect(navActive) {
        if (!navActive) {
            engine.setBackground(false, backgroundOwner)
            engine.setState(MotionState.IDLE)
        }
    }

    var zoom by remember { mutableFloatStateOf(1f) }

    // 胶囊增多时自动收拢：最外轨超出画布则把 zoom 压到刚好全量可见（只缩不放，手动缩放仍自由）
    LaunchedEffect(capsules.size) {
        val fit = TimeTrackLayout.fitZoom(capsules.size)
        if (zoom > fit) zoom = fit
    }

    var pan by remember { mutableStateOf(Offset.Zero) }
    var focusId by remember { mutableStateOf<String?>(null) }

    // 陀螺仪视差（设置页开关；页面重入组合时按最新开关值决定是否监听）
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val parallax = remember { com.muxiao.timart.utils.sensor.ParallaxSensor(context) }

    // 生命周期：进页 300ms 尘粒复位 + 背景开启；ON_PAUSE 背景暂停；离页清空 BREATHE 归属
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, engine, pageActive) {
        val startParallax = { if (com.muxiao.timart.utils.RuntimeSettings.gyroEnabled && pageActive) parallax.start() }
        startParallax()
        engine.resume()
        engine.setBackground(pageActive, backgroundOwner)
        vm.onScreenResumed()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    startParallax()
                    engine.setBackground(pageActive, backgroundOwner)
                    resumeTick++
                    vm.onScreenResumed()
                }

                Lifecycle.Event.ON_PAUSE -> {
                    parallax.stop()
                    engine.setBackground(false, backgroundOwner)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            parallax.stop()
            engine.setBackground(false, backgroundOwner)
            // 交还 BREATHE 归属：清空循环尘，星屑不再滞留到其他 pager 页
            engine.setState(MotionState.IDLE)
        }
    }

    // 粒子画布原点（窗口根坐标）：引擎锚点在 ParticleCanvas 画布局部坐标系渲染，
    // 时轨画布锚点换算需要本原点（否则状态栏 + 页眉高度会被误计成整体偏移）
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepCharcoal),
    ) {
        // 背景漂移尘层（全屏，最底层；宿主页：循环尘只在此画布渲染）
        ParticleCanvas(
            engine = engine,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    overlayOrigin = coords.localToRoot(Offset.Zero)
                },
            owner = ParticleEngine.OWNER_HOME,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            SectionHeader(title = L.tabTimeTrack, note = "HOME")

            Text(
                text = L.homeNarrative,
                style = TimartType.body,
                color = InkSecondary,
                modifier = Modifier.padding(top = 14.dp),
            )

            // 时轨画布（占满剩余空间；空库引导文案叠加居中）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                TimeTrackCanvas(
                    capsules = capsules,
                    engine = engine,
                    zoom = zoom,
                    pan = pan,
                    focusId = focusId,
                    unsealedIds = unsealed,
                    pendingIds = pending,
                    parallax = parallax,
                    tier = container.particleTier,
                    onTransform = { panDelta, zoomDelta ->
                        zoom = TimeTrackLayout.clampZoom(zoom * zoomDelta)
                        pan += panDelta
                    },
                    onCapsuleTap = { id, firstUnlock, ax, ay ->
                        focusId = id
                        if (firstUnlock) vm.consumeUnsealed(id)
                        // 拾起粒子：胶囊被点开瞬间向心聚合一次（PENDING 预算复用）
                        engine.fire(
                            ParticlePreset.PENDING,
                            ax,
                            ay,
                            with(density) { 40.dp.toPx() },
                            ParticleEngine.TIME_GOLD,
                        )
                        onOpenDetail(id, firstUnlock)
                    },
                    // 长按拖拽球体：松手持久化自定义星图坐标（updateLayout）
                    onLayoutChange = { id, nx, ny, finished ->
                        if (finished) vm.saveLayout(id, nx, ny)
                    },
                    resync = resumeTick,
                    overlayOrigin = overlayOrigin,
                    modifier = Modifier.fillMaxSize(),
                )
                if (capsules.isEmpty()) {
                    Text(
                        text = L.homeEmpty,
                        style = TimartType.body,
                        color = InkSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            // 底部「最近的一颗」预览卡与「新建」入口同行（预览占主宽，新建等高成组）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(bottom = 10.dp),
            ) {
                if (latest != null) {
                    val preview = latest!!
                    HomePreviewCard(
                        preview = preview,
                        onView = { onOpenDetail(preview.id, preview.unlocked && preview.id in unsealed) },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.width(12.dp))
                HomeCreateEntry(
                    onCreate = onCreate,
                    modifier = Modifier
                        .width(88.dp)
                        .fillMaxHeight(),
                )
            }
        }
    }
}
