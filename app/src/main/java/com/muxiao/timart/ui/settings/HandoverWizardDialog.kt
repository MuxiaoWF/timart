package com.muxiao.timart.ui.settings

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.muxiao.timart.AppContainer
import com.muxiao.timart.data.local.crypto.ContentCryptoManager
import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.domain.model.unlock.ConditionText
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.utils.RuntimeSettings
import com.muxiao.timart.utils.export.BackupManager
import com.muxiao.timart.utils.export.HandoverGuideComposer
import com.muxiao.timart.utils.format.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * 遗产移交向导（移交包 = 加密赠予 zip + 一页本地渲染的开启指引图）：
 * 选胶囊 → （会话锁定时输口令）→ 打包 + 指引图 → 展示两份产物并可一次分享。
 * 纯本地编排既有赠予导出链路（[BackupManager.exportGift]），零网络；
 * 接收方凭「安装时粒 + 移交包文件 + 封存者告知的口令」即可开启。
 */
@Composable
fun HandoverWizardDialog(
    container: AppContainer,
    manager: BackupManager,
    onDismiss: () -> Unit,
) {
    val L = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 0 选胶囊 / 1 口令或确认 / 2 进行中 / 3 完成 / 4 失败
    var step by remember { mutableIntStateOf(0) }
    var candidates by remember { mutableStateOf<List<Capsule>?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var failedAttempts by remember { mutableIntStateOf(0) }
    var lockUntil by remember { mutableLongStateOf(0L) }
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val locked = nowTick < lockUntil
    LaunchedEffect(locked) {
        while (locked) {
            delay(500.milliseconds)
            nowTick = System.currentTimeMillis()
        }
    }
    var giftPath by remember { mutableStateOf("") }
    var guidePath by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val list = withContext(Dispatchers.IO) {
            runCatching {
                container.capsuleRepository.allSync().filter {
                    it.state != CapsuleState.DESTROYED && it.contentCipher != null
                }
            }.getOrDefault(emptyList())
        }
        candidates = list
    }

    fun sharePackage(files: List<java.io.File>) {
        val uris = files.filter { it.exists() }.map {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
        }
        if (uris.isEmpty()) return
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            // 授权随 Chooser 传递（与海报分享同因：部分 ROM 仅 EXTRA_STREAM 拿不到授权）
            clipData = ClipData.newRawUri("handover", uris.first())
            for (uri in uris) clipData?.addItem(ClipData.Item(uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(send, L.handoverShareTitle)) }
    }

    AlertDialog(
        onDismissRequest = { if (step != 2) onDismiss() },
        containerColor = SurfaceRaise,
        title = { Text(text = L.handoverTitle, style = TimartType.titleSerif) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                when (step) {
                    0 -> {
                        Text(
                            text = L.handoverPickInfo,
                            style = TimartType.caption,
                            color = InkSecondary,
                        )
                        val list = candidates
                        when {
                            list == null -> Text(
                                text = L.inProgress,
                                style = TimartType.caption,
                                color = InkDisabled,
                                modifier = Modifier.padding(top = 10.dp),
                            )

                            list.isEmpty() -> Text(
                                text = L.handoverPickEmpty,
                                style = TimartType.body,
                                color = InkSecondary,
                                modifier = Modifier.padding(top = 10.dp),
                            )

                            else -> Column(modifier = Modifier.padding(top = 10.dp)) {
                                list.forEach { capsule ->
                                    val selected = capsule.id == selectedId
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (selected) {
                                                    DeepCharcoal.copy(alpha = 0.5f)
                                                } else {
                                                    androidx.compose.ui.graphics.Color.Transparent
                                                },
                                            )
                                            .clickable { selectedId = capsule.id }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                    ) {
                                        Text(
                                            text = if (selected) "●" else "○",
                                            style = TimartType.caption,
                                            color = TimeGold,
                                        )
                                        Column(modifier = Modifier.padding(start = 10.dp)) {
                                            Text(
                                                text = capsule.title.ifBlank { L.untitled },
                                                style = TimartType.body,
                                                color = InkPrimary,
                                            )
                                            Text(
                                                text = TimeFormatter.dateTime(capsule.createTimestamp),
                                                style = TimartType.caption,
                                                color = InkDisabled,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        Text(
                            text = L.handoverConfirmInfo,
                            style = TimartType.caption,
                            color = InkSecondary,
                        )
                        if (manager.isSessionUnlocked()) {
                            Text(
                                text = L.bkExportSessionInfo,
                                style = TimartType.caption,
                                color = InkSecondary,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        } else {
                            Text(
                                text = L.bkExportAskPw,
                                style = TimartType.caption,
                                color = InkSecondary,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                            BasicTextField(
                                value = password,
                                onValueChange = { password = it },
                                singleLine = true,
                                enabled = !locked,
                                textStyle = TimartType.body.copy(color = InkPrimary),
                                cursorBrush = SolidColor(TimeGold),
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier
                                    .padding(top = 10.dp)
                                    .fillMaxWidth()
                                    .background(DeepCharcoal.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 14.dp, vertical = 13.dp),
                            )
                        }
                        errorText?.let {
                            Text(
                                text = it,
                                style = TimartType.caption,
                                color = TimeGold,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        if (locked) {
                            Text(
                                text = L.bkCooldownFmt.format((lockUntil - nowTick) / 1000 + 1),
                                style = TimartType.caption,
                                color = TimeGold,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }

                    2 -> Text(
                        text = L.bkPacking,
                        style = TimartType.caption,
                        color = InkSecondary,
                    )

                    3 -> {
                        Text(
                            text = L.handoverDoneInfo,
                            style = TimartType.body,
                            color = InkPrimary,
                        )
                        Text(
                            text = giftPath,
                            style = TimartType.caption.copy(fontFamily = FontFamily.Monospace),
                            color = InkSecondary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            text = guidePath,
                            style = TimartType.caption.copy(fontFamily = FontFamily.Monospace),
                            color = InkSecondary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            text = L.handoverDoneNote,
                            style = TimartType.caption,
                            color = InkDisabled,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    else -> Text(
                        text = errorText ?: L.bkExportFailed,
                        style = TimartType.caption,
                        color = InkSecondary,
                    )
                }
            }
        },
        confirmButton = {
            when (step) {
                0 -> TextButton(
                    onClick = { if (selectedId != null) step = 1 },
                    enabled = selectedId != null,
                    colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
                ) {
                    Text(text = L.continueWord)
                }

                1 -> TextButton(
                    onClick = {
                        val capsuleId = selectedId ?: return@TextButton
                        val usePassword = !manager.isSessionUnlocked()
                        if (usePassword && password.length < ContentCryptoManager.MIN_PASSWORD_LENGTH) {
                            errorText = L.bkWrongPw
                            return@TextButton
                        }
                        errorText = null
                        step = 2
                        scope.launch {
                            val result = runCatching {
                                val export = withContext(Dispatchers.IO) {
                                    manager.exportGift(
                                        capsuleId,
                                        if (usePassword) password.toCharArray() else null,
                                    )
                                }
                                val guide = withContext(Dispatchers.IO) {
                                    val capsule = container.capsuleRepository.byIdSync(capsuleId)
                                    val content = capsule?.let { c ->
                                        HandoverGuideComposer.GuideContent(
                                            title = c.title.ifBlank { L.untitled },
                                            createdLine = TimeFormatter.dateTime(c.createTimestamp),
                                            steps = listOf(
                                                L.handoverStep1,
                                                L.handoverStep2,
                                                L.handoverStep3,
                                                L.handoverStep4,
                                            ),
                                            unlockSummary = c.unlockRule.conditionList.map {
                                                ConditionText.conditionSentence(it, RuntimeSettings.resolvedLang)
                                            },
                                            noteLine = L.handoverNoteLine,
                                        )
                                    }
                                    content?.let { HandoverGuideComposer(context).compose(it) }
                                }
                                export.file to guide
                            }
                            val pair = result.getOrNull()
                            val gift = pair?.first
                            val guide = pair?.second
                            if (gift != null) {
                                giftPath = gift.absolutePath
                                guidePath = guide?.absolutePath ?: ""
                                step = 3
                            } else {
                                val failure = result.exceptionOrNull()
                                errorText = failure?.message ?: L.bkExportFailed
                                if (failure is com.muxiao.timart.utils.export.BackupException && failure.wrongPassword) {
                                    failedAttempts++
                                    if (failedAttempts >= 5) {
                                        lockUntil = System.currentTimeMillis() + COOLDOWN_MILLIS
                                        failedAttempts = 0
                                        password = ""
                                    }
                                    step = 1
                                } else {
                                    step = 4
                                }
                            }
                        }
                    },
                    enabled = (manager.isSessionUnlocked() || password.isNotBlank()) && !locked,
                    colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
                ) {
                    Text(text = L.handoverSealAction)
                }

                2 -> TextButton(onClick = {}, enabled = false) {
                    Text(text = L.inProgress, color = InkDisabled)
                }

                3 -> TextButton(
                    onClick = {
                        val files = listOfNotNull(
                            giftPath.takeIf { it.isNotBlank() }?.let { java.io.File(it) },
                            guidePath.takeIf { it.isNotBlank() }?.let { java.io.File(it) },
                        )
                        sharePackage(files)
                        Toast.makeText(context, L.handoverShareHint, Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
                ) {
                    Text(text = L.handoverShare)
                }

                else -> TextButton(onClick = onDismiss) {
                    Text(text = L.done, color = TimeGold)
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

/** 口令连错冷却（与备份弹窗同量级） */
private const val COOLDOWN_MILLIS = 30_000L
