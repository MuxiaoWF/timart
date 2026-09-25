package com.muxiao.timart.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.muxiao.timart.MainActivity
import com.muxiao.timart.R
import com.muxiao.timart.TimartApplication
import com.muxiao.timart.domain.usecase.UpcomingReminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil

/**
 * 单球小组件（储备池 v7）：1×1 重点倒计时球——临近可解的那颗胶囊的剩余整天数。
 * 数据口径与列表小组件一致（复用 [UpcomingReminders.fixedTargetAt]，锚定封存日的
 * 相对条件同样参与；休眠种子跳过不泄露；挑战/传感器类不可预估不猜测）。
 * **仅剩余天数元数据：标题/密文/判定结果不进 1×1**（尺寸只够一个数字）。
 * 刷新：6h 周期 Worker 收尾 [refreshAll] + 系统刷新回调（onUpdate）；点击深链详情。
 */
class TimartWidgetBallProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val days = nearestDaysLeft(context)
                appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, render(context, days, null)) }
            } catch (_: Throwable) {
                // 数据不可得时退化为静默圆点（不展示占位错误）
            } finally {
                pending.finish()
            }
        }
    }

    /** Worker 收尾触发的全量刷新（与列表小组件同拍；无实例 no-op） */
    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, TimartWidgetBallProvider::class.java))
        if (ids.isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nearest = nearestEntry(context)
                val views = render(context, nearest?.daysLeft, nearest?.id)
                ids.forEach { id -> manager.updateAppWidget(id, views) }
            } catch (_: Throwable) {
                // 刷新失败保持旧视图（下个 6h 周期自然重试）
            }
        }
    }

    /** 最近可预估目标剩余整天数（无可预估胶囊时 null → 静默圆点） */
    private suspend fun nearestDaysLeft(context: Context): Int? = nearestEntry(context)?.daysLeft

    private suspend fun nearestEntry(context: Context): BallEntry? {
        val app = context.applicationContext as? TimartApplication ?: return null
        val container = app.container
        val locked = container.capsuleRepository.allLockedSync()
            .filter { !container.isSeedDormant(it.id) }
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val dayMs = 24L * 60 * 60 * 1000
        return locked.mapNotNull { capsule ->
            val createdDay = Instant.ofEpochMilli(capsule.createTimestamp).atZone(zone).toLocalDate()
            val target = capsule.unlockRule.conditionList
                .mapNotNull { UpcomingReminders.fixedTargetAt(it, now, zone, createdDay) }
                .minOrNull() ?: return@mapNotNull null
            BallEntry(
                id = capsule.id,
                daysLeft = ceil((target - now).toDouble() / dayMs).toInt().coerceAtLeast(1),
            )
        }.minByOrNull { it.daysLeft }
    }

    /** 渲染：数字球体（daysLeft = null → 静默圆点；id 非空时深链该胶囊） */
    private fun render(context: Context, daysLeft: Int?, capsuleId: String?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_ball)
        if (daysLeft != null) {
            views.setTextViewText(R.id.widget_ball_days, daysLeft.toString())
        } else {
            views.setTextViewText(R.id.widget_ball_days, context.getString(R.string.widget_ball_placeholder))
        }
        val clickIntent = Intent(context, MainActivity::class.java).apply {
            capsuleId?.let { putExtra(MainActivity.EXTRA_OPEN_CAPSULE_ID, it) }
        }
        views.setOnClickPendingIntent(
            R.id.widget_ball_root,
            PendingIntent.getActivity(
                context,
                capsuleId?.hashCode() ?: 0,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        return views
    }

    /** 单球数据（daysLeft 为整天数向上取整，≥1） */
    private data class BallEntry(val id: String, val daysLeft: Int)
}
