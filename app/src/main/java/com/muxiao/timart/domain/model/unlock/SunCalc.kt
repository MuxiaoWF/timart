package com.muxiao.timart.domain.model.unlock

import java.time.LocalDate
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * 日出日落天文计算（Wikipedia "Sunrise equation" 标准算法，纯 JVM 可单测）。
 * 输出 UTC 毫秒时间戳，判定侧直接与 nowMillis 比较，无时区转换歧义。
 */
object SunCalc {

    /** 当日日出/日落 UTC 毫秒；极昼极夜返回 null */
    fun sunTimesUtcMillis(lat: Double, lng: Double, date: LocalDate): Pair<Long, Long>? {
        // 1. 当日 00:00 UT 的儒略日 → 自 J2000 起的天数
        val jdate = date.toEpochDay() + 2440587.5
        val n = ceil(jdate - 2451545.0 + 0.0008)
        // 2. 平太阳时（经度修正，东经为正）
        val jStar = n - lng / 360.0
        // 3–4. 平近点角与中心差
        val m = mod360(357.5291 + 0.98560028 * jStar)
        val c = 1.9148 * sin(Math.toRadians(m)) +
            0.0200 * sin(Math.toRadians(2 * m)) +
            0.0003 * sin(Math.toRadians(3 * m))
        // 5. 黄经
        val lambda = mod360(m + c + 180.0 + 102.9372)
        // 6. 太阳过中天
        val jTransit = 2451545.0 + jStar + 0.0053 * sin(Math.toRadians(m)) -
            0.0069 * sin(Math.toRadians(2 * lambda))
        // 7. 太阳赤纬
        val declination = sin(Math.toRadians(lambda)) * sin(Math.toRadians(23.44))
        // 8. 时角（-0.83° 考虑大气折射与太阳视半径）
        val cosOmega = (sin(Math.toRadians(-0.83)) - sin(Math.toRadians(lat)) * declination) /
            (cos(Math.toRadians(lat)) * cos(asin(declination)))
        if (cosOmega < -1.0 || cosOmega > 1.0) return null // 极昼 / 极夜
        val omegaDeg = Math.toDegrees(acos(cosOmega))
        // 9. 升落时刻（儒略日）
        val jRise = jTransit - omegaDeg / 360.0
        val jSet = jTransit + omegaDeg / 360.0
        return Pair(julianToUtcMillis(jRise), julianToUtcMillis(jSet))
    }

    /** 当前太阳相位（nowMillis 为 UTC 毫秒；跨日边界用昨日落/明日升修正） */
    fun phaseOf(nowMillis: Long, lat: Double, lng: Double, today: LocalDate): SunPhaseKind {
        val todayTimes = sunTimesUtcMillis(lat, lng, today)
        if (todayTimes != null) {
            val (rise, set) = todayTimes
            if (kotlin.math.abs(nowMillis - rise) <= SUNRISE_WINDOW_MILLIS) return SunPhaseKind.SUNRISE
            if (kotlin.math.abs(nowMillis - set) <= SUNRISE_WINDOW_MILLIS) return SunPhaseKind.SUNSET
            if (nowMillis in rise..set) return SunPhaseKind.DAY
        }
        // 夜间可能跨日：早于今日日出时查昨日日落，晚于今日日落时查明日日出
        if (todayTimes == null || nowMillis < todayTimes.first) {
            val yesterdaySet = sunTimesUtcMillis(lat, lng, today.minusDays(1))?.second
            if (yesterdaySet != null && nowMillis >= yesterdaySet) return SunPhaseKind.NIGHT
            // 昨日极昼等边界：按夜间兜底
        }
        return SunPhaseKind.NIGHT
    }

    /**
     * 当前太阳高度角（度；低于地平线为负，极昼极夜照常返回计算值）。
     * 与 [sunTimesUtcMillis] 同源的中天/赤纬中间量，零权限纯计算。
     */
    fun solarElevationDeg(nowMillis: Long, lat: Double, lng: Double, date: LocalDate): Double {
        val jdate = date.toEpochDay() + 2440587.5
        val n = ceil(jdate - 2451545.0 + 0.0008)
        val jStar = n - lng / 360.0
        val m = mod360(357.5291 + 0.98560028 * jStar)
        val c = 1.9148 * sin(Math.toRadians(m)) +
            0.0200 * sin(Math.toRadians(2 * m)) +
            0.0003 * sin(Math.toRadians(3 * m))
        val lambda = mod360(m + c + 180.0 + 102.9372)
        val jTransit = 2451545.0 + jStar + 0.0053 * sin(Math.toRadians(m)) -
            0.0069 * sin(Math.toRadians(2 * lambda))
        val declination = sin(Math.toRadians(lambda)) * sin(Math.toRadians(23.44))
        // 时角：距中天的时间差换算为角度（cos 周期自然处理跨日）
        val hourAngleDeg = (nowMillis - julianToUtcMillis(jTransit)) / 86_400_000.0 * 360.0
        val sinAlt = sin(Math.toRadians(lat)) * declination +
            cos(Math.toRadians(lat)) * cos(asin(declination)) * cos(Math.toRadians(hourAngleDeg))
        return Math.toDegrees(asin(sinAlt.coerceIn(-1.0, 1.0)))
    }

    /**
     * 是否处于金色时刻：太阳已升起且高度角 ≤ 6°（摄影黄金时段通用定义）。
     * 时段长度随纬度/季节自然伸缩，无需固定分钟数；极夜恒为 false。
     */
    fun isGoldenHour(nowMillis: Long, lat: Double, lng: Double, date: LocalDate): Boolean {
        val alt = solarElevationDeg(nowMillis, lat, lng, date)
        return alt > 0.0 && alt <= GOLDEN_MAX_ELEVATION_DEG
    }

    private fun julianToUtcMillis(julianDay: Double): Long =
        ((julianDay - 2440587.5) * 86400000.0).toLong()

    private fun mod360(x: Double): Double = ((x % 360.0) + 360.0) % 360.0

    /** 日出/日落判定窗口 ±30 分钟 */
    private const val SUNRISE_WINDOW_MILLIS = 30 * 60 * 1000L

    /** 金色时刻高度角上限（度；太阳过中天时高度角远超此值） */
    private const val GOLDEN_MAX_ELEVATION_DEG = 6.0
}

/**
 * 月相计算（朔望月 29.530588853 天，基准新月 2000-01-06 18:14 UTC）。
 * 纯日期函数，零权限零联网。
 */
object MoonCalc {

    private const val SYNODIC_DAYS = 29.530588853
    private const val NEW_MOON_EPOCH_MILLIS = 947182440_000L // 2000-01-06T18:14Z

    /**
     * 以日期所在 UTC 正午采样月相，全天同一相位。
     * 分桶按**桶中点对齐**：相位 k 覆盖 [k×S/8 − S/16, k×S/8 + S/16)，
     * 使包含新月/满月时刻的当天判为该相位（若按桶起点对齐，基准日会因采样点
     * 早于新月时刻 6h 而被错判到前一相位）。
     */
    fun phaseOf(date: LocalDate): MoonPhaseKind {
        val noonUtc = date.toEpochDay() * 86400000L + 12 * 3600000L
        val ageDays = ((noonUtc - NEW_MOON_EPOCH_MILLIS).toDouble().mod(SYNODIC_DAYS * 86400000.0)) / 86400000.0
        val bucketWidth = SYNODIC_DAYS / 8.0
        val idx = ((ageDays + bucketWidth / 2).mod(SYNODIC_DAYS) / bucketWidth).toInt().coerceIn(0, 7)
        return MoonPhaseKind.entries[idx]
    }
}
