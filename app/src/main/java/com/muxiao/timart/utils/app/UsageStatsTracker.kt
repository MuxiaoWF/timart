package com.muxiao.timart.utils.app

import android.content.Context
import android.content.SharedPreferences
import com.muxiao.timart.domain.context.UsageStatsProvider
import androidx.core.content.edit

/**
 * 应用内使用统计（打开次数 / 连续打开 / 上次打开），纯本地 SharedPreferences 持久化。
 *
 * 会话去重：[SESSION_GAP_MILLIS] 内重复进入不累计（一次会话一次）；
 * [lastOpenMillis] 返回"上一次会话"——刚开启的当前会话不参与
 * 「距上次打开超过 N 天」判定，否则该条件恒不满足。
 * 调用时机：MainActivity ON_RESUME 判定前执行 [recordOpen]。
 */
class UsageStatsTracker(context: Context) : UsageStatsProvider {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("usage_stats", Context.MODE_PRIVATE)

    /** 记录一次进入（会话去重）；返回 true = 本次计入新会话 */
    fun recordOpen(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val last = prefs.getLong(KEY_LAST_OPEN, 0L)
        if (nowMillis - last < SESSION_GAP_MILLIS) return false
        prefs.edit {
            putLong(KEY_PREV_OPEN, last)
                .putLong(KEY_LAST_OPEN, nowMillis)
                .putInt(KEY_OPEN_COUNT, prefs.getInt(KEY_OPEN_COUNT, 0) + 1)
                .putString(KEY_OPEN_DATES, prefs.getString(KEY_OPEN_DATES, null)?.let { dates ->
                    (dates.split(",").filter { it.isNotEmpty() } + dateKey(nowMillis))
                        .distinct()
                        .sorted()
                        .takeLast(KEEP_DAYS)
                        .joinToString(",")
                } ?: dateKey(nowMillis))
        }
        return true
    }

    override fun openCount(): Int = prefs.getInt(KEY_OPEN_COUNT, 0)

    override fun lastOpenMillis(): Long? {
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST_OPEN, 0L)
        val prev = prefs.getLong(KEY_PREV_OPEN, 0L)
        return when {
            last == 0L -> null
            // 当前会话刚开启（未超过去重窗口）：返回上一次会话
            now - last < SESSION_GAP_MILLIS -> if (prev == 0L) null else prev
            else -> last
        }
    }

    override fun openStreakDays(): Int {
        val dates = prefs.getString(KEY_OPEN_DATES, null)
            ?.split(",")
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: return 0
        var streak = 0
        var cursor = java.time.LocalDate.now()
        // 今日未开也不断签：从昨日回溯
        if (dateKeyOf(cursor) !in dates) cursor = cursor.minusDays(1)
        while (dateKeyOf(cursor) in dates) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    private fun dateKey(millis: Long): String =
        dateKeyOf(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate())

    private fun dateKeyOf(date: java.time.LocalDate): String = date.toString()

    private companion object {
        const val KEY_OPEN_COUNT = "openCount"
        const val KEY_LAST_OPEN = "lastOpen"
        const val KEY_PREV_OPEN = "prevOpen"
        const val KEY_OPEN_DATES = "openDates"
        const val KEEP_DAYS = 60
        const val SESSION_GAP_MILLIS = 10 * 60 * 1000L
    }
}
