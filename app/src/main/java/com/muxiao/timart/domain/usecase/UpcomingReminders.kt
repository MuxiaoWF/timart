package com.muxiao.timart.domain.usecase

import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
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
 * 仅对「确定性时间条件」估算可预知的到达时刻——`FixedDate`（指定日）、`FixedDateTime`
 * （指定时刻）、`YearlyDate`（每年纪念日取下一次）三种；相对时长（MinElapsed*）需创建时刻
 * 推算也可预知，但语义上「提前 N 天」提示价值低，且农历 `LunarDate` 需农历换算表，
 * 均不参与（宁可少提醒，不猜测——与挑战条件 fail-closed 同哲学）。
 *
 * 提醒去重（每档只发一条）由调用方负责：meta `settings.remindSent.<id>` 记录上次已提醒的
 * 剩余天数档位，本类只输出当期应提醒集合（capsuleId, 目标时刻, 剩余整天数）。
 */
object UpcomingReminders {

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** 一条待提醒（daysLeft = 距目标时刻的整天数，向上取整，≥1） */
    data class Due(val capsule: Capsule, val targetAt: Long, val daysLeft: Int)

    /**
     * 单条件的确定性目标时刻（epoch millis）；非时间类 / 过去时刻 / 解析失败返回 null。
     * [now] 与 [zone] 决定「下一次」语义（YearlyDate 取今天或未来最近一次）。
     */
    fun fixedTargetAt(condition: UnlockCondition, now: Long, zone: ZoneId): Long? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
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

    /**
     * 全库扫描：状态 LOCKED、用户设置了提前量（[leadDaysOf] 返回非 null）、
     * 任一确定性条件的目标时刻落在提前量窗口内 → 待提醒。多条确定性条件取最近者。
     */
    fun due(
        capsules: List<Capsule>,
        leadDaysOf: (String) -> Int?,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Due> = capsules.mapNotNull { capsule ->
        if (capsule.state != CapsuleState.LOCKED) return@mapNotNull null
        val lead = leadDaysOf(capsule.id)?.takeIf { it in 1..30 } ?: return@mapNotNull null
        val target = capsule.unlockRule.conditionList
            .mapNotNull { fixedTargetAt(it, now, zone) }
            .minOrNull() ?: return@mapNotNull null
        val daysLeft = ceil((target - now).toDouble() / DAY_MS).toInt()
        if (daysLeft in 1..lead) Due(capsule, target, daysLeft) else null
    }
}
