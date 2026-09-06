package com.muxiao.timart.utils.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import com.muxiao.timart.data.remote.geocode.GeoSuggestion
import java.util.Locale

/**
 * Android 原生 Geocoder 封装（地址解析链第一级）：
 * 由设备后端服务解析，应用自身零网络、零 Key。设备无后端服务（isPresent=false，
 * 常见于无 GMS 的国内 ROM）或任何异常时返回空，由解析链下一级（Nominatim/Photon）兜底。
 */
class NativeGeocoder(context: Context) {

    /** 正向解析：地点文字 → 候选列表（阻塞调用，调用方自行切 IO 线程） */
    fun search(query: String, locale: Locale, maxResults: Int = MAX_RESULTS): List<GeoSuggestion> {
        if (!Geocoder.isPresent()) return emptyList()
        return try {
            Geocoder(context, locale).getFromLocationName(query, maxResults)
                .orEmpty()
                .filter { it.hasLatitude() && it.hasLongitude() }
                .map { it.toSuggestion() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 逆向解析：坐标 → 可读地址（失败返回 null） */
    fun reverse(lat: Double, lng: Double, locale: Locale): GeoSuggestion? {
        if (!Geocoder.isPresent()) return null
        return try {
            Geocoder(context, locale).getFromLocation(lat, lng, 1)?.firstOrNull()?.toSuggestion()
        } catch (_: Exception) {
            null
        }
    }

    private fun Address.toSuggestion(): GeoSuggestion =
        GeoSuggestion(
            lat = latitude,
            lng = longitude,
            label = listOfNotNull(featureName, locality, adminArea, countryName)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" · "),
        )

    private val context: Context = context.applicationContext

    private companion object {
        const val MAX_RESULTS = 5
    }
}
