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

    /** 今日步数处于 [minTodayStep, maxTodayStep]（任一端可空，与 [BatteryLevel] 同构） */
    data class StepCount(val minTodayStep: Int?, val maxTodayStep: Int? = null) : UnlockCondition()

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

    /** 每年农历 [month] 月 [day] 日（春节/中秋等传统节日；遇闰月按同月同日处理） */
    data class LunarDate(val month: Int, val day: Int) : UnlockCondition()

    // ---- 网络 & 环境扩展 ----

    /** 离开指定坐标半径之外（[GpsLocation] 的反向语义） */
    data class AwayFromLocation(val lat: Double, val lng: Double, val radiusMeter: Int) : UnlockCondition()

    /** 当前太阳相位属于指定集合（日出/日落按 ±30 分钟窗口，昼夜按日出日落分界） */
    data class SunPhase(val phases: Set<SunPhaseKind>) : UnlockCondition()

    /** 金色时刻：太阳已升起且高度角 ≤ 6°（摄影黄金时段，随纬度/季节自然伸缩） */
    data object GoldenHour : UnlockCondition()

    /** 湿度/风速/气压/紫外线处于 [min, max]（任一端可空；复用天气链路） */
    data class WeatherMetric(val metric: WeatherMetricKind, val min: Double?, val max: Double?) : UnlockCondition()

    /** 当前月相属于指定集合（纯天文计算，零权限离线可判） */
    data class MoonPhase(val phases: Set<MoonPhaseKind>) : UnlockCondition()

    /** 今日位于指定流星雨的极大期（公历月-日年复推算，长期误差 ≤1 天，窗口 ±1 天吸收） */
    data class MeteorShower(val showers: Set<MeteorShowerKind>) : UnlockCondition()

    /** 环境光照度 ≤ [maxLux]（黑暗中打开；光线传感器，硬件门控） */
    data class AmbientLight(val maxLux: Int) : UnlockCondition()

    /** 当前系统时区 ≠ 封存时所在时区（到另一个时区/国家；零权限） */
    data class TimezoneChange(val homeZoneId: String) : UnlockCondition()

    /** 定位速度 ≥ [minSpeedKmh] km/h（移动中；GPS 前台通道，权限/后台语义与 [GpsLocation] 一致） */
    data class MovingAboveSpeed(val minSpeedKmh: Int) : UnlockCondition()

    // ---- 设备状态扩展 ----

    /** 下一个系统闹钟响起之前（未设置闹钟时给原因判不满足） */
    data object BeforeNextAlarm : UnlockCondition()

    /** 系统省电模式开关状态匹配 */
    data class PowerSaveMode(val isActive: Boolean) : UnlockCondition()

    /** 系统静音（含振动）状态匹配 */
    data class SilentMode(val isSilent: Boolean) : UnlockCondition()

    /** 系统飞行模式开关状态匹配（零权限，读系统设置） */
    data class AirplaneMode(val isEnabled: Boolean) : UnlockCondition()

    /** 是否有音乐等媒体音频正在播放（快照语义：判定瞬间活跃即算） */
    data class MusicPlaying(val isPlaying: Boolean) : UnlockCondition()

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

    /** 指定胶囊已开启阅读过（meta `capsule.read.<id>`，与首页三态同一事实源；快照语义恒满足） */
    data class OtherCapsuleRead(val capsuleId: String) : UnlockCondition()

    /** 打开过这颗胶囊详情 ≥ [count] 次（meta `capsule.views.<id>` 计数，含锁定态凝视） */
    data class ViewCountAtLeast(val count: Int) : UnlockCondition()

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

    /** 长按屏幕不放 [holdSeconds] 秒（打开时当场完成） */
    data class HoldPress(
        override val challengeId: String,
        val holdSeconds: Int,
    ) : UnlockCondition(), ChallengeCondition

    /** 用生物识别（指纹/面容）验证（打开时当场完成；无硬件设备创建侧禁用） */
    data class BiometricUnlock(
        override val challengeId: String,
    ) : UnlockCondition(), ChallengeCondition

    /** 拍一张此刻的照片留念（打开时当场拍摄；照片仅当场展示，不保存） */
    data class PhotoKeepsake(
        override val challengeId: String,
    ) : UnlockCondition(), ChallengeCondition

    // ---- 时间类扩展（储备池 §1；delta D-1.2）----

    /** 今日处于指定节气集合（太阳黄经每 15° 一气，离线纯计算，长期误差 ≤1 天） */
    data class SolarTerm(val solarTerms: Set<SolarTermKind>) : UnlockCondition()

    /** 距创建天数恰为 [modulus] 的整数倍（百日/千日/万日纪念；满 1 天才起算） */
    data class RoundDaysElapsed(val modulus: Int) : UnlockCondition()

    /** 当前季节属于指定集合（气象四季：3–5 春 / 6–8 夏 / 9–11 秋 / 12–2 冬） */
    data class Season(val seasons: Set<SeasonKind>) : UnlockCondition()

    /** 距创建满 [months] 个整月（严格历月语义） */
    data class MinElapsedMonths(val months: Int) : UnlockCondition()

    /** 每月第 [nth] 个 [dayOfWeek]（如"每月第一个周一"） */
    data class NthWeekdayOfMonth(val nth: Int, val dayOfWeek: DayOfWeek) : UnlockCondition()

    /** 每年 [month] 月第 [nth] 个 [dayOfWeek]（母亲节/感恩节等浮动节日） */
    data class YearlyNthWeekday(val month: Int, val nth: Int, val dayOfWeek: DayOfWeek) : UnlockCondition()

    /** 2 月 29 日当天满足（四年一遇；非闰年恒不满足） */
    data object LeapDay : UnlockCondition()

    /** 每月最后一天当天满足 */
    data object LastDayOfMonth : UnlockCondition()

    /** 封存满 [minDays] 天后的首个 [dayOfWeek] 当天满足（错过该 7 天窗口即不再满足） */
    data class NthWeekdaySince(val minDays: Int, val dayOfWeek: DayOfWeek) : UnlockCondition()

    /** 太阳黄经处于指定星座区间（占星月份，与节气同源计算） */
    data class ZodiacSeason(val zodiac: ZodiacKind) : UnlockCondition()

    /** 白昼长度处于 [minHours, maxHours] 小时（任一端可空；随纬度/季节变化，需定位换算） */
    data class DayLength(val minHours: Double?, val maxHours: Double?) : UnlockCondition()

    /** 日出钟点处于 [minMinute, maxMinute]（当日 0 点起算的本地分钟数，任一端可空；需定位换算） */
    data class SunriseTimeRange(val minMinute: Int?, val maxMinute: Int?) : UnlockCondition()

    /** 今日处于农历 [month] 月整月（含闰同月） */
    data class LunarMonthRange(val month: Int) : UnlockCondition()

    /** 每月固定多天（如发薪日 + 前一天） */
    data class MonthlyDaySet(val days: Set<Int>) : UnlockCondition()

    // ---- 设备状态扩展（储备池 §2；delta D-1.2）----

    /** 系统深色模式开关状态匹配（零权限） */
    data class DarkTheme(val isDark: Boolean) : UnlockCondition()

    /** 系统勿扰模式开启状态匹配（零权限） */
    data class DoNotDisturb(val isActive: Boolean) : UnlockCondition()

    /** 当前设备姿态属于指定集合（加速度计快照：平放/直立/倒置；硬件门控） */
    data class DevicePose(val kinds: Set<PoseKind>) : UnlockCondition()

    /** 屏幕亮度 ≤ [maxLevel]（0–255，读系统设置，零权限） */
    data class ScreenBrightness(val maxLevel: Int) : UnlockCondition()

    /** 媒体音量静音（音量 0）状态匹配 */
    data class MediaVolume(val isMuted: Boolean) : UnlockCondition()

    /** VPN 连接状态匹配（TRANSPORT_VPN 检测，零权限） */
    data class VpnActive(val isActive: Boolean) : UnlockCondition()

    /** 当前充电方式属于指定集合（AC / USB / 无线；未充电判不满足） */
    data class PlugType(val kinds: Set<PlugKind>) : UnlockCondition()

    /** 电池温度处于 [minC, maxC] °C（任一端可空；读取失败给原因） */
    data class BatteryTemp(val minC: Double?, val maxC: Double?) : UnlockCondition()

    /** 横竖屏状态匹配（快照语义） */
    data class Orientation(val isLandscape: Boolean) : UnlockCondition()

    /** 定位速度处于 [minKmh, maxKmh] km/h（任一端可空；通道与 [MovingAboveSpeed] 一致） */
    data class SpeedRange(val minKmh: Int?, val maxKmh: Int?) : UnlockCondition()

    /** 当前连接的 Wi-Fi BSSID 属于指定集合（精确到具体路由器；权限语义同 [SsidMatch]） */
    data class SsidBssidMatch(val bssids: Set<String>) : UnlockCondition()

    /** 已连接蓝牙设备名与 [deviceNames] 有交集（附近设备权限未授权判不满足并给原因） */
    data class BluetoothDevice(val deviceNames: Set<String>) : UnlockCondition()

    /** 接近传感器被遮挡（手捂住手机顶部；硬件门控） */
    data object ProximityCovered : UnlockCondition()

    /** 距上次开机不足 [withinMinutes] 分钟（重启手机后解锁） */
    data class FreshBoot(val withinMinutes: Int) : UnlockCondition()

    /** 本机已安装 [packageName] 指定的应用（queries 声明，零运行时权限） */
    data class InstalledApp(val packageName: String) : UnlockCondition()

    // ---- 网络 & 环境扩展（储备池 §3；delta D-1.2）----

    /** 指定快照城市当前空气质量 AQI ≤ [maxAqi]（Open-Meteo Air Quality API，同天气链路） */
    data class AirQuality(val maxAqi: Int) : UnlockCondition()

    /** 当前风向属于指定集合（八方位，Open-Meteo 现有字段） */
    data class WindDirection(val dirs: Set<WindDirKind>) : UnlockCondition()

    /** 当前位于北/南半球（纬度符号判定） */
    data class Hemisphere(val north: Boolean) : UnlockCondition()

    /** 气温比昨日均温低 ≥ [minDropC] °C（"大幅降温那天"；复用天气链路） */
    data class TempDelta(val minDropC: Double) : UnlockCondition()

    /** 到达指定城市（城市中心 ± 半径；[cityName] 仅用于条件句展示，判定同 [GpsLocation] 通道） */
    data class CityLocation(
        val cityName: String,
        val lat: Double,
        val lng: Double,
        val radiusMeter: Int,
    ) : UnlockCondition()

    /** 海拔较封存时升高/降低 ≥ [deltaM] 米（气压计通道；[baseAltM] 为封存时海拔） */
    data class RelativeAltitude(
        val baseAltM: Double,
        val deltaM: Double,
        val direction: LiftDirection,
    ) : UnlockCondition()

    /** 今日降水概率 ≥ [minProb]%（Open-Meteo daily 字段） */
    data class PrecipitationProbability(val minProb: Int) : UnlockCondition()

    // ---- 应用内统计扩展（储备池 §4；delta D-1.2）----

    /** 累计凝视这颗胶囊 ≥ [seconds] 秒（meta `capsule.watch.<id>`，详情页前台累计） */
    data class WatchDurationAtLeast(val seconds: Int) : UnlockCondition()

    /** 已开启阅读过的胶囊总数 ≥ [count] 颗 */
    data class ReadCountAtLeast(val count: Int) : UnlockCondition()

    /** 尘迹档案数 ≥ [count] 条（"送走 N 颗后解锁"） */
    data class DestroyCountAtLeast(val count: Int) : UnlockCondition()

    /** 指定胶囊仍处于锁定（否定依赖："另一颗没被打开前你也不能开"） */
    data class OtherCapsuleStillLocked(val capsuleId: String) : UnlockCondition()

    /** 完成过一次备份导出（meta `app.backup.done`，正向引导数据安全习惯） */
    data object BackupDone : UnlockCondition()

    /** 累计创建胶囊总数 ≥ [count]（含已删/已毁；meta `app.created.total`） */
    data class TotalCreatedCount(val count: Int) : UnlockCondition()

    /** 指定胶囊被开启阅读的当天满足（同日联动，快照语义） */
    data class SameDayAsCapsuleRead(val capsuleId: String) : UnlockCondition()

    /** 指定胶囊被开启阅读已满 [days] 天（时间轴联动） */
    data class DaysSinceCapsuleRead(val days: Int, val capsuleId: String) : UnlockCondition()

    /** 本应用的桌面小组件已绑定到启动器（零权限，功能引导） */
    data object WidgetBound : UnlockCondition()

    /** 今日打开时粒 ≥ [count] 次（按会话计，去重口径同 [OpenCountAtLeast]） */
    data class TodayOpenCount(val count: Int) : UnlockCondition()

    // ---- 即时挑战扩展（储备池 §5；delta D-1.2；周期巡检 fail-closed）----

    /** 手势图案挑战：连接九宫格点位的规范化序列 SHA-256 比对（答案不入明文存储） */
    data class GesturePattern(
        override val challengeId: String,
        val answerHash: String,
    ) : UnlockCondition(), ChallengeCondition

    /** 当场走 [steps] 步（硬件计步器会话差值，当场完成） */
    data class WalkStepsNow(
        override val challengeId: String,
        val steps: Int,
    ) : UnlockCondition(), ChallengeCondition

    /** 把手机水平旋转累计 [degrees] 度（陀螺仪积分，当场完成） */
    data class SpinPhone(
        override val challengeId: String,
        val degrees: Int,
    ) : UnlockCondition(), ChallengeCondition

    /** 同时按住两个音量键 [holdSeconds] 秒（当场完成） */
    data class VolumeKeyCombo(
        override val challengeId: String,
        val holdSeconds: Int,
    ) : UnlockCondition(), ChallengeCondition

    /** 让手机保持静止 [holdSeconds] 秒（加速度方差判定，当场完成） */
    data class StayStill(
        override val challengeId: String,
        val holdSeconds: Int,
    ) : UnlockCondition(), ChallengeCondition

    /** 把手机举起/放低 [meters] 米（气压差判定，当场完成；[direction] 二选一） */
    data class LiftHighLowerLow(
        override val challengeId: String,
        val direction: LiftDirection,
        val meters: Double,
    ) : UnlockCondition(), ChallengeCondition

    /** 说出预设口令（语音识别当场应答；文本比对口径同 [QuestionAnswer]） */
    data class VoicePassword(
        override val challengeId: String,
        val expectedAnswer: String,
    ) : UnlockCondition(), ChallengeCondition

    /** 连续点击屏幕 [taps] 下（当场完成） */
    data class TapCount(
        override val challengeId: String,
        val taps: Int,
    ) : UnlockCondition(), ChallengeCondition

    /** 当场爬 [floors] 层楼（气压差判定，每层约 3 米，当场完成） */
    data class ClimbFloors(
        override val challengeId: String,
        val floors: Int,
    ) : UnlockCondition(), ChallengeCondition

    /** 扫一枚二维码（[expectedPayload] 非空表示扫码内容须一致，null=任意二维码） */
    data class ScanQr(
        override val challengeId: String,
        val expectedPayload: String? = null,
    ) : UnlockCondition(), ChallengeCondition

    /** 算力挑战：找到 nonce 使 SHA-256(challengeId:nonce) 前导 0 ≥ [difficulty] 位（当场计算） */
    data class ProofOfWork(
        override val challengeId: String,
        val difficulty: Int,
    ) : UnlockCondition(), ChallengeCondition

    // ---- 网络 & 环境扩展（储备池 v5；delta-prd-vs-code.md D-1.5）----

    /** 降雪观测：快照城市正在下雪；[firstOfSeason] = true 时还要求此前无雪记录（今冬首雪；历史窗口见 `WeatherSnapshot.snowDaysPast`） */
    data class SnowObservation(val firstOfSeason: Boolean) : UnlockCondition()

    // ---- 应用内统计扩展（储备池 v5；delta-prd-vs-code.md D-1.5）----

    /** 自封存日（含）起累计步行 ≥ [minSteps] 步（按已采样每日步数合计，缺采样日按 0 计；通道见 `StepHistoryProvider.stepsSince`） */
    data class CumulativeSteps(val minSteps: Int) : UnlockCondition()

    // ---- 网络与环境扩展（储备池 v6；delta-prd-vs-code.md D-1.6）----

    /**
     * 连续降雨观测：截至今日连续 [days] 天降雨（`WeatherSnapshot.rainStreakPast` 历史窗口 +
     * 今日天气类型；雨类 = 毛雨/雨/雷，见 `WmoCodeMapper.isRain`）。
     * [afterRain] = true 为「雨后初晴」语义：此前连续 [days] 天降雨且今日转晴。
     */
    data class RainStreak(val days: Int, val afterRain: Boolean) : UnlockCondition()

    /**
     * 封存日天气对比：当前气温比封存时刻（`Capsule.weather.tempC`，创建时的天气快照）
     * 更冷/更热 ≥ [deltaC] °C（[hotter] 二选一）。封存时未选天气城市 → fail-closed 给原因。
     */
    data class TempVsSealDay(val deltaC: Double, val hotter: Boolean) : UnlockCondition()

    // ---- 扩展（储备池 v7；delta-prd-vs-code.md D-1.7）----

    /** 每月农历固定多天（如每月农历初一/十五；闰月按同月同日，与 [LunarDate] 同口径） */
    data class LunarDayOfMonth(val days: Set<Int>) : UnlockCondition()

    /** 雷暴观测：快照城市今日雷暴；[firstOfSeason] = true 时还要求此前历史窗口无雷记录（今季首雷） */
    data class ThunderObservation(val firstOfSeason: Boolean) : UnlockCondition()

    /** 明亮环境：环境光照度 ≥ [minLux]（阳光下/亮灯处；光线传感器，硬件门控；[AmbientLight] 的反向语义） */
    data class BrightLight(val minLux: Int) : UnlockCondition()

    /** 今日使用 [packageName] 应用不超过 [maxMinutes] 分钟（数字戒断；需系统使用统计权限，未授予 fail-closed） */
    data class AppUsageCeiling(val packageName: String, val maxMinutes: Int) : UnlockCondition()

    /** 气压较昨日均压下降 ≥ [minDropHpa] hPa（风雨将至；复用天气链路历史窗口，缺昨日数据 fail-closed） */
    data class PressureDelta(val minDropHpa: Double) : UnlockCondition()

    // ---- 现场挑战扩展（储备池 v7；周期巡检 fail-closed）----

    /** 录一段此刻的声音留念（当场录、当场回放即删，不保存；完成后应答 DONE） */
    data class VoiceKeepsake(
        override val challengeId: String,
    ) : UnlockCondition(), ChallengeCondition

    /** 对着麦克风持续发出响亮声音 [seconds] 秒（呐喊/吹气；振幅 RMS 判定，当场完成） */
    data class ShoutOut(
        override val challengeId: String,
        val seconds: Int,
    ) : UnlockCondition(), ChallengeCondition
}

/** 太阳相位 */
enum class SunPhaseKind { SUNRISE, DAY, SUNSET, NIGHT }

/** 天气指标（[UnlockCondition.WeatherMetric] 判定通道） */
enum class WeatherMetricKind { HUMIDITY, WIND, PRESSURE, UV, APPARENT }

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

/**
 * 年周期流星雨（极大日期按公历月-日静态推算，每年复现，长期误差 ≤1 天）。
 * 极大日期为天顶小时率（ZHR）峰值日的国际通行中值，详见 [MeteorCalendar]。
 */
enum class MeteorShowerKind { QUADRANTIDS, LYRIDS, ETA_AQUARIIDS, DELTA_AQUARIIDS, PERSEIDS, ORIONIDS, LEONIDS, GEMINIDS, URSIDS }

/** 运动状态（受系统限制：无 GMS 仅能识别步行/静止，故只保留两态） */
enum class MotionKind { STILL, WALKING }

/** 二十四节气（太阳黄经每 15° 一气；立春 315°、春分 0°，详推算见 [SolarTermCalendar]） */
enum class SolarTermKind {
    LICHUN, YUSHUI, JINGZHE, CHUNFEN, QINGMING, GUYU,
    LIXIA, XIAOMAN, MANGZHONG, XIAZHI, XIAOSHU, DASHU,
    LIQIU, CHUSHU, BAILU, QIUFEN, HANLU, SHUANGJIANG,
    LIDONG, XIAOXUE, DAXUE, DONGZHI, XIAOHAN, DAHAN,
}

/** 黄道十二宫（太阳黄经 30° 一宫，白羊 0° 起） */
enum class ZodiacKind {
    ARIES, TAURUS, GEMINI, CANCER, LEO, VIRGO,
    LIBRA, SCORPIO, SAGITTARIUS, CAPRICORN, AQUARIUS, PISCES,
}

/** 气象四季（3–5 春 / 6–8 夏 / 9–11 秋 / 12–2 冬，北半球口径） */
enum class SeasonKind { SPRING, SUMMER, AUTUMN, WINTER }

/** 充电方式（AC 电源 / USB / 无线） */
enum class PlugKind { AC, USB, WIRELESS }

/** 八方位风向（气象惯例 0° = 北，90° = 东） */
enum class WindDirKind { N, NE, E, SE, S, SW, W, NW }

/** 设备姿态（加速度计快照三态） */
enum class PoseKind { FLAT, UPRIGHT, UPSIDE_DOWN }

/** 垂直方向（举起 / 放低） */
enum class LiftDirection { UP, DOWN }

/** 风向度数 → 八方位（每 45° 一方位，北居中 ±22.5°） */
fun windDirFromDeg(deg: Int): WindDirKind {
    val normalized = ((deg % 360) + 360) % 360
    return WindDirKind.entries[((normalized + 22) % 360) / 45]
}

/** 手势挑战类型 */
enum class GestureKind { FLIP, HOLD }

/** 当前网络类型 */
enum class NetType { WIFI, CELLULAR, NONE }

/** 条件合并逻辑：AND 全部满足；OR 任意满足；AT_LEAST 达到阈值条数即满足（M-of-N） */
enum class LogicType { AND, OR, AT_LEAST }

/**
 * 状态类条件（判定语义 = 当前状态 == 要求状态 的六种二元开关）取否定态：
 * 结果即"当前实际状态"，详情页时间线在条件未满足时用它展示现状句。
 * 其余条件（区间/集合/挑战等）没有唯一否定态，返回 null。
 */
fun UnlockCondition.oppositeState(): UnlockCondition? = when (this) {
    is UnlockCondition.HeadphoneConnected -> copy(isConnected = !isConnected)
    is UnlockCondition.ChargingState -> copy(isCharging = !isCharging)
    is UnlockCondition.PowerSaveMode -> copy(isActive = !isActive)
    is UnlockCondition.SilentMode -> copy(isSilent = !isSilent)
    is UnlockCondition.AirplaneMode -> copy(isEnabled = !isEnabled)
    is UnlockCondition.MusicPlaying -> copy(isPlaying = !isPlaying)
    is UnlockCondition.DarkTheme -> copy(isDark = !isDark)
    is UnlockCondition.DoNotDisturb -> copy(isActive = !isActive)
    is UnlockCondition.MediaVolume -> copy(isMuted = !isMuted)
    is UnlockCondition.Orientation -> copy(isLandscape = !isLandscape)
    is UnlockCondition.VpnActive -> copy(isActive = !isActive)
    else -> null
}

/** 地理坐标点（GPS 条件与地图画布共用） */
data class GeoPoint(val lat: Double, val lng: Double)

/**
 * 子群组（储备池暂缓项落地）：把 [UnlockRule.conditionList] 中的若干条按**下标**归为一组，
 * 组内独立 AND/OR/任选M。例：「（周六 或 周日）且（在家 或 在公司）」= 顶层 AND + 两个 OR 组。
 * [indexes] 指向扁平 [UnlockRule.conditionList] 的下标（扁平列表是唯一存储权威，
 * 组只是其上的划分视图——condMet `capsule.condMet.<id>.<index>` 等按下标落键的契约不受影响）；
 * [name] 仅为展示层组名，可空。
 */
data class ConditionGroup(
    val name: String? = null,
    val logicType: LogicType = LogicType.AND,
    val threshold: Int? = null,
    val indexes: List<Int>,
)

/**
 * 解锁规则：逻辑类型 + 条件列表 + 阈值 + 可选子群组。
 * [threshold] 仅在 [LogicType.AT_LEAST] 下生效（"N 条满足 M 条即可"，备用钥匙语义）；
 * 其余逻辑类型应为 null。挑战条目照常计入 N 与 M（未应答按不满足，快照 fail-closed 不变）。
 * [groups] 非空时为**部分划分**：判定按 [units] 划成「组 + 未分组单例」单元，顶层逻辑作用于单元；
 * 为空 / 非法（下标越界或重叠）时整体回退旧扁平语义（见 [units]，向后兼容零行为变化）。
 */
data class UnlockRule(
    val logicType: LogicType,
    val conditionList: List<UnlockCondition>,
    val threshold: Int? = null,
    val groups: List<ConditionGroup> = emptyList(),
)

/**
 * 判定/预估/摘要共用的「单元」划分：组 = 多条件单元，未分组条件 = 单例单元。
 * 组无重叠且下标都在界内时按扁平顺序输出（命中组首条时输出整组）；否则全部退化为单例
 * （等价旧扁平语义——脏数据 fail-safe，宁可退化也不猜）。
 */
data class RuleUnit(
    val indexes: List<Int>,

    /** null = 未分组单例；非空 = 子群组（展示名） */
    val name: String?,
    val logicType: LogicType,
    val threshold: Int? = null,
)

/** 把规则划成判定/预估/摘要共用的单元序列（组在扁平顺序中首次命中处整体展开） */
fun units(rule: UnlockRule): List<RuleUnit> {
    val valid = rule.groups.filter { group ->
        group.indexes.isNotEmpty() &&
            group.indexes.all { it in rule.conditionList.indices } &&
            group.indexes.toSet().size == group.indexes.size
    }
    val claimed = valid.flatMap { it.indexes }
    if (valid.isEmpty() || claimed.size != claimed.toSet().size) {
        // 无组 / 下标越界 / 组间重叠：全部退化为单例（与旧扁平判定逐字节一致）
        return rule.conditionList.indices.map { RuleUnit(listOf(it), name = null, logicType = LogicType.AND) }
    }
    val groupByIndex = buildMap {
        valid.forEach { group -> group.indexes.forEach { put(it, group) } }
    }
    val emitted = mutableSetOf<ConditionGroup>()
    return buildList {
        for (index in rule.conditionList.indices) {
            val group = groupByIndex[index]
            when {
                group == null -> add(RuleUnit(listOf(index), name = null, logicType = LogicType.AND))
                group in emitted -> Unit // 组内非首条：已在组单元里
                else -> {
                    emitted += group
                    add(RuleUnit(group.indexes, group.name, group.logicType, group.threshold))
                }
            }
        }
    }
}

/**
 * 单元进度：(已满足单元数, 单元总数)。单例单元满足 = 该条件满足；组单元满足按组内逻辑。
 * 详情页「差 M 项」与 M-of-N 措辞在分组规则下按单元口径计算（判定引擎同源）。
 */
fun unitProgress(rule: UnlockRule, satisfiedFlags: List<Boolean>): Pair<Int, Int> {
    val unitList = units(rule)
    val satisfiedUnits = unitList.count { unit ->
        val memberFlags = unit.indexes.mapNotNull { satisfiedFlags.getOrNull(it) }
        when (unit.logicType) {
            LogicType.AND -> memberFlags.all { it }
            LogicType.OR -> memberFlags.any { it }
            LogicType.AT_LEAST -> {
                val required = (unit.threshold ?: memberFlags.size).coerceIn(1, memberFlags.size)
                memberFlags.count { it } >= required
            }
        }
    }
    return satisfiedUnits to unitList.size
}
