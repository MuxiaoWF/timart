package com.muxiao.timart.domain.model.unlock

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin

/**
 * 太阳黄历推算：二十四节气与黄道十二宫（纯 JVM 可单测，零权限零联网）。
 *
 * 太阳视黄经与 [SunCalc] 的日出日落同源（平近点角 → 中心差 → 黄经链路）；
 * 节气 = 黄经 15° 分档，星座 = 黄经 30° 分档（春分点 0° 起）。
 * 按日期采样黄经，交界日可能与权威天文年历差一天（与 [MeteorCalendar] 的
 * "长期误差 ≤1 天"口径一致）；判据纯静态、年复推算，无网络依赖。
 */
object SolarTermCalendar {

    /** 当日节气（黄经 15° 分档；立春 315°、春分 0°） */
    fun termOf(date: LocalDate): SolarTermKind = SOLAR_TERMS[longitudeIndex(date, 15)]

    /** 当日所处黄道十二宫（黄经 30° 分档；白羊 0° 起） */
    fun zodiacOf(date: LocalDate): ZodiacKind = ZodiacKind.entries[longitudeIndex(date, 30)]

    /** 当日太阳视黄经（0–360 度；与 [SunCalc] 同源的近似算法） */
    fun solarLongitudeDeg(date: LocalDate): Double {
        // 与 SunCalc.sunTimesUtcMillis 相同的儒略日 → 平近点角 → 中心差 → 黄经链路
        val jdate = date.toEpochDay() + 2440587.5
        val n = ceil(jdate - 2451545.0 + 0.0008)
        val m = mod360(357.5291 + 0.98560028 * n)
        val c = 1.9148 * sin(Math.toRadians(m)) +
            0.0200 * sin(Math.toRadians(2 * m)) +
            0.0003 * sin(Math.toRadians(3 * m))
        return mod360(m + c + 180.0 + 102.9372)
    }

    private fun longitudeIndex(date: LocalDate, sectorDeg: Int): Int {
        val sectors = 360 / sectorDeg
        val index = floor(solarLongitudeDeg(date) / sectorDeg).toInt()
        return index.coerceIn(0, sectors - 1)
    }

    private fun mod360(x: Double): Double = ((x % 360.0) + 360.0) % 360.0

    /** 黄经 15° 分档 → 节气（0 = 春分 0°–15°，顺序沿黄经递增） */
    private val SOLAR_TERMS = listOf(
        SolarTermKind.CHUNFEN, SolarTermKind.QINGMING, SolarTermKind.GUYU,
        SolarTermKind.LIXIA, SolarTermKind.XIAOMAN, SolarTermKind.MANGZHONG,
        SolarTermKind.XIAZHI, SolarTermKind.XIAOSHU, SolarTermKind.DASHU,
        SolarTermKind.LIQIU, SolarTermKind.CHUSHU, SolarTermKind.BAILU,
        SolarTermKind.QIUFEN, SolarTermKind.HANLU, SolarTermKind.SHUANGJIANG,
        SolarTermKind.LIDONG, SolarTermKind.XIAOXUE, SolarTermKind.DAXUE,
        SolarTermKind.DONGZHI, SolarTermKind.XIAOHAN, SolarTermKind.DAHAN,
        SolarTermKind.LICHUN, SolarTermKind.YUSHUI, SolarTermKind.JINGZHE,
    )
}
