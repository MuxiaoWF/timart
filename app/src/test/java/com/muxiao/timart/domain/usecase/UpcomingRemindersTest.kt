package com.muxiao.timart.domain.usecase

import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.domain.model.unlock.LogicType
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.domain.model.unlock.UnlockRule
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
}
