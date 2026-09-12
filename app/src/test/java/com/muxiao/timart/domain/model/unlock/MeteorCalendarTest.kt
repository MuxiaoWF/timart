package com.muxiao.timart.domain.model.unlock

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 流星雨静态表测试：峰值命中 / 窗口边界 / 年复现（月-日静态表可无限期后推，±1 天）。
 */
class MeteorCalendarTest {

    @Test
    fun `每场流星雨峰值日命中且五天后不命中`() {
        val peaks = mapOf(
            MeteorShowerKind.QUADRANTIDS to LocalDate.of(2026, 1, 4),
            MeteorShowerKind.LYRIDS to LocalDate.of(2026, 4, 22),
            MeteorShowerKind.ETA_AQUARIIDS to LocalDate.of(2026, 5, 6),
            MeteorShowerKind.DELTA_AQUARIIDS to LocalDate.of(2026, 7, 30),
            MeteorShowerKind.PERSEIDS to LocalDate.of(2026, 8, 13),
            MeteorShowerKind.ORIONIDS to LocalDate.of(2026, 10, 21),
            MeteorShowerKind.LEONIDS to LocalDate.of(2026, 11, 17),
            MeteorShowerKind.GEMINIDS to LocalDate.of(2026, 12, 14),
            MeteorShowerKind.URSIDS to LocalDate.of(2026, 12, 22),
        )
        peaks.forEach { (kind, peak) ->
            assertTrue("$kind 峰值日应命中", MeteorCalendar.isPeakNight(kind, peak))
            assertFalse("$kind 五天后不应命中", MeteorCalendar.isPeakNight(kind, peak.plusDays(5)))
            assertFalse("$kind 五天前不应命中", MeteorCalendar.isPeakNight(kind, peak.minusDays(5)))
        }
    }

    @Test
    fun `窗口为峰值日前后一天`() {
        // 英仙座峰值 8/13：8/12–8/14 满足，8/11 与 8/15 不满足
        assertTrue(MeteorCalendar.isPeakNight(MeteorShowerKind.PERSEIDS, LocalDate.of(2026, 8, 12)))
        assertTrue(MeteorCalendar.isPeakNight(MeteorShowerKind.PERSEIDS, LocalDate.of(2026, 8, 13)))
        assertTrue(MeteorCalendar.isPeakNight(MeteorShowerKind.PERSEIDS, LocalDate.of(2026, 8, 14)))
        assertFalse(MeteorCalendar.isPeakNight(MeteorShowerKind.PERSEIDS, LocalDate.of(2026, 8, 11)))
        assertFalse(MeteorCalendar.isPeakNight(MeteorShowerKind.PERSEIDS, LocalDate.of(2026, 8, 15)))
    }

    @Test
    fun `年复现_静态表在远期年份同样命中`() {
        // 月-日静态推算：2050 与 2100 年英仙座极大窗口同样命中（外推能力）
        assertTrue(MeteorCalendar.isPeakNight(MeteorShowerKind.PERSEIDS, LocalDate.of(2050, 8, 13)))
        assertTrue(MeteorCalendar.isPeakNight(MeteorShowerKind.PERSEIDS, LocalDate.of(2100, 8, 12)))
        // 十二月下旬双雨季窗口互不重叠（双子座 12/14、小熊座 12/22，间隔 8 天 > 2 天窗口）
        assertFalse(
            MeteorCalendar.isPeakNight(MeteorShowerKind.GEMINIDS, LocalDate.of(2026, 12, 22)) &&
                MeteorCalendar.isPeakNight(MeteorShowerKind.URSIDS, LocalDate.of(2026, 12, 22)),
        )
    }
}
