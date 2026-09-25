package com.muxiao.timart.domain.usecase

import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.domain.model.WeatherSnapshot
import com.muxiao.timart.domain.model.WeatherType
import com.muxiao.timart.domain.model.unlock.LogicType
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.domain.model.unlock.UnlockRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 解锁进度预估（储备池 v6）纯逻辑测试：
 * 单条件推算锚点、AND/OR/AT_LEAST 合并语义、不可推算条件保守返回 null。
 */
class EarliestUnlockEstimatorTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 固定「现在」= 2026-09-25 12:00 +08:00（周五） */
    private val now: Long = LocalDateTime.of(2026, 9, 25, 12, 0).atZone(zone).toInstant().toEpochMilli()

    /** 封存时刻 = 2026-09-01 08:00 +08:00 */
    private val sealTs: Long = LocalDateTime.of(2026, 9, 1, 8, 0).atZone(zone).toInstant().toEpochMilli()

    private fun capsule(vararg conditions: UnlockCondition, logic: LogicType = LogicType.AND, threshold: Int? = null) =
        Capsule(
            id = "cap-1",
            title = "测试",
            contentCipher = null,
            createTimestamp = sealTs,
            weather = WeatherSnapshot("city-1", "东京", WeatherType.CLEAR, 20.0, 0L),
            unlockRule = UnlockRule(logic, conditions.toList(), threshold),
            state = CapsuleState.LOCKED,
        )

    private fun dayStart(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    // ---- 单条件锚点 ----

    @Test
    fun fixedDateReturnsStartOfDay() {
        val est = EarliestUnlockEstimator.earliestAt(
            UnlockCondition.FixedDate(LocalDate.of(2027, 1, 1)),
            capsule(),
            now,
            zone,
        )
        assertEquals(dayStart(LocalDate.of(2027, 1, 1)), est)
    }

    @Test
    fun fixedDateTimeReturnsExactMoment() {
        val est = EarliestUnlockEstimator.earliestAt(
            UnlockCondition.FixedDateTime("2027-03-01T08:30"),
            capsule(),
            now,
            zone,
        )
        assertEquals(LocalDateTime.of(2027, 3, 1, 8, 30).atZone(zone).toInstant().toEpochMilli(), est)
    }

    @Test
    fun minElapsedDayCountsFromSealDate() {
        // 封存 9/1 + 30 天 = 10/1 零点
        val est = EarliestUnlockEstimator.earliestAt(
            UnlockCondition.MinElapsedDay(30),
            capsule(),
            now,
            zone,
        )
        assertEquals(dayStart(LocalDate.of(2026, 10, 1)), est)
    }

    @Test
    fun weekDayFindsNextMatchingDay() {
        // 今天周五（2026-09-25），下一个周一 = 9/28
        val est = EarliestUnlockEstimator.earliestAt(
            UnlockCondition.WeekDay(setOf(DayOfWeek.MONDAY)),
            capsule(),
            now,
            zone,
        )
        assertEquals(dayStart(LocalDate.of(2026, 9, 28)), est)
    }

    @Test
    fun yearlyDateResolvesNextOccurrence() {
        // 8/12 已过（今年），下一次 = 2027-08-12
        val est = EarliestUnlockEstimator.earliestAt(
            UnlockCondition.YearlyDate(8, 12),
            capsule(),
            now,
            zone,
        )
        assertEquals(dayStart(LocalDate.of(2027, 8, 12)), est)
    }

    @Test
    fun monthlyDayResolvesWithinMonth() {
        // 今天 9/25 → 本月 30 日
        val est = EarliestUnlockEstimator.earliestAt(
            UnlockCondition.MonthlyDay(30),
            capsule(),
            now,
            zone,
        )
        assertEquals(dayStart(LocalDate.of(2026, 9, 30)), est)
    }

    // ---- 合并语义 ----

    @Test
    fun andMergeTakesLatestWhenAllEstimable() {
        // 10/1 与 10/5 → AND 取最晚 10/5
        val est = EarliestUnlockEstimator.estimate(
            capsule(
                UnlockCondition.MinElapsedDay(30),
                UnlockCondition.FixedDate(LocalDate.of(2026, 10, 5)),
            ),
            now,
            zone,
        )
        assertEquals(dayStart(LocalDate.of(2026, 10, 5)), est)
    }

    @Test
    fun andMergeReturnsNullWhenAnyConditionNotEstimable() {
        // 传感器类（电池）不可推算 → 整体 null（保守，不猜测）
        val est = EarliestUnlockEstimator.estimate(
            capsule(
                UnlockCondition.MinElapsedDay(1),
                UnlockCondition.BatteryLevel(50, null),
            ),
            now,
            zone,
        )
        assertNull(est)
    }

    @Test
    fun orMergeTakesEarliestEstimable() {
        // OR：取可推算条件中最早者（10/1）；不可推算条件满足只会更早，展示侧须带限定文案
        val est = EarliestUnlockEstimator.estimate(
            capsule(
                UnlockCondition.MinElapsedDay(30),
                UnlockCondition.FixedDate(LocalDate.of(2026, 10, 5)),
                UnlockCondition.BatteryLevel(50, null),
                logic = LogicType.OR,
            ),
            now,
            zone,
        )
        assertEquals(dayStart(LocalDate.of(2026, 10, 1)), est)
    }

    @Test
    fun atLeastMergeTakesNthEarliest() {
        // M-of-N（M=2，全部可估）：三个时刻 10/1、10/5、10/9 → 第 2 早 = 10/5
        val est = EarliestUnlockEstimator.estimate(
            capsule(
                UnlockCondition.MinElapsedDay(30),
                UnlockCondition.FixedDate(LocalDate.of(2026, 10, 5)),
                UnlockCondition.FixedDate(LocalDate.of(2026, 10, 9)),
                logic = LogicType.AT_LEAST,
                threshold = 2,
            ),
            now,
            zone,
        )
        assertEquals(dayStart(LocalDate.of(2026, 10, 5)), est)
    }

    @Test
    fun emptyConditionListYieldsNull() {
        assertNull(EarliestUnlockEstimator.estimate(capsule(), now, zone))
    }
}
