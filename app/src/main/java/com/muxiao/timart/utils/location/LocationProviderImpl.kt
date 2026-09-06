package com.muxiao.timart.utils.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.muxiao.timart.domain.context.LocationProvider
import com.muxiao.timart.domain.model.unlock.GeoPoint

/**
 * LocationManager 实现的 LocationProvider。
 *
 * - 构造参数 [foregroundOnly]：Worker 场景传 true（后台不判 GPS，判定侧按 skipped 处理）；
 * - [lastKnown] 返回 GPS/NETWORK 最近定位中的更新者，并异步预热一次刷新（fire-and-forget）；
 * - API 30+ 用 getCurrentLocation，24-29 用 requestSingleUpdate。
 */
class LocationProviderImpl(
    private val context: Context,
    override val foregroundOnly: Boolean = false,
) : LocationProvider {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /** 异步刷新得到的新鲜定位（尽力而为，不阻塞同步查询） */
    @Volatile
    private var freshFix: GeoPoint? = null

    override fun isPermitted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override fun lastKnown(): GeoPoint? {
        if (!isPermitted()) return null
        freshFix?.let { return it }

        val candidates = mutableListOf<Location>()
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.getLastKnownLocation(provider)?.let { candidates.add(it) }
                }
            } catch (_: SecurityException) {
                // 权限在检查后被系统回收，跳过该 provider
            } catch (_: IllegalArgumentException) {
                // 设备无该 provider
            }
        }
        val best = candidates.maxByOrNull { it.time } ?: return null
        requestSingleRefresh()
        return GeoPoint(best.latitude, best.longitude)
    }

    /** 异步预热下一次定位结果（失败静默，不影响本次同步返回） */
    @SuppressLint("MissingPermission")
    private fun requestSingleRefresh() {
        if (!isPermitted()) return
        try {
            val provider = when {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                    LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                    LocationManager.NETWORK_PROVIDER
                else -> return
            }
            val listener = LocationListener { location ->
                freshFix = GeoPoint(location.latitude, location.longitude)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                locationManager.getCurrentLocation(
                    provider,
                    null,
                    ContextCompat.getMainExecutor(context),
                ) { location ->
                    // 平台约定：无法取得定位时以 null 回调（provider 超时/无 fix），必须判空
                    location?.let { freshFix = GeoPoint(it.latitude, it.longitude) }
                }
            } else {
                @Suppress("DEPRECATION")
                locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        } catch (_: Throwable) {
            // 定位刷新失败不影响 lastKnown 缓存路径
        }
    }
}
