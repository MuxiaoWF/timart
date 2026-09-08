package com.muxiao.timart.ui.cosmic

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.ui.components.particle.GlowPainter
import com.muxiao.timart.ui.components.particle.ParticleEngine
import com.muxiao.timart.ui.components.visual.GlowOrb
import com.muxiao.timart.ui.theme.DustAsh
import com.muxiao.timart.ui.theme.GlowGold
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.LockedSlate
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TrackHairline
import kotlin.math.cos
import kotlin.math.sin

/**
 * 单球渲染（架构 §2.14）：四状态视觉映射，预览卡 / 详情页复用。
 * - LOCKED：冷灰青星球质感尘核（PlanetPainter 预渲染贴图；引擎 BREATHE 内流由上层接入；
 *   radius ≥ 64dp 加「封印轨道环」——细环 + 两粒固定封印节点，与启动图标斜轨同构）
 * - PENDING：LockedSlate→TimeGold lerp 内光 + 固定种子金点 + 封印环变进度环
 *   （[pending] 标志，非 CapsuleState 枚举值）
 * - UNLOCKED：暖金稳光慢呼吸（breathing 开关）
 * - DESTROYED：暖灰尘迹残影（无光晕，维持扁平余烬感不走星球贴图）
 * 同页同一时刻仅焦点球开呼吸（PRD 红线：单主焦点动画）。
 */
@Composable
fun CapsuleOrbView(
    state: CapsuleState,
    radius: Dp,
    modifier: Modifier = Modifier,

    /** PENDING 满足进度 0..1（lerp 配色） */
    satisfyProgress: Float = 0f,

    /** 呼吸动画开关（仅焦点球 true） */
    breathing: Boolean = false,

    /** PENDING 视觉态：LOCKED 胶囊条件满足待点击（UI 层判定，不改库状态） */
    pending: Boolean = false,
) {
    // 呼吸动画按需创建（A3）：非焦点球（breathing=false）不创建 infiniteTransition，
    // 避免依赖行 / 星库列表 / 预览卡中每颗小球都在每帧空转一个永不读取的动画；
    // 非呼吸的 UNLOCKED 稳光沿用原定值 0.4f
    val breath = if (breathing) {
        val transition = rememberInfiniteTransition(label = "orbBreath")
        transition.animateFloat(
            initialValue = 0.26f,
            targetValue = 0.5f,
            animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
            label = "orbBreathAlpha",
        ).value
    } else {
        0.4f
    }

    val isPending = pending && state == CapsuleState.LOCKED
    val coreColor = when {
        isPending -> lerp(LockedSlate, TimeGold, satisfyProgress.coerceIn(0f, 1f))
        state == CapsuleState.LOCKED -> LockedSlate
        state == CapsuleState.UNLOCKED -> TimeGold
        else -> DustAsh.copy(alpha = 0.55f)
    }
    val glowColor = when {
        isPending -> lerp(LockedSlate, GlowGold, satisfyProgress.coerceIn(0f, 1f))
        state == CapsuleState.LOCKED -> LockedSlate.copy(alpha = 0.7f)
        state == CapsuleState.UNLOCKED -> GlowGold
        else -> Color.Transparent
    }
    val glowAlpha = when {
        state == CapsuleState.UNLOCKED -> breath // breathing=false 时 breath 已是 0.4f 稳光
        state == CapsuleState.DESTROYED -> 0f
        else -> 0.3f
    }

    // 布局占位 = 球核直径：GlowOrb 的 Canvas 无尺寸约束时高度为 0，球会溢出压到后续内容；
    // 光晕按 glowScale 越界绘制（不裁剪），故占位只需覆盖球核
    androidx.compose.foundation.layout.Box(
        modifier = modifier.size(radius * 2),
        contentAlignment = Alignment.Center,
    ) {
        GlowOrb(
            radius = radius,
            coreColor = coreColor,
            glowColor = glowColor,
            glowAlpha = glowAlpha,
            // 星球质感球核：除 DESTROYED 残影（保持扁平余烬感）外全部启用；
            // 小位图（依赖球 11dp 等）在 PlanetPainter 内自动跳过尘带/微尘，仅保留渐变体积
            planetCore = state != CapsuleState.DESTROYED,
        )
        // PENDING 内部固定种子金点（尘核内部流动的静态兜底；纯 LOCKED 态尘点与
        // 球核同色不可见，已删除——星球贴图自带烘焙微尘承担内部质感）
        if (isPending) {
            Canvas(modifier = Modifier) {
                val r = radius.toPx()
                val seeds = floatArrayOf(0.13f, 0.71f, 0.33f, 0.91f)
                val color = TimeGold
                for (i in seeds.indices) {
                    val fx = seeds[i]
                    val fy = seeds[(i + 2) % seeds.size]
                    drawIntoCanvas { canvas ->
                        GlowPainter.drawDot(
                            canvas.nativeCanvas,
                            size.width / 2f + (fx - 0.5f) * r * 0.9f,
                            size.height / 2f + (fy - 0.5f) * r * 0.9f,
                            r * 0.05f + 1.5f,
                            color.toArgb(),
                            0.9f,
                        )
                    }
                }
            }
        }
        // DESTROYED 尘迹残影弧
        if (state == CapsuleState.DESTROYED) {
            Canvas(modifier = Modifier) {
                val r = radius.toPx() * 0.92f
                drawArc(
                    color = DustAsh.copy(alpha = 0.4f),
                    startAngle = 40f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(size.width / 2f - r, size.height / 2f - r),
                    size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
                    style = Stroke(width = 1.5f),
                )
            }
        }
        // 封印轨道环（详情页大球专属，radius ≥ 64dp 才启用）：
        // 细环 + 环上两粒固定「封印节点」，与 App 启动图标斜轨同构——
        // 环属于封印叙事（待启的核被扣在轨道上），首页时轨小球保持无环语义不变；
        // 引擎 ORBIT 尘环（1.10–1.56× 球径）从环带穿过，动静两层互为支撑。
        // PENDING 态同一位置改为进度环：底环 + 时金进度弧（自顶部顺时针）。
        if (state == CapsuleState.LOCKED && radius >= 64.dp) {
            Canvas(modifier = Modifier) {
                val r = radius.toPx()
                val ringR = r * 1.32f
                if (isPending) {
                    drawCircle(
                        color = TrackHairline,
                        radius = ringR,
                        style = Stroke(width = 1.2.dp.toPx()),
                    )
                    drawArc(
                        color = TimeGold.copy(alpha = 0.85f),
                        startAngle = -90f,
                        sweepAngle = 360f * satisfyProgress.coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = Offset(size.width / 2f - ringR, size.height / 2f - ringR),
                        size = androidx.compose.ui.geometry.Size(ringR * 2f, ringR * 2f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                    )
                } else {
                    drawCircle(
                        color = InkPrimary.copy(alpha = 0.20f),
                        radius = ringR,
                        style = Stroke(width = 1.2.dp.toPx()),
                    )
                    drawIntoCanvas { canvas ->
                        for (a in floatArrayOf(210f, 30f)) {
                            val rad = a * Math.PI.toFloat() / 180f
                            GlowPainter.drawDot(
                                canvas.nativeCanvas,
                                size.width / 2f + cos(rad) * ringR,
                                size.height / 2f + sin(rad) * ringR,
                                2.4f.dp.toPx(),
                                InkPrimary.toArgb(),
                                0.55f,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 状态 → 引擎锚点颜色（ARGB），供 TimeTrackCanvas 同步 BREATHE 粒子色；pending 时用暖金过渡色 */
internal fun CapsuleState.anchorColorArgb(pending: Boolean = false): Int = when {
    pending && this == CapsuleState.LOCKED -> 0xFFB8933F.toInt()
    this == CapsuleState.LOCKED -> 0xFF5A6B7A.toInt()
    this == CapsuleState.UNLOCKED -> ParticleEngine.TIME_GOLD
    else -> 0xFF7A7268.toInt()
}
