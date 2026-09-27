package com.muxiao.timart.utils.export

import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.domain.usecase.UpcomingReminders
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * iCalendar（.ics，RFC 5545）日历导出：把「等待中的胶囊」的确定性时间条件写进用户自己的日历。
 *
 * - 条件来源 = [UpcomingReminders.fixedTargetAt] 可静态推算的时间类（FixedDate / FixedDateTime /
 *   YearlyDate / 农历节气月相等），只导出未来目标（过去时刻本就不可再满足，宁少勿错）；
 * - FixedDateTime 带时刻 → 浮动本地时间事件（无时区语义，日历侧按设备本地时区解释）；
 *   YearlyDate → 全天事件 + `RRULE:FREQ=YEARLY`；其余日期类 → 全天单次事件；
 * - 纯 Kotlin（零 android import），可 JVM 单测；文本遵循 RFC 5545 转义与 75 字节折行。
 */
object CalendarExporter {

    /** 一条待导出日历事件 */
    data class IcsEvent(
        val uid: String,
        val summary: String,
        /** 全天事件的日期（[dateTime] == null 时使用） */
        val date: LocalDate,
        /** 带时刻事件的本地时间（浮动时间）；null = 全天事件 */
        val dateTime: LocalDateTime?,
        /** 年重复（YearlyDate → RRULE:FREQ=YEARLY） */
        val yearlyRepeat: Boolean = false,
    )

    private val UTC_STAMP: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC"))
    private val LOCAL_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val DAY_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    /**
     * 收集锁定胶囊的确定性时间条件未来目标。休眠种子过滤由调用方先行完成
     * （与小组件 upcomingEntries 同口径）；[summarize] 由调用方注入三语摘要（含胶囊标题）。
     */
    fun collect(
        capsules: List<Capsule>,
        now: Long,
        zone: ZoneId,
        summarize: (Capsule) -> String,
    ): List<IcsEvent> {
        val events = ArrayList<IcsEvent>()
        for (capsule in capsules) {
            if (capsule.state != CapsuleState.LOCKED) continue
            val createdDay = Instant.ofEpochMilli(capsule.createTimestamp).atZone(zone).toLocalDate()
            capsule.unlockRule.conditionList.forEachIndexed { index, condition ->
                val target = UpcomingReminders.fixedTargetAt(condition, now, zone, createdDay)
                    ?: return@forEachIndexed
                val summary = summarize(capsule)
                when (condition) {
                    is UnlockCondition.FixedDateTime -> runCatching {
                        LocalDateTime.parse(condition.targetDateTime)
                    }.getOrNull()?.let { dt ->
                        events.add(IcsEvent(uidOf(capsule.id, index), summary, dt.toLocalDate(), dt))
                    }

                    is UnlockCondition.YearlyDate -> events.add(
                        IcsEvent(
                            uidOf(capsule.id, index),
                            summary,
                            Instant.ofEpochMilli(target).atZone(zone).toLocalDate(),
                            dateTime = null,
                            yearlyRepeat = true,
                        ),
                    )

                    // FixedDate 及其余可推算日期类（农历/节气/月相等）：全天空事件（精度 1 天）
                    else -> events.add(
                        IcsEvent(
                            uidOf(capsule.id, index),
                            summary,
                            Instant.ofEpochMilli(target).atZone(zone).toLocalDate(),
                            dateTime = null,
                        ),
                    )
                }
            }
        }
        return events.sortedBy { it.dateTime ?: it.date.atStartOfDay() }
    }

    /** 组装 .ics 文本（CRLF 行尾、RFC 5545 转义与折行） */
    fun build(events: List<IcsEvent>, nowMillis: Long, calendarName: String): String {
        val stamp = UTC_STAMP.format(Instant.ofEpochMilli(nowMillis))
        return buildString {
            append("BEGIN:VCALENDAR\r\n")
            append("VERSION:2.0\r\n")
            append("PRODID:-//Timart//Shili//CN\r\n")
            append("CALSCALE:GREGORIAN\r\n")
            append("X-WR-CALNAME:").append(fold(escape(calendarName))).append("\r\n")
            for (event in events) {
                append("BEGIN:VEVENT\r\n")
                append("UID:").append(event.uid).append("\r\n")
                append("DTSTAMP:").append(stamp).append("\r\n")
                append("SUMMARY:").append(fold(escape(event.summary))).append("\r\n")
                val start = event.dateTime
                if (start != null) {
                    append("DTSTART:").append(LOCAL_STAMP.format(start)).append("\r\n")
                    append("DTEND:").append(LOCAL_STAMP.format(start.plusHours(1))).append("\r\n")
                } else {
                    append("DTSTART;VALUE=DATE:").append(DAY_STAMP.format(event.date)).append("\r\n")
                    append("DTEND;VALUE=DATE:").append(DAY_STAMP.format(event.date.plusDays(1))).append("\r\n")
                }
                if (event.yearlyRepeat) append("RRULE:FREQ=YEARLY\r\n")
                append("END:VEVENT\r\n")
            }
            append("END:VCALENDAR\r\n")
        }
    }

    private fun uidOf(capsuleId: String, index: Int): String = "$capsuleId-$index@timart.local"

    /** RFC 5545 TEXT 转义：反斜杠 / 分号 / 逗号 / 换行 */
    private fun escape(text: String): String = text
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")

    /**
     * 内容行折行：超 60 字符折为「CRLF + 空格」续行（按字符不按字节折，保证不切断
     * UTF-8 多字节序列；CJK 摘要折后行长温和超限，主流日历实现均接受）。
     */
    private fun fold(line: String): String {
        if (line.length <= FOLD_AT) return line
        val sb = StringBuilder(line.length + 16)
        sb.append(line, 0, FOLD_AT)
        var i = FOLD_AT
        while (i < line.length) {
            val end = minOf(i + FOLD_AT, line.length)
            sb.append("\r\n ").append(line, i, end)
            i = end
        }
        return sb.toString()
    }

    private const val FOLD_AT = 60
}
