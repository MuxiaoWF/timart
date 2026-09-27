package com.muxiao.timart.ui.settings

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.muxiao.timart.AppContainer
import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.utils.export.AlbumComposer
import com.muxiao.timart.utils.export.PosterComposer
import com.muxiao.timart.utils.format.ImageDecode
import com.muxiao.timart.utils.format.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/**
 * 纪念册按年合订导出弹窗：选年份 → 逐颗解密渲染海报页（需口令会话已解锁，fail-closed）→
 * 封面 + 页纵向拼接分卷落盘 → 分享。纯本地（解密/渲染/落盘），零网络。
 */
@Composable
fun AlbumExportDialog(
    container: AppContainer,
    onDismiss: () -> Unit,
) {
    val L = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    /** 按年分组的可导出胶囊（year → 该年开启的胶囊，按开启时刻升序）；null = 装载中 */
    var years by remember { mutableStateOf<Map<Int, List<Capsule>>?>(null) }
    var selectedYear by remember { mutableIntStateOf(0) }
    /** null = 空闲；非 null = 正在合成（值 = 已完成页数） */
    var progress by remember { mutableStateOf<Int?>(null) }
    var totalPages by remember { mutableIntStateOf(0) }
    var resultFiles by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }

    // 解密需要口令会话：未解锁时 fail-closed（引导先去详情页解封一次以建立会话）
    val sessionUnlocked = remember { container.contentCryptoManager.isUnlocked }

    LaunchedEffect(Unit) {
        val grouped = withContext(Dispatchers.IO) {
            runCatching {
                container.capsuleRepository.allSync()
                    .filter { it.state == CapsuleState.UNLOCKED && it.unlockTimestamp != null }
                    .groupBy {
                        Instant.ofEpochMilli(it.unlockTimestamp!!).atZone(ZoneId.systemDefault()).year
                    }
                    .mapValues { (_, list) -> list.sortedBy { it.unlockTimestamp } }
            }.getOrDefault(emptyMap())
        }
        years = grouped
        selectedYear = grouped.keys.maxOrNull() ?: 0
    }

    fun shareAlbum(files: List<java.io.File>) {
        val uris = files.filter { it.exists() }.map {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
        }
        if (uris.isEmpty()) return
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/jpeg"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            clipData = ClipData.newRawUri("album", uris.first())
            for (uri in uris) clipData?.addItem(ClipData.Item(uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(send, L.albumShare)) }
    }

    fun startCompose() {
        val map = years ?: return
        val list = map[selectedYear].orEmpty()
        if (progress != null) return
        progress = 0
        totalPages = list.size
        resultFiles = emptyList()
        scope.launch {
            val posterComposer = PosterComposer(context)
            val albumComposer = AlbumComposer(context)
            val files = withContext(Dispatchers.IO) {
                albumComposer.compose(
                    year = selectedYear,
                    coverSubtitle = L.albumCoverSubtitle,
                    pageCount = list.size,
                    pageAt = { index ->
                        val capsule = list.getOrNull(index) ?: return@compose null
                        val content = runCatching {
                            container.readCapsuleUseCase.read(capsule)
                        }.getOrNull() ?: return@compose null
                        val images = content.images.mapNotNull { bytes ->
                            ImageDecode.decodeOriented(bytes, maxDimension = 2048)
                        }
                        val page = posterComposer.composeBitmap(
                            PosterComposer.PosterContent(
                                title = content.title,
                                paragraphs = content.paragraphs,
                                images = images,
                                weatherLine = content.snapshot?.let {
                                    "${it.cityName} · ${it.tempC.toInt()}°C"
                                },
                                createdLine = TimeFormatter.dateTime(content.createdAt),
                                unlockedLine = capsule.unlockTimestamp?.let { TimeFormatter.dateTime(it) },
                                tagsLine = content.tags.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                                note = content.note.takeIf { it.isNotBlank() },
                            ),
                        )
                        images.forEach { it.recycle() }
                        withContext(Dispatchers.Main) { progress = index + 1 }
                        page
                    },
                )
            }
            resultFiles = files
            progress = null
            if (files.isEmpty()) {
                failed = true
                Toast.makeText(context, L.albumFail, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, L.albumDone, Toast.LENGTH_SHORT).show()
                shareAlbum(files)
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (progress == null) onDismiss() },
        containerColor = SurfaceRaise,
        title = { Text(text = L.albumTitle, style = TimartType.titleSerif) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                val map = years
                when {
                    !sessionUnlocked -> Text(
                        text = L.albumNeedSession,
                        style = TimartType.body,
                        color = InkSecondary,
                    )

                    map == null -> Text(
                        text = L.inProgress,
                        style = TimartType.caption,
                        color = InkSecondary,
                    )

                    map.isEmpty() -> Text(
                        text = L.albumNoCapsule,
                        style = TimartType.body,
                        color = InkSecondary,
                    )

                    else -> {
                        Text(
                            text = L.albumPickYear,
                            style = TimartType.caption,
                            color = InkSecondary,
                        )
                        Row(modifier = Modifier.padding(top = 8.dp)) {
                            map.keys.sortedDescending().forEach { year ->
                                val selected = year == selectedYear
                                TextButton(onClick = { selectedYear = year }) {
                                    Text(
                                        text = year.toString(),
                                        style = TimartType.body,
                                        color = if (selected) TimeGold else InkSecondary,
                                    )
                                }
                            }
                        }
                        Text(
                            text = L.albumYearInfoFmt.format(
                                selectedYear,
                                map[selectedYear].orEmpty().size,
                            ),
                            style = TimartType.caption,
                            color = InkDisabled,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            text = L.albumPagingNote,
                            style = TimartType.caption,
                            color = InkDisabled,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        val current = progress
                        if (current != null) {
                            Text(
                                text = L.albumComposingFmt.format(current, totalPages),
                                style = TimartType.body,
                                color = TimeGold,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        if (resultFiles.isNotEmpty()) {
                            Text(
                                text = resultFiles.joinToString("\n") { it.name },
                                style = TimartType.caption.copy(fontFamily = FontFamily.Monospace),
                                color = InkSecondary,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        if (failed) {
                            Text(
                                text = L.albumFail,
                                style = TimartType.caption,
                                color = TimeGold,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val map = years
            when {
                progress != null -> TextButton(onClick = {}, enabled = false) {
                    Text(text = L.inProgress, color = InkDisabled)
                }

                resultFiles.isNotEmpty() -> TextButton(
                    onClick = { shareAlbum(resultFiles) },
                    colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
                ) {
                    Text(text = L.albumShare)
                }

                map.isNullOrEmpty() || !sessionUnlocked -> TextButton(onClick = onDismiss) {
                    Text(text = L.done, color = TimeGold)
                }

                else -> TextButton(
                    onClick = { startCompose() },
                    colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
                ) {
                    Text(text = L.albumCompose)
                }
            }
        },
        dismissButton = {
            if (progress == null && resultFiles.isEmpty()) {
                TextButton(onClick = onDismiss) {
                    Text(text = L.cancel, color = InkSecondary)
                }
            }
        },
    )
}
