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
            is UnlockCondition.StepCount -> w.stepsOver.format(condition.minTodayStep)
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
                w.sunEncounter + condition.phases.map { sunName(it, lang) }.joinToString(w.listSep)
            is UnlockCondition.WeatherMetric -> {
                val name = metricName(condition.metric, lang)
                val minV = condition.min?.let { formatMetric(condition.metric, it, lang) }
                val maxV = condition.max?.let { formatMetric(condition.metric, it, lang) }
                when {
                    minV != null && maxV != null -> w.metricBetween.format(name, minV, maxV)
                    minV != null -> w.metricAbove.format(name, minV)
                    maxV != null -> w.metricBelow.format(name, maxV)
                    else -> w.metricAny.format(name)
                }
            }
            is UnlockCondition.MoonPhase ->
                w.moonEncounter + condition.phases.map { moonName(it, lang) }.joinToString(w.listSep)
            is UnlockCondition.BeforeNextAlarm -> w.beforeNextAlarm
            is UnlockCondition.PowerSaveMode -> if (condition.isActive) w.powerSaveOn else w.powerSaveOff
            is UnlockCondition.SilentMode -> if (condition.isSilent) w.silentOn else w.silentOff
            is UnlockCondition.HeadphoneConnected -> if (condition.isConnected) w.headphoneOn else w.headphoneOff
            is UnlockCondition.MotionActivity ->
                w.motionEncounter + condition.kinds.map { motionName(it, lang) }.joinToString(w.listSep)
            is UnlockCondition.CompassHeading -> w.compassFmt.format(condition.targetDeg, condition.toleranceDeg)
            is UnlockCondition.AltitudeRange -> when {
                condition.minM != null && condition.maxM != null ->
                    w.altitudeBetween.format(formatMetric(WeatherMetricKind.PRESSURE, condition.minM, lang), formatMetric(WeatherMetricKind.PRESSURE, condition.maxM, lang))
                condition.minM != null -> w.altitudeAbove.format(formatMetric(WeatherMetricKind.PRESSURE, condition.minM, lang))
                condition.maxM != null -> w.altitudeBelow.format(formatMetric(WeatherMetricKind.PRESSURE, condition.maxM, lang))
                else -> w.altitudeAny
            }
            is UnlockCondition.OpenCountAtLeast -> w.openCountFmt.format(condition.count)
            is UnlockCondition.OpenStreak -> w.openStreakFmt.format(condition.days)
            is UnlockCondition.DaysSinceLastOpen -> w.lastOpenFmt.format(condition.days)
            is UnlockCondition.CapsuleCountAtLeast -> w.capsuleCountFmt.format(condition.count)
            is UnlockCondition.OtherCapsuleUnlocked -> w.otherUnlocked
            is UnlockCondition.OtherCapsuleDestroyed -> w.otherDestroyed
            is UnlockCondition.QuestionAnswer -> w.questionFmt.format(condition.question)
            is UnlockCondition.PuzzleAnswer -> w.puzzleFmt.format(condition.question)
            is UnlockCondition.ShakeCount -> w.shakeFmt.format(condition.shakes)
            is UnlockCondition.FlipOrHold ->
                if (condition.gesture == GestureKind.FLIP) w.flip else w.holdFmt.format(condition.holdSeconds)
            is UnlockCondition.NfcTap ->
                if (condition.expectedPayload != null) w.nfcTapPaired else w.nfcTap
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

    /** WeatherMetricKind → 名称 */
    fun metricName(kind: WeatherMetricKind, lang: Lang = Lang.ZH_HANS): String = when (kind) {
        WeatherMetricKind.HUMIDITY -> words(lang).metricHumidity
        WeatherMetricKind.WIND -> words(lang).metricWind
        WeatherMetricKind.PRESSURE -> words(lang).metricPressure
        WeatherMetricKind.UV -> words(lang).metricUv
    }

    /** 指标数值格式化（湿度 %、风速 km/h、气压 hPa、紫外线指数与海拔 m 无单位后缀差异） */
    fun formatMetric(kind: WeatherMetricKind, value: Double, lang: Lang = Lang.ZH_HANS): String {
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
        streakFmt = "连续 %1\$d 天每天超过 %2\$d 步",
        momentAt = "到达 %s 及之后",
        elapsedMinutesFmt = "封存满 %d 分钟",
        monthlyDayFmt = "每逢每月 %d 号",
        yearlyFmt = "每年 %1\$d 月 %2\$d 日",
        awayFromFmt = "离开指定地点（半径 %d 米以外）",
        sunEncounter = "正值",
        sunRise = "日出时分",
        sunDay = "白天",
        sunSet = "日落时分",
        sunNight = "夜晚",
        metricBetween = "%1\$s在 %2\$s 到 %3\$s 之间",
        metricAbove = "%1\$s高于 %2\$s",
        metricBelow = "%1\$s低于 %2\$s",
        metricAny = "%1\$s不限",
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
        compassFmt = "手机朝向 %1\$d°（±%2\$d°）",
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
        questionFmt = "回答问题「%s」",
        puzzleFmt = "解开谜题「%s」",
        shakeFmt = "摇一摇手机 %d 下",
        flip = "把手机翻面",
        holdFmt = "屏幕朝下静置 %d 秒",
        nfcTap = "触碰一枚 NFC 标签",
        nfcTapPaired = "触碰绑定的那枚 NFC 卡",
        weekdayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日"),
        weekdayShorts = listOf("一", "二", "三", "四", "五", "六", "日"),
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
        streakFmt = "連續 %1\$d 天每天超過 %2\$d 步",
        momentAt = "抵達 %s 及之後",
        elapsedMinutesFmt = "封存滿 %d 分鐘",
        monthlyDayFmt = "每逢每月 %d 號",
        yearlyFmt = "每年 %1\$d 月 %2\$d 日",
        awayFromFmt = "離開指定地點（半徑 %d 公尺以外）",
        sunEncounter = "正值",
        sunRise = "日出時分",
        sunDay = "白天",
        sunSet = "日落時分",
        sunNight = "夜晚",
        metricBetween = "%1\$s在 %2\$s 到 %3\$s 之間",
        metricAbove = "%1\$s高於 %2\$s",
        metricBelow = "%1\$s低於 %2\$s",
        metricAny = "%1\$s不限",
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
        compassFmt = "手機朝向 %1\$d°（±%2\$d°）",
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
        questionFmt = "回答問題「%s」",
        puzzleFmt = "解開謎題「%s」",
        shakeFmt = "搖一搖手機 %d 下",
        flip = "把手機翻面",
        holdFmt = "螢幕朝下靜置 %d 秒",
        nfcTap = "觸碰一枚 NFC 標籤",
        nfcTapPaired = "觸碰綁定的那枚 NFC 卡",
        weekdayNames = listOf("週一", "週二", "週三", "週四", "週五", "週六", "週日"),
        weekdayShorts = listOf("一", "二", "三", "四", "五", "六", "日"),
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
        streakFmt = "Over %2\$d steps for %1\$d days in a row",
        momentAt = "From %s onward",
        elapsedMinutesFmt = "Sealed for %d minutes",
        monthlyDayFmt = "On day %d of every month",
        yearlyFmt = "Every year on %1\$d/%2\$d",
        awayFromFmt = "Away from the spot (beyond %d m)",
        sunEncounter = "During ",
        sunRise = "sunrise",
        sunDay = "daytime",
        sunSet = "sunset",
        sunNight = "night",
        metricBetween = "%1\$s between %2\$s and %3\$s",
        metricAbove = "%1\$s above %2\$s",
        metricBelow = "%1\$s below %2\$s",
        metricAny = "Any %1\$s",
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
        compassFmt = "Phone facing %1\$d° (±%2\$d°)",
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
        questionFmt = "Answer the question \"%s\"",
        puzzleFmt = "Solve the riddle \"%s\"",
        shakeFmt = "Shake the phone %d times",
        flip = "Flip the phone over",
        holdFmt = "Keep it face-down for %d seconds",
        nfcTap = "Tap an NFC tag",
        nfcTapPaired = "Tap the paired NFC card",
        weekdayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"),
        weekdayShorts = listOf("M", "T", "W", "T", "F", "S", "S"),
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
    val questionFmt: String,
    val puzzleFmt: String,
    val shakeFmt: String,
    val flip: String,
    val holdFmt: String,
    val nfcTap: String,
    val nfcTapPaired: String,
    val weekdayNames: List<String>,
    val weekdayShorts: List<String>,
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
)
