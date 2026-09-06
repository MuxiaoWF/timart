package com.muxiao.timart.ui.starlibrary

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.AppContainer
import com.muxiao.timart.domain.model.DestroyRecord
import com.muxiao.timart.ui.components.particle.ParticleCanvas
import com.muxiao.timart.ui.components.particle.ParticlePreset
import com.muxiao.timart.ui.components.visual.SectionHeader
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.DustAsh
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.utils.format.TimeFormatter
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * 页面 3：尘迹档案（PRD §3.4.6）：
 * - 档案视觉：灰调浮层卡，仅标题 + 创建/归尘时间（销毁后内容已物理删除）；
 * - 文字按序 250ms 轻浮现（ASSEMBLE 语义的静态版）；
 * - 「删除记录」确认后才移除档案：DISSOLVE 粒子在该行位置散逸 + 删除元记录。
 */
@Composable
fun DustRecordsScreen(container: AppContainer) {
    val L = LocalStrings.current
    val vm: DustRecordsViewModel = viewModel { DustRecordsViewModel(container.destroyedRepository) }
    val records by vm.records.collectAsStateWithLifecycle()
    val engine = container.particleEngine
    val density = LocalDensity.current

    // 待确认删除的记录（非 null 时显示确认弹窗）
    var pendingDelete by remember { mutableStateOf<DestroyRecord?>(null) }
    // 长按进入批量删除：selection 非空即为选择态，点击在 选中/取消 间切换；确认后移除档案
    var selection by remember { mutableStateOf(setOf<String>()) }
    var confirmBatch by remember { mutableStateOf(false) }
    // 各行中心坐标（root 本地，供 DISSOLVE 粒子定位）
    val rowCenters = remember { mutableStateMapOf<String, IntOffset>() }
    var rootOrigin by remember { mutableStateOf(IntOffset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepCharcoal)
            .onGloballyPositioned { rootOrigin = it.localToRoot(Offset.Zero).let { p -> IntOffset(p.x.toInt(), p.y.toInt()) } },
    ) {
        ParticleCanvas(engine = engine, modifier = Modifier.matchParentSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            SectionHeader(title = L.tabDustRecords, note = "ARCHIVE")

            Text(
                text = L.dustNarrative,
                style = TimartType.body,
                color = InkSecondary,
                modifier = Modifier.padding(top = 14.dp),
            )

            if (records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = L.dustEmpty,
                        style = TimartType.body,
                        color = InkSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    itemsIndexed(records, key = { _, record -> record.id }) { index, record ->
                        DustRow(
                            record = record,
                            index = index,
                            selected = record.id in selection,
                            inSelection = selection.isNotEmpty(),
                            onPositioned = { center -> rowCenters[record.id] = center },
                            onToggle = {
                                selection = if (record.id in selection) {
                                    selection - record.id
                                } else {
                                    selection + record.id
                                }
                            },
                            onLongPress = { selection = selection + record.id },
                            onDeleteRequest = { pendingDelete = record },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = SurfaceRaise,
            title = { Text(text = L.dustDeleteTitle, style = TimartType.titleSerif) },
            text = {
                Text(
                    text = L.dustDeleteBodyFmt.format(record.title),
                    style = TimartType.caption,
                    color = InkSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // DISSOLVE 粒子在该行位置散逸；记录删除不等动画，直接执行（Room Flow 回流清行）
                        rowCenters[record.id]?.let { center ->
                            val local = center - rootOrigin
                            engine.fire(
                                preset = ParticlePreset.DISSOLVE,
                                anchorX = local.x.toFloat(),
                                anchorY = local.y.toFloat(),
                                anchorRadius = with(density) { 42.dp.toPx() },
                                colorArgb = DustAsh.toArgb(),
                            )
                        }
                        vm.deleteRecord(record.id)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
                ) {
                    Text(text = L.dustDeleteRecord)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(text = L.keep, color = InkSecondary)
                }
            },
        )
    }

    if (confirmBatch) {
        AlertDialog(
            onDismissRequest = { confirmBatch = false },
            containerColor = SurfaceRaise,
            title = { Text(text = L.dustBatchDeleteTitle, style = TimartType.titleSerif) },
            text = {
                Text(
                    text = L.dustBatchDeleteBodyFmt.format(selection.size),
                    style = TimartType.caption,
                    color = InkSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 选中行位置逐一 DISSOLVE 散逸；删除不等动画（Room Flow 回流清行）
                        selection.forEach { id ->
                            rowCenters[id]?.let { center ->
                                val local = center - rootOrigin
                                engine.fire(
                                    preset = ParticlePreset.DISSOLVE,
                                    anchorX = local.x.toFloat(),
                                    anchorY = local.y.toFloat(),
                                    anchorRadius = with(density) { 42.dp.toPx() },
                                    colorArgb = DustAsh.toArgb(),
                                )
                            }
                        }
                        vm.deleteRecords(selection)
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

/** 尘迹档案行：250ms 文字浮现（按序号轻 stagger）+ 灰调档案卡；长按进入批量选择 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DustRow(
    record: DestroyRecord,
    index: Int,
    selected: Boolean,
    inSelection: Boolean,
    onPositioned: (IntOffset) -> Unit,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    val L = LocalStrings.current
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // 逐条 stagger 浮现（最长只等前 8 条，避免长列表首屏过慢）
        delay((minOf(index, 8) * 60L).milliseconds)
        appeared = true
    }
    val reveal by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "dustReveal",
    )
    // 标题高亮笔触（Text Highlight）：浮现后金色底色从左到右扫过一次并保留；
    // 只盖到标题文字的实际宽度（不划到行尾空白）
    val sweep = remember { androidx.compose.animation.core.Animatable(0f) }
    val textMeasurer = rememberTextMeasurer()
    val titleStyle = TimartType.body.copy(fontSize = 15.sp)
    val titleWidthPx = remember(record.title) {
        textMeasurer.measure(
            androidx.compose.ui.text.AnnotatedString(record.title),
            titleStyle,
            maxLines = 1,
            softWrap = false,
        ).size.width.toFloat()
    }
    LaunchedEffect(appeared) {
        if (appeared) {
            delay(140.milliseconds)
            sweep.animateTo(
                1f,
                animationSpec = tween(durationMillis = 650, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = reveal
                translationY = (1f - reveal) * 8.dp.toPx()
            }
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceRaise)
            .then(
                if (selected) {
                    Modifier.border(1.2.dp, TimeGold, RoundedCornerShape(14.dp))
                } else {
                    Modifier
                },
            )
            .combinedClickable(
                onClick = { if (inSelection) onToggle() },
                onLongClick = onLongPress,
            )
            .onGloballyPositioned { coords ->
                onPositioned(
                    IntOffset(
                        coords.localToRoot(Offset.Zero).x.toInt() + coords.size.width / 2,
                        coords.localToRoot(Offset.Zero).y.toInt() + coords.size.height / 2,
                    ),
                )
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(DustAsh, CircleShape),
            )
            Text(
                text = record.title,
                style = TimartType.body.copy(fontSize = 15.sp),
                color = InkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
                    .drawBehind {
                        if (sweep.value > 0f) {
                            // 荧光笔触：宽度以标题文字实测宽为上限（更宽更实，扫过后保留）
                            val bandWidth = minOf(titleWidthPx, size.width) * sweep.value
                            drawRect(
                                color = TimeGold.copy(alpha = 0.32f),
                                topLeft = Offset(0f, size.height * 0.50f),
                                size = androidx.compose.ui.geometry.Size(bandWidth, size.height * 0.44f),
                            )
                        }
                    },
            )
            if (!inSelection) {
                Text(
                    text = L.dustDeleteRecord,
                    style = TimartType.caption,
                    color = InkDisabled,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .clickable(onClick = onDeleteRequest),
                )
            }
        }
        Text(
            text = L.dustRecordSpanFmt.format(
                TimeFormatter.date(record.createdAt), TimeFormatter.date(record.destroyedAt)),
            style = TimartType.caption,
            color = InkDisabled,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
