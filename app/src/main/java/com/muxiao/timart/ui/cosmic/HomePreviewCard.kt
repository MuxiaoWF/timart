package com.muxiao.timart.ui.cosmic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartType

/**
 * 底部「最近的一颗」预览卡（架构 §2.14，对齐设计稿 01）：
 * 小注 + 标题 + 条件句；右侧进度 x/y 与「查看」入口。无最近胶囊时不渲染。
 */
@Composable
fun HomePreviewCard(
    preview: HomeViewModel.LatestPreview,
    onView: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val L = LocalStrings.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onView),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceRaise,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = L.previewLatest,
                    style = TimartType.caption,
                    color = InkSecondary,
                )
                Text(
                    text = preview.title,
                    style = TimartType.titleSerif,
                    color = InkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                preview.sentence?.let { sentence ->
                    Text(
                        text = sentence,
                        style = TimartType.caption,
                        color = InkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = when {
                        preview.unlocked -> L.previewReady
                        preview.satisfied != null && preview.total != null ->
                            L.previewProgressFmt.format(preview.satisfied, preview.total)
                        else -> L.previewWaiting
                    },
                    style = TimartType.caption,
                    color = TimeGold,
                )
                Text(
                    text = L.previewView,
                    style = TimartType.body,
                    color = InkPrimary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/**
 * 「＋ 新建」聚合尘核入口：与预览卡等高的描边幽灵卡（透明底 + 金描边），
 * ＋ 与文字纵向居中，和预览卡构成一组上下沿对齐的底部条。
 */
@Composable
fun HomeCreateEntry(
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val L = LocalStrings.current
    Surface(
        modifier = modifier.clickable(onClick = onCreate),
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, TimeGold.copy(alpha = 0.55f)),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
        ) {
            // B5：全角字符「＋」改为代码内置矢量（TabIcons.plus）——
            // 几何与线宽锁死，不再随字体/语言（英文词条）漂移基线与字重
            androidx.compose.material3.Icon(
                imageVector = com.muxiao.timart.ui.navigation.TabIcons.plus,
                contentDescription = null,
                tint = TimeGold,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = L.createNew,
                style = TimartType.caption.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                color = TimeGold,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

