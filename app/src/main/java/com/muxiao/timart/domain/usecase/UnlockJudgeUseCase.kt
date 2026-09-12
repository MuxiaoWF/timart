package com.muxiao.timart.domain.usecase

import com.muxiao.timart.domain.context.ConditionContext
import com.muxiao.timart.domain.context.DependencyStatus
import com.muxiao.timart.domain.context.WifiSsidState
import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.domain.model.ConditionStatus
import com.muxiao.timart.domain.model.JudgeResult
import com.muxiao.timart.domain.model.Lang
import com.muxiao.timart.domain.model.unlock.JudgeReasons
import com.muxiao.timart.domain.model.unlock.LogicType
import com.muxiao.timart.domain.model.unlock.LunarCalendar
import com.muxiao.timart.domain.model.unlock.MeteorCalendar
import com.muxiao.timart.domain.model.unlock.MoonCalc
import com.muxiao.timart.domain.model.unlock.SunCalc
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.domain.model.unlock.UnlockRule
import com.muxiao.timart.domain.model.unlock.WeatherMetricKind
import com.muxiao.timart.domain.repository.CapsuleRepository
import com.muxiao.timart.utils.location.GeoMath
import java.time.temporal.ChronoUnit

/**
 * 解锁判定引擎（规格严格按 ARCHITECTURE §7，前台页面与 Worker 共用同一套逻辑）。
 *
 * - [judge] 同步纯逻辑，可单测；
 * - [judgeAllLocked] 由 VM / Worker 协程调用，负责状态持久化与解锁回调（通知 + UI 反馈）。
 *
 * 判定顺序：非 LOCKED 短路 → 依赖检查 → 逐条件求值 → AND/OR 合并 → overallOk。
 * skipped 条件（后台 GPS）一律按不满足参与合并。
 */
class UnlockJudgeUseCase {

    /**
     * 单颗胶囊判定（不产生副作用；[lang] 决定判定原因文案语言，默认简中）。
     * [challengeAnswers] = 即时挑战应答（challengeId → 应答串），仅详情页当场判定时携带；
     * 周期巡检与 ON_RESUME 全量判定不携带 → 挑战条件按不满足 fail-closed，绝不自动解锁。
     */
    fun judge(
        capsule: Capsule,
        ctx: ConditionContext,
        lang: Lang = Lang.ZH_HANS,
        challengeAnswers: Map<String, String> = emptyMap(),
    ): JudgeResult {
        if (capsule.state != CapsuleState.LOCKED) {
            // 非 LOCKED 直接短路返回，不做任何求值
            val status = capsule.dependCapsuleId?.let { ctx.dependency.statusOf(it) }
            return JudgeResult(
                overallOk = false,
                dependencyOk = false,
                dependencyStatus = status ?: DependencyStatus.UNLOCKED,
                items = emptyList(),
            )
        }

        // 1. 依赖检查
        val depStatus = capsule.dependCapsuleId?.let { ctx.dependency.statusOf(it) }
        val dependencyOk = depStatus == null || depStatus == DependencyStatus.UNLOCKED

        // 2. 逐条件求值
        val items = capsule.unlockRule.conditionList.map { evaluate(it, capsule, ctx, lang, challengeAnswers) }

        // 3. AND/OR 合并（skipped 按不满足计入）
        val conditionsOk = merge(capsule.unlockRule, items)

        return JudgeResult(
            overallOk = dependencyOk && conditionsOk,
            dependencyOk = dependencyOk,
            dependencyStatus = depStatus ?: DependencyStatus.UNLOCKED,
            items = items,
        )
    }

    /**
     * 判定全部 LOCKED 胶囊并持久化；解锁成功的胶囊经 [onUnlocked] 回调（通知 + UI 反馈）。
     * 调用方（MainActivity ON_RESUME / Worker / 详情页）各自决定协程作用域。
     */
    suspend fun judgeAllLocked(
        ctx: ConditionContext,
        repo: CapsuleRepository,
        lang: Lang = Lang.ZH_HANS,
        onUnlocked: (Capsule) -> Unit = {},
    ) {
        for (capsule in repo.allLockedSync()) {
            val result = judge(capsule, ctx, lang)
            if (result.overallOk) {
                val now = ctx.time.nowMillis()
                repo.updateState(capsule.id, CapsuleState.UNLOCKED, now)
                onUnlocked(capsule.copy(state = CapsuleState.UNLOCKED, unlockTimestamp = now))
            }
        }
    }

    /** 依赖胶囊解锁状态文本（详情页时间线复用；[lang] 决定文案语言） */
    fun dependencyReason(status: DependencyStatus, lang: Lang = Lang.ZH_HANS): String? {
        val r = JudgeReasons.forLang(lang)
        return when (status) {
            DependencyStatus.LOCKED -> r.dependencyLocked
            DependencyStatus.NOT_FOUND -> r.dependencyNotFound
            DependencyStatus.DESTROYED -> r.dependencyDestroyed
            DependencyStatus.UNLOCKED -> null
        }
    }

    // ---- 逐条件求值：每条产出 satisfied / skipped / reason ----

    private fun evaluate(
        condition: UnlockCondition,
        capsule: Capsule,
        ctx: ConditionContext,
        lang: Lang,
        challengeAnswers: Map<String, String>,
    ): ConditionStatus {
        val r = JudgeReasons.forLang(lang)
        val (satisfied, skipped, reason) = when (condition) {
            is UnlockCondition.FixedDate -> {
                val ok = !ctx.time.today().isBefore(condition.targetDate)
                Triple(ok, false, null)
            }

            is UnlockCondition.MinElapsedDay -> {
                val createdDate = java.time.Instant.ofEpochMilli(capsule.createTimestamp)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                val elapsed = ChronoUnit.DAYS.between(createdDate, ctx.time.today())
                Triple(elapsed >= condition.days, false, null)
            }

            is UnlockCondition.WeekDay -> {
                Triple(ctx.time.today().dayOfWeek in condition.weekSet, false, null)
            }

            is UnlockCondition.TimeRange -> {
                val hour = ctx.time.nowHour()
                val ok = if (condition.endHour > condition.startHour) {
                    hour in condition.startHour until condition.endHour
                } else {
                    // 跨零点时段（如 22-6）
                    hour >= condition.startHour || hour < condition.endHour
                }
                Triple(ok, false, null)
            }

            is UnlockCondition.BatteryLevel -> {
                val info = ctx.battery.battery()
                if (info.levelPercent == null) {
                    Triple(false, false, r.batteryUnknown)
                } else {
                    val byMin = condition.min?.let { info.levelPercent >= it } ?: true
                    val byMax = condition.max?.let { info.levelPercent <= it } ?: true
                    Triple(byMin && byMax, false, null)
                }
            }

            is UnlockCondition.ChargingState -> {
                Triple(ctx.battery.battery().isCharging == condition.isCharging, false, null)
            }

            is UnlockCondition.StepCount -> {
                val steps = ctx.step.todaySteps()
                if (steps == null) {
                    // 无计步硬件或无权限
                    Triple(false, false, r.stepUnavailable)
                } else {
                    // 与 BatteryLevel 同构：任一端可空，闭区间
                    val byMin = condition.minTodayStep?.let { steps >= it } ?: true
                    val byMax = condition.maxTodayStep?.let { steps <= it } ?: true
                    Triple(byMin && byMax, false, null)
                }
            }

            is UnlockCondition.NetworkType -> {
                Triple(ctx.network.current() in condition.types, false, null)
            }

            is UnlockCondition.GpsLocation -> {
                when {
                    // 权限拒绝 → 不满足并给出修复方式
                    !ctx.location.isPermitted() ->
                        Triple(false, false, r.gpsNoPermission)
                    // Worker 后台场景 → skipped，按不满足参与合并
                    ctx.location.foregroundOnly ->
                        Triple(false, true, r.gpsBackground)
                    ctx.location.lastKnown() == null ->
                        Triple(false, false, r.gpsNoFix)
                    else -> {
                        val point = ctx.location.lastKnown()!!
                        val ok = GeoMath.withinRadius(
                            lat = point.lat,
                            lng = point.lng,
                            centerLat = condition.lat,
                            centerLng = condition.lng,
                            radiusMeters = condition.radiusMeter,
                        )
                        Triple(ok, false, null)
                    }
                }
            }

            is UnlockCondition.WeatherType -> {
                val cityId = capsule.weather?.cityId
                if (cityId == null) {
                    Triple(false, false, r.weatherNoSnapshot)
                } else {
                    val snapshot = ctx.weather.currentWeather(cityId)
                    if (snapshot == null) {
                        // 天气获取失败（30min 缓存在实现层）
                        Triple(false, false, r.weatherFailed)
                    } else {
                        Triple(snapshot.weatherType.name in condition.weatherTypes, false, null)
                    }
                }
            }

            is UnlockCondition.TemperatureThreshold -> {
                val cityId = capsule.weather?.cityId
                if (cityId == null) {
                    Triple(false, false, r.weatherNoSnapshot)
                } else {
                    val snapshot = ctx.weather.currentWeather(cityId)
                    if (snapshot == null) {
                        Triple(false, false, r.weatherFailed)
                    } else {
                        // 与 BatteryLevel 同构：任一端可空，闭区间
                        val byMin = condition.minC?.let { snapshot.tempC >= it } ?: true
                        val byMax = condition.maxC?.let { snapshot.tempC <= it } ?: true
                        Triple(byMin && byMax, false, null)
                    }
                }
            }

            is UnlockCondition.SsidMatch -> {
                val info = ctx.wifi.current()
                when (info.state) {
                    WifiSsidState.NO_PERMISSION ->
                        Triple(false, false, r.ssidNoPermission)
                    WifiSsidState.NO_LOCATION_SERVICE ->
                        Triple(false, false, r.ssidLocationOff)
                    WifiSsidState.NOT_CONNECTED ->
                        Triple(false, false, r.ssidNotConnected)
                    WifiSsidState.CONNECTED ->
                        Triple(info.ssid in condition.ssids, false, null)
                }
            }

            is UnlockCondition.StepStreak -> {
                val today = ctx.step.todaySteps()
                if (today == null) {
                    Triple(false, false, r.stepUnavailable)
                } else if (today < condition.goal) {
                    Triple(false, false, null)
                } else {
                    // 今日已达标，再查此前 days-1 天的历史记录（缺记录日 fail-closed）
                    var ok = true
                    for (daysAgo in 1 until condition.days) {
                        val past = ctx.stepHistory.daySteps(daysAgo)
                        if (past == null || past < condition.goal) {
                            ok = false
                            break
                        }
                    }
                    Triple(ok, false, if (ok) null else r.streakNoRecord)
                }
            }

            is UnlockCondition.FixedDateTime -> {
                val target = runCatching { java.time.LocalDateTime.parse(condition.targetDateTime) }.getOrNull()
                if (target == null) {
                    Triple(false, false, null)
                } else {
                    val now = java.time.Instant.ofEpochMilli(ctx.time.nowMillis())
                        .atZone(java.time.ZoneId.systemDefault())
                    Triple(now.toLocalDateTime() >= target, false, null)
                }
            }

            is UnlockCondition.MinElapsedMinutes -> {
                val elapsedMin = (ctx.time.nowMillis() - capsule.createTimestamp) / 60_000L
                Triple(elapsedMin >= condition.minutes, false, null)
            }

            is UnlockCondition.MonthlyDay ->
                Triple(ctx.time.today().dayOfMonth == condition.dayOfMonth, false, null)

            is UnlockCondition.YearlyDate -> {
                val today = ctx.time.today()
                Triple(today.monthValue == condition.month && today.dayOfMonth == condition.day, false, null)
            }

            is UnlockCondition.LunarDate -> {
                // 闰月按同月同日处理（闰六月十五也满足"农历六月十五"）；表外日期 fail-closed
                val lunar = LunarCalendar.solarToLunar(ctx.time.today())
                Triple(
                    lunar != null && lunar.month == condition.month && lunar.day == condition.day,
                    false,
                    null,
                )
            }

            is UnlockCondition.AwayFromLocation -> when {
                !ctx.location.isPermitted() ->
                    Triple(false, false, r.gpsNoPermission)
                ctx.location.foregroundOnly ->
                    Triple(false, true, r.gpsBackground)
                ctx.location.lastKnown() == null ->
                    Triple(false, false, r.gpsNoFix)
                else -> {
                    val point = ctx.location.lastKnown()!!
                    val inside = GeoMath.withinRadius(
                        lat = point.lat,
                        lng = point.lng,
                        centerLat = condition.lat,
                        centerLng = condition.lng,
                        radiusMeters = condition.radiusMeter,
                    )
                    Triple(!inside, false, null)
                }
            }

            is UnlockCondition.SunPhase -> when {
                !ctx.location.isPermitted() ->
                    Triple(false, false, r.gpsNoPermission)
                ctx.location.foregroundOnly ->
                    Triple(false, true, r.gpsBackground)
                ctx.location.lastKnown() == null ->
                    Triple(false, false, r.gpsNoFix)
                else -> {
                    val point = ctx.location.lastKnown()!!
                    val phase = SunCalc.phaseOf(ctx.time.nowMillis(), point.lat, point.lng, ctx.time.today())
                    Triple(phase in condition.phases, false, null)
                }
            }

            // 金色时刻与 SunPhase 同通道：需要定位换取经纬度做天文计算
            is UnlockCondition.GoldenHour -> when {
                !ctx.location.isPermitted() ->
                    Triple(false, false, r.gpsNoPermission)
                ctx.location.foregroundOnly ->
                    Triple(false, true, r.gpsBackground)
                ctx.location.lastKnown() == null ->
                    Triple(false, false, r.gpsNoFix)
                else -> {
                    val point = ctx.location.lastKnown()!!
                    Triple(
                        SunCalc.isGoldenHour(ctx.time.nowMillis(), point.lat, point.lng, ctx.time.today()),
                        false,
                        null,
                    )
                }
            }

            is UnlockCondition.WeatherMetric -> {
                val cityId = capsule.weather?.cityId
                if (cityId == null) {
                    Triple(false, false, r.weatherNoSnapshot)
                } else {
                    val snapshot = ctx.weather.currentWeather(cityId)
                    val value: Double? = snapshot?.let {
                        when (condition.metric) {
                            WeatherMetricKind.HUMIDITY -> it.humidityPercent
                            WeatherMetricKind.WIND -> it.windKmh
                            WeatherMetricKind.PRESSURE -> it.pressureHpa
                            WeatherMetricKind.UV -> it.uvIndex
                        }
                    }
                    if (snapshot == null) {
                        Triple(false, false, r.weatherFailed)
                    } else if (value == null) {
                        Triple(false, false, r.metricUnavailable)
                    } else {
                        val byMin = condition.min?.let { value >= it } ?: true
                        val byMax = condition.max?.let { value <= it } ?: true
                        Triple(byMin && byMax, false, null)
                    }
                }
            }

            is UnlockCondition.MoonPhase ->
                Triple(MoonCalc.phaseOf(ctx.time.today()) in condition.phases, false, null)

            is UnlockCondition.MeteorShower ->
                Triple(condition.showers.any { MeteorCalendar.isPeakNight(it, ctx.time.today()) }, false, null)

            is UnlockCondition.AmbientLight -> {
                val lux = ctx.ambientLight.lux()
                if (lux == null) {
                    Triple(false, false, r.ambientLightUnavailable)
                } else {
                    Triple(lux <= condition.maxLux, false, null)
                }
            }

            is UnlockCondition.TimezoneChange ->
                Triple(ctx.time.zoneId() != condition.homeZoneId, false, null)

            // 与 GPS 同通道：权限拒绝给原因、后台 skipped、无定位给原因
            is UnlockCondition.MovingAboveSpeed -> when {
                !ctx.location.isPermitted() ->
                    Triple(false, false, r.gpsNoPermission)
                ctx.location.foregroundOnly ->
                    Triple(false, true, r.gpsBackground)
                else -> {
                    val speed = ctx.location.speedMps()
                    if (speed == null) {
                        Triple(false, false, r.gpsNoFix)
                    } else {
                        Triple(speed * 3.6f >= condition.minSpeedKmh, false, null)
                    }
                }
            }

            is UnlockCondition.BeforeNextAlarm -> {
                val next = ctx.alarm.nextAlarmMillis()
                if (next == null) {
                    Triple(false, false, r.alarmNotSet)
                } else {
                    Triple(ctx.time.nowMillis() < next, false, null)
                }
            }

            is UnlockCondition.PowerSaveMode ->
                Triple(ctx.systemMode.isPowerSave() == condition.isActive, false, null)

            is UnlockCondition.SilentMode ->
                Triple(ctx.systemMode.isSilentRinger() == condition.isSilent, false, null)

            is UnlockCondition.AirplaneMode ->
                Triple(ctx.systemMode.isAirplaneModeOn() == condition.isEnabled, false, null)

            is UnlockCondition.MusicPlaying ->
                Triple(ctx.systemMode.isMusicPlaying() == condition.isPlaying, false, null)

            is UnlockCondition.HeadphoneConnected ->
                Triple(ctx.systemMode.isHeadphoneConnected() == condition.isConnected, false, null)

            is UnlockCondition.MotionActivity -> {
                val kind = ctx.motion.current()
                if (kind == null) {
                    Triple(false, false, r.motionUnavailable)
                } else {
                    Triple(kind in condition.kinds, false, null)
                }
            }

            is UnlockCondition.CompassHeading -> {
                val heading = ctx.compass.headingDeg()
                if (heading == null) {
                    Triple(false, false, r.compassUnavailable)
                } else {
                    val diff = kotlin.math.abs(heading - condition.targetDeg) % 360f
                    val distance = if (diff > 180f) 360f - diff else diff
                    Triple(distance <= condition.toleranceDeg, false, null)
                }
            }

            is UnlockCondition.AltitudeRange -> {
                val alt = ctx.altitude.altitudeMeters()
                if (alt == null) {
                    Triple(false, false, r.altitudeUnavailable)
                } else {
                    val byMin = condition.minM?.let { alt >= it } ?: true
                    val byMax = condition.maxM?.let { alt <= it } ?: true
                    Triple(byMin && byMax, false, null)
                }
            }

            is UnlockCondition.OpenCountAtLeast ->
                Triple(ctx.usage.openCount() >= condition.count, false, null)

            is UnlockCondition.OpenStreak ->
                Triple(ctx.usage.openStreakDays() >= condition.days, false, null)

            is UnlockCondition.DaysSinceLastOpen -> {
                val last = ctx.usage.lastOpenMillis()
                if (last == null) {
                    Triple(false, false, r.noOpenRecord)
                } else {
                    Triple(ctx.time.nowMillis() - last >= condition.days * 86_400_000L, false, null)
                }
            }

            is UnlockCondition.CapsuleCountAtLeast ->
                Triple(ctx.meta.capsuleCount() >= condition.count, false, null)

            is UnlockCondition.ViewCountAtLeast ->
                Triple(ctx.meta.viewCount(capsule.id) >= condition.count, false, null)

            is UnlockCondition.OtherCapsuleUnlocked -> {
                val status = ctx.dependency.statusOf(condition.capsuleId)
                if (status == DependencyStatus.NOT_FOUND) {
                    Triple(false, false, r.dependencyNotFound)
                } else {
                    Triple(status == DependencyStatus.UNLOCKED, false, null)
                }
            }

            is UnlockCondition.OtherCapsuleDestroyed -> {
                val status = ctx.dependency.statusOf(condition.capsuleId)
                if (status == DependencyStatus.NOT_FOUND) {
                    Triple(false, false, r.dependencyNotFound)
                } else {
                    Triple(status == DependencyStatus.DESTROYED, false, null)
                }
            }

            // 已读状态取 meta 事实源（与首页三态一致）：读过即恒满足，含读后销毁的胶囊
            is UnlockCondition.OtherCapsuleRead ->
                Triple(ctx.meta.isRead(condition.capsuleId), false, null)

            is UnlockCondition.ChallengeCondition -> {
                val answer = challengeAnswers[condition.challengeId]
                if (answer == null) {
                    // 未携带应答：周期巡检 / 全量判定 fail-closed
                    Triple(false, false, r.challengePending)
                } else {
                    val ok = when (condition) {
                        is UnlockCondition.QuestionAnswer ->
                            answer.trim().equals(condition.expectedAnswer.trim(), ignoreCase = true)
                        is UnlockCondition.PuzzleAnswer ->
                            sha256Hex(answer.trim().lowercase()).equals(condition.answerHash, ignoreCase = true)
                        // 感官挑战（摇一摇/翻面静置/NFC）：物理动作由 App 当场核验，应答传 DONE
                        else -> answer == CHALLENGE_DONE
                    }
                    Triple(ok, false, if (ok) null else r.challengeWrong)
                }
            }
        }
        return ConditionStatus(condition = condition, satisfied = satisfied, skipped = skipped, reason = reason)
    }

    private fun merge(rule: UnlockRule, items: List<ConditionStatus>): Boolean = when (rule.logicType) {
        LogicType.AND -> if (items.isEmpty()) true else items.all { it.satisfied }
        LogicType.OR -> items.any { it.satisfied }
    }

    /** 感官挑战完成应答的约定值（App 当场核验物理动作后传入） */
    companion object {
        const val CHALLENGE_DONE = "DONE"

        fun sha256Hex(input: String): String =
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
