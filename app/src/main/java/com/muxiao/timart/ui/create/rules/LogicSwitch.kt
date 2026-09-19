package com.muxiao.timart.ui.create.rules

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.domain.model.unlock.LogicType
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartMotion
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.ui.create.rules.condition.IntSlider

/**
 * AND / OR / AT_LEAST 逻辑切换（架构 §2.15）：切换时 240ms 缩放淡入重排（统一走 TimartMotion，禁 spring）。
 * AND = 全部满足；OR = 任一满足；AT_LEAST = N 条满足 M 条即可（备用钥匙语义）。
 * 布局 = 左「标签 + 说明」列，右药丸组（同设置页开关行模式）：
 * 药丸占固有宽度且文字锁单行，窄屏不再被压缩换行错位。
 * AT_LEAST 选中时在下方出现 M 选择滑条（1..N，N = 当前条件数）。
 */
@Composable
fun LogicSwitch(
    logic: LogicType,
    conditionCount: Int,
    threshold: Int,
    onLogicChange: (LogicType) -> Unit,
    onThresholdChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val L = LocalStrings.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = L.logicLabel, style = TimartType.body, color = InkPrimary)
                Text(
                    text = when (logic) {
                        LogicType.AND -> L.logicAndDesc
                        LogicType.OR -> L.logicOrDesc
                        LogicType.AT_LEAST -> L.logicAtLeastDesc
                    },
                    style = TimartType.caption,
                    color = InkSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            AnimatedContent(
                targetState = logic,
                transitionSpec = {
                    // 240ms 与全局内容级转场同档（原 250ms 离群值，B4 收敛）
                    (scaleIn(initialScale = 0.92f, animationSpec = tween(TimartMotion.CONTENT_MILLIS)) +
                        fadeIn(tween(TimartMotion.CONTENT_MILLIS)))
                        .togetherWith(fadeOut(tween(TimartMotion.CONTENT_MILLIS)))
                },
                label = "LogicSwitchReorder",
            ) { current ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LogicPill(
                        label = L.logicAndPill,
                        selected = current == LogicType.AND,
                        onClick = { onLogicChange(LogicType.AND) },
                    )
                    LogicPill(
                        label = L.logicOrPill,
                        selected = current == LogicType.OR,
                        onClick = { onLogicChange(LogicType.OR) },
                    )
                    LogicPill(
                        label = L.logicAtLeastPill,
                        selected = current == LogicType.AT_LEAST,
                        onClick = { onLogicChange(LogicType.AT_LEAST) },
                    )
                }
            }
        }

        // M 选择器：仅 AT_LEAST 且条件数 ≥ 2 时出现（1 条时任选即 AND，面板本身也只在 ≥2 条时展示）
        if (logic == LogicType.AT_LEAST && conditionCount >= 2) {
            Text(
                text = L.thresholdFmt.format(threshold.coerceIn(1, conditionCount), conditionCount),
                style = TimartType.body.copy(color = TimeGold),
                modifier = Modifier.padding(top = 14.dp),
            )
            IntSlider(
                value = threshold.coerceIn(1, conditionCount),
                range = 1f..conditionCount.toFloat(),
                steps = (conditionCount - 2).coerceAtLeast(0),
            ) { onThresholdChange(it) }
        }
    }
}

/** 单个逻辑药丸：选中金色实底，未选中浮层灰底；文字永远单行 */
@Composable
private fun LogicPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .background(
                color = if (selected) TimeGold else SurfaceRaise,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = TimartType.caption,
            color = if (selected) DeepCharcoal else InkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Visible,
        )
    }
}
