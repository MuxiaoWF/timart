package com.muxiao.timart.domain.usecase

import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 规则体检 lint（纯逻辑，可 JVM 单测）：静态检测「逻辑上永不可能满足」的解锁条件——
 * 空区间（min > max）、空集合、非法取值（历法月日越界、半径 ≤ 0、零长时段）、
 * 已错过的 7 天窗口（NthWeekdaySince）。这类规则只能来自旧版本数据或导入备份
 * （创建侧表单由滑条/预设约束取值，产不出异常值），体检发现后由用户经「后悔药」修改。
 *
 * 只报确定性异常，不做语义猜测（如"组合起来很难满足"），与提醒覆盖面同哲学：
 * 宁可少报，不误报。
 */
object RuleSanityCheck {

    /** 异常类别（UI 文案按 kind 组织；均为"该条件永不可能满足"） */
    enum class Kind { EMPTY_INTERVAL, EMPTY_SET, INVALID_VALUE, WINDOW_MISSED }

    /** 一条规则异常（conditionIndex = 在 conditionList 中的下标，便于定位） */
    data class Issue(val capsuleId: String, val conditionIndex: Int, val kind: Kind)

    /** 单胶囊检查：逐条件静态校验 */
    fun inspect(capsule: Capsule, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Issue> {
        val createdDay = Instant.ofEpochMilli(capsule.createTimestamp).atZone(zone).toLocalDate()
        return capsule.unlockRule.conditionList.mapIndexedNotNull { index, condition ->
            val kind = kindOf(condition, createdDay, today) ?: return@mapIndexedNotNull null
            Issue(capsule.id, index, kind)
        }
    }

    /** 全库扫描（体检编排一次调用） */
    fun scan(capsules: List<Capsule>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Issue> =
        capsules.flatMap { inspect(it, today, zone) }

    private fun kindOf(condition: UnlockCondition, createdDay: LocalDate, today: LocalDate): Kind? = when (condition) {
        // ---- 区间两端倒置（任一端可空，双端在场才有意义）----
        is UnlockCondition.BatteryLevel ->
            if (condition.min != null && condition.max != null && condition.min > condition.max) {
                Kind.EMPTY_INTERVAL
            } else {
                null
            }
        is UnlockCondition.StepCount ->
            if (condition.minTodayStep != null && condition.maxTodayStep != null &&
                condition.minTodayStep > condition.maxTodayStep
            ) {
                Kind.EMPTY_INTERVAL
            } else {
                null
            }
        is UnlockCondition.TemperatureThreshold ->
            if (condition.minC != null && condition.maxC != null && condition.minC > condition.maxC) {
                Kind.EMPTY_INTERVAL
            } else {
                null
            }
        is UnlockCondition.WeatherMetric ->
            if (condition.min != null && condition.max != null && condition.min > condition.max) {
                Kind.EMPTY_INTERVAL
            } else {
                null
            }
        is UnlockCondition.BatteryTemp ->
            if (condition.minC != null && condition.maxC != null && condition.minC > condition.maxC) {
                Kind.EMPTY_INTERVAL
            } else {
                null
            }
        is UnlockCondition.AltitudeRange ->
            if (condition.minM != null && condition.maxM != null && condition.minM > condition.maxM) {
                Kind.EMPTY_INTERVAL
            } else {
                null
            }
        is UnlockCondition.SpeedRange ->
            if (condition.minKmh != null && condition.maxKmh != null && condition.minKmh > condition.maxKmh) {
                Kind.EMPTY_INTERVAL
            } else {
                null
            }
        is UnlockCondition.DayLength ->
            if (condition.minHours != null && condition.maxHours != null && condition.minHours > condition.maxHours) {
                Kind.EMPTY_INTERVAL
            } else {
                null
            }
        is UnlockCondition.SunriseTimeRange ->
            if (condition.minMinute != null && condition.maxMinute != null && condition.minMinute > condition.maxMinute) {
                Kind.EMPTY_INTERVAL
            } else {
                null
            }

        // ---- 集合为空 / 取值越界 ----
        is UnlockCondition.WeekDay -> ifEmptySet(condition.weekSet.isEmpty())
        is UnlockCondition.NetworkType -> ifEmptySet(condition.types.isEmpty())
        is UnlockCondition.WeatherType -> ifEmptySet(condition.weatherTypes.isEmpty())
        is UnlockCondition.SunPhase -> ifEmptySet(condition.phases.isEmpty())
        is UnlockCondition.MoonPhase -> ifEmptySet(condition.phases.isEmpty())
        is UnlockCondition.MeteorShower -> ifEmptySet(condition.showers.isEmpty())
        is UnlockCondition.MotionActivity -> ifEmptySet(condition.kinds.isEmpty())
        is UnlockCondition.PlugType -> ifEmptySet(condition.kinds.isEmpty())
        is UnlockCondition.WindDirection -> ifEmptySet(condition.dirs.isEmpty())
        is UnlockCondition.Season -> ifEmptySet(condition.seasons.isEmpty())
        is UnlockCondition.SolarTerm -> ifEmptySet(condition.solarTerms.isEmpty())
        is UnlockCondition.LunarDayOfMonth ->
            ifEmptySet(condition.days.isEmpty() || condition.days.any { it !in 1..30 })
        is UnlockCondition.MonthlyDaySet ->
            ifEmptySet(condition.days.isEmpty() || condition.days.any { it !in 1..31 })
        is UnlockCondition.TimeRange ->
            if (condition.startHour !in 0..23 || condition.endHour !in 0..23 || condition.startHour == condition.endHour) {
                Kind.INVALID_VALUE
            } else {
                null
            }
        is UnlockCondition.LunarDate ->
            if (condition.month !in 1..12 || condition.day !in 1..30) Kind.INVALID_VALUE else null
        is UnlockCondition.LunarMonthRange -> if (condition.month !in 1..12) Kind.INVALID_VALUE else null
        is UnlockCondition.NthWeekdayOfMonth ->
            if (condition.nth !in 1..5) Kind.INVALID_VALUE else null
        is UnlockCondition.YearlyNthWeekday ->
            if (condition.month !in 1..12 || condition.nth !in 1..5) Kind.INVALID_VALUE else null
        is UnlockCondition.GpsLocation -> if (condition.radiusMeter <= 0) Kind.INVALID_VALUE else null
        is UnlockCondition.AwayFromLocation -> if (condition.radiusMeter <= 0) Kind.INVALID_VALUE else null
        is UnlockCondition.CityLocation -> if (condition.radiusMeter <= 0) Kind.INVALID_VALUE else null
        is UnlockCondition.CompassHeading -> if (condition.toleranceDeg < 0) Kind.INVALID_VALUE else null
        is UnlockCondition.StepStreak ->
            if (condition.days <= 0 || condition.goal <= 0) Kind.INVALID_VALUE else null
        is UnlockCondition.RoundDaysElapsed -> if (condition.modulus <= 0) Kind.INVALID_VALUE else null
        is UnlockCondition.AppUsageCeiling ->
            if (condition.packageName.isBlank() || condition.maxMinutes < 0) Kind.INVALID_VALUE else null

        // ---- 已错过的固定窗口 ----
        is UnlockCondition.NthWeekdaySince -> {
            if (condition.minDays < 0) return Kind.INVALID_VALUE
            // 窗口 = 封存满 minDays 后的首个 weekday 及其后 6 天；整窗已过 → 永不再满足
            val windowStart = createdDay.plusDays(condition.minDays.toLong())
            var cursor = windowStart
            repeat(7) {
                if (cursor.dayOfWeek == condition.dayOfWeek) {
                    return if (cursor.isBefore(today)) Kind.WINDOW_MISSED else null
                }
                cursor = cursor.plusDays(1)
            }
            null
        }

        else -> null
    }

    private fun ifEmptySet(empty: Boolean): Kind? = if (empty) Kind.EMPTY_SET else null
}
