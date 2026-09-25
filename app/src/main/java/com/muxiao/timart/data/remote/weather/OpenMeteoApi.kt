package com.muxiao.timart.data.remote.weather

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Open-Meteo 当前天气 API（免费、无 Key、仅经纬度参数，不携带任何用户标识）。
 * HttpURLConnection + org.json，10s 超时；任何异常返回 null（判定侧按"获取失败"处理）。
 *
 * daily 请求携带 weathercode 历史（`past_days=92`，储备池 v5 首雪条件的历史窗口：
 * 92 天内无雪记录 + 今日降雪 ≈ 今冬初雪）；所有 daily 数组一律按 `daily.time`
 * 日期锚定取值，不依赖数组位置（forecast_days/past_days 调整不影响语义）。
 */
class OpenMeteoApi {

    /** 当前天气数据集（扩展指标可空：接口缺字段/解析失败时为 null，判定侧按不可用处理） */
    data class CurrentWeather(
        val weatherCode: Int,
        val temperatureC: Double,
        val humidityPercent: Double? = null,
        val windKmh: Double? = null,
        val pressureHpa: Double? = null,
        val uvIndex: Double? = null,
        val windDirectionDeg: Int? = null,
        val precipProbPercent: Int? = null,
        val yesterdayMeanTempC: Double? = null,
        val snowDaysPast: Int? = null,
        val rainStreakPast: Int? = null,
        val feelsLikeC: Double? = null,
        val thunderDaysPast: Int? = null,
        val yesterdayMeanPressureHpa: Double? = null,
    )

    /** 未来逐日预报条目（储备池 v6 天气码 + v7 指标扩展；概率性预告通知通道） */
    data class DailyForecastEntry(
        val date: java.time.LocalDate,
        val weatherCode: Int,
        val tempMaxC: Double? = null,
        val tempMinC: Double? = null,
        val precipProbMax: Int? = null,
        val windDirDeg: Int? = null,
    )

    fun fetchCurrentWeather(latitude: Double, longitude: Double): CurrentWeather? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$latitude&longitude=$longitude" +
                    "&current_weather=true" +
                    "&current=relative_humidity_2m,surface_pressure,wind_speed_10m,wind_direction_10m,apparent_temperature" +
                    "&hourly=uv_index" +
                    "&daily=temperature_2m_mean,pressure_msl_mean,precipitation_probability_max,weathercode" +
                    "&past_days=92&forecast_days=1",
            )
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                requestMethod = "GET"
                connect()
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val currentWeather = root.getJSONObject("current_weather")
            val current = root.optJSONObject("current")
            val todayIso = java.time.LocalDate.now().toString()
            // UV：hourly.time 日期锚定取今日当前小时（past_days 扩大后数组起点不再是今天）
            val uvIndex = runCatching {
                val hourly = root.getJSONObject("hourly")
                val times = hourly.getJSONArray("time")
                val arr = hourly.getJSONArray("uv_index")
                var start = -1
                for (i in 0 until times.length()) {
                    if (times.getString(i).startsWith(todayIso)) {
                        start = i
                        break
                    }
                }
                if (start < 0) error("hourly 无今日条目")
                val hour = java.time.LocalTime.now().hour.coerceIn(0, 23)
                arr.optDouble((start + hour).coerceAtMost(arr.length() - 1))
            }.getOrNull().takeIf { it?.isNaN() == false }
            // daily 数组：日期锚定定位今日下标（取不到时间数组时退化为末位 = 今日，forecast_days=1）
            val daily = root.optJSONObject("daily")
            val dailyTimes = runCatching { daily?.getJSONArray("time") }.getOrNull()
            val todayIdx = if (dailyTimes != null) {
                var found = -1
                for (i in 0 until dailyTimes.length()) {
                    if (dailyTimes.getString(i) == todayIso) {
                        found = i
                        break
                    }
                }
                if (found >= 0) found else dailyTimes.length() - 1
            } else {
                -1
            }
            // 昨日均温：今日下标前一位（无昨日数据时为 null）
            val yesterdayMean = if (daily != null && todayIdx >= 1) {
                runCatching {
                    val arr = daily.getJSONArray("temperature_2m_mean")
                    arr.optDouble(todayIdx - 1)
                }.getOrNull().takeIf { it?.isNaN() == false }
            } else {
                null
            }
            // 今日降水概率上限
            val precipProb = if (daily != null && todayIdx >= 0) {
                runCatching {
                    val arr = daily.getJSONArray("precipitation_probability_max")
                    val value = arr.opt(todayIdx)
                    if (value == null || value == JSONObject.NULL) null else (value as Number).toInt()
                }.getOrNull()
            } else {
                null
            }
            // 今日之前的降雪日计数（首雪条件历史窗口；weathercode 数组缺失时为 null）
            val snowDaysPast = if (daily != null && todayIdx >= 0) {
                runCatching {
                    val arr = daily.getJSONArray("weathercode")
                    var count = 0
                    for (i in 0 until todayIdx) {
                        if (WmoCodeMapper.isSnow(arr.optInt(i, -1))) count++
                    }
                    count
                }.getOrNull()
            } else {
                null
            }
            // 截至昨日的连续降雨天数（连续降雨条件历史窗口；数组缺失时为 null）
            val rainStreakPast = if (daily != null && todayIdx >= 0) {
                runCatching {
                    val arr = daily.getJSONArray("weathercode")
                    val codes = (0 until arr.length()).map { arr.optInt(it, -1) }
                    WmoCodeMapper.rainStreakPast(codes, todayIdx)
                }.getOrNull()
            } else {
                null
            }
            // 今日之前的雷暴日计数（今季首雷条件历史窗口；数组缺失时为 null）
            val thunderDaysPast = if (daily != null && todayIdx >= 0) {
                runCatching {
                    val arr = daily.getJSONArray("weathercode")
                    var count = 0
                    for (i in 0 until todayIdx) {
                        if (WmoCodeMapper.isThunder(arr.optInt(i, -1))) count++
                    }
                    count
                }.getOrNull()
            } else {
                null
            }
            // 昨日日均气压（气压骤降条件基线；无昨日数据时为 null）
            val yesterdayMeanPressure = if (daily != null && todayIdx >= 1) {
                runCatching {
                    val arr = daily.getJSONArray("pressure_msl_mean")
                    arr.optDouble(todayIdx - 1)
                }.getOrNull().takeIf { it?.isNaN() == false }
            } else {
                null
            }
            CurrentWeather(
                weatherCode = currentWeather.getInt("weathercode"),
                temperatureC = currentWeather.getDouble("temperature"),
                humidityPercent = current?.optDouble("relative_humidity_2m")?.takeUnless { it.isNaN() },
                windKmh = currentWeather.optDouble("windspeed").takeUnless { it.isNaN() },
                pressureHpa = current?.optDouble("surface_pressure")?.takeUnless { it.isNaN() },
                uvIndex = uvIndex,
                windDirectionDeg = currentWeather.optInt("winddirection", -1).takeIf { it >= 0 },
                precipProbPercent = precipProb,
                yesterdayMeanTempC = yesterdayMean,
                snowDaysPast = snowDaysPast,
                rainStreakPast = rainStreakPast,
                feelsLikeC = current?.optDouble("apparent_temperature")?.takeUnless { it.isNaN() },
                thunderDaysPast = thunderDaysPast,
                yesterdayMeanPressureHpa = yesterdayMeanPressure,
            )
        } catch (_: Throwable) {
            // 网络/解析异常一律吞掉返回 null：天气失败不阻断判定与创建
            null
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000
    }

    /**
     * 未来 [days] 天逐日预报（储备池 v6 概率性预告通知通道；仅城市坐标参数，无 Key）。
     * daily 含天气码 + 气温极值 / 降水概率 / 主导风向（储备池 v7 指标类预告）。
     * 与 [fetchCurrentWeather] 独立请求（forecast 模式），解析失败/网络异常返回 null。
     * 预报只是「预告」不是判定：不触发解锁，仅供提醒文案（fail-quiet）。
     */
    fun fetchDailyForecast(latitude: Double, longitude: Double, days: Int): List<DailyForecastEntry>? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$latitude&longitude=$longitude" +
                    "&daily=weathercode,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_direction_10m_dominant" +
                    "&forecast_days=${days.coerceIn(1, 7)}&past_days=0",
            )
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                requestMethod = "GET"
                connect()
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val daily = JSONObject(body).getJSONObject("daily")
            val times = daily.getJSONArray("time")
            val codes = daily.getJSONArray("weathercode")
            fun doubleArray(name: String) = runCatching { daily.getJSONArray(name) }.getOrNull()
            fun intArray(name: String) = runCatching { daily.getJSONArray(name) }.getOrNull()
            val tempMaxArr = doubleArray("temperature_2m_max")
            val tempMinArr = doubleArray("temperature_2m_min")
            val precipArr = intArray("precipitation_probability_max")
            val windDirArr = intArray("wind_direction_10m_dominant")
            (0 until times.length()).mapNotNull { i ->
                runCatching {
                    fun doubleAt(arr: org.json.JSONArray?) = arr?.opt(i)
                        ?.takeIf { it != JSONObject.NULL }
                        ?.let { (it as Number).toDouble() }
                        ?.takeUnless { it.isNaN() }
                    fun intAt(arr: org.json.JSONArray?) = arr?.opt(i)
                        ?.takeIf { it != JSONObject.NULL }
                        ?.let { (it as Number).toInt() }
                    DailyForecastEntry(
                        date = java.time.LocalDate.parse(times.getString(i)),
                        weatherCode = codes.getInt(i),
                        tempMaxC = doubleAt(tempMaxArr),
                        tempMinC = doubleAt(tempMinArr),
                        precipProbMax = intAt(precipArr),
                        windDirDeg = intAt(windDirArr)?.takeIf { it >= 0 },
                    )
                }.getOrNull()
            }.takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
