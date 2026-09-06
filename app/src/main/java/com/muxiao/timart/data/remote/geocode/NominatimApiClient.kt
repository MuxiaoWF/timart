package com.muxiao.timart.data.remote.geocode

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder

/**
 * Nominatim（OpenStreetMap 官方免费地址解析，免 Key）正/逆向解析。
 * 仅发送地点文字/坐标参数，无任何用户标识；单次用户触发的低频请求符合官方使用政策；
 * 任何网络/解析异常返回空列表或 null，由解析链下一级兜底。
 */
class NominatimApiClient {

    /** 正向解析：地点文字 → 候选列表（阻塞网络调用，调用方自行切 IO 线程） */
    fun search(query: String, acceptLanguage: String?): List<GeoSuggestion> {
        val lang = acceptLanguage?.let { "&accept-language=${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
        val url = "$BASE_URL/search?q=${URLEncoder.encode(query, "UTF-8")}&format=jsonv2&limit=5$lang"
        return httpGetString(url)?.let { parseSearch(it) }.orEmpty()
    }

    /** 逆向解析：坐标 → 可读地址（失败返回 null） */
    fun reverse(lat: Double, lng: Double, acceptLanguage: String?): GeoSuggestion? {
        val lang = acceptLanguage?.let { "&accept-language=${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
        val url = "$BASE_URL/reverse?lat=$lat&lon=$lng&format=jsonv2&zoom=18$lang"
        return httpGetString(url)?.let { parseReverse(it) }
    }

    /** 纯函数：jsonv2 数组 → 候选列表（internal 供纯 JVM 测试） */
    internal fun parseSearch(body: String): List<GeoSuggestion> =
        runCatching { json.decodeFromString<List<NominatimPlace>>(body) }
            .getOrDefault(emptyList())
            .mapNotNull { it.toSuggestion() }

    /** 纯函数：jsonv2 单对象 → 候选（internal 供纯 JVM 测试） */
    internal fun parseReverse(body: String): GeoSuggestion? =
        runCatching { json.decodeFromString<NominatimPlace>(body) }
            .getOrNull()
            ?.toSuggestion()

    private fun NominatimPlace.toSuggestion(): GeoSuggestion? {
        val latValue = lat.toDoubleOrNull() ?: return null
        val lngValue = lon.toDoubleOrNull() ?: return null
        val label = displayName?.takeIf { it.isNotBlank() } ?: return null
        return GeoSuggestion(latValue, lngValue, label)
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    internal data class NominatimPlace(
        val lat: String = "",
        val lon: String = "",
        @SerialName("display_name") val displayName: String? = null,
    )

    private companion object {
        const val BASE_URL = "https://nominatim.openstreetmap.org"
    }
}
