package com.muxiao.timart.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.data.local.crypto.ContentCryptoManager
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.utils.export.BackupException
import com.muxiao.timart.utils.export.BackupManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 备份导出弹窗（v3：备份口令独立于主口令）：
 * 说明 → 口令步（备份口令未设置时内联设置两遍；会话锁定时另输主口令仅用于取得内容解密密钥）
 * → 导出（全部内容重加密到备份口令，主口令材料不入包）→ 展示结果路径。
 * 连续 5 次口令错误锁定 30s（计数与倒计时都在对话框内存态，架构 §2.12）；
 * 口令过短为客户端校验，仅提示不计次数。
 *
 * @param backupPwSet 备份口令是否已设置（SettingsViewModel 镜像；false 时弹窗内联设置并存档）
 * @param onSetBackupPassword 存档备份口令的回调（SettingsViewModel.setBackupPassword；成功后继续导出）
 */
@Composable
fun BackupExportDialog(
    manager: BackupManager,
    backupPwSet: Boolean,
    onSetBackupPassword: (CharArray, (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val L = LocalStrings.current
    // 0 加密说明 / 1 口令 / 2 进行中 / 3 完成 / 4 失败
    var step by remember { mutableIntStateOf(0) }
    var resultPath by remember { mutableStateOf("") }
    var unsupportedLabels by remember { mutableStateOf(emptyList<String>()) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var mainPassword by remember { mutableStateOf("") }
    var backupPassword by remember { mutableStateOf("") }
    var backupPasswordConfirm by remember { mutableStateOf("") }
    var failedAttempts by remember { mutableIntStateOf(0) }

    // 5 次口令错 30s 锁（对话框内存态，与导入弹窗同规则）
    var lockUntil by remember { mutableLongStateOf(0L) }
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val locked = nowTick < lockUntil
    LaunchedEffect(locked) {
        while (locked) {
            delay(500.milliseconds)
            nowTick = System.currentTimeMillis()
        }
    }

    val scope = rememberCoroutineScope()
    val needMainPassword = !manager.isSessionUnlocked()
    val confirmMismatch = backupPasswordConfirm.isNotEmpty() && backupPassword != backupPasswordConfirm

    fun startExport() {
        errorText = null
        step = 2
        val mainPw = if (needMainPassword) mainPassword.toCharArray() else null
        val bkPw = if (backupPwSet) null else backupPassword.toCharArray()
        scope.launch {
            val result = runCatching {
                manager.exportBackup(backupPassword = bkPw, mainPassword = mainPw)
            }
            mainPw?.fill('\u0000')
            bkPw?.fill('\u0000')
            if (result.isSuccess) {
                val export = result.getOrNull()
                resultPath = export?.file?.absolutePath ?: ""
                unsupportedLabels = export?.unsupportedLabels ?: emptyList()
                step = 3
            } else {
                val failure = result.exceptionOrNull()
                errorText = failure?.message ?: L.bkExportFailed
                if (failure is BackupException && failure.wrongPassword) {
                    failedAttempts++
                    if (failedAttempts >= 5) {
                        // 5 次错 → 30s 锁定（回输入口令步骤）
                        lockUntil = System.currentTimeMillis() + LOCK_MILLIS
                        failedAttempts = 0
                        mainPassword = ""
                    }
                    step = 1
                } else {
                    step = 4
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (step != 2) onDismiss() },
        containerColor = SurfaceRaise,
        title = { Text(text = L.bkExportTitle, style = TimartType.titleSerif) },
        text = {
            // text 槽位必须单根：多行文案包在 Column 里，否则白/黄提示互相叠压
            Column {
                when (step) {
                    0 -> {
                        InfoLine(text = L.bkExportInfo)
                        WarnLine(text = L.bkExportWarn)
                    }

                    1 -> {
                        if (needMainPassword) {
                            InfoLine(text = L.bkExportAskPw)
                            BasicTextField(
                                value = mainPassword,
                                onValueChange = { mainPassword = it },
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
                        if (backupPwSet) {
                            InfoLine(text = L.bkExportUseStored)
                        } else {
                            InfoLine(text = L.bkExportSetBkPw)
                            BasicTextField(
                                value = backupPassword,
                                onValueChange = { backupPassword = it },
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
                            BasicTextField(
                                value = backupPasswordConfirm,
                                onValueChange = { backupPasswordConfirm = it },
                                singleLine = true,
                                enabled = !locked,
                                textStyle = TimartType.body.copy(color = InkPrimary),
                                cursorBrush = SolidColor(TimeGold),
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .fillMaxWidth()
                                    .background(DeepCharcoal.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 14.dp, vertical = 13.dp),
                            )
                            if (confirmMismatch) {
                                WarnLine(text = L.bkPwMismatch)
                            }
                        }
                        if (locked) {
                            val remainSeconds = (lockUntil - nowTick) / 1000 + 1
                            WarnLine(text = L.bkCooldownFmt.format(remainSeconds))
                        } else if (failedAttempts > 0) {
                            WarnLine(text = L.bkAttemptsLeftFmt.format(5 - failedAttempts))
                        }
                    }

                    2 -> InfoLine(text = L.bkPacking)

                    3 -> {
                        InfoLine(text = L.bkExportDone)
                        MonoLine(text = resultPath)
                        if (unsupportedLabels.isNotEmpty()) {
                            WarnLine(
                                text = L.bkDeviceUnsupportedFmt
                                    .format(unsupportedLabels.joinToString(" · ")),
                            )
                        }
                    }

                    else -> WarnLine(text = errorText ?: L.bkExportFailed)
                }
            }
        },
        confirmButton = {
            when (step) {
                0 -> GoldTextButton(label = L.continueWord, onClick = { step = 1 })

                1 -> GoldTextButton(
                    label = L.bkExportTitle,
                    enabled = !locked &&
                        (!needMainPassword || mainPassword.isNotBlank()) &&
                        (backupPwSet || (backupPassword.length >= ContentCryptoManager.MIN_PASSWORD_LENGTH && !confirmMismatch)),
                    onClick = {
                        if (!backupPwSet) {
                            if (backupPassword.length < ContentCryptoManager.MIN_PASSWORD_LENGTH) {
                                // 客户端校验失败：仅提示，不计入口令错误次数
                                errorText = L.bkWrongPw
                                return@GoldTextButton
                            }
                            errorText = null
                            onSetBackupPassword(backupPassword.toCharArray()) { ok ->
                                if (ok) startExport() else errorText = L.bkPwSaveFailed
                            }
                        } else {
                            startExport()
                        }
                    },
                )

                2 -> TextButton(onClick = {}, enabled = false) {
                    Text(text = L.inProgress, color = InkDisabled)
                }

                else -> GoldTextButton(label = L.done, onClick = onDismiss)
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

/**
 * 备份口令设置/更换弹窗（设置页备份卡「备份口令」行入口）：
 * 两遍输入 → 存档 meta `settings.backupPw`（自动备份与导出共用）。
 * 仅存档口令本身，不触碰主口令体系；[onSaved] 供调用方刷新状态。
 */
@Composable
fun BackupPasswordDialog(
    manager: BackupManager,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
) {
    val L = LocalStrings.current
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val mismatch = confirm.isNotEmpty() && confirm != password
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        containerColor = SurfaceRaise,
        title = { Text(text = L.bkPwTitle, style = TimartType.titleSerif) },
        text = {
            Column {
                InfoLine(text = L.bkPwDesc)
                BasicTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    enabled = !saving,
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
                BasicTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    singleLine = true,
                    enabled = !saving,
                    textStyle = TimartType.body.copy(color = InkPrimary),
                    cursorBrush = SolidColor(TimeGold),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .background(DeepCharcoal.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                )
                if (mismatch) {
                    WarnLine(text = L.bkPwMismatch)
                }
                errorText?.let { WarnLine(text = it) }
            }
        },
        confirmButton = {
            if (saving) {
                TextButton(onClick = {}, enabled = false) {
                    Text(text = L.inProgress, color = InkDisabled)
                }
            } else {
                GoldTextButton(
                    label = L.save,
                    enabled = password.length >= ContentCryptoManager.MIN_PASSWORD_LENGTH && !mismatch,
                    onClick = {
                        saving = true
                        // meta 为主线程禁查的 Room 库：存档放 IO 协程
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val ok = manager.setBackupPassword(password.toCharArray())
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                saving = false
                                if (ok) {
                                    onSaved()
                                    onDismiss()
                                } else {
                                    errorText = L.bkPwSaveFailed
                                }
                            }
                        }
                    },
                )
            }
        },
        dismissButton = {
            if (!saving) {
                TextButton(onClick = onDismiss) {
                    Text(text = L.cancel, color = InkSecondary)
                }
            }
        },
    )
}

/**
 * 备份导入弹窗（重加密入库）：选文件 → 输入**该备份包的口令**（v3 = 导出时设置的备份口令；
 * 旧版备份 = 导出时的主口令）→ 校验解密 → 以本机会话密钥重加密入库。
 * 本机口令会话未解锁时明示引导先解锁（重加密目标缺失，绝不静默）。
 * 连续 5 次口令错误锁定 30s（计数与倒计时都在对话框内存态，架构 §2.12）。
 * 导入为追加语义：id 冲突的胶囊跳过。
 *
 * @param presetUri 预选的备份包（N4 历史备份指定份恢复；非空时跳过选文件步直接输口令）
 */
@Composable
fun BackupImportDialog(
    manager: BackupManager,
    onDismiss: () -> Unit,
    presetUri: Uri? = null,
) {
    val L = LocalStrings.current
    // 0 提示选文件 / 1 已选文件输口令 / 2 进行中 / 3 成功 / 4 失败
    var step by remember { mutableIntStateOf(if (presetUri != null) 1 else 0) }
    var pickedUri by remember { mutableStateOf(presetUri) }
    var password by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("") }
    var unsupportedLabels by remember { mutableStateOf(emptyList<String>()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    // 5 次错 30s 锁（对话框内存态）
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

    val scope = rememberCoroutineScope()
    val needLocalUnlock = !manager.isSessionUnlocked()
    val pickZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pickedUri = uri
            step = 1
        }
    }

    AlertDialog(
        onDismissRequest = { if (step != 2) onDismiss() },
        containerColor = SurfaceRaise,
        title = { Text(text = L.bkImportTitle, style = TimartType.titleSerif) },
        text = {
            // text 槽位必须单根：多行文案包在 Column 里，否则白/黄提示互相叠压
            Column {
                when (step) {
                    0 -> {
                        InfoLine(text = L.bkImportInfo)
                        WarnLine(text = L.bkImportWarn)
                    }

                    1 -> {
                        InfoLine(text = L.bkImportAskPw)
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
                        if (needLocalUnlock) {
                            WarnLine(text = L.bkImportNeedUnlock)
                        }
                        if (locked) {
                            val remainSeconds = (lockUntil - nowTick) / 1000 + 1
                            WarnLine(text = L.bkCooldownFmt.format(remainSeconds))
                        } else if (failedAttempts > 0) {
                            WarnLine(text = L.bkAttemptsLeftFmt.format(5 - failedAttempts))
                        }
                    }

                    2 -> InfoLine(text = L.bkVerifying)

                    3 -> {
                        InfoLine(text = resultText)
                        if (unsupportedLabels.isNotEmpty()) {
                            WarnLine(
                                text = L.bkDeviceUnsupportedFmt
                                    .format(unsupportedLabels.joinToString(" · ")),
                            )
                        }
                    }

                    else -> WarnLine(text = errorText ?: L.bkImportFailed)
                }
            }
        },
        confirmButton = {
            when (step) {
                0 -> GoldTextButton(
                    label = L.bkPickFile,
                    onClick = {
                        pickZip.launch(
                            arrayOf(
                                "application/zip",
                                "application/x-zip-compressed",
                                "application/octet-stream",
                            ),
                        )
                    },
                )

                1 -> GoldTextButton(
                    label = L.bkImportTitle,
                    enabled = password.isNotBlank() && !locked && !needLocalUnlock,
                    onClick = {
                        val uri = pickedUri
                        if (uri != null) {
                            step = 2
                            scope.launch {
                                val result = runCatching {
                                    manager.importBackup(uri, password.toCharArray())
                                }
                                result.fold(
                                    onSuccess = { r ->
                                        resultText =
                                            L.bkImportDoneFmt.format(r.importedCapsules, r.skippedCapsules, r.importedDestroyed)
                                        unsupportedLabels = r.unsupportedLabels
                                        step = 3
                                    },
                                    onFailure = { throwable ->
                                        val wrongPassword = throwable is BackupException &&
                                            throwable.wrongPassword
                                        errorText = throwable.message ?: L.bkImportFailed
                                        if (wrongPassword) {
                                            failedAttempts++
                                            if (failedAttempts >= 5) {
                                                // 5 次错 → 30s 锁定（回输入口令步骤）
                                                lockUntil = System.currentTimeMillis() + LOCK_MILLIS
                                                failedAttempts = 0
                                                password = ""
                                            }
                                            step = 1
                                        } else {
                                            step = 4
                                        }
                                    },
                                )
                            }
                        }
                    },
                )

                2 -> TextButton(onClick = {}, enabled = false) {
                    Text(text = L.inProgress, color = InkDisabled)
                }

                else -> GoldTextButton(label = L.done, onClick = onDismiss)
            }
        },
        dismissButton = {
            if (step == 0 || step == 1 || step == 4) {
                TextButton(onClick = onDismiss) {
                    Text(text = L.cancel, color = InkSecondary)
                }
            }
        },
    )
}

// ---- 弹窗内文案与按钮（文件内私有） ----

/** 5 次口令错误的锁定时长（30s；迁移向导同规则复用） */
internal const val LOCK_MILLIS = 30_000L

@Composable
internal fun InfoLine(text: String) {
    Text(text = text, style = TimartType.caption, color = InkSecondary)
}

@Composable
internal fun WarnLine(text: String) {
    Text(
        text = text,
        style = TimartType.caption,
        color = TimeGold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
internal fun MonoLine(text: String) {
    Text(
        text = text,
        style = TimartType.caption,
        color = InkDisabled,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
internal fun GoldTextButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
    ) {
        Text(text = label)
    }
}

/**
 * 单胶囊赠予导出弹窗（体验储备池 §4）：说明 → （会话锁定时输口令 / 已解锁直接确认）→ 打包 → 展示结果路径。
 * 与整库导出的差异：只含一颗胶囊；依赖关系不随赠予携带；不写 BackupDone 标记。
 */
@Composable
fun GiftExportDialog(
    manager: BackupManager,
    capsuleId: String,
    onDismiss: () -> Unit,
) {
    val L = LocalStrings.current
    // 0 说明 / 1 口令或确认 / 2 进行中 / 3 完成 / 4 失败
    var step by remember { mutableIntStateOf(0) }
    var resultPath by remember { mutableStateOf("") }
    var unsupportedLabels by remember { mutableStateOf(emptyList<String>()) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
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

    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (step != 2) onDismiss() },
        containerColor = SurfaceRaise,
        title = { Text(text = L.giftExportTitle, style = TimartType.titleSerif) },
        text = {
            Column {
                when (step) {
                    0 -> {
                        InfoLine(text = L.giftExportInfo)
                        WarnLine(text = L.giftExportNote)
                    }

                    1 -> {
                        if (manager.isSessionUnlocked()) {
                            InfoLine(text = L.bkExportSessionInfo)
                        } else {
                            InfoLine(text = L.bkExportAskPw)
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
                            errorText?.let { WarnLine(text = it) }
                            if (locked) {
                                val remainSeconds = (lockUntil - nowTick) / 1000 + 1
                                WarnLine(text = L.bkCooldownFmt.format(remainSeconds))
                            } else if (failedAttempts > 0) {
                                WarnLine(text = L.bkAttemptsLeftFmt.format(5 - failedAttempts))
                            }
                        }
                    }

                    2 -> InfoLine(text = L.bkPacking)

                    3 -> {
                        InfoLine(text = L.giftExportDone)
                        MonoLine(text = resultPath)
                        if (unsupportedLabels.isNotEmpty()) {
                            WarnLine(
                                text = L.bkDeviceUnsupportedFmt
                                    .format(unsupportedLabels.joinToString(" · ")),
                            )
                        }
                    }

                    else -> WarnLine(text = errorText ?: L.bkExportFailed)
                }
            }
        },
        confirmButton = {
            when (step) {
                0 -> GoldTextButton(label = L.continueWord, onClick = { step = 1 })

                1 -> GoldTextButton(
                    label = L.giftExportTitle,
                    enabled = (manager.isSessionUnlocked() || password.isNotBlank()) && !locked,
                    onClick = {
                        val usePassword = !manager.isSessionUnlocked()
                        if (usePassword && password.length < ContentCryptoManager.MIN_PASSWORD_LENGTH) {
                            errorText = L.bkWrongPw
                            return@GoldTextButton
                        }
                        errorText = null
                        step = 2
                        scope.launch {
                            val result = runCatching {
                                manager.exportGift(capsuleId, if (usePassword) password.toCharArray() else null)
                            }
                            if (result.isSuccess) {
                                val export = result.getOrNull()
                                resultPath = export?.file?.absolutePath ?: ""
                                unsupportedLabels = export?.unsupportedLabels ?: emptyList()
                                step = 3
                            } else {
                                val failure = result.exceptionOrNull()
                                errorText = failure?.message ?: L.bkExportFailed
                                if (failure is BackupException && failure.wrongPassword) {
                                    failedAttempts++
                                    if (failedAttempts >= 5) {
                                        lockUntil = System.currentTimeMillis() + LOCK_MILLIS
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
                )

                2 -> TextButton(onClick = {}, enabled = false) {
                    Text(text = L.inProgress, color = InkDisabled)
                }

                else -> GoldTextButton(label = L.done, onClick = onDismiss)
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

/**
 * 赠予收下弹窗（重加密导入）：选文件 → 输入**封存者告知的口令** → 校验解密 →
 * 以本机会话密钥重加密入库。本机口令未解锁时导入侧显式报错并引导先解锁。
 */
@Composable
fun GiftImportDialog(
    manager: BackupManager,
    onDismiss: () -> Unit,
) {
    val L = LocalStrings.current
    // 0 提示选文件 / 1 输封存者口令 / 2 进行中 / 3 成功 / 4 失败
    var step by remember { mutableIntStateOf(0) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("") }
    var unsupportedLabels by remember { mutableStateOf(emptyList<String>()) }
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

    val scope = rememberCoroutineScope()
    val pickZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pickedUri = uri
            step = 1
        }
    }

    AlertDialog(
        onDismissRequest = { if (step != 2) onDismiss() },
        containerColor = SurfaceRaise,
        title = { Text(text = L.giftImportTitle, style = TimartType.titleSerif) },
        text = {
            Column {
                when (step) {
                    0 -> {
                        InfoLine(text = L.giftImportInfo)
                        WarnLine(text = L.giftImportAskPw)
                    }

                    1 -> {
                        InfoLine(text = L.giftImportAskPw)
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
                        if (locked) {
                            val remainSeconds = (lockUntil - nowTick) / 1000 + 1
                            WarnLine(text = L.bkCooldownFmt.format(remainSeconds))
                        } else if (failedAttempts > 0) {
                            WarnLine(text = L.bkAttemptsLeftFmt.format(5 - failedAttempts))
                        }
                    }

                    2 -> InfoLine(text = L.bkVerifying)

                    3 -> {
                        InfoLine(text = resultText)
                        if (unsupportedLabels.isNotEmpty()) {
                            WarnLine(
                                text = L.bkDeviceUnsupportedFmt
                                    .format(unsupportedLabels.joinToString(" · ")),
                            )
                        }
                    }

                    else -> WarnLine(text = errorText ?: L.bkImportFailed)
                }
            }
        },
        confirmButton = {
            when (step) {
                0 -> GoldTextButton(
                    label = L.bkPickFile,
                    onClick = {
                        pickZip.launch(
                            arrayOf(
                                "application/zip",
                                "application/x-zip-compressed",
                                "application/octet-stream",
                            ),
                        )
                    },
                )

                1 -> GoldTextButton(
                    label = L.giftImportTitle,
                    enabled = password.isNotBlank() && !locked,
                    onClick = {
                        val uri = pickedUri
                        if (uri != null) {
                            step = 2
                            scope.launch {
                                val result = runCatching {
                                    manager.importGift(uri, password.toCharArray())
                                }
                                result.fold(
                                    onSuccess = { r ->
                                        resultText = L.giftImportDoneFmt.format(r.importedCapsules, r.skippedCapsules)
                                        unsupportedLabels = r.unsupportedLabels
                                        step = 3
                                    },
                                    onFailure = { throwable ->
                                        val wrongPassword = throwable is BackupException &&
                                            throwable.wrongPassword
                                        errorText = throwable.message ?: L.bkImportFailed
                                        if (wrongPassword) {
                                            failedAttempts++
                                            if (failedAttempts >= 5) {
                                                lockUntil = System.currentTimeMillis() + LOCK_MILLIS
                                                failedAttempts = 0
                                                password = ""
                                            }
                                            step = 1
                                        } else {
                                            step = 4
                                        }
                                    },
                                )
                            }
                        }
                    },
                )

                2 -> TextButton(onClick = {}, enabled = false) {
                    Text(text = L.inProgress, color = InkDisabled)
                }

                else -> GoldTextButton(label = L.done, onClick = onDismiss)
            }
        },
        dismissButton = {
            if (step == 0 || step == 1 || step == 4) {
                TextButton(onClick = onDismiss) {
                    Text(text = L.cancel, color = InkSecondary)
                }
            }
        },
    )
}
