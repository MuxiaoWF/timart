package com.muxiao.timart.ui.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 底部导航四页签图标与通用小图标：代码内置矢量（零依赖、零资源膨胀），随 Icon tint 用主题 Token 着色。
 * 造型取自「时轨宇宙」世界观：轨道 / 星 / 尘 / 调节杆，四个页签互不相同；
 * 内容一律以 Color.Black 占位，实际颜色由使用处 tint 注入（选中 TimeGold / 未选中 InkSecondary）。
 *
 * 线宽体系（B2 统一）：描边类一律 1.5f（原 timeTrack 1.3 / settings 2.0 混用，
 * 22dp 下视觉粗细失衡）；实体类（星芒 / 尘粒 / 旋钮）保持填充式。设置页旋钮半径
 * 2.6→2.3 微调，与 1.5f 线宽重新配平视觉重量。
 */
object TabIcons {

    /** 时轨：行星 + 轨道环 + 环上星子 */
    val timeTrack: ImageVector = buildIcon("TabTimeTrack") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f) {
            circle(12f, 12f, 7f)
        }
        path(fill = SolidColor(Color.Black)) {
            circle(12f, 12f, 3f)
        }
        path(fill = SolidColor(Color.Black)) {
            circle(16.95f, 7.05f, 1.7f)
        }
    }

    /** 星库：四芒星（跨度 16/24，与时轨行星 16 光学配平，避免底栏并排时此签偏重） */
    val starLibrary: ImageVector = buildIcon("TabStarLibrary") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 4f)
            curveTo(12.72f, 8.48f, 15.52f, 11.28f, 20f, 12f)
            curveTo(15.52f, 12.72f, 12.72f, 15.52f, 12f, 20f)
            curveTo(11.28f, 15.52f, 8.48f, 12.72f, 4f, 12f)
            curveTo(8.48f, 11.28f, 11.28f, 8.48f, 12f, 4f)
            close()
        }
    }

    /** 尘迹：渐散尘带（小圆点沿对角淡出） */
    val dustRecords: ImageVector = buildIcon("TabDustRecords") {
        path(fill = SolidColor(Color.Black)) {
            circle(5.5f, 18f, 2.1f)
        }
        path(fill = SolidColor(Color.Black)) {
            circle(10.5f, 14.5f, 1.6f)
        }
        path(fill = SolidColor(Color.Black)) {
            circle(14.5f, 10.5f, 1.2f)
        }
        path(fill = SolidColor(Color.Black)) {
            circle(17.5f, 6.5f, 0.85f)
        }
        path(fill = SolidColor(Color.Black)) {
            circle(20.2f, 4.2f, 0.5f)
        }
    }

    /** 设置：调节杆（三横杆 + 错位旋钮） */
    val settings: ImageVector = buildIcon("TabSettings") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(4f, 7f)
            lineTo(20f, 7f)
            moveTo(4f, 12f)
            lineTo(20f, 12f)
            moveTo(4f, 17f)
            lineTo(20f, 17f)
        }
        path(fill = SolidColor(Color.Black)) {
            circle(14.5f, 7f, 2.3f)
        }
        path(fill = SolidColor(Color.Black)) {
            circle(8f, 12f, 2.3f)
        }
        path(fill = SolidColor(Color.Black)) {
            circle(16.5f, 17f, 2.3f)
        }
    }

    /** 通用「＋」：新建入口等（B5：替代全角字符 ＋，几何与字重锁死、随 tint 着色） */
    val plus: ImageVector = buildIcon("IconPlus") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(12f, 5f)
            lineTo(12f, 19f)
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }
    }

    private fun buildIcon(
        name: String,
        builder: ImageVector.Builder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(builder).build()
}

/** 圆 = 两段半圆弧拼合（PathBuilder 无原生圆） */
private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = false, dx1 = 2 * r, dy1 = 0f)
    arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = false, dx1 = -2 * r, dy1 = 0f)
    close()
}
