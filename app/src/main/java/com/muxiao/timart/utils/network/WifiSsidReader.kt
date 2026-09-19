package com.muxiao.timart.utils.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.muxiao.timart.domain.context.WifiProvider
import com.muxiao.timart.domain.context.WifiSsidInfo
import com.muxiao.timart.domain.context.WifiSsidState

/**
 * 当前 Wi-Fi SSID 读取（SSID 条件的判定通道）。
 *
 * 平台约束：Android 8.1+（26+）读取 SSID 前置两个系统条件——
 * ① ACCESS_FINE_LOCATION 运行时权限已授予；② 系统定位服务已开启。
 * 任一缺失即按对应状态返回（判定位 fail-closed + 原因文案），不静默当未连接。
 *
 * 返回的 SSID 去除系统历史遗留的引号包裹（"MyHome" → MyHome）。
 */
class WifiSsidReader(private val context: Context) : WifiProvider {

    private val wifiManager =
        context.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    override fun current(): WifiSsidInfo {
        // 定位权限 + 定位服务是 API 27+ 读取 SSID 的系统前置；更低版本直接读
        val gated = Build.VERSION.SDK_INT >= 27
        val permitted = !gated ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!permitted) return WifiSsidInfo(WifiSsidState.NO_PERMISSION)

        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val locationOn = !gated || locationManager.let { lm ->
            lm != null && (
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                )
        }
        if (!locationOn) return WifiSsidInfo(WifiSsidState.NO_LOCATION_SERVICE)

        val manager = wifiManager ?: return WifiSsidInfo(WifiSsidState.NOT_CONNECTED)
        // 单次读取 connectionInfo 快照（弃用 API 无同步替代；SSID 与 BSSID 须取自同一快照）
        @Suppress("DEPRECATION")
        val info = try {
            manager.connectionInfo
        } catch (_: SecurityException) {
            null
        }
        val ssid = info?.ssid
            ?.removeSurrounding("\"")
            ?.takeIf { it != UNKNOWN_SSID && it.isNotBlank() }
        return if (ssid == null) {
            WifiSsidInfo(WifiSsidState.NOT_CONNECTED)
        } else {
            // 部分 ROM 可能返回空/02:00:00:00:00:00 占位
            val bssid = info.bssid
                ?.takeIf { it.isNotBlank() && it != DEFAULT_BSSID_PLACEHOLDER }
            WifiSsidInfo(WifiSsidState.CONNECTED, ssid, bssid)
        }
    }

    private companion object {
        /** 未连接时部分 ROM 返回字面量 <unknown ssid> */
        const val UNKNOWN_SSID = "<unknown ssid>"

        /** 部分 ROM 未连接/受限时返回的 BSSID 占位值 */
        const val DEFAULT_BSSID_PLACEHOLDER = "02:00:00:00:00:00"
    }
}
