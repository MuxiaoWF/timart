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
import com.muxiao.timart.l10n.currentStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/**
 * 时粒桌面小组件（储备池 v4 WidgetBound 条件的绑定事实源）：
 * 品牌行 + 叙事一句（N21 列表化）+ **临近待解锁列表**——LOCKED 且含确定性时间条件
 * （FixedDate / FixedDateTime / YearlyDate，复用 [UpcomingReminders.fixedTargetAt] 估算，
 * 相对/挑战条件不可预估不猜测）的胶囊按目标时刻升序取前 3 行（标题 + 倒计时），
 * 整行深链直达详情（经 MainActivity extra → `pendingNfcCapsuleId` 同一交接位）。
 * **仅元数据与确定时间：正文密文与判定结果不进小组件**（无口令会话也不泄露任何内容）。
 * 刷新：6h 周期 Worker 收尾 [refreshAll] + 系统刷新回调（onUpdate）。
 */
class TimartWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entries = upcomingEntries(context)
                appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, render(context, entries)) }
            } catch (_: Throwable) {
                // 数据不可得时退化为静态品牌卡（列表留空，不展示占位错误）
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Worker 收尾触发的全量刷新（6h 周期与提醒/判定同拍）：
     * 枚举本 provider 全部实例逐个重渲染；无实例时为 no-op。
     * （Worker 线程直调，不走 [onUpdate] 的 goAsync 路径——goAsync 仅限 onReceive。）
     */
    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, TimartWidgetProvider::class.java))
        if (ids.isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entries = upcomingEntries(context)
                ids.forEach { id -> manager.updateAppWidget(id, render(context, entries)) }
            } catch (_: Throwable) {
                // 刷新失败保持旧视图（下个 6h 周期自然重试）
            }
        }
    }

    // ---- 数据装配（IO 线程） ----

    /** 待展示行：确定性目标时刻升序前 3 个（仅 LOCKED；休眠种子跳过，不泄露隐藏种子） */
    private suspend fun upcomingEntries(context: Context): List<Entry> {
        val app = context.applicationContext as? TimartApplication ?: return emptyList()
        val container = app.container
        val locked = container.capsuleRepository.allLockedSync()
            .filter { !container.isSeedDormant(it.id) }
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val dayMs = 24L * 60 * 60 * 1000
        val dateFmt = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        return locked.mapNotNull { capsule ->
            val target = capsule.unlockRule.conditionList
                .mapNotNull { UpcomingReminders.fixedTargetAt(it, now, zone) }
                .minOrNull() ?: return@mapNotNull null
            Entry(
                id = capsule.id,
                title = capsule.title,
                daysLeft = ceil((target - now).toDouble() / dayMs).toInt().coerceAtLeast(1),
                dateText = dateFmt.format(Date(target)),
            )
        }.sortedBy { it.daysLeft }.take(MAX_ROWS)
    }

    /** 渲染：品牌行固定；列表逐行动态填充（addView 复用同一 item 布局） */
    private fun render(context: Context, entries: List<Entry>): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_timart)
        // 品牌卡整卡点击 = 打开应用
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        views.removeAllViews(R.id.widget_rows)
        val L = currentStrings()
        entries.forEach { entry ->
            val row = RemoteViews(context.packageName, R.layout.widget_row)
            row.setTextViewText(R.id.widget_row_title, entry.title)
            row.setTextViewText(R.id.widget_row_countdown, L.widgetCountdownFmt.format(entry.daysLeft, entry.dateText))
            row.setOnClickPendingIntent(
                R.id.widget_row_root,
                PendingIntent.getActivity(
                    context,
                    entry.id.hashCode(),
                    Intent(context, MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_OPEN_CAPSULE_ID, entry.id)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            views.addView(R.id.widget_rows, row)
        }
        return views
    }

    /** 一行待解锁（daysLeft 为整天数向上取整，≥1） */
    private data class Entry(val id: String, val title: String, val daysLeft: Int, val dateText: String)

    private companion object {
        /** 桌面卡可见行数上限（超出部分进应用内星库查看） */
        const val MAX_ROWS = 3
    }
}
