package com.muxiao.timart.data.local.db.mapper

import android.util.Log
import com.muxiao.timart.domain.model.unlock.ConditionGroup
import com.muxiao.timart.domain.model.unlock.GestureKind
import com.muxiao.timart.domain.model.unlock.LogicType
import com.muxiao.timart.domain.model.unlock.LiftDirection
import com.muxiao.timart.domain.model.unlock.MeteorShowerKind
import com.muxiao.timart.domain.model.unlock.MoonPhaseKind
import com.muxiao.timart.domain.model.unlock.MotionKind
import com.muxiao.timart.domain.model.unlock.NetType
import com.muxiao.timart.domain.model.unlock.PlugKind
import com.muxiao.timart.domain.model.unlock.PoseKind
import com.muxiao.timart.domain.model.unlock.SeasonKind
import com.muxiao.timart.domain.model.unlock.SolarTermKind
import com.muxiao.timart.domain.model.unlock.SunPhaseKind
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.domain.model.unlock.UnlockRule
import com.muxiao.timart.domain.model.unlock.WeatherMetricKind
import com.muxiao.timart.domain.model.unlock.WindDirKind
import com.muxiao.timart.domain.model.unlock.ZodiacKind
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 解锁规则 DTO（扁平 ConditionDto + type 判别，格式见 ARCHITECTURE §5；threshold 供 AT_LEAST 逻辑用） */
@Serializable
data class UnlockRuleDto(
    val v: Int = 1,
    val logicType: String,
    val conditions: List<ConditionDto> = emptyList(),
    val threshold: Int? = null,
    /**
     * 子群组划分（v2，可选）：下标指向顶层 [conditions]（扁平列表双写保留——旧版本 App 读新包
     * 仍按扁平语义判定，缺组不致命）；非法划分在解码侧整体丢弃回退扁平。
     */
    val groups: List<ConditionGroupDto> = emptyList(),
)

/** 子群组 DTO（下标划分 + 组内逻辑；name 可空为纯展示层） */
@Serializable
data class ConditionGroupDto(
    val name: String? = null,
    val logicType: String,
    val threshold: Int? = null,
    val indexes: List<Int> = emptyList(),
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
    val maxTodaySteps: Int? = null,
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
    val meteorTypes: List<String>? = null,
    val maxLux: Int? = null,
    val zoneId: String? = null,
    val speedKmh: Int? = null,
    // ---- 储备池 v4 扩展字段（按 type 取用，全可空）----
    val solarTerms: List<String>? = null,
    val modulus: Int? = null,
    val seasons: List<String>? = null,
    val months: Int? = null,
    val nth: Int? = null,
    val dayList: List<Int>? = null,
    val zodiac: String? = null,
    val level: Int? = null,
    val plugTypes: List<String>? = null,
    val speedMaxKmh: Int? = null,
    val bssids: List<String>? = null,
    val deviceNames: List<String>? = null,
    val packageName: String? = null,
    val aqiMax: Int? = null,
    val windDirs: List<String>? = null,
    val cityName: String? = null,
    val baseAltM: Double? = null,
    val meters: Double? = null,
    val direction: String? = null,
    val minProb: Int? = null,
    val difficulty: Int? = null,
    val poses: List<String>? = null,
    // ---- 储备池 v7 扩展字段（按 type 取用，全可空）----
    val minLux: Int? = null,
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
    // ---- 扩展条件第二批（金色时刻/农历/流星雨/飞行模式/音乐/步数区间/已读联动/凝视次数）----
    private const val TYPE_GOLDEN_HOUR = "GOLDEN_HOUR"
    private const val TYPE_LUNAR_DATE = "LUNAR_DATE"
    private const val TYPE_METEOR_SHOWER = "METEOR_SHOWER"
    private const val TYPE_AIRPLANE = "AIRPLANE_MODE"
    private const val TYPE_MUSIC = "MUSIC_PLAYING"
    private const val TYPE_OTHER_READ = "OTHER_CAPSULE_READ"
    private const val TYPE_VIEW_COUNT = "VIEW_COUNT"
    // ---- 扩展条件第三批（黑暗中/时区/移动中 + 长按/生物识别/拍照留念）----
    private const val TYPE_AMBIENT_LIGHT = "AMBIENT_LIGHT"
    private const val TYPE_TIMEZONE_AWAY = "TIMEZONE_AWAY"
    private const val TYPE_MOVING_SPEED = "MOVING_SPEED"
    private const val TYPE_HOLD_PRESS = "HOLD_PRESS"
    private const val TYPE_BIOMETRIC = "BIOMETRIC_UNLOCK"
    private const val TYPE_PHOTO_KEEPSAKE = "PHOTO_KEEPSAKE"
    // ---- 储备池 v4 扩展（delta-prd-vs-code.md D-1.2，57 种）----
    private const val TYPE_SOLAR_TERM = "SOLAR_TERM"
    private const val TYPE_ROUND_DAYS = "ROUND_DAYS_ELAPSED"
    private const val TYPE_SEASON = "SEASON"
    private const val TYPE_ELAPSED_MONTHS = "MIN_ELAPSED_MONTHS"
    private const val TYPE_NTH_WEEKDAY_MONTH = "NTH_WEEKDAY_OF_MONTH"
    private const val TYPE_NTH_WEEKDAY_YEAR = "YEARLY_NTH_WEEKDAY"
    private const val TYPE_LEAP_DAY = "LEAP_DAY"
    private const val TYPE_LAST_DAY_MONTH = "LAST_DAY_OF_MONTH"
    private const val TYPE_NTH_WEEKDAY_SINCE = "NTH_WEEKDAY_SINCE"
    private const val TYPE_ZODIAC = "ZODIAC_SEASON"
    private const val TYPE_DAY_LENGTH = "DAY_LENGTH"
    private const val TYPE_SUNRISE_RANGE = "SUNRISE_TIME_RANGE"
    private const val TYPE_LUNAR_MONTH = "LUNAR_MONTH_RANGE"
    private const val TYPE_MONTHLY_DAYS = "MONTHLY_DAY_SET"
    private const val TYPE_DARK_THEME = "DARK_THEME"
    private const val TYPE_DND = "DO_NOT_DISTURB"
    private const val TYPE_DEVICE_POSE = "DEVICE_POSE"
    private const val TYPE_SCREEN_BRIGHTNESS = "SCREEN_BRIGHTNESS"
    private const val TYPE_MEDIA_VOLUME = "MEDIA_VOLUME"
    private const val TYPE_VPN = "VPN_ACTIVE"
    private const val TYPE_PLUG_TYPE = "PLUG_TYPE"
    private const val TYPE_BATTERY_TEMP = "BATTERY_TEMP"
    private const val TYPE_ORIENTATION = "ORIENTATION"
    private const val TYPE_SPEED_RANGE = "SPEED_RANGE"
    private const val TYPE_BSSID = "WIFI_BSSID"
    private const val TYPE_BLUETOOTH = "BLUETOOTH_DEVICE"
    private const val TYPE_PROXIMITY = "PROXIMITY_COVERED"
    private const val TYPE_FRESH_BOOT = "FRESH_BOOT"
    private const val TYPE_INSTALLED_APP = "INSTALLED_APP"
    private const val TYPE_AIR_QUALITY = "AIR_QUALITY"
    private const val TYPE_WIND_DIR = "WIND_DIRECTION"
    private const val TYPE_HEMISPHERE = "HEMISPHERE"
    private const val TYPE_TEMP_DELTA = "TEMP_DELTA"
    private const val TYPE_CITY_LOCATION = "CITY_LOCATION"
    private const val TYPE_REL_ALTITUDE = "RELATIVE_ALTITUDE"
    private const val TYPE_PRECIP_PROB = "PRECIPITATION_PROBABILITY"
    private const val TYPE_WATCH_DURATION = "WATCH_DURATION"
    private const val TYPE_READ_COUNT = "READ_COUNT"
    private const val TYPE_DESTROY_COUNT = "DESTROY_COUNT"
    private const val TYPE_STILL_LOCKED = "OTHER_CAPSULE_STILL_LOCKED"
    private const val TYPE_BACKUP_DONE = "BACKUP_DONE"
    private const val TYPE_TOTAL_CREATED = "TOTAL_CREATED_COUNT"
    private const val TYPE_SAME_DAY_READ = "SAME_DAY_AS_CAPSULE_READ"
    private const val TYPE_DAYS_SINCE_READ = "DAYS_SINCE_CAPSULE_READ"
    private const val TYPE_WIDGET_BOUND = "WIDGET_BOUND"
    private const val TYPE_TODAY_OPEN = "TODAY_OPEN_COUNT"
    private const val TYPE_GESTURE = "GESTURE_PATTERN"
    private const val TYPE_WALK_NOW = "WALK_STEPS_NOW"
    private const val TYPE_SPIN = "SPIN_PHONE"
    private const val TYPE_VOLUME_KEYS = "VOLUME_KEY_COMBO"
    private const val TYPE_STAY_STILL = "STAY_STILL"
    private const val TYPE_LIFT = "LIFT_HIGH_LOWER_LOW"
    private const val TYPE_VOICE = "VOICE_PASSWORD"
    private const val TYPE_TAP = "TAP_COUNT"
    private const val TYPE_CLIMB = "CLIMB_FLOORS"
    private const val TYPE_SCAN_QR = "SCAN_QR"
    private const val TYPE_POW = "PROOF_OF_WORK"
    // ---- 储备池 v5 扩展（delta-prd-vs-code.md D-1.5，2 种）----
    private const val TYPE_SNOWFALL = "SNOWFALL"
    private const val TYPE_CUM_STEPS = "CUMULATIVE_STEPS"
    private const val TYPE_RAIN_STREAK = "RAIN_STREAK"
    private const val TYPE_TEMP_VS_SEAL = "TEMP_VS_SEAL"
    // ---- 储备池 v7 扩展（delta-prd-vs-code.md D-1.7，7 种）----
    private const val TYPE_LUNAR_DAY_SET = "LUNAR_DAY_SET"
    private const val TYPE_THUNDER = "THUNDER"
    private const val TYPE_BRIGHT_LIGHT = "BRIGHT_LIGHT"
    private const val TYPE_APP_USAGE = "APP_USAGE"
    private const val TYPE_PRESSURE_DELTA = "PRESSURE_DELTA"
    private const val TYPE_VOICE_KEEPSAKE = "VOICE_KEEPSAKE"
    private const val TYPE_SHOUT = "SHOUT"

    /** 领域规则 → §5.1 JSON（扁平 conditions 恒双写；groups 非空才写入并升 v=2，旧版读取按扁平语义） */
    fun toJson(rule: UnlockRule): String {
        val dto = UnlockRuleDto(
            v = if (rule.groups.isEmpty()) 1 else 2,
            logicType = rule.logicType.name,
            conditions = rule.conditionList.mapNotNull { toDto(it) },
            threshold = rule.threshold?.takeIf { rule.logicType == LogicType.AT_LEAST },
            groups = rule.groups.map { group ->
                ConditionGroupDto(
                    name = group.name,
                    logicType = group.logicType.name,
                    threshold = group.threshold?.takeIf { group.logicType == LogicType.AT_LEAST },
                    indexes = group.indexes,
                )
            },
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
        // AT_LEAST 阈值归一：缺省 = 全部（等价 AND）；越界 coerce 到 [1, N]，旧备份无此字段自然回退
        val threshold = dto.threshold
            ?.takeIf { logic == LogicType.AT_LEAST && conditions.isNotEmpty() }
            ?.coerceIn(1, conditions.size)
        return UnlockRule(logic, conditions, threshold, parseGroups(dto.groups, conditions.size))
    }

    /**
     * 子群组解析：逻辑非法 / 下标越界 / 组间重叠任一出现 → 整体丢弃回退扁平（fail-safe）；
     * 组内 AT_LEAST 阈值 coerce 到 [1, 组员数]。
     */
    private fun parseGroups(dtos: List<ConditionGroupDto>, conditionCount: Int): List<ConditionGroup> {
        if (dtos.isEmpty() || conditionCount == 0) return emptyList()
        val parsed = ArrayList<ConditionGroup>(dtos.size)
        for (dto in dtos) {
            val logic = runCatching { LogicType.valueOf(dto.logicType) }.getOrNull() ?: return emptyList()
            val indexes = dto.indexes.distinct()
            if (indexes.isEmpty() || indexes.any { it !in 0 until conditionCount }) return emptyList()
            val threshold = dto.threshold
                ?.takeIf { logic == LogicType.AT_LEAST }
                ?.coerceIn(1, indexes.size)
            parsed += ConditionGroup(name = dto.name?.trim()?.takeIf { it.isNotEmpty() }, logicType = logic, threshold = threshold, indexes = indexes)
        }
        val claimed = parsed.flatMap { it.indexes }
        if (claimed.size != claimed.toSet().size) return emptyList()
        return parsed
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
            ConditionDto(
                type = TYPE_STEPS,
                minTodaySteps = condition.minTodayStep,
                maxTodaySteps = condition.maxTodayStep,
            )
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
        is UnlockCondition.MeteorShower ->
            ConditionDto(type = TYPE_METEOR_SHOWER, meteorTypes = condition.showers.map { it.name })
        is UnlockCondition.GoldenHour ->
            ConditionDto(type = TYPE_GOLDEN_HOUR)
        is UnlockCondition.LunarDate ->
            ConditionDto(type = TYPE_LUNAR_DATE, month = condition.month, dayOfMonth = condition.day)
        is UnlockCondition.AirplaneMode ->
            ConditionDto(type = TYPE_AIRPLANE, flag = condition.isEnabled)
        is UnlockCondition.MusicPlaying ->
            ConditionDto(type = TYPE_MUSIC, flag = condition.isPlaying)
        is UnlockCondition.AmbientLight ->
            ConditionDto(type = TYPE_AMBIENT_LIGHT, maxLux = condition.maxLux)
        is UnlockCondition.TimezoneChange ->
            ConditionDto(type = TYPE_TIMEZONE_AWAY, zoneId = condition.homeZoneId)
        is UnlockCondition.MovingAboveSpeed ->
            ConditionDto(type = TYPE_MOVING_SPEED, speedKmh = condition.minSpeedKmh)
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
        is UnlockCondition.OtherCapsuleRead ->
            ConditionDto(type = TYPE_OTHER_READ, capsuleId = condition.capsuleId)
        is UnlockCondition.ViewCountAtLeast ->
            ConditionDto(type = TYPE_VIEW_COUNT, count = condition.count)
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
        is UnlockCondition.HoldPress ->
            ConditionDto(type = TYPE_HOLD_PRESS, challengeId = condition.challengeId, holdSeconds = condition.holdSeconds)
        is UnlockCondition.BiometricUnlock ->
            ConditionDto(type = TYPE_BIOMETRIC, challengeId = condition.challengeId)
        is UnlockCondition.PhotoKeepsake ->
            ConditionDto(type = TYPE_PHOTO_KEEPSAKE, challengeId = condition.challengeId)

        // ---- 储备池 v4 ----
        is UnlockCondition.SolarTerm ->
            ConditionDto(type = TYPE_SOLAR_TERM, solarTerms = condition.solarTerms.map { it.name })
        is UnlockCondition.RoundDaysElapsed ->
            ConditionDto(type = TYPE_ROUND_DAYS, modulus = condition.modulus)
        is UnlockCondition.Season ->
            ConditionDto(type = TYPE_SEASON, seasons = condition.seasons.map { it.name })
        is UnlockCondition.MinElapsedMonths ->
            ConditionDto(type = TYPE_ELAPSED_MONTHS, months = condition.months)
        is UnlockCondition.NthWeekdayOfMonth ->
            ConditionDto(type = TYPE_NTH_WEEKDAY_MONTH, nth = condition.nth, weekDays = listOf(condition.dayOfWeek.name))
        is UnlockCondition.YearlyNthWeekday ->
            ConditionDto(
                type = TYPE_NTH_WEEKDAY_YEAR,
                month = condition.month,
                nth = condition.nth,
                weekDays = listOf(condition.dayOfWeek.name),
            )
        is UnlockCondition.LeapDay -> ConditionDto(type = TYPE_LEAP_DAY)
        is UnlockCondition.LastDayOfMonth -> ConditionDto(type = TYPE_LAST_DAY_MONTH)
        is UnlockCondition.NthWeekdaySince ->
            ConditionDto(type = TYPE_NTH_WEEKDAY_SINCE, days = condition.minDays, weekDays = listOf(condition.dayOfWeek.name))
        is UnlockCondition.ZodiacSeason ->
            ConditionDto(type = TYPE_ZODIAC, zodiac = condition.zodiac.name)
        is UnlockCondition.DayLength ->
            ConditionDto(type = TYPE_DAY_LENGTH, minVal = condition.minHours, maxVal = condition.maxHours)
        is UnlockCondition.SunriseTimeRange ->
            ConditionDto(type = TYPE_SUNRISE_RANGE, min = condition.minMinute, max = condition.maxMinute)
        is UnlockCondition.LunarMonthRange ->
            ConditionDto(type = TYPE_LUNAR_MONTH, month = condition.month)
        is UnlockCondition.MonthlyDaySet ->
            ConditionDto(type = TYPE_MONTHLY_DAYS, dayList = condition.days.sorted())
        is UnlockCondition.DarkTheme ->
            ConditionDto(type = TYPE_DARK_THEME, flag = condition.isDark)
        is UnlockCondition.DoNotDisturb ->
            ConditionDto(type = TYPE_DND, flag = condition.isActive)
        is UnlockCondition.DevicePose ->
            ConditionDto(type = TYPE_DEVICE_POSE, poses = condition.kinds.map { it.name })
        is UnlockCondition.ScreenBrightness ->
            ConditionDto(type = TYPE_SCREEN_BRIGHTNESS, level = condition.maxLevel)
        is UnlockCondition.MediaVolume ->
            ConditionDto(type = TYPE_MEDIA_VOLUME, flag = condition.isMuted)
        is UnlockCondition.VpnActive ->
            ConditionDto(type = TYPE_VPN, flag = condition.isActive)
        is UnlockCondition.PlugType ->
            ConditionDto(type = TYPE_PLUG_TYPE, plugTypes = condition.kinds.map { it.name })
        is UnlockCondition.BatteryTemp ->
            ConditionDto(type = TYPE_BATTERY_TEMP, minVal = condition.minC, maxVal = condition.maxC)
        is UnlockCondition.Orientation ->
            ConditionDto(type = TYPE_ORIENTATION, flag = condition.isLandscape)
        is UnlockCondition.SpeedRange ->
            ConditionDto(type = TYPE_SPEED_RANGE, speedKmh = condition.minKmh, speedMaxKmh = condition.maxKmh)
        is UnlockCondition.SsidBssidMatch ->
            ConditionDto(type = TYPE_BSSID, bssids = condition.bssids.toList())
        is UnlockCondition.BluetoothDevice ->
            ConditionDto(type = TYPE_BLUETOOTH, deviceNames = condition.deviceNames.toList())
        is UnlockCondition.ProximityCovered -> ConditionDto(type = TYPE_PROXIMITY)
        is UnlockCondition.FreshBoot ->
            ConditionDto(type = TYPE_FRESH_BOOT, minutes = condition.withinMinutes.toLong())
        is UnlockCondition.InstalledApp ->
            ConditionDto(type = TYPE_INSTALLED_APP, packageName = condition.packageName)
        is UnlockCondition.AirQuality ->
            ConditionDto(type = TYPE_AIR_QUALITY, aqiMax = condition.maxAqi)
        is UnlockCondition.WindDirection ->
            ConditionDto(type = TYPE_WIND_DIR, windDirs = condition.dirs.map { it.name })
        is UnlockCondition.Hemisphere ->
            ConditionDto(type = TYPE_HEMISPHERE, flag = condition.north)
        is UnlockCondition.TempDelta ->
            ConditionDto(type = TYPE_TEMP_DELTA, minVal = condition.minDropC)
        is UnlockCondition.CityLocation ->
            ConditionDto(
                type = TYPE_CITY_LOCATION,
                cityName = condition.cityName,
                lat = condition.lat,
                lng = condition.lng,
                radiusMeter = condition.radiusMeter,
            )
        is UnlockCondition.RelativeAltitude ->
            ConditionDto(
                type = TYPE_REL_ALTITUDE,
                baseAltM = condition.baseAltM,
                meters = condition.deltaM,
                direction = condition.direction.name,
            )
        is UnlockCondition.PrecipitationProbability ->
            ConditionDto(type = TYPE_PRECIP_PROB, minProb = condition.minProb)
        is UnlockCondition.WatchDurationAtLeast ->
            ConditionDto(type = TYPE_WATCH_DURATION, count = condition.seconds)
        is UnlockCondition.ReadCountAtLeast ->
            ConditionDto(type = TYPE_READ_COUNT, count = condition.count)
        is UnlockCondition.DestroyCountAtLeast ->
            ConditionDto(type = TYPE_DESTROY_COUNT, count = condition.count)
        is UnlockCondition.OtherCapsuleStillLocked ->
            ConditionDto(type = TYPE_STILL_LOCKED, capsuleId = condition.capsuleId)
        is UnlockCondition.BackupDone -> ConditionDto(type = TYPE_BACKUP_DONE)
        is UnlockCondition.TotalCreatedCount ->
            ConditionDto(type = TYPE_TOTAL_CREATED, count = condition.count)
        is UnlockCondition.SameDayAsCapsuleRead ->
            ConditionDto(type = TYPE_SAME_DAY_READ, capsuleId = condition.capsuleId)
        is UnlockCondition.DaysSinceCapsuleRead ->
            ConditionDto(type = TYPE_DAYS_SINCE_READ, days = condition.days, capsuleId = condition.capsuleId)
        is UnlockCondition.WidgetBound -> ConditionDto(type = TYPE_WIDGET_BOUND)
        is UnlockCondition.TodayOpenCount ->
            ConditionDto(type = TYPE_TODAY_OPEN, count = condition.count)
        is UnlockCondition.GesturePattern ->
            ConditionDto(type = TYPE_GESTURE, challengeId = condition.challengeId, answerHash = condition.answerHash)
        is UnlockCondition.WalkStepsNow ->
            ConditionDto(type = TYPE_WALK_NOW, challengeId = condition.challengeId, count = condition.steps)
        is UnlockCondition.SpinPhone ->
            ConditionDto(type = TYPE_SPIN, challengeId = condition.challengeId, deg = condition.degrees)
        is UnlockCondition.VolumeKeyCombo ->
            ConditionDto(type = TYPE_VOLUME_KEYS, challengeId = condition.challengeId, holdSeconds = condition.holdSeconds)
        is UnlockCondition.StayStill ->
            ConditionDto(type = TYPE_STAY_STILL, challengeId = condition.challengeId, holdSeconds = condition.holdSeconds)
        is UnlockCondition.LiftHighLowerLow ->
            ConditionDto(
                type = TYPE_LIFT,
                challengeId = condition.challengeId,
                direction = condition.direction.name,
                meters = condition.meters,
            )
        is UnlockCondition.VoicePassword ->
            ConditionDto(type = TYPE_VOICE, challengeId = condition.challengeId, answer = condition.expectedAnswer)
        is UnlockCondition.TapCount ->
            ConditionDto(type = TYPE_TAP, challengeId = condition.challengeId, count = condition.taps)
        is UnlockCondition.ClimbFloors ->
            ConditionDto(type = TYPE_CLIMB, challengeId = condition.challengeId, count = condition.floors)
        is UnlockCondition.ScanQr ->
            ConditionDto(type = TYPE_SCAN_QR, challengeId = condition.challengeId, answer = condition.expectedPayload)
        is UnlockCondition.ProofOfWork ->
            ConditionDto(type = TYPE_POW, challengeId = condition.challengeId, difficulty = condition.difficulty)

        // ---- 储备池 v5 ----
        is UnlockCondition.SnowObservation ->
            ConditionDto(type = TYPE_SNOWFALL, flag = condition.firstOfSeason)
        is UnlockCondition.CumulativeSteps ->
            ConditionDto(type = TYPE_CUM_STEPS, min = condition.minSteps)

        // ---- 储备池 v6 ----
        is UnlockCondition.RainStreak ->
            ConditionDto(
                type = TYPE_RAIN_STREAK,
                days = condition.days,
                direction = if (condition.afterRain) "AFTER_RAIN" else "RAINING",
            )
        is UnlockCondition.TempVsSealDay ->
            ConditionDto(
                type = TYPE_TEMP_VS_SEAL,
                minVal = condition.deltaC,
                direction = if (condition.hotter) "HOTTER" else "COLDER",
            )

        // ---- 储备池 v7 ----
        is UnlockCondition.LunarDayOfMonth ->
            ConditionDto(type = TYPE_LUNAR_DAY_SET, dayList = condition.days.sorted())
        is UnlockCondition.ThunderObservation ->
            ConditionDto(type = TYPE_THUNDER, flag = condition.firstOfSeason)
        is UnlockCondition.BrightLight ->
            ConditionDto(type = TYPE_BRIGHT_LIGHT, minLux = condition.minLux)
        is UnlockCondition.AppUsageCeiling ->
            ConditionDto(
                type = TYPE_APP_USAGE,
                packageName = condition.packageName,
                minutes = condition.maxMinutes.toLong(),
            )
        is UnlockCondition.PressureDelta ->
            ConditionDto(type = TYPE_PRESSURE_DELTA, minVal = condition.minDropHpa)
        is UnlockCondition.VoiceKeepsake ->
            ConditionDto(type = TYPE_VOICE_KEEPSAKE, challengeId = condition.challengeId)
        is UnlockCondition.ShoutOut ->
            ConditionDto(
                type = TYPE_SHOUT,
                challengeId = condition.challengeId,
                holdSeconds = condition.seconds,
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
            TYPE_STEPS -> run {
                // 区间条件：任一端可空，但至少一端有值
                val minSteps = minTodaySteps
                val maxSteps = maxTodaySteps
                require(minSteps != null || maxSteps != null) { "minTodaySteps/maxTodaySteps 全空" }
                UnlockCondition.StepCount(minTodayStep = minSteps, maxTodayStep = maxSteps)
            }
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
            TYPE_METEOR_SHOWER -> UnlockCondition.MeteorShower(
                showers = requireNotNull(meteorTypes) { "缺 meteorTypes" }
                    .mapNotNull { runCatching { MeteorShowerKind.valueOf(it) }.getOrNull() }
                    .toSet()
                    .also { require(it.isNotEmpty()) { "meteorTypes 全空" } },
            )
            TYPE_GOLDEN_HOUR -> UnlockCondition.GoldenHour
            TYPE_LUNAR_DATE -> UnlockCondition.LunarDate(
                month = requireNotNull(month) { "缺 month" }.also { require(it in 1..12) { "month 非法" } },
                day = requireNotNull(dayOfMonth) { "缺 day" }.also { require(it in 1..30) { "day 非法" } },
            )
            TYPE_AIRPLANE -> UnlockCondition.AirplaneMode(
                isEnabled = requireNotNull(flag) { "缺 flag" },
            )
            TYPE_MUSIC -> UnlockCondition.MusicPlaying(
                isPlaying = requireNotNull(flag) { "缺 flag" },
            )
            TYPE_AMBIENT_LIGHT -> UnlockCondition.AmbientLight(
                maxLux = requireNotNull(maxLux) { "缺 maxLux" }.also { require(it in 1..50000) { "maxLux 非法" } },
            )
            TYPE_TIMEZONE_AWAY -> UnlockCondition.TimezoneChange(
                homeZoneId = requireNotNull(zoneId) { "缺 zoneId" }.also { require(it.isNotBlank()) { "zoneId 空" } },
            )
            TYPE_MOVING_SPEED -> UnlockCondition.MovingAboveSpeed(
                minSpeedKmh = requireNotNull(speedKmh) { "缺 speedKmh" }.also { require(it in 1..300) { "speedKmh 非法" } },
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
            TYPE_OTHER_READ -> UnlockCondition.OtherCapsuleRead(
                capsuleId = requireNotNull(capsuleId) { "缺 capsuleId" },
            )
            TYPE_VIEW_COUNT -> UnlockCondition.ViewCountAtLeast(
                count = requireNotNull(count) { "缺 count" }.also { require(it >= 1) { "count 非法" } },
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
            TYPE_HOLD_PRESS -> UnlockCondition.HoldPress(
                challengeId = requireNotNull(challengeId) { "缺 challengeId" },
                holdSeconds = requireNotNull(holdSeconds) { "缺 holdSeconds" }
                    .also { require(it in 1..60) { "holdSeconds 非法" } },
            )
            TYPE_BIOMETRIC -> UnlockCondition.BiometricUnlock(
                challengeId = requireNotNull(challengeId) { "缺 challengeId" },
            )
            TYPE_PHOTO_KEEPSAKE -> UnlockCondition.PhotoKeepsake(
                challengeId = requireNotNull(challengeId) { "缺 challengeId" },
            )

            // ---- 储备池 v4 ----
            TYPE_SOLAR_TERM -> UnlockCondition.SolarTerm(
                solarTerms = requireNotNull(solarTerms) { "缺 solarTerms" }
                    .mapNotNull { runCatching { SolarTermKind.valueOf(it) }.getOrNull() }
                    .toSet()
                    .also { require(it.isNotEmpty()) { "solarTerms 全空" } },
            )
            TYPE_ROUND_DAYS -> UnlockCondition.RoundDaysElapsed(
                modulus = requireNotNull(modulus) { "缺 modulus" }.also { require(it >= 1) { "modulus 非法" } },
            )
            TYPE_SEASON -> UnlockCondition.Season(
                seasons = requireNotNull(seasons) { "缺 seasons" }
                    .mapNotNull { runCatching { SeasonKind.valueOf(it) }.getOrNull() }
                    .toSet()
                    .also { require(it.isNotEmpty()) { "seasons 全空" } },
            )
            TYPE_ELAPSED_MONTHS -> UnlockCondition.MinElapsedMonths(
                months = requireNotNull(months) { "缺 months" }.also { require(it >= 1) { "months 非法" } },
            )
            TYPE_NTH_WEEKDAY_MONTH -> UnlockCondition.NthWeekdayOfMonth(
                nth = requireNotNull(nth) { "缺 nth" }.also { require(it in 1..5) { "nth 非法" } },
                dayOfWeek = requireNotNull(weekDays?.firstOrNull()) { "缺 weekDays" }.let { DayOfWeek.valueOf(it) },
            )
            TYPE_NTH_WEEKDAY_YEAR -> UnlockCondition.YearlyNthWeekday(
                month = requireNotNull(month) { "缺 month" }.also { require(it in 1..12) { "month 非法" } },
                nth = requireNotNull(nth) { "缺 nth" }.also { require(it in 1..5) { "nth 非法" } },
                dayOfWeek = requireNotNull(weekDays?.firstOrNull()) { "缺 weekDays" }.let { DayOfWeek.valueOf(it) },
            )
            TYPE_LEAP_DAY -> UnlockCondition.LeapDay
            TYPE_LAST_DAY_MONTH -> UnlockCondition.LastDayOfMonth
            TYPE_NTH_WEEKDAY_SINCE -> UnlockCondition.NthWeekdaySince(
                minDays = requireNotNull(days) { "缺 days" }.also { require(it >= 1) { "days 非法" } },
                dayOfWeek = requireNotNull(weekDays?.firstOrNull()) { "缺 weekDays" }.let { DayOfWeek.valueOf(it) },
            )
            TYPE_ZODIAC -> UnlockCondition.ZodiacSeason(
                zodiac = requireNotNull(zodiac) { "缺 zodiac" }.let { ZodiacKind.valueOf(it) },
            )
            TYPE_DAY_LENGTH -> run {
                require(minVal != null || maxVal != null) { "minVal/maxVal 全空" }
                UnlockCondition.DayLength(minHours = minVal, maxHours = maxVal)
            }
            TYPE_SUNRISE_RANGE -> run {
                require(min != null || max != null) { "min/max 全空" }
                UnlockCondition.SunriseTimeRange(minMinute = min, maxMinute = max)
            }
            TYPE_LUNAR_MONTH -> UnlockCondition.LunarMonthRange(
                month = requireNotNull(month) { "缺 month" }.also { require(it in 1..12) { "month 非法" } },
            )
            TYPE_MONTHLY_DAYS -> UnlockCondition.MonthlyDaySet(
                days = requireNotNull(dayList) { "缺 dayList" }
                    .filter { it in 1..31 }
                    .toSet()
                    .also { require(it.isNotEmpty()) { "dayList 全空" } },
            )
            TYPE_DARK_THEME -> UnlockCondition.DarkTheme(
                isDark = requireNotNull(flag) { "缺 flag" },
            )
            TYPE_DND -> UnlockCondition.DoNotDisturb(
                isActive = requireNotNull(flag) { "缺 flag" },
            )
            TYPE_DEVICE_POSE -> UnlockCondition.DevicePose(
                kinds = requireNotNull(poses) { "缺 poses" }
                    .mapNotNull { runCatching { PoseKind.valueOf(it) }.getOrNull() }
                    .toSet()
                    .also { require(it.isNotEmpty()) { "poses 全空" } },
            )
            TYPE_SCREEN_BRIGHTNESS -> UnlockCondition.ScreenBrightness(
                maxLevel = requireNotNull(level) { "缺 level" }.also { require(it in 1..255) { "level 非法" } },
            )
            TYPE_MEDIA_VOLUME -> UnlockCondition.MediaVolume(
                isMuted = requireNotNull(flag) { "缺 flag" },
            )
            TYPE_VPN -> UnlockCondition.VpnActive(
                isActive = requireNotNull(flag) { "缺 flag" },
            )
            TYPE_PLUG_TYPE -> UnlockCondition.PlugType(
                kinds = requireNotNull(plugTypes) { "缺 plugTypes" }
                    .mapNotNull { runCatching { PlugKind.valueOf(it) }.getOrNull() }
                    .toSet()
                    .also { require(it.isNotEmpty()) { "plugTypes 全空" } },
            )
            TYPE_BATTERY_TEMP -> UnlockCondition.BatteryTemp(minC = minVal, maxC = maxVal)
            TYPE_ORIENTATION -> UnlockCondition.Orientation(
                isLandscape = requireNotNull(flag) { "缺 flag" },
            )
            TYPE_SPEED_RANGE -> UnlockCondition.SpeedRange(minKmh = speedKmh, maxKmh = speedMaxKmh)
            TYPE_BSSID -> UnlockCondition.SsidBssidMatch(
                bssids = requireNotNull(bssids) { "缺 bssids" }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
                    .also { require(it.isNotEmpty()) { "bssids 全空" } },
            )
            TYPE_BLUETOOTH -> UnlockCondition.BluetoothDevice(
                deviceNames = requireNotNull(deviceNames) { "缺 deviceNames" }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
                    .also { require(it.isNotEmpty()) { "deviceNames 全空" } },
            )
            TYPE_PROXIMITY -> UnlockCondition.ProximityCovered
            TYPE_FRESH_BOOT -> UnlockCondition.FreshBoot(
                withinMinutes = requireNotNull(minutes?.toInt()) { "缺 minutes" }
                    .also { require(it in 1..1440) { "minutes 非法" } },
            )
            TYPE_INSTALLED_APP -> UnlockCondition.InstalledApp(
                packageName = requireNotNull(packageName) { "缺 packageName" }
                    .also { require(it.isNotBlank()) { "packageName 空" } },
            )
            TYPE_AIR_QUALITY -> UnlockCondition.AirQuality(
                maxAqi = requireNotNull(aqiMax) { "缺 aqiMax" }.also { require(it in 1..500) { "aqiMax 非法" } },
            )
            TYPE_WIND_DIR -> UnlockCondition.WindDirection(
                dirs = requireNotNull(windDirs) { "缺 windDirs" }
                    .mapNotNull { runCatching { WindDirKind.valueOf(it) }.getOrNull() }
                    .toSet()
                    .also { require(it.isNotEmpty()) { "windDirs 全空" } },
            )
            TYPE_HEMISPHERE -> UnlockCondition.Hemisphere(
                north = requireNotNull(flag) { "缺 flag" },
            )
            TYPE_TEMP_DELTA -> UnlockCondition.TempDelta(
                minDropC = requireNotNull(minVal) { "缺 minVal" }.also { require(it > 0.0) { "minVal 非法" } },
            )
            TYPE_CITY_LOCATION -> UnlockCondition.CityLocation(
                cityName = requireNotNull(cityName) { "缺 cityName" },
                lat = requireNotNull(lat) { "缺 lat" },
                lng = requireNotNull(lng) { "缺 lng" },
                radiusMeter = requireNotNull(radiusMeter) { "缺 radiusMeter" },
            )
            TYPE_REL_ALTITUDE -> UnlockCondition.RelativeAltitude(
                baseAltM = requireNotNull(baseAltM) { "缺 baseAltM" },
                deltaM = requireNotNull(meters) { "缺 meters" }.also { require(it > 0.0) { "meters 非法" } },
                direction = requireNotNull(direction) { "缺 direction" }.let { LiftDirection.valueOf(it) },
            )
            TYPE_PRECIP_PROB -> UnlockCondition.PrecipitationProbability(
                minProb = requireNotNull(minProb) { "缺 minProb" }.also { require(it in 1..100) { "minProb 非法" } },
            )
            TYPE_WATCH_DURATION -> UnlockCondition.WatchDurationAtLeast(
                seconds = requireNotNull(count) { "缺 count" }.also { require(it >= 60) { "count 非法" } },
            )
            TYPE_READ_COUNT -> UnlockCondition.ReadCountAtLeast(
                count = requireNotNull(count) { "缺 count" }.also { require(it >= 1) { "count 非法" } },
            )
            TYPE_DESTROY_COUNT -> UnlockCondition.DestroyCountAtLeast(
                count = requireNotNull(count) { "缺 count" }.also { require(it >= 1) { "count 非法" } },
            )
            TYPE_STILL_LOCKED -> UnlockCondition.OtherCapsuleStillLocked(
                capsuleId = requireNotNull(capsuleId) { "缺 capsuleId" },
            )
            TYPE_BACKUP_DONE -> UnlockCondition.BackupDone
            TYPE_TOTAL_CREATED -> UnlockCondition.TotalCreatedCount(
                count = requireNotNull(count) { "缺 count" }.also { require(it >= 1) { "count 非法" } },
            )
            TYPE_SAME_DAY_READ -> UnlockCondition.SameDayAsCapsuleRead(
                capsuleId = requireNotNull(capsuleId) { "缺 capsuleId" },
            )
            TYPE_DAYS_SINCE_READ -> UnlockCondition.DaysSinceCapsuleRead(
                days = requireNotNull(days) { "缺 days" }.also { require(it >= 1) { "days 非法" } },
                capsuleId = requireNotNull(capsuleId) { "缺 capsuleId" },
            )
            TYPE_WIDGET_BOUND -> UnlockCondition.WidgetBound
            TYPE_TODAY_OPEN -> UnlockCondition.TodayOpenCount(
                count = requireNotNull(count) { "缺 count" }.also { require(it >= 1) { "count 非法" } },
            )
            TYPE_GESTURE -> UnlockCondition.GesturePattern(
                challengeId = requireNotNull(challengeId) { "缺 challengeId" },
                answerHash = requireNotNull(answerHash) { "缺 answerHash" },
            )
            TYPE_WALK_NOW -> UnlockCondition.WalkStepsNow(
                challengeId = requireNotNull(challengeId) { "缺 challengeId" },
                steps = requireNotNull(count) { "缺 count" }.also { require(it in 1..5000) { "count 非法" } },
            )
            TYPE_SPIN -> UnlockCondition.SpinPhone(
                challengeId = requireNotNull(challengeId) { "缺 challengeId" },
                degrees = requireNotNull(deg) { "缺 deg" }.also { require(it in 90..1440) { "deg 非法" } },
            )
            TYPE_VOLUME_KEYS -> UnlockCondition.VolumeKeyCombo(
                challengeId = requireNotNull(challengeId) { "缺 challengeId" },
                holdSeconds = requireNotNull(holdSeconds) { "缺 holdSeconds" }
                    .also { require(it in 1..60) { "holdSeconds 非法" } },
            )
            TYPE_STAY_STILL -> UnlockCondition.StayStill(
                challengeId = requireNotNull(challengeId) { "缺 challengeId" },
                holdSeconds = requireNotNull(holdSeconds) { "缺 holdSeconds" }
                    .also { require(it in 1..120) { "holdSeconds 非法" } },
            )
            TYPE_LIFT -> UnlockCondition.LiftHighLowerLow(
                challengeId = requireNotNull(challengeId) { "缺 challengeId" },
                direction = requireNotNull(direction) { "缺 direction" }.let { LiftDirection.valueOf(it) },
                meters = requireNotNull(meters) { "缺 meters" }.also { require(it in 1.0..200.0) { "meters 非法" } },
            )
            TYPE_VOICE -> UnlockCondition.VoicePassword(
                challengeId = requireNotNull(challengeId) { "缺 challengeId" },
                expectedAnswer = requireNotNull(answer) { "缺 answer" },
            )
            TYPE_TAP -> UnlockCondition.TapCount(
                challengeId = requireNotNull(challengeId) { "缺 challengeId" },
                taps = requireNotNull(count) { "缺 count" }.also { require(it in 1..200) { "count 非法" } },
            )
            TYPE_CLIMB -> UnlockCondition.ClimbFloors(
                challengeId = requireNotNull(challengeId) { "缺 challengeId" },
                floors = requireNotNull(count) { "缺 count" }.also { require(it in 1..100) { "count 非法" } },
            )
            TYPE_SCAN_QR -> UnlockCondition.ScanQr(
                challengeId = requireNotNull(challengeId) { "缺 challengeId" },
                expectedPayload = answer?.takeIf { it.isNotBlank() },
            )
            TYPE_POW -> UnlockCondition.ProofOfWork(
                challengeId = requireNotNull(challengeId) { "缺 challengeId" },
                difficulty = requireNotNull(difficulty) { "缺 difficulty" }
                    .also { require(it in 2..6) { "difficulty 非法" } },
            )

            // ---- 储备池 v5 ----
            TYPE_SNOWFALL -> UnlockCondition.SnowObservation(
                firstOfSeason = requireNotNull(flag) { "缺 flag" },
            )
            TYPE_CUM_STEPS -> UnlockCondition.CumulativeSteps(
                minSteps = requireNotNull(min) { "缺 min" }.also { require(it >= 1) { "min 非法" } },
            )

            // ---- 储备池 v6 ----
            TYPE_RAIN_STREAK -> UnlockCondition.RainStreak(
                days = requireNotNull(days) { "缺 days" }.also { require(it in 1..90) { "days 非法" } },
                afterRain = direction == "AFTER_RAIN",
            )
            TYPE_TEMP_VS_SEAL -> UnlockCondition.TempVsSealDay(
                deltaC = requireNotNull(minVal) { "缺 minVal" }.also { require(it in 0.5..50.0) { "minVal 非法" } },
                hotter = direction == "HOTTER",
            )

            // ---- 储备池 v7 ----
            TYPE_LUNAR_DAY_SET -> UnlockCondition.LunarDayOfMonth(
                days = requireNotNull(dayList) { "缺 dayList" }
                    .also { list -> require(list.isNotEmpty() && list.all { it in 1..30 }) { "dayList 非法" } }
                    .toSet(),
            )
            TYPE_THUNDER -> UnlockCondition.ThunderObservation(firstOfSeason = flag == true)
            TYPE_BRIGHT_LIGHT -> UnlockCondition.BrightLight(
                minLux = requireNotNull(minLux) { "缺 minLux" }.also { require(it in 1..100_000) { "minLux 非法" } },
            )
            TYPE_APP_USAGE -> UnlockCondition.AppUsageCeiling(
                packageName = requireNotNull(packageName) { "缺 packageName" }.trim(),
                maxMinutes = requireNotNull(minutes) { "缺 minutes" }
                    .also { require(it in 0..1440) { "minutes 非法" } }
                    .toInt(),
            )
            TYPE_PRESSURE_DELTA -> UnlockCondition.PressureDelta(
                minDropHpa = requireNotNull(minVal) { "缺 minVal" }
                    .also { require(it in 0.5..30.0) { "minVal 非法" } },
            )
            TYPE_VOICE_KEEPSAKE -> UnlockCondition.VoiceKeepsake(
                challengeId = requireNotNull(challengeId) { "缺 challengeId" },
            )
            TYPE_SHOUT -> UnlockCondition.ShoutOut(
                challengeId = requireNotNull(challengeId) { "缺 challengeId" },
                seconds = requireNotNull(holdSeconds) { "缺 holdSeconds" }
                    .also { require(it in 1..60) { "holdSeconds 非法" } },
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
