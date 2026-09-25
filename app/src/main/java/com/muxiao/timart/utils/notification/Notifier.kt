package com.muxiao.timart.utils.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.muxiao.timart.utils.RuntimeSettings
import com.muxiao.timart.l10n.stringsFor
import com.muxiao.timart.R

/**
 * 本地通知封装：解锁提醒渠道 `unlock_reminders`（API 26+ 建）。
 * Android 13+ 无 POST_NOTIFICATIONS 权限则静默跳过（拒绝仅收不到提醒，不影响判定）。
 */
class Notifier(private val context: Context) {

    /** 渠道创建（Application onCreate 调用，幂等） */
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            stringsFor(RuntimeSettings.resolvedLang).notifChannelName,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = stringsFor(RuntimeSettings.resolvedLang).notifChannelDesc
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 60, 80, 40)
        }
        manager.createNotificationChannel(channel)
    }

    /** 解锁提醒：正文含胶囊标题（无标题回退通用文案）+ 轻震动（渠道振动） */
    fun notifyUnlock(capsuleId: String, capsuleTitle: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // 33+ 无权限：静默跳过
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val s = stringsFor(RuntimeSettings.resolvedLang)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(s.notifUnlockTitle)
            .setContentText(
                capsuleTitle?.let { s.notifUnlockBodyFmt.format(it) } ?: s.notifUnlockBody,
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVibrate(longArrayOf(0, 60, 80, 40))

        try {
            NotificationManagerCompat.from(context).notify(capsuleId.hashCode(), builder.build())
        } catch (_: SecurityException) {
            // 竞态下的权限回收：跳过即可
        }
    }

    /** 临近解锁提醒（N1）：确定性时间条件距到达只剩提前量窗口内的整天数（Worker 周期触发，每档去重由调用方负责） */
    fun notifyUpcoming(capsuleId: String, capsuleTitle: String?, daysLeft: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val s = stringsFor(RuntimeSettings.resolvedLang)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(s.notifUpcomingTitle)
            .setContentText(
                capsuleTitle?.let { s.notifUpcomingBodyFmt.format(daysLeft, it) }
                    ?: s.notifUpcomingTitle,
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVibrate(longArrayOf(0, 60, 80, 40))

        try {
            NotificationManagerCompat.from(context)
                .notify(("upcoming_$capsuleId").hashCode(), builder.build())
        } catch (_: SecurityException) {
        }
    }

    /**
     * 天气预告（储备池 v6）：概率性天气条件的「接近可解」提示（预报满足时 Worker 周期触发，
     * 每天最多一条去重由调用方负责）。只预告不判锁——解锁仍由判定引擎依据实况天气决定。
     */
    fun notifyForecast(capsuleId: String, capsuleTitle: String?, weatherName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val s = stringsFor(RuntimeSettings.resolvedLang)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(s.notifForecastTitle)
            .setContentText(
                capsuleTitle?.let { s.notifForecastBodyFmt.format(weatherName, it) }
                    ?: s.notifForecastTitle,
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        try {
            NotificationManagerCompat.from(context)
                .notify(("forecast_$capsuleId").hashCode(), builder.build())
        } catch (_: SecurityException) {
        }
    }

    companion object {
        const val CHANNEL_ID = "unlock_reminders"
    }
}
