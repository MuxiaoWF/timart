package com.muxiao.timart.ui.starlibrary

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muxiao.timart.AppContainer
import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.components.visual.SectionHeader
import com.muxiao.timart.ui.cosmic.CapsuleOrbView
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.utils.format.TimeFormatter

/**
 * 页面 4：已解锁星库（PRD §3.4.5 / 设计稿 10）：
 * - 不复制完整首页，仅保留已解锁内容的简化时间空间；
 * - 暖金 BREATHE 球体，动画频率低于首页（仅首屏第一颗呼吸，避免长时间浏览疲劳）；
 * - 默认按最近解锁排序 + 标签筛选；
 * - 点击直接进详情（450ms 简化重读过渡在详情页呈现）。
 */
@Composable
fun StarLibraryScreen(
    container: AppContainer,
    onOpenDetail: (capsuleId: String) -> Unit,
) {
    val L = LocalStrings.current
    val vm: StarLibraryViewModel = viewModel { StarLibraryViewModel(container) }
    val filtered by vm.filtered.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val selectedTag by vm.selectedTag.collectAsStateWithLifecycle()

    // 长按进入批量删除：selection 非空即为选择态，点击在 选中/取消 间切换；确认后物理删除
    var selection by remember { mutableStateOf(setOf<String>()) }
    var confirmBatch by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepCharcoal)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        SectionHeader(title = L.tabStarLibrary, note = "STARS")

        Text(
            text = L.starNarrative,
            style = TimartType.body,
            color = InkSecondary,
            modifier = Modifier.padding(top = 14.dp),
        )

        // 标签筛选（有标签才显示）
        if (tags.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 14.dp),
            ) {
                items(tags) { tag ->
                    val selected = tag == selectedTag
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) TimeGold else SurfaceRaise)
                            .clickable { vm.selectTag(tag) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = tag,
                            style = TimartType.caption,
                            color = if (selected) DeepCharcoal else InkSecondary,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 批量选择操作栏（长按任一卡片后出现）
        if (selection.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            ) {
                Text(
                    text = L.batchSelectedFmt.format(selection.size),
                    style = TimartType.caption,
                    color = TimeGold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = L.batchSelectAll,
                    style = TimartType.caption,
                    color = InkSecondary,
                    modifier = Modifier
                        .clickable {
                            selection = if (selection.size == filtered.size) {
                                emptySet()
                            } else {
                                filtered.map { it.id }.toSet()
                            }
                        }
                        .padding(start = 16.dp),
                )
                Text(
                    text = L.batchDelete,
                    style = TimartType.caption,
                    color = TimeGold,
                    modifier = Modifier
                        .clickable { confirmBatch = true }
                        .padding(start = 16.dp),
                )
                Text(
                    text = L.batchCancel,
                    style = TimartType.caption,
                    color = InkSecondary,
                    modifier = Modifier
                        .clickable { selection = emptySet() }
                        .padding(start = 16.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (selectedTag == null) {
                        L.starEmpty
                    } else {
                        L.starEmptyTag
                    },
                    style = TimartType.body,
                    color = InkSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            // 简化时间空间：两列球体卡（动画频率低于首页：仅第一颗呼吸）
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                items(filtered, key = { it.id }) { capsule ->
                    StarCard(
                        capsule = capsule,
                        breathing = capsule.id == filtered.first().id,
                        selected = capsule.id in selection,
                        onClick = {
                            if (selection.isNotEmpty()) {
                                // 选择态：点击切换勾选
                                selection = if (capsule.id in selection) {
                                    selection - capsule.id
                                } else {
                                    selection + capsule.id
                                }
                            } else {
                                onOpenDetail(capsule.id)
                            }
                        },
                        onLongClick = { selection = setOf(capsule.id) },
                    )
                }
            }
        }
    }

    if (confirmBatch) {
        AlertDialog(
            onDismissRequest = { confirmBatch = false },
            containerColor = SurfaceRaise,
            title = { Text(text = L.starBatchDeleteTitle, style = TimartType.titleSerif) },
            text = {
                Text(
                    text = L.starBatchDeleteBodyFmt.format(selection.size),
                    style = TimartType.caption,
                    color = InkSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteSelected(selection)
                        selection = emptySet()
                        confirmBatch = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
                ) {
                    Text(text = L.batchDelete)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBatch = false }) {
                    Text(text = L.batchCancel, color = InkSecondary)
                }
            },
        )
    }
}

/** 星库卡片：星球从卡顶地平线升起（约 2/3 露出，光晕下渗与文字自然重叠）+ 标题 + 解锁时间 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StarCard(
    capsule: Capsule,
    breathing: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val orbRadius = 34.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceRaise)
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, TimeGold, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                },
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        // 星球视窗：requiredSize 锁定球体完整尺寸（窗口高度小于球径也不被压缩）；
        // 球心落在卡缘下方 10dp，卡外上段由卡片圆角裁剪兜底——不做视窗硬裁剪，
        // 外圈光晕得以向下渗入文字区（文字叠在光晕上，无裁剪接缝）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(orbRadius + 10.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            CapsuleOrbView(
                state = com.muxiao.timart.domain.model.CapsuleState.UNLOCKED,
                radius = orbRadius,
                breathing = breathing,
                modifier = Modifier
                    .requiredSize(orbRadius * 2)
                    .offset(y = -(orbRadius - 10.dp)),
            )
        }
        Text(
            text = capsule.title,
            style = TimartType.body.copy(fontSize = 15.sp),
            color = InkPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp, start = 12.dp, end = 12.dp),
        )
        Text(
            text = capsule.unlockTimestamp?.let { TimeFormatter.date(it) } ?: "",
            style = TimartType.caption,
            color = InkDisabled,
            modifier = Modifier.padding(top = 3.dp, start = 12.dp, end = 12.dp),
        )
        Spacer(modifier = Modifier.height(18.dp))
    }
}
