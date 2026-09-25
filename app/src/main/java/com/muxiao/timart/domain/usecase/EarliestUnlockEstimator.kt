package com.muxiao.timart.domain.usecase

import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.unlock.LogicType
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

/**
 * 解锁进度预估（储备池 v6 功能增量；纯逻辑可 JVM 单测）：
 *
 * 对「确定性时间类条件」静态推算各自最早可能满足的时刻，再按规则逻辑合并：
 * - AND：所有条件都可推算时取最晚者（全部达成的最早时刻）；任一不可推算 → null（宁可不算，不猜测）；
 * - AT_LEAST：同 AND 全部可推算，取第 M 早者；
 * - OR：取可推算条件中最早者（其余不可推算条件满足只会更早，故此值是「由可预知条件给出的
 *   最早可能时刻」下界语义，展示文案须带「按可推算条件」限定）。
 *
 * 不参与推算的条件大类（一律 null）：现场挑战（当场完成）、传感器/设备/网络/位置/天气类
 * （现实状态驱动）、应用内统计类（使用行为驱动）、依赖联动（他胶囊状态驱动）。
 * 天文类（月相/流星雨/节气/星座/农历区间）按逐日查表枚举未来一年内首个匹配日推算。
 */
object EarliestUnlockEstimator {

    /** 逐日枚举窗口（天）：天文/历法类条件的搜索上限 */
    private const val SCAN_DAYS = 400

    /**
     * 单条件最早满足时刻（epoch millis）；null = 无法静态推算。
     * 时间语义一律为「时刻下界」：按天推算的条件返回当日零点（当天窗口期内更早的实际满足
     * 时刻不展开——预估面板展示日期粒度已足够）。
     */
    fun earliestAt(condition: UnlockCondition, capsule: Capsule, now: Long, zone: ZoneId): Long? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val createdDay = Instant.ofEpochMilli(capsule.createTimestamp).atZone(zone).toLocalDate()
        return when (condition) {
            is UnlockCondition.FixedDate -> startOfDay(condition.targetDate, zone)

            is UnlockCondition.FixedDateTime -> runCatching {
                LocalDateTime.parse(condition.targetDateTime).atZone(zone).toInstant().toEpochMilli()
            }.getOrNull()

            is UnlockCondition.MinElapsedDay -> startOfDay(createdDay.plusDays(condition.days.toLong()), zone)

            is UnlockCondition.MinElapsedMinutes -> capsule.createTimestamp + condition.minutes * 60_000L

            is UnlockCondition.MinElapsedMonths -> startOfDay(
                Instant.ofEpochMilli(capsule.createTimestamp).atZone(zone).toLocalDateTime()
                    .plusMonths(condition.months.toLong()).toLocalDate(),
                zone,
            )

            is UnlockCondition.WeekDay ->
                firstMatchingDate(today, 8) { it.dayOfWeek in condition.weekSet }?.let {
                    startOfDay(it, zone)
                }

            is UnlockCondition.TimeRange -> {
                // 每日固定窗口：从今天起枚举 3 天的窗口起点；当前正处窗口内则以当前时刻为下界
                (0L..3L).asSequence()
                    .map { today.plusDays(it) }
                    .mapNotNull { day ->
                        val start = startOfDay(day, zone)?.plus(condition.startHour * 3_600_000L) ?: return@mapNotNull null
                        // 跨零点窗口（22-6）：起点在前一天晚，向后借一天
                        val effectiveStart =
                            if (condition.endHour > condition.startHour) start
                            else startOfDay(day.minusDays(1), zone)?.plus(condition.startHour * 3_600_000L) ?: return@mapNotNull null
                        effectiveStart.takeIf { it >= now }
                    }
                    .firstOrNull()
            }

            is UnlockCondition.MonthlyDay -> {
                val next = upcomingMonthlyDay(today, condition.dayOfMonth) ?: return null
                startOfDay(next, zone)
            }

            is UnlockCondition.YearlyDate -> {
                val date = resolveYearly(today.year, condition.month, condition.day)
                    ?: resolveYearly(today.year + 1, condition.month, condition.day)
                    ?: return null
                val next = if (!date.isBefore(today)) date else {
                    resolveYearly(today.year + 1, condition.month, condition.day) ?: return null
                }
                startOfDay(next, zone)
            }

            is UnlockCondition.MonthlyDaySet -> {
                val next = condition.days.mapNotNull { upcomingMonthlyDay(today, it) }.minOrNull() ?: return null
                startOfDay(next, zone)
            }

            is UnlockCondition.LastDayOfMonth -> {
                val thisEnd = YearMonth.of(today.year, today.monthValue).atEndOfMonth()
                val next = if (thisEnd.isAfter(today)) thisEnd else thisEnd.plusMonths(1)
                startOfDay(next, zone)
            }

            is UnlockCondition.NthWeekdayOfMonth -> {
                val next = firstMatchingDate(today, 62) { isNthWeekday(it, condition.nth, condition.dayOfWeek) }
                    ?: return null
                startOfDay(next, zone)
            }

            is UnlockCondition.YearlyNthWeekday -> {
                val next = firstMatchingDate(today, 366) {
                    it.monthValue == condition.month && isNthWeekday(it, condition.nth, condition.dayOfWeek)
                } ?: return null
                startOfDay(next, zone)
            }

            is UnlockCondition.RoundDaysElapsed -> {
                // 满 1 天后的首个 modulus 倍数日（与判定语义同构）
                (1..SCAN_DAYS).asSequence()
                    .map { createdDay.plusDays(it.toLong()) }
                    .firstOrNull { date ->
                        val elapsed = java.time.temporal.ChronoUnit.DAYS.between(createdDay, date)
                        elapsed >= 1 && elapsed % condition.modulus == 0L && !date.isBefore(today)
                    }?.let { startOfDay(it, zone) }
            }

            is UnlockCondition.LunarDate ->
                firstMatchingDate(today, SCAN_DAYS) { lunar ->
                    val l = LunarCalendar.solarToLunar(lunar)
                    l != null && l.month == condition.month && l.day == condition.day
                }?.let { startOfDay(it, zone) }

            is UnlockCondition.LunarMonthRange ->
                firstMatchingDate(today, SCAN_DAYS) {
                    LunarCalendar.solarToLunar(it)?.month == condition.month
                }?.let { startOfDay(it, zone) }

            is UnlockCondition.SolarTerm ->
                firstMatchingDate(today, SCAN_DAYS) { SolarTermCalendar.termOf(it) in condition.solarTerms }
                    ?.let { startOfDay(it, zone) }

            is UnlockCondition.ZodiacSeason ->
                firstMatchingDate(today, SCAN_DAYS) { SolarTermCalendar.zodiacOf(it) == condition.zodiac }
                    ?.let { startOfDay(it, zone) }

            is UnlockCondition.Season ->
                firstMatchingDate(today, SCAN_DAYS) { seasonOf(it) in condition.seasons }
                    ?.let { startOfDay(it, zone) }

            is UnlockCondition.MoonPhase ->
                firstMatchingDate(today, SCAN_DAYS) { MoonCalc.phaseOf(it) in condition.phases }
                    ?.let { startOfDay(it, zone) }

            is UnlockCondition.MeteorShower ->
                firstMatchingDate(today, SCAN_DAYS) { date ->
                    condition.showers.any { MeteorCalendar.isPeakNight(it, date) }
                }?.let { startOfDay(it, zone) }

            else -> null
        }
    }

    /**
     * 全规则预估：见类注释的合并语义。返回值仅供展示（"按可推算条件估算"），
     * 不参与判定（判定引擎仍是唯一事实源）。
     * 含子群组的规则按单元（组 + 未分组单例）先归并组内时刻，再按顶层逻辑归并单元；
     * 语义与 [com.muxiao.timart.domain.model.unlock.units] 的判定划分同源。
     */
    fun estimate(capsule: Capsule, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val rule = capsule.unlockRule
        if (rule.conditionList.isEmpty()) return null
        return if (rule.groups.isEmpty()) {
            reduce(rule.logicType, rule.threshold, rule.conditionList.map { earliestAt(it, capsule, now, zone) })
        } else {
            val unitTimes = com.muxiao.timart.domain.model.unlock.units(rule).map { unit ->
                val memberTimes = unit.indexes
                    .mapNotNull { rule.conditionList.getOrNull(it) }
                    .map { earliestAt(it, capsule, now, zone) }
                reduce(unit.logicType, unit.threshold, memberTimes)
            }
            reduce(rule.logicType, rule.threshold, unitTimes)
        }
    }

    /** 单层归并（与 UnlockJudgeUseCase.mergeFlat 同语义；null = 不可推算） */
    private fun reduce(logic: LogicType, threshold: Int?, times: List<Long?>): Long? = when (logic) {
        LogicType.AND -> {
            if (times.any { it == null }) null else times.filterNotNull().maxOrNull()
        }
        LogicType.OR -> times.filterNotNull().minOrNull()
        LogicType.AT_LEAST -> {
            if (times.any { it == null }) {
                null
            } else {
                val required = (threshold ?: times.size).coerceIn(1, times.size)
                times.filterNotNull().sorted()[required - 1]
            }
        }
    }

    // ---- 推算工具 ----

    private fun startOfDay(date: LocalDate, zone: ZoneId): Long? = runCatching {
        date.atStartOfDay(zone).toInstant().toEpochMilli()
    }.getOrNull()

    /** 从 [today] 起在 [window] 天内找首个满足 [pred] 的日期 */
    private fun firstMatchingDate(today: LocalDate, window: Int, pred: (LocalDate) -> Boolean): LocalDate? =
        (0 until window).asSequence()
            .map { today.plusDays(it.toLong()) }
            .firstOrNull(pred)

    /** 未来首个「当月 dayOfMonth 日」（2/30 等落回月末，宁早勿漏） */
    private fun upcomingMonthlyDay(today: LocalDate, dayOfMonth: Int): LocalDate? {
        if (dayOfMonth !in 1..31) return null
        val thisMonth = YearMonth.of(today.year, today.monthValue).atDay(dayOfMonth.coerceAtMost(YearMonth.of(today.year, today.monthValue).lengthOfMonth()))
        return if (!thisMonth.isBefore(today)) thisMonth else {
            val nextMonth = thisMonth.plusMonths(1)
            val ym = YearMonth.of(nextMonth.year, nextMonth.monthValue)
            ym.atDay(dayOfMonth.coerceAtMost(ym.lengthOfMonth()))
        }
    }

    /** 年-月-日合法化：2/29 落回当月最后一天（与 UpcomingReminders 同口径） */
    private fun resolveYearly(year: Int, month: Int, day: Int): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        val yearMonth = YearMonth.of(year, month)
        return yearMonth.atDay(day.coerceAtMost(yearMonth.lengthOfMonth()))
    }

    /** 是否为本月第 nth 个 dayOfWeek（与判定引擎同口径） */
    private fun isNthWeekday(date: LocalDate, nth: Int, dayOfWeek: java.time.DayOfWeek): Boolean =
        date.dayOfWeek == dayOfWeek && (date.dayOfMonth - 1) / 7 + 1 == nth

    /** 公历季节（与判定引擎同口径：3-5 春 / 6-8 夏 / 9-11 秋 / 12-2 冬） */
    private fun seasonOf(date: LocalDate): SeasonKind = when (date.monthValue) {
        3, 4, 5 -> SeasonKind.SPRING
        6, 7, 8 -> SeasonKind.SUMMER
        9, 10, 11 -> SeasonKind.AUTUMN
        else -> SeasonKind.WINTER
    }
}
