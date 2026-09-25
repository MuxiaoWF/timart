package com.muxiao.timart.ui.create.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.muxiao.timart.domain.model.unlock.ConditionText
import com.muxiao.timart.domain.model.unlock.LogicType
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.create.CreateViewModel
import com.muxiao.timart.ui.create.rules.condition.IntSlider
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.utils.RuntimeSettings

/**
 * 子群组编辑器（储备池暂缓项落地）：
 * - 已有组：改组名（可空 = 默认名）/ 切组内 AND-OR-任选M（含 M 滑条）/ 解散；
 * - 未分组条件：点选 ≥2 条 →「组成分组」（默认 OR 语义，可在组卡上再调）。
 * 组划分保存进 VM 草稿，判定按「组 + 未分组单例」单元合并（domain `units()` 同源语义）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupEditorSheet(
    vm: CreateViewModel,
    onDismiss: () -> Unit,
) {
    val L = LocalStrings.current
    var selected by remember { mutableStateOf(setOf<Int>()) }

    val groupedIndexes = vm.groups.flatMap { it.indexes }.toSet()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceRaise,
        // 与条件选择面板同口径：跳过半展开，避免占满屏瞬间的嵌套滚动交接卡顿
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(text = L.groupEditorTitle, style = TimartType.titleSerif, color = InkPrimary)
            Text(
                text = L.groupEditorDesc,
                style = TimartType.caption,
                color = InkSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )

            // ---- 已有组 ----
            vm.groups.forEachIndexed { groupIndex, _ ->
                GroupEditorCard(
                    groupIndex = groupIndex,
                    vm = vm,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            // ---- 未分组条件：点选组成分组 ----
            val ungrouped = vm.conditions.indices.filter { it !in groupedIndexes }
            if (ungrouped.size >= 2) {
                Text(
                    text = L.groupPickHint,
                    style = TimartType.caption,
                    color = InkSecondary,
                    modifier = Modifier.padding(top = 20.dp),
                )
                ungrouped.chunked(2).forEach { rowIndexes ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth(),
                    ) {
                        rowIndexes.forEach { index ->
                            val isSelected = index in selected
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isSelected) TimeGold.copy(alpha = 0.12f) else DeepCharcoal,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) TimeGold else DeepCharcoal,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .clickable {
                                        selected = if (isSelected) selected - index else selected + index
                                    }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    text = "${index + 1}. " + ConditionText.conditionSentence(
                                        vm.conditions[index],
                                        RuntimeSettings.resolvedLang,
                                    ),
                                    style = TimartType.caption,
                                    color = if (isSelected) TimeGold else InkPrimary,
                                    maxLines = 2,
                                )
                            }
                        }
                        repeat(2 - rowIndexes.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                TextButton(
                    onClick = {
                        vm.createGroup(selected.toList())
                        selected = emptySet()
                    },
                    enabled = selected.size >= 2,
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = TimeGold),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(text = L.groupCreateFmt.format(selected.size))
                }
            }

            TextButton(
                onClick = onDismiss,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = InkSecondary),
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            ) {
                Text(text = L.done)
            }
        }
    }
}

/** 单个组的编辑卡：组名输入 + 组内逻辑药丸 + M 滑条 + 组员预览 + 解散 */
@Composable
private fun GroupEditorCard(
    groupIndex: Int,
    vm: CreateViewModel,
    modifier: Modifier = Modifier,
) {
    val L = LocalStrings.current
    val group = vm.groups.getOrNull(groupIndex) ?: return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, TimeGold.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = group.name ?: "",
                onValueChange = { vm.updateGroup(groupIndex, it, group.logicType, group.threshold ?: (group.indexes.size / 2)) },
                singleLine = true,
                textStyle = TimartType.body.copy(color = TimeGold),
                cursorBrush = SolidColor(TimeGold),
                decorationBox = { inner ->
                    if (group.name == null) {
                        Text(
                            text = L.groupNameHintFmt.format(groupIndex + 1),
                            style = TimartType.body,
                            color = InkDisabled,
                        )
                    } else {
                        inner()
                    }
                },
                modifier = Modifier.weight(1f),
            )
            Text(
                text = L.groupDissolve,
                style = TimartType.caption,
                color = InkSecondary,
                modifier = Modifier
                    .clickable { vm.dissolveGroup(groupIndex) }
                    .padding(start = 12.dp),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            GroupLogicPill(
                label = L.logicAndPill,
                selected = group.logicType == LogicType.AND,
            ) { vm.updateGroup(groupIndex, group.name, LogicType.AND, group.threshold ?: 1) }
            GroupLogicPill(
                label = L.logicOrPill,
                selected = group.logicType == LogicType.OR,
            ) { vm.updateGroup(groupIndex, group.name, LogicType.OR, group.threshold ?: 1) }
            GroupLogicPill(
                label = L.logicAtLeastPill,
                selected = group.logicType == LogicType.AT_LEAST,
            ) {
                vm.updateGroup(
                    groupIndex,
                    group.name,
                    LogicType.AT_LEAST,
                    group.threshold ?: (group.indexes.size / 2).coerceAtLeast(1),
                )
            }
        }
        if (group.logicType == LogicType.AT_LEAST) {
            val memberCount = group.indexes.size
            val current = (group.threshold ?: (memberCount / 2).coerceAtLeast(1)).coerceIn(1, memberCount)
            Text(
                text = L.thresholdFmt.format(current, memberCount),
                style = TimartType.caption.copy(color = TimeGold),
                modifier = Modifier.padding(top = 8.dp),
            )
            IntSlider(
                value = current,
                range = 1f..memberCount.toFloat(),
                steps = (memberCount - 2).coerceAtLeast(0),
            ) { vm.updateGroup(groupIndex, group.name, LogicType.AT_LEAST, it) }
        }
        Text(
            text = group.indexes.mapNotNull { index ->
                vm.conditions.getOrNull(index)?.let {
                    ConditionText.conditionSentence(it, RuntimeSettings.resolvedLang)
                }
            }.joinToString(if (group.logicType == LogicType.AND) " / " else " · "),
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** 组内逻辑药丸（比 LogicSwitch 轻量：无说明文字，仅供组卡内切换） */
@Composable
private fun GroupLogicPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .background(
                color = if (selected) TimeGold else DeepCharcoal,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = TimartType.caption,
            color = if (selected) DeepCharcoal else InkSecondary,
        )
    }
}
