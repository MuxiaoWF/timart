package com.muxiao.timart.utils.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** haversine 已知距离对拍 */
class GeoMathTest {

    /** 北京天安门 ↔ 上海人民广场，理论球面距离约 1067 km */
    @Test
    fun beijingToShanghaiAbout1067km() {
        val distance = GeoMath.haversineDistanceMeters(
            lat1 = 39.9042,
            lng1 = 116.4074,
            lat2 = 31.2304,
            lng2 = 121.4737,
        )
        assertEquals(1_067_000.0, distance, 3_000.0)
    }

    /** 同一点距离为 0 */
    @Test
    fun samePointIsZero() {
        val distance = GeoMath.haversineDistanceMeters(35.6812, 139.7671, 35.6812, 139.7671)
        assertEquals(0.0, distance, 0.000_001)
    }

    /** 赤道上经度差 1° ≈ 111.19 km */
    @Test
    fun oneDegreeLongitudeAtEquator() {
        val distance = GeoMath.haversineDistanceMeters(0.0, 0.0, 0.0, 1.0)
        assertEquals(111_194.9, distance, 500.0)
    }

    /** 纬度方向 1° ≈ 111.19 km（子午线） */
    @Test
    fun oneDegreeLatitudeOnMeridian() {
        val distance = GeoMath.haversineDistanceMeters(0.0, 0.0, 1.0, 0.0)
        assertEquals(111_194.9, distance, 500.0)
    }

    /** 东京站半径 300m 场景：站内满足、2km 外不满足 */
    @Test
    fun withinRadiusSemantics() {
        // 东京站 35.6812,139.7671；丸之内侧约 150m 处的点
        assertTrue(GeoMath.withinRadius(35.6823, 139.7671, 35.6812, 139.7671, 300))
        // 东京塔（约 2km 外）
        assertFalse(GeoMath.withinRadius(35.6586, 139.7454, 35.6812, 139.7671, 300))
    }

    /** 对跖点距离约为地球周长的一半 ≈ 20015 km */
    @Test
    fun antipodalPoints() {
        val distance = GeoMath.haversineDistanceMeters(0.0, 0.0, 0.0, 180.0)
        assertEquals(20_015_086.0, distance, 5_000.0)
    }
}
