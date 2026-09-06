package com.muxiao.timart.utils.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.muxiao.timart.domain.context.NetworkProvider
import com.muxiao.timart.domain.model.unlock.NetType

/**
 * 网络类型判定（ConnectivityManager，零运行时权限）。
 * API 29+ 走 networkCapabilities，23-28 走 activeNetworkInfo。
 */
class NetworkTypeDetector(context: Context) : NetworkProvider {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun current(): NetType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        currentModern()
    } else {
        currentLegacy()
    }

    private fun currentModern(): NetType {
        val network = connectivityManager.activeNetwork ?: return NetType.NONE
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetType.NONE
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> NetType.CELLULAR
            else -> NetType.NONE
        }
    }

    @Suppress("DEPRECATION")
    private fun currentLegacy(): NetType {
        val info = connectivityManager.activeNetworkInfo ?: return NetType.NONE
        if (!info.isConnected) return NetType.NONE
        return when (info.type) {
            ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_ETHERNET -> NetType.WIFI
            ConnectivityManager.TYPE_MOBILE, ConnectivityManager.TYPE_WIMAX -> NetType.CELLULAR
            else -> NetType.NONE
        }
    }

    /** 仅供调试排查：人类可读的网络名（不参与判定） */
    fun describe(type: NetType = current()): String = when (type) {
        NetType.WIFI -> "Wi-Fi"
        NetType.CELLULAR -> "移动数据"
        NetType.NONE -> "无网络"
    }
}
