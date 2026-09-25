package com.muxiao.timart.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import com.muxiao.timart.utils.RuntimeSettings

/**
 * TimartTheme：恒定深色（无浅色主题），状态栏样式由 themes.xml 启动主题承担。
 * 昼夜暖色变体（N17，开关默认关）：清晨/黄昏时段根部叠加极低 alpha 暖色层，
 * 只读本地时钟（组合期评估一次，导航重组 / 回前台再组合时自然刷新），零网络。
 */
private val TimartColorScheme = darkColorScheme(
    primary = TimeGold,
    onPrimary = DeepCharcoal,
    primaryContainer = SurfaceRaise,
    onPrimaryContainer = InkPrimary,
    secondary = GlowGold,
    onSecondary = DeepCharcoal,
    background = DeepCharcoal,
    onBackground = InkPrimary,
    surface = SurfaceRaise,
    onSurface = InkPrimary,
    surfaceVariant = TrackHairline,
    onSurfaceVariant = InkSecondary,
    outline = InkDisabled,
    error = TimeGold, // 状态表达禁用错误红，借用主强调色
    onError = DeepCharcoal,
)

@Composable
fun TimartTheme(content: @Composable () -> Unit) {
    // 暖色叠加层颜色：开关关 / 非晨昏时段为 null。组合期评估一次（相位粒度足够：
    // 窗口以小时计，无需时钟驱动重组）
    val warmOverlay = remember { if (RuntimeSettings.dawnDuskTint) DawnDuskTint.overlayFor(java.time.LocalTime.now()) else null }
    MaterialTheme(
        colorScheme = TimartColorScheme,
        typography = TimartTypography,
        shapes = TimartShapes,
    ) {
        if (warmOverlay == null) {
            content()
        } else {
            Box(
                modifier = Modifier.drawWithContent {
                    drawContent()
                    // SoftLight：保对比度的暖色浸染（比 Plus 亮化克制，比 Multiply 柔和）
                    drawRect(color = warmOverlay, blendMode = BlendMode.Softlight)
                },
            ) {
                content()
            }
        }
    }
}
