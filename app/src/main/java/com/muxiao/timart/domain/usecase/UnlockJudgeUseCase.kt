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
import com.muxiao.timart.domain.model.unlock.SeasonKind
import com.muxiao.timart.domain.model.unlock.SolarTermCalendar
import com.muxiao.timart.domain.model.unlock.SunCalc
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.domain.model.unlock.UnlockRule
import com.muxiao.timart.domain.model.unlock.LiftDirection
import com.muxiao.timart.domain.model.unlock.WeatherMetricKind
import com.muxiao.timart.domain.model.unlock.windDirFromDeg
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
     *
     * [onConditionSatisfied]：每当观测到某条件满足即回调（capsuleId, 条件下标, 条件本体），
     * 供「条件达成时刻」埋点（capsule.condMet.*，写入由调用方负责且只写不覆写）；
     * 每轮全量判定都会对已满足条件重复回调，去重责任在回调侧。默认空实现。
     * [shouldJudge]：跳过不该参与周期判定的胶囊（嵌套种子的休眠态，体验储备池 §3——
     * 藏着的种子条件即便已达成也不能在萌芽前解锁）；默认全量判定。
     */
    suspend fun judgeAllLocked(
        ctx: ConditionContext,
        repo: CapsuleRepository,
        lang: Lang = Lang.ZH_HANS,
        onUnlocked: (Capsule) -> Unit = {},
        onConditionSatisfied: (capsuleId: String, index: Int, condition: UnlockCondition) -> Unit = { _, _, _ -> },
        shouldJudge: (Capsule) -> Boolean = { _ -> true },
    ) {
        for (capsule in repo.allLockedSync()) {
            if (!shouldJudge(capsule)) continue
            val result = judge(capsule, ctx, lang)
            result.items.forEachIndexed { index, item ->
                if (item.satisfied) onConditionSatisfied(capsule.id, index, item.condition)
            }
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
                        // 图案挑战：应答 = 九宫格点位序列（规范化串），SHA-256 比对
                        is UnlockCondition.GesturePattern ->
                            sha256Hex(answer).equals(condition.answerHash, ignoreCase = true)
                        // 语音口令：识别文本比对口径同问答
                        is UnlockCondition.VoicePassword ->
                            answer.trim().equals(condition.expectedAnswer.trim(), ignoreCase = true)
                        // 扫码：内容一致即通过；未绑定内容时任意非空码通过
                        is UnlockCondition.ScanQr ->
                            condition.expectedPayload == null || answer.trim() == condition.expectedPayload.trim()
                        // 算力挑战：应答 = nonce，SHA-256(challengeId:nonce) 前导零 ≥ difficulty
                        is UnlockCondition.ProofOfWork ->
                            sha256Hex("${condition.challengeId}:$answer")
                                .startsWith("0".repeat(condition.difficulty))
                        // 感官挑战（摇一摇/翻面静置/NFC/当场步数/转机/音量键/静置/举放/爬楼/连点）：
                        // 物理动作由 App 当场核验，应答传 DONE
                        else -> answer == CHALLENGE_DONE
                    }
                    Triple(ok, false, if (ok) null else r.challengeWrong)
                }
            }

            // ================= 储备池 v4 扩展（delta-prd-vs-code.md D-1.2） =================

            is UnlockCondition.SolarTerm ->
                Triple(SolarTermCalendar.termOf(ctx.time.today()) in condition.solarTerms, false, null)

            is UnlockCondition.RoundDaysElapsed -> {
                val createdDate = java.time.Instant.ofEpochMilli(capsule.createTimestamp)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                val elapsed = ChronoUnit.DAYS.between(createdDate, ctx.time.today())
                Triple(elapsed >= 1 && elapsed % condition.modulus == 0L, false, null)
            }

            is UnlockCondition.Season -> {
                val season = when (ctx.time.today().monthValue) {
                    3, 4, 5 -> SeasonKind.SPRING
                    6, 7, 8 -> SeasonKind.SUMMER
                    9, 10, 11 -> SeasonKind.AUTUMN
                    else -> SeasonKind.WINTER
                }
                Triple(season in condition.seasons, false, null)
            }

            is UnlockCondition.MinElapsedMonths -> {
                val created = java.time.Instant.ofEpochMilli(capsule.createTimestamp)
                    .atZone(java.time.ZoneId.systemDefault())
                val now = java.time.Instant.ofEpochMilli(ctx.time.nowMillis()).atZone(java.time.ZoneId.systemDefault())
                Triple(!now.isBefore(created.plusMonths(condition.months.toLong())), false, null)
            }

            is UnlockCondition.NthWeekdayOfMonth -> {
                val today = ctx.time.today()
                Triple(isNthWeekdayOf(today, condition.nth, condition.dayOfWeek), false, null)
            }

            is UnlockCondition.YearlyNthWeekday -> {
                val today = ctx.time.today()
                Triple(
                    today.monthValue == condition.month && isNthWeekdayOf(today, condition.nth, condition.dayOfWeek),
                    false,
                    null,
                )
            }

            is UnlockCondition.LeapDay ->
                Triple(ctx.time.today().monthValue == 2 && ctx.time.today().dayOfMonth == 29, false, null)

            is UnlockCondition.LastDayOfMonth ->
                Triple(ctx.time.today().dayOfMonth == ctx.time.today().lengthOfMonth(), false, null)

            is UnlockCondition.NthWeekdaySince -> {
                val createdDate = java.time.Instant.ofEpochMilli(capsule.createTimestamp)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                val elapsed = ChronoUnit.DAYS.between(createdDate, ctx.time.today())
                // 满 minDays 天后的首个星期X当天：7 天窗口内恰有一个星期X，错过即不再满足
                Triple(
                    elapsed >= condition.minDays &&
                        elapsed - condition.minDays < 7 &&
                        ctx.time.today().dayOfWeek == condition.dayOfWeek,
                    false,
                    null,
                )
            }

            is UnlockCondition.ZodiacSeason ->
                Triple(SolarTermCalendar.zodiacOf(ctx.time.today()) == condition.zodiac, false, null)

            // 白昼长度 / 日出钟点：与 SunPhase 同通道（定位换经纬度做天文计算）
            is UnlockCondition.DayLength -> when {
                !ctx.location.isPermitted() ->
                    Triple(false, false, r.gpsNoPermission)
                ctx.location.foregroundOnly ->
                    Triple(false, true, r.gpsBackground)
                else -> {
                    val point = ctx.location.lastKnown()
                    val times = point?.let { SunCalc.sunTimesUtcMillis(it.lat, it.lng, ctx.time.today()) }
                    if (point == null) {
                        Triple(false, false, r.gpsNoFix)
                    } else if (times == null) {
                        // 极昼/极夜：无日出日落，白昼长度无定义，fail-closed
                        Triple(false, false, null)
                    } else {
                        val hours = (times.second - times.first) / 3_600_000.0
                        val byMin = condition.minHours?.let { hours >= it } ?: true
                        val byMax = condition.maxHours?.let { hours <= it } ?: true
                        Triple(byMin && byMax, false, null)
                    }
                }
            }

            is UnlockCondition.SunriseTimeRange -> when {
                !ctx.location.isPermitted() ->
                    Triple(false, false, r.gpsNoPermission)
                ctx.location.foregroundOnly ->
                    Triple(false, true, r.gpsBackground)
                else -> {
                    val point = ctx.location.lastKnown()
                    val riseUtc = point?.let { SunCalc.sunTimesUtcMillis(it.lat, it.lng, ctx.time.today())?.first }
                    if (point == null) {
                        Triple(false, false, r.gpsNoFix)
                    } else if (riseUtc == null) {
                        Triple(false, false, null)
                    } else {
                        val minuteOfDay = java.time.Instant.ofEpochMilli(riseUtc)
                            .atZone(java.time.ZoneId.systemDefault())
                            .let { it.hour * 60 + it.minute }
                        val byMin = condition.minMinute?.let { minuteOfDay >= it } ?: true
                        val byMax = condition.maxMinute?.let { minuteOfDay <= it } ?: true
                        Triple(byMin && byMax, false, null)
                    }
                }
            }

            is UnlockCondition.LunarMonthRange -> {
                val lunar = LunarCalendar.solarToLunar(ctx.time.today())
                Triple(lunar != null && lunar.month == condition.month, false, null)
            }

            is UnlockCondition.MonthlyDaySet ->
                Triple(ctx.time.today().dayOfMonth in condition.days, false, null)

            is UnlockCondition.DarkTheme -> when (ctx.systemUi) {
                null -> Triple(false, false, r.deviceStateUnavailable)
                else -> Triple(ctx.systemUi.isDarkThemeOn() == condition.isDark, false, null)
            }

            is UnlockCondition.DoNotDisturb -> when (ctx.systemUi) {
                null -> Triple(false, false, r.deviceStateUnavailable)
                else -> Triple(ctx.systemUi.isDndActive() == condition.isActive, false, null)
            }

            is UnlockCondition.DevicePose -> {
                val pose = ctx.sensorExtra?.pose()
                if (pose == null) {
                    Triple(false, false, r.envSensorUnavailable)
                } else {
                    Triple(pose in condition.kinds, false, null)
                }
            }

            is UnlockCondition.ScreenBrightness -> {
                val level = ctx.systemUi?.screenBrightnessLevel()
                if (level == null) {
                    Triple(false, false, r.deviceStateUnavailable)
                } else {
                    Triple(level <= condition.maxLevel, false, null)
                }
            }

            is UnlockCondition.MediaVolume -> when (ctx.systemUi) {
                null -> Triple(false, false, r.deviceStateUnavailable)
                else -> Triple(ctx.systemUi.isMediaMuted() == condition.isMuted, false, null)
            }

            is UnlockCondition.VpnActive -> when (ctx.deviceExtra) {
                null -> Triple(false, false, r.deviceStateUnavailable)
                else -> Triple(ctx.deviceExtra.isVpnActive() == condition.isActive, false, null)
            }

            is UnlockCondition.PlugType -> {
                val kind = ctx.deviceExtra?.pluggedKind()
                if (kind == null) {
                    // 未充电或读取失败：指定充电方式的条件自然不满足
                    Triple(false, false, null)
                } else {
                    Triple(kind in condition.kinds, false, null)
                }
            }

            is UnlockCondition.BatteryTemp -> {
                val temp = ctx.deviceExtra?.batteryTempC()
                if (temp == null) {
                    Triple(false, false, r.deviceStateUnavailable)
                } else {
                    val byMin = condition.minC?.let { temp >= it } ?: true
                    val byMax = condition.maxC?.let { temp <= it } ?: true
                    Triple(byMin && byMax, false, null)
                }
            }

            is UnlockCondition.Orientation -> when (ctx.systemUi) {
                null -> Triple(false, false, r.deviceStateUnavailable)
                else -> Triple(ctx.systemUi.isLandscape() == condition.isLandscape, false, null)
            }

            is UnlockCondition.SpeedRange -> when {
                !ctx.location.isPermitted() ->
                    Triple(false, false, r.gpsNoPermission)
                ctx.location.foregroundOnly ->
                    Triple(false, true, r.gpsBackground)
                else -> {
                    val speed = ctx.location.speedMps()
                    if (speed == null) {
                        Triple(false, false, r.gpsNoFix)
                    } else {
                        val kmh = speed * 3.6
                        val byMin = condition.minKmh?.let { kmh >= it } ?: true
                        val byMax = condition.maxKmh?.let { kmh <= it } ?: true
                        Triple(byMin && byMax, false, null)
                    }
                }
            }

            is UnlockCondition.SsidBssidMatch -> {
                val info = ctx.wifi.current()
                when (info.state) {
                    WifiSsidState.NO_PERMISSION ->
                        Triple(false, false, r.ssidNoPermission)
                    WifiSsidState.NO_LOCATION_SERVICE ->
                        Triple(false, false, r.ssidLocationOff)
                    WifiSsidState.NOT_CONNECTED ->
                        Triple(false, false, r.ssidNotConnected)
                    WifiSsidState.CONNECTED ->
                        Triple(info.bssid != null && info.bssid in condition.bssids, false, null)
                }
            }

            is UnlockCondition.BluetoothDevice -> {
                val names = ctx.appEnv?.connectedBluetoothNames()
                when {
                    ctx.appEnv == null -> Triple(false, false, r.deviceStateUnavailable)
                    names == null -> Triple(false, false, r.btNoPermission)
                    else -> Triple(names.any { it in condition.deviceNames }, false, null)
                }
            }

            is UnlockCondition.ProximityCovered -> {
                val covered = ctx.sensorExtra?.proximityCovered()
                if (covered == null) {
                    Triple(false, false, r.envSensorUnavailable)
                } else {
                    Triple(covered, false, null)
                }
            }

            is UnlockCondition.FreshBoot -> {
                val elapsed = ctx.deviceExtra?.bootElapsedMillis()
                if (elapsed == null) {
                    Triple(false, false, r.deviceStateUnavailable)
                } else {
                    Triple(elapsed <= condition.withinMinutes * 60_000L, false, null)
                }
            }

            is UnlockCondition.InstalledApp -> when (ctx.appEnv) {
                null -> Triple(false, false, r.deviceStateUnavailable)
                else -> Triple(ctx.appEnv.isAppInstalled(condition.packageName), false, null)
            }

            is UnlockCondition.AirQuality -> {
                val cityId = capsule.weather?.cityId
                if (cityId == null) {
                    Triple(false, false, r.weatherNoSnapshot)
                } else {
                    val aqi = ctx.airQuality?.aqi(cityId)
                    if (aqi == null) {
                        Triple(false, false, r.airQualityFailed)
                    } else {
                        Triple(aqi <= condition.maxAqi, false, null)
                    }
                }
            }

            is UnlockCondition.WindDirection -> {
                val cityId = capsule.weather?.cityId
                if (cityId == null) {
                    Triple(false, false, r.weatherNoSnapshot)
                } else {
                    val snapshot = ctx.weather.currentWeather(cityId)
                    val deg = snapshot?.windDirectionDeg
                    if (snapshot == null) {
                        Triple(false, false, r.weatherFailed)
                    } else if (deg == null) {
                        Triple(false, false, r.metricUnavailable)
                    } else {
                        Triple(windDirFromDeg(deg) in condition.dirs, false, null)
                    }
                }
            }

            is UnlockCondition.Hemisphere -> when {
                !ctx.location.isPermitted() ->
                    Triple(false, false, r.gpsNoPermission)
                ctx.location.foregroundOnly ->
                    Triple(false, true, r.gpsBackground)
                else -> {
                    val point = ctx.location.lastKnown()
                    if (point == null) {
                        Triple(false, false, r.gpsNoFix)
                    } else {
                        Triple(if (condition.north) point.lat >= 0 else point.lat < 0, false, null)
                    }
                }
            }

            is UnlockCondition.TempDelta -> {
                val cityId = capsule.weather?.cityId
                if (cityId == null) {
                    Triple(false, false, r.weatherNoSnapshot)
                } else {
                    val snapshot = ctx.weather.currentWeather(cityId)
                    val yesterday = snapshot?.yesterdayMeanTempC
                    if (snapshot == null) {
                        Triple(false, false, r.weatherFailed)
                    } else if (yesterday == null) {
                        Triple(false, false, r.metricUnavailable)
                    } else {
                        Triple(snapshot.tempC <= yesterday - condition.minDropC, false, null)
                    }
                }
            }

            is UnlockCondition.CityLocation -> when {
                !ctx.location.isPermitted() ->
                    Triple(false, false, r.gpsNoPermission)
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

            is UnlockCondition.RelativeAltitude -> {
                val alt = ctx.altitude.altitudeMeters()
                if (alt == null) {
                    Triple(false, false, r.altitudeUnavailable)
                } else {
                    val delta = alt - condition.baseAltM
                    Triple(
                        if (condition.direction == LiftDirection.UP) delta >= condition.deltaM else -delta >= condition.deltaM,
                        false,
                        null,
                    )
                }
            }

            is UnlockCondition.PrecipitationProbability -> {
                val cityId = capsule.weather?.cityId
                if (cityId == null) {
                    Triple(false, false, r.weatherNoSnapshot)
                } else {
                    val snapshot = ctx.weather.currentWeather(cityId)
                    val prob = snapshot?.precipProbPercent
                    if (snapshot == null) {
                        Triple(false, false, r.weatherFailed)
                    } else if (prob == null) {
                        Triple(false, false, r.metricUnavailable)
                    } else {
                        Triple(prob >= condition.minProb, false, null)
                    }
                }
            }

            is UnlockCondition.WatchDurationAtLeast ->
                Triple(ctx.meta.watchSeconds(capsule.id) >= condition.seconds, false, null)

            is UnlockCondition.ReadCountAtLeast ->
                Triple(ctx.meta.readCount() >= condition.count, false, null)

            is UnlockCondition.DestroyCountAtLeast ->
                Triple(ctx.meta.destroyCount() >= condition.count, false, null)

            is UnlockCondition.OtherCapsuleStillLocked -> {
                val status = ctx.dependency.statusOf(condition.capsuleId)
                if (status == DependencyStatus.NOT_FOUND) {
                    Triple(false, false, r.dependencyNotFound)
                } else {
                    Triple(status == DependencyStatus.LOCKED, false, null)
                }
            }

            is UnlockCondition.BackupDone ->
                Triple(ctx.meta.backupDone(), false, null)

            is UnlockCondition.TotalCreatedCount ->
                Triple(ctx.meta.totalCreated() >= condition.count, false, null)

            is UnlockCondition.SameDayAsCapsuleRead -> {
                val readAt = ctx.meta.lastReadAt(condition.capsuleId)
                if (readAt == null) {
                    Triple(false, false, null)
                } else {
                    val readDay = java.time.Instant.ofEpochMilli(readAt)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                    Triple(readDay == ctx.time.today(), false, null)
                }
            }

            is UnlockCondition.DaysSinceCapsuleRead -> {
                val readAt = ctx.meta.lastReadAt(condition.capsuleId)
                if (readAt == null) {
                    Triple(false, false, null)
                } else {
                    Triple(ctx.time.nowMillis() - readAt >= condition.days * 86_400_000L, false, null)
                }
            }

            is UnlockCondition.WidgetBound -> when (ctx.appEnv) {
                null -> Triple(false, false, r.deviceStateUnavailable)
                else -> Triple(ctx.appEnv.isAnyWidgetBound(), false, null)
            }

            is UnlockCondition.TodayOpenCount ->
                Triple(ctx.usage.todayOpenCount() >= condition.count, false, null)
        }
        return ConditionStatus(condition = condition, satisfied = satisfied, skipped = skipped, reason = reason)
    }

    private fun merge(rule: UnlockRule, items: List<ConditionStatus>): Boolean = when (rule.logicType) {
        LogicType.AND -> if (items.isEmpty()) true else items.all { it.satisfied }
        LogicType.OR -> items.any { it.satisfied }
        // M-of-N：满足条数达到阈值即通过；skipped / 未应答挑战照常计为不满足（fail-closed 不变）；
        // 阈值缺省回退为"全部"（等价 AND），阈值越界在序列化层已归一并 coerce，这里再兜底一次
        LogicType.AT_LEAST -> {
            val required = (rule.threshold ?: items.size).coerceIn(1, items.size)
            items.count { it.satisfied } >= required
        }
    }

    /** 今天是否为本月第 [nth] 个 [dayOfWeek]（nth 1–5；如"每月第一个周一"） */
    private fun isNthWeekdayOf(today: java.time.LocalDate, nth: Int, dayOfWeek: java.time.DayOfWeek): Boolean {
        if (today.dayOfWeek != dayOfWeek) return false
        return (today.dayOfMonth - 1) / 7 + 1 == nth
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
