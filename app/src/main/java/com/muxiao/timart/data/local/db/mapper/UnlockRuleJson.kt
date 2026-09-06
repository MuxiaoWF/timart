package com.muxiao.timart.data.local.db.mapper

import android.util.Log
import com.muxiao.timart.domain.model.unlock.GestureKind
import com.muxiao.timart.domain.model.unlock.LogicType
import com.muxiao.timart.domain.model.unlock.MoonPhaseKind
import com.muxiao.timart.domain.model.unlock.MotionKind
import com.muxiao.timart.domain.model.unlock.NetType
import com.muxiao.timart.domain.model.unlock.SunPhaseKind
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.domain.model.unlock.UnlockRule
import com.muxiao.timart.domain.model.unlock.WeatherMetricKind
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 解锁规则 DTO（扁平 ConditionDto + type 判别，格式见 ARCHITECTURE §5） */
@Serializable
data class UnlockRuleDto(
    val v: Int = 1,
    val logicType: String,
    val conditions: List<ConditionDto> = emptyList(),
)

/** 条件 DTO：字段全可空、按 type 取用，跨版本恢复稳定 */
@Serializable
data class ConditionDto(
    val type: String,
    val isoDate: String? = null,
    val days: Int? = null,
    val weekDays: List<String>? = null,
    val startHour: Int? = null,
    val endHour: Int? = null,
    val min: Int? = null,
    val max: Int? = null,
    val isCharging: Boolean? = null,
    val minTodaySteps: Int? = null,
    val netTypes: List<String>? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val radiusMeter: Int? = null,
    val weatherTypes: List<String>? = null,
    val minTempC: Double? = null,
    val maxTempC: Double? = null,
    val ssids: List<String>? = null,
    // ---- 扩展条件字段 ----
    val isoDateTime: String? = null,
    val minutes: Long? = null,
    val dayOfMonth: Int? = null,
    val month: Int? = null,
    val phases: List<String>? = null,
    val metric: String? = null,
    val minVal: Double? = null,
    val maxVal: Double? = null,
    val flag: Boolean? = null,
    val motionKinds: List<String>? = null,
    val deg: Int? = null,
    val tolerance: Int? = null,
    val count: Int? = null,
    val capsuleId: String? = null,
    val challengeId: String? = null,
    val question: String? = null,
    val answer: String? = null,
    val answerHash: String? = null,
    val gesture: String? = null,
    val holdSeconds: Int? = null,
    val nfcPayload: String? = null,
)

/**
 * UnlockRule ↔ JSON 双向编解码。
 * fromJson 对未知 type / 缺必填字段容错丢弃该条并 log（向前兼容），不抛异常。
 */
object UnlockRuleJson {

    private const val TAG = "TimartUnlockRule"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        isLenient = true
    }

    // ---- 条件 type 字符串常量（§5.1）----
    private const val TYPE_FIXED_DATE = "FIXED_DATE"
    private const val TYPE_MIN_ELAPSED_DAYS = "MIN_ELAPSED_DAYS"
    private const val TYPE_WEEK_DAY = "WEEK_DAY"
    private const val TYPE_TIME_RANGE = "TIME_RANGE"
    private const val TYPE_BATTERY = "BATTERY"
    private const val TYPE_CHARGING = "CHARGING"
    private const val TYPE_STEPS = "STEPS"
    private const val TYPE_NETWORK = "NETWORK"
    private const val TYPE_GPS = "GPS_LOCATION"
    private const val TYPE_WEATHER = "WEATHER"
    private const val TYPE_TEMPERATURE = "TEMPERATURE"
    private const val TYPE_SSID = "WIFI_SSID"
    private const val TYPE_STREAK = "STEP_STREAK"
    // ---- 扩展条件（26 种）----
    private const val TYPE_FIXED_DATE_TIME = "FIXED_DATE_TIME"
    private const val TYPE_MIN_ELAPSED_MINUTES = "MIN_ELAPSED_MINUTES"
    private const val TYPE_MONTHLY_DAY = "MONTHLY_DAY"
    private const val TYPE_YEARLY_DATE = "YEARLY_DATE"
    private const val TYPE_AWAY_GPS = "AWAY_FROM_LOCATION"
    private const val TYPE_SUN_PHASE = "SUN_PHASE"
    private const val TYPE_WEATHER_METRIC = "WEATHER_METRIC"
    private const val TYPE_MOON_PHASE = "MOON_PHASE"
    private const val TYPE_BEFORE_ALARM = "BEFORE_NEXT_ALARM"
    private const val TYPE_POWER_SAVE = "POWER_SAVE_MODE"
    private const val TYPE_SILENT = "SILENT_MODE"
    private const val TYPE_HEADPHONE = "HEADPHONE_CONNECTED"
    private const val TYPE_MOTION = "MOTION_ACTIVITY"
    private const val TYPE_COMPASS = "COMPASS_HEADING"
    private const val TYPE_ALTITUDE = "ALTITUDE_RANGE"
    private const val TYPE_OPEN_COUNT = "OPEN_COUNT"
    private const val TYPE_OPEN_STREAK = "OPEN_STREAK"
    private const val TYPE_LAST_OPEN = "DAYS_SINCE_LAST_OPEN"
    private const val TYPE_CAPSULE_COUNT = "CAPSULE_COUNT"
    private const val TYPE_OTHER_UNLOCKED = "OTHER_CAPSULE_UNLOCKED"
    private const val TYPE_OTHER_DESTROYED = "OTHER_CAPSULE_DESTROYED"
    private const val TYPE_QUESTION = "QUESTION_ANSWER"
    private const val TYPE_PUZZLE = "PUZZLE_ANSWER"
    private const val TYPE_SHAKE = "SHAKE_COUNT"
    private const val TYPE_FLIP_HOLD = "FLIP_OR_HOLD"
    private const val TYPE_NFC = "NFC_TAP"

    /** 领域规则 → §5.1 JSON */
    fun toJson(rule: UnlockRule): String {
        val dto = UnlockRuleDto(
            v = 1,
            logicType = rule.logicType.name,
            conditions = rule.conditionList.mapNotNull { toDto(it) },
        )
        return json.encodeToString(UnlockRuleDto.serializer(), dto)
    }

    /** §5.1 JSON → 领域规则；非法条目丢弃，整体解析失败回退空 AND 规则 */
    fun fromJson(raw: String): UnlockRule {
        if (raw.isBlank()) return UnlockRule(LogicType.AND, emptyList())
        val dto = try {
            json.decodeFromString(UnlockRuleDto.serializer(), raw)
        } catch (e: Exception) {
            Log.w(TAG, "unlockRuleJson 解析失败，回退空规则", e)
            return UnlockRule(LogicType.AND, emptyList())
        }
        val logic = runCatching { LogicType.valueOf(dto.logicType) }.getOrDefault(LogicType.AND)
        val conditions = dto.conditions.mapNotNull { it.toCondition() }
        return UnlockRule(logic, conditions)
    }

    // ---- 领域 → DTO ----

    private fun toDto(condition: UnlockCondition): ConditionDto? = when (condition) {
        is UnlockCondition.FixedDate ->
            ConditionDto(type = TYPE_FIXED_DATE, isoDate = condition.targetDate.toString())
        is UnlockCondition.MinElapsedDay ->
            ConditionDto(type = TYPE_MIN_ELAPSED_DAYS, days = condition.days)
        is UnlockCondition.WeekDay ->
            ConditionDto(type = TYPE_WEEK_DAY, weekDays = condition.weekSet.map { it.name })
        is UnlockCondition.TimeRange ->
            ConditionDto(type = TYPE_TIME_RANGE, startHour = condition.startHour, endHour = condition.endHour)
        is UnlockCondition.BatteryLevel ->
            ConditionDto(type = TYPE_BATTERY, min = condition.min, max = condition.max)
        is UnlockCondition.ChargingState ->
            ConditionDto(type = TYPE_CHARGING, isCharging = condition.isCharging)
        is UnlockCondition.StepCount ->
            ConditionDto(type = TYPE_STEPS, minTodaySteps = condition.minTodayStep)
        is UnlockCondition.NetworkType ->
            ConditionDto(type = TYPE_NETWORK, netTypes = condition.types.map { it.name })
        is UnlockCondition.GpsLocation ->
            ConditionDto(
                type = TYPE_GPS,
                lat = condition.lat,
                lng = condition.lng,
                radiusMeter = condition.radiusMeter,
            )
        is UnlockCondition.WeatherType ->
            ConditionDto(type = TYPE_WEATHER, weatherTypes = condition.weatherTypes.toList())
        is UnlockCondition.TemperatureThreshold ->
            ConditionDto(type = TYPE_TEMPERATURE, minTempC = condition.minC, maxTempC = condition.maxC)
        is UnlockCondition.SsidMatch ->
            ConditionDto(type = TYPE_SSID, ssids = condition.ssids.toList())
        is UnlockCondition.StepStreak ->
            ConditionDto(type = TYPE_STREAK, days = condition.days, minTodaySteps = condition.goal)
        is UnlockCondition.FixedDateTime ->
            ConditionDto(type = TYPE_FIXED_DATE_TIME, isoDateTime = condition.targetDateTime)
        is UnlockCondition.MinElapsedMinutes ->
            ConditionDto(type = TYPE_MIN_ELAPSED_MINUTES, minutes = condition.minutes)
        is UnlockCondition.MonthlyDay ->
            ConditionDto(type = TYPE_MONTHLY_DAY, dayOfMonth = condition.dayOfMonth)
        is UnlockCondition.YearlyDate ->
            ConditionDto(type = TYPE_YEARLY_DATE, month = condition.month, dayOfMonth = condition.day)
        is UnlockCondition.AwayFromLocation ->
            ConditionDto(
                type = TYPE_AWAY_GPS,
                lat = condition.lat,
                lng = condition.lng,
                radiusMeter = condition.radiusMeter,
            )
        is UnlockCondition.SunPhase ->
            ConditionDto(type = TYPE_SUN_PHASE, phases = condition.phases.map { it.name })
        is UnlockCondition.WeatherMetric ->
            ConditionDto(
                type = TYPE_WEATHER_METRIC,
                metric = condition.metric.name,
                minVal = condition.min,
                maxVal = condition.max,
            )
        is UnlockCondition.MoonPhase ->
            ConditionDto(type = TYPE_MOON_PHASE, phases = condition.phases.map { it.name })
        is UnlockCondition.BeforeNextAlarm ->
            ConditionDto(type = TYPE_BEFORE_ALARM)
        is UnlockCondition.PowerSaveMode ->
            ConditionDto(type = TYPE_POWER_SAVE, flag = condition.isActive)
        is UnlockCondition.SilentMode ->
            ConditionDto(type = TYPE_SILENT, flag = condition.isSilent)
        is UnlockCondition.HeadphoneConnected ->
            ConditionDto(type = TYPE_HEADPHONE, flag = condition.isConnected)
        is UnlockCondition.MotionActivity ->
            ConditionDto(type = TYPE_MOTION, motionKinds = condition.kinds.map { it.name })
        is UnlockCondition.CompassHeading ->
            ConditionDto(type = TYPE_COMPASS, deg = condition.targetDeg, tolerance = condition.toleranceDeg)
        is UnlockCondition.AltitudeRange ->
            ConditionDto(type = TYPE_ALTITUDE, minVal = condition.minM, maxVal = condition.maxM)
        is UnlockCondition.OpenCountAtLeast ->
            ConditionDto(type = TYPE_OPEN_COUNT, count = condition.count)
        is UnlockCondition.OpenStreak ->
            ConditionDto(type = TYPE_OPEN_STREAK, days = condition.days)
        is UnlockCondition.DaysSinceLastOpen ->
            ConditionDto(type = TYPE_LAST_OPEN, days = condition.days)
        is UnlockCondition.CapsuleCountAtLeast ->
            ConditionDto(type = TYPE_CAPSULE_COUNT, count = condition.count)
        is UnlockCondition.OtherCapsuleUnlocked ->
            ConditionDto(type = TYPE_OTHER_UNLOCKED, capsuleId = condition.capsuleId)
        is UnlockCondition.OtherCapsuleDestroyed ->
            ConditionDto(type = TYPE_OTHER_DESTROYED, capsuleId = condition.capsuleId)
        is UnlockCondition.QuestionAnswer ->
            ConditionDto(
                type = TYPE_QUESTION,
                challengeId = condition.challengeId,
                question = condition.question,
                answer = condition.expectedAnswer,
            )
        is UnlockCondition.PuzzleAnswer ->
            ConditionDto(
                type = TYPE_PUZZLE,
                challengeId = condition.challengeId,
                question = condition.question,
                answerHash = condition.answerHash,
            )
        is UnlockCondition.ShakeCount ->
            ConditionDto(type = TYPE_SHAKE, challengeId = condition.challengeId, count = condition.shakes)
        is UnlockCondition.FlipOrHold ->
            ConditionDto(
                type = TYPE_FLIP_HOLD,
                challengeId = condition.challengeId,
                gesture = condition.gesture.name,
                holdSeconds = condition.holdSeconds,
            )
        is UnlockCondition.NfcTap ->
            ConditionDto(
                type = TYPE_NFC,
                challengeId = condition.challengeId,
                nfcPayload = condition.expectedPayload,
            )
    }

    // ---- DTO → 领域（必填字段校验，缺失/非法返回 null 丢弃）----

    private fun ConditionDto.toCondition(): UnlockCondition? = try {
        when (type) {
            TYPE_FIXED_DATE -> UnlockCondition.FixedDate(
                targetDate = LocalDate.parse(requireNotNull(isoDate) { "缺 isoDate" }),
            )
            TYPE_MIN_ELAPSED_DAYS -> UnlockCondition.MinElapsedDay(
                days = requireNotNull(days) { "缺 days" },
            )
            TYPE_WEEK_DAY -> UnlockCondition.WeekDay(
                weekSet = requireNotNull(weekDays) { "缺 weekDays" }
                    .map { DayOfWeek.valueOf(it) }
                    .toSet(),
            )
            TYPE_TIME_RANGE -> UnlockCondition.TimeRange(
                startHour = requireNotNull(startHour) { "缺 startHour" },
                endHour = requireNotNull(endHour) { "缺 endHour" },
            )
            TYPE_BATTERY -> UnlockCondition.BatteryLevel(min = min, max = max)
            TYPE_CHARGING -> UnlockCondition.ChargingState(
                isCharging = requireNotNull(isCharging) { "缺 isCharging" },
            )
            TYPE_STEPS -> UnlockCondition.StepCount(
                minTodayStep = requireNotNull(minTodaySteps) { "缺 minTodaySteps" },
            )
            TYPE_NETWORK -> UnlockCondition.NetworkType(
                types = requireNotNull(netTypes) { "缺 netTypes" }
                    .mapNotNull { name -> runCatching { NetType.valueOf(name) }.getOrNull() }
                    .toSet(),
            )
            TYPE_GPS -> UnlockCondition.GpsLocation(
                lat = requireNotNull(lat) { "缺 lat" },
                lng = requireNotNull(lng) { "缺 lng" },
                radiusMeter = requireNotNull(radiusMeter) { "缺 radiusMeter" },
            )
            TYPE_WEATHER -> UnlockCondition.WeatherType(
                weatherTypes = requireNotNull(weatherTypes) { "缺 weatherTypes" }.toSet(),
            )
            TYPE_TEMPERATURE -> UnlockCondition.TemperatureThreshold(
                minC = minTempC,
                maxC = maxTempC,
            )
            TYPE_SSID -> UnlockCondition.SsidMatch(
                ssids = requireNotNull(ssids) { "缺 ssids" }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
                    .also { require(it.isNotEmpty()) { "ssids 全空" } },
            )
            TYPE_STREAK -> UnlockCondition.StepStreak(
                days = requireNotNull(days) { "缺 days" }.also { require(it >= 1) { "days 非法" } },
                goal = requireNotNull(minTodaySteps) { "缺 minTodaySteps" },
            )
            TYPE_FIXED_DATE_TIME -> UnlockCondition.FixedDateTime(
                targetDateTime = requireNotNull(isoDateTime) { "缺 isoDateTime" },
            )
            TYPE_MIN_ELAPSED_MINUTES -> UnlockCondition.MinElapsedMinutes(
                minutes = requireNotNull(minutes) { "缺 minutes" }.also { require(it >= 1) { "minutes 非法" } },
            )
            TYPE_MONTHLY_DAY -> UnlockCondition.MonthlyDay(
                dayOfMonth = requireNotNull(dayOfMonth) { "缺 dayOfMonth" }
                    .also { require(it in 1..31) { "dayOfMonth 非法" } },
            )
            TYPE_YEARLY_DATE -> UnlockCondition.YearlyDate(
                month = requireNotNull(month) { "缺 month" }.also { require(it in 1..12) { "month 非法" } },
                day = requireNotNull(dayOfMonth) { "缺 day" }.also { require(it in 1..31) { "day 非法" } },
            )
            TYPE_AWAY_GPS -> UnlockCondition.AwayFromLocation(
                lat = requireNotNull(lat) { "缺 lat" },
                lng = requireNotNull(lng) { "缺 lng" },
                radiusMeter = requireNotNull(radiusMeter) { "缺 radiusMeter" },
            )
            TYPE_SUN_PHASE -> UnlockCondition.SunPhase(
                phases = requireNotNull(phases) { "缺 phases" }
                    .mapNotNull { runCatching { SunPhaseKind.valueOf(it) }.getOrNull() }
                    .toSet()
                    .also { require(it.isNotEmpty()) { "phases 全空" } },
            )
            TYPE_WEATHER_METRIC -> UnlockCondition.WeatherMetric(
                metric = requireNotNull(metric) { "缺 metric" }.let { WeatherMetricKind.valueOf(it) },
                min = minVal,
                max = maxVal,
            )
            TYPE_MOON_PHASE -> UnlockCondition.MoonPhase(
                phases = requireNotNull(phases) { "缺 phases" }
                    .mapNotNull { runCatching { MoonPhaseKind.valueOf(it) }.getOrNull() }
                    .toSet()
                    .also { require(it.isNotEmpty()) { "phases 全空" } },
            )
            TYPE_BEFORE_ALARM -> UnlockCondition.BeforeNextAlarm
            TYPE_POWER_SAVE -> UnlockCondition.PowerSaveMode(
                isActive = requireNotNull(flag) { "缺 flag" },
            )
            TYPE_SILENT -> UnlockCondition.SilentMode(
                isSilent = requireNotNull(flag) { "缺 flag" },
            )
            TYPE_HEADPHONE -> UnlockCondition.HeadphoneConnected(
                isConnected = requireNotNull(flag) { "缺 flag" },
            )
            TYPE_MOTION -> UnlockCondition.MotionActivity(
                kinds = requireNotNull(motionKinds) { "缺 motionKinds" }
                    .mapNotNull { runCatching { MotionKind.valueOf(it) }.getOrNull() }
                    .toSet()
                    .also { require(it.isNotEmpty()) { "motionKinds 全空" } },
            )
            TYPE_COMPASS -> UnlockCondition.CompassHeading(
                targetDeg = requireNotNull(deg) { "缺 deg" }.also { require(it in 0..359) { "deg 非法" } },
                toleranceDeg = requireNotNull(tolerance) { "缺 tolerance" }
                    .also { require(it in 1..180) { "tolerance 非法" } },
            )
            TYPE_ALTITUDE -> UnlockCondition.AltitudeRange(minM = minVal, maxM = maxVal)
            TYPE_OPEN_COUNT -> UnlockCondition.OpenCountAtLeast(
                count = requireNotNull(count) { "缺 count" }.also { require(it >= 1) { "count 非法" } },
            )
            TYPE_OPEN_STREAK -> UnlockCondition.OpenStreak(
                days = requireNotNull(days) { "缺 days" }.also { require(it >= 1) { "days 非法" } },
            )
            TYPE_LAST_OPEN -> UnlockCondition.DaysSinceLastOpen(
                days = requireNotNull(days) { "缺 days" }.also { require(it >= 1) { "days 非法" } },
            )
            TYPE_CAPSULE_COUNT -> UnlockCondition.CapsuleCountAtLeast(
                count = requireNotNull(count) { "缺 count" }.also { require(it >= 1) { "count 非法" } },
            )
            TYPE_OTHER_UNLOCKED -> UnlockCondition.OtherCapsuleUnlocked(
                capsuleId = requireNotNull(capsuleId) { "缺 capsuleId" },
            )
            TYPE_OTHER_DESTROYED -> UnlockCondition.OtherCapsuleDestroyed(
                capsuleId = requireNotNull(capsuleId) { "缺 capsuleId" },
            )
            TYPE_QUESTION -> UnlockCondition.QuestionAnswer(
                challengeId = requireNotNull(challengeId) { "缺 challengeId" },
                question = requireNotNull(question) { "缺 question" }.also { require(it.isNotBlank()) { "question 空" } },
                expectedAnswer = requireNotNull(answer) { "缺 answer" },
            )
            TYPE_PUZZLE -> UnlockCondition.PuzzleAnswer(
                challengeId = requireNotNull(challengeId) { "缺 challengeId" },
                question = requireNotNull(question) { "缺 question" }.also { require(it.isNotBlank()) { "question 空" } },
                answerHash = requireNotNull(answerHash) { "缺 answerHash" },
            )
            TYPE_SHAKE -> UnlockCondition.ShakeCount(
                challengeId = requireNotNull(challengeId) { "缺 challengeId" },
                shakes = requireNotNull(count) { "缺 count" }.also { require(it >= 1) { "count 非法" } },
            )
            TYPE_FLIP_HOLD -> UnlockCondition.FlipOrHold(
                challengeId = requireNotNull(challengeId) { "缺 challengeId" },
                gesture = requireNotNull(gesture) { "缺 gesture" }.let { GestureKind.valueOf(it) },
                holdSeconds = requireNotNull(holdSeconds) { "缺 holdSeconds" },
            )
            TYPE_NFC -> UnlockCondition.NfcTap(
                challengeId = requireNotNull(challengeId) { "缺 challengeId" },
                expectedPayload = nfcPayload?.takeIf { it.isNotBlank() },
            )
            else -> {
                Log.w(TAG, "未知条件 type=$type，已丢弃（向前兼容）")
                null
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "条件 type=$type 字段不完整，已丢弃：${e.message}")
        null
    }
}
