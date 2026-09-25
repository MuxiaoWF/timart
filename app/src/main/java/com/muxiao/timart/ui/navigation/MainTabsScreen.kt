package com.muxiao.timart.ui.navigation

import android.annotation.SuppressLint
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
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
import com.muxiao.timart.ui.theme.rememberWindowAdaptive
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

/**
 * 四大主页面容器（PRD §2.2 页脚导航）：HorizontalPager 承载 时轨 / 星库 / 尘迹 / 设置，
 * 支持手势横向滑动切换（核心对象连续运动，不整页替换），导航条与页码双向联动。
 * 页面切换动效 = pager 自带位移 + 页内容保持组合态（无独立进场动画，符合 §2.3 连续性要求）。
 * 竖屏 = 底部导航条；高度紧凑（手机横屏，见 `ui/theme/Adaptive.kt`）= 左侧栏，
 * 两种形态页签视觉同规格，Pager 始终横向滑动。
 */
@SuppressLint("FrequentlyChangingValue")
@Composable
fun MainTabsScreen(
    container: AppContainer,
    onOpenDetail: (capsuleId: String, firstUnlock: Boolean) -> Unit,
    onCreate: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { MAIN_TAB_COUNT })
    val scope = rememberCoroutineScope()
    val adaptive = rememberWindowAdaptive()

    // 连续页位置（currentPage + offsetFraction）：底栏/侧栏光晕随手指与翻页动画连续跟随，
    // 取代「翻页过半瞬间整体跳档」的离散联动
    val pagePosition = pagerState.currentPage + pagerState.currentPageOffsetFraction

    val onSelect: (Int) -> Unit = { page ->
        scope.launch {
            pagerState.animateScrollToPage(
                page,
                animationSpec = tween(TimartMotion.CONTENT_MILLIS, easing = TimartMotion.Easing),
            )
        }
    }

    if (adaptive.isCompactHeight) {
        Row(modifier = Modifier.fillMaxSize().background(DeepCharcoal)) {
            TimartSideRail(
                pagePosition = pagePosition,
                onSelect = onSelect,
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) { page ->
                MainTabPage(page, container, pagerState, onOpenDetail, onCreate)
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().background(DeepCharcoal)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                MainTabPage(page, container, pagerState, onOpenDetail, onCreate)
            }
            TimartBottomBar(
                pagePosition = pagePosition,
                onSelect = onSelect,
            )
        }
    }
}

/** 四页内容分发（底栏/侧栏两种形态共享，避免 when 双份漂移） */
@Composable
private fun MainTabPage(
    page: Int,
    container: AppContainer,
    pagerState: PagerState,
    onOpenDetail: (capsuleId: String, firstUnlock: Boolean) -> Unit,
    onCreate: () -> Unit,
) {
    when (page) {
        0 -> HomeScreen(
            container = container,
            // settledPage（落定页）而非 currentPage：翻页过半即清会让页面半可见时尘瞬灭——
            // 循环尘带 OWNER_HOME 归属标记，迟清不会跨页残影；落定后再清场/认领。
            // 无需导航级激活位：循环尘只渲染在宿主页画布（引擎 owner 过滤，跨页无残影），
            // 进详情时 BREATHE 认领（releaseLoopsExcept）本就会立即释放 home 尘
            pageActive = pagerState.settledPage == 0,
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

/** 底部导航条：四页签各用专属图标（TabIcons），选中项为「金色图标 + 加宽光晕胶囊」；
 *  光晕/墨色按与 [pagePosition]（连续页位置）的距离插值，随手指与翻页动画连续跟随 */
@Composable
private fun TimartBottomBar(pagePosition: Float, onSelect: (Int) -> Unit) {
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
                // A10 续（连续联动）：按「本页与当前连续页位置的距离」插值，落定时邻近度
                // 恰为 1/0，静态观感与原选中态一致；翻页全程连续无跳档。
                // 原三重 animate*AsState 的过渡职责由 pager 自身的连续位置接管
                val proximity = (1f - (pagePosition - index).absoluteValue).coerceIn(0f, 1f)
                val glowWidth = lerp(34.dp, 64.dp, proximity)
                // 光晕胶囊底色随宽度同规格淡入淡出
                val glowColor = lerp(Color.Transparent, TimeGold.copy(alpha = 0.16f), proximity)
                val labelAlpha = 0.62f + 0.38f * proximity
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
                            tint = lerp(InkSecondary.copy(alpha = 0.55f), TimeGold, proximity),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = label,
                        style = TimartType.caption,
                        color = lerp(InkSecondary, InkPrimary, proximity),
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

/** 横屏侧栏宽度 */
private val SIDE_RAIL_WIDTH = 80.dp

/**
 * 横屏侧栏：竖向四页签，视觉与底栏同规格（SurfaceRaise 底 + TrackHairline 分隔线 + 金色光晕胶囊）。
 * 背景延伸到系统条之下，内容经 systemBarsPadding 内缩（横屏状态栏在顶、导航条在侧，各自兜住）。
 */
@Composable
private fun TimartSideRail(pagePosition: Float, onSelect: (Int) -> Unit) {
    val L = LocalStrings.current
    val labels = listOf(
        L.tabTimeTrack,
        L.tabStarLibrary,
        L.tabDustRecords,
        L.tabSettings,
    )
    val icons = listOf(TabIcons.timeTrack, TabIcons.starLibrary, TabIcons.dustRecords, TabIcons.settings)
    Row(modifier = Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(SIDE_RAIL_WIDTH)
                .background(SurfaceRaise)
                .systemBarsPadding(),
        ) {
            labels.forEachIndexed { index, label ->
                // 连续联动（与底栏同规格）：光晕高度/颜色按连续页位置的距离插值，翻页全程无跳档
                val proximity = (1f - (pagePosition - index).absoluteValue).coerceIn(0f, 1f)
                // 光晕胶囊竖放：宽度与底栏胶囊窄轴同值（26dp），高度滑动同规格
                val glowHeight = lerp(34.dp, 64.dp, proximity)
                val glowColor = lerp(Color.Transparent, TimeGold.copy(alpha = 0.16f), proximity)
                val labelAlpha = 0.62f + 0.38f * proximity
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable { onSelect(index) },
                ) {
                    Box(
                        modifier = Modifier
                            .height(glowHeight)
                            .width(26.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(glowColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icons[index],
                            contentDescription = label,
                            tint = lerp(InkSecondary.copy(alpha = 0.55f), TimeGold, proximity),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = TimartType.caption,
                        color = lerp(InkSecondary, InkPrimary, proximity),
                        modifier = Modifier.alpha(labelAlpha),
                    )
                }
            }
        }
        // 竖向分隔线（对应底栏顶边 hairline），随侧栏铺满全高
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(TrackHairline),
        )
    }
}
