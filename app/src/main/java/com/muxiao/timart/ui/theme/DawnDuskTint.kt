package com.muxiao.timart.ui.theme

import androidx.compose.ui.graphics.Color
import java.time.LocalTime

/**
 * 昼夜暖色变体（N17，默认关）：清晨 5:00–9:00 / 黄昏 17:00–21:00 给主题一层极低 alpha
 * 的暖色叠加，只读本地时钟、零网络。纯函数可 JVM 单测；色相取自既有 Token（TimeGold 系），
 * 不新增高饱和色（Color.kt 头注红线），禁蓝紫渐变。
 *
 * 叠加而非逐屏替换 Token：全项目直接引用顶层 Token 常量，叠加层在根部一次生效，
 * 且对信笺纸色（PaperCream）同样成立——清晨偏晨光、黄昏偏烛光，语义自洽。
 */
object DawnDuskTint {

    /** 时段（null = 叠加不生效：深夜/白天） */
    enum class Phase { DAWN, DUSK }

    /** 清晨窗口 [5,9)；黄昏窗口 [17,21) */
    fun phaseOf(time: LocalTime): Phase? = when (time.hour) {
        in 5..8 -> Phase.DAWN
        in 17..20 -> Phase.DUSK
        else -> null
    }

    /**
     * 叠加色：清晨 TimeGold 提亮偏晨光（alpha 0.05），黄昏偏深琥珀（alpha 0.07，暗色下黄昏更易压暗）。
     * 用 SoftLight 语义在 draw 层实现；这里只给颜色与 alpha。
     */
    fun overlayFor(time: LocalTime): Color? = when (phaseOf(time)) {
        Phase.DAWN -> Color(0xFFE8B44A).copy(alpha = 0.05f)
        Phase.DUSK -> Color(0xFFC88A2E).copy(alpha = 0.07f)
        null -> null
    }
}
