package com.muxiao.timart.data.remote.weather

import com.muxiao.timart.domain.model.WeatherType

/**
 * WMO 天气代码 → 应用内天气类型（纯函数，可 JVM 单测）。
 * 语义对齐 ARCHITECTURE §2.11：0 晴；1-3 多云；45/48 雾；51-57 毛雨；
 * 61-67/80-82 雨；71-77/85-86 雪；95-99 雷；其余代码回退多云。
 */
object WmoCodeMapper {

    fun map(code: Int): WeatherType = when (code) {
        0 -> WeatherType.CLEAR
        in 1..3 -> WeatherType.CLOUDY
        45, 48 -> WeatherType.FOG
        in 51..57 -> WeatherType.DRIZZLE
        in 61..67 -> WeatherType.RAIN
        in 71..77 -> WeatherType.SNOW
        in 80..82 -> WeatherType.RAIN
        85, 86 -> WeatherType.SNOW
        in 95..99 -> WeatherType.THUNDER
        else -> WeatherType.CLOUDY
    }
}
