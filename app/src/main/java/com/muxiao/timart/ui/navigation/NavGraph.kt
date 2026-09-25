package com.muxiao.timart.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.muxiao.timart.AppContainer
import com.muxiao.timart.ui.create.CreateScreen
import com.muxiao.timart.ui.detail.DetailScreen
import com.muxiao.timart.ui.settings.PasswordSetupScreen
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.TimartMotion

/** 进场轻缩放起点（280ms 缩放淡入，见 TimartMotion） */
private const val ENTER_SCALE = 0.92f

/**
 * 应用导航：NavHost + 路由表 + 统一缩放淡入转场。
 *
 * 四大主页面（时轨/星库/尘迹/设置）收敛在 [MainTabsScreen]：
 * HorizontalPager 承载、手势滑动切换、底部导航条联动（PRD 连续运动要求）；
 * NavHost 仅负责二级目的地（创建 / 详情 / 口令设置）的进出场。
 * Scaffold 只注入状态栏 inset，导航栏区域由底栏与各二级页自行处理。
 */
@Composable
fun NavGraph(container: AppContainer) {
    val navController = rememberNavController()

    val backStackEntry by navController.currentBackStackEntryAsState()

    // NFC 实体锚点直达（体验储备池 §4）：碰卡分发 → 导航详情并消费，胶囊不存在时详情页 MISSING 态兜底
    LaunchedEffect(container.pendingNfcCapsuleId) {
        val id = container.pendingNfcCapsuleId
        if (!id.isNullOrEmpty()) {
            container.pendingNfcCapsuleId = null
            navController.navigate(Routes.detail(id, firstUnlock = false))
        }
    }

    Scaffold(
        containerColor = DeepCharcoal,
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TIME_TRACK,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            enterTransition = {
                scaleIn(
                    initialScale = ENTER_SCALE,
                    animationSpec = tween(TimartMotion.ENTER_MILLIS, easing = TimartMotion.Easing),
                ) + fadeIn(tween(TimartMotion.ENTER_MILLIS, easing = TimartMotion.Easing))
            },
            exitTransition = {
                // 主页面（时轨）离场：淡出压到 140ms + 轻缩放（1→0.98），与新页交叉时拉开前后纵深
                if (initialState.destination.route == Routes.TIME_TRACK) {
                    scaleOut(
                        targetScale = 0.98f,
                        animationSpec = tween(TimartMotion.EXIT_HOME_MILLIS, easing = TimartMotion.Easing),
                    ) + fadeOut(tween(TimartMotion.EXIT_HOME_MILLIS))
                } else {
                    fadeOut(tween(TimartMotion.EXIT_MILLIS))
                }
            },
            popEnterTransition = {
                scaleIn(
                    initialScale = ENTER_SCALE,
                    animationSpec = tween(TimartMotion.ENTER_MILLIS, easing = TimartMotion.Easing),
                ) + fadeIn(tween(TimartMotion.ENTER_MILLIS, easing = TimartMotion.Easing))
            },
            popExitTransition = { fadeOut(tween(TimartMotion.EXIT_MILLIS)) },
        ) {
            composable(Routes.TIME_TRACK) {
                MainTabsScreen(
                    container = container,
                    // 导航级激活态：进入创建/详情/口令页的瞬间（转场开始）即置 false，
                    // Home 据此立即清空背景/循环粒子，不等 180ms 退场动画结束的 onDispose
                    navActive = backStackEntry?.destination?.route == Routes.TIME_TRACK,
                    onOpenDetail = { id, firstUnlock ->
                        navController.navigate(Routes.detail(id, firstUnlock))
                    },
                    onCreate = { navController.navigate(Routes.CREATE) },
                )
            }
            composable(Routes.CREATE) {
                CreateScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onNeedPasswordSetup = { navController.navigate(Routes.PASSWORD_SETUP) },
                )
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(
                    navArgument("capsuleId") { type = NavType.StringType },
                    navArgument("firstUnlock") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { entry ->
                DetailScreen(
                    container = container,
                    capsuleId = entry.arguments?.getString("capsuleId").orEmpty(),
                    firstUnlock = entry.arguments?.getBoolean("firstUnlock") ?: false,
                    onBack = { navController.popBackStack() },
                    // 回信转新胶囊（N5）：交接预填草稿后进创建页
                    onCreateCapsule = { navController.navigate(Routes.CREATE) },
                )
            }
            composable(Routes.PASSWORD_SETUP) {
                PasswordSetupScreen(
                    container = container,
                    onDone = { navController.popBackStack() },
                )
            }
        }
    }
}
