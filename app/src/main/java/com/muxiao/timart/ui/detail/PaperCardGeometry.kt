package com.muxiao.timart.ui.detail

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 信笺卡片几何唯一来源（B1 收敛）。
 *
 * 此前 `(24dp, 64dp, 屏宽−48dp, min(0.46h, 440dp), 18dp)` 在 UnlockedLetterView /
 * UnsealSequence / RereadVeilOverlay 三处各自硬编码、仅靠注释互指"与 XX 相同"——
 * 任何一侧改动都会让揭封双翼、重读遮罩与真实卡片错位（正是 UnsealSequence 用
 * "实测高度通道"苦心消除的那类几何差）。现在三处统一引用本对象；
 * 运行时实测高度（onCardHeightChanged）仍优先于估算值。
 *
 * 注意：真实卡片圆角是 PaperLetterCard 的不规则四角（18/14/15/19dp），
 * 双翼/遮罩用 [Corner] 作近似值；如调整纸卡圆角，两处需同步。
 */
object PaperCardGeometry {

    /** 卡片左右边距（UnlockedLetterView 列 padding 与舞台左缘共用） */
    val HorizontalMargin: Dp = 24.dp

    /** 有返回按钮时卡片列顶部下移量（舞台 top 同值，保证同位交接） */
    val TopWithBackButton: Dp = 64.dp

    /** 名义圆角（纸卡不规则角的代表值，双翼 / 遮罩近似用） */
    val Corner: Dp = 18.dp

    /** 估算高度 = 画布高度比例 */
    const val HEIGHT_FRACTION = 0.46f

    /** 估算高度上限 */
    val MaxHeight: Dp = 440.dp

    fun leftPx(density: Density): Float = with(density) { HorizontalMargin.toPx() }

    fun topPx(density: Density): Float = with(density) { TopWithBackButton.toPx() }

    /** 卡片宽度 = 画布宽 − 两侧边距（非负钳制） */
    fun widthPx(density: Density, canvasWidth: Float): Float =
        (canvasWidth - with(density) { HorizontalMargin.toPx() * 2f }).coerceAtLeast(0f)

    /** 估算高度（实测通道缺失时的兜底：min(画布高 × 比例, 上限)，非负钳制） */
    fun estimatedHeightPx(density: Density, canvasHeight: Float): Float =
        minOf(canvasHeight * HEIGHT_FRACTION, with(density) { MaxHeight.toPx() }).coerceAtLeast(0f)
}
