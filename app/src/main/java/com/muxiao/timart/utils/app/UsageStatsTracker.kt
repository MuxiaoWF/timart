package com.muxiao.timart.utils.app

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Process
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
class UsageStatsTracker(private val context: Context) : UsageStatsProvider {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("usage_stats", Context.MODE_PRIVATE)

    /** 记录一次进入（会话去重）；返回 true = 本次计入新会话 */
    fun recordOpen(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val last = prefs.getLong(KEY_LAST_OPEN, 0L)
        if (nowMillis - last < SESSION_GAP_MILLIS) return false
        val today = dateKey(nowMillis)
        // 今日打开计数：跨日重置（TodayOpenCount 条件输入）
        val newTodayCount = if (prefs.getString(KEY_TODAY_DATE, null) == today) {
            prefs.getInt(KEY_TODAY_COUNT, 0) + 1
        } else {
            1
        }
        prefs.edit {
            putLong(KEY_PREV_OPEN, last)
                .putLong(KEY_LAST_OPEN, nowMillis)
                .putInt(KEY_OPEN_COUNT, prefs.getInt(KEY_OPEN_COUNT, 0) + 1)
                .putString(KEY_TODAY_DATE, today)
                .putInt(KEY_TODAY_COUNT, newTodayCount)
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

    override fun todayOpenCount(): Int {
        val today = dateKey(System.currentTimeMillis())
        return if (prefs.getString(KEY_TODAY_DATE, null) == today) prefs.getInt(KEY_TODAY_COUNT, 0) else 0
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

    /**
     * 今日指定应用前台使用时长（分钟；储备池 v7 应用用量条件通道，数字戒断）。
     * 使用统计是 AppOps 特殊权限（须用户在系统设置中授予），未授予返回 null → 判定 fail-closed。
     */
    override fun foregroundMinutesToday(packageName: String): Long? {
        if (!hasUsageStatsPermission()) return null
        return runCatching {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return null
            val zone = java.time.ZoneId.systemDefault()
            val begin = java.time.LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
            val now = System.currentTimeMillis()
            var total = 0L
            for (stat in usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, now)) {
                if (stat.packageName == packageName) total += stat.totalTimeInForeground
            }
            total / 60_000L
        }.getOrNull()
    }

    private fun hasUsageStatsPermission(): Boolean = hasUsageStatsPermission(context)

    private companion object {
        const val KEY_OPEN_COUNT = "openCount"
        const val KEY_LAST_OPEN = "lastOpen"
        const val KEY_PREV_OPEN = "prevOpen"
        const val KEY_OPEN_DATES = "openDates"
        const val KEY_TODAY_DATE = "todayDate"
        const val KEY_TODAY_COUNT = "todayCount"
        const val KEEP_DAYS = 60
        const val SESSION_GAP_MILLIS = 10 * 60 * 1000L
    }
}

/** 使用统计（AppOps 特殊权限）是否已授予：应用用量条件判定与创建侧引导共用 */
fun hasUsageStatsPermission(context: Context): Boolean = runCatching {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        ?: return false
    // AppOps 的 String 系检查 API 在新 SDK 上全线标记 deprecated，且没有覆盖 29+ 全区间的非弃用替代，
    // 两个分支同为受控的遗留口径（使用统计授予态检查的事实标准做法）。
    @Suppress("DEPRECATION")
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    mode == AppOpsManager.MODE_ALLOWED
}.getOrDefault(false)
