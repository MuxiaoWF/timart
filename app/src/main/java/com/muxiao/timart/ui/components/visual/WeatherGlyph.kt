package com.muxiao.timart.ui.components.visual

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.muxiao.timart.domain.model.WeatherType
import com.muxiao.timart.ui.theme.GlowGold
import com.muxiao.timart.ui.theme.InkSecondary
import kotlin.math.cos
import kotlin.math.sin

/**
 * 纯 Canvas 天气小图形（架构 §2.18）：7 种 WeatherType 各自的简笔绘制。
 * 不引图标库、不用 emoji；线色随参数注入（默认次级文字色）。
 *
 * @param accentColor 强调色（B3 参数化）：仅 THUNDER 的闪电折线使用，默认光晕金——
 *   雷电保留"金"的语义例外，但不再硬编码，反色等特殊场景可注入覆盖
 */
@Composable
fun WeatherGlyph(
    type: WeatherType,
    modifier: Modifier = Modifier,
    color: Color = InkSecondary,
    accentColor: Color = GlowGold,
) {
    Canvas(modifier = modifier) { drawGlyph(type, color, accentColor) }
}

private fun DrawScope.drawGlyph(type: WeatherType, color: Color, accentColor: Color) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val stroke = h * 0.09f
    when (type) {
        // 圆日
        WeatherType.CLEAR -> {
            drawCircle(color, radius = h * 0.28f, center = Offset(cx, h * 0.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
            // 光线四点
            listOf(0f, 90f, 180f, 270f).forEach { deg ->
                val rad = Math.toRadians(deg.toDouble())
                val r1 = h * 0.38f
                val r2 = h * 0.46f
                drawLine(
                    color,
                    Offset(cx + (cos(rad) * r1).toFloat(), h * 0.5f + (sin(rad) * r1).toFloat()),
                    Offset(cx + (cos(rad) * r2).toFloat(), h * 0.5f + (sin(rad) * r2).toFloat()),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }

        // 云
        WeatherType.CLOUDY -> {
            drawCircle(color, radius = h * 0.22f, center = Offset(cx - w * 0.12f, h * 0.55f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
            drawCircle(color, radius = h * 0.28f, center = Offset(cx + w * 0.10f, h * 0.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
            drawLine(color, Offset(cx - w * 0.26f, h * 0.72f), Offset(cx + w * 0.30f, h * 0.72f), strokeWidth = stroke, cap = StrokeCap.Round)
        }

        // 雾线
        WeatherType.FOG -> {
            val ys = listOf(0.32f, 0.52f, 0.72f)
            ys.forEachIndexed { i, fy ->
                val inset = if (i == 1) w * 0.10f else w * 0.20f
                drawLine(color, Offset(inset, h * fy), Offset(w - inset, h * fy), strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }

        // 雨丝（云 + 斜线）
        WeatherType.DRIZZLE, WeatherType.RAIN -> {
            drawCircle(color, radius = h * 0.2f, center = Offset(cx - w * 0.1f, h * 0.36f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
            drawCircle(color, radius = h * 0.25f, center = Offset(cx + w * 0.12f, h * 0.33f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
            drawLine(color, Offset(cx - w * 0.28f, h * 0.55f), Offset(cx + w * 0.32f, h * 0.55f), strokeWidth = stroke, cap = StrokeCap.Round)
            val drops = if (type == WeatherType.RAIN) 3 else 2
            for (i in 0 until drops) {
                val dx = cx - w * 0.18f + i * w * 0.18f
                drawLine(
                    color,
                    Offset(dx, h * 0.64f),
                    Offset(dx - w * 0.06f, h * 0.8f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }

        // 雪花（六向线）
        WeatherType.SNOW -> {
            for (deg in 0 until 180 step 30) {
                val rad = Math.toRadians(deg.toDouble())
                drawLine(
                    color,
                    Offset(cx - (cos(rad) * h * 0.32f).toFloat(), h * 0.5f - (sin(rad) * h * 0.32f).toFloat()),
                    Offset(cx + (cos(rad) * h * 0.32f).toFloat(), h * 0.5f + (sin(rad) * h * 0.32f).toFloat()),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
            drawCircle(color, radius = stroke * 0.9f, center = Offset(cx, h * 0.5f))
        }

        // 雷弧（云 + 闪电折线；闪电用强调色，其余线条随 color 注入）
        WeatherType.THUNDER -> {
            drawCircle(color, radius = h * 0.2f, center = Offset(cx - w * 0.08f, h * 0.32f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
            drawCircle(color, radius = h * 0.24f, center = Offset(cx + w * 0.14f, h * 0.3f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
            drawLine(color, Offset(cx - w * 0.26f, h * 0.5f), Offset(cx + w * 0.3f, h * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(accentColor, Offset(cx + w * 0.05f, h * 0.55f), Offset(cx - w * 0.04f, h * 0.7f), strokeWidth = stroke * 1.2f, cap = StrokeCap.Round)
            drawLine(accentColor, Offset(cx - w * 0.04f, h * 0.7f), Offset(cx + w * 0.04f, h * 0.72f), strokeWidth = stroke * 1.2f, cap = StrokeCap.Round)
            drawLine(accentColor, Offset(cx + w * 0.04f, h * 0.72f), Offset(cx - w * 0.06f, h * 0.9f), strokeWidth = stroke * 1.2f, cap = StrokeCap.Round)
        }
    }
}
