package com.muxiao.timart.ui.detail

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.components.particle.GlowPainter
import com.muxiao.timart.ui.components.particle.ParticleCanvas
import com.muxiao.timart.ui.components.particle.ParticleEngine
import com.muxiao.timart.ui.components.particle.ParticlePreset
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.GlowGold
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.PaperCream
import com.muxiao.timart.ui.theme.PaperInk
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.ui.theme.TimeGold
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

/**
 * 状态 B：UNSEAL 揭封序列（**火漆裂开 · 双翼展开**，单焦点叙事）：
 *
 * | 阶段 | 内容                                                     | 时长 ms   |
 * | 0    | 合拢的信笺与火漆印自暗场浮现（印面放大归位 + 短震动一次）    | 0–420    |
 * | 1    | 印面裂开：中缝金光纵向生长，火漆化作光散去                   | 420–760  |
 * | 2    | 双翼横向展开：左右两半绕中缝轴回转摊平，折痕阴影随开合变亮    | 700–1350 |
 * | 3    | 平整信笺短暂停留 → 交接正文卡片（同位同构，文字显现效果接管）| 1350–1700|
 *
 * 位置契约：舞台即最终卡片矩形（左上 (24dp, 64dp)、宽 = 屏宽-48dp、圆角 18dp，
 * 与 `PaperLetterCard` 一致）；双翼摊平后与 CONTENT 相位的真实卡片完全同位，
 * 切换时卡片"留在原地"，文字显现（打字机/扰乱/模糊/波浪）在真实卡片上接管。
 *
 * - 粒子批次由共享引擎 UNSEAL 时间轴驱动；skipToEnd 任意时刻安全落 CONTENT；
 * - 任意点击 → finishNow()（业务相位切换不等待动画回调）；
 * - 震动只在阶段 0 使用一次；全程无背景闪白、无烟花爆散。
 */
@Composable
fun UnsealSequence(
    engine: ParticleEngine,
    onDone: () -> Unit,
    /** 真实卡片实测高度（px）；null 时退回估算值。双翼按此高度生成，交接零几何差 */
    cardHeightPx: Int? = null,
    /** 序列启动时的音效回调（unseal 短音效；受设置音效开关控制） */
    onPlaySound: () -> Unit = {},
) {
    val L = LocalStrings.current
    val density = LocalDensity.current
    val context = LocalContext.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var elapsed by remember { mutableLongStateOf(0L) }
    val finished = remember { AtomicBoolean(false) }

    // 幂等收尾：平整停留结束 / 兜底超时 / 点击跳过 只走一次
    fun finishNow() {
        if (finished.compareAndSet(false, true)) {
            engine.onSequenceFinished = null
            engine.skipToEnd()
            onDone()
        }
    }

    // 阶段 0 一次性：短音效 + 短震动（避免廉价游戏反馈：震动仅此一次）
    LaunchedEffect(Unit) {
        onPlaySound()
        vibrateOnce(context)
    }

    // 逐帧推进 elapsed（揭封视觉的唯一时钟），平整停留结束即落 CONTENT
    LaunchedEffect(Unit) {
        val startNanos = withFrameNanos { it }
        while (isActive) {
            var ms = 0L
            withFrameNanos { frame -> ms = (frame - startNanos) / 1_000_000L }
            elapsed = ms.coerceAtMost(TOTAL_MS + HOLD_MS)
            if (ms >= TOTAL_MS + HOLD_MS) {
                finishNow()
                break
            }
        }
    }

    // 卡片几何：与 CONTENT 相位 UnlockedLetterView 的信笺卡片严格同位（B1：常量走 PaperCardGeometry 唯一来源）
    val cardLeftPx = PaperCardGeometry.leftPx(density)
    val cardTopPx = PaperCardGeometry.topPx(density)
    val cardWpx = PaperCardGeometry.widthPx(density, canvasSize.width.toFloat())
    // 高度：优先用真实卡片实测值（DetailScreen 隐藏排版通道），未就绪时退回估算
    val cardHpx = cardHeightPx?.toFloat()?.coerceAtLeast(0f)
        ?: PaperCardGeometry.estimatedHeightPx(density, canvasSize.height.toFloat())
    val cardHdp = with(density) { cardHpx.toDp() }
    val halfWdp = with(density) { (cardWpx / 2f).toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepCharcoal)
            .onSizeChanged { canvasSize = it }
            // 任意点击立即跳至稳定内容态
            .pointerInput(Unit) { detectTapGestures { finishNow() } },
    ) {
        // 粒子层（引擎 UNSEAL 五批次：聚拢/内光/金环/峰值/归稳）
        ParticleCanvas(
            engine = engine,
            modifier = Modifier.fillMaxSize(),
            owner = ParticleEngine.OWNER_DETAIL,
        )

        // 中缝光与火漆金晕（底层 Canvas）
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = cardLeftPx + cardWpx / 2f
            val cy = cardTopPx + cardHpx / 2f
            val e = elapsed.toFloat()
            val crackP = progressOf(e, CRACK_START, CRACK_END)
            val wingE = easeOutCubic(progressOf(e, WINGS_START, WINGS_END))
            val inP = easeOutCubic(progressOf(e, 0f, SEAL_IN_END))

            // 火漆金晕：随裂开涨落后随展开收束
            if (inP > 0f) {
                drawIntoCanvas { canvas ->
                    GlowPainter.drawGlow(
                        canvas.nativeCanvas,
                        cx,
                        cy,
                        cardHpx * (0.20f + 0.12f * crackP) * inP,
                        GlowGold.copy(alpha = 0.30f).toArgb(),
                        (0.32f + 0.32f * crackP) * (1f - wingE * 0.85f),
                    )
                }
            }
        }

        // 信笺双翼（左右两半绕中缝轴摊平）
        // A4：elapsed 只在 graphicsLayer / Canvas draw 相位读取——layer 属性与折痕阴影
        // 逐帧更新不再触发本序列子树逐帧重组
        if (canvasSize != IntSize.Zero && cardWpx > 0f) {
            // 左翼（铰链：自身右缘 = 中缝）
            Box(
                modifier = Modifier
                    .offset { IntOffset(cardLeftPx.roundToInt(), cardTopPx.roundToInt()) }
                    .size(halfWdp, cardHdp)
                    .zIndex(1f)
                    .graphicsLayer {
                        val e = elapsed.toFloat()
                        val inP = easeOutCubic(progressOf(e, 0f, SEAL_IN_END))
                        // 初始 55°：折叠感可读（35° 时透视缩短不足 18%，读作倾斜卡片而非合拢信笺）
                        rotationY = 55f * (1f - easeOutCubic(progressOf(e, WINGS_START, WINGS_END)))
                        transformOrigin = TransformOrigin(1f, 0.5f)
                        cameraDistance = 16f * this.density
                        // 阶段 0 浮现纵深：自铰链（中缝）微缩放大归位，与火漆印 scale 同语
                        val s = 0.96f + 0.04f * inP
                        scaleX = s
                        scaleY = s
                        alpha = inP
                    }
                    .background(
                        PaperCream,
                        RoundedCornerShape(topStart = PaperCardGeometry.Corner, bottomStart = PaperCardGeometry.Corner),
                    ),
            ) {
                // 折痕阴影（draw 相位读 elapsed：仅重绘，不重组）
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val wingE = easeOutCubic(progressOf(elapsed.toFloat(), WINGS_START, WINGS_END))
                    drawRect(Color.Black.copy(alpha = 0.36f * (1f - wingE)))
                }
            }

            // 右翼（铰链：自身左缘 = 中缝）
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset((cardLeftPx + cardWpx / 2f).roundToInt(), cardTopPx.roundToInt())
                    }
                    .size(halfWdp, cardHdp)
                    .zIndex(1f)
                    .graphicsLayer {
                        val e = elapsed.toFloat()
                        val inP = easeOutCubic(progressOf(e, 0f, SEAL_IN_END))
                        rotationY = -55f * (1f - easeOutCubic(progressOf(e, WINGS_START, WINGS_END)))
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        cameraDistance = 16f * this.density
                        val s = 0.96f + 0.04f * inP
                        scaleX = s
                        scaleY = s
                        alpha = inP
                    }
                    .background(
                        PaperCream,
                        RoundedCornerShape(topEnd = PaperCardGeometry.Corner, bottomEnd = PaperCardGeometry.Corner),
                    ),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val wingE = easeOutCubic(progressOf(elapsed.toFloat(), WINGS_START, WINGS_END))
                    drawRect(Color.Black.copy(alpha = 0.36f * (1f - wingE)))
                }
            }

            // 火漆印（压在中缝上；裂开时化作光散去）
            // 原组合期门控 `if (crackP < 1f)` 改为 layer alpha 归零：移除组合相位读取，
            // 消散完成后仅保留一个透明 layer，成本可忽略
            Canvas(
                modifier = Modifier
                    .zIndex(2f)
                    .offset {
                        IntOffset(
                            (cardLeftPx + cardWpx / 2f - with(density) { 42.dp.toPx() }).roundToInt(),
                            (cardTopPx + cardHpx / 2f - with(density) { 42.dp.toPx() }).roundToInt(),
                        )
                    }
                    .size(84.dp)
                    .graphicsLayer {
                        val e = elapsed.toFloat()
                        val inP = easeOutCubic(progressOf(e, 0f, SEAL_IN_END))
                        val crackP = progressOf(e, CRACK_START, CRACK_END)
                        scaleX = (0.6f + 0.4f * inP) * (1f - 0.25f * crackP)
                        scaleY = scaleX
                        alpha = inP * (1f - crackP)
                    },
            ) {
                val c = size.minDimension / 2f
                val waxRim = Color(0xFF8B6C2C)
                // 蜡质不规则外缘：12 粒确定伪随机小圆沿外缘叠加，打破正圆的"硬币感"
                for (i in 0 until 12) {
                    val ang = i * (6.2832f / 12f)
                    val jr = ((i * 73) % 13) / 13f // 半径抖动
                    val jd = ((i * 41) % 11) / 11f // 距离抖动
                    val dist = c * (0.90f + 0.05f * jd)
                    drawCircle(
                        color = waxRim,
                        radius = c * (0.12f + 0.08f * jr),
                        center = Offset(c + cos(ang) * dist, c + sin(ang) * dist),
                    )
                }
                // 火漆：暗缘底盘 → 金核 → 环形压纹 → 时轨压纹（斜轨 + 星核，与启动图标同构）
                drawCircle(color = waxRim, radius = c * 0.98f, center = Offset(c, c))
                drawCircle(color = TimeGold, radius = c * 0.78f, center = Offset(c, c))
                drawCircle(
                    color = PaperInk.copy(alpha = 0.35f),
                    radius = c * 0.52f,
                    center = Offset(c, c),
                    style = Stroke(width = 1.6f),
                )
                rotate(degrees = -24f, pivot = Offset(c, c)) {
                    drawOval(
                        color = PaperInk.copy(alpha = 0.45f),
                        topLeft = Offset(c - c * 0.42f, c - c * 0.17f),
                        size = Size(c * 0.84f, c * 0.34f),
                        style = Stroke(width = 1.2f),
                    )
                }
                drawCircle(color = TimeGold.copy(alpha = 0.85f), radius = c * 0.14f, center = Offset(c, c))
            }

            // 中缝裂光（顶层 Canvas：金光沿中缝纵向生长，随展开收束）
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = cardLeftPx + cardWpx / 2f
                val e = elapsed.toFloat()
                val crackP = progressOf(e, CRACK_START, CRACK_END)
                val wingP = progressOf(e, WINGS_START, WINGS_END)
                if (crackP > 0f && wingP < 1f) {
                    val half = cardHpx * 0.34f * crackP
                    val start = Offset(cx, cardTopPx + cardHpx / 2f - half)
                    val end = Offset(cx, cardTopPx + cardHpx / 2f + half)
                    // 外晕 + 内芯双层：裂光有"从蜡缝溢出"的体积感，而非一根细线
                    drawLine(
                        color = GlowGold.copy(alpha = (0.30f - 0.18f * wingP)),
                        start = start,
                        end = end,
                        strokeWidth = (5f + 4f * crackP) * (1f - wingP * 0.6f),
                    )
                    drawLine(
                        color = GlowGold.copy(alpha = (0.85f - 0.5f * wingP)),
                        start = start,
                        end = end,
                        strokeWidth = (1.4f + 1.8f * crackP) * (1f - wingP * 0.6f),
                    )
                }
            }
        }

        // 底部提示（可点击跳过）
        Text(
            text = L.unsealSkipHint,
            style = TimartType.caption,
            color = InkSecondary.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 42.dp),
        )
    }

    // 引擎序列发射 + 兜底超时（跳过与完成均不阻塞业务）
    LaunchedEffect(canvasSize) {
        if (canvasSize == IntSize.Zero) return@LaunchedEffect
        // 锚点 = 火漆印中心（卡片中缝中点）：聚拢/峰值批次与裂开的印面同位，
        // 不再用屏幕比例猜位；半径 ≈ 印面半径（84dp 印面 → 42dp）
        val anchorRadius = with(density) { 48.dp.toPx() }
        engine.onSequenceFinished = null // 收尾由本地时间轴负责
        engine.fire(
            preset = ParticlePreset.UNSEAL,
            anchorX = cardLeftPx + cardWpx / 2f,
            anchorY = cardTopPx + cardHpx / 2f,
            anchorRadius = anchorRadius,
            colorArgb = ParticleEngine.TIME_GOLD,
            sequence = true,
        )
        delay((TOTAL_MS + HOLD_MS + 600L).milliseconds)
        finishNow()
    }
}

/** 线性进度 0..1（钳制） */
private fun progressOf(value: Float, start: Float, end: Float): Float =
    ((value - start) / (end - start)).coerceIn(0f, 1f)

/** easeOutCubic：先快后慢的手感（禁 spring 弹跳） */
private fun easeOutCubic(p: Float): Float {
    val t = p.coerceIn(0f, 1f)
    return 1f - (1f - t) * (1f - t) * (1f - t)
}

/** 阶段 0 一次性短震动（API 24–25 旧 API，26+ VibrationEffect，31+ VibratorManager） */
private fun vibrateOnce(context: Context) {
    runCatching {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }
    }
}

/**
 * 简化重读过渡（PRD 状态 C 规则 7 / 星库重读 450ms；与揭封同语言：火漆微光 + 中缝一闪
 * + REREAD 预算的尘粒向卡片中心聚合——该预算此前从未接线）：
 * 纯视觉层，业务内容已在 CONTENT 态就绪。
 */
@Composable
fun RereadVeilOverlay(
    engine: ParticleEngine,
    onDone: () -> Unit,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    // 重读粒子：档位预算 reread（高 40 / 中 20 / 低 0）向卡片中心聚合
    LaunchedEffect(canvasSize) {
        if (canvasSize == IntSize.Zero) return@LaunchedEffect
        val w = PaperCardGeometry.widthPx(density, canvasSize.width.toFloat())
        val h = PaperCardGeometry.estimatedHeightPx(density, canvasSize.height.toFloat())
        engine.fire(
            preset = ParticlePreset.REREAD,
            anchorX = with(density) { 24.dp.toPx() } + w / 2f,
            anchorY = with(density) { 64.dp.toPx() } + h / 2f,
            anchorRadius = h / 2f,
            colorArgb = 0xFFF5D08C.toInt(),
        )
    }
    LaunchedEffect(Unit) {
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(durationMillis = 450, easing = LinearOutSlowInEasing),
        ) { value, _ -> progress = value }
        onDone()
    }
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it },
    ) {
        // 与 UnlockedLetterView 相同的卡片矩形（B1：常量走 PaperCardGeometry 唯一来源）
        val cardLeft = PaperCardGeometry.leftPx(this)
        val cardTop = PaperCardGeometry.topPx(this)
        val w = PaperCardGeometry.widthPx(this, size.width)
        val h = PaperCardGeometry.estimatedHeightPx(this, size.height)
        val cx = cardLeft + w / 2f
        val cy = cardTop + h / 2f

        // 火漆微光：中心光晕涨落
        drawIntoCanvas { canvas ->
            GlowPainter.drawGlow(
                canvas.nativeCanvas,
                cx,
                cy,
                h * (0.16f + 0.18f * progress),
                0xFFF5D08C.toInt(),
                (1f - progress) * 0.55f,
            )
        }
        // 中缝竖光一闪
        drawLine(
            color = GlowGold.copy(alpha = (1f - progress) * 0.7f),
            start = Offset(cx, cy - h * 0.09f * (0.5f + progress)),
            end = Offset(cx, cy + h * 0.09f * (0.5f + progress)),
            strokeWidth = 1.5f + (1f - progress) * 1.5f,
        )
    }
}

// ---- 揭封时间轴常量（ms）----

/** 阶段 0：信笺与火漆浮现完成 */
private const val SEAL_IN_END = 420f

/** 阶段 1：印面裂开窗口 */
private const val CRACK_START = 420f
private const val CRACK_END = 760f

/** 阶段 2：双翼展开窗口（与裂开尾段重叠 60ms，衔接更顺） */
private const val WINGS_START = 700f
private const val WINGS_END = 1350f

/** 成形完成时刻（此后短暂停留） */
private const val TOTAL_MS = 1400L

/** 平整停留时长：摊平的信笺原位稍停，之后交接正文卡片（同位，文字效果接管） */
private const val HOLD_MS = 300L
