package com.muxiao.timart.ui.components.visual

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.muxiao.timart.ui.theme.GlowGold
import com.muxiao.timart.ui.theme.TimeGold
import kotlin.math.cos
import kotlin.math.sin

/**
 * 火漆印章（N19）：参数化矢量印章，零资源文件，颜色只用主题 Token（红线：不引 emoji/外部图标）。
 *
 * 样式（1..5）：
 * 1 圆月（双环） / 2 星芒（四芒） / 3 山岳（双峰） / 4 航线（弧线 + 归点） / 5 时针（表盘两针）。
 * 静态绘制（无帧循环、无分配热路径），外圈磨损感用两段不等宽描边近似。
 */
object WaxSealStyle {
    const val MOON = 1
    const val STAR = 2
    const val MOUNTAIN = 3
    const val VOYAGE = 4
    const val CLOCK = 5

    /** 样式总数（创建侧选择器与取值上界一致） */
    const val COUNT = 5

    fun isValid(style: Int): Boolean = style in 1..COUNT
}

@Composable
fun WaxSeal(
    style: Int,
    modifier: Modifier = Modifier,
    waxColor: Color = TimeGold,
    inkColor: Color = GlowGold,
) {
    Canvas(modifier = modifier) {
        if (!WaxSealStyle.isValid(style)) return@Canvas
        val side = minOf(size.width, size.height)
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = side / 2f

        // 火漆基底：外圈厚描边（两段不等宽近似手工磨损）
        drawCircle(color = waxColor.copy(alpha = 0.16f), radius = radius, center = center)
        drawCircle(
            color = waxColor,
            radius = radius * 0.92f,
            center = center,
            style = Stroke(width = radius * 0.12f),
        )
        drawCircle(
            color = waxColor.copy(alpha = 0.55f),
            radius = radius * 0.78f,
            center = center,
            style = Stroke(width = radius * 0.035f),
        )

        // 内纹（归一化坐标系，r = 相对半径）
        fun rPath(block: Path.(Float) -> Unit): Path = Path().also { it.block(radius) }

        val glyph: Path = when (style) {
            WaxSealStyle.MOON -> rPath { r ->
                addOval(androidx.compose.ui.geometry.Rect(center.x - r * 0.42f, center.y - r * 0.42f, center.x + r * 0.42f, center.y + r * 0.42f))
                addOval(androidx.compose.ui.geometry.Rect(center.x - r * 0.22f, center.y - r * 0.22f, center.x + r * 0.22f, center.y + r * 0.22f))
            }
            WaxSealStyle.STAR -> rPath { r ->
                val inner = r * 0.16f
                val outer = r * 0.52f
                for (i in 0 until 4) {
                    val angle = Math.toRadians((i * 90 - 90).toDouble())
                    val spikeX = center.x + outer * cos(angle).toFloat()
                    val spikeY = center.y + outer * sin(angle).toFloat()
                    if (i == 0) moveTo(spikeX, spikeY) else lineTo(spikeX, spikeY)
                    val half = Math.toRadians((i * 90 - 45).toDouble())
                    lineTo(center.x + inner * cos(half).toFloat(), center.y + inner * sin(half).toFloat())
                }
                close()
            }
            WaxSealStyle.MOUNTAIN -> rPath { r ->
                moveTo(center.x - r * 0.5f, center.y + r * 0.28f)
                lineTo(center.x - r * 0.12f, center.y - r * 0.34f)
                lineTo(center.x + r * 0.06f, center.y - r * 0.05f)
                lineTo(center.x + r * 0.28f, center.y - r * 0.42f)
                lineTo(center.x + r * 0.52f, center.y + r * 0.28f)
                close()
            }
            WaxSealStyle.VOYAGE -> rPath { r ->
                arcTo(
                    rect = androidx.compose.ui.geometry.Rect(
                        center.x - r * 0.5f,
                        center.y - r * 0.1f,
                        center.x + r * 0.5f,
                        center.y + r * 0.7f,
                    ),
                    startAngleDegrees = 190f,
                    sweepAngleDegrees = 160f,
                    forceMoveTo = true,
                )
                addOval(androidx.compose.ui.geometry.Rect(center.x + r * 0.34f, center.y - r * 0.24f, center.x + r * 0.48f, center.y - r * 0.1f))
            }
            WaxSealStyle.CLOCK -> rPath { r ->
                addOval(androidx.compose.ui.geometry.Rect(center.x - r * 0.46f, center.y - r * 0.46f, center.x + r * 0.46f, center.y + r * 0.46f))
                moveTo(center.x, center.y)
                lineTo(center.x, center.y - r * 0.3f)
                moveTo(center.x, center.y)
                lineTo(center.x + r * 0.22f, center.y + r * 0.12f)
            }
            else -> return@Canvas
        }
        drawPath(path = glyph, color = inkColor, style = Stroke(width = radius * 0.05f))
    }
}
