package com.muxiao.timart.utils.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * WGS-84（GPS/存储/判定）↔ BD-09（百度地图展示与选点）坐标转换（纯函数，可 JVM 单测）。
 *
 * 链路：WGS-84 → GCJ-02（国测局偏移）→ BD-09（百度二次偏移），以及各自的近似逆变换。
 * GCJ-02↔WGS-84 为数值近似（迭代一次），残余误差约 1–2 米，远小于民用 GPS 5–50 米误差；
 * 境外（GCJ-02 偏移范围之外）三种坐标系一致，直接透传。
 */
object CoordTransform {

    private const val X_PI = Math.PI * 3000.0 / 180.0
    private const val RADIUS = 6378245.0 // 克拉索夫斯基椭球长半轴
    private const val EE = 0.00669342162296594323 // 第一偏心率平方

    /** WGS-84 → BD-09（GPS 实际位置 → 百度地图展示点） */
    fun wgs84ToBd09(lat: Double, lng: Double): Pair<Double, Double> {
        val (gLat, gLng) = wgs84ToGcj02(lat, lng)
        return gcj02ToBd09(gLat, gLng)
    }

    /** BD-09 → WGS-84（百度地图选点 → 存储判定坐标） */
    fun bd09ToWgs84(lat: Double, lng: Double): Pair<Double, Double> {
        val (gLat, gLng) = bd09ToGcj02(lat, lng)
        return gcj02ToWgs84(gLat, gLng)
    }

    private fun outOfChina(lat: Double, lng: Double): Boolean =
        lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271

    private fun wgs84ToGcj02(lat: Double, lng: Double): Pair<Double, Double> {
        if (outOfChina(lat, lng)) return lat to lng
        var dLat = transformLat(lng - 105.0, lat - 35.0)
        var dLng = transformLng(lng - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * Math.PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((RADIUS * (1 - EE)) / (magic * sqrtMagic) * Math.PI)
        dLng = (dLng * 180.0) / (RADIUS / sqrtMagic * cos(radLat) * Math.PI)
        return (lat + dLat) to (lng + dLng)
    }

    private fun gcj02ToWgs84(lat: Double, lng: Double): Pair<Double, Double> {
        if (outOfChina(lat, lng)) return lat to lng
        // 数值近似逆：把点当作 WGS 再正向算一遍，取偏移差的反向补偿
        val (gLat, gLng) = wgs84ToGcj02(lat, lng)
        return (lat * 2 - gLat) to (lng * 2 - gLng)
    }

    private fun gcj02ToBd09(lat: Double, lng: Double): Pair<Double, Double> {
        val z = sqrt(lng * lng + lat * lat) + 0.00002 * sin(lat * X_PI)
        val theta = atan2(lat, lng) + 0.000003 * cos(lng * X_PI)
        return (z * sin(theta) + 0.006) to (z * cos(theta) + 0.0065)
    }

    private fun bd09ToGcj02(lat: Double, lng: Double): Pair<Double, Double> {
        val x = lng - 0.0065
        val y = lat - 0.006
        val z = sqrt(x * x + y * y) - 0.00002 * sin(y * X_PI)
        val theta = atan2(y, x) - 0.000003 * cos(x * X_PI)
        return (z * sin(theta)) to (z * cos(theta))
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y +
            0.2 * sqrt(kotlin.math.abs(x))
        ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * Math.PI) + 40.0 * sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * Math.PI) + 320.0 * sin(y * Math.PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y +
            0.1 * sqrt(kotlin.math.abs(x))
        ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * Math.PI) + 40.0 * sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * Math.PI) + 300.0 * sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
        return ret
    }
}
