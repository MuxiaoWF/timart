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
 * 信纸样式（体验储备池 §1）：纸色 × 墨色三档，封存时选定（meta `capsule.paper.<id>`，
 * 键契约见 `CapsuleMetaKeys.paper`），解封信笺按此呈现；纯呈现层，缺省回落原纸。
 * 新增档位 = 追加枚举 + 三语名称词条（paperName*）。
 */
enum class PaperStyle(val paper: Color, val ink: Color) {
    /** 原纸：暖米纸 + 深墨（默认） */
    PLAIN(PaperCream, PaperInk),

    /** 月白：冷调浅青纸 + 青墨 */
    MIST(Color(0xFFE7ECEA), Color(0xFF2E3538)),

    /** 暮棕：暖褐纸 + 深褐墨 */
    EMBER(Color(0xFFE9DAC4), Color(0xFF43362A));

    companion object {
        /** 序号反查（越界/解析失败回落原纸，fail-closed） */
        fun of(index: Int): PaperStyle = entries.getOrElse(index) { PLAIN }
    }
}

/**
 * 微暖纸面卡片（架构 §2.18）：纸底 + 轻噪点 + 不规则圆角边缘。
 * 仅解锁后内容 / 草稿阅读场景出现（PRD 视觉红线：纸色不用于锁定期）。
 * 噪点用固定种子静态绘制（无动画、无每帧成本）；[style] 决定纸色与墨色（信纸样式）。
 */
@Composable
fun PaperLetterCard(
    modifier: Modifier = Modifier,
    style: PaperStyle = PaperStyle.PLAIN,
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
        color = style.paper,
        shadowElevation = 0.dp,
    ) {
        Box {
            content()
            Canvas(modifier = Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                speckles.forEach { (fx, fy, fa) ->
                    drawCircle(
                        color = style.ink.copy(alpha = 0.05f + fa * 0.05f),
                        radius = 1.1f,
                        center = Offset(fx * w, fy * h),
                    )
                }
            }
        }
    }
}
