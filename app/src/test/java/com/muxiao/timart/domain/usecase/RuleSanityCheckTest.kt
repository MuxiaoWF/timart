package com.muxiao.timart.domain.usecase

import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.domain.model.unlock.LogicType
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.domain.model.unlock.UnlockRule
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 规则体检 lint 纯逻辑单测：空区间 / 空集合 / 非法取值 / 已错过窗口，
 * 以及「合法规则零误报」。
 */
class RuleSanityCheckTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 26)
    private val zone: ZoneId = ZoneId.of("UTC")

    private fun capsule(vararg conditions: UnlockCondition, createdDay: LocalDate = LocalDate.of(2026, 9, 1)) =
        Capsule(
            id = "c1",
            title = "测试",
            contentCipher = null,
            createTimestamp = createdDay.atStartOfDay(zone).toInstant().toEpochMilli(),
            unlockRule = UnlockRule(LogicType.AND, conditions.toList()),
            state = CapsuleState.LOCKED,
        )

    @Test
    fun detectsInvertedIntervals() {
        val issues = RuleSanityCheck.inspect(
            capsule(
                UnlockCondition.BatteryLevel(90, 50),
                UnlockCondition.TemperatureThreshold(30.0, 10.0),
                UnlockCondition.WeatherMetric(
                    com.muxiao.timart.domain.model.unlock.WeatherMetricKind.HUMIDITY,
                    min = 80.0,
                    max = 20.0,
                ),
                UnlockCondition.SpeedRange(100, 20),
            ),
            today,
            zone,
        )
        assertEquals(4, issues.size)
        assertTrue(issues.all { it.kind == RuleSanityCheck.Kind.EMPTY_INTERVAL })
    }

    @Test
    fun detectsEmptySetsAndInvalidValues() {
        val issues = RuleSanityCheck.inspect(
            capsule(
                UnlockCondition.WeekDay(emptySet()),
                UnlockCondition.LunarDate(13, 1),
                UnlockCondition.TimeRange(6, 6),
                UnlockCondition.GpsLocation(0.0, 0.0, 0),
                UnlockCondition.LunarDayOfMonth(emptySet()),
            ),
            today,
            zone,
        )
        assertEquals(5, issues.size)
        assertEquals(RuleSanityCheck.Kind.EMPTY_SET, issues[0].kind)
        assertEquals(RuleSanityCheck.Kind.INVALID_VALUE, issues[1].kind)
        assertEquals(RuleSanityCheck.Kind.INVALID_VALUE, issues[2].kind)
        assertEquals(RuleSanityCheck.Kind.INVALID_VALUE, issues[3].kind)
        assertEquals(RuleSanityCheck.Kind.EMPTY_SET, issues[4].kind)
    }

    @Test
    fun detectsMissedFixedWindow() {
        // 封存 9/1 满 0 天后的首个周六窗口：9/5（周六）起 7 天，today 9/26 已错过
        val missed = RuleSanityCheck.inspect(
            capsule(UnlockCondition.NthWeekdaySince(0, java.time.DayOfWeek.SATURDAY)),
            today,
            zone,
        )
        assertEquals(1, missed.size)
        assertEquals(RuleSanityCheck.Kind.WINDOW_MISSED, missed[0].kind)

        // 窗口未到 / 窗口内 → 无异常
        val upcoming = RuleSanityCheck.inspect(
            capsule(
                UnlockCondition.NthWeekdaySince(400, java.time.DayOfWeek.SATURDAY),
                createdDay = today.minusDays(10),
            ),
            today,
            zone,
        )
        assertTrue(upcoming.isEmpty())
    }

    @Test
    fun cleanRuleYieldsNoFindings() {
        val issues = RuleSanityCheck.inspect(
            capsule(
                UnlockCondition.FixedDate(LocalDate.of(2027, 1, 1)),
                UnlockCondition.BatteryLevel(20, 80),
                UnlockCondition.LunarDate(1, 1),
                UnlockCondition.TimeRange(6, 8),
                UnlockCondition.NthWeekdaySince(30, java.time.DayOfWeek.MONDAY),
            ),
            today,
            zone,
        )
        assertTrue(issues.isEmpty())
    }

    @Test
    fun scanAggregatesAcrossCapsules() {
        val good = capsule(UnlockCondition.FixedDate(LocalDate.of(2027, 1, 1)))
            .copy(id = "good")
        val bad = capsule(UnlockCondition.BatteryLevel(90, 50)).copy(id = "bad")
        val findings = RuleSanityCheck.scan(listOf(good, bad), today, zone)
        assertEquals(1, findings.size)
        assertEquals("bad", findings[0].capsuleId)
    }
}
