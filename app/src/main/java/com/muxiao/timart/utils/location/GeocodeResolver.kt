package com.muxiao.timart.utils.location

import android.content.Context
import com.muxiao.timart.data.remote.geocode.GeoSuggestion
import com.muxiao.timart.data.remote.geocode.NominatimApiClient
import com.muxiao.timart.data.remote.geocode.PhotonApiClient
import com.muxiao.timart.data.remote.geocode.TiandituApiClient
import com.muxiao.timart.domain.model.Lang
import java.util.Locale

/**
 * GPS 条件表单的地址解析链（同步阻塞，调用方自行切 IO 线程）：
 * 原生 Geocoder（设备后端，应用零网络）→ Nominatim → Photon → 天地图（国内可达保底，tk 缺失自动跳过）。
 * 四级均为"失败返回空"的软兜底：某一级给出非空结果即短路返回，
 * 全部落空时由表单给出提示文案（可手动输入经纬度兜底）。
 */
class GeocodeResolver(context: Context, tiandituTk: String) {

    private val native = NativeGeocoder(context)
    private val nominatim = NominatimApiClient()
    private val photon = PhotonApiClient()
    private val tianditu = TiandituApiClient(tiandituTk)

    /** 正向解析：地点文字 → 候选坐标列表（最多 5 条） */
    fun search(query: String, lang: Lang): List<GeoSuggestion> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        native.search(trimmed, lang.toLocale()).takeIf { it.isNotEmpty() }?.let { return it }
        nominatim.search(trimmed, lang.toAcceptLanguage()).takeIf { it.isNotEmpty() }?.let { return it }
        photon.search(trimmed, lang.toPhotonLang()).takeIf { it.isNotEmpty() }?.let { return it }
        return tianditu.search(trimmed)
    }

    /** 逆向解析：坐标 → 可读地址（「使用当前位置」的展示文案，失败返回 null） */
    fun reverse(lat: Double, lng: Double, lang: Lang): GeoSuggestion? {
        native.reverse(lat, lng, lang.toLocale())?.let { return it }
        nominatim.reverse(lat, lng, lang.toAcceptLanguage())?.let { return it }
        photon.reverse(lat, lng, lang.toPhotonLang())?.let { return it }
        return tianditu.reverse(lat, lng)
    }
}

private fun Lang.toLocale(): Locale = when (this) {
    Lang.ZH_HANS -> Locale.SIMPLIFIED_CHINESE
    Lang.ZH_HANT -> Locale.TRADITIONAL_CHINESE
    Lang.EN -> Locale.ENGLISH
}

private fun Lang.toAcceptLanguage(): String = when (this) {
    Lang.ZH_HANS -> "zh-CN"
    Lang.ZH_HANT -> "zh-TW"
    Lang.EN -> "en"
}

private fun Lang.toPhotonLang(): String? = if (this == Lang.EN) "en" else null
