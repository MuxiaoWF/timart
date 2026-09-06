package com.muxiao.timart.data.remote.geocode

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder

/**
 * 天地图地理编码 V2.0（国内官方服务；CGCS2000 与 WGS-84 实用精度一致，免坐标转换），
 * 作为解析链最后一级兜底：国外两级（Nominatim/Photon）在境内可达性不佳时的保底。
 * 免费 tk 经 local.properties → BuildConfig 注入；tk 缺失（空串）时本级整体跳过。
 * 请求形态：正向 `ds={"keyWord":...}`，逆向 `postStr={"lon":..,"lat":..,"ver":1}`，
 * 响应 `status=="0"` 即成功。明文流量已全局禁用，故强制 https。
 * 任何网络/解析异常返回空列表或 null。
 */
class TiandituApiClient(private val tk: String) {

    /** 正向解析：地点文字 → 候选（地理编码返回单一最优，0..1 条；阻塞调用，调用方自行切 IO 线程） */
    fun search(query: String): List<GeoSuggestion> {
        if (tk.isBlank()) return emptyList()
        val ds = URLEncoder.encode("""{"keyWord":"${jsonEscape(query)}"}""", "UTF-8").replace("+", "%20")
        return httpGetString("$BASE_URL/geocoder?ds=$ds&tk=$tk")?.let { parseSearch(it) }.orEmpty()
    }

    /** 逆向解析：坐标 → 可读地址（失败返回 null；坐标回填请求值） */
    fun reverse(lat: Double, lng: Double): GeoSuggestion? {
        if (tk.isBlank()) return null
        val postStr = URLEncoder.encode("""{"lon":$lng,"lat":$lat,"ver":1}""", "UTF-8").replace("+", "%20")
        val label = httpGetString("$BASE_URL/geocoder?postStr=$postStr&tk=$tk")?.let { parseReverseLabel(it) }
        return label?.let { GeoSuggestion(lat, lng, it) }
    }

    /** 纯函数：响应 → 候选列表（internal 供纯 JVM 测试；status 非 "0"/缺坐标/缺地址一律过滤） */
    internal fun parseSearch(body: String): List<GeoSuggestion> {
        val response = runCatching { json.decodeFromString<TdtResponse>(body) }.getOrNull() ?: return emptyList()
        if (response.status != STATUS_OK) return emptyList()
        val location = response.result?.location ?: return emptyList()
        val label = response.result.formattedAddress?.takeIf { it.isNotBlank() } ?: return emptyList()
        return listOf(GeoSuggestion(location.lat, location.lon, label))
    }

    /** 纯函数：逆向响应 → 可读地址（internal 供纯 JVM 测试） */
    internal fun parseReverseLabel(body: String): String? {
        val response = runCatching { json.decodeFromString<TdtResponse>(body) }.getOrNull() ?: return null
        if (response.status != STATUS_OK) return null
        return response.result?.formattedAddress?.takeIf { it.isNotBlank() }
    }

    /** keyWord 内的引号/反斜杠转义，防地点文字破坏 ds 参数的 JSON 结构 */
    private fun jsonEscape(text: String): String =
        text.replace("\\", "\\\\").replace("\"", "\\\"")

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    internal data class TdtResponse(
        val status: String? = null,
        val msg: String? = null,
        val result: TdtResult? = null,
    )

    @Serializable
    internal data class TdtResult(
        @SerialName("formatted_address") val formattedAddress: String? = null,
        val location: TdtLocation? = null,
    )

    @Serializable
    internal data class TdtLocation(val lon: Double = 0.0, val lat: Double = 0.0)

    private companion object {
        const val BASE_URL = "https://api.tianditu.gov.cn"
        const val STATUS_OK = "0"
    }
}
