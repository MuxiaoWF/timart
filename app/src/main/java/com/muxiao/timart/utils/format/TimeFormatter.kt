package com.muxiao.timart.utils.format

import com.muxiao.timart.l10n.currentStrings

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 设计稿格式化的时间纯函数。
 * 时间一律 epoch Long 存储，展示时本地化（java.time desugaring）。
 */
object TimeFormatter {

    private val DATE_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneId.systemDefault())

    private val DATE_TIME_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm").withZone(ZoneId.systemDefault())

    /** "封存于 2026.08.09" */
    fun sealDate(epochMillis: Long): String =
        currentStrings().sealOnPrefix + DATE_FORMATTER.format(Instant.ofEpochMilli(epochMillis))

    /** "2027.08.12 09:06" */
    fun dateTime(epochMillis: Long): String = DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis))

    /** "2026.08.09" */
    fun date(epochMillis: Long): String = DATE_FORMATTER.format(Instant.ofEpochMilli(epochMillis))
}
