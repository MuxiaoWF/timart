package com.muxiao.timart.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muxiao.timart.AppContainer
import com.muxiao.timart.domain.model.unlock.conditionKindName
import com.muxiao.timart.domain.usecase.StarLedgerStatsCalculator
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.utils.RuntimeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 星库志（N6）：全库统计常驻入口。
 * 纯本地聚合（`StarLedgerStatsCalculator` 纯逻辑），密文不参与——只读元数据与时间戳。
 */
@Composable
fun StarLedgerDialog(
    container: AppContainer,
    onDismiss: () -> Unit,
) {
    val L = LocalStrings.current
    var stats by remember { mutableStateOf<StarLedgerStatsCalculator.Stats?>(null) }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            val capsules = container.capsuleRepository.allSync()
            val destroyed = container.destroyedRepository.observeAll().first().size
            stats = StarLedgerStatsCalculator.compute(capsules, destroyed, System.currentTimeMillis())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceRaise,
        title = { Text(text = L.ledgerTitle, style = TimartType.titleSerif) },
        text = {
            val s = stats
            if (s == null) {
                Text(text = L.ledgerComputing, style = TimartType.body, color = InkSecondary)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LedgerRow(L.ledgerTotalFmt.format(s.totalSealed))
                    LedgerRow(
                        L.ledgerCurrentFmt.format(
                            s.currentlyLocked,
                            s.currentlyUnlocked,
                            s.destroyedArchives,
                        ),
                    )
                    LedgerRow(L.ledgerOpenRateFmt.format(s.openRatePercent))
                    s.longestWaitMs?.let {
                        LedgerRow(L.ledgerLongestWaitFmt.format(daysOf(it)))
                    }
                    s.avgSealToOpenMs?.let {
                        LedgerRow(L.ledgerAvgSealFmt.format(daysOf(it)))
                    }
                    if (s.kindCounts.isNotEmpty()) {
                        Text(
                            text = L.ledgerKindDist,
                            style = TimartType.caption,
                            color = InkDisabled,
                        )
                        Text(
                            text = s.kindCounts.entries
                                .sortedByDescending { it.value }
                                .joinToString(" · ") { (kind, n) ->
                                    "${conditionKindName(kind, RuntimeSettings.resolvedLang)} $n"
                                },
                            style = TimartType.body,
                            color = InkPrimary,
                        )
                    }
                    if (s.topTags.isNotEmpty()) {
                        Text(
                            text = L.ledgerTopTagsFmt.format(s.topTags.joinToString(" · ")),
                            style = TimartType.caption,
                            color = InkSecondary,
                        )
                    }
                    Text(
                        text = L.ledgerPrivacyNote,
                        style = TimartType.caption,
                        color = InkDisabled,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = L.cancel, color = TimeGold)
            }
        },
    )
}

@Composable
private fun LedgerRow(text: String) {
    Text(text = text, style = TimartType.body, color = InkPrimary, modifier = Modifier.fillMaxWidth())
}

/** 毫秒 → 天（向下取整；不足一天显示 0 天属可接受口径） */
private const val DAY_MS = 24L * 60 * 60 * 1000

private fun daysOf(ms: Long): Long = ms / DAY_MS
