package com.muxiao.timart.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.muxiao.timart.MainActivity
import com.muxiao.timart.R

/**
 * 时粒桌面小组件（储备池 v4 WidgetBound 条件的绑定事实源）：
 * 极简静态卡（应用名 + 叙事一句），点击进主页面。
 * 绑定与否由 AppEnvReader.isAnyWidgetBound 扫描本 provider 的 appWidgetIds 判定。
 */
class TimartWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_timart)
        val launch = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, launch)
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
    }
}
