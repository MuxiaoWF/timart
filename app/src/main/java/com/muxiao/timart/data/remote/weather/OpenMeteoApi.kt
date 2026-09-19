package com.muxiao.timart.data.remote.weather

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Open-Meteo 当前天气 API（免费、无 Key、仅经纬度参数，不携带任何用户标识）。
 * HttpURLConnection + org.json，10s 超时；任何异常返回 null（判定侧按"获取失败"处理）。
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
    )

    fun fetchCurrentWeather(latitude: Double, longitude: Double): CurrentWeather? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$latitude&longitude=$longitude" +
                    "&current_weather=true" +
                    "&current=relative_humidity_2m,surface_pressure,wind_speed_10m,wind_direction_10m" +
                    "&hourly=uv_index" +
                    "&daily=temperature_2m_mean,precipitation_probability_max" +
                    "&past_days=1&forecast_days=2",
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
            // UV：当日逐小时数组取当前小时（本地时区小时序号，Open-Meteo 默认 auto timezone）
            val uvIndex = runCatching {
                val hourly = root.getJSONObject("hourly")
                val arr = hourly.getJSONArray("uv_index")
                val hour = java.time.LocalTime.now().hour.coerceIn(0, arr.length() - 1)
                arr.optDouble(hour)
            }.getOrNull().takeIf { it?.isNaN() == false }
            // 昨日均温 / 今日降水概率：daily 数组含 [昨日, 今日]（past_days=1），取倒数第二/最后一项
            val daily = root.optJSONObject("daily")
            val yesterdayMean = runCatching {
                val arr = daily?.getJSONArray("temperature_2m_mean") ?: error("无 daily")
                arr.optDouble(arr.length() - 2)
            }.getOrNull().takeIf { it?.isNaN() == false }
            val precipProb = runCatching {
                val arr = daily?.getJSONArray("precipitation_probability_max") ?: error("无 daily")
                val value = arr.opt(arr.length() - 1)
                if (value == null || value == JSONObject.NULL) null else (value as Number).toInt()
            }.getOrNull()
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
}
