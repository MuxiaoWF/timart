package com.muxiao.timart.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.muxiao.timart.AppContainer
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.cosmic.HomeScreen
import com.muxiao.timart.ui.settings.SettingsScreen
import com.muxiao.timart.ui.starlibrary.DustRecordsScreen
import com.muxiao.timart.ui.starlibrary.StarLibraryScreen
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimartMotion
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TrackHairline
import kotlinx.coroutines.launch

/**
 * 四大主页面容器（PRD §2.2 页脚导航）：HorizontalPager 承载 时轨 / 星库 / 尘迹 / 设置，
 * 支持手势横向滑动切换（核心对象连续运动，不整页替换），底部导航条与页码双向联动。
 * 页面切换动效 = pager 自带位移 + 页内容保持组合态（无独立进场动画，符合 §2.3 连续性要求）。
 */
@Composable
fun MainTabsScreen(
    container: AppContainer,
    /** 导航级激活态：本容器是否是当前 NavHost 目的地（false = 正在进转场离开），透传给 Home 即时收粒子 */
    navActive: Boolean = true,
    onOpenDetail: (capsuleId: String, firstUnlock: Boolean) -> Unit,
    onCreate: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { MAIN_TAB_COUNT })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().background(DeepCharcoal)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            when (page) {
                0 -> HomeScreen(
                    container = container,
                    pageActive = pagerState.currentPage == 0,
                    navActive = navActive,
                    onOpenDetail = onOpenDetail,
                    onCreate = onCreate,
                )

                1 -> StarLibraryScreen(
                    container = container,
                    onOpenDetail = { onOpenDetail(it, false) },
                )

                2 -> DustRecordsScreen(container = container)
                else -> SettingsScreen(container = container)
            }
        }
        TimartBottomBar(
            selectedPage = pagerState.currentPage,
            onSelect = { page ->
                scope.launch {
                    pagerState.animateScrollToPage(
                        page,
                        animationSpec = tween(TimartMotion.CONTENT_MILLIS, easing = TimartMotion.Easing),
                    )
                }
            },
        )
    }
}

/** 底部导航条：四页签各用专属图标（TabIcons），选中项为「金色图标 + 加宽光晕胶囊」 */
@Composable
private fun TimartBottomBar(selectedPage: Int, onSelect: (Int) -> Unit) {
    val L = LocalStrings.current
    val labels = listOf(
        L.tabTimeTrack,
        L.tabStarLibrary,
        L.tabDustRecords,
        L.tabSettings,
    )
    val icons = listOf(TabIcons.timeTrack, TabIcons.starLibrary, TabIcons.dustRecords, TabIcons.settings)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceRaise)
            .navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(TrackHairline),
        )
        Row(modifier = Modifier.fillMaxWidth().height(BOTTOM_BAR_HEIGHT)) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedPage
                val glowWidth by animateDpAsState(
                    targetValue = if (selected) 64.dp else 34.dp,
                    animationSpec = tween(TimartMotion.CONTENT_MILLIS),
                    label = "tabGlow",
                )
                // A10：光晕胶囊底色随宽度同规格淡入淡出（原为二值瞬变，观感是"宽度滑动、颜色跳变"）
                val glowColor by animateColorAsState(
                    targetValue = if (selected) TimeGold.copy(alpha = 0.16f) else Color.Transparent,
                    animationSpec = tween(TimartMotion.CONTENT_MILLIS),
                    label = "tabGlowColor",
                )
                val labelAlpha by animateFloatAsState(
                    targetValue = if (selected) 1f else 0.62f,
                    animationSpec = tween(TimartMotion.CONTENT_MILLIS),
                    label = "tabLabel",
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable { onSelect(index) }
                        .padding(top = 8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(glowWidth)
                            .height(26.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(glowColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icons[index],
                            contentDescription = label,
                            tint = if (selected) TimeGold else InkSecondary.copy(alpha = 0.55f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = label,
                        style = TimartType.caption,
                        color = if (selected) InkPrimary else InkSecondary,
                        modifier = Modifier.alpha(labelAlpha),
                    )
                }
            }
        }
    }
}

/** 主 Tab 数量 */
const val MAIN_TAB_COUNT = 4

/** 底部导航条内容高度 */
private val BOTTOM_BAR_HEIGHT = 66.dp
