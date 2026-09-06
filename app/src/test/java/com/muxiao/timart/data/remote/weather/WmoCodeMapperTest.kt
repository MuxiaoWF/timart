package com.muxiao.timart.data.remote.weather

import com.muxiao.timart.domain.model.WeatherType
import org.junit.Assert.assertEquals
import org.junit.Test

/** WMO 边界码值映射全覆盖（含未知码回退） */
class WmoCodeMapperTest {

    private fun assertMaps(code: Int, expected: WeatherType) {
        assertEquals("WMO code $code", expected, WmoCodeMapper.map(code))
    }

    @Test
    fun clearSky() {
        assertMaps(0, WeatherType.CLEAR)
    }

    @Test
    fun cloudyBoundaries() {
        assertMaps(1, WeatherType.CLOUDY)
        assertMaps(2, WeatherType.CLOUDY)
        assertMaps(3, WeatherType.CLOUDY)
    }

    @Test
    fun fogBoundaries() {
        assertMaps(45, WeatherType.FOG)
        assertMaps(48, WeatherType.FOG)
    }

    @Test
    fun drizzleBoundaries() {
        assertMaps(51, WeatherType.DRIZZLE)
        assertMaps(55, WeatherType.DRIZZLE)
        assertMaps(57, WeatherType.DRIZZLE)
    }

    @Test
    fun rainBoundaries() {
        assertMaps(61, WeatherType.RAIN)
        assertMaps(65, WeatherType.RAIN)
        assertMaps(67, WeatherType.RAIN)
        assertMaps(80, WeatherType.RAIN)
        assertMaps(82, WeatherType.RAIN)
    }

    @Test
    fun snowBoundaries() {
        assertMaps(71, WeatherType.SNOW)
        assertMaps(75, WeatherType.SNOW)
        assertMaps(77, WeatherType.SNOW)
        assertMaps(85, WeatherType.SNOW)
        assertMaps(86, WeatherType.SNOW)
    }

    @Test
    fun thunderBoundaries() {
        assertMaps(95, WeatherType.THUNDER)
        assertMaps(97, WeatherType.THUNDER)
        assertMaps(99, WeatherType.THUNDER)
    }

    @Test
    fun unknownCodesFallBackToCloudy() {
        // 未定义区段：4-44、49-50、58-60、68-70、78-79、83-84、87-94、100+
        assertMaps(4, WeatherType.CLOUDY)
        assertMaps(44, WeatherType.CLOUDY)
        assertMaps(50, WeatherType.CLOUDY)
        assertMaps(58, WeatherType.CLOUDY)
        assertMaps(60, WeatherType.CLOUDY)
        assertMaps(68, WeatherType.CLOUDY)
        assertMaps(70, WeatherType.CLOUDY)
        assertMaps(79, WeatherType.CLOUDY)
        assertMaps(83, WeatherType.CLOUDY)
        assertMaps(84, WeatherType.CLOUDY)
        assertMaps(87, WeatherType.CLOUDY)
        assertMaps(94, WeatherType.CLOUDY)
        assertMaps(-1, WeatherType.CLOUDY)
        assertMaps(120, WeatherType.CLOUDY)
    }
}
