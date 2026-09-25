package com.muxiao.timart.utils.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.muxiao.timart.domain.context.StepHistoryProvider
import com.muxiao.timart.domain.context.StepProvider
import java.time.LocalDate
import java.time.YearMonth
import androidx.core.content.edit
import org.json.JSONObject

/**
 * 今日步数读取：TYPE_STEP_COUNTER（开机累计值）+ 每日步数历史落盘。
 *
 * 基线语义 = **当日零点的累计值估计**，按天持久化到 SharedPreferences：
 * - 当天内进程重启 → 恢复持久化基线，步数继续累计（修复旧实现"每次冷启动归零"）；
 * - 跨零点（进程存活）→ 重置基线为当前值（零点前的步数计入昨日，误差可接受）；
 * - 设备重启 → TYPE_STEP_COUNTER 归零，检测到数值回退即重建基线；
 * - 当日首次打开（无持久化基线）→ 以当前值为基线起算（零点至今的步数不可恢复，PRD 未定义更高精度）。
 *
 * 每日历史：每次读到今日步数即落盘 `day.<date>`（取当日已见最大值），滚动保留 [KEEP_DAYS] 天，
 * 供连续步数（StepStreak）条件查询。某天全程无采样（前台 + Worker 均未运行）→ 该日无记录 → streak fail-closed。
 *
 * **跨零点回补**：每次采样同时记录（日期, 累计值, 当时当日步数）；新一天首次采样时，
 * 昨日完整总量 = 昨日最后采样步数 + (当前累计值 − 昨日最后采样累计值)。
 * 累计值跨零点不归零，因此即使 App 昨晚 23 点后被杀、今晨才打开，昨日步数也能补准
 * （仅设备重启期间的数据硬件层面丢失，属全行业共性）。
 *
 * **月度归档（储备池 v5 累计步行条件）**：日级记录滑出 [KEEP_DAYS] 窗口前，把当日终值并入
 * 月度归档 `cum.<yyyy-MM>`（JSON：日号 → 步数），供 `stepsSince` 做自封存日起的累计求和——
 * 归档覆盖窗口外的历史日，日级记录覆盖最近 [KEEP_DAYS] 天，两者互补不重叠；
 * 归档只记已采样日（缺采样日贡献 0），仅设备重启期间的数据硬件层面无法回补。
 *
 * 无计步硬件 / API 29+ 无 ACTIVITY_RECOGNITION 权限时返回 null；
 * 注册监听后传感器尚未上报首帧前也返回 null（不显示误导性的 0）。
 */
class StepCounterReader(private val context: Context) : StepProvider, StepHistoryProvider {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    /** 当日基线持久化（key 为基线日期 + 该日零点估计的累计值） */
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var latestCounterValue: Float? = null

    @Volatile
    private var baselineValue: Float? = null

    @Volatile
    private var baselineDate: LocalDate? = null

    @Volatile
    private var listening = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            latestCounterValue = event.values.firstOrNull()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun ensureListening() {
        if (listening || stepSensor == null) return
        val registered = sensorManager.registerListener(
            listener,
            stepSensor,
            SensorManager.SENSOR_DELAY_UI,
        )
        listening = registered
    }

    @Synchronized
    override fun todaySteps(): Int? {
        if (stepSensor == null) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        ensureListening()
        val value = latestCounterValue ?: return null
        val today = LocalDate.now()

        var baseline = baselineValue
        var baseDate = baselineDate
        var dayChanged = false
        when {
            // 进程内首次读取：优先恢复当日持久化基线
            baseDate == null || baseline == null -> {
                val savedDate = prefs.getString(KEY_BASELINE_DATE, null)
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val saved = if (savedDate != null) prefs.getFloat(KEY_BASELINE_COUNTER, Float.NaN) else Float.NaN
                if (savedDate == today && !saved.isNaN()) {
                    baseline = saved
                    baseDate = today
                } else {
                    // 当日首次读取（隔日/首次安装）：今日已有采样时先回推基线（当前累计值 −
                    // 最近采样步数），进程内基线意外丢失也不归零；无采样才以当前值起算
                    val lastDate = prefs.getString(KEY_LAST_DATE, null)
                        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    val lastSteps = prefs.getInt(KEY_LAST_STEPS, -1)
                    val lastCum = prefs.getFloat(KEY_LAST_CUM, Float.NaN)
                    baseline = if (lastDate == today && lastSteps >= 0 && !lastCum.isNaN() && value >= lastCum) {
                        value - lastSteps
                    } else {
                        value
                    }
                    baseDate = today
                    dayChanged = savedDate != null
                }
            }

            // 进程存活跨零点：重置基线（零点前的步数计入昨日）
            baseDate != today -> {
                baseline = value
                baseDate = today
                dayChanged = true
            }

            // TYPE_STEP_COUNTER 开机累计：数值回退 = 设备重启，重建基线
            value < baseline -> {
                baseline = value
            }
        }

        // 跨零点回补（仅 dayChanged 的首次采样执行一次，防止当日后续采样把新一天的步数漏进昨日）：
        // 昨日完整总量 = 昨日最后采样步数 + (当前累计值 − 昨日最后采样累计值)。
        // 累计值跨零点不归零，故 App 昨晚被杀、今晨才打开也能补准；
        // 仅昨日的最后采样可回补（更早缺该日零点累计值）；设备重启（累计值回退）无法回补。
        if (dayChanged) {
            val lastSampleDate = prefs.getString(KEY_LAST_DATE, null)
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (lastSampleDate == today.minusDays(1)) {
                val lastCum = prefs.getFloat(KEY_LAST_CUM, Float.NaN)
                val lastSteps = prefs.getInt(KEY_LAST_STEPS, -1)
                if (!lastCum.isNaN() && lastSteps >= 0 && value >= lastCum) {
                    val yesterdayFinal = lastSteps + (value - lastCum).toInt()
                    if (yesterdayFinal > prefs.getInt(dayKey(lastSampleDate), -1)) {
                        prefs.edit { putInt(dayKey(lastSampleDate), yesterdayFinal) }
                    }
                }
            }
        }

        baselineValue = baseline
        baselineDate = baseDate
        prefs.edit {
            putString(KEY_BASELINE_DATE, baseDate.toString())
            putFloat(KEY_BASELINE_COUNTER, baseline)
        }

        val steps = (value - baseline).toInt().coerceAtLeast(0)

        // 每日历史落盘：当日取已见最大值（重启重建基线后可能短暂变小，不回退历史）
        if (steps > prefs.getInt(dayKey(today), -1)) {
            prefs.edit { putInt(dayKey(today), steps) }
            pruneHistory(today)
        }

        // 记录最近采样三元组（供跨零点回补昨日总量）
        prefs.edit {
            putString(KEY_LAST_DATE, today.toString())
                .putFloat(KEY_LAST_CUM, value)
                .putInt(KEY_LAST_STEPS, steps)
        }
        return steps
    }

    /** 每日步数历史：[daysAgo] = 0 即今日；无记录返回 null（fail-closed） */
    override fun daySteps(daysAgo: Int): Int? {
        if (daysAgo < 0) return null
        val date = LocalDate.now().minusDays(daysAgo.toLong())
        val stored = prefs.getInt(dayKey(date), -1)
        return stored.takeIf { it >= 0 }
    }

    /**
     * 自 [sinceDate]（含）至今天的累计步行（储备池 v5 累计步行条件通道）。
     * 口径 = 已采样每日步数合计：日级记录（最近 [KEEP_DAYS] 天）+ 月度归档（更早的已采样日），
     * 缺采样日按 0 计（App 未运行期间的步数无法回补，与计步器硬件能力一致）。
     * 返回 null = 无计步硬件 / 无权限（判定侧按「设备不支持」fail-closed）。
     */
    @Synchronized
    override fun stepsSince(sinceDate: LocalDate): Long? {
        if (stepSensor == null) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        // 先触发一次采样：保证今日步数已落盘（顺带完成权限与硬件校验）
        todaySteps() ?: return null

        val today = LocalDate.now()
        if (sinceDate.isAfter(today)) return 0L
        var total = 0L
        val windowStart = today.minusDays(KEEP_DAYS.toLong())

        // 1. 日级记录窗口：[max(sinceDate, windowStart) .. today]
        var day = if (sinceDate.isAfter(windowStart)) sinceDate else windowStart
        while (!day.isAfter(today)) {
            prefs.getInt(dayKey(day), -1).takeIf { it >= 0 }?.let { total += it }
            day = day.plusDays(1)
        }

        // 2. 月度归档：滑出日级窗口的历史日（全部 < windowStart，且仅计 sinceDate 之后的部分）
        for (key in prefs.all.keys) {
            if (!key.startsWith(ARCHIVE_PREFIX)) continue
            val month = runCatching { YearMonth.parse(key.removePrefix(ARCHIVE_PREFIX)) }.getOrNull() ?: continue
            val obj = prefs.getString(key, null)
                ?.let { runCatching { JSONObject(it) }.getOrNull() }
                ?: continue
            for (dayOfMonth in 1..month.lengthOfMonth()) {
                val value = obj.optInt(dayOfMonth.toString(), -1)
                if (value < 0) continue
                val date = runCatching { month.atDay(dayOfMonth) }.getOrNull() ?: continue
                if (!date.isBefore(sinceDate) && date.isBefore(windowStart)) total += value
            }
        }
        return total
    }

    /** 滚动清理超出保留窗口的历史记录（滑出前先并入月度归档，供累计步行条件查询） */
    private fun pruneHistory(today: LocalDate) {
        val all = prefs.all.keys.filter { it.startsWith(KEY_DAY_PREFIX) }
        val stale = all.filter { key ->
            val date = runCatching { LocalDate.parse(key.removePrefix(KEY_DAY_PREFIX)) }.getOrNull()
            date == null || date.isBefore(today.minusDays(KEEP_DAYS.toLong()))
        }
        if (stale.isNotEmpty()) {
            prefs.edit {
                stale.forEach { key ->
                    val date = runCatching { LocalDate.parse(key.removePrefix(KEY_DAY_PREFIX)) }.getOrNull()
                    if (date != null) {
                        val value = prefs.getInt(key, -1)
                        if (value >= 0) {
                            val archiveKey = ARCHIVE_PREFIX + YearMonth.from(date)
                            val obj = prefs.getString(archiveKey, null)
                                ?.let { runCatching { JSONObject(it) }.getOrNull() }
                                ?: JSONObject()
                            obj.put(date.dayOfMonth.toString(), value)
                            putString(archiveKey, obj.toString())
                        }
                    }
                    remove(key)
                }
            }
        }
    }

    private fun dayKey(date: LocalDate) = KEY_DAY_PREFIX + date.toString()

    private companion object {
        const val PREFS_NAME = "step_counter_baseline"
        const val KEY_BASELINE_DATE = "baseline_date"
        const val KEY_BASELINE_COUNTER = "baseline_counter"
        const val KEY_DAY_PREFIX = "day."

        /** 月度归档前缀（值 = JSON：日号 → 当日终值步数；储备池 v5 累计步行条件通道） */
        const val ARCHIVE_PREFIX = "cum."

        /** 最近采样三元组（跨零点回补用） */
        const val KEY_LAST_DATE = "last_sample_date"
        const val KEY_LAST_CUM = "last_sample_cum"
        const val KEY_LAST_STEPS = "last_sample_steps"

        /** 每日历史保留窗口（天）；覆盖 streak 表单上限 30 天，留余量 */
        const val KEEP_DAYS = 40
    }
}
