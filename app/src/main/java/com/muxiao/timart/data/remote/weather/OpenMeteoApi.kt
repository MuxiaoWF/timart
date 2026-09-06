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
    )

    fun fetchCurrentWeather(latitude: Double, longitude: Double): CurrentWeather? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$latitude&longitude=$longitude" +
                    "&current_weather=true" +
                    "&current=relative_humidity_2m,surface_pressure,wind_speed_10m" +
                    "&hourly=uv_index&forecast_days=1",
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
            CurrentWeather(
                weatherCode = currentWeather.getInt("weathercode"),
                temperatureC = currentWeather.getDouble("temperature"),
                humidityPercent = current?.optDouble("relative_humidity_2m")?.takeUnless { it.isNaN() },
                windKmh = currentWeather.optDouble("windspeed")?.takeUnless { it.isNaN() },
                pressureHpa = current?.optDouble("surface_pressure")?.takeUnless { it.isNaN() },
                uvIndex = uvIndex,
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
