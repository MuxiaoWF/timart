package com.muxiao.timart.widget

/**
 * 锁屏小组件（Android 12+ 设备支持锁屏组件的形态）：单球的锁屏变体。
 * 数据口径、渲染与点击深链全部复用 [TimartWidgetBallProvider]——锁屏形态比桌面版更克制，
 * 只显示单球剩余天数（manifest 独立 receiver + timart_widget_lock_info.xml，widgetCategory=keyguard）。
 * 刷新：6h 周期 Worker 收尾 refreshAll（继承，组件类覆写为自身）+ 系统刷新回调（继承 onUpdate）。
 */
class TimartWidgetLockProvider : TimartWidgetBallProvider() {

    override fun widgetComponentClass(): Class<*> = TimartWidgetLockProvider::class.java
}
