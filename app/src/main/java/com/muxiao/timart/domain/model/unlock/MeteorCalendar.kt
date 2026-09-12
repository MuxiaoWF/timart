package com.muxiao.timart.domain.model.unlock

import java.time.LocalDate

/**
 * 年周期流星雨极大期静态表（纯日期函数，零权限零联网）。
 *
 * 极大日为国际通行 ZHR 峰值日的公历月-日中值。这些 Shower 由地球每年穿越同一流星
 * 物质带引起，极大日历年在固定月-日附近小幅摆动（≤1 天，源于历法与轨道周期的错位），
 * 因此按月-日静态表可**无限期向后推算**，长期误差 ≤1 天，由 ±1 天判定窗口吸收。
 * 小时级精确极大时刻无法静态推算（每年由 IMO 现测发布），本条件不做该精度承诺。
 */
object MeteorCalendar {

    /** 极大日（公历月、日中值） */
    private val PEAKS: Map<MeteorShowerKind, Pair<Int, Int>> = mapOf(
        MeteorShowerKind.QUADRANTIDS to Pair(1, 4), // 象限仪座流星雨（1/3–4）
        MeteorShowerKind.LYRIDS to Pair(4, 22), // 天琴座流星雨（4/22–23）
        MeteorShowerKind.ETA_AQUARIIDS to Pair(5, 6), // 宝瓶座η流星雨（5/5–6）
        MeteorShowerKind.DELTA_AQUARIIDS to Pair(7, 30), // 宝瓶座δ南流星雨（7/29–30）
        MeteorShowerKind.PERSEIDS to Pair(8, 13), // 英仙座流星雨（8/12–13）
        MeteorShowerKind.ORIONIDS to Pair(10, 21), // 猎户座流星雨（10/21–22）
        MeteorShowerKind.LEONIDS to Pair(11, 17), // 狮子座流星雨（11/17–18）
        MeteorShowerKind.GEMINIDS to Pair(12, 14), // 双子座流星雨（12/13–14）
        MeteorShowerKind.URSIDS to Pair(12, 22), // 小熊座流星雨（12/21–22）
    )

    /** 极大日判定窗口：极大日 ±1 天（吸收历年摆动） */
    private const val PEAK_WINDOW_DAYS = 1L

    /** 今日是否处于某场流星雨的极大期窗口内 */
    fun isPeakNight(kind: MeteorShowerKind, today: LocalDate): Boolean {
        val (month, day) = PEAKS[kind] ?: return false
        val peak = LocalDate.of(today.year, month, day)
        return kotlin.math.abs(today.toEpochDay() - peak.toEpochDay()) <= PEAK_WINDOW_DAYS
    }
}
