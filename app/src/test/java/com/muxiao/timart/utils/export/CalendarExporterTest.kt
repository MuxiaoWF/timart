package com.muxiao.timart.utils.export

import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.domain.model.unlock.UnlockRule
import com.muxiao.timart.domain.model.unlock.LogicType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iCalendar 导出（ics 日历导出）纯逻辑用例 */
class CalendarExporterTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    /** 2026-09-27 12:00 +08:00 */
    private val now = LocalDateTime.of(2026, 9, 27, 12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun capsule(
        id: String = "cap-1",
        title: String = "测试胶囊",
        state: CapsuleState = CapsuleState.LOCKED,
        createTimestamp: Long = 0L,
        conditions: List<UnlockCondition>,
    ) = Capsule(
        id = id,
        title = title,
        contentCipher = null,
        createTimestamp = createTimestamp,
        unlockRule = UnlockRule(LogicType.AND, conditions),
        state = state,
    )

    private fun collect(vararg capsules: Capsule): List<CalendarExporter.IcsEvent> =
        CalendarExporter.collect(capsules.toList(), now, zone) { c -> "时粒 · ${c.title}" }

    @Test
    fun fixedDateBecomesAllDayEvent() {
        val events = collect(
            capsule(conditions = listOf(UnlockCondition.FixedDate(LocalDate.of(2026, 12, 24)))),
        )
        assertEquals(1, events.size)
        val event = events[0]
        assertEquals(LocalDate.of(2026, 12, 24), event.date)
        assertNull(event.dateTime)
        assertFalse(event.yearlyRepeat)
        assertTrue(event.uid.startsWith("cap-1-"))
        assertTrue(event.summary.contains("测试胶囊"))
    }

    @Test
    fun fixedDateTimeBecomesTimedFloatingEvent() {
        val events = collect(
            capsule(conditions = listOf(UnlockCondition.FixedDateTime("2026-10-01T09:30"))),
        )
        assertEquals(1, events.size)
        assertEquals(LocalDateTime.of(2026, 10, 1, 9, 30), events[0].dateTime)
        assertFalse(events[0].yearlyRepeat)
    }

    @Test
    fun yearlyDateBecomesRecurringAllDayEvent() {
        val events = collect(
            capsule(conditions = listOf(UnlockCondition.YearlyDate(month = 2, day = 14))),
        )
        assertEquals(1, events.size)
        assertTrue(events[0].yearlyRepeat)
        // 下一次 2027-02-14（2026 当日已过）
        assertEquals(LocalDate.of(2027, 2, 14), events[0].date)
    }

    @Test
    fun pastTargetsAndNonLockedStatesAreSkipped() {
        val events = collect(
            capsule(
                id = "past",
                conditions = listOf(UnlockCondition.FixedDate(LocalDate.of(2026, 1, 1))),
            ),
            capsule(
                id = "unlocked",
                state = CapsuleState.UNLOCKED,
                conditions = listOf(UnlockCondition.FixedDate(LocalDate.of(2026, 12, 1))),
            ),
        )
        assertEquals(0, events.size)
    }

    @Test
    fun eventsSortedByTargetTime() {
        val events = collect(
            capsule(
                id = "late",
                conditions = listOf(UnlockCondition.FixedDateTime("2026-12-01T10:00")),
            ),
            capsule(
                id = "early",
                conditions = listOf(UnlockCondition.FixedDate(LocalDate.of(2026, 10, 10))),
            ),
        )
        assertEquals(2, events.size)
        assertEquals(LocalDate.of(2026, 10, 10), events[0].date)
        assertEquals(LocalDateTime.of(2026, 12, 1, 10, 0), events[1].dateTime)
    }

    @Test
    fun icsTextStructureAndEscaping() {
        val text = CalendarExporter.build(
            listOf(
                CalendarExporter.IcsEvent(
                    uid = "uid-1@timart.local",
                    summary = "等待; 冬至,第二场雪",
                    date = LocalDate.of(2026, 12, 21),
                    dateTime = null,
                ),
            ),
            nowMillis = now,
            calendarName = "时粒待解",
        )
        assertTrue(text.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(text.contains("VERSION:2.0"))
        assertTrue(text.contains("SUMMARY:等待\\; 冬至\\,第二场雪"))
        assertTrue(text.contains("DTSTART;VALUE=DATE:20261221"))
        assertTrue(text.contains("DTEND;VALUE=DATE:20261222"))
        assertFalse(text.contains("RRULE"))
        assertTrue(text.trimEnd().endsWith("END:VCALENDAR"))
        // 行尾一律 CRLF
        assertTrue(text.trimEnd().endsWith("END:VCALENDAR"))
        // 行尾一律 CRLF（去掉 CRLF 后不应残留裸 LF）
        assertFalse(text.replace("\r\n", "").contains("\n"))
    }

    @Test
    fun longSummaryFoldsAndYearlyRuleEmitted() {
        val text = CalendarExporter.build(
            listOf(
                CalendarExporter.IcsEvent(
                    uid = "uid-2@timart.local",
                    summary = "很长".repeat(40),
                    date = LocalDate.of(2027, 5, 1),
                    dateTime = null,
                    yearlyRepeat = true,
                ),
            ),
            nowMillis = now,
            calendarName = "时粒待解",
        )
        assertTrue(text.contains("RRULE:FREQ=YEARLY"))
        // 折行续行以空格开头，拼接后原文可还原
        val joined = text.split("\r\n")
            .joinToString("") { if (it.startsWith(" ")) it.substring(1) else it }
        assertTrue(joined.contains("SUMMARY:${"很长".repeat(40)}"))
    }
}
