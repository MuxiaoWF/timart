package com.muxiao.timart.ui.components.visual

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.muxiao.timart.ui.components.particle.GlowPainter

/**
 * 通用发光球体（架构 §2.18）：预渲染光晕 + **微缩星球质感**球核 + 可选进度圆环。
 * 球核 = 暗边底盘 + 偏光内芯 + 左上高光弧（纯分层圆形，无 Shader，帧循环安全）；
 * 四状态配色由参数注入（首页时轨 / 预览卡 / 详情页共用）；
 * 循环动效不由本组件承担（引擎粒子 / 上层呼吸动画注入 alpha）。
 */
@Composable
fun GlowOrb(
    radius: Dp,
    coreColor: Color,
    glowColor: Color,
    modifier: Modifier = Modifier,
    glowAlpha: Float = 0.35f,
    glowScale: Float = 1.8f,

    /** 圆环进度（0..1，UNLOCKED 金环弧；null 不画环） */
    ringProgress: Float? = null,
    ringColor: Color = Color.Transparent,
    ringWidth: Dp = 2.dp,
) {
    Canvas(modifier = modifier) {
        val r = radius.toPx()
        val cx = size.width / 2f
        val cy = size.height / 2f
        drawIntoCanvas { canvas ->
            GlowPainter.drawGlow(
                canvas = canvas.nativeCanvas,
                cx = cx,
                cy = cy,
                radius = r * glowScale,
                colorArgb = glowColor.toArgb(),
                alpha = glowAlpha,
            )
        }
        // 微缩星球：暗边底盘 → 偏光内芯 → 左上高光弧
        drawCircle(
            color = coreColor.copy(alpha = coreColor.alpha).let { lerp(it, Color.Black, 0.32f) },
            radius = r,
            center = Offset(cx, cy),
        )
        drawCircle(
            color = coreColor,
            radius = r * 0.85f,
            center = Offset(cx - r * 0.10f, cy - r * 0.13f),
        )
        drawArc(
            color = GLOW_HIGHLIGHT.copy(alpha = 0.45f),
            startAngle = -150f,
            sweepAngle = 55f,
            useCenter = false,
            topLeft = Offset(cx - r * 0.8f, cy - r * 0.8f),
            size = androidx.compose.ui.geometry.Size(r * 1.6f, r * 1.6f),
            style = Stroke(width = 1.6f),
        )
        ringProgress?.let { progress ->
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
                style = Stroke(width = ringWidth.toPx()),
            )
        }
    }
}

/** 高光弧颜色（暖白，与主题 InkPrimary 同值，避免组件反向依赖主题层） */
private val GLOW_HIGHLIGHT = Color(0xFFEDE6D8)
