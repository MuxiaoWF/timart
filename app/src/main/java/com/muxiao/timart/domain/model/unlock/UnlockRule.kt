package com.muxiao.timart.domain.model.unlock

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 解锁条件密封类，逐字对照 PRD §2.2。
 * 13 种子类覆盖时间 / 设备 / 网络&环境三大类。
 */
sealed class UnlockCondition {
    // ---- 时间类 ----

    /** 指定日历日期（到达该日期当天及之后即满足） */
    data class FixedDate(val targetDate: LocalDate) : UnlockCondition()

    /** 距离创建必须满 N 天（基于系统时间，不防篡改） */
    data class MinElapsedDay(val days: Int) : UnlockCondition()

    /** 指定星期集合 */
    data class WeekDay(val weekSet: Set<DayOfWeek>) : UnlockCondition()

    /** 一天内时间段 [startHour, endHour)，支持跨零点（如 22-6） */
    data class TimeRange(val startHour: Int, val endHour: Int) : UnlockCondition()

    // ---- 设备状态 ----

    /** 电量 ≥ min / ≤ max（任一端可空） */
    data class BatteryLevel(val min: Int?, val max: Int?) : UnlockCondition()

    /** 充电 / 非充电 */
    data class ChargingState(val isCharging: Boolean) : UnlockCondition()

    /** 今日步数大于阈值 */
    data class StepCount(val minTodayStep: Int) : UnlockCondition()

    // ---- 网络 & 环境 ----

    /** 当前网络类型属于指定集合（WIFI / CELLULAR / NONE），零权限判定 */
    data class NetworkType(val types: Set<NetType>) : UnlockCondition()

    /** 到达指定坐标半径内 */
    data class GpsLocation(val lat: Double, val lng: Double, val radiusMeter: Int) : UnlockCondition()

    /** 指定城市的当前天气属于指定集合（元素为 [com.muxiao.timart.domain.model.WeatherType] 枚举名） */
    data class WeatherType(val weatherTypes: Set<String>) : UnlockCondition()

    /** 气温处于 [minC, maxC]（任一端可空；复用天气链路与 30min 缓存） */
    data class TemperatureThreshold(val minC: Double?, val maxC: Double?) : UnlockCondition()

    /** 当前连接的 Wi-Fi SSID 属于指定集合（26+ 读取需定位权限，未授权判不满足并给原因） */
    data class SsidMatch(val ssids: Set<String>) : UnlockCondition()

    /** 连续 [days] 天每天步数超过 [goal] 步（含今日；缺历史记录日按不满足 fail-closed） */
    data class StepStreak(val days: Int, val goal: Int) : UnlockCondition()

    // ---- 时间类扩展 ----

    /** 精确到分钟的到达时刻（ISO-8601 本地时间 "2026-09-07T14:30"，届时及之后满足） */
    data class FixedDateTime(val targetDateTime: String) : UnlockCondition()

    /** 距创建满 N 分钟（小时级由 [MinElapsedDay] 与本条组合表达） */
    data class MinElapsedMinutes(val minutes: Long) : UnlockCondition()

    /** 每月第 [dayOfMonth] 号（当天满足） */
    data class MonthlyDay(val dayOfMonth: Int) : UnlockCondition()

    /** 每年 [month] 月 [day] 日（纪念日，每年当天满足） */
    data class YearlyDate(val month: Int, val day: Int) : UnlockCondition()

    // ---- 网络 & 环境扩展 ----

    /** 离开指定坐标半径之外（[GpsLocation] 的反向语义） */
    data class AwayFromLocation(val lat: Double, val lng: Double, val radiusMeter: Int) : UnlockCondition()

    /** 当前太阳相位属于指定集合（日出/日落按 ±30 分钟窗口，昼夜按日出日落分界） */
    data class SunPhase(val phases: Set<SunPhaseKind>) : UnlockCondition()

    /** 湿度/风速/气压/紫外线处于 [min, max]（任一端可空；复用天气链路） */
    data class WeatherMetric(val metric: WeatherMetricKind, val min: Double?, val max: Double?) : UnlockCondition()

    /** 当前月相属于指定集合（纯天文计算，零权限离线可判） */
    data class MoonPhase(val phases: Set<MoonPhaseKind>) : UnlockCondition()

    // ---- 设备状态扩展 ----

    /** 下一个系统闹钟响起之前（未设置闹钟时给原因判不满足） */
    data object BeforeNextAlarm : UnlockCondition()

    /** 系统省电模式开关状态匹配 */
    data class PowerSaveMode(val isActive: Boolean) : UnlockCondition()

    /** 系统静音（含振动）状态匹配 */
    data class SilentMode(val isSilent: Boolean) : UnlockCondition()

    /** 耳机（有线或蓝牙音频）连接状态匹配 */
    data class HeadphoneConnected(val isConnected: Boolean) : UnlockCondition()

    /** 当前运动状态属于指定集合（受系统限制仅能识别 步行/静止 两种） */
    data class MotionActivity(val kinds: Set<MotionKind>) : UnlockCondition()

    /** 指南针朝向处于 [targetDeg] ± [toleranceDeg]（地磁北 0–359） */
    data class CompassHeading(val targetDeg: Int, val toleranceDeg: Int) : UnlockCondition()

    /** 海拔处于 [minM, maxM]（米，任一端可空；气压计数据） */
    data class AltitudeRange(val minM: Double?, val maxM: Double?) : UnlockCondition()

    // ---- 应用内使用统计 ----

    /** 累计打开时粒 ≥ [count] 次（按会话计，10 分钟内重复进入算一次） */
    data class OpenCountAtLeast(val count: Int) : UnlockCondition()

    /** 连续打开时粒 [days] 天（含今日若今日已开，否则止于昨日） */
    data class OpenStreak(val days: Int) : UnlockCondition()

    /** 距上一次打开超过 [days] 天（"好久不见"；从未打开过判不满足并给原因） */
    data class DaysSinceLastOpen(val days: Int) : UnlockCondition()

    /** 胶囊库总数 ≥ [count] 颗 */
    data class CapsuleCountAtLeast(val count: Int) : UnlockCondition()

    /** 指定胶囊已解锁（时间线联动） */
    data class OtherCapsuleUnlocked(val capsuleId: String) : UnlockCondition()

    /** 指定胶囊已销毁（"另一颗销毁后解锁"的快照语义：销毁后恒满足） */
    data class OtherCapsuleDestroyed(val capsuleId: String) : UnlockCondition()

    // ---- 即时挑战（打开胶囊当场完成；周期巡检 fail-closed，不影响自动解锁） ----

    /** 挑战型条件标记：判定需携带当场完成的应答（challengeId → 应答串），未携带按不满足 */
    interface ChallengeCondition {
        val challengeId: String
    }

    /** 回答创建者预设的问题（文本匹配，忽略大小写与首尾空白） */
    data class QuestionAnswer(
        override val challengeId: String,
        val question: String,
        val expectedAnswer: String,
    ) : UnlockCondition(), ChallengeCondition

    /** 数学题 / 小谜题（SHA-256 比对，答案不入明文存储；[question] 为谜面，解锁时展示） */
    data class PuzzleAnswer(
        override val challengeId: String,
        val question: String,
        val answerHash: String,
    ) : UnlockCondition(), ChallengeCondition

    /** 摇一摇 [shakes] 下（加速度峰值计数，打开时当场完成） */
    data class ShakeCount(
        override val challengeId: String,
        val shakes: Int,
    ) : UnlockCondition(), ChallengeCondition

    /** 手势挑战：把手机翻面 / 屏幕朝下静置 [holdSeconds] 秒（[gesture] 二选一） */
    data class FlipOrHold(
        override val challengeId: String,
        val gesture: GestureKind,
        val holdSeconds: Int,
    ) : UnlockCondition(), ChallengeCondition

    /** 触碰一枚 NFC 标签（打开时当场贴卡）；[expectedPayload] 非空表示绑定指定卡（卡上配对记录须一致），null=任意标签 */
    data class NfcTap(
        override val challengeId: String,
        val expectedPayload: String? = null,
    ) : UnlockCondition(), ChallengeCondition
}

/** 太阳相位 */
enum class SunPhaseKind { SUNRISE, DAY, SUNSET, NIGHT }

/** 天气指标（[WeatherMetric] 判定通道） */
enum class WeatherMetricKind { HUMIDITY, WIND, PRESSURE, UV }

/** 月相（8 相） */
enum class MoonPhaseKind {
    NEW,
    WAXING_CRESCENT,
    FIRST_QUARTER,
    WAXING_GIBBOUS,
    FULL,
    WANING_GIBBOUS,
    LAST_QUARTER,
    WANING_CRESCENT,
}

/** 运动状态（受系统限制：无 GMS 仅能识别步行/静止，故只保留两态） */
enum class MotionKind { STILL, WALKING }

/** 手势挑战类型 */
enum class GestureKind { FLIP, HOLD }

/** 当前网络类型 */
enum class NetType { WIFI, CELLULAR, NONE }

/** 条件合并逻辑：AND 全部满足；OR 任意满足 */
enum class LogicType { AND, OR }

/** 地理坐标点（GPS 条件与地图画布共用） */
data class GeoPoint(val lat: Double, val lng: Double)

/** 解锁规则：逻辑类型 + 条件列表 */
data class UnlockRule(
    val logicType: LogicType,
    val conditionList: List<UnlockCondition>,
)
