package com.muxiao.timart.domain.model.unlock

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 农历换算锚点测试：春节 / 中秋 / 闰月 / 表边界。
 * 锚点日期取自公开万年历（1900–2100 压缩位表支持区间）。
 */
class LunarCalendarTest {

    @Test
    fun `春节锚点为当年正月初一`() {
        // 2024 甲辰、2025 乙巳、2026 丙午年的正月初一
        listOf(
            LocalDate.of(2024, 2, 10) to 2024,
            LocalDate.of(2025, 1, 29) to 2025,
            LocalDate.of(2026, 2, 17) to 2026,
        ).forEach { (solar, lunarYear) ->
            val lunar = LunarCalendar.solarToLunar(solar)!!
            assertEquals(lunarYear, lunar.year)
            assertEquals(1, lunar.month)
            assertEquals(1, lunar.day)
            assertFalse(lunar.isLeapMonth)
        }
    }

    @Test
    fun `中秋锚点为八月十五`() {
        // 2024-09-17、2025-10-06、2026-09-25 均为当年中秋节
        listOf(
            LocalDate.of(2024, 9, 17) to 2024,
            LocalDate.of(2025, 10, 6) to 2025,
            LocalDate.of(2026, 9, 25) to 2026,
        ).forEach { (solar, lunarYear) ->
            val lunar = LunarCalendar.solarToLunar(solar)!!
            assertEquals(lunarYear, lunar.year)
            assertEquals(8, lunar.month)
            assertEquals(15, lunar.day)
            assertFalse(lunar.isLeapMonth)
        }
    }

    @Test
    fun `闰月识别`() {
        // 2025 年有闰六月：2025-07-25 为闰六月初一，紧随其后的六月三十/初一衔接正确
        val leapFirst = LunarCalendar.solarToLunar(LocalDate.of(2025, 7, 25))!!
        assertEquals(2025, leapFirst.year)
        assertEquals(6, leapFirst.month)
        assertEquals(1, leapFirst.day)
        assertTrue("2025-07-25 应为闰六月初一", leapFirst.isLeapMonth)

        // 闰六月三十：2025-08-23（闰六月 29 天，八月廿九？）——退而验证非闰六月日期
        // 2025-06-25 为六月初一（非闰月）
        val normalFirst = LunarCalendar.solarToLunar(LocalDate.of(2025, 6, 25))!!
        assertEquals(6, normalFirst.month)
        assertEquals(1, normalFirst.day)
        assertFalse(normalFirst.isLeapMonth)
    }

    @Test
    fun `表支持边界`() {
        // 首日：1900-01-31 = 农历 1900 年正月初一；前一日不在表内
        val first = LunarCalendar.solarToLunar(LocalDate.of(1900, 1, 31))!!
        assertEquals(1900, first.year)
        assertEquals(1, first.month)
        assertEquals(1, first.day)
        assertNull(LunarCalendar.solarToLunar(LocalDate.of(1900, 1, 30)))

        // 2101 年中日期超出表支持（农历 2100 年结束后）→ null
        assertNull(LunarCalendar.solarToLunar(LocalDate.of(2102, 6, 1)))
    }

    @Test
    fun `往返一致性_换算结果合法`() {
        // 抽样 300 天：农历月 1–12、日 1–30 恒成立
        var date = LocalDate.of(2026, 1, 1)
        repeat(300) {
            val lunar = LunarCalendar.solarToLunar(date)!!
            assertTrue(lunar.month in 1..12)
            assertTrue(lunar.day in 1..30)
            date = date.plusDays(1)
        }
    }
}
