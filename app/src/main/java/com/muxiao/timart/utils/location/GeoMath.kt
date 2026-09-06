package com.muxiao.timart.utils.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 球面几何纯函数（GPS 条件半径判定与地图画布共用，可单测）。
 * 该对象零 Android 依赖，允许被 domain 层直接复用（保持单一实现来源）。
 */
object GeoMath {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /** haversine 球面距离（米） */
    fun haversineDistanceMeters(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double,
    ): Double {
        val radLat1 = Math.toRadians(lat1)
        val radLat2 = Math.toRadians(lat2)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(radLat1) * cos(radLat2) * sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /** 是否落在以 (centerLat, centerLng) 为圆心 [radiusMeters] 的范围内 */
    fun withinRadius(
        lat: Double,
        lng: Double,
        centerLat: Double,
        centerLng: Double,
        radiusMeters: Int,
    ): Boolean = haversineDistanceMeters(lat, lng, centerLat, centerLng) <= radiusMeters
}
