package com.muxiao.timart.ui.detail

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.utils.device.NfcCardWriter

/**
 * NFC 实体锚点写卡弹层（体验储备池 §4）：把 `timart.com:link` 胶囊链接记录写入卡贴，
 * 卡贴在实物上，碰卡（NDEF 系统分发，见 MainActivity/Manifest）直达该胶囊详情。
 * reader mode 写卡流程与挑战写卡同构；失败分支显式枚举（无 NDEF / 容量不足 / IO）。
 */
@Composable
fun NfcLinkWriteDialog(
    capsuleId: String,
    onDismiss: () -> Unit,
) {
    val L = LocalStrings.current
    val context = LocalContext.current
    // 0 说明 / 1 等待贴卡 / 2 成功 / 3 失败
    var step by remember { mutableIntStateOf(0) }
    var failNote by remember { mutableStateOf("") }
    val nfcAvailable = remember(context) { NfcAdapter.getDefaultAdapter(context) != null }

    DisposableEffect(context, step == 1) {
        val activity = context as? Activity
        val adapter = NfcAdapter.getDefaultAdapter(context)
        if (step == 1 && activity != null && adapter != null) {
            val callback = NfcAdapter.ReaderCallback { tag: Tag ->
                if (step != 1) return@ReaderCallback
                when (val result = NfcCardWriter.writeCapsuleLink(tag, capsuleId)) {
                    is NfcCardWriter.Result.Success -> step = 2
                    is NfcCardWriter.Result.Failure -> {
                        failNote = when (result.reason) {
                            NfcCardWriter.FailReason.NO_NDEF -> L.nfcWriteFailNoNdef
                            NfcCardWriter.FailReason.TOO_SMALL -> L.nfcWriteFailSize
                            NfcCardWriter.FailReason.IO -> L.nfcWriteFailIo
                        }
                        step = 3
                    }
                }
            }
            adapter.enableReaderMode(
                activity,
                callback,
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
                null,
            )
        }
        onDispose {
            if (activity != null && adapter != null) {
                runCatching { adapter.disableReaderMode(activity) }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceRaise,
        title = { Text(text = L.nfcLinkTitle, style = TimartType.titleSerif) },
        text = {
            Column {
                Text(
                    text = L.nfcLinkInfo,
                    style = TimartType.caption,
                    color = InkSecondary,
                )
                when (step) {
                    1 -> Text(
                        text = L.nfcWriteWaiting,
                        style = TimartType.body,
                        color = TimeGold,
                        modifier = Modifier.padding(top = 10.dp),
                    )

                    2 -> Text(
                        text = L.nfcWriteSuccess,
                        style = TimartType.body,
                        color = TimeGold,
                        modifier = Modifier.padding(top = 10.dp),
                    )

                    3 -> Text(
                        text = failNote,
                        style = TimartType.caption,
                        color = TimeGold,
                        modifier = Modifier.padding(top = 10.dp),
                    )

                    else -> if (!nfcAvailable) {
                        Text(
                            text = L.nfcFormHint,
                            style = TimartType.caption,
                            color = TimeGold,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                0 -> TextButton(
                    onClick = { step = 1 },
                    enabled = nfcAvailable,
                    colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
                ) {
                    Text(text = L.nfcWriteStart)
                }

                1 -> TextButton(onClick = {}, enabled = false) {
                    Text(text = L.nfcWriteWaiting, color = InkSecondary)
                }

                3 -> TextButton(
                    onClick = { step = 1 },
                    colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
                ) {
                    Text(text = L.nfcWriteRewrite)
                }

                else -> TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
                ) {
                    Text(text = L.done)
                }
            }
        },
        dismissButton = {
            if (step == 0 || step == 1) {
                TextButton(onClick = onDismiss) {
                    Text(text = L.cancel, color = InkSecondary)
                }
            }
        },
    )
}
