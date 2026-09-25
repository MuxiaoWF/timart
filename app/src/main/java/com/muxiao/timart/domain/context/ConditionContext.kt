package com.muxiao.timart.domain.context

import com.muxiao.timart.domain.model.WeatherSnapshot
import com.muxiao.timart.domain.model.unlock.GeoPoint
import com.muxiao.timart.domain.model.unlock.MotionKind
import com.muxiao.timart.domain.model.unlock.NetType
import com.muxiao.timart.domain.model.unlock.PlugKind
import com.muxiao.timart.domain.model.unlock.PoseKind
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

    /** 当前系统时区 ID（如 Asia/Shanghai；时区变更条件判定通道） */
    fun zoneId(): String
}

/** 电池状态提供者 */
interface BatteryProvider {
    fun battery(): BatteryInfo
}

/** 今日步数提供者；null = 无计步硬件或无权限 */
interface StepProvider {
    fun todaySteps(): Int?
}

/** 每日步数历史提供者（连续步数条件用）；`daysAgo` = 0 即今日，1 为昨天；null = 该日无记录（fail-closed） */
interface StepHistoryProvider {
    fun daySteps(daysAgo: Int): Int?

    /**
     * 自 [sinceDate]（含）至今天的累计步行（按已采样每日步数合计，缺采样日按 0 计；
     * 储备池 v5 累计步行条件通道）。
     * null = 无计步硬件 / 无权限 / 实现未接入（判定按「设备不支持」fail-closed）。
     * 默认 null = 旧实现未接入，既有实现与单测假件无需改动。
     */
    fun stepsSince(sinceDate: LocalDate): Long? = null
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

/** 当前 Wi-Fi 连接信息（SSID 已去除系统返回值包裹的引号；BSSID 为接入点 MAC，供路由器级条件判定） */
data class WifiSsidInfo(
    val state: WifiSsidState,
    val ssid: String? = null,
    val bssid: String? = null,
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

    /** 最近一次定位的速度（米/秒）；null = 无定位或该定位无速度分量（移动中条件通道） */
    fun speedMps(): Float?
}

/** 环境光提供者（照度 lux）；null = 设备无光线传感器 */
interface AmbientLightProvider {
    fun lux(): Float?
}

// ---- 储备池 v4 扩展通道（五接口全部同步快照语义）----

/** 系统界面状态提供者：深色模式 / 横竖屏 / 勿扰 / 屏幕亮度 / 媒体静音（全部零权限） */
interface SystemUiStateProvider {
    /** 系统深色模式已开启（uiMode 判定） */
    fun isDarkThemeOn(): Boolean

    /** 当前为横屏 */
    fun isLandscape(): Boolean

    /** 勿扰模式（zen mode）处于激活（非"全部允许"） */
    fun isDndActive(): Boolean

    /** 屏幕亮度 0–255；null = 读取失败 */
    fun screenBrightnessLevel(): Int?

    /** 媒体音量为 0 */
    fun isMediaMuted(): Boolean
}

/** 设备硬件状态提供者：VPN / 充电方式 / 电池温度 / 开机时长 */
interface DeviceExtraProvider {
    /** VPN 处于连接状态（TRANSPORT_VPN） */
    fun isVpnActive(): Boolean

    /** 当前充电方式；null = 未充电或读取失败 */
    fun pluggedKind(): PlugKind?

    /** 电池温度 °C；null = 读取失败 */
    fun batteryTempC(): Double?

    /** 距上次开机的毫秒数；null = 读取失败 */
    fun bootElapsedMillis(): Long?
}

/** 传感器扩展提供者：设备姿态 / 接近遮挡（null = 传感器不可用，判定 fail-closed 给原因） */
interface SensorExtraProvider {
    /** 当前设备姿态（加速度计快照三态） */
    fun pose(): PoseKind?

    /** 接近传感器被遮挡 */
    fun proximityCovered(): Boolean?
}

/** 应用生态提供者：已装应用 / 桌面小组件 / 蓝牙设备 */
interface AppEnvProvider {
    /** 本机已安装指定包名的应用 */
    fun isAppInstalled(packageName: String): Boolean

    /** 本应用的桌面小组件已绑定到启动器 */
    fun isAnyWidgetBound(): Boolean

    /** 已连接蓝牙设备名集合；null = 权限未授予（fail-closed 给原因），空集 = 无连接 */
    fun connectedBluetoothNames(): Set<String>?
}

/** 空气质量提供者；null = 获取失败（缓存策略同天气，由实现方负责） */
interface AirQualityProvider {
    fun aqi(cityId: String): Int?
}

/** 依赖胶囊状态查询者 */
interface DependencyChecker {
    fun statusOf(id: String): DependencyStatus
}

/** 下一闹钟提供者；null = 未设置闹钟（返回触发时刻 UTC 毫秒） */
interface AlarmProvider {
    fun nextAlarmMillis(): Long?
}

/** 系统模式提供者：省电 / 静音（含振动）/ 耳机 / 飞行模式 / 媒体播放 */
interface SystemModeProvider {
    fun isPowerSave(): Boolean

    /** 铃声静音或振动均视为静音 */
    fun isSilentRinger(): Boolean

    /** 有线或蓝牙音频输出已连接 */
    fun isHeadphoneConnected(): Boolean

    /** 系统飞行模式已开启（读系统设置，零权限） */
    fun isAirplaneModeOn(): Boolean

    /** 有音乐等媒体音频正在播放（快照语义：判定瞬间活跃即算） */
    fun isMusicPlaying(): Boolean
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

    /** 今日打开会话数（去重口径同 [openCount]；默认 0 = 旧实现未接入，按不满足处理） */
    fun todayOpenCount(): Int = 0

    /**
     * 今日指定应用的前台使用时长（分钟；储备池 v7 应用用量条件通道）。
     * null = 使用统计权限未授予 / 实现未接入（判定按「使用统计不可用」fail-closed）。
     * 默认 null = 旧实现未接入，既有实现与单测假件无需改动。
     */
    fun foregroundMinutesToday(packageName: String): Long? = null
}

/** 胶囊库元信息提供者 */
interface CapsuleMetaProvider {
    fun capsuleCount(): Int

    /** 指定胶囊是否已开启阅读过（meta `capsule.read.<id>`；写入点 DetailViewModel.markAsRead） */
    fun isRead(capsuleId: String): Boolean

    /** 指定胶囊详情页累计被打开次数（meta `capsule.views.<id>`；无记录为 0，写入点 DetailViewModel） */
    fun viewCount(capsuleId: String): Int

    // ---- 储备池 v4 扩展通道（带默认实现，旧实现与单测假件无需改动）----

    /** 已开启阅读过的胶囊总数（meta `capsule.read.*` 前缀计数；默认 0 = 未接入） */
    fun readCount(): Int = 0

    /** 尘迹档案数（destroyed_records 表计数；默认 0 = 未接入） */
    fun destroyCount(): Int = 0

    /** 累计创建胶囊总数（meta `app.created.total`；默认 0 = 未接入） */
    fun totalCreated(): Int = 0

    /** 是否完成过一次备份导出（meta `app.backup.done`；默认 false = 未接入） */
    fun backupDone(): Boolean = false

    /** 指定胶囊累计凝视秒数（meta `capsule.watch.<id>`；默认 0 = 未接入） */
    fun watchSeconds(capsuleId: String): Int = 0

    /** 指定胶囊最近一次开启阅读时刻（meta `capsule.readAt.<id>`；null = 从未读） */
    fun lastReadAt(capsuleId: String): Long? = null
}

/**
 * 条件判定上下文聚合类：UnlockJudgeUseCase 的唯一输入通道。
 *
 * v4 扩展通道（systemUi / deviceExtra / sensorExtra / appEnv / airQuality）全部
 * 可空且带 null 默认值：未注入 = 本场景不支持该通道，判定按"不可用"fail-closed
 * 处理；既有装配点与单测假件因此无需改动。
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
    val ambientLight: AmbientLightProvider,
    val systemUi: SystemUiStateProvider? = null,
    val deviceExtra: DeviceExtraProvider? = null,
    val sensorExtra: SensorExtraProvider? = null,
    val appEnv: AppEnvProvider? = null,
    val airQuality: AirQualityProvider? = null,
)
