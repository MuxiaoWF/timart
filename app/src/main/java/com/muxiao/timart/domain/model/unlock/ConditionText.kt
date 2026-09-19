package com.muxiao.timart.domain.model.unlock

import com.muxiao.timart.domain.model.Lang
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 条件句与判定原因文案（纯函数，创建页卡片与详情页时间线共用）。
 * 语言风格对齐设计稿："到达东京""今天下雨""电量低于 20%"。
 * 三语文案随 [Lang] 切换；默认简中（历史行为，也是 51 个单测的基准语言）。
 */
object ConditionText {

    /** 把一条解锁条件翻译成设计稿语言风格的自然语言条件句 */
    fun conditionSentence(condition: UnlockCondition, lang: Lang = Lang.ZH_HANS): String {
        val w = words(lang)
        return when (condition) {
            is UnlockCondition.FixedDate -> w.arriveAt.format(formatDate(condition.targetDate))
            is UnlockCondition.MinElapsedDay -> w.sealedDays.format(condition.days)
            is UnlockCondition.WeekDay ->
                w.everyWeekday + condition.weekSet.sorted().joinToString(w.listSep) { dayName(it, lang) }
            is UnlockCondition.TimeRange -> {
                val start = condition.startHour
                val end = condition.endHour
                if (end > start) {
                    w.dailyRange.format(start, end)
                } else {
                    // 跨零点时段（如 22-6）
                    w.dailyRangeCrossMidnight.format(start, end)
                }
            }
            is UnlockCondition.BatteryLevel -> when {
                condition.min != null && condition.max != null ->
                    w.batteryBetween.format(condition.min, condition.max)
                condition.min != null -> w.batteryAbove.format(condition.min)
                condition.max != null -> w.batteryBelow.format(condition.max)
                else -> w.batteryAny
            }
            is UnlockCondition.ChargingState ->
                if (condition.isCharging) w.charging else w.notCharging
            is UnlockCondition.StepCount -> when {
                condition.minTodayStep != null && condition.maxTodayStep != null ->
                    w.stepsBetween.format(condition.minTodayStep, condition.maxTodayStep)
                condition.minTodayStep != null -> w.stepsOver.format(condition.minTodayStep)
                condition.maxTodayStep != null -> w.stepsBelow.format(condition.maxTodayStep)
                else -> w.stepsAny
            }
            is UnlockCondition.NetworkType ->
                condition.types.joinToString(w.netOr) { netName(it, lang) }.ifEmpty { w.netAny }
            is UnlockCondition.GpsLocation -> w.atLocation.format(condition.radiusMeter)
            is UnlockCondition.WeatherType ->
                w.weatherEncounter + condition.weatherTypes.mapNotNull { weatherName(it, lang) }
                    .joinToString(w.listSep).ifEmpty { w.weatherAny }
            is UnlockCondition.TemperatureThreshold -> when {
                condition.minC != null && condition.maxC != null ->
                    w.tempBetween.format(formatTemp(condition.minC), formatTemp(condition.maxC))
                condition.minC != null -> w.tempAbove.format(formatTemp(condition.minC))
                condition.maxC != null -> w.tempBelow.format(formatTemp(condition.maxC))
                else -> w.tempAny
            }
            is UnlockCondition.SsidMatch -> w.ssidAt.format(condition.ssids.sorted().joinToString(w.listSep))
            is UnlockCondition.StepStreak -> w.streakFmt.format(condition.days, condition.goal)
            is UnlockCondition.FixedDateTime -> w.momentAt.format(condition.targetDateTime.replace('T', ' '))
            is UnlockCondition.MinElapsedMinutes -> w.elapsedMinutesFmt.format(condition.minutes)
            is UnlockCondition.MonthlyDay -> w.monthlyDayFmt.format(condition.dayOfMonth)
            is UnlockCondition.YearlyDate -> w.yearlyFmt.format(condition.month, condition.day)
            is UnlockCondition.AwayFromLocation -> w.awayFromFmt.format(condition.radiusMeter)
            is UnlockCondition.SunPhase ->
                w.sunEncounter + condition.phases.joinToString(w.listSep) { sunName(it, lang) }
            is UnlockCondition.WeatherMetric -> {
                val name = metricName(condition.metric, lang)
                val minV = condition.min?.let { formatMetric(condition.metric, it) }
                val maxV = condition.max?.let { formatMetric(condition.metric, it) }
                when {
                    minV != null && maxV != null -> w.metricBetween.format(name, minV, maxV)
                    minV != null -> w.metricAbove.format(name, minV)
                    maxV != null -> w.metricBelow.format(name, maxV)
                    else -> w.metricAny.format(name)
                }
            }
            is UnlockCondition.MoonPhase ->
                w.moonEncounter + condition.phases.joinToString(w.listSep) { moonName(it, lang) }
            is UnlockCondition.MeteorShower ->
                w.meteorEncounter + condition.showers.joinToString(w.listSep) { meteorName(it, lang) }
            is UnlockCondition.AmbientLight -> w.ambientFmt.format(condition.maxLux)
            is UnlockCondition.TimezoneChange -> w.timezoneAway
            is UnlockCondition.MovingAboveSpeed -> w.movingFmt.format(condition.minSpeedKmh)
            is UnlockCondition.GoldenHour -> w.goldenHour
            is UnlockCondition.LunarDate -> w.lunarFmt.format(condition.month, condition.day)
            is UnlockCondition.AirplaneMode ->
                if (condition.isEnabled) w.airplaneOn else w.airplaneOff
            is UnlockCondition.MusicPlaying ->
                if (condition.isPlaying) w.musicOn else w.musicOff
            is UnlockCondition.BeforeNextAlarm -> w.beforeNextAlarm
            is UnlockCondition.PowerSaveMode -> if (condition.isActive) w.powerSaveOn else w.powerSaveOff
            is UnlockCondition.SilentMode -> if (condition.isSilent) w.silentOn else w.silentOff
            is UnlockCondition.HeadphoneConnected -> if (condition.isConnected) w.headphoneOn else w.headphoneOff
            is UnlockCondition.MotionActivity ->
                w.motionEncounter + condition.kinds.joinToString(w.listSep) { motionName(it, lang) }
            is UnlockCondition.CompassHeading -> w.compassFmt.format(condition.targetDeg, condition.toleranceDeg)
            is UnlockCondition.AltitudeRange -> when {
                condition.minM != null && condition.maxM != null ->
                    w.altitudeBetween.format(formatMetric(WeatherMetricKind.PRESSURE, condition.minM), formatMetric(WeatherMetricKind.PRESSURE, condition.maxM))
                condition.minM != null -> w.altitudeAbove.format(formatMetric(WeatherMetricKind.PRESSURE, condition.minM))
                condition.maxM != null -> w.altitudeBelow.format(formatMetric(WeatherMetricKind.PRESSURE, condition.maxM))
                else -> w.altitudeAny
            }
            is UnlockCondition.OpenCountAtLeast -> w.openCountFmt.format(condition.count)
            is UnlockCondition.OpenStreak -> w.openStreakFmt.format(condition.days)
            is UnlockCondition.DaysSinceLastOpen -> w.lastOpenFmt.format(condition.days)
            is UnlockCondition.CapsuleCountAtLeast -> w.capsuleCountFmt.format(condition.count)
            is UnlockCondition.OtherCapsuleUnlocked -> w.otherUnlocked
            is UnlockCondition.OtherCapsuleDestroyed -> w.otherDestroyed
            is UnlockCondition.OtherCapsuleRead -> w.otherRead
            is UnlockCondition.ViewCountAtLeast -> w.viewCountFmt.format(condition.count)
            is UnlockCondition.QuestionAnswer -> w.questionFmt.format(condition.question)
            is UnlockCondition.PuzzleAnswer -> w.puzzleFmt.format(condition.question)
            is UnlockCondition.ShakeCount -> w.shakeFmt.format(condition.shakes)
            is UnlockCondition.FlipOrHold ->
                if (condition.gesture == GestureKind.FLIP) w.flip else w.holdFmt.format(condition.holdSeconds)
            is UnlockCondition.NfcTap ->
                if (condition.expectedPayload != null) w.nfcTapPaired else w.nfcTap
            is UnlockCondition.HoldPress -> w.holdPressFmt.format(condition.holdSeconds)
            is UnlockCondition.BiometricUnlock -> w.biometric
            is UnlockCondition.PhotoKeepsake -> w.photoKeepsake

            // ---- 储备池 v4：时间 / 天文 ----
            is UnlockCondition.SolarTerm ->
                w.solarEncounter + condition.solarTerms.joinToString(w.listSep) { solarTermName(it, lang) }
            is UnlockCondition.RoundDaysElapsed -> w.roundDaysFmt.format(condition.modulus)
            is UnlockCondition.Season ->
                w.seasonEncounter + condition.seasons.sorted().joinToString(w.listSep) { seasonName(it, lang) }
            is UnlockCondition.MinElapsedMonths -> w.elapsedMonthsFmt.format(condition.months)
            is UnlockCondition.NthWeekdayOfMonth -> w.nthWeekdayFmt.format(condition.nth, dayName(condition.dayOfWeek, lang))
            is UnlockCondition.YearlyNthWeekday ->
                w.yearlyNthFmt.format(condition.month, condition.nth, dayName(condition.dayOfWeek, lang))
            is UnlockCondition.LeapDay -> w.leapDay
            is UnlockCondition.LastDayOfMonth -> w.lastDayOfMonth
            is UnlockCondition.NthWeekdaySince ->
                w.nthWeekdaySinceFmt.format(condition.minDays, dayName(condition.dayOfWeek, lang))
            is UnlockCondition.ZodiacSeason -> w.zodiacEncounter + zodiacName(condition.zodiac, lang)
            is UnlockCondition.DayLength -> when {
                condition.minHours != null && condition.maxHours != null ->
                    w.dayLenBetween.format(formatTemp(condition.minHours), formatTemp(condition.maxHours))
                condition.minHours != null -> w.dayLenAbove.format(formatTemp(condition.minHours))
                condition.maxHours != null -> w.dayLenBelow.format(formatTemp(condition.maxHours))
                else -> w.dayLenAny
            }
            is UnlockCondition.SunriseTimeRange -> when {
                condition.minMinute != null && condition.maxMinute != null ->
                    w.sunriseBetween.format(formatMinuteOfDay(condition.minMinute), formatMinuteOfDay(condition.maxMinute))
                condition.minMinute != null -> w.sunriseAfter.format(formatMinuteOfDay(condition.minMinute))
                condition.maxMinute != null -> w.sunriseBefore.format(formatMinuteOfDay(condition.maxMinute))
                else -> w.sunriseAny
            }
            is UnlockCondition.LunarMonthRange -> w.lunarMonthFmt.format(condition.month)
            is UnlockCondition.MonthlyDaySet -> w.monthlyDaysFmt.format(condition.days.sorted().joinToString(w.listSep))

            // ---- 储备池 v4：设备 / 系统 ----
            is UnlockCondition.DarkTheme -> if (condition.isDark) w.darkOn else w.darkOff
            is UnlockCondition.DoNotDisturb -> if (condition.isActive) w.dndOn else w.dndOff
            is UnlockCondition.DevicePose ->
                w.poseEncounter + condition.kinds.sorted().joinToString(w.listSep) { poseName(it, lang) }
            is UnlockCondition.ScreenBrightness -> w.brightnessFmt.format(condition.maxLevel)
            is UnlockCondition.MediaVolume -> if (condition.isMuted) w.volumeMuted else w.volumeNotMuted
            is UnlockCondition.VpnActive -> if (condition.isActive) w.vpnOn else w.vpnOff
            is UnlockCondition.PlugType ->
                w.plugEncounter + condition.kinds.sorted().joinToString(w.listSep) { plugName(it, lang) }
            is UnlockCondition.BatteryTemp -> when {
                condition.minC != null && condition.maxC != null ->
                    w.bTempBetween.format(formatTemp(condition.minC), formatTemp(condition.maxC))
                condition.minC != null -> w.bTempAbove.format(formatTemp(condition.minC))
                condition.maxC != null -> w.bTempBelow.format(formatTemp(condition.maxC))
                else -> w.bTempAny
            }
            is UnlockCondition.Orientation -> if (condition.isLandscape) w.landscape else w.portrait
            is UnlockCondition.SpeedRange -> when {
                condition.minKmh != null && condition.maxKmh != null ->
                    w.speedBetweenFmt.format(condition.minKmh, condition.maxKmh)
                condition.minKmh != null -> w.speedAboveFmt.format(condition.minKmh)
                condition.maxKmh != null -> w.speedBelowFmt.format(condition.maxKmh)
                else -> w.speedAny
            }
            is UnlockCondition.SsidBssidMatch -> w.bssidAt.format(condition.bssids.sorted().joinToString(w.listSep))
            is UnlockCondition.BluetoothDevice -> w.btAt.format(condition.deviceNames.sorted().joinToString(w.listSep))
            is UnlockCondition.ProximityCovered -> w.proximity
            is UnlockCondition.FreshBoot -> w.freshBootFmt.format(condition.withinMinutes)
            is UnlockCondition.InstalledApp -> w.installedFmt.format(condition.packageName)

            // ---- 储备池 v4：位置 / 天气 ----
            is UnlockCondition.AirQuality -> w.aqiFmt.format(condition.maxAqi)
            is UnlockCondition.WindDirection ->
                w.windEncounter + condition.dirs.sorted().joinToString(w.listSep) { windDirName(it, lang) }
            is UnlockCondition.Hemisphere -> if (condition.north) w.hemisphereNorth else w.hemisphereSouth
            is UnlockCondition.TempDelta -> w.tempDropFmt.format(formatTemp(condition.minDropC))
            is UnlockCondition.CityLocation -> w.cityAtFmt.format(condition.cityName, condition.radiusMeter)
            is UnlockCondition.RelativeAltitude ->
                if (condition.direction == LiftDirection.UP) {
                    w.relAltUpFmt.format(formatTemp(condition.deltaM))
                } else {
                    w.relAltDownFmt.format(formatTemp(condition.deltaM))
                }
            is UnlockCondition.PrecipitationProbability -> w.precipProbFmt.format(condition.minProb)

            // ---- 储备池 v4：应用内 ----
            is UnlockCondition.WatchDurationAtLeast -> w.watchFmt.format(condition.seconds / 60)
            is UnlockCondition.ReadCountAtLeast -> w.readCountFmt.format(condition.count)
            is UnlockCondition.DestroyCountAtLeast -> w.destroyCountFmt.format(condition.count)
            is UnlockCondition.OtherCapsuleStillLocked -> w.otherStillLocked
            is UnlockCondition.BackupDone -> w.backupDone
            is UnlockCondition.TotalCreatedCount -> w.totalCreatedFmt.format(condition.count)
            is UnlockCondition.SameDayAsCapsuleRead -> w.sameDayRead
            is UnlockCondition.DaysSinceCapsuleRead -> w.daysSinceReadFmt.format(condition.days)
            is UnlockCondition.WidgetBound -> w.widgetBound
            is UnlockCondition.TodayOpenCount -> w.todayOpenFmt.format(condition.count)

            // ---- 储备池 v4：现场挑战 ----
            is UnlockCondition.GesturePattern -> w.gesturePattern
            is UnlockCondition.WalkStepsNow -> w.walkNowFmt.format(condition.steps)
            is UnlockCondition.SpinPhone -> w.spinFmt.format(condition.degrees)
            is UnlockCondition.VolumeKeyCombo -> w.volumeKeysFmt.format(condition.holdSeconds)
            is UnlockCondition.StayStill -> w.stayStillFmt.format(condition.holdSeconds)
            is UnlockCondition.LiftHighLowerLow ->
                if (condition.direction == LiftDirection.UP) {
                    w.liftUpFmt.format(formatTemp(condition.meters))
                } else {
                    w.liftDownFmt.format(formatTemp(condition.meters))
                }
            is UnlockCondition.VoicePassword -> w.voiceFmt
            is UnlockCondition.TapCount -> w.tapFmt.format(condition.taps)
            is UnlockCondition.ClimbFloors -> w.climbFmt.format(condition.floors)
            is UnlockCondition.ScanQr ->
                if (condition.expectedPayload != null) w.scanQrPaired else w.scanQr
            is UnlockCondition.ProofOfWork -> w.powFmt.format(condition.difficulty)
        }
    }

    /** SunPhaseKind → 名称 */
    fun sunName(kind: SunPhaseKind, lang: Lang = Lang.ZH_HANS): String = when (kind) {
        SunPhaseKind.SUNRISE -> words(lang).sunRise
        SunPhaseKind.DAY -> words(lang).sunDay
        SunPhaseKind.SUNSET -> words(lang).sunSet
        SunPhaseKind.NIGHT -> words(lang).sunNight
    }

    /** MoonPhaseKind → 名称 */
    fun moonName(kind: MoonPhaseKind, lang: Lang = Lang.ZH_HANS): String = when (kind) {
        MoonPhaseKind.NEW -> words(lang).moonNew
        MoonPhaseKind.WAXING_CRESCENT -> words(lang).moonWaxCrescent
        MoonPhaseKind.FIRST_QUARTER -> words(lang).moonFirstQ
        MoonPhaseKind.WAXING_GIBBOUS -> words(lang).moonWaxGib
        MoonPhaseKind.FULL -> words(lang).moonFull
        MoonPhaseKind.WANING_GIBBOUS -> words(lang).moonWanGib
        MoonPhaseKind.LAST_QUARTER -> words(lang).moonLastQ
        MoonPhaseKind.WANING_CRESCENT -> words(lang).moonWanCrescent
    }

    /** MeteorShowerKind → 名称（顺序即枚举序） */
    fun meteorName(kind: MeteorShowerKind, lang: Lang = Lang.ZH_HANS): String =
        words(lang).meteorNames[kind.ordinal]

    /** 农历节日预设（月, 日），顺序与词表 lunarFestNames 一致 */
    private val LUNAR_FESTIVAL_MONTH_DAYS =
        listOf(1 to 1, 1 to 15, 5 to 5, 7 to 7, 8 to 15, 9 to 9, 12 to 8)

    /** 农历节日预设：名称 + 农历月日（创建表单预设用） */
    fun lunarFestivals(lang: Lang = Lang.ZH_HANS): List<Triple<String, Int, Int>> =
        words(lang).lunarFestNames.mapIndexed { index, name ->
            val (month, day) = LUNAR_FESTIVAL_MONTH_DAYS[index]
            Triple(name, month, day)
        }

    /** WeatherMetricKind → 名称 */
    fun metricName(kind: WeatherMetricKind, lang: Lang = Lang.ZH_HANS): String = when (kind) {
        WeatherMetricKind.HUMIDITY -> words(lang).metricHumidity
        WeatherMetricKind.WIND -> words(lang).metricWind
        WeatherMetricKind.PRESSURE -> words(lang).metricPressure
        WeatherMetricKind.UV -> words(lang).metricUv
    }

    /** 指标数值格式化（湿度 %、风速 km/h、气压 hPa、紫外线指数与海拔 m 无单位后缀差异；单位全语言通用，无 lang 参数） */
    fun formatMetric(kind: WeatherMetricKind, value: Double): String {
        val num = if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)
        return when (kind) {
            WeatherMetricKind.HUMIDITY -> "$num%"
            WeatherMetricKind.WIND -> "$num km/h"
            WeatherMetricKind.PRESSURE -> "$num hPa"
            WeatherMetricKind.UV -> num
        }
    }

    /** MotionKind → 名称 */
    fun motionName(kind: MotionKind, lang: Lang = Lang.ZH_HANS): String = when (kind) {
        MotionKind.STILL -> words(lang).motionStill
        MotionKind.WALKING -> words(lang).motionWalking
    }

    /** SolarTermKind → 名称（顺序即黄经 15° 分档序，0 = 春分） */
    fun solarTermName(kind: SolarTermKind, lang: Lang = Lang.ZH_HANS): String =
        words(lang).solarTermNames[kind.ordinal]

    /** ZodiacKind → 名称 */
    fun zodiacName(kind: ZodiacKind, lang: Lang = Lang.ZH_HANS): String =
        words(lang).zodiacNames[kind.ordinal]

    /** SeasonKind → 名称 */
    fun seasonName(kind: SeasonKind, lang: Lang = Lang.ZH_HANS): String =
        words(lang).seasonNames[kind.ordinal]

    /** PlugKind → 名称 */
    fun plugName(kind: PlugKind, lang: Lang = Lang.ZH_HANS): String =
        words(lang).plugNames[kind.ordinal]

    /** WindDirKind → 名称（顺序即枚举序 N/NE/E/SE/S/SW/W/NW） */
    fun windDirName(kind: WindDirKind, lang: Lang = Lang.ZH_HANS): String =
        words(lang).windDirNames[kind.ordinal]

    /** PoseKind → 名称 */
    fun poseName(kind: PoseKind, lang: Lang = Lang.ZH_HANS): String =
        words(lang).poseNames[kind.ordinal]

    /** 当日分钟数 → "HH:mm"（日出钟点区间句用） */
    fun formatMinuteOfDay(minuteOfDay: Int): String =
        "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    /** 气温 → 整数省小数（30.0 → "30"，-5.5 → "-5.5"） */
    fun formatTemp(c: Double): String =
        if (c == c.toLong().toDouble()) c.toLong().toString() else c.toString()

    /** 日期 → "2026.08.12"（三语共用数字格式） */
    fun formatDate(date: LocalDate): String =
        "%04d.%02d.%02d".format(date.year, date.monthValue, date.dayOfMonth)

    /** DayOfWeek → 星期全称 */
    fun dayName(day: DayOfWeek, lang: Lang = Lang.ZH_HANS): String =
        words(lang).weekdayNames[day.ordinal]

    /** DayOfWeek → 单字短标签（周选择 pill；替代旧的 removePrefix 硬解） */
    fun dayShortName(day: DayOfWeek, lang: Lang = Lang.ZH_HANS): String =
        words(lang).weekdayShorts[day.ordinal]

    /** NetType → 网络状态全称 */
    fun netName(type: NetType, lang: Lang = Lang.ZH_HANS): String =
        when (type) {
            NetType.WIFI -> words(lang).netWifi
            NetType.CELLULAR -> words(lang).netCellular
            NetType.NONE -> words(lang).netNone
        }

    /** NetType → 选择 pill 短标签（替代旧的 removePrefix 硬解） */
    fun netShortName(type: NetType, lang: Lang = Lang.ZH_HANS): String =
        when (type) {
            NetType.WIFI -> words(lang).netWifiShort
            NetType.CELLULAR -> words(lang).netCellularShort
            NetType.NONE -> words(lang).netNone
        }

    /** WeatherType 枚举名 → 天气描述（未知名称返回 null，由调用方过滤） */
    fun weatherName(typeName: String, lang: Lang = Lang.ZH_HANS): String? =
        when (typeName) {
            "CLEAR" -> words(lang).weatherClear
            "CLOUDY" -> words(lang).weatherCloudy
            "FOG" -> words(lang).weatherFog
            "DRIZZLE" -> words(lang).weatherDrizzle
            "RAIN" -> words(lang).weatherRain
            "SNOW" -> words(lang).weatherSnow
            "THUNDER" -> words(lang).weatherThunder
            else -> null
        }

    private fun words(lang: Lang): ConditionWords = when (lang) {
        Lang.ZH_HANS -> ZH_WORDS
        Lang.ZH_HANT -> ZH_HANT_WORDS
        Lang.EN -> EN_WORDS
    }

    private val ZH_WORDS = ConditionWords(
        arriveAt = "到达 %s",
        sealedDays = "封存满 %d 天",
        everyWeekday = "每逢",
        listSep = "、",
        dailyRange = "每天 %d 点到 %d 点",
        dailyRangeCrossMidnight = "每天 %d 点到次日 %d 点",
        batteryBetween = "电量在 %d%% 到 %d%% 之间",
        batteryAbove = "电量高于 %d%%",
        batteryBelow = "电量低于 %d%%",
        batteryAny = "电量不限",
        charging = "正在充电",
        notCharging = "未在充电",
        stepsOver = "今日步数超过 %d 步",
        stepsBetween = "今日步数在 %d 步到 %d 步之间",
        stepsBelow = "今日步数低于 %d 步",
        stepsAny = "今日步数不限",
        netOr = " 或 ",
        netAny = "网络状态不限",
        netWifi = "连接 Wi-Fi",
        netWifiShort = "Wi-Fi",
        netCellular = "使用移动数据",
        netCellularShort = "移动数据",
        netNone = "没有任何网络连接",
        atLocation = "到达指定地点（半径 %d 米）",
        weatherEncounter = "遇到",
        weatherAny = "任意天气",
        weatherClear = "天晴",
        weatherCloudy = "多云",
        weatherFog = "起雾",
        weatherDrizzle = "毛毛雨",
        weatherRain = "下雨",
        weatherSnow = "下雪",
        weatherThunder = "雷雨",
        tempBetween = "气温在 %s°C 到 %s°C 之间",
        tempAbove = "气温高于 %s°C",
        tempBelow = "气温低于 %s°C",
        tempAny = "气温不限",
        ssidAt = "连接到 Wi-Fi「%s」",
        streakFmt = $$"连续 %1$d 天每天超过 %2$d 步",
        momentAt = "到达 %s 及之后",
        elapsedMinutesFmt = "封存满 %d 分钟",
        monthlyDayFmt = "每逢每月 %d 号",
        yearlyFmt = $$"每年 %1$d 月 %2$d 日",
        awayFromFmt = "离开指定地点（半径 %d 米以外）",
        sunEncounter = "正值",
        sunRise = "日出时分",
        sunDay = "白天",
        sunSet = "日落时分",
        sunNight = "夜晚",
        metricBetween = $$"%1$s在 %2$s 到 %3$s 之间",
        metricAbove = $$"%1$s高于 %2$s",
        metricBelow = $$"%1$s低于 %2$s",
        metricAny = $$"%1$s不限",
        metricHumidity = "湿度",
        metricWind = "风速",
        metricPressure = "气压",
        metricUv = "紫外线指数",
        moonEncounter = "月相为",
        moonNew = "新月",
        moonWaxCrescent = "娥眉月",
        moonFirstQ = "上弦月",
        moonWaxGib = "盈凸月",
        moonFull = "满月",
        moonWanGib = "亏凸月",
        moonLastQ = "下弦月",
        moonWanCrescent = "残月",
        meteorEncounter = "正值",
        meteorNames = listOf(
            "象限仪座流星雨", "天琴座流星雨", "宝瓶座η流星雨", "宝瓶座δ南流星雨", "英仙座流星雨",
            "猎户座流星雨", "狮子座流星雨", "双子座流星雨", "小熊座流星雨",
        ),
        goldenHour = "正值金色时刻",
        ambientFmt = "环境光照度低于 %d lux",
        timezoneAway = "身处与封存时不同的时区",
        movingFmt = "正在移动（速度超过 %d km/h）",
        holdPressFmt = "长按屏幕不放 %d 秒",
        biometric = "用生物识别（指纹/面容）验证身份",
        photoKeepsake = "拍一张此刻的照片留念",
        lunarFmt = "每逢农历 %d 月 %d 日",
        lunarFestNames = listOf("春节", "元宵节", "端午节", "七夕节", "中秋节", "重阳节", "腊八节"),
        airplaneOn = "手机处于飞行模式",
        airplaneOff = "手机未开启飞行模式",
        musicOn = "手机正在播放音乐",
        musicOff = "手机没有在播放音乐",
        beforeNextAlarm = "下一个闹钟响起之前",
        powerSaveOn = "手机处于省电模式",
        powerSaveOff = "手机不在省电模式",
        silentOn = "手机静音（含振动）",
        silentOff = "手机未静音",
        headphoneOn = "耳机已连接",
        headphoneOff = "耳机未连接",
        motionEncounter = "正在",
        motionStill = "静止",
        motionWalking = "步行",
        compassFmt = $$"手机朝向 %1$d°（±%2$d°）",
        altitudeBetween = "海拔在 %s 到 %s 之间",
        altitudeAbove = "海拔高于 %s",
        altitudeBelow = "海拔低于 %s",
        altitudeAny = "海拔不限",
        openCountFmt = "累计打开时粒 %d 次",
        openStreakFmt = "连续打开时粒 %d 天",
        lastOpenFmt = "距上次打开超过 %d 天",
        capsuleCountFmt = "拥有至少 %d 颗胶囊",
        otherUnlocked = "另一颗胶囊已解锁",
        otherDestroyed = "另一颗胶囊已销毁",
        otherRead = "另一颗胶囊已开启阅读",
        viewCountFmt = "凝视这颗胶囊满 %d 次",
        questionFmt = "回答问题「%s」",
        puzzleFmt = "解开谜题「%s」",
        shakeFmt = "摇一摇手机 %d 下",
        flip = "把手机翻面",
        holdFmt = "屏幕朝下静置 %d 秒",
        nfcTap = "触碰一枚 NFC 标签",
        nfcTapPaired = "触碰绑定的那枚 NFC 卡",
        weekdayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日"),
        weekdayShorts = listOf("一", "二", "三", "四", "五", "六", "日"),
        // ---- 储备池 v4 ----
        solarEncounter = "正值",
        solarTermNames = listOf(
            "立春", "雨水", "惊蛰", "春分", "清明", "谷雨",
            "立夏", "小满", "芒种", "夏至", "小暑", "大暑",
            "立秋", "处暑", "白露", "秋分", "寒露", "霜降",
            "立冬", "小雪", "大雪", "冬至", "小寒", "大寒",
        ),
        roundDaysFmt = "封存天数恰逢 %d 的整数倍",
        seasonEncounter = "正值",
        seasonNames = listOf("春季", "夏季", "秋季", "冬季"),
        elapsedMonthsFmt = "封存满 %d 个月",
        nthWeekdayFmt = $$"每逢每月第 %1$d 个%2$s",
        yearlyNthFmt = $$"每年 %1$d 月第 %2$d 个%3$s",
        leapDay = "每逢 2 月 29 日（四年一遇）",
        lastDayOfMonth = "每逢每月最后一天",
        nthWeekdaySinceFmt = $$"封存满 %1$d 天后的第一个%2$s",
        zodiacEncounter = "太阳行至",
        zodiacNames = listOf(
            "白羊座", "金牛座", "双子座", "巨蟹座", "狮子座", "处女座",
            "天秤座", "天蝎座", "射手座", "摩羯座", "水瓶座", "双鱼座",
        ),
        dayLenBetween = "白昼在 %s 到 %s 小时之间",
        dayLenAbove = "白昼长于 %s 小时",
        dayLenBelow = "白昼短于 %s 小时",
        dayLenAny = "白昼长度不限",
        sunriseBetween = "日出在 %s 到 %s 之间",
        sunriseAfter = "日出晚于 %s",
        sunriseBefore = "日出早于 %s",
        sunriseAny = "日出钟点不限",
        lunarMonthFmt = "每逢农历 %d 月整月",
        monthlyDaysFmt = "每逢每月 %s 号",
        darkOn = "手机处于深色模式",
        darkOff = "手机未处于深色模式",
        dndOn = "手机处于勿扰模式",
        dndOff = "手机未开启勿扰模式",
        poseEncounter = "手机",
        poseNames = listOf("平放着", "直立着", "倒置着"),
        brightnessFmt = "屏幕亮度低于 %d",
        volumeMuted = "手机媒体音量为静音",
        volumeNotMuted = "手机媒体音量未静音",
        vpnOn = "VPN 已连接",
        vpnOff = "VPN 未连接",
        plugEncounter = "充电方式为",
        plugNames = listOf("电源适配器充电", "USB 充电", "无线充电"),
        bTempBetween = "电池温度在 %s°C 到 %s°C 之间",
        bTempAbove = "电池温度高于 %s°C",
        bTempBelow = "电池温度低于 %s°C",
        bTempAny = "电池温度不限",
        landscape = "手机处于横屏",
        portrait = "手机处于竖屏",
        speedBetweenFmt = $$"移动速度在 %1$d 到 %2$d km/h 之间",
        speedAboveFmt = "移动速度超过 %d km/h",
        speedBelowFmt = "移动速度低于 %d km/h",
        speedAny = "移动速度不限",
        bssidAt = "连接到指定路由器「%s」",
        btAt = "蓝牙连接到「%s」",
        proximity = "手捂住手机顶部（接近传感器被遮挡）",
        freshBootFmt = "距上次开机不足 %d 分钟",
        installedFmt = "已安装应用「%s」",
        aqiFmt = "空气质量指数（AQI）低于 %d",
        windEncounter = "刮",
        windDirNames = listOf("北风", "东北风", "东风", "东南风", "南风", "西南风", "西风", "西北风"),
        hemisphereNorth = "身处北半球",
        hemisphereSouth = "身处南半球",
        tempDropFmt = "气温比昨日低 %s°C 以上",
        cityAtFmt = "身处 %s（半径 %d 米）",
        relAltUpFmt = "身处比封存时高 %s 米以上",
        relAltDownFmt = "身处比封存时低 %s 米以上",
        precipProbFmt = "今日降水概率超过 %d%%",
        watchFmt = "凝视这颗胶囊累计满 %d 分钟",
        readCountFmt = "已开启阅读过 %d 颗胶囊",
        destroyCountFmt = "已送走（销毁）%d 颗胶囊",
        otherStillLocked = "另一颗指定胶囊仍处于锁定",
        backupDone = "完成过一次备份导出",
        totalCreatedFmt = "累计封存过 %d 颗胶囊",
        sameDayRead = "另一颗指定胶囊今日被开启阅读",
        daysSinceReadFmt = "另一颗指定胶囊被开启已满 %d 天",
        widgetBound = "时粒的小组件已钉在桌面上",
        todayOpenFmt = "今日打开时粒 %d 次",
        gesturePattern = "画出正确的手势图案（连接九宫格点位）",
        walkNowFmt = "当场走 %d 步",
        spinFmt = "把手机水平旋转累计 %d 度",
        volumeKeysFmt = "同时按住两个音量键 %d 秒",
        stayStillFmt = "让手机保持静止 %d 秒",
        liftUpFmt = "把手机举高 %s 米",
        liftDownFmt = "把手机放低 %s 米",
        voiceFmt = "说出正确的口令（语音识别）",
        tapFmt = "连续点击屏幕 %d 下",
        climbFmt = "当场爬上 %d 层楼",
        scanQr = "扫一枚二维码",
        scanQrPaired = "扫出约定的那枚二维码",
        powFmt = "完成算力挑战（%d 位前导零）",
    )

    private val ZH_HANT_WORDS = ConditionWords(
        arriveAt = "抵達 %s",
        sealedDays = "封存滿 %d 天",
        everyWeekday = "每逢",
        listSep = "、",
        dailyRange = "每天 %d 點到 %d 點",
        dailyRangeCrossMidnight = "每天 %d 點到次日 %d 點",
        batteryBetween = "電量在 %d%% 到 %d%% 之間",
        batteryAbove = "電量高於 %d%%",
        batteryBelow = "電量低於 %d%%",
        batteryAny = "電量不限",
        charging = "正在充電",
        notCharging = "未在充電",
        stepsOver = "今日步數超過 %d 步",
        stepsBetween = "今日步數在 %d 步到 %d 步之間",
        stepsBelow = "今日步數低於 %d 步",
        stepsAny = "今日步數不限",
        netOr = " 或 ",
        netAny = "網路狀態不限",
        netWifi = "連接 Wi-Fi",
        netWifiShort = "Wi-Fi",
        netCellular = "使用行動數據",
        netCellularShort = "行動數據",
        netNone = "沒有任何網路連線",
        atLocation = "抵達指定地點（半徑 %d 公尺）",
        weatherEncounter = "遇到",
        weatherAny = "任意天氣",
        weatherClear = "天晴",
        weatherCloudy = "多雲",
        weatherFog = "起霧",
        weatherDrizzle = "毛毛雨",
        weatherRain = "下雨",
        weatherSnow = "下雪",
        weatherThunder = "雷雨",
        tempBetween = "氣溫在 %s°C 到 %s°C 之間",
        tempAbove = "氣溫高於 %s°C",
        tempBelow = "氣溫低於 %s°C",
        tempAny = "氣溫不限",
        ssidAt = "連接到 Wi-Fi「%s」",
        streakFmt = $$"連續 %1$d 天每天超過 %2$d 步",
        momentAt = "抵達 %s 及之後",
        elapsedMinutesFmt = "封存滿 %d 分鐘",
        monthlyDayFmt = "每逢每月 %d 號",
        yearlyFmt = $$"每年 %1$d 月 %2$d 日",
        awayFromFmt = "離開指定地點（半徑 %d 公尺以外）",
        sunEncounter = "正值",
        sunRise = "日出時分",
        sunDay = "白天",
        sunSet = "日落時分",
        sunNight = "夜晚",
        metricBetween = $$"%1$s在 %2$s 到 %3$s 之間",
        metricAbove = $$"%1$s高於 %2$s",
        metricBelow = $$"%1$s低於 %2$s",
        metricAny = $$"%1$s不限",
        metricHumidity = "濕度",
        metricWind = "風速",
        metricPressure = "氣壓",
        metricUv = "紫外線指數",
        moonEncounter = "月相為",
        moonNew = "新月",
        moonWaxCrescent = "娥眉月",
        moonFirstQ = "上弦月",
        moonWaxGib = "盈凸月",
        moonFull = "滿月",
        moonWanGib = "虧凸月",
        moonLastQ = "下弦月",
        moonWanCrescent = "殘月",
        meteorEncounter = "正值",
        meteorNames = listOf(
            "象限儀座流星雨", "天琴座流星雨", "寶瓶座η流星雨", "寶瓶座δ南流星雨", "英仙座流星雨",
            "獵戶座流星雨", "獅子座流星雨", "雙子座流星雨", "小熊座流星雨",
        ),
        goldenHour = "正值金色時刻",
        ambientFmt = "環境光照度低於 %d lux",
        timezoneAway = "身處與封存時不同的時區",
        movingFmt = "正在移動（速度超過 %d km/h）",
        holdPressFmt = "長按螢幕不放 %d 秒",
        biometric = "用生物辨識（指紋/面孔）驗證身份",
        photoKeepsake = "拍一張此刻的照片留念",
        lunarFmt = "每逢農曆 %d 月 %d 日",
        lunarFestNames = listOf("春節", "元宵節", "端午節", "七夕節", "中秋節", "重陽節", "臘八節"),
        airplaneOn = "手機處於飛行模式",
        airplaneOff = "手機未開啟飛行模式",
        musicOn = "手機正在播放音樂",
        musicOff = "手機沒有在播放音樂",
        beforeNextAlarm = "下一個鬧鐘響起之前",
        powerSaveOn = "手機處於省電模式",
        powerSaveOff = "手機不在省電模式",
        silentOn = "手機靜音（含振動）",
        silentOff = "手機未靜音",
        headphoneOn = "耳機已連接",
        headphoneOff = "耳機未連接",
        motionEncounter = "正在",
        motionStill = "靜止",
        motionWalking = "步行",
        compassFmt = $$"手機朝向 %1$d°（±%2$d°）",
        altitudeBetween = "海拔在 %s 到 %s 之間",
        altitudeAbove = "海拔高於 %s",
        altitudeBelow = "海拔低於 %s",
        altitudeAny = "海拔不限",
        openCountFmt = "累計開啟時粒 %d 次",
        openStreakFmt = "連續開啟時粒 %d 天",
        lastOpenFmt = "距上次開啟超過 %d 天",
        capsuleCountFmt = "擁有至少 %d 顆膠囊",
        otherUnlocked = "另一顆膠囊已解鎖",
        otherDestroyed = "另一顆膠囊已銷毀",
        otherRead = "另一顆膠囊已開啟閱讀",
        viewCountFmt = "凝視這顆膠囊滿 %d 次",
        questionFmt = "回答問題「%s」",
        puzzleFmt = "解開謎題「%s」",
        shakeFmt = "搖一搖手機 %d 下",
        flip = "把手機翻面",
        holdFmt = "螢幕朝下靜置 %d 秒",
        nfcTap = "觸碰一枚 NFC 標籤",
        nfcTapPaired = "觸碰綁定的那枚 NFC 卡",
        weekdayNames = listOf("週一", "週二", "週三", "週四", "週五", "週六", "週日"),
        weekdayShorts = listOf("一", "二", "三", "四", "五", "六", "日"),
        // ---- 儲備池 v4 ----
        solarEncounter = "正值",
        solarTermNames = listOf(
            "立春", "雨水", "驚蟄", "春分", "清明", "穀雨",
            "立夏", "小滿", "芒種", "夏至", "小暑", "大暑",
            "立秋", "處暑", "白露", "秋分", "寒露", "霜降",
            "立冬", "小雪", "大雪", "冬至", "小寒", "大寒",
        ),
        roundDaysFmt = "封存天數恰逢 %d 的整數倍",
        seasonEncounter = "正值",
        seasonNames = listOf("春季", "夏季", "秋季", "冬季"),
        elapsedMonthsFmt = "封存滿 %d 個月",
        nthWeekdayFmt = $$"每逢每月第 %1$d 個%2$s",
        yearlyNthFmt = $$"每年 %1$d 月第 %2$d 個%3$s",
        leapDay = "每逢 2 月 29 日（四年一遇）",
        lastDayOfMonth = "每逢每月最後一天",
        nthWeekdaySinceFmt = $$"封存滿 %1$d 天後的第一個%2$s",
        zodiacEncounter = "太陽行至",
        zodiacNames = listOf(
            "白羊座", "金牛座", "雙子座", "巨蟹座", "獅子座", "處女座",
            "天秤座", "天蠍座", "射手座", "摩羯座", "水瓶座", "雙魚座",
        ),
        dayLenBetween = "白晝在 %s 到 %s 小時之間",
        dayLenAbove = "白晝長於 %s 小時",
        dayLenBelow = "白晝短於 %s 小時",
        dayLenAny = "白晝長度不限",
        sunriseBetween = "日出在 %s 到 %s 之間",
        sunriseAfter = "日出晚於 %s",
        sunriseBefore = "日出早於 %s",
        sunriseAny = "日出鐘點不限",
        lunarMonthFmt = "每逢農曆 %d 月整月",
        monthlyDaysFmt = "每逢每月 %s 號",
        darkOn = "手機處於深色模式",
        darkOff = "手機未處於深色模式",
        dndOn = "手機處於勿擾模式",
        dndOff = "手機未開啟勿擾模式",
        poseEncounter = "手機",
        poseNames = listOf("平放著", "直立著", "倒置著"),
        brightnessFmt = "螢幕亮度低於 %d",
        volumeMuted = "手機媒體音量為靜音",
        volumeNotMuted = "手機媒體音量未靜音",
        vpnOn = "VPN 已連接",
        vpnOff = "VPN 未連接",
        plugEncounter = "充電方式為",
        plugNames = listOf("電源轉接器充電", "USB 充電", "無線充電"),
        bTempBetween = "電池溫度在 %s°C 到 %s°C 之間",
        bTempAbove = "電池溫度高於 %s°C",
        bTempBelow = "電池溫度低於 %s°C",
        bTempAny = "電池溫度不限",
        landscape = "手機處於橫向",
        portrait = "手機處於直向",
        speedBetweenFmt = $$"移動速度在 %1$d 到 %2$d km/h 之間",
        speedAboveFmt = "移動速度超過 %d km/h",
        speedBelowFmt = "移動速度低於 %d km/h",
        speedAny = "移動速度不限",
        bssidAt = "連接到指定路由器「%s」",
        btAt = "藍牙連接到「%s」",
        proximity = "手摀住手機頂部（接近感測器被遮擋）",
        freshBootFmt = "距上次開機不足 %d 分鐘",
        installedFmt = "已安裝應用「%s」",
        aqiFmt = "空氣品質指標（AQI）低於 %d",
        windEncounter = "颳",
        windDirNames = listOf("北風", "東北風", "東風", "東南風", "南風", "西南風", "西風", "西北風"),
        hemisphereNorth = "身處北半球",
        hemisphereSouth = "身處南半球",
        tempDropFmt = "氣溫比昨日低 %s°C 以上",
        cityAtFmt = "身處 %s（半徑 %d 公尺）",
        relAltUpFmt = "身處比封存時高 %s 公尺以上",
        relAltDownFmt = "身處比封存時低 %s 公尺以上",
        precipProbFmt = "今日降水機率超過 %d%%",
        watchFmt = "凝視這顆膠囊累計滿 %d 分鐘",
        readCountFmt = "已開啟閱讀過 %d 顆膠囊",
        destroyCountFmt = "已送走（銷毀）%d 顆膠囊",
        otherStillLocked = "另一顆指定膠囊仍處於鎖定",
        backupDone = "完成過一次備份匯出",
        totalCreatedFmt = "累計封存過 %d 顆膠囊",
        sameDayRead = "另一顆指定膠囊今日被開啟閱讀",
        daysSinceReadFmt = "另一顆指定膠囊被開啟已滿 %d 天",
        widgetBound = "時粒的小工具已釘在桌面上",
        todayOpenFmt = "今日開啟時粒 %d 次",
        gesturePattern = "畫出正確的手勢圖案（連接九宮格點位）",
        walkNowFmt = "當場走 %d 步",
        spinFmt = "把手機水平旋轉累計 %d 度",
        volumeKeysFmt = "同時按住兩個音量鍵 %d 秒",
        stayStillFmt = "讓手機保持靜止 %d 秒",
        liftUpFmt = "把手機舉高 %s 公尺",
        liftDownFmt = "把手機放低 %s 公尺",
        voiceFmt = "說出正確的通關密語（語音辨識）",
        tapFmt = "連續點擊螢幕 %d 下",
        climbFmt = "當場爬上 %d 層樓",
        scanQr = "掃一枚 QR 碼",
        scanQrPaired = "掃出約定的那枚 QR 碼",
        powFmt = "完成算力挑戰（%d 位前導零）",
    )

    private val EN_WORDS = ConditionWords(
        arriveAt = "Arrive at %s",
        sealedDays = "Sealed for %d days",
        everyWeekday = "Every ",
        listSep = ", ",
        dailyRange = "Daily %d:00–%d:00",
        dailyRangeCrossMidnight = "Daily %d:00–next day %d:00",
        batteryBetween = "Battery between %d%% and %d%%",
        batteryAbove = "Battery above %d%%",
        batteryBelow = "Battery below %d%%",
        batteryAny = "Any battery level",
        charging = "While charging",
        notCharging = "While not charging",
        stepsOver = "Over %d steps today",
        stepsBetween = "Steps today between %d and %d",
        stepsBelow = "Under %d steps today",
        stepsAny = "Any step count today",
        netOr = " or ",
        netAny = "Any network state",
        netWifi = "Connected to Wi-Fi",
        netWifiShort = "Wi-Fi",
        netCellular = "Using mobile data",
        netCellularShort = "Mobile data",
        netNone = "No network connection",
        atLocation = "Arrive at the spot (within %d m)",
        weatherEncounter = "When ",
        weatherAny = "any weather",
        weatherClear = "sunny",
        weatherCloudy = "cloudy",
        weatherFog = "foggy",
        weatherDrizzle = "drizzling",
        weatherRain = "raining",
        weatherSnow = "snowing",
        weatherThunder = "thunderstorm",
        tempBetween = "Temperature between %s°C and %s°C",
        tempAbove = "Temperature above %s°C",
        tempBelow = "Temperature below %s°C",
        tempAny = "Any temperature",
        ssidAt = "Connected to Wi-Fi \"%s\"",
        streakFmt = $$"Over %2$d steps for %1$d days in a row",
        momentAt = "From %s onward",
        elapsedMinutesFmt = "Sealed for %d minutes",
        monthlyDayFmt = "On day %d of every month",
        yearlyFmt = $$"Every year on %1$d/%2$d",
        awayFromFmt = "Away from the spot (beyond %d m)",
        sunEncounter = "During ",
        sunRise = "sunrise",
        sunDay = "daytime",
        sunSet = "sunset",
        sunNight = "night",
        metricBetween = $$"%1$s between %2$s and %3$s",
        metricAbove = $$"%1$s above %2$s",
        metricBelow = $$"%1$s below %2$s",
        metricAny = $$"Any %1$s",
        metricHumidity = "humidity ",
        metricWind = "wind speed ",
        metricPressure = "pressure ",
        metricUv = "UV index ",
        moonEncounter = "Moon phase: ",
        moonNew = "new moon",
        moonWaxCrescent = "waxing crescent",
        moonFirstQ = "first quarter",
        moonWaxGib = "waxing gibbous",
        moonFull = "full moon",
        moonWanGib = "waning gibbous",
        moonLastQ = "last quarter",
        moonWanCrescent = "waning crescent",
        meteorEncounter = "During ",
        meteorNames = listOf(
            "the Quadrantids", "the Lyrids", "the Eta Aquariids", "the Delta Aquariids", "the Perseids",
            "the Orionids", "the Leonids", "the Geminids", "the Ursids",
        ),
        goldenHour = "During golden hour",
        ambientFmt = "Ambient light below %d lux",
        timezoneAway = "In a different timezone than when sealed",
        movingFmt = "On the move (over %d km/h)",
        holdPressFmt = "Press and hold the screen for %d seconds",
        biometric = "Verify with biometrics (fingerprint/face)",
        photoKeepsake = "Take a photo of this moment",
        lunarFmt = "Every year on lunar month %d, day %d",
        lunarFestNames = listOf(
            "Chinese New Year", "Lantern Festival", "Dragon Boat Festival", "Qixi Festival",
            "Mid-Autumn Festival", "Double Ninth Festival", "Laba Festival",
        ),
        airplaneOn = "Phone is in airplane mode",
        airplaneOff = "Phone is not in airplane mode",
        musicOn = "Music is playing",
        musicOff = "No music is playing",
        beforeNextAlarm = "Before the next alarm goes off",
        powerSaveOn = "Phone is in power-save mode",
        powerSaveOff = "Phone is not in power-save mode",
        silentOn = "Phone is silenced (incl. vibrate)",
        silentOff = "Phone is not silenced",
        headphoneOn = "Headphones connected",
        headphoneOff = "No headphones connected",
        motionEncounter = "While ",
        motionStill = "still",
        motionWalking = "walking",
        compassFmt = $$"Phone facing %1$d° (±%2$d°)",
        altitudeBetween = "Altitude between %s and %s",
        altitudeAbove = "Altitude above %s",
        altitudeBelow = "Altitude below %s",
        altitudeAny = "Any altitude",
        openCountFmt = "Opened Timart %d times",
        openStreakFmt = "Opened Timart %d days in a row",
        lastOpenFmt = "More than %d days since last open",
        capsuleCountFmt = "Own at least %d capsules",
        otherUnlocked = "Another capsule has been unlocked",
        otherDestroyed = "Another capsule has been destroyed",
        otherRead = "Another capsule has been opened and read",
        viewCountFmt = "Gazed at this capsule %d times",
        questionFmt = "Answer the question \"%s\"",
        puzzleFmt = "Solve the riddle \"%s\"",
        shakeFmt = "Shake the phone %d times",
        flip = "Flip the phone over",
        holdFmt = "Keep it face-down for %d seconds",
        nfcTap = "Tap an NFC tag",
        nfcTapPaired = "Tap the paired NFC card",
        weekdayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"),
        weekdayShorts = listOf("M", "T", "W", "T", "F", "S", "S"),
        // ---- Reserve pool v4 ----
        solarEncounter = "During ",
        solarTermNames = listOf(
            "Start of Spring", "Rain Water", "Awakening of Insects", "Spring Equinox", "Pure Brightness", "Grain Rain",
            "Start of Summer", "Grain Buds", "Grain in Ear", "Summer Solstice", "Minor Heat", "Major Heat",
            "Start of Autumn", "End of Heat", "White Dew", "Autumn Equinox", "Cold Dew", "Frost's Descent",
            "Start of Winter", "Minor Snow", "Major Snow", "Winter Solstice", "Minor Cold", "Major Cold",
        ),
        roundDaysFmt = "On a day a multiple of %d since sealing",
        seasonEncounter = "During ",
        seasonNames = listOf("spring", "summer", "autumn", "winter"),
        elapsedMonthsFmt = "Sealed for %d months",
        nthWeekdayFmt = $$"Every month on the %1$d-th %2$s",
        yearlyNthFmt = $$"Every year in month %1$d on the %2$d-th %3$s",
        leapDay = "On February 29 (once every four years)",
        lastDayOfMonth = "On the last day of every month",
        nthWeekdaySinceFmt = $$"On the first %2$s after %1$d days sealed",
        zodiacEncounter = "While the sun is in ",
        zodiacNames = listOf(
            "Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo",
            "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces",
        ),
        dayLenBetween = "Daylight between %s and %s hours",
        dayLenAbove = "Daylight longer than %s hours",
        dayLenBelow = "Daylight shorter than %s hours",
        dayLenAny = "Any daylight length",
        sunriseBetween = "Sunrise between %s and %s",
        sunriseAfter = "Sunrise after %s",
        sunriseBefore = "Sunrise before %s",
        sunriseAny = "Any sunrise time",
        lunarMonthFmt = "During lunar month %d",
        monthlyDaysFmt = "On day %s of every month",
        darkOn = "Phone is in dark theme",
        darkOff = "Phone is not in dark theme",
        dndOn = "Do-not-disturb is on",
        dndOff = "Do-not-disturb is off",
        poseEncounter = "While the phone is ",
        poseNames = listOf("lying flat", "upright", "upside down"),
        brightnessFmt = "Screen brightness below %d",
        volumeMuted = "Media volume is muted",
        volumeNotMuted = "Media volume is not muted",
        vpnOn = "VPN is connected",
        vpnOff = "VPN is not connected",
        plugEncounter = "While charging via ",
        plugNames = listOf("wall adapter", "USB", "wireless charger"),
        bTempBetween = "Battery temperature between %s°C and %s°C",
        bTempAbove = "Battery temperature above %s°C",
        bTempBelow = "Battery temperature below %s°C",
        bTempAny = "Any battery temperature",
        landscape = "Phone is in landscape",
        portrait = "Phone is in portrait",
        speedBetweenFmt = $$"Moving between %1$d and %2$d km/h",
        speedAboveFmt = "Moving over %d km/h",
        speedBelowFmt = "Moving under %d km/h",
        speedAny = "Any moving speed",
        bssidAt = "Connected to the designated router (%s)",
        btAt = "Bluetooth connected to %s",
        proximity = "Palm covering the top of the phone (proximity sensor blocked)",
        freshBootFmt = "Within %d minutes of the last reboot",
        installedFmt = "App \"%s\" is installed",
        aqiFmt = "Air quality index (AQI) below %d",
        windEncounter = "While the wind blows from ",
        windDirNames = listOf(
            "the north", "the northeast", "the east", "the southeast",
            "the south", "the southwest", "the west", "the northwest",
        ),
        hemisphereNorth = "In the northern hemisphere",
        hemisphereSouth = "In the southern hemisphere",
        tempDropFmt = "Temperature %s°C below yesterday",
        cityAtFmt = "In %s (within %d m)",
        relAltUpFmt = "%s m higher than when sealed",
        relAltDownFmt = "%s m lower than when sealed",
        precipProbFmt = "Precipitation probability today over %d%%",
        watchFmt = "Gazed at this capsule for %d minutes in total",
        readCountFmt = "Opened and read %d capsules",
        destroyCountFmt = "Sent off (destroyed) %d capsules",
        otherStillLocked = "Another designated capsule is still locked",
        backupDone = "A backup has been exported at least once",
        totalCreatedFmt = "Sealed %d capsules in total",
        sameDayRead = "Another designated capsule was opened today",
        daysSinceReadFmt = "Another designated capsule was opened over %d days ago",
        widgetBound = "The Timart widget is pinned to the home screen",
        todayOpenFmt = "Opened Timart %d times today",
        gesturePattern = "Draw the right gesture pattern (connect the grid dots)",
        walkNowFmt = "Walk %d steps on the spot",
        spinFmt = "Spin the phone a total of %d degrees",
        volumeKeysFmt = "Hold both volume keys for %d seconds",
        stayStillFmt = "Keep the phone perfectly still for %d seconds",
        liftUpFmt = "Lift the phone %s m higher",
        liftDownFmt = "Lower the phone %s m",
        voiceFmt = "Speak the right passphrase (voice recognition)",
        tapFmt = "Tap the screen %d times in a row",
        climbFmt = "Climb %d floors on the spot",
        scanQr = "Scan a QR code",
        scanQrPaired = "Scan the agreed QR code",
        powFmt = "Complete the proof-of-work (%d leading zeros)",
    )
}

/** 单语言的条件句词表（[ConditionText] 内部使用） */
private data class ConditionWords(
    val arriveAt: String,
    val sealedDays: String,
    val everyWeekday: String,
    val listSep: String,
    val dailyRange: String,
    val dailyRangeCrossMidnight: String,
    val batteryBetween: String,
    val batteryAbove: String,
    val batteryBelow: String,
    val batteryAny: String,
    val charging: String,
    val notCharging: String,
    val stepsOver: String,
    val stepsBetween: String,
    val stepsBelow: String,
    val stepsAny: String,
    val netOr: String,
    val netAny: String,
    val netWifi: String,
    val netWifiShort: String,
    val netCellular: String,
    val netCellularShort: String,
    val netNone: String,
    val atLocation: String,
    val weatherEncounter: String,
    val weatherAny: String,
    val weatherClear: String,
    val weatherCloudy: String,
    val weatherFog: String,
    val weatherDrizzle: String,
    val weatherRain: String,
    val weatherSnow: String,
    val weatherThunder: String,
    val tempBetween: String,
    val tempAbove: String,
    val tempBelow: String,
    val tempAny: String,
    val ssidAt: String,
    val streakFmt: String,
    val momentAt: String,
    val elapsedMinutesFmt: String,
    val monthlyDayFmt: String,
    val yearlyFmt: String,
    val awayFromFmt: String,
    val sunEncounter: String,
    val sunRise: String,
    val sunDay: String,
    val sunSet: String,
    val sunNight: String,
    val metricBetween: String,
    val metricAbove: String,
    val metricBelow: String,
    val metricAny: String,
    val metricHumidity: String,
    val metricWind: String,
    val metricPressure: String,
    val metricUv: String,
    val moonEncounter: String,
    val moonNew: String,
    val moonWaxCrescent: String,
    val moonFirstQ: String,
    val moonWaxGib: String,
    val moonFull: String,
    val moonWanGib: String,
    val moonLastQ: String,
    val moonWanCrescent: String,
    val meteorEncounter: String,
    val meteorNames: List<String>,
    val goldenHour: String,
    val ambientFmt: String,
    val timezoneAway: String,
    val movingFmt: String,
    val holdPressFmt: String,
    val biometric: String,
    val photoKeepsake: String,
    val lunarFmt: String,
    val lunarFestNames: List<String>,
    val airplaneOn: String,
    val airplaneOff: String,
    val musicOn: String,
    val musicOff: String,
    val beforeNextAlarm: String,
    val powerSaveOn: String,
    val powerSaveOff: String,
    val silentOn: String,
    val silentOff: String,
    val headphoneOn: String,
    val headphoneOff: String,
    val motionEncounter: String,
    val motionStill: String,
    val motionWalking: String,
    val compassFmt: String,
    val altitudeBetween: String,
    val altitudeAbove: String,
    val altitudeBelow: String,
    val altitudeAny: String,
    val openCountFmt: String,
    val openStreakFmt: String,
    val lastOpenFmt: String,
    val capsuleCountFmt: String,
    val otherUnlocked: String,
    val otherDestroyed: String,
    val otherRead: String,
    val viewCountFmt: String,
    val questionFmt: String,
    val puzzleFmt: String,
    val shakeFmt: String,
    val flip: String,
    val holdFmt: String,
    val nfcTap: String,
    val nfcTapPaired: String,
    val weekdayNames: List<String>,
    val weekdayShorts: List<String>,
    // ---- 储备池 v4 ----
    val solarEncounter: String,
    val solarTermNames: List<String>,
    val roundDaysFmt: String,
    val seasonEncounter: String,
    val seasonNames: List<String>,
    val elapsedMonthsFmt: String,
    val nthWeekdayFmt: String,
    val yearlyNthFmt: String,
    val leapDay: String,
    val lastDayOfMonth: String,
    val nthWeekdaySinceFmt: String,
    val zodiacEncounter: String,
    val zodiacNames: List<String>,
    val dayLenBetween: String,
    val dayLenAbove: String,
    val dayLenBelow: String,
    val dayLenAny: String,
    val sunriseBetween: String,
    val sunriseAfter: String,
    val sunriseBefore: String,
    val sunriseAny: String,
    val lunarMonthFmt: String,
    val monthlyDaysFmt: String,
    val darkOn: String,
    val darkOff: String,
    val dndOn: String,
    val dndOff: String,
    val poseEncounter: String,
    val poseNames: List<String>,
    val brightnessFmt: String,
    val volumeMuted: String,
    val volumeNotMuted: String,
    val vpnOn: String,
    val vpnOff: String,
    val plugEncounter: String,
    val plugNames: List<String>,
    val bTempBetween: String,
    val bTempAbove: String,
    val bTempBelow: String,
    val bTempAny: String,
    val landscape: String,
    val portrait: String,
    val speedBetweenFmt: String,
    val speedAboveFmt: String,
    val speedBelowFmt: String,
    val speedAny: String,
    val bssidAt: String,
    val btAt: String,
    val proximity: String,
    val freshBootFmt: String,
    val installedFmt: String,
    val aqiFmt: String,
    val windEncounter: String,
    val windDirNames: List<String>,
    val hemisphereNorth: String,
    val hemisphereSouth: String,
    val tempDropFmt: String,
    val cityAtFmt: String,
    val relAltUpFmt: String,
    val relAltDownFmt: String,
    val precipProbFmt: String,
    val watchFmt: String,
    val readCountFmt: String,
    val destroyCountFmt: String,
    val otherStillLocked: String,
    val backupDone: String,
    val totalCreatedFmt: String,
    val sameDayRead: String,
    val daysSinceReadFmt: String,
    val widgetBound: String,
    val todayOpenFmt: String,
    val gesturePattern: String,
    val walkNowFmt: String,
    val spinFmt: String,
    val volumeKeysFmt: String,
    val stayStillFmt: String,
    val liftUpFmt: String,
    val liftDownFmt: String,
    val voiceFmt: String,
    val tapFmt: String,
    val climbFmt: String,
    val scanQr: String,
    val scanQrPaired: String,
    val powFmt: String,
)

/**
 * 判定原因文案（JudgeResult.reason 统一取值来源）。
 * [DEPENDENCY_DESTROYED] 等简中常量为既有取值（单测基准）；
 * 展示侧经 [forLang] 按界面语言取词，判定与显示须同语言。
 */
object JudgeReasons {
    const val DEPENDENCY_DESTROYED = "依赖胶囊已销毁，此胶囊无法再解锁"
    const val DEPENDENCY_LOCKED = "依赖胶囊尚未解锁"
    const val DEPENDENCY_NOT_FOUND = "依赖的胶囊不存在"
    const val GPS_BACKGROUND = "打开 App 后检测位置条件"
    const val GPS_NO_PERMISSION = "位置权限未授予，请到系统设置中开启"
    const val GPS_NO_FIX = "暂未获取到位置信息，请稍后重试"
    const val WEATHER_FAILED = "暂时无法获取天气，请检查网络后重试"
    const val WEATHER_NO_SNAPSHOT = "未选择快照城市，无法判定天气条件"
    const val STEP_UNAVAILABLE = "该设备不支持此条件或未授予活动记录权限"
    const val BATTERY_UNKNOWN = "暂时无法读取电量"
    const val SSID_NO_PERMISSION = "位置权限未授予，无法识别 Wi-Fi 名称，请到系统设置中开启"
    const val SSID_LOCATION_OFF = "系统定位服务未开启，无法识别 Wi-Fi 名称"
    const val SSID_NOT_CONNECTED = "当前未连接 Wi-Fi"
    const val STREAK_NO_RECORD = "此前天数的步数记录不足，请保持每天打开应用以累积记录"
    const val CHALLENGE_PENDING = "此条件需在打开胶囊时当场完成挑战"
    const val CHALLENGE_WRONG = "挑战未通过，请重试"
    const val ALARM_NOT_SET = "未设置任何闹钟"
    const val METRIC_UNAVAILABLE = "该天气指标暂不可用"
    const val MOTION_UNAVAILABLE = "运动状态不可用（设备不支持或未授权活动记录）"
    const val COMPASS_UNAVAILABLE = "指南针不可用（设备无磁力计）"
    const val ALTITUDE_UNAVAILABLE = "海拔不可用（设备无气压计）"
    const val NO_OPEN_RECORD = "还没有打开时粒的记录"
    const val AMBIENT_LIGHT_UNAVAILABLE = "环境光传感器不可用（设备无光线传感器）"

    /** 储备池 v4 新增判定原因 */
    const val DEVICE_STATE_UNAVAILABLE = "系统状态不可用（设备不支持或读取失败）"
    const val ENV_SENSOR_UNAVAILABLE = "传感器不可用（设备无对应硬件）"
    const val BT_NO_PERMISSION = "附近设备权限未授予，无法识别蓝牙设备，请到系统设置中开启"
    const val AIR_QUALITY_FAILED = "暂时无法获取空气质量，请检查网络后重试"

    /** 按界面语言取判定原因词表（判定入口与展示比较两侧同源） */
    fun forLang(lang: Lang): JudgeReasonTexts = when (lang) {
        Lang.ZH_HANS -> ZH
        Lang.ZH_HANT -> ZH_HANT
        Lang.EN -> EN
    }

    private val ZH = JudgeReasonTexts(
        dependencyDestroyed = DEPENDENCY_DESTROYED,
        dependencyLocked = DEPENDENCY_LOCKED,
        dependencyNotFound = DEPENDENCY_NOT_FOUND,
        gpsBackground = GPS_BACKGROUND,
        gpsNoPermission = GPS_NO_PERMISSION,
        gpsNoFix = GPS_NO_FIX,
        weatherFailed = WEATHER_FAILED,
        weatherNoSnapshot = WEATHER_NO_SNAPSHOT,
        stepUnavailable = STEP_UNAVAILABLE,
        batteryUnknown = BATTERY_UNKNOWN,
        ssidNoPermission = SSID_NO_PERMISSION,
        ssidLocationOff = SSID_LOCATION_OFF,
        ssidNotConnected = SSID_NOT_CONNECTED,
        streakNoRecord = STREAK_NO_RECORD,
        challengePending = CHALLENGE_PENDING,
        challengeWrong = CHALLENGE_WRONG,
        alarmNotSet = ALARM_NOT_SET,
        metricUnavailable = METRIC_UNAVAILABLE,
        motionUnavailable = MOTION_UNAVAILABLE,
        compassUnavailable = COMPASS_UNAVAILABLE,
        altitudeUnavailable = ALTITUDE_UNAVAILABLE,
        noOpenRecord = NO_OPEN_RECORD,
        ambientLightUnavailable = AMBIENT_LIGHT_UNAVAILABLE,
        deviceStateUnavailable = DEVICE_STATE_UNAVAILABLE,
        envSensorUnavailable = ENV_SENSOR_UNAVAILABLE,
        btNoPermission = BT_NO_PERMISSION,
        airQualityFailed = AIR_QUALITY_FAILED,
    )

    private val ZH_HANT = JudgeReasonTexts(
        dependencyDestroyed = "依賴膠囊已銷毀，此膠囊無法再解鎖",
        dependencyLocked = "依賴膠囊尚未解鎖",
        dependencyNotFound = "依賴的膠囊不存在",
        gpsBackground = "開啟 App 後偵測位置條件",
        gpsNoPermission = "位置權限未授予，請到系統設定中開啟",
        gpsNoFix = "暫未取得位置資訊，請稍後重試",
        weatherFailed = "暫時無法取得天氣，請檢查網路後重試",
        weatherNoSnapshot = "未選擇快照城市，無法判定天氣條件",
        stepUnavailable = "此裝置不支援此條件，或未授予活動記錄權限",
        batteryUnknown = "暫時無法讀取電量",
        ssidNoPermission = "位置權限未授予，無法識別 Wi-Fi 名稱，請到系統設定中開啟",
        ssidLocationOff = "系統定位服務未開啟，無法識別 Wi-Fi 名稱",
        ssidNotConnected = "當前未連接 Wi-Fi",
        streakNoRecord = "此前天數的步數記錄不足，請保持每天開啟應用以累積記錄",
        challengePending = "此條件需在開啟膠囊時當場完成挑戰",
        challengeWrong = "挑戰未通過，請重試",
        alarmNotSet = "未設定任何鬧鐘",
        metricUnavailable = "該天氣指標暫不可用",
        motionUnavailable = "運動狀態不可用（裝置不支援或未授權活動記錄）",
        compassUnavailable = "指南針不可用（裝置無磁力計）",
        altitudeUnavailable = "海拔不可用（裝置無氣壓計）",
        noOpenRecord = "還沒有開啟時粒的記錄",
        ambientLightUnavailable = "環境光感測器不可用（裝置無光線感測器）",
        deviceStateUnavailable = "系統狀態不可用（裝置不支援或讀取失敗）",
        envSensorUnavailable = "感測器不可用（裝置無對應硬體）",
        btNoPermission = "附近裝置權限未授予，無法識別藍牙裝置，請到系統設定中開啟",
        airQualityFailed = "暫時無法取得空氣品質，請檢查網路後重試",
    )

    private val EN = JudgeReasonTexts(
        dependencyDestroyed = "The source capsule was destroyed; this capsule can no longer be unlocked",
        dependencyLocked = "The source capsule is still locked",
        dependencyNotFound = "The source capsule no longer exists",
        gpsBackground = "Location will be checked after you open the app",
        gpsNoPermission = "Location permission not granted; enable it in system settings",
        gpsNoFix = "No location fix yet; try again later",
        weatherFailed = "Couldn't fetch weather; check your network and retry",
        weatherNoSnapshot = "No snapshot city selected; weather can't be judged",
        stepUnavailable = "This device doesn't support the condition, or activity permission is missing",
        batteryUnknown = "Battery level unavailable right now",
        ssidNoPermission = "Location permission not granted; the Wi-Fi name can't be identified. Enable it in system settings",
        ssidLocationOff = "System location service is off; the Wi-Fi name can't be identified",
        ssidNotConnected = "Not connected to any Wi-Fi network",
        streakNoRecord = "Step history from previous days is incomplete; open the app daily to build up records",
        challengePending = "This condition requires completing a challenge right when opening the capsule",
        challengeWrong = "Challenge failed; try again",
        alarmNotSet = "No alarm is set",
        metricUnavailable = "This weather metric is unavailable right now",
        motionUnavailable = "Motion activity unavailable (unsupported device or missing permission)",
        compassUnavailable = "Compass unavailable (no magnetometer on this device)",
        altitudeUnavailable = "Altitude unavailable (no barometer on this device)",
        noOpenRecord = "No record of opening the app yet",
        ambientLightUnavailable = "Ambient light sensor unavailable (no light sensor on this device)",
        deviceStateUnavailable = "System state unavailable (unsupported device or read failure)",
        envSensorUnavailable = "Sensor unavailable (missing hardware on this device)",
        btNoPermission = "Nearby devices permission not granted; Bluetooth devices can't be identified. Enable it in system settings",
        airQualityFailed = "Couldn't fetch air quality; check your network and retry",
    )
}

/** 单语言的判定原因词表（[JudgeReasons.forLang] 返回值） */
data class JudgeReasonTexts(
    val dependencyDestroyed: String,
    val dependencyLocked: String,
    val dependencyNotFound: String,
    val gpsBackground: String,
    val gpsNoPermission: String,
    val gpsNoFix: String,
    val weatherFailed: String,
    val weatherNoSnapshot: String,
    val stepUnavailable: String,
    val batteryUnknown: String,
    val ssidNoPermission: String,
    val ssidLocationOff: String,
    val ssidNotConnected: String,
    val streakNoRecord: String,
    val challengePending: String,
    val challengeWrong: String,
    val alarmNotSet: String,
    val metricUnavailable: String,
    val motionUnavailable: String,
    val compassUnavailable: String,
    val altitudeUnavailable: String,
    val noOpenRecord: String,
    val ambientLightUnavailable: String,
    val deviceStateUnavailable: String,
    val envSensorUnavailable: String,
    val btNoPermission: String,
    val airQualityFailed: String,
)

/**
 * 条件大类（体验储备池 §5 年度星图报告用）：与创建页条件选择面板的分组口径一致
 * （分组归属对照 RulesStep.ConditionType.group；l10n 的 cat* 词条同语义——
 * domain 不得 import l10n，故此处自持一份三语名，见 [conditionKindName]）。
 */
enum class ConditionKind {
    TIME,
    DEVICE,
    NET,
    USAGE,
    CHALLENGE,
}

/** UnlockCondition → 大类归属（与选择面板分组同口径，新增条件须同步补分支） */
fun conditionKind(condition: UnlockCondition): ConditionKind = when (condition) {
    is UnlockCondition.FixedDate,
    is UnlockCondition.MinElapsedDay,
    is UnlockCondition.WeekDay,
    is UnlockCondition.TimeRange,
    is UnlockCondition.FixedDateTime,
    is UnlockCondition.MinElapsedMinutes,
    is UnlockCondition.MonthlyDay,
    is UnlockCondition.YearlyDate,
    is UnlockCondition.LunarDate,
    is UnlockCondition.SolarTerm,
    is UnlockCondition.RoundDaysElapsed,
    is UnlockCondition.Season,
    is UnlockCondition.MinElapsedMonths,
    is UnlockCondition.NthWeekdayOfMonth,
    is UnlockCondition.YearlyNthWeekday,
    is UnlockCondition.LeapDay,
    is UnlockCondition.LastDayOfMonth,
    is UnlockCondition.NthWeekdaySince,
    is UnlockCondition.ZodiacSeason,
    is UnlockCondition.LunarMonthRange,
    is UnlockCondition.MonthlyDaySet,
    -> ConditionKind.TIME

    is UnlockCondition.BatteryLevel,
    is UnlockCondition.ChargingState,
    is UnlockCondition.StepCount,
    is UnlockCondition.StepStreak,
    is UnlockCondition.BeforeNextAlarm,
    is UnlockCondition.PowerSaveMode,
    is UnlockCondition.SilentMode,
    is UnlockCondition.HeadphoneConnected,
    is UnlockCondition.AirplaneMode,
    is UnlockCondition.MusicPlaying,
    is UnlockCondition.MotionActivity,
    is UnlockCondition.CompassHeading,
    is UnlockCondition.AltitudeRange,
    is UnlockCondition.DarkTheme,
    is UnlockCondition.DoNotDisturb,
    is UnlockCondition.DevicePose,
    is UnlockCondition.ScreenBrightness,
    is UnlockCondition.MediaVolume,
    is UnlockCondition.VpnActive,
    is UnlockCondition.PlugType,
    is UnlockCondition.BatteryTemp,
    is UnlockCondition.Orientation,
    is UnlockCondition.SsidBssidMatch,
    is UnlockCondition.ProximityCovered,
    is UnlockCondition.FreshBoot,
    is UnlockCondition.InstalledApp,
    is UnlockCondition.BluetoothDevice,
    -> ConditionKind.DEVICE

    is UnlockCondition.NetworkType,
    is UnlockCondition.SsidMatch,
    is UnlockCondition.GpsLocation,
    is UnlockCondition.AwayFromLocation,
    is UnlockCondition.WeatherType,
    is UnlockCondition.TemperatureThreshold,
    is UnlockCondition.SunPhase,
    is UnlockCondition.MoonPhase,
    is UnlockCondition.MeteorShower,
    is UnlockCondition.GoldenHour,
    is UnlockCondition.AmbientLight,
    is UnlockCondition.TimezoneChange,
    is UnlockCondition.MovingAboveSpeed,
    is UnlockCondition.WeatherMetric,
    is UnlockCondition.SpeedRange,
    is UnlockCondition.DayLength,
    is UnlockCondition.SunriseTimeRange,
    is UnlockCondition.Hemisphere,
    is UnlockCondition.CityLocation,
    is UnlockCondition.RelativeAltitude,
    is UnlockCondition.AirQuality,
    is UnlockCondition.WindDirection,
    is UnlockCondition.TempDelta,
    is UnlockCondition.PrecipitationProbability,
    -> ConditionKind.NET

    is UnlockCondition.OpenCountAtLeast,
    is UnlockCondition.OpenStreak,
    is UnlockCondition.DaysSinceLastOpen,
    is UnlockCondition.CapsuleCountAtLeast,
    is UnlockCondition.OtherCapsuleUnlocked,
    is UnlockCondition.OtherCapsuleDestroyed,
    is UnlockCondition.OtherCapsuleRead,
    is UnlockCondition.ViewCountAtLeast,
    is UnlockCondition.WatchDurationAtLeast,
    is UnlockCondition.ReadCountAtLeast,
    is UnlockCondition.DestroyCountAtLeast,
    is UnlockCondition.OtherCapsuleStillLocked,
    is UnlockCondition.BackupDone,
    is UnlockCondition.TotalCreatedCount,
    is UnlockCondition.SameDayAsCapsuleRead,
    is UnlockCondition.DaysSinceCapsuleRead,
    is UnlockCondition.WidgetBound,
    is UnlockCondition.TodayOpenCount,
    -> ConditionKind.USAGE

    else -> ConditionKind.CHALLENGE
}

/** 大类显示名（三语；语义对齐 l10n 的 cat* 词条，改名须两处同步） */
fun conditionKindName(kind: ConditionKind, lang: Lang): String = when (lang) {
    Lang.ZH_HANS -> when (kind) {
        ConditionKind.TIME -> "时间"
        ConditionKind.DEVICE -> "设备"
        ConditionKind.NET -> "网络与环境"
        ConditionKind.USAGE -> "应用内"
        ConditionKind.CHALLENGE -> "现场挑战"
    }
    Lang.ZH_HANT -> when (kind) {
        ConditionKind.TIME -> "時間"
        ConditionKind.DEVICE -> "裝置"
        ConditionKind.NET -> "網路與環境"
        ConditionKind.USAGE -> "應用內"
        ConditionKind.CHALLENGE -> "現場挑戰"
    }
    Lang.EN -> when (kind) {
        ConditionKind.TIME -> "Time"
        ConditionKind.DEVICE -> "Device"
        ConditionKind.NET -> "Network & environment"
        ConditionKind.USAGE -> "In-app"
        ConditionKind.CHALLENGE -> "Live challenge"
    }
}
