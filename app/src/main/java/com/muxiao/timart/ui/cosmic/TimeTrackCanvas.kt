package com.muxiao.timart.ui.cosmic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.ui.components.particle.GlowPainter
import com.muxiao.timart.ui.components.particle.ParticleEngine
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TrackHairline
import com.muxiao.timart.utils.sensor.ParallaxSensor
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 时轨画布（架构 §2.14）：手势（双指捏合缩放 0.6×–2.5× + 双指平移 / 长按拖拽球体自定义星图坐标）
 * + 绘制编排。绘制层次：星云底光 → 星野（远层）→ 轨道细线 → 球体（光晕 + 球核 + 环带）→ 标题。
 *
 * 视差（陀螺仪，设置页开关）：星野 / 星云 / 球体三层各按不同幅度随设备倾斜偏移，
 * 形成纵深（远层反向、球体层同向且幅度最大）；绘制相位延迟读取 [ParallaxSensor.tilt]，
 * 视差变化只触发重绘不触发重组。
 *
 * 手势与主页面滑动切换的分工：单指拖动不消费（让 HorizontalPager 完成翻页），
 * 仅双指捏合/平移被画布消费；长按拖拽球体在长按触发后照常接管。
 *
 * 粒子层由共享 [ParticleEngine]（ParticleCanvas 背景层 + BREATHE 锚点）承担，
 * 本画布每帧同步锚点屏幕坐标与状态色（预分配通道，零分配）。
 * 锚点坐标系约定：引擎在宿主 ParticleCanvas 的**画布局部坐标系**绘制，
 * 因此锚点 = 本画布局部坐标 + 本画布相对粒子画布的原点差
 * （rootOrigin − overlayOrigin；此前直接加 rootOrigin 会把状态栏与页眉高度
 * 误计成整体偏移，粒子相对胶囊错位）。
 */
@Composable
fun TimeTrackCanvas(
    capsules: List<Capsule>,
    engine: ParticleEngine,
    zoom: Float,
    pan: Offset,
    focusId: String?,
    unsealedIds: Set<String>,
    pendingIds: Set<String>,
    modifier: Modifier = Modifier,
    /** 已开启过（读过内容，meta `capsule.read.*`）的胶囊 id 集：已读金球做余温降档（亮度/环带/尘埃全弱化） */
    readIds: Set<String> = emptySet(),

    /**
     * 锁定胶囊「满足比」快照（体验储备池 §2 成熟度渐变）：
     * 满足条件占比 0..1，锁定球色从尘埃灰向暖金过渡（上限压到 0.45，
     * 与「即将达成」PENDING 的金色渐变段区分开——成熟只给温度，不冒充可开启）。
     */
    satisfactionRatios: Map<String, Float> = emptyMap(),
    onTransform: (panDelta: Offset, zoomDelta: Float) -> Unit,
    onCapsuleTap: (id: String, firstUnlock: Boolean, anchorX: Float, anchorY: Float) -> Unit,
    onLayoutChange: (id: String, nx: Float, ny: Float, finished: Boolean) -> Unit,
    parallax: ParallaxSensor? = null,
    tier: com.muxiao.timart.domain.model.AnimationTier = com.muxiao.timart.domain.model.AnimationTier.HIGH,

    /** 重同步触发计数（上层 ON_RESUME 重入时 +1：共享引擎的锚点可能被详情页改写，需重写回本页布局） */
    resync: Int = 0,

    /** 粒子画布（宿主 ParticleCanvas）在窗口根坐标中的原点：换算画布局部锚点用 */
    overlayOrigin: Offset = Offset.Zero,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    // 画布原点（根坐标）：引擎粒子在 ParticleCanvas（全屏浮层）坐标系渲染，
    // 锚点若直接用画布局部坐标会整体错位一个页眉高度，同步时必须加上本原点
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }

    // 视差幅度（px）：球体层最大（近景）、星野反向（远景）、星云居中
    val orbParallaxPx = with(density) { ORB_PARALLAX_DP.toPx() }
    val starParallaxPx = with(density) { STAR_PARALLAX_DP.toPx() }
    val nebulaParallaxPx = with(density) { NEBULA_PARALLAX_DP.toPx() }

    // ---- 星野（远层静态尘星 + 微闪烁；种子固定，随画布与档位生成） ----
    // 档位缩放：高 110 / 中 60 / 低 0（低档完全静态，与引擎背景尘预算同向）
    val starCount = when (tier) {
        com.muxiao.timart.domain.model.AnimationTier.HIGH -> STAR_COUNT
        com.muxiao.timart.domain.model.AnimationTier.MEDIUM -> STAR_COUNT * 6 / 11
        com.muxiao.timart.domain.model.AnimationTier.LOW -> 0
    }
    val starfield = remember(starCount) {
        FloatArray(starCount * STAR_STRIDE).also { a ->
            val rnd = java.util.Random(STAR_SEED)
            for (i in 0 until starCount) {
                a[i * STAR_STRIDE] = rnd.nextFloat()            // 归一化 x
                a[i * STAR_STRIDE + 1] = rnd.nextFloat()        // 归一化 y
                a[i * STAR_STRIDE + 2] = 0.6f + rnd.nextFloat() * 1.4f // 半径基准（px × density）
                a[i * STAR_STRIDE + 3] = rnd.nextFloat() * 6.2832f     // 闪烁相位
                a[i * STAR_STRIDE + 4] = 0.3f + rnd.nextFloat() * 0.7f // 闪烁速率
            }
        }
    }

    // ---- 长按拖拽（自定义星图坐标） ----
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragFinger by remember { mutableStateOf(Offset.Zero) } // 手指屏幕坐标

    // ---- 帧时钟（draw 相位读状态，只触发重绘不触发重组）----
    // 漂浮 / 星野闪烁 / 卫星公转都依赖每帧变化的 nowSec；没有它画布画完首帧即静止。
    // 节流到 ≥30fps：慢速氛围运动半帧率即可（120Hz 屏上省 ~75% 画布重绘），
    // 手势（平移/缩放/拖拽）仍由自身状态变化驱动全帧率重绘
    val frameTick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frame ->
                if (frame - frameTick.longValue >= 33_000_000L) frameTick.longValue = frame
            }
        }
    }

    // 标题排版缓存：TextMeasurer.measure 结果按标题复用——
    // 若每帧以带颜色的 style 调 drawText，alpha 变化会让布局缓存全 miss，
    // 每帧每球重新排版（多球时是时轨最大的卡顿源）；绘制时只传 color/alpha
    val titleLayouts = remember { mutableMapOf<String, androidx.compose.ui.text.TextLayoutResult>() }
    val titleStyle = TextStyle(fontSize = 12.sp)

    // A5：id → Capsule 查找表。绘制相位与锚点同步（视差开启时以传感器频率触发）
    // 原先对每球做 capsules.find 线性扫描（O(n²)），改 O(1) 取
    val capsulesById = remember(capsules) { capsules.associateBy { it.id } }

    // 星座连线（体验储备池 §2）：依赖边 child.dependCapsuleId → 前置球，两端都在图上才成线；
    // 锁定段为暗金细线，child 解锁后整段点亮（解锁一颗点亮一段）
    val dependencyEdges = remember(capsules) {
        capsules.filter { it.state != CapsuleState.DESTROYED && it.dependCapsuleId != null }
            .map { it.id to it.dependCapsuleId!! }
    }

    // 盲盒遮蔽题（组合期取词，随语言即时重组；遮蔽规则与预览卡一致）
    val blindMaskTitle = com.muxiao.timart.l10n.LocalStrings.current.blindMaskTitle

    // A8：绘制期常量提升——Stroke 对象与 dp→px 换算不再每帧每球重建/重算
    val ringGoldPadPx = remember(density) { with(density) { 7.dp.toPx() } }
    val ringLockedPadPx = remember(density) { with(density) { 4.dp.toPx() } }
    val moteBasePx = remember(density) { with(density) { 6.dp.toPx() } }
    val moteStepPx = remember(density) { with(density) { 3.dp.toPx() } }
    val moteSizePx = remember(density) { with(density) { 2.2.dp.toPx() } }
    val titleGapPx = remember(density) { with(density) { 6.dp.toPx() } }

    // 布局计算（星图坐标系：画布中心为原点参考，已含 zoom）
    val baseRadiusPx = if (canvasSize == IntSize.Zero) {
        0f
    } else {
        minOf(canvasSize.width, canvasSize.height) / 2f - with(density) { 24.dp.toPx() }
    }
    val layout = remember(capsules, canvasSize, zoom, focusId, dragId, dragFinger, blindMaskTitle) {
        if (canvasSize == IntSize.Zero || baseRadiusPx <= 0f) {
            TimeTrackLayout.TrackLayoutResult(emptyList(), emptyList())
        } else {
            val result = TimeTrackLayout.layout(
                items = capsules
                    .filter { it.state != CapsuleState.DESTROYED }
                    .map {
                        TimeTrackLayout.TrackItem(
                            it.id,
                            // 盲盒封存且仍锁定：球旁标题遮蔽（与预览卡同规则，解锁后恢复）
                            if (it.blindBox && it.state == CapsuleState.LOCKED) blindMaskTitle else it.title,
                            it.createTimestamp,
                            it.layoutX,
                            it.layoutY,
                        )
                    },
                centerX = canvasSize.width / 2f,
                centerY = canvasSize.height / 2f,
                baseRadius = baseRadiusPx,
                zoom = zoom,
                focusId = focusId,
            )
            // 拖拽中的球体覆盖为手指位置（星图空间），即时反馈
            val id = dragId
            if (id != null) {
                val shift = orbShift(parallax, orbParallaxPx)
                TimeTrackLayout.TrackLayoutResult(
                    capsules = result.capsules.map {
                        if (it.id == id) it.copy(x = dragFinger.x - pan.x - shift.x, y = dragFinger.y - pan.y - shift.y) else it
                    },
                    orbitRadii = result.orbitRadii,
                )
            } else {
                result
            }
        }
    }

    // 星座连线端点查找（draw 相位零分配：随 layout 记忆，不每帧重建）
    val placedById = remember(layout) { layout.capsules.associateBy { it.id } }

    /** 屏幕坐标 → 归一化星图坐标（±1.5 钳制，与 TimeTrackLayout 自定义覆盖约定一致） */
    fun normalized(screen: Offset): Pair<Float, Float> {
        val shift = orbShift(parallax, orbParallaxPx)
        val denom = (baseRadiusPx * TimeTrackLayout.clampZoom(zoom)).coerceAtLeast(1f)
        val nx = ((screen.x - pan.x - shift.x - canvasSize.width / 2f) / denom).coerceIn(-1.5f, 1.5f)
        val ny = ((screen.y - pan.y - shift.y - canvasSize.height / 2f) / denom).coerceIn(-1.5f, 1.5f)
        return nx to ny
    }

    // 引擎锚点同步（BREATHE 内流粒子跟随球体；视差倾斜实时重同步）。
    // 锚点用「本画布相对粒子画布的原点差」换算成粒子画布局部坐标：
    // delta = rootOrigin − overlayOrigin（状态栏 + 页眉高度此前被误计进偏移）
    LaunchedEffect(layout, pan, engine, parallax, rootOrigin, overlayOrigin, resync) {
        fun syncAnchors() {
            val shift = orbShift(parallax, orbParallaxPx)
            val dx = rootOrigin.x - overlayOrigin.x
            val dy = rootOrigin.y - overlayOrigin.y
            engine.setOrbCount(layout.capsules.size)
            layout.capsules.forEachIndexed { i, p ->
                val capsuleState = capsulesById[p.id]?.state ?: CapsuleState.LOCKED
                engine.setOrbAnchor(
                    index = i,
                    x = dx + p.x + pan.x + shift.x,
                    y = dy + p.y + pan.y + shift.y,
                    radius = orbitDotRadius(p.orbitRadius, zoom, density),
                    colorArgb = dustWhitened(
                        when {
                            p.id in unsealedIds -> ParticleEngine.TIME_GOLD
                            // 已读金球：环绕尘埃随余温降档（同色压暗，见 ReadGoldDraw）
                            capsuleState == CapsuleState.UNLOCKED && p.id in readIds -> ReadGoldArgb
                            // 锁定非 PENDING：尘埃随成熟度向暖金过渡（与球核同源取色）
                            capsuleState == CapsuleState.LOCKED && p.id !in pendingIds ->
                                maturityColor(p.id, satisfactionRatios).toArgb()
                            else -> capsuleState.anchorColorArgb(pending = p.id in pendingIds)
                        },
                    ),
                )
            }
        }
        syncAnchors()
        if (parallax != null) {
            snapshotFlow { parallax.tilt }.collect { syncAnchors() }
        }
    }

    // 条件新满足 → PENDING 向心单发（在该球锚点；300–600ms，不持续）
    var firedPending by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(pendingIds, layout, pan, rootOrigin, overlayOrigin) {
        val shift = orbShift(parallax, orbParallaxPx)
        val dx = rootOrigin.x - overlayOrigin.x
        val dy = rootOrigin.y - overlayOrigin.y
        pendingIds.filter { it !in firedPending }.forEach { id ->
            val placed = layout.capsules.firstOrNull { it.id == id } ?: return@forEach
            engine.fire(
                com.muxiao.timart.ui.components.particle.ParticlePreset.PENDING,
                dx + placed.x + pan.x + shift.x,
                dy + placed.y + pan.y + shift.y,
                orbitDotRadius(placed.orbitRadius, zoom, density),
                ParticleEngine.TIME_GOLD,
            )
        }
        firedPending = pendingIds
    }

    fun hitTest(position: Offset): PlacedHit? {
        val shift = orbShift(parallax, orbParallaxPx)
        val now = System.nanoTime() / 1_000_000_000.0
        val p = position - pan - shift
        var best: PlacedHit? = null
        for (c in layout.capsules) {
            // 命中测试与绘制共用同一漂浮偏移，保证点得准
            val dy = c.y + orbBobY(c.id, dragId, now, density)
            val dx = p.x - c.x
            val dyy = p.y - dy
            val d = sqrt(dx * dx + dyy * dyy)
            val touch = orbitDotRadius(c.orbitRadius, zoom, density) + with(density) { 16.dp.toPx() }
            if (d <= touch && (best == null || d < best.dist)) {
                best = PlacedHit(c.id, d, c.x, dy)
            }
        }
        return best
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(layout, pan, zoom) {
                detectTapGestures { position ->
                    hitTest(position)?.let { hit ->
                        // 锚点回调换算到粒子画布局部坐标（上层 engine.fire 打在粒子画布上）
                        val dx = rootOrigin.x - overlayOrigin.x
                        val dy = rootOrigin.y - overlayOrigin.y
                        onCapsuleTap(hit.id, hit.id in unsealedIds, hit.cx + dx, hit.cy + dy)
                    }
                }
            }
            .pointerInput(layout, pan, zoom) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { position ->
                        hitTest(position)?.let { hit ->
                            dragId = hit.id
                            dragFinger = position
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val id = dragId ?: return@detectDragGesturesAfterLongPress
                        dragFinger += dragAmount
                        val (nx, ny) = normalized(dragFinger)
                        onLayoutChange(id, nx, ny, false)
                    },
                    onDragEnd = {
                        val id = dragId ?: return@detectDragGesturesAfterLongPress
                        val (nx, ny) = normalized(dragFinger)
                        onLayoutChange(id, nx, ny, true)
                        dragId = null
                    },
                    onDragCancel = { dragId = null },
                )
            }
            // 双指捏合缩放 / 平移（Initial 相位消费，优先级高于页面翻页）；单指让位给 pager
            .pointerInput(onTransform) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                onTransform(panChange, zoomChange)
                                event.changes.forEach { if (it.pressed) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .onSizeChanged { canvasSize = it }
            .onGloballyPositioned { coords ->
                rootOrigin = coords.localToRoot(Offset.Zero)
            },
    ) {
        // 视差偏移（draw 相位读取：只重绘，不重组）
        val tilt = parallax?.tilt ?: Offset.Zero
        val orbShiftX = tilt.x * orbParallaxPx
        val orbShiftY = tilt.y * orbParallaxPx
        val starShiftX = -tilt.x * starParallaxPx
        val starShiftY = -tilt.y * starParallaxPx
        // 帧时钟（状态读取触发每帧重绘；漂浮/闪烁/公转共用）
        val nowSec = frameTick.longValue / 1_000_000_000.0

        // 星云底光（中心极轻暖光，给纯黑底一点纵深；GlowPainter 唯一光晕路径）
        drawIntoCanvas { canvas ->
            GlowPainter.drawGlow(
                canvas.nativeCanvas,
                size.width / 2f + tilt.x * nebulaParallaxPx,
                size.height * 0.46f + tilt.y * nebulaParallaxPx,
                baseRadiusPx.coerceAtLeast(1f) * 1.55f,
                NEBULA_COLOR,
                0.34f,
            )
        }

        // 星野（远层；闪烁微光，倾斜反向漂移）
        drawIntoCanvas { canvas ->
            for (i in 0 until starCount) {
                val base = i * STAR_STRIDE
                val twinkle = 0.5f + 0.5f * sin((nowSec * starfield[base + 4] + starfield[base + 3])).toFloat()
                val alpha = 0.14f + 0.30f * twinkle
                GlowPainter.drawDot(
                    canvas.nativeCanvas,
                    starfield[base] * size.width + starShiftX,
                    starfield[base + 1] * size.height + starShiftY,
                    starfield[base + 2],
                    if (i % 9 == 0) STAR_GOLD else STAR_INK,
                    alpha,
                )
            }
        }

        // 轨道细线 + 球体（星图坐标 + pan 平移 + 球体层视差；zoom 已计入 layout）
        withTransform({ translate(pan.x + orbShiftX, pan.y + orbShiftY) }) {
            layout.orbitRadii.forEach { r ->
                drawCircle(
                    color = TrackHairline,
                    radius = r,
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = TRACK_STROKE,
                )
            }
            // 星座连线（球体层之下）：依赖边 child → 前置球；锁定段暗金细线，
            // child 解锁（含 UNSEAL 待点击）后整段点亮——解锁一颗点亮一段
            dependencyEdges.forEach { (childId, prereqId) ->
                val child = placedById[childId] ?: return@forEach
                val prereq = placedById[prereqId] ?: return@forEach
                val lit = childId in unsealedIds ||
                    capsulesById[childId]?.state == CapsuleState.UNLOCKED
                drawLine(
                    color = if (lit) CONSTELLATION_LIT else CONSTELLATION_DIM,
                    start = Offset(child.x, child.y),
                    end = Offset(prereq.x, prereq.y),
                    strokeWidth = if (lit) 1.6f else 1.2f,
                )
            }
            layout.capsules.forEach { p ->
                val state = capsulesById[p.id]?.state ?: CapsuleState.LOCKED
                val isUnsealed = p.id in unsealedIds
                val isPending = p.id in pendingIds
                // 已读金球余温降档：金相保留（身份不变），亮度/光晕/环带/尘埃整体弱化——
                // 金球三档：UNSEAL 待点击（最亮）> 未读金球（满亮度，引导开启）> 已读（余温，不再催点）
                val isReadGold = !isUnsealed && state == CapsuleState.UNLOCKED && p.id in readIds
                val r = orbitDotRadius(p.orbitRadius, zoom, density)
                val coreColor = when {
                    isUnsealed -> TimeGold
                    state == CapsuleState.UNLOCKED -> if (isReadGold) ReadGoldDraw else TimeGold
                    isPending -> pendingColor(p.id)
                    // 成熟度渐变（体验储备池 §2）：锁定球随满足比从尘埃灰向暖金过渡（压在 PENDING 段之下）
                    else -> maturityColor(p.id, satisfactionRatios)
                }
                val isGolden = isUnsealed || state == CapsuleState.UNLOCKED
                // 漂浮（Floating Element）：每球慢速正弦上下起伏，拖拽中的球归零
                val bobY = orbBobY(p.id, dragId, nowSec, density)
                val py = p.y + bobY
                drawIntoCanvas { canvas ->
                    GlowPainter.drawGlow(
                        canvas.nativeCanvas,
                        p.x,
                        py,
                        r * if (isGolden && !isReadGold) 2.4f else 1.7f,
                        coreColor.toArgb(),
                        when {
                            isReadGold -> 0.22f
                            isGolden -> 0.5f
                            else -> 0.3f
                        },
                    )
                }
                // 微缩星球：暗边底盘 → 偏光内芯 → 左上高光弧（与 GlowOrb 同构）
                drawCircle(
                    color = androidx.compose.ui.graphics.lerp(coreColor, androidx.compose.ui.graphics.Color.Black, 0.32f),
                    radius = r,
                    center = Offset(p.x, py),
                )
                drawCircle(
                    color = coreColor,
                    radius = r * 0.85f,
                    center = Offset(p.x - r * 0.10f, py - r * 0.13f),
                )
                drawArc(
                    color = InkPrimary.copy(alpha = 0.4f),
                    startAngle = -150f,
                    sweepAngle = 55f,
                    useCenter = false,
                    topLeft = Offset(p.x - r * 0.8f, py - r * 0.8f),
                    size = androidx.compose.ui.geometry.Size(r * 1.6f, r * 1.6f),
                    style = ORB_HIGHLIGHT_STROKE,
                )
                // 环带：亮金环仅属于待开启/未读（引导信号）；已读降为暗金细环；未解锁暗色细环
                if (isGolden && !isReadGold) {
                    drawCircle(
                        color = TimeGold.copy(alpha = 0.32f),
                        radius = r + ringGoldPadPx,
                        center = Offset(p.x, py),
                        style = ORB_HIGHLIGHT_STROKE,
                    )
                } else {
                    drawCircle(
                        color = coreColor.copy(alpha = if (isReadGold) 0.28f else 0.4f),
                        radius = r + ringLockedPadPx,
                        center = Offset(p.x, py),
                        style = RING_LOCKED_STROKE,
                    )
                }
                val motePhase = (p.id.hashCode() and 0xFF) / 255f * 6.2832f
                val moteSpeed = when {
                    isReadGold -> 0.24f
                    isGolden -> 0.45f
                    else -> 0.28f
                }
                val moteColor = if (isGolden && !isReadGold) TimeGold else coreColor
                for (m in 0..1) {
                    val moteAngle = nowSec * moteSpeed + motePhase + m * 3.1416f
                    val moteR = r + moteBasePx + m * moteStepPx
                    drawIntoCanvas { canvas ->
                        GlowPainter.drawDot(
                            canvas.nativeCanvas,
                            p.x + (cos(moteAngle) * moteR).toFloat(),
                            py + (sin(moteAngle) * moteR).toFloat(),
                            moteSizePx,
                            moteColor.toArgb(),
                            when {
                                isReadGold -> 0.4f
                                isGolden -> 0.85f
                                else -> 0.65f
                            },
                        )
                    }
                }
                // 标题（焦点显隐 / 邻近渐进；随球体一同漂浮；排版走缓存）
                if (p.titleAlpha > 0.05f) {
                    val layout = titleLayouts.getOrPut(p.title) {
                        textMeasurer.measure(p.title, titleStyle)
                    }
                    if (titleLayouts.size > 96) titleLayouts.clear()
                    drawText(
                        layout,
                        color = InkPrimary,
                        alpha = p.titleAlpha,
                        topLeft = Offset(p.x - r, py + r + titleGapPx),
                    )
                }
            }
        }
    }
}

/** 球体层视差偏移（倾斜 × 幅度） */
private fun orbShift(parallax: ParallaxSensor?, parallaxPx: Float): Offset {
    val tilt = parallax?.tilt ?: return Offset.Zero
    return Offset(tilt.x * parallaxPx, tilt.y * parallaxPx)
}

/**
 * 内流尘点提白：尘点与球核同为状态色时对比不足（金球上的金尘几乎不可见），
 * 向暖白（InkPrimary 同值）混色 75% 让尘点读作「星屑」；保留 25% 状态色微弱区分冷暖
 */
private fun dustWhitened(colorArgb: Int): Int =
    androidx.compose.ui.graphics.lerp(
        androidx.compose.ui.graphics.Color(colorArgb),
        InkPrimary,
        0.75f,
    ).toArgb()

/** 漂浮（Floating Element）：每球慢速正弦上下起伏，相位/幅度由 id 种子决定；拖拽中归零 */
private fun orbBobY(id: String, dragId: String?, nowSec: Double, density: androidx.compose.ui.unit.Density): Float {
    if (id == dragId) return 0f
    val seed = ((id.hashCode() shr 8) and 0xFF) / 255f
    val ampDp = 2f + seed * 2f
    val phase = seed * 6.2832
    val speed = 0.55 + seed * 0.5
    return (sin(nowSec * speed + phase) * ampDp * with(density) { 1.dp.toPx() }).toFloat()
}

private data class PlacedHit(val id: String, val dist: Float, val cx: Float, val cy: Float)

/** 球半径随轨道微缩（外圈略小，视觉聚拢），并随 zoom（≤1）等比联动：
 *  自动收拢/捏合缩小时轨道与球整体等比缩放，圈距压缩不致球体压叠；zoom ≥1 保持原大小 */
private fun orbitDotRadius(orbitRadius: Float, zoom: Float, density: androidx.compose.ui.unit.Density): Float =
    with(density) { ((18 - (orbitRadius / 220f).toInt().coerceIn(0, 6)) * minOf(zoom, 1f)).dp.toPx() }

/** PENDING 球 lerp 色（LockedSlate → TimeGold 定值近似，精确进度由详情页承担） */
private fun pendingColor(id: String): androidx.compose.ui.graphics.Color {
    val seed = (id.hashCode() and 0xFF) / 255f
    return androidx.compose.ui.graphics.lerp(LockedSlateDraw, TimeGold, 0.35f + seed * 0.3f)
}

/**
 * 成熟度渐变（体验储备池 §2）：锁定非 PENDING 球核色 = 尘埃灰 → 暖金 lerp，
 * 比例 = 满足比 × 0.45（上限刻意压在 PENDING 段 0.35–0.65 的下缘之下，
 * 「温度在涨」可感但不会与「即将达成」混淆）；无快照数据回落纯尘埃灰。
 */
private fun maturityColor(id: String, ratios: Map<String, Float>): androidx.compose.ui.graphics.Color =
    androidx.compose.ui.graphics.lerp(
        LockedSlateDraw,
        TimeGold,
        (ratios[id] ?: 0f).coerceIn(0f, 1f) * 0.45f,
    )

private val LockedSlateDraw = androidx.compose.ui.graphics.Color(0xFF5A6B7A)

/**
 * 已读金球余温色：TimeGold 压暗 40%（同色系降亮度，不引入新视觉 Token）——
 * 与「未读金球满亮度」拉开一档，语义为「内容已读、不再催点，但仍是已解锁的金星」
 */
private val ReadGoldDraw = androidx.compose.ui.graphics.lerp(TimeGold, androidx.compose.ui.graphics.Color.Black, 0.40f)
private val ReadGoldArgb = ReadGoldDraw.toArgb()

// A8：绘制期 Stroke 常量（原每帧每球 new Stroke，提升为不可变单例）
private val TRACK_STROKE = Stroke(width = 1.2f)
private val RING_LOCKED_STROKE = Stroke(width = 1.4f)
private val ORB_HIGHLIGHT_STROKE = Stroke(width = 1.6f)

/** 星座连线：锁定段暗金（低透明时金，不与轨道细线混淆）、点亮段满亮时金（主题 Token 派生） */
private val CONSTELLATION_DIM = TimeGold.copy(alpha = 0.18f)
private val CONSTELLATION_LIT = TimeGold.copy(alpha = 0.6f)

/** 星云底光（暖琥珀，极低亮度） */
private const val NEBULA_COLOR = 0xFF4A3813.toInt()

/** 星野尘星配色：暖白为主，少量时金 */
private const val STAR_INK = 0xFFCFC5B2.toInt()
private const val STAR_GOLD = 0xFFE8B44A.toInt()

/** 星野数量与数组步长（x/y/r/相位/速率） */
private const val STAR_COUNT = 110
private const val STAR_STRIDE = 5
private const val STAR_SEED = 20260906L

/** 视差幅度：球体层（近景）最大，星云居中，星野（远景）反向最小幅度但反向 */
private val ORB_PARALLAX_DP = 18.dp
private val NEBULA_PARALLAX_DP = 7.dp
private val STAR_PARALLAX_DP = 9.dp
