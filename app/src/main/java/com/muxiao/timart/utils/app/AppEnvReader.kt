package com.muxiao.timart.utils.app

import android.appwidget.AppWidgetManager
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.muxiao.timart.domain.context.AppEnvProvider

/**
 * 应用生态快照读取（已装应用 / 桌面小组件 / 蓝牙设备；储备池 v4 判定通道）。
 *
 * - 已装应用：queries 声明的包名可零权限查询；未声明一律 false（系统不暴露）；
 * - 小组件：扫描本应用全部 AppWidgetProvider 的已绑定 appWidgetIds（零权限）；
 * - 蓝牙设备名：走 AudioManager 输出设备通道（零权限，与耳机判定同源）；
 *   productName 为厂商随设备下发的友好名，个别 ROM 可能返回厂商名而非设备名。
 *   本通道无权限前置，恒返回非空可空集（无连接即空集）。
 */
class AppEnvReader(private val context: Context) : AppEnvProvider {

    override fun isAppInstalled(packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    override fun isAnyWidgetBound(): Boolean = runCatching {
        val manager = AppWidgetManager.getInstance(context) ?: return@runCatching false
        val providers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.getInstalledProvidersForPackage(context.packageName, null)
        } else {
            manager.installedProviders.filter { it.provider.packageName == context.packageName }
        }
        providers.any { provider ->
            runCatching { manager.getAppWidgetIds(provider.provider).isNotEmpty() }.getOrDefault(false)
        }
    }.getOrDefault(false)

    override fun connectedBluetoothNames(): Set<String>? = runCatching {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return@runCatching emptySet()
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in BLUETOOTH_OUTPUT_TYPES }
            .mapNotNull { device ->
                device.productName?.toString()?.trim()?.takeIf { name -> name.isNotEmpty() }
            }
            .toSet()
    }.getOrNull()

    private companion object {
        val BLUETOOTH_OUTPUT_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        )
    }
}
