package com.muxiao.timart.utils.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 权限状态查询与"是否需要引导申请"判断。
 * 原则：权限拒绝 → 对应条件判定不满足；不强制索取。
 */
class PermissionHelper(private val context: Context) {

    /** 任意权限是否已授予 */
    fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** 位置（前台）权限：GPS 条件使用 */
    fun hasLocationPermission(): Boolean =
        isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    /** 活动记录权限：仅 API 29+ 需要；低版本直接读传感器，视为已授予 */
    fun hasActivityRecognitionPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            isGranted(Manifest.permission.ACTIVITY_RECOGNITION)

    /** 通知权限：仅 API 33+ 需要；低版本视为已授予 */
    fun hasPostNotificationsPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            isGranted(Manifest.permission.POST_NOTIFICATIONS)

    /** GPS 条件是否应引导申请位置权限 */
    fun shouldRequestLocation(): Boolean = !hasLocationPermission()

    /** 步数条件是否应引导申请活动记录权限（29+） */
    fun shouldRequestActivityRecognition(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasActivityRecognitionPermission()

    /** 是否应引导申请通知权限（33+） */
    fun shouldRequestPostNotifications(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPostNotificationsPermission()
}
