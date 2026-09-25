package com.muxiao.timart.domain.usecase

import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.domain.model.unlock.LogicType
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.domain.model.unlock.UnlockRule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 临近解锁提醒（N1）纯逻辑单测：
 * FixedDate / FixedDateTime / YearlyDate 三类确定性目标时刻 + due 窗口过滤。
 * 全部用固定时区 UTC 排除本地时区漂移。
 */
class UpcomingRemindersTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun atUtc(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        ZonedDateTime.of(LocalDateTime.of(year, month, day, hour, minute), zone).toInstant().toEpochMilli()

    private fun capsule(vararg conditions: UnlockCondition) = Capsule(
        id = "c1",
        title = "测试",
        contentCipher = null,
        createTimestamp = 0L,
        unlockRule = UnlockRule(LogicType.AND, conditions.toList()),
        state = CapsuleState.LOCKED,
    )

    // ---- fixedTargetAt ----

    @Test
    fun fixedDateTargetAtStartOfDay() {
        val target = UpcomingReminders.fixedTargetAt(
            UnlockCondition.FixedDate(LocalDate.of(2027, 1, 1)),
            now = atUtc(2026, 12, 30),
            zone = zone,
        )
        assertEquals(atUtc(2027, 1, 1), target)
    }

    @Test
    fun fixedDateInPastReturnsNull() {
        val target = UpcomingReminders.fixedTargetAt(
            UnlockCondition.FixedDate(LocalDate.of(2026, 1, 1)),
            now = atUtc(2026, 12, 30),
            zone = zone,
        )
        assertNull(target)
    }

    @Test
    fun fixedDateTimeParsesIsoLocal() {
        val target = UpcomingReminders.fixedTargetAt(
            UnlockCondition.FixedDateTime("2026-12-31T18:30"),
            now = atUtc(2026, 12, 30),
            zone = zone,
        )
        assertEquals(atUtc(2026, 12, 31, 18, 30), target)
    }

    @Test
    fun fixedDateTimeMalformedReturnsNull() {
        val target = UpcomingReminders.fixedTargetAt(
            UnlockCondition.FixedDateTime("not-a-time"),
            now = atUtc(2026, 12, 30),
            zone = zone,
        )
        assertNull(target)
    }

    @Test
    fun yearlyDateTakesNextOccurrence() {
        val target = UpcomingReminders.fixedTargetAt(
            UnlockCondition.YearlyDate(12, 31),
            now = atUtc(2026, 12, 25),
            zone = zone,
        )
        assertEquals(atUtc(2026, 12, 31), target)
    }

    @Test
    fun yearlyDateRollsToNextYear() {
        // 纪念日（2026-12-31）已过 → 取下一次 2027-12-31
        val target = UpcomingReminders.fixedTargetAt(
            UnlockCondition.YearlyDate(12, 31),
            now = atUtc(2027, 1, 2),
            zone = zone,
        )
        assertEquals(atUtc(2027, 12, 31), target)
    }

    @Test
    fun yearlyDateOnAnniversaryDayAfterStartReturnsNull() {
        // 纪念日当天零点起即视为已到达（判定侧当日即满足）；日间再无「即将到来」的时刻
        val target = UpcomingReminders.fixedTargetAt(
            UnlockCondition.YearlyDate(12, 31),
            now = atUtc(2026, 12, 31, 12),
            zone = zone,
        )
        assertNull(target)
    }

    @Test
    fun yearlyDateFeb29CoercesToMonthEnd() {
        // 平年 2/29 落回 2/28（宁早勿漏）
        val target = UpcomingReminders.fixedTargetAt(
            UnlockCondition.YearlyDate(2, 29),
            now = atUtc(2027, 2, 26),
            zone = zone,
        )
        assertEquals(atUtc(2027, 2, 28), target)
    }

    @Test
    fun nonTimeConditionReturnsNull() {
        val target = UpcomingReminders.fixedTargetAt(
            UnlockCondition.NetworkType(setOf(com.muxiao.timart.domain.model.unlock.NetType.WIFI)),
            now = atUtc(2026, 12, 30),
            zone = zone,
        )
        assertNull(target)
    }

    // ---- due ----

    @Test
    fun dueWithinLeadWindow() {
        // now 12-30，目标 1-1 → 剩 2 天；提前量 7 → 提醒，daysLeft = 2
        val dues = UpcomingReminders.due(
            capsules = listOf(capsule(UnlockCondition.FixedDate(LocalDate.of(2027, 1, 1)))),
            leadDaysOf = { 7 },
            now = atUtc(2026, 12, 30),
            zone = zone,
        )
        assertEquals(1, dues.size)
        assertEquals(2, dues[0].daysLeft)
    }

    @Test
    fun dueBeyondLeadWindowExcluded() {
        // 剩 2 天 > 提前量 1 → 不提醒
        val dues = UpcomingReminders.due(
            capsules = listOf(capsule(UnlockCondition.FixedDate(LocalDate.of(2027, 1, 1)))),
            leadDaysOf = { 1 },
            now = atUtc(2026, 12, 30),
            zone = zone,
        )
        assertEquals(0, dues.size)
    }

    @Test
    fun dueNoLeadConfigExcluded() {
        val dues = UpcomingReminders.due(
            capsules = listOf(capsule(UnlockCondition.FixedDate(LocalDate.of(2027, 1, 1)))),
            leadDaysOf = { null },
            now = atUtc(2026, 12, 30),
            zone = zone,
        )
        assertEquals(0, dues.size)
    }

    @Test
    fun dueUnlockedCapsuleExcluded() {
        val unlocked = capsule(UnlockCondition.FixedDate(LocalDate.of(2027, 1, 1)))
            .copy(state = CapsuleState.UNLOCKED)
        val dues = UpcomingReminders.due(
            capsules = listOf(unlocked),
            leadDaysOf = { 7 },
            now = atUtc(2026, 12, 30),
            zone = zone,
        )
        assertEquals(0, dues.size)
    }

    // ---- 覆盖面扩展（储备池 v7）：历法 / 农历 / 天文 / 锚定封存日 ----

    @Test
    fun monthlyDayTargetsNextOccurrence() {
        val target = UpcomingReminders.fixedTargetAt(
            UnlockCondition.MonthlyDay(31),
            now = atUtc(2026, 12, 25),
            zone = zone,
        )
        assertEquals(atUtc(2026, 12, 31), target)
    }

    @Test
    fun lastDayOfMonthTargetsMonthEnd() {
        val target = UpcomingReminders.fixedTargetAt(
            UnlockCondition.LastDayOfMonth,
            now = atUtc(2026, 12, 25),
            zone = zone,
        )
        assertEquals(atUtc(2026, 12, 31), target)
    }

    @Test
    fun nthWeekdayOfMonthTargetsFirstMatching() {
        // 2026-12-25 → 2027 年 1 月第一个周一 = 1/4
        val target = UpcomingReminders.fixedTargetAt(
            UnlockCondition.NthWeekdayOfMonth(1, java.time.DayOfWeek.MONDAY),
            now = atUtc(2026, 12, 25),
            zone = zone,
        )
        assertEquals(atUtc(2027, 1, 4), target)
    }

    @Test
    fun yearlyNthWeekdayTargetsMothersDay() {
        // 2026-12-25 → 2027 母亲节（5 月第二个周日）= 5/9
        val target = UpcomingReminders.fixedTargetAt(
            UnlockCondition.YearlyNthWeekday(5, 2, java.time.DayOfWeek.SUNDAY),
            now = atUtc(2026, 12, 25),
            zone = zone,
        )
        assertEquals(atUtc(2027, 5, 9), target)
    }

    @Test
    fun leapDayTargetsNextFeb29() {
        val target = UpcomingReminders.fixedTargetAt(
            UnlockCondition.LeapDay,
            now = atUtc(2027, 2, 26),
            zone = zone,
        )
        assertEquals(atUtc(2028, 2, 29), target)
    }

    @Test
    fun minElapsedDayNeedsCreatedDay() {
        val condition = UnlockCondition.MinElapsedDay(30)
        // 未传封存日 → 锚定封存日的条件不可推算
        assertNull(
            UpcomingReminders.fixedTargetAt(condition, now = atUtc(2026, 9, 25), zone = zone),
        )
        val target = UpcomingReminders.fixedTargetAt(
            condition,
            now = atUtc(2026, 9, 25),
            zone = zone,
            createdDay = LocalDate.of(2026, 9, 1),
        )
        assertEquals(atUtc(2026, 10, 1), target)
    }

    @Test
    fun roundDaysElapsedTargetsNextMultiple() {
        // 封存 9/1、满 100 天 = 12/10；12/20 时下一次倍数 = +200 天（2027-03-20）
        val target = UpcomingReminders.fixedTargetAt(
            UnlockCondition.RoundDaysElapsed(100),
            now = atUtc(2026, 12, 20),
            zone = zone,
            createdDay = LocalDate.of(2026, 9, 1),
        )
        assertEquals(atUtc(2027, 3, 20), target)
    }

    @Test
    fun lunarDateTargetsNextNewYear() {
        // 用农历位表作 oracle 扫下一次春节（正月初一），校验「下一次」语义
        val now = atUtc(2026, 9, 25)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        // 农历位表作 oracle：自 today 起逐日扫（至多 500 天），首个正月初一即「下一次」
        val expected = generateSequence(today) { it.plusDays(1) }
            .take(500)
            .firstOrNull { cursor ->
                val lunar = com.muxiao.timart.domain.model.unlock.LunarCalendar.solarToLunar(cursor)
                lunar != null && lunar.month == 1 && lunar.day == 1
            }
        val target = UpcomingReminders.fixedTargetAt(
            UnlockCondition.LunarDate(1, 1),
            now = now,
            zone = zone,
        )
        assertEquals(expected!!.atStartOfDay(zone).toInstant().toEpochMilli(), target)
    }

    @Test
    fun solarTermTargetsNextOccurrence() {
        // 立春（2 月初）：2026-12-25 → 2027 立春，用节气位表作 oracle
        val now = atUtc(2026, 12, 25)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        // 节气位表作 oracle：自 today 起逐日扫（至多 500 天），首个立春即「下一次」
        val expected = generateSequence(today) { it.plusDays(1) }
            .take(500)
            .firstOrNull {
                com.muxiao.timart.domain.model.unlock.SolarTermCalendar.termOf(it) ==
                    com.muxiao.timart.domain.model.unlock.SolarTermKind.LICHUN
            }
        val target = UpcomingReminders.fixedTargetAt(
            UnlockCondition.SolarTerm(setOf(com.muxiao.timart.domain.model.unlock.SolarTermKind.LICHUN)),
            now = now,
            zone = zone,
        )
        assertEquals(expected!!.atStartOfDay(zone).toInstant().toEpochMilli(), target)
    }

    @Test
    fun weeklyAndDailyRecurringStillExcluded() {
        // 周/日级高频重复不参与（提醒疲乏）
        assertNull(
            UpcomingReminders.fixedTargetAt(
                UnlockCondition.WeekDay(setOf(java.time.DayOfWeek.MONDAY)),
                now = atUtc(2026, 12, 30),
                zone = zone,
            ),
        )
        assertNull(
            UpcomingReminders.fixedTargetAt(
                UnlockCondition.TimeRange(22, 6),
                now = atUtc(2026, 12, 30),
                zone = zone,
            ),
        )
    }

    @Test
    fun dueUsesCreatedDayForRelativeConditions() {
        // 锚定封存日：9/1 封存 + 10 天 = 9/11；9/9 距目标 2 天，提前量 3 → 提醒
        val sealed = capsule(UnlockCondition.MinElapsedDay(10))
            .copy(createTimestamp = atUtc(2026, 9, 1))
        val dues = UpcomingReminders.due(
            capsules = listOf(sealed),
            leadDaysOf = { 3 },
            now = atUtc(2026, 9, 9),
            zone = zone,
        )
        assertEquals(1, dues.size)
        assertEquals(2, dues[0].daysLeft)
    }
}
