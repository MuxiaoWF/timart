package com.muxiao.timart.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字体体系：标题用系统衬线回退（不打包字体文件，视觉验收接受机型差异）；
 * 正文系统无衬线，行高 1.6。
 */
object TimartType {

    /** 大标题 / 叙事（Serif，"时轨/写下/等待开启"页眉） */
    val displaySerif = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.5.sp,
    )

    /** 中标题（Serif） */
    val titleSerif = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.3.sp,
    )

    /** 页眉小注（细尘线旁的引导语） */
    val sectionNote = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 2.sp,
    )

    /** 正文 */
    val body = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
    )

    /** 辅助说明 */
    val caption = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )
}

/** Material3 Typography 映射（组件默认样式从体系取值） */
val TimartTypography = Typography(
    displayLarge = TimartType.displaySerif,
    displayMedium = TimartType.titleSerif,
    headlineSmall = TimartType.titleSerif,
    titleLarge = TimartType.titleSerif,
    titleMedium = TimartType.body.copy(fontWeight = FontWeight.Medium),
    bodyLarge = TimartType.body,
    bodyMedium = TimartType.body.copy(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TimartType.caption,
    labelLarge = TimartType.body.copy(fontWeight = FontWeight.Medium),
    labelMedium = TimartType.caption,
    labelSmall = TimartType.caption,
)
