package com.muxiao.timart.ui.components.visual

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.muxiao.timart.ui.theme.PaperCream
import com.muxiao.timart.ui.theme.PaperInk
import kotlin.random.Random

/**
 * 微暖纸面卡片（架构 §2.18）：`#F2EDE3` 底 + 轻噪点 + 不规则圆角边缘。
 * 仅解锁后内容 / 草稿阅读场景出现（PRD 视觉红线：纸色不用于锁定期）。
 * 噪点用固定种子静态绘制（无动画、无每帧成本）。
 */
@Composable
fun PaperLetterCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // 固定种子噪点坐标（remember 复用，重组零重算）
    val speckles = remember {
        val rnd = Random(20260812)
        List(160) {
            Triple(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat())
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 14.dp, bottomStart = 15.dp, bottomEnd = 19.dp),
        color = PaperCream,
        shadowElevation = 0.dp,
    ) {
        Box {
            content()
            Canvas(modifier = Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                speckles.forEach { (fx, fy, fa) ->
                    drawCircle(
                        color = PaperInk.copy(alpha = 0.05f + fa * 0.05f),
                        radius = 1.1f,
                        center = Offset(fx * w, fy * h),
                    )
                }
            }
        }
    }
}

/** 纸面墨色（供纸卡内文本取色，避免外部散落硬编码） */
val PaperInkColor: Color = PaperInk
