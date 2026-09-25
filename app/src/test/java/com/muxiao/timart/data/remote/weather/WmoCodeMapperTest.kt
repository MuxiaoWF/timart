package com.muxiao.timart.data.remote.weather

import com.muxiao.timart.domain.model.WeatherType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun isSnowMatchesSnowCodeRanges() {
        // 雪系 71-77 与阵雪 85/86（储备池 v5 首雪条件历史日计数口径）
        assertTrue(WmoCodeMapper.isSnow(71))
        assertTrue(WmoCodeMapper.isSnow(73))
        assertTrue(WmoCodeMapper.isSnow(77))
        assertTrue(WmoCodeMapper.isSnow(85))
        assertTrue(WmoCodeMapper.isSnow(86))
        // 雨 / 阵雨 / 雾 / 未定义码均非雪
        assertFalse(WmoCodeMapper.isSnow(61))
        assertFalse(WmoCodeMapper.isSnow(80))
        assertFalse(WmoCodeMapper.isSnow(82))
        assertFalse(WmoCodeMapper.isSnow(45))
        assertFalse(WmoCodeMapper.isSnow(79))
        assertFalse(WmoCodeMapper.isSnow(87))
        assertFalse(WmoCodeMapper.isSnow(-1))
    }

    @Test
    fun isRainMatchesRainCodeRanges() {
        // 毛雨 51-57 / 雨 61-67、80-82 / 雷 95-99（储备池 v6 连续降雨口径；雪不算雨）
        assertTrue(WmoCodeMapper.isRain(51))
        assertTrue(WmoCodeMapper.isRain(57))
        assertTrue(WmoCodeMapper.isRain(63))
        assertTrue(WmoCodeMapper.isRain(67))
        assertTrue(WmoCodeMapper.isRain(80))
        assertTrue(WmoCodeMapper.isRain(82))
        assertTrue(WmoCodeMapper.isRain(95))
        assertTrue(WmoCodeMapper.isRain(99))
        // 晴 / 云 / 雪 / 雾 / 未定义码均非雨
        assertFalse(WmoCodeMapper.isRain(0))
        assertFalse(WmoCodeMapper.isRain(3))
        assertFalse(WmoCodeMapper.isRain(73))
        assertFalse(WmoCodeMapper.isRain(86))
        assertFalse(WmoCodeMapper.isRain(45))
        assertFalse(WmoCodeMapper.isRain(79))
        assertFalse(WmoCodeMapper.isRain(-1))
    }

    @Test
    fun rainStreakPastCountsConsecutiveRainDaysBeforeToday() {
        // 今日是雨不算：todayIdx=6（今日 61），昨日下标 5 = 多云 → streak = 0
        assertEquals(0, WmoCodeMapper.rainStreakPast(listOf(0, 61, 61, 61, 0, 3, 61), 6))
        // 下标 0-5 全雨（毛雨/雨/雷混排），todayIdx=6 → 6
        assertEquals(6, WmoCodeMapper.rainStreakPast(listOf(61, 80, 51, 95, 63, 65, 61), 6))
        // 中途晴天截断：晴 雨 雨 雨（todayIdx=4）→ 从下标 3 往回 3 天雨 → 3
        assertEquals(3, WmoCodeMapper.rainStreakPast(listOf(0, 61, 61, 61, 0), 4))
        // 雪不算雨：雪 雨 雨（todayIdx=3）→ 2
        assertEquals(2, WmoCodeMapper.rainStreakPast(listOf(73, 61, 61, 61), 3))
        // todayIdx = 0（无昨日数据）→ 0
        assertEquals(0, WmoCodeMapper.rainStreakPast(listOf(61), 0))
    }

    @Test
    fun isThunderMatchesThunderCodeRanges() {
        // 雷暴 95-99（储备池 v7 今季首雷条件的历史日计数口径）
        assertTrue(WmoCodeMapper.isThunder(95))
        assertTrue(WmoCodeMapper.isThunder(99))
        assertTrue(WmoCodeMapper.isThunder(97))
        // 大雨夹雷 95 已覆盖；雨/雪/云/晴均非雷
        assertFalse(WmoCodeMapper.isThunder(65))
        assertFalse(WmoCodeMapper.isThunder(80))
        assertFalse(WmoCodeMapper.isThunder(73))
        assertFalse(WmoCodeMapper.isThunder(0))
        assertFalse(WmoCodeMapper.isThunder(-1))
    }
}
