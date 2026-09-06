package com.muxiao.timart.domain.context

import com.muxiao.timart.domain.model.WeatherSnapshot
import com.muxiao.timart.domain.model.unlock.GeoPoint
import com.muxiao.timart.domain.model.unlock.MotionKind
import com.muxiao.timart.domain.model.unlock.NetType
import java.time.LocalDate

/**
 * 条件判定上下文——九接口全部为**同步纯 Kotlin**（前台页面与 Worker 共用）。
 *
 * 约束：
 * - 禁止 `android.*` import（可测性红线），单测用假实现；
 * - 禁止 suspend（判定主链路为同步纯逻辑）；
 * - Android Context 一律隔离在 utils / data 实现类中，经本接口组注入。
 */

/** 依赖胶囊状态（DependencyChecker 的返回值） */
enum class DependencyStatus {
    NOT_FOUND,
    LOCKED,
    UNLOCKED,
    DESTROYED,
}

/** 电池信息：电量百分比（读取失败为 null）与充电状态 */
data class BatteryInfo(
    val levelPercent: Int?,
    val isCharging: Boolean,
)

/** 时间提供者（PRD 明示不防篡改，直接读系统时间） */
interface TimeProvider {
    fun nowMillis(): Long
    fun today(): LocalDate
    fun nowHour(): Int
}

/** 电池状态提供者 */
interface BatteryProvider {
    fun battery(): BatteryInfo
}

/** 今日步数提供者；null = 无计步硬件或无权限 */
interface StepProvider {
    fun todaySteps(): Int?
}

/** 每日步数历史提供者（连续步数条件用）；[daysAgo] = 0 即今日，1 为昨天；null = 该日无记录（fail-closed） */
interface StepHistoryProvider {
    fun daySteps(daysAgo: Int): Int?
}

/** 当前 Wi-Fi 连接状态（SSID 条件判定通道） */
enum class WifiSsidState {
    /** 已连接 Wi-Fi，[WifiSsidInfo.ssid] 有效 */
    CONNECTED,

    /** Wi-Fi 未连接 */
    NOT_CONNECTED,

    /** 定位权限未授予（26+ 读取 SSID 的前置） */
    NO_PERMISSION,

    /** 系统定位服务未开启（26+ 读取 SSID 的前置） */
    NO_LOCATION_SERVICE,
}

/** 当前 Wi-Fi 连接信息（SSID 已去除系统返回值包裹的引号） */
data class WifiSsidInfo(
    val state: WifiSsidState,
    val ssid: String? = null,
)

/** 当前 Wi-Fi SSID 提供者 */
interface WifiProvider {
    fun current(): WifiSsidInfo
}

/** 当前网络类型提供者（零权限判定） */
interface NetworkProvider {
    fun current(): NetType
}

/** 天气快照提供者；null = 获取失败（30min 缓存由实现方负责） */
interface WeatherProvider {
    fun currentWeather(cityId: String): WeatherSnapshot?
}

/**
 * 位置提供者。
 * [foregroundOnly] = true 时表示后台 Worker 场景，GPS 条件应按"跳过"处理。
 */
interface LocationProvider {
    val foregroundOnly: Boolean
    fun isPermitted(): Boolean
    fun lastKnown(): GeoPoint?
}

/** 依赖胶囊状态查询者 */
interface DependencyChecker {
    fun statusOf(id: String): DependencyStatus
}

/** 下一闹钟提供者；null = 未设置闹钟（返回触发时刻 UTC 毫秒） */
interface AlarmProvider {
    fun nextAlarmMillis(): Long?
}

/** 系统模式提供者：省电 / 静音（含振动）/ 耳机连接 */
interface SystemModeProvider {
    fun isPowerSave(): Boolean

    /** 铃声静音或振动均视为静音 */
    fun isSilentRinger(): Boolean

    /** 有线或蓝牙音频输出已连接 */
    fun isHeadphoneConnected(): Boolean
}

/** 当前运动状态提供者；null = 无数据 / 无权限 / 设备不支持 */
interface MotionActivityProvider {
    fun current(): MotionKind?
}

/** 指南针提供者：地磁北向方位角 0–359；null = 传感器不可用 */
interface CompassProvider {
    fun headingDeg(): Float?
}

/** 海拔提供者（米，气压计）；null = 传感器不可用 */
interface AltitudeProvider {
    fun altitudeMeters(): Double?
}

/** 应用内使用统计提供者（打开次数 / 连击 / 上次打开，App 自持久化） */
interface UsageStatsProvider {
    /** 累计打开会话数 */
    fun openCount(): Int

    /** 连续打开天数（含今日若今日已开，否则止于昨日） */
    fun openStreakDays(): Int

    /** 上一次打开时刻（不含当前会话）；null = 从未打开 */
    fun lastOpenMillis(): Long?
}

/** 胶囊库元信息提供者 */
interface CapsuleMetaProvider {
    fun capsuleCount(): Int
}

/**
 * 条件判定上下文聚合类：UnlockJudgeUseCase 的唯一输入通道。
 */
data class ConditionContext(
    val time: TimeProvider,
    val battery: BatteryProvider,
    val step: StepProvider,
    val network: NetworkProvider,
    val weather: WeatherProvider,
    val location: LocationProvider,
    val dependency: DependencyChecker,
    val wifi: WifiProvider,
    val stepHistory: StepHistoryProvider,
    val alarm: AlarmProvider,
    val systemMode: SystemModeProvider,
    val motion: MotionActivityProvider,
    val compass: CompassProvider,
    val altitude: AltitudeProvider,
    val usage: UsageStatsProvider,
    val meta: CapsuleMetaProvider,
)
