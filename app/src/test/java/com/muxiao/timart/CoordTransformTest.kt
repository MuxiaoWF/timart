package com.muxiao.timart

import com.muxiao.timart.utils.location.CoordTransform
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * WGS-84 ↔ BD-09 坐标转换测试。
 * 基准：北京天安门 BD-09 (39.915077, 116.403887) ↔ WGS-84 (39.90733, 116.39129)。
 * 注意：BD-09 偏移是百度坐标系自带的，全球生效；只有 GCJ-02 偏移是国内专属。
 */
class CoordTransformTest {

    private fun assertClose(expected: Double, actual: Double, tol: Double, name: String) {
        assertTrue("$name: expected≈$expected actual=$actual", abs(expected - actual) < tol)
    }

    @Test
    fun `bd09 转回 wgs84 与公开基准一致`() {
        val (lat, lng) = CoordTransform.bd09ToWgs84(39.915077, 116.403887)
        assertClose(39.90733, lat, 5e-4, "lat")
        assertClose(116.39129, lng, 5e-4, "lng")
    }

    @Test
    fun `国内城市 wgs84 往返误差在 20 米内`() {
        val samples = listOf(
            39.9042 to 116.4074, // 北京
            31.2304 to 121.4737, // 上海
            23.1291 to 113.2644, // 广州
            30.5728 to 104.0668, // 成都
            45.8038 to 126.5350, // 哈尔滨
            22.5431 to 114.0579, // 深圳
        )
        samples.forEach { (lat, lng) ->
            val (bdLat, bdLng) = CoordTransform.wgs84ToBd09(lat, lng)
            val (backLat, backLng) = CoordTransform.bd09ToWgs84(bdLat, bdLng)
            assertClose(lat, backLat, 2e-4, "roundtrip lat@$lat")
            assertClose(lng, backLng, 2e-4, "roundtrip lng@$lng")
        }
    }

    @Test
    fun `国内偏移方向为东北向`() {
        val (bdLat, bdLng) = CoordTransform.wgs84ToBd09(39.9042, 116.4074)
        assertTrue("bd lat 应大于 wgs lat", bdLat > 39.9042)
        assertTrue("bd lng 应大于 wgs lng", bdLng > 116.4074)
    }

    @Test
    fun `境外坐标往返闭合_BD09偏移全球生效`() {
        // 纽约 / 伦敦 / 悉尼：GCJ 偏移透传，但 BD-09 偏移仍然施加（差 ~0.006°）——往返应闭合
        listOf(
            40.7128 to -74.0060,
            51.5074 to -0.1278,
            -33.8688 to 151.2093,
        ).forEach { (lat, lng) ->
            val (bdLat, bdLng) = CoordTransform.wgs84ToBd09(lat, lng)
            val (backLat, backLng) = CoordTransform.bd09ToWgs84(bdLat, bdLng)
            assertClose(lat, backLat, 2e-4, "overseas roundtrip lat@$lat")
            assertClose(lng, backLng, 2e-4, "overseas roundtrip lng@$lng")
        }
    }

    @Test
    fun `天安门 bd09 到 wgs 的偏移在 0_007 到 0_015 度之间`() {
        val (lat, lng) = CoordTransform.bd09ToWgs84(39.915077, 116.403887)
        val dLat = 39.915077 - lat
        val dLng = 116.403887 - lng
        assertTrue("dLat=$dLat", dLat in 0.007..0.015)
        assertTrue("dLng=$dLng", dLng in 0.007..0.015)
    }
}
