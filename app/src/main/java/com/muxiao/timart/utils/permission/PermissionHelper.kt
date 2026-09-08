package com.muxiao.timart.utils.permission

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 高版本 API 权限字段（POST_NOTIFICATIONS = API 33 / ACTIVITY_RECOGNITION = API 29）：
 * 编译期内联为常量字符串，minSdk 24 下直接使用平台契约字面量，避免 lint InlinedApi 告警
 * （跨 UI 文件共享此定义，见 AGENTS.md §7.22）。
 */
internal const val PERM_POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
internal const val PERM_ACTIVITY_RECOGNITION = "android.permission.ACTIVITY_RECOGNITION"

/**
 * 权限状态查询与"是否需要引导申请"判断。
 * 原则：权限拒绝 → 对应条件判定不满足；不强制索取。
 */
class PermissionHelper(private val context: Context) {

    /** 任意权限是否已授予 */
    fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** 通知权限：仅 API 33+ 需要；低版本视为已授予 */
    fun hasPostNotificationsPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            isGranted(PERM_POST_NOTIFICATIONS)

    /** 是否应引导申请通知权限（33+） */
    fun shouldRequestPostNotifications(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPostNotificationsPermission()
}
