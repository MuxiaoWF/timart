package com.muxiao.timart.data.remote.weather

import com.muxiao.timart.domain.model.WeatherType

/**
 * WMO 天气代码 → 应用内天气类型（纯函数，可 JVM 单测）。
 * 语义对齐 ARCHITECTURE §2.11：0 晴；1-3 多云；45/48 雾；51-57 毛雨；
 * 61-67/80-82 雨；71-77/85-86 雪；95-99 雷；其余代码回退多云。
 */
object WmoCodeMapper {

    fun map(code: Int): WeatherType = when {
        code == 0 -> WeatherType.CLEAR
        code in 1..3 -> WeatherType.CLOUDY
        code == 45 || code == 48 -> WeatherType.FOG
        code in 51..57 -> WeatherType.DRIZZLE
        code in 61..67 -> WeatherType.RAIN
        isSnow(code) -> WeatherType.SNOW
        code in 80..82 -> WeatherType.RAIN
        code in 95..99 -> WeatherType.THUNDER
        else -> WeatherType.CLOUDY
    }

    /** 雪码判定（71-77 雪系 + 85/86 阵雪；储备池 v5 首雪条件的历史日计数用，可 JVM 单测） */
    fun isSnow(code: Int): Boolean = code in 71..77 || code == 85 || code == 86

    /** 雷暴码判定（95-99；储备池 v7 今季首雷条件的历史日计数用，可 JVM 单测） */
    fun isThunder(code: Int): Boolean = code in 95..99

    /**
     * 雨码判定（51-57 毛雨 + 61-67/80-82 雨 + 95-99 雷；储备池 v6 连续降雨条件的历史
     * 日计数用，可 JVM 单测）。与 `UnlockJudgeUseCase.RAIN_TYPES` 的类型口径一致：
     * 判定侧按 `map(code) in RAIN_TYPES`，本函数是历史数组上的同语义纯函数快捷方式。
     */
    fun isRain(code: Int): Boolean = code in 51..57 || code in 61..67 || code in 80..82 || code in 95..99

    /**
     * 截至昨日（下标 [todayIdx] - 1）的连续降雨天数（储备池 v6 连续降雨条件通道；
     * 纯函数可 JVM 单测）。从昨日往回数连续 [isRain] 日，遇非雨日或窗口头即停。
     */
    fun rainStreakPast(dailyCodes: List<Int>, todayIdx: Int): Int {
        if (todayIdx <= 0) return 0
        var streak = 0
        var i = todayIdx - 1
        while (i >= 0 && isRain(dailyCodes[i])) {
            streak++
            i--
        }
        return streak
    }
}
