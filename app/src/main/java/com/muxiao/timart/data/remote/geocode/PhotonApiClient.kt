package com.muxiao.timart.data.remote.geocode

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder

/**
 * Photon（komoot 的 OSM 地址解析服务，免 Key、面向搜索场景）正/逆向解析，
 * 作为解析链在 Nominatim 之后的最后一级兜底。
 * 返回 GeoJSON：features[].geometry.coordinates = [lng, lat]（注意经度在前）；
 * 任何网络/解析异常返回空列表或 null。
 */
class PhotonApiClient {

    /** 正向解析：地点文字 → 候选列表（阻塞网络调用，调用方自行切 IO 线程） */
    fun search(query: String, lang: String?): List<GeoSuggestion> {
        val langPart = lang?.let { "&lang=${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
        val url = "$BASE_URL/api/?q=${URLEncoder.encode(query, "UTF-8")}&limit=5$langPart"
        return httpGetString(url)?.let { parseSearch(it) }.orEmpty()
    }

    /** 逆向解析：坐标 → 可读地址（失败返回 null） */
    fun reverse(lat: Double, lng: Double, lang: String?): GeoSuggestion? {
        val langPart = lang?.let { "&lang=${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
        val url = "$BASE_URL/reverse?lat=$lat&lon=$lng$langPart"
        return httpGetString(url)?.let { parseSearch(it).firstOrNull() }
    }

    /** 纯函数：GeoJSON → 候选列表（internal 供纯 JVM 测试） */
    internal fun parseSearch(body: String): List<GeoSuggestion> =
        runCatching { json.decodeFromString<PhotonResponse>(body) }
            .getOrDefault(PhotonResponse())
            .features
            .mapNotNull { it.toSuggestion() }

    private fun PhotonFeature.toSuggestion(): GeoSuggestion? {
        val lngValue = geometry.coordinates.getOrNull(0) ?: return null
        val latValue = geometry.coordinates.getOrNull(1) ?: return null
        val p = properties
        val primary = p.name?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(p.housenumber, p.street).joinToString(" ").takeIf { it.isNotBlank() }
            ?: return null
        val label = listOfNotNull(primary, p.city ?: p.county, p.state, p.country)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" · ")
        return GeoSuggestion(latValue, lngValue, label)
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    internal data class PhotonResponse(val features: List<PhotonFeature> = emptyList())

    @Serializable
    internal data class PhotonFeature(
        val geometry: PhotonGeometry = PhotonGeometry(),
        val properties: PhotonProperties = PhotonProperties(),
    )

    @Serializable
    internal data class PhotonGeometry(val coordinates: List<Double> = emptyList())

    @Serializable
    internal data class PhotonProperties(
        val name: String? = null,
        val street: String? = null,
        val housenumber: String? = null,
        val city: String? = null,
        val county: String? = null,
        val state: String? = null,
        val country: String? = null,
    )

    private companion object {
        const val BASE_URL = "https://photon.komoot.io"
    }
}
