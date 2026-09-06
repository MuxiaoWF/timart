package com.muxiao.timart.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.TimeGold

/**
 * 详情页左上返回按钮（锁定 / 不存在 / 尘迹 / 阅读态共用）：
 * 40dp 圆形深底 + 金描边 + 笔绘箭头（无图标库）。父级内容区已含状态栏 inset，
 * 定位 (16dp, 10dp)；使用方需让内容顶部让位至 64dp，避免与卡片/标题重叠。
 */
@Composable
fun DetailBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(start = 16.dp, top = 10.dp)
            .size(40.dp)
            .background(DeepCharcoal.copy(alpha = 0.72f), CircleShape)
            .border(1.dp, TimeGold.copy(alpha = 0.35f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BackArrowGlyph()
    }
}

/** 返回箭头（Canvas 笔画） */
@Composable
private fun BackArrowGlyph() {
    Canvas(modifier = Modifier.size(width = 18.dp, height = 14.dp)) {
        val y = size.height / 2f
        val stroke = 1.8.dp.toPx()
        drawLine(InkPrimary, Offset(size.width, y), Offset(2f, y), strokeWidth = stroke)
        drawLine(
            InkPrimary,
            Offset(2f, y),
            Offset(size.width * 0.42f, y - size.height * 0.34f),
            strokeWidth = stroke,
        )
        drawLine(
            InkPrimary,
            Offset(2f, y),
            Offset(size.width * 0.42f, y + size.height * 0.34f),
            strokeWidth = stroke,
        )
    }
}
