package com.muxiao.timart.ui.navigation

/**
 * 导航路由表（ARCHITECTURE §9）。
 *
 * 四大主页面（时轨 / 星库 / 尘迹 / 设置）不再是独立路由目的地：
 * 由 [MainTabsScreen] 的 HorizontalPager 承载（手势滑动切换，状态保留），
 * NavHost 仅注册下列二级目的地。
 */
object Routes {

    /** 主页面容器（含 4 个 pager 页） */
    const val TIME_TRACK = "timetrack"

    /** 三步封存（内部 pager，非路由切换） */
    const val CREATE = "create"

    /** 胶囊详情四状态 */
    const val DETAIL = "detail/{capsuleId}?firstUnlock={firstUnlock}"

    /** 详情路由参数构造 */
    fun detail(id: String, firstUnlock: Boolean = false) = "detail/$id?firstUnlock=$firstUnlock"

    /** 首次保存前的口令引导（半透明对话框式目的地） */
    const val PASSWORD_SETUP = "passwordSetup"
}
