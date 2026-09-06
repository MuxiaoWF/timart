package com.muxiao.timart.ui.create.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muxiao.timart.domain.model.unlock.ConditionText
import com.muxiao.timart.domain.model.unlock.LogicType
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.utils.RuntimeSettings

/**
 * 已添加条件卡列表（架构 §2.15）：每张卡显示 ConditionText 条件句，
 * 卡与卡之间按 AND/OR 显示连接词（"并且"/"或者"），右上角可移除。
 */
@Composable
fun ConditionCardList(
    conditions: List<UnlockCondition>,
    logic: LogicType,
    onRemove: (UnlockCondition) -> Unit,
    modifier: Modifier = Modifier,
) {
    val L = LocalStrings.current
    Column(modifier = modifier.fillMaxWidth()) {
        conditions.forEachIndexed { index, condition ->
            if (index > 0) {
                Text(
                    text = if (logic == LogicType.AND) L.andWord else L.orWord,
                    style = TimartType.caption,
                    color = InkSecondary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            ConditionCard(
                index = index,
                condition = condition,
                onRemove = { onRemove(condition) },
            )
        }
    }
}

/** 单张条件卡：序号尘核 + 条件句 + 移除 */
@Composable
private fun ConditionCard(
    index: Int,
    condition: UnlockCondition,
    onRemove: () -> Unit,
) {
    val L = LocalStrings.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceRaise, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        // 序号尘核（金色小圆点 + 编号）
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(TimeGold.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${index + 1}",
                style = TimartType.caption,
                color = TimeGold,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = ConditionText.conditionSentence(condition, RuntimeSettings.resolvedLang),
                style = TimartType.body.copy(fontSize = 15.sp),
                color = InkPrimary,
            )
        }
        // 移除（细 "×"，非红叉，保持主题 Token 色）
        Text(
            text = L.removeCond,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier
                .clickable(onClick = onRemove)
                .padding(6.dp),
        )
    }
}

/** 条件区标题行（RulesStep 顶部用） */
@Composable
fun ConditionSectionTitle(
    conditionsCount: Int,
    modifier: Modifier = Modifier,
) {
    val L = LocalStrings.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(text = L.realConditionLabel, style = TimartType.body, color = InkPrimary)
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "$conditionsCount / 10",
            style = TimartType.caption,
            color = if (conditionsCount > 0) TimeGold else InkSecondary,
        )
    }
}
