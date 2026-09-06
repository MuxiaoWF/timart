package com.muxiao.timart.ui.create

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.domain.model.City
import com.muxiao.timart.domain.model.WeatherSnapshot
import com.muxiao.timart.domain.model.unlock.ConditionText
import com.muxiao.timart.ui.components.visual.WeatherGlyph
import com.muxiao.timart.ui.theme.GlowGold
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TrackHairline
import com.muxiao.timart.ui.theme.TimartType
import kotlin.math.roundToInt

/**
 * 城市 · 温度 · 天气快照行（架构 §2.15，对齐设计稿 02）：
 * 未选城市 → 「选择快照城市」入口；
 * 已选 → 「城市名 · 温度 · 天气」一行展示（含纯 Canvas 天气小图形）；
 * 快照拉取失败 → 重试 / 跳过（跳过即存空快照，不阻塞封存）。
 */
@Composable
fun CityWeatherSection(
    selectedCity: City?,
    snapshot: WeatherSnapshot?,
    status: CreateViewModel.CityStatus,
    onPickCity: () -> Unit,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val L = LocalStrings.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = L.weatherCityLabel, style = TimartType.body, color = InkPrimary)
        Text(
            text = L.cwDesc,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )

        // 快照入口 / 展示卡
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .background(SurfaceRaise, RoundedCornerShape(12.dp))
                .clickable(enabled = status != CreateViewModel.CityStatus.LOADING, onClick = onPickCity)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (selectedCity == null) {
                Text(text = L.cwPickCity, style = TimartType.body, color = InkSecondary)
                Spacer(modifier = Modifier.weight(1f))
                Text(text = "›", style = TimartType.titleSerif, color = InkDisabled)
            } else {
                Text(text = selectedCity.name, style = TimartType.body, color = InkPrimary)
                Text(
                    text = when {
                        status == CreateViewModel.CityStatus.LOADING -> " · " + L.cwLoading
                        snapshot != null -> " · " + L.cwTempFmt.format(snapshot.tempC.roundToInt(), ConditionText.weatherName(snapshot.weatherType.name, com.muxiao.timart.utils.RuntimeSettings.resolvedLang) ?: L.unknown)
                        else -> " · " + L.cwNone
                    },
                    style = TimartType.body,
                    color = InkSecondary,
                    modifier = Modifier.padding(start = 4.dp),
                )
                if (snapshot != null) {
                    WeatherGlyph(
                        type = snapshot.weatherType,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(20.dp),
                        color = GlowGold,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(text = "›", style = TimartType.titleSerif, color = InkDisabled)
            }
        }

        // 失败路径：重试 / 跳过
        if (status == CreateViewModel.CityStatus.FAILED) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = L.cwFailed,
                    style = TimartType.caption,
                    color = InkSecondary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
                ) {
                    Text(text = L.retry, style = TimartType.caption)
                }
                TextButton(
                    onClick = onSkip,
                    colors = ButtonDefaults.textButtonColors(contentColor = InkSecondary),
                ) {
                    Text(text = L.skip, style = TimartType.caption)
                }
            }
        }

        // 加载中的细尘线（静态，不做长期旋转）
        if (status == CreateViewModel.CityStatus.LOADING) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                (0 until 3).forEach { i ->
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(
                                color = if (i == 0) TimeGold else TrackHairline,
                                shape = CircleShape,
                            ),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
    }
}
