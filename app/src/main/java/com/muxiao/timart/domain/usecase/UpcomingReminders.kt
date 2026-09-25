package com.muxiao.timart.domain.usecase

import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.domain.model.unlock.LunarCalendar
import com.muxiao.timart.domain.model.unlock.MeteorCalendar
import com.muxiao.timart.domain.model.unlock.MoonCalc
import com.muxiao.timart.domain.model.unlock.SeasonKind
import com.muxiao.timart.domain.model.unlock.SolarTermCalendar
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.ceil

/**
 * 临近解锁提醒（N1，纯逻辑可 JVM 单测）：
 *
 * 对「可静态推算」的条件估算下一个到达时刻——绝对日期、月/年历法、农历、节气星座季节、
 * 月相流星雨，以及以封存日为锚的相对天数/整月/整倍数日。不参与（宁可少提醒）：
 * 周/日级高频重复（`WeekDay` / `TimeRange`——反复提醒疲乏）、传感器/天气/挑战/应用内
 * 统计等现实状态驱动类、依赖联动（与挑战条件 fail-closed 同哲学）。
 *
 * 提醒去重（每档只发一条）由调用方负责：meta `settings.remindSent.<id>` 记录上次已提醒的
 * 剩余天数档位，本类只输出当期应提醒集合（capsuleId, 目标时刻, 剩余整天数）。
 */
object UpcomingReminders {

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** 历法/天文类的逐日枚举窗口（与 EarliestUnlockEstimator 同口径） */
    private const val SCAN_DAYS = 400

    /** 一条待提醒（daysLeft = 距目标时刻的整天数，向上取整，≥1） */
    data class Due(val capsule: Capsule, val targetAt: Long, val daysLeft: Int)

    /**
     * 单条件的确定性目标时刻（epoch millis）；非可推算类 / 过去时刻 / 解析失败返回 null。
     * [now] 与 [zone] 决定「下一次」语义；[createdDay] 为封存日（锚定封存日的相对条件
     * 需要，null 时这类条件按不可推算返回 null）。
     */
    fun fixedTargetAt(
        condition: UnlockCondition,
        now: Long,
        zone: ZoneId,
        createdDay: LocalDate? = null,
    ): Long? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        fun dateTarget(date: LocalDate?): Long? =
            date?.let { startOfDayOrNull(it, zone)?.takeIf { t -> t > now } }
        return when (condition) {
            is UnlockCondition.FixedDate ->
                startOfDayOrNull(condition.targetDate, zone)?.takeIf { it > now }

            is UnlockCondition.FixedDateTime -> runCatching {
                LocalDateTime.parse(condition.targetDateTime).atZone(zone).toInstant().toEpochMilli()
            }.getOrNull()?.takeIf { it > now }

            is UnlockCondition.YearlyDate -> {
                val date = resolveYearly(today.year, condition.month, condition.day)
                    ?: return null
                val next = if (!date.isBefore(today)) date else {
                    resolveYearly(today.year + 1, condition.month, condition.day) ?: return null
                }
                startOfDayOrNull(next, zone)?.takeIf { it > now }
            }

            // ---- 历法月/年类（储备池 v6 覆盖面扩展）----
            is UnlockCondition.MonthlyDay -> dateTarget(nextMonthlyDay(today, condition.dayOfMonth))
            is UnlockCondition.MonthlyDaySet ->
                dateTarget(condition.days.mapNotNull { nextMonthlyDay(today, it) }.minOrNull())
            is UnlockCondition.LastDayOfMonth -> {
                val end = YearMonth.of(today.year, today.monthValue).atEndOfMonth()
                dateTarget(if (!end.isBefore(today)) end else end.plusMonths(1))
            }
            is UnlockCondition.NthWeekdayOfMonth ->
                dateTarget(scanDays(today, 62) { isNthWeekday(it, condition.nth, condition.dayOfWeek) })
            is UnlockCondition.YearlyNthWeekday ->
                dateTarget(
                    scanDays(today, 366) {
                        it.monthValue == condition.month && isNthWeekday(it, condition.nth, condition.dayOfWeek)
                    },
                )
            is UnlockCondition.LeapDay -> dateTarget(nextLeapDay(today))

            // ---- 锚定封存日的相对类（createdDay 缺省时不可推算）----
            is UnlockCondition.MinElapsedDay ->
                createdDay?.plusDays(condition.days.toLong())?.let { dateTarget(it) }
            is UnlockCondition.MinElapsedMonths ->
                createdDay?.plusMonths(condition.months.toLong())?.let { dateTarget(it) }
            is UnlockCondition.RoundDaysElapsed -> {
                if (createdDay == null) return null
                val modulus = condition.modulus.toLong()
                if (modulus <= 0) return null
                // 满 1 天后的首个 modulus 倍数日（与判定/预估同口径）
                val firstMultiple = createdDay.plusDays(modulus)
                val next = if (!firstMultiple.isBefore(today)) {
                    firstMultiple
                } else {
                    val elapsed = java.time.temporal.ChronoUnit.DAYS.between(createdDay, today) + 1
                    createdDay.plusDays(((elapsed / modulus) + 1) * modulus)
                }
                dateTarget(next)
            }
            is UnlockCondition.NthWeekdaySince -> {
                if (createdDay == null) return null
                // 封存满 minDays 后的首个 weekday（7 天窗口，错过不再满足）——目标即该日
                val windowStart = createdDay.plusDays(condition.minDays.toLong())
                val target = (0L..6L).asSequence()
                    .map { windowStart.plusDays(it) }
                    .firstOrNull { it.dayOfWeek == condition.dayOfWeek } ?: return null
                dateTarget(target)
            }

            // ---- 农历 / 天文历法类（逐日查表推下一次）----
            is UnlockCondition.LunarDate ->
                dateTarget(
                    scanDays(today, SCAN_DAYS) { d ->
                        val l = LunarCalendar.solarToLunar(d)
                        l != null && l.month == condition.month && l.day == condition.day
                    },
                )
            is UnlockCondition.LunarMonthRange ->
                dateTarget(scanDays(today, SCAN_DAYS) { LunarCalendar.solarToLunar(it)?.month == condition.month })
            is UnlockCondition.SolarTerm ->
                dateTarget(scanDays(today, SCAN_DAYS) { SolarTermCalendar.termOf(it) in condition.solarTerms })
            is UnlockCondition.ZodiacSeason ->
                dateTarget(scanDays(today, SCAN_DAYS) { SolarTermCalendar.zodiacOf(it) == condition.zodiac })
            is UnlockCondition.Season ->
                dateTarget(scanDays(today, SCAN_DAYS) { seasonOf(it) in condition.seasons })
            is UnlockCondition.MoonPhase ->
                dateTarget(scanDays(today, SCAN_DAYS) { MoonCalc.phaseOf(it) in condition.phases })
            is UnlockCondition.MeteorShower ->
                dateTarget(
                    scanDays(today, SCAN_DAYS) { d -> condition.showers.any { MeteorCalendar.isPeakNight(it, d) } },
                )

            else -> null
        }
    }

    /** 年-月-日合法化：2/29 等落回当月最后一天（纪念日在平年提前一天，宁早勿漏） */
    private fun resolveYearly(year: Int, month: Int, day: Int): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        val yearMonth = YearMonth.of(year, month)
        return yearMonth.atDay(day.coerceAtMost(yearMonth.lengthOfMonth()))
    }

    private fun startOfDayOrNull(date: LocalDate, zone: ZoneId): Long? = runCatching {
        date.atStartOfDay(zone).toInstant().toEpochMilli()
    }.getOrNull()

    /** 未来首个「当月 dayOfMonth 日」（2/30 等落回月末，宁早勿漏） */
    private fun nextMonthlyDay(today: LocalDate, dayOfMonth: Int): LocalDate? {
        if (dayOfMonth !in 1..31) return null
        val thisMonth = YearMonth.of(today.year, today.monthValue)
        val candidate = thisMonth.atDay(dayOfMonth.coerceAtMost(thisMonth.lengthOfMonth()))
        return if (!candidate.isBefore(today)) candidate else {
            val nextYm = thisMonth.plusMonths(1)
            nextYm.atDay(dayOfMonth.coerceAtMost(nextYm.lengthOfMonth()))
        }
    }

    /** 下一个 2 月 29 日（四年一遇；判定侧非闰年恒不满足，提醒只押闰年当天） */
    private fun nextLeapDay(today: LocalDate): LocalDate? {
        var year = today.year
        repeat(5) {
            if (YearMonth.of(year, 2).isLeapYear) {
                val date = LocalDate.of(year, 2, 29)
                if (!date.isBefore(today)) return date
            }
            year++
        }
        return null
    }

    private fun isNthWeekday(date: LocalDate, nth: Int, dayOfWeek: java.time.DayOfWeek): Boolean =
        date.dayOfWeek == dayOfWeek && (date.dayOfMonth - 1) / 7 + 1 == nth

    /** 公历季节（与判定/预估同口径：3-5 春 / 6-8 夏 / 9-11 秋 / 12-2 冬） */
    private fun seasonOf(date: LocalDate): SeasonKind = when (date.monthValue) {
        3, 4, 5 -> SeasonKind.SPRING
        6, 7, 8 -> SeasonKind.SUMMER
        9, 10, 11 -> SeasonKind.AUTUMN
        else -> SeasonKind.WINTER
    }

    /** 从 [today] 起在 [window] 天内找首个满足 [pred] 的日期 */
    private fun scanDays(today: LocalDate, window: Int, pred: (LocalDate) -> Boolean): LocalDate? =
        (0 until window).asSequence().map { today.plusDays(it.toLong()) }.firstOrNull(pred)

    /**
     * 全库扫描：状态 LOCKED、用户设置了提前量（[leadDaysOf] 返回非 null）、
     * 任一可推算条件的目标时刻落在提前量窗口内 → 待提醒。多条可推算条件取最近者。
     */
    fun due(
        capsules: List<Capsule>,
        leadDaysOf: (String) -> Int?,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Due> = capsules.mapNotNull { capsule ->
        if (capsule.state != CapsuleState.LOCKED) return@mapNotNull null
        val lead = leadDaysOf(capsule.id)?.takeIf { it in 1..30 } ?: return@mapNotNull null
        val createdDay = Instant.ofEpochMilli(capsule.createTimestamp).atZone(zone).toLocalDate()
        val target = capsule.unlockRule.conditionList
            .mapNotNull { fixedTargetAt(it, now, zone, createdDay) }
            .minOrNull() ?: return@mapNotNull null
        val daysLeft = ceil((target - now).toDouble() / DAY_MS).toInt()
        if (daysLeft in 1..lead) Due(capsule, target, daysLeft) else null
    }
}
