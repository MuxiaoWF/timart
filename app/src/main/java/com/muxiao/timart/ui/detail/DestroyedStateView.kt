package com.muxiao.timart.ui.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.ui.components.visual.SectionHeader
import com.muxiao.timart.ui.cosmic.CapsuleOrbView
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.utils.format.TimeFormatter

/**
 * 状态 D：DESTROYED 尘迹态（PRD §3.4.4 状态 D / 设计稿 09）：
 * - 不保留完整尘核，仅原位置附近极淡尘迹残影；
 * - 入场：残影淡入显现（0 → 1，320ms）再缓慢静止沉降（1.0 → 0.62，900ms）——
 *   此前 t=0 直接满 alpha 弹出，与消散序列散尽后的「落尘成迹」叙事不符；
 * - 仅标题、销毁时间与一句低调纪念文案；不出现任何原始正文或图片；
 * - 「生成纪念页」导出（留存与输出：PosterComposer 尘迹变体，无正文无图片，
 *   销毁不被庆祝但可被纪念——与「回信留存于尘迹」同一叙事）。
 */
@Composable
fun DestroyedStateView(
    title: String?,
    destroyedAt: Long?,
    modifier: Modifier = Modifier,
    onPoster: (() -> Unit)? = null,
) {
    val L = LocalStrings.current
    // 「淡入显现 → 缓慢静止」
    val still = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        still.animateTo(1f, animationSpec = tween(durationMillis = 320))
        still.animateTo(0.62f, animationSpec = tween(durationMillis = 900))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // 顶部 64dp：为左上返回按钮让位
            .padding(start = 32.dp, end = 32.dp, top = 64.dp, bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SectionHeader(title = L.destroyedTitle, note = "ARCHIVED")

        Spacer(modifier = Modifier.weight(0.28f))

        // 尘迹残影（无完整球体，残影弧由 CapsuleOrbView DESTROYED 分支绘制）
        CapsuleOrbView(
            state = CapsuleState.DESTROYED,
            radius = 64.dp,
            modifier = Modifier.graphicsLayer { alpha = still.value },
        )

        Spacer(modifier = Modifier.height(26.dp))

        Text(
            text = title ?: L.untitledCapsule,
            style = TimartType.titleSerif,
            color = InkSecondary,
        )
        Text(
            text = destroyedAt?.let { TimeFormatter.dateTime(it) } ?: L.destroyedTimeUnknown,
            style = TimartType.caption,
            color = InkDisabled,
            modifier = Modifier.padding(top = 10.dp),
        )

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = L.destroyedBody,
            style = TimartType.body,
            color = InkDisabled,
        )

        // 纪念页导出入口（留存与输出；null = 不显示）
        if (onPoster != null) {
            Text(
                text = L.dustPoster,
                style = TimartType.caption,
                color = TimeGold,
                modifier = Modifier
                    .padding(top = 26.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onPoster)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Spacer(modifier = Modifier.weight(0.72f))
    }
}
