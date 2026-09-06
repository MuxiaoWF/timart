package com.muxiao.timart.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearOutSlowInEasing

/**
 * 全局动效时长体系（B4 收敛）：应用全线 tween + LinearOutSlowInEasing，禁 spring 弹跳。
 * 此前时长魔法数散落在 NavGraph / MainTabsScreen / CreateScreen / LogicSwitch 四处
 * 且有一处 250ms 离群值，现统一为三个档位：
 *
 * - [ENTER_MILLIS] 280ms：二级路由进场（缩放 0.92→1 淡入，NavGraph）
 * - [EXIT_MILLIS] 180ms：二级路由离场淡出（NavGraph / CreateScreen 步进退场）
 * - [EXIT_HOME_MILLIS] 140ms：时轨主页离场（更快 + 轻缩放，与新页交叉拉开纵深）
 * - [CONTENT_MILLIS] 240ms：内容级转场（主 Tab 指示 / 创建步进滑动 / 逻辑切换重排 /
 *   页内元素淡入），全部对齐此值，不再有离群时长
 */
object TimartMotion {
    const val ENTER_MILLIS = 280
    const val EXIT_MILLIS = 180
    const val EXIT_HOME_MILLIS = 140
    const val CONTENT_MILLIS = 240

    /** 全局统一缓动（先快后慢，无回弹） */
    val Easing: Easing = LinearOutSlowInEasing
}
