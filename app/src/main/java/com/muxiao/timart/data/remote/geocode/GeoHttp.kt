package com.muxiao.timart.data.remote.geocode

import java.net.HttpURLConnection
import java.net.URL

/**
 * 地理编码 HTTP 通道（HttpURLConnection，与 OpenMeteoApi 同款零依赖模式）。
 * 任何网络/IO 异常返回 null，由解析链软兜底；
 * UA 标识应用以满足 Nominatim 官方使用政策（禁止匿名批量请求）。
 */
internal fun httpGetString(url: String, timeoutMillis: Int = TIMEOUT_MILLIS): String? {
    var connection: HttpURLConnection? = null
    return try {
        connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            connect()
        }
        connection.inputStream.bufferedReader().use { it.readText() }
    } catch (_: Throwable) {
        null
    } finally {
        connection?.disconnect()
    }
}

private const val USER_AGENT = "Timart/1.0 (Android; local-first time-capsule app)"
private const val TIMEOUT_MILLIS = 6_000
