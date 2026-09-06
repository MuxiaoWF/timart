package com.muxiao.timart.domain.model

import com.muxiao.timart.utils.location.GeoMath

/** 内置城市码表条目（assets/cities.json 冷加载后的内存形态；含中国城市与国外城市） */
data class City(
    val id: String,
    val name: String,
    val province: String,
    val lat: Double,
    val lng: Double,
    /** 拉丁文名（英文名；中国城市为拼音或空），支持英文关键词搜索 */
    val alias: String? = null,
)

/** 城市 × 关键词匹配（纯函数，可 JVM 单测）：中文名 / 省份（国外为国家）/ 拉丁别名（忽略大小写） */
fun City.matchesKeyword(rawKeyword: String): Boolean {
    val kw = rawKeyword.trim()
    if (kw.isEmpty()) return true
    if (name.contains(kw) || province.contains(kw)) return true
    return alias?.contains(kw, ignoreCase = true) == true
}

/** 距 (lat, lng) 最近的 [count] 个城市（城市选择器「定位附近」排序；纯函数，可 JVM 单测） */
fun List<City>.nearestTo(lat: Double, lng: Double, count: Int): List<City> =
    sortedBy { GeoMath.haversineDistanceMeters(lat, lng, it.lat, it.lng) }.take(count)

/** 应用内天气类型（由 WMO code 映射而来） */
enum class WeatherType {
    CLEAR,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    SNOW,
    THUNDER,
}

/** 创建时刻的天气快照（创建后不再因快照联网）；扩展指标可空（旧缓存/接口缺字段时为 null） */
data class WeatherSnapshot(
    val cityId: String,
    val cityName: String,
    val weatherType: WeatherType,
    val tempC: Double,
    val capturedAt: Long,
    val humidityPercent: Double? = null,
    val windKmh: Double? = null,
    val pressureHpa: Double? = null,
    val uvIndex: Double? = null,
)
