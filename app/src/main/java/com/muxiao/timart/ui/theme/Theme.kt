package com.muxiao.timart.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * TimartTheme：恒定深色（无浅色主题），状态栏样式由 themes.xml 启动主题承担。
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
    MaterialTheme(
        colorScheme = TimartColorScheme,
        typography = TimartTypography,
        shapes = TimartShapes,
        content = content,
    )
}
