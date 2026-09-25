package com.muxiao.timart.ui.cosmic

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.muxiao.timart.ui.components.visual.SectionHeader
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.rememberWindowAdaptive

/**
 * 首页时轨（架构 §2.14）：
 * Serif 页眉「时轨」+ 叙事文案 + TimeTrackCanvas（拖拽/缩放手势）+ 底部预览卡
 * （第 1 页「最近的一颗」，第 2 页「即将达成的一颗」，左右滑动切换）+ 右下「＋ 新建」
 * 聚合尘核入口；进页仅 300ms 尘粒复位，ON_PAUSE 背景暂停。
 * 空库显示引导文案；UNSEAL 待点击球点击进详情（首页不播完整解锁高潮）。
 */
@Composable
fun HomeScreen(
    container: AppContainer,
    pageActive: Boolean,
    onOpenDetail: (capsuleId: String, firstUnlock: Boolean) -> Unit,
    onCreate: () -> Unit,
) {
    val L = LocalStrings.current
    val vm: HomeViewModel = viewModel { HomeViewModel(container) }
    val adaptive = rememberWindowAdaptive()
    val capsules by vm.capsules.collectAsStateWithLifecycle()
    val unsealed by vm.unsealedIds.collectAsStateWithLifecycle()
    val pending by vm.pendingIds.collectAsStateWithLifecycle()
    val readMarks by vm.readIds.collectAsStateWithLifecycle()
    val previews by vm.previews.collectAsStateWithLifecycle()
    val satisfaction by vm.satisfaction.collectAsStateWithLifecycle()
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

    // 导航离场不做预清场：循环尘带 OWNER_HOME 归属标记，引擎 draw 只绘给宿主画布，
    // 转场重叠期不会在新页面留残影（ParticleCanvas owner 过滤）——尘随本页淡出自然收场，
    // 离场后由 onDispose 统一清场。若在此提前 setState(IDLE)，退场动画开头尘会瞬间消失。

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

            // 叙事行仅在竖向充裕时显示：横屏（高度紧凑）收起，给时轨画布让竖向空间
            // （轨道是圆形，半径受画布高度限制，见 TimeTrackLayout）
            if (!adaptive.isCompactHeight) {
                Text(
                    text = L.homeNarrative,
                    style = TimartType.body,
                    color = InkSecondary,
                    modifier = Modifier.padding(top = 14.dp),
                )
                // 那年今日（N8）：历年同日封存的轻提示，点击直达详情；纯本地日期匹配，无推送打扰
                val memory by vm.todayMemory.collectAsStateWithLifecycle()
                memory?.let { m ->
                    Text(
                        text = L.homeMemoryFmt.format(m.yearsAgo, m.title),
                        style = TimartType.caption,
                        color = TimeGold.copy(alpha = 0.85f),
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clickable(onClick = { onOpenDetail(m.capsuleId, false) }),
                    )
                }
            }

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
                    readIds = readMarks,
                    satisfactionRatios = satisfaction,
                    parallax = parallax,
                    tier = container.particleTier,
                    onTransform = { panDelta, zoomDelta ->
                        zoom = TimeTrackLayout.clampZoom(zoom * zoomDelta)
                        pan += panDelta
                    },
                    onCapsuleTap = { id, firstUnlock ->
                        focusId = id
                        if (firstUnlock) vm.consumeUnsealed(id)
                        // 拾起收束已迁至详情页侧（LockedStateView 入口脉冲）：在真实尘核位置
                        // 完整播放，不再被 home 画布 detach 的 releaseAll 在转场半途处决
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

            // 底部预览卡（第 1 页「最近的一颗」，第 2 页「即将达成的一颗」，左右滑动切换，
            // 页点指示在卡下方居中）与「新建」入口同行。
            // 注意：Pager 不支持 intrinsic 测量，此行不能用 IntrinsicSize.Min 定高——
            // 改为行高随预览卡内容自适应（pager 页面包裹内容），新建入口自身内容定高、纵向居中
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
            ) {
                when {
                    previews.size > 1 -> {
                        val pagerState = rememberPagerState(pageCount = { previews.size })
                        Column(modifier = Modifier.weight(1f)) {
                            HorizontalPager(
                                state = pagerState,
                                pageSpacing = 12.dp,
                                modifier = Modifier.fillMaxWidth(),
                            ) { page ->
                                val preview = previews.getOrNull(page) ?: return@HorizontalPager
                                HomePreviewCard(
                                    preview = preview,
                                    onView = { onOpenDetail(preview.id, preview.unlocked && preview.id in unsealed) },
                                )
                            }
                            // 页点指示（卡外下方居中）：多页时必显示，明确「还有一页可滑」
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                            ) {
                                repeat(previews.size) { index ->
                                    val active = index == pagerState.currentPage
                                    val dotSize by animateDpAsState(if (active) 7.dp else 5.dp, label = "previewDotSize")
                                    val dotColor by animateColorAsState(
                                        if (active) TimeGold else InkSecondary.copy(alpha = 0.35f),
                                        label = "previewDotColor",
                                    )
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 3.dp)
                                            .size(dotSize)
                                            .clip(CircleShape)
                                            .background(dotColor),
                                    )
                                }
                            }
                        }
                    }

                    previews.isNotEmpty() -> {
                        // 单页：不挂 Pager——单页 Pager 仍会消费横向拖动（回弹无效果），
                        // 观感即「滑不动」；不挂后卡片上的左右滑动交还主 Tab 翻页
                        val preview = previews[0]
                        HomePreviewCard(
                            preview = preview,
                            onView = { onOpenDetail(preview.id, preview.unlocked && preview.id in unsealed) },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    else -> Spacer(modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.width(12.dp))
                HomeCreateEntry(
                    onCreate = onCreate,
                    modifier = Modifier.width(88.dp),
                )
            }
        }
    }
}
