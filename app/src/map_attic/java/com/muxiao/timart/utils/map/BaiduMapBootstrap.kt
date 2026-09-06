package com.muxiao.timart.utils.map

import android.content.Context
import android.content.pm.PackageManager
import com.baidu.mapapi.SDKInitializer

/**
 * 百度地图 SDK 懒初始化（仅 GPS 条件在线选点使用）。
 *
 * - 不在 Application 启动链上初始化：应用整体承诺零联网，地图只在用户进入 GPS 条件表单时才拉起；
 * - 工信部合规要求：setAgreePrivacy 必须先于 initialize；
 * - AK 缺失（local.properties 未配置 BAIDU_MAP_AK）或初始化异常时返回 false，
 *   调用方回退到离线经纬画布（LatLngPickerCanvas），功能可用性不受影响。
 */
object BaiduMapBootstrap {

    @Volatile
    private var available: Boolean? = null

    /** 是否已初始化成功（线程安全，幂等） */
    fun ensureInitialized(context: Context): Boolean {
        available?.let { return it }
        return synchronized(this) {
            available?.let { return it }
            val appContext = context.applicationContext
            val ak = readManifestAk(appContext)
            val ok = ak.isNotEmpty() && runCatching {
                SDKInitializer.setAgreePrivacy(appContext, true)
                SDKInitializer.initialize(appContext)
            }.onFailure {
                android.util.Log.e(TAG, "百度地图 SDK 初始化失败", it)
            }.isSuccess
            android.util.Log.i(TAG, "baidu map available=$ok akLen=${ak.length}")
            available = ok
            ok
        }
    }

    private fun readManifestAk(context: Context): String = runCatching {
        val info = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA,
        )
        info.metaData?.getString("com.baidu.lbsapi.API_KEY").orEmpty().trim()
    }.getOrDefault("")

    private const val TAG = "BaiduMapBootstrap"
}
