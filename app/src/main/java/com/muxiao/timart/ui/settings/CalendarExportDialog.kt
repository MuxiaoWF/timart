package com.muxiao.timart.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.muxiao.timart.AppContainer
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.utils.export.CalendarExporter
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ics 日历导出弹窗：把等待中胶囊的确定性时间条件（fixedTargetAt 可推算类）导出为
 * .ics 日历文件，经 SAF CreateDocument 写入用户选择的位置。零新权限、零网络；
 * 只读规则元数据（密文不参与），口令会话未解锁也可导出。
 */
@Composable
fun CalendarExportDialog(
    container: AppContainer,
    onDismiss: () -> Unit,
) {
    val L = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // null = 统计中；非 null = 可导出事件集（可能为空）
    var events by remember { mutableStateOf<List<CalendarExporter.IcsEvent>?>(null) }
    var writing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val collected = withContext(Dispatchers.IO) {
            runCatching {
                val now = container.timeProvider.nowMillis()
                val zone = runCatching {
                    ZoneId.of(container.timeProvider.zoneId())
                }.getOrDefault(ZoneId.systemDefault())
                val capsules = container.capsuleRepository.allLockedSync()
                    .filter { !container.isSeedDormant(it.id) }
                CalendarExporter.collect(capsules, now, zone) { capsule ->
                    L.icsEventSummaryFmt.format(capsule.title)
                }
            }.getOrDefault(emptyList())
        }
        events = collected
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar"),
    ) { uri ->
        val pending = events.orEmpty()
        if (uri == null || writing) return@rememberLauncherForActivityResult
        writing = true
        scope.launch {
            val text = CalendarExporter.build(
                pending,
                nowMillis = container.timeProvider.nowMillis(),
                calendarName = L.icsCalendarName,
            )
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(text.toByteArray(Charsets.UTF_8))
                        true
                    } ?: false
                }.getOrDefault(false)
            }
            writing = false
            Toast.makeText(
                context,
                if (ok) L.icsExportDone else L.icsExportFail,
                Toast.LENGTH_SHORT,
            ).show()
            if (ok) onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!writing) onDismiss() },
        containerColor = SurfaceRaise,
        title = { Text(text = L.icsDialogTitle, style = TimartType.titleSerif) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                val current = events
                when {
                    current == null -> Text(
                        text = L.inProgress,
                        style = TimartType.caption,
                        color = InkSecondary,
                    )

                    current.isEmpty() -> Text(
                        text = L.icsEmpty,
                        style = TimartType.body,
                        color = InkSecondary,
                    )

                    else -> {
                        Text(
                            text = L.icsDialogInfo.format(current.size),
                            style = TimartType.body,
                            color = InkPrimary,
                        )
                        Text(
                            text = L.icsDialogNote,
                            style = TimartType.caption,
                            color = InkDisabled,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            val ready = !events.isNullOrEmpty() && !writing
            TextButton(
                onClick = { exportLauncher.launch("timart-reminders.ics") },
                enabled = ready,
                colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
            ) {
                Text(text = if (writing) L.inProgress else L.icsExportAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !writing) {
                Text(text = L.cancel, color = InkSecondary)
            }
        },
    )
}
