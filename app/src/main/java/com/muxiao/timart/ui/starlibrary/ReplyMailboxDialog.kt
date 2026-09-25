package com.muxiao.timart.ui.starlibrary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.muxiao.timart.data.local.db.CapsuleMetaKeys
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.ui.theme.TimeGold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 回信信箱：全库回信聚合视图（星库页弹层，零路由改动）。
 * 数据 = meta `capsule.reply.<id>`（回信文本，写入点 DetailViewModel.saveReply）
 * + `capsule.replyTo.<newId>`（值 = 来源 id，标记该回信已转存为新胶囊）。
 * 只读元数据与回信明文（回信本身即用户明文输入），不触碰正文密文。
 */
data class ReplyMailboxEntry(
    val capsuleId: String,
    val title: String,
    val reply: String,
    /** 非空 = 该回信已通过「一键转新胶囊」封存 */
    val convertedToCapsuleId: String?,
)

@Composable
fun ReplyMailboxDialog(
    container: AppContainer,
    onDismiss: () -> Unit,
    onOpenCapsule: (capsuleId: String) -> Unit,
) {
    val L = LocalStrings.current
    var entries by remember { mutableStateOf<List<ReplyMailboxEntry>?>(null) }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            entries = runCatching {
                val replyRows = container.database.metaDao().listLike(CapsuleMetaKeys.REPLY_KEY_PREFIX)
                val converted = container.database.metaDao()
                    .listLike(CapsuleMetaKeys.REPLY_TO_KEY_PREFIX)
                    .associate { it.value to it.key.removePrefix(CapsuleMetaKeys.REPLY_TO_KEY_PREFIX) }
                replyRows.mapNotNull { row ->
                    val id = row.key.removePrefix(CapsuleMetaKeys.REPLY_KEY_PREFIX)
                    if (id.isEmpty()) return@mapNotNull null
                    val capsule = container.capsuleRepository.byIdSync(id) ?: return@mapNotNull null
                    ReplyMailboxEntry(
                        capsuleId = id,
                        title = capsule.title,
                        reply = row.value,
                        convertedToCapsuleId = converted[id],
                    )
                }.sortedByDescending { it.capsuleId }
            }.getOrDefault(emptyList())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceRaise,
        title = { Text(text = L.mailboxTitle, style = TimartType.titleSerif) },
        text = {
            val list = entries
            when {
                list == null ->
                    Text(text = L.ledgerComputing, style = TimartType.body, color = InkSecondary)
                list.isEmpty() ->
                    Text(text = L.mailboxEmpty, style = TimartType.body, color = InkSecondary)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(list, key = { it.capsuleId }) { entry ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    onOpenCapsule(entry.capsuleId)
                                },
                        ) {
                            Text(
                                text = entry.title,
                                style = TimartType.caption,
                                color = TimeGold,
                            )
                            Text(
                                text = entry.reply,
                                style = TimartType.body,
                                color = InkPrimary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            if (entry.convertedToCapsuleId != null) {
                                Text(
                                    text = L.mailboxConverted,
                                    style = TimartType.caption,
                                    color = InkDisabled,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
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
