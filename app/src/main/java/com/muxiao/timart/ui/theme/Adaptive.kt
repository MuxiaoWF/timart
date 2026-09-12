package com.muxiao.timart.ui.theme

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 窗口尺寸档位（横屏适配统一取数口，Material 3 断点）：
 * - **高度紧凑**（<480dp，典型为手机横屏）：竖向空间稀缺 —— 主导航底栏换左侧栏（`MainTabsScreen`），
 *   首页叙事行收起；
 * - **宽屏**（宽度 ≥600dp，平板/分屏/自由窗口）：横向充裕 —— 阅读面限宽居中（[WideContentMaxWidth]），
 *   星库网格自动加列。
 * 断点值只在本文件定义；页面不要各自拿窗口尺寸私设阈值，避免同机不同页断点漂移。
 */
data class WindowAdaptive(
    val widthClass: WindowWidthClass,
    val heightClass: WindowHeightClass,
) {
    /** 高度紧凑窗口（手机横屏 / 矮窗口）：竖向空间稀缺 */
    val isCompactHeight: Boolean get() = heightClass == WindowHeightClass.COMPACT

    companion object {
        /** Material 3 断点：宽 <600 / 600–839 / ≥840；高 <480 / 480–899 / ≥900（dp） */
        fun of(width: Dp, height: Dp): WindowAdaptive = WindowAdaptive(
            widthClass = when {
                width >= 840.dp -> WindowWidthClass.EXPANDED
                width >= 600.dp -> WindowWidthClass.MEDIUM
                else -> WindowWidthClass.COMPACT
            },
            heightClass = when {
                height >= 900.dp -> WindowHeightClass.EXPANDED
                height >= 480.dp -> WindowHeightClass.MEDIUM
                else -> WindowHeightClass.COMPACT
            },
        )
    }
}

/** 窗口宽度档位 */
enum class WindowWidthClass { COMPACT, MEDIUM, EXPANDED }

/** 窗口高度档位 */
enum class WindowHeightClass { COMPACT, MEDIUM, EXPANDED }

/**
 * 组合期读取当前窗口档位。取 `LocalWindowInfo.containerSize`（窗口实际容器像素）而非
 * `Configuration.screenWidthDp/screenHeightDp`：前者是本窗口的真实尺寸，
 * 分屏/自由窗口/预览下跟随窗口本身，也免于 Configuration 的系统条折算差异。
 */
@Composable
fun rememberWindowAdaptive(): WindowAdaptive {
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val widthDp = with(density) { containerSize.width.toDp() }
    val heightDp = with(density) { containerSize.height.toDp() }
    return remember(widthDp, heightDp) { WindowAdaptive.of(widthDp, heightDp) }
}

/** 宽屏阅读面统一限宽：行宽过长伤可读性，超宽后由父容器居中 */
val WideContentMaxWidth: Dp = 640.dp

/** 阅读面限宽：置于 `fillMaxSize()` **之前**（先收上限再撑满，超宽窗口才生效） */
fun Modifier.wideContentWidth(): Modifier = widthIn(max = WideContentMaxWidth)
