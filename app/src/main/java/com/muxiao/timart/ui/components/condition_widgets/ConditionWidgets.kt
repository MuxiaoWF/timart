package com.muxiao.timart.ui.components.condition_widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muxiao.timart.ui.components.particle.GlowPainter
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.ui.theme.TrackHairline

/**
 * 条件句与条件时间线组件（架构 §2.18 condition_widgets，创建页与详情页共用）。
 *
 * 状态表达红线：满足=稳定微光（金点 + 轻光晕）、未满足=低亮空位、
 * 无法满足/后台跳过=低亮并附原因；禁止绿勾红叉与徽章堆叠。
 */

/** 时间线单行视觉状态 */
enum class ConditionTone {
    /** 已满足：稳定微光 */
    SATISFIED,

    /** 未满足：低亮空位 */
    WAITING,

    /** 无法满足 / 后台跳过：低亮 + 原因 */
    BLOCKED,
}

/** 时间线行数据（创建页与详情页各自从领域模型映射） */
data class ConditionTimelineItem(
    val title: String,
    val detail: String? = null,
    val tone: ConditionTone = ConditionTone.WAITING,
)

/** 条件时间线（纵向：状态点 + 细尘连线 + 条件句 + 状态/原因小字） */
@Composable
fun ConditionTimeline(
    items: List<ConditionTimelineItem>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            ConditionTimelineRow(
                item = item,
                showConnector = index < items.lastIndex,
            )
        }
    }
}

@Composable
private fun ConditionTimelineRow(
    item: ConditionTimelineItem,
    showConnector: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // 状态点 + 连线列
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ConditionDot(tone = item.tone, dotSize = 9.dp)
            if (showConnector) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(34.dp)
                        .padding(top = 2.dp),
                ) {
                    Canvas(modifier = Modifier.size(width = 1.dp, height = 34.dp)) {
                        drawLine(
                            color = TrackHairline,
                            start = androidx.compose.ui.geometry.Offset(0.5f, 0f),
                            end = androidx.compose.ui.geometry.Offset(0.5f, size.height),
                            strokeWidth = 1f,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            ConditionSentenceText(text = item.title, tone = item.tone)
            item.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                Text(
                    text = detail,
                    style = TimartType.caption,
                    color = if (item.tone == ConditionTone.SATISFIED) InkSecondary else InkDisabled,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** 条件句文本：满足=金色主文字，其余=低亮 */
@Composable
fun ConditionSentenceText(
    text: String,
    tone: ConditionTone,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = TimartType.body.copy(fontSize = 15.sp),
        color = when (tone) {
            ConditionTone.SATISFIED -> InkPrimary
            ConditionTone.WAITING -> InkSecondary
            ConditionTone.BLOCKED -> InkDisabled
        },
        modifier = modifier,
    )
}

/** 状态点：满足=金点微光 / 等待=冷灰低亮 / 受阻=暖灰低亮 */
@Composable
private fun ConditionDot(
    tone: ConditionTone,
    dotSize: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(dotSize + 14.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = dotSize.toPx() / 2f
        drawIntoCanvas { canvas ->
            val (glowArgb, glowAlpha) = when (tone) {
                ConditionTone.SATISFIED -> 0xFFE8B44A.toInt() to 0.35f
                ConditionTone.WAITING -> 0xFF5A6B7A.toInt() to 0.15f
                ConditionTone.BLOCKED -> 0xFF7A7268.toInt() to 0.15f
            }
            GlowPainter.drawGlow(canvas.nativeCanvas, cx, cy, r * 2.6f, glowArgb, glowAlpha)
        }
        drawCircle(
            color = when (tone) {
                ConditionTone.SATISFIED -> TimeGold
                ConditionTone.WAITING -> InkDisabled
                ConditionTone.BLOCKED -> InkDisabled
            },
            radius = r,
            center = androidx.compose.ui.geometry.Offset(cx, cy),
        )
    }
}
