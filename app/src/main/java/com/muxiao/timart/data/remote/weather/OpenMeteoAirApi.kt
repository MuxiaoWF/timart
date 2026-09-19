package com.muxiao.timart.data.remote.weather

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Open-Meteo Air Quality API（与天气同服务商，免费无 Key，仅经纬度参数）。
 * HttpURLConnection + org.json，10s 超时；任何异常返回 null（判定侧按"获取失败"处理）。
 * 采用 US AQI 口径（0–500，与 l10n 文案一致）。
 */
class OpenMeteoAirApi {

    fun fetchAqi(latitude: Double, longitude: Double): Int? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(
                "https://air-quality-api.open-meteo.com/v1/air-quality" +
                    "?latitude=$latitude&longitude=$longitude" +
                    "&current=us_aqi&timezone=auto",
            )
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                requestMethod = "GET"
                connect()
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val current = root.getJSONObject("current")
            val aqi = current.opt("us_aqi")
            if (aqi == null || aqi == JSONObject.NULL) null else (aqi as Number).toInt()
        } catch (_: Throwable) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000
    }
}
