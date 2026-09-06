package com.muxiao.timart.data.remote.geocode

/**
 * 地址解析结果（GPS 条件表单 UI 消费）：WGS-84 坐标 + 可读标签。
 * 解析链三级（原生 Geocoder / Nominatim / Photon）统一输出此模型，
 * 坐标协议与判定链一致（WGS-84），无需任何坐标转换。
 */
data class GeoSuggestion(
    val lat: Double,
    val lng: Double,
    val label: String,
)
