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
    /** 当前风向（度，气象惯例 0 = 北；储备池 v4 风向条件通道） */
    val windDirectionDeg: Int? = null,
    /** 今日降水概率上限（%，Open-Meteo daily 字段） */
    val precipProbPercent: Int? = null,
    /** 昨日日均温（°C，past_days=1；降温条件通道） */
    val yesterdayMeanTempC: Double? = null,
    /**
     * 今日之前的降雪日计数（Open-Meteo daily weathercode 历史窗口，储备池 v5 首雪条件通道）；
     * null = 接口缺字段 / 解析失败（首雪判定 fail-closed 按指标不可用）
     */
    val snowDaysPast: Int? = null,
    /**
     * 截至昨日的连续降雨天数（雨 = 毛雨/雨/雷，储备池 v6 连续降雨条件通道；
     * 历史窗口同 snowDaysPast 的 daily weathercode）；
     * null = 接口缺字段 / 解析失败（判定 fail-closed 按指标不可用）
     */
    val rainStreakPast: Int? = null,
    /** 当前体感温度（°C，Open-Meteo apparent_temperature；储备池 v7 WeatherMetric.APPARENT 通道） */
    val feelsLikeC: Double? = null,
    /**
     * 今日之前的雷暴日计数（储备池 v7 今季首雷条件通道，历史窗口同 snowDaysPast）；
     * null = 接口缺字段 / 解析失败（判定 fail-closed 按指标不可用）
     */
    val thunderDaysPast: Int? = null,
    /**
     * 昨日日均气压（hPa，Open-Meteo daily pressure_msl_mean，储备池 v7 气压骤降条件通道）；
     * null = 接口缺字段 / 解析失败（判定 fail-closed 按指标不可用）
     */
    val yesterdayMeanPressureHpa: Double? = null,
)
