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
 *
 * @Suppress("DEPRECATION") 说明：Geocoder 同步 API 在 API 33 废弃的理由仅是
 * "不应阻塞主线程"；本类契约即阻塞调用（调用方自行切 IO 线程），且作为同步解析链
 * 第一级，换用 Listener 异步版（API 33+）会破坏调用方契约，无收益。
 * 同步 API 在 API 33+ 仍正常工作，并非移除性废弃。
 */
@Suppress("DEPRECATION")
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
