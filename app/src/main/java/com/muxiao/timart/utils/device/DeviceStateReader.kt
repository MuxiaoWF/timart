package com.muxiao.timart.utils.device

import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.SystemClock
import android.provider.Settings
import com.muxiao.timart.domain.context.DeviceExtraProvider
import com.muxiao.timart.domain.context.SystemUiStateProvider
import com.muxiao.timart.domain.model.unlock.PlugKind

/**
 * 系统界面状态 + 设备硬件状态快照读取（储备池 v4 判定通道）。
 * 与 [SystemModeReader] 同款设计：全部同步零回调，每次判定即时读取，
 * 服务缺失按安全默认值返回，永不抛异常打断判定链。
 */
class DeviceStateReader(context: Context) : SystemUiStateProvider, DeviceExtraProvider {

    private val appContext = context.applicationContext
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    override fun isDarkThemeOn(): Boolean =
        (appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    override fun isLandscape(): Boolean =
        appContext.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    override fun isDndActive(): Boolean = runCatching {
        notificationManager?.currentInterruptionFilter
            ?.let { it != NotificationManager.INTERRUPTION_FILTER_ALL } ?: false
    }.getOrDefault(false)

    override fun screenBrightnessLevel(): Int? = runCatching {
        Settings.System.getInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
            .takeIf { it >= 0 }
    }.getOrNull()

    override fun isMediaMuted(): Boolean = runCatching {
        val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        (audio?.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) ?: 0) == 0
    }.getOrDefault(true)

    // allNetworks 自 API 31 起弃用且无同步替代（网络枚举只剩回调通道）；判定引擎需要同步快照，保留并抑制
    @Suppress("DEPRECATION")
    override fun isVpnActive(): Boolean = runCatching {
        val cm = connectivityManager ?: return@runCatching false
        cm.allNetworks.any { network ->
            val caps = cm.getNetworkCapabilities(network)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }.getOrDefault(false)

    override fun pluggedKind(): PlugKind? {
        // 插拔类型唯一同步通道 = 系统 sticky 电池广播（无需注册常驻 receiver）
        val intent = appContext.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        return when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
            BatteryManager.BATTERY_PLUGGED_AC -> PlugKind.AC
            BatteryManager.BATTERY_PLUGGED_USB -> PlugKind.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> PlugKind.WIRELESS
            else -> null // 未充电（0）或未知（-1）
        }
    }

    override fun batteryTempC(): Double? {
        val intent = appContext.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        if (tenths == Int.MIN_VALUE) return null
        return tenths / 10.0
    }

    override fun bootElapsedMillis(): Long = SystemClock.elapsedRealtime()
}
