package com.muxiao.timart.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.unlock.ConditionText
import com.muxiao.timart.domain.model.unlock.UnlockRule
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.create.CreateViewModel
import com.muxiao.timart.ui.create.rules.ConditionFormSheet
import com.muxiao.timart.ui.create.rules.ConditionType
import com.muxiao.timart.ui.create.rules.ConditionTypeSheet
import com.muxiao.timart.ui.create.rules.LogicSwitch
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.ui.theme.TrackHairline
import com.muxiao.timart.utils.RuntimeSettings
import com.muxiao.timart.utils.location.GeocodeResolver

/**
 * 「后悔药」条件编辑面板（架构 §2.16 增补；隐藏入口：LOCKED 态长按尘核，每胶囊仅一次）：
 * - 可移除 / 新增解锁条件、切换 AND·OR 逻辑，确认才写库并消耗机会；
 * - 信笺内容永不改变；依赖另一颗胶囊的条件不在编辑范围（依赖关系不可改）；
 * - 至少保留一条条件（空规则 AND 合并为真，会立即解锁）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConditionEditSheet(
    capsule: Capsule,
    geocodeResolver: GeocodeResolver,
    onConfirm: (UnlockRule) -> Unit,
    onDismiss: () -> Unit,
) {
    val L = LocalStrings.current
    // 面板内暂存增删结果，确认才提交（与创建流程「表单只产出对象」同哲学）
    var conditions by remember { mutableStateOf(capsule.unlockRule.conditionList) }
    var logic by remember { mutableStateOf(capsule.unlockRule.logicType) }
    var showTypeSheet by remember { mutableStateOf(false) }
    var activeForm by remember { mutableStateOf<ConditionType?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceRaise,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(text = L.condEditTitle, style = TimartType.titleSerif, color = InkPrimary)
            Text(
                text = L.condEditDesc,
                style = TimartType.caption,
                color = InkSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )

            // 条件列表（暂存态，行尾移除）
            conditions.forEachIndexed { index, condition ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = if (index == 0) 16.dp else 0.dp)
                        .fillMaxWidth()
                        .padding(vertical = 13.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(TimeGold, androidx.compose.foundation.shape.CircleShape),
                    )
                    Text(
                        text = ConditionText.conditionSentence(condition, RuntimeSettings.resolvedLang),
                        style = TimartType.body,
                        color = InkPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    )
                    Text(
                        text = L.condEditRemove,
                        style = TimartType.caption,
                        color = InkSecondary,
                        modifier = Modifier.clickable {
                            conditions = conditions.filterIndexed { i, _ -> i != index }
                        },
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(TrackHairline),
                )
            }
            if (conditions.isEmpty()) {
                Text(
                    text = L.condEditNeedOne,
                    style = TimartType.caption,
                    color = TimeGold,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            // AND/OR 切换（两条及以上才有意义，同创建流程）
            if (conditions.size >= 2) {
                LogicSwitch(
                    logic = logic,
                    onLogicChange = { logic = it },
                    modifier = Modifier.padding(top = 18.dp),
                )
            }

            // 加条件：上限与创建流程一致；依赖类条件不在编辑范围
            if (conditions.size < CreateViewModel.CONDITION_MAX) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .fillMaxWidth()
                        .background(SurfaceRaise, RoundedCornerShape(12.dp))
                        .clickable { showTypeSheet = true }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                ) {
                    Text(text = "＋", style = TimartType.body, color = TimeGold)
                    Text(
                        text = LocalStrings.current.rulesAddMore,
                        style = TimartType.body,
                        color = InkPrimary,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }

            // 确认：写库 + 消耗机会（由 VM 完成）；空规则禁止提交
            Button(
                onClick = { onConfirm(UnlockRule(logicType = logic, conditionList = conditions)) },
                enabled = conditions.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TimeGold,
                    contentColor = DeepCharcoal,
                    disabledContainerColor = SurfaceRaise,
                    disabledContentColor = InkSecondary,
                ),
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .padding(top = 28.dp)
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(text = LocalStrings.current.confirm, style = TimartType.body)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ---- 加条件的两级面板（同创建流程；依赖类条件排除） ----
    if (showTypeSheet) {
        ConditionTypeSheet(
            onPick = { type ->
                showTypeSheet = false
                activeForm = type
            },
            onDismiss = { showTypeSheet = false },
            excluded = setOf(ConditionType.OTHER_UNLOCKED, ConditionType.OTHER_DESTROYED, ConditionType.OTHER_READ),
        )
    }
    activeForm?.let { type ->
        ConditionFormSheet(
            type = type,
            vm = null,
            snapshotCityName = capsule.weather?.cityName,
            geocodeResolver = geocodeResolver,
            onConfirm = { condition ->
                conditions = conditions + condition
                activeForm = null
            },
            onDismiss = { activeForm = null },
        )
    }
}
