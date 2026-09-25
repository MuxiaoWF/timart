package com.muxiao.timart.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.AppContainer
import com.muxiao.timart.data.local.crypto.ContentCryptoManager
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 首次设置隐私口令（架构 §2.16，PASSWORD_SETUP 路由目的地）：
 * 两遍输入 + 「我已知晓遗忘口令将永久丢失数据」勾选，通过后调用
 * [com.muxiao.timart.data.local.crypto.ContentCryptoManager.setupPassword]。
 * 成功返回创建流程（popBackStack），创建页 ON_RESUME 续跑被挂起的封存。
 * T13 将在本文件扩展「修改口令」模式（旧口令验证 + 全库重加密）。
 */
@Composable
fun PasswordSetupScreen(
    container: AppContainer,
    onDone: () -> Unit,
) {
    val L = LocalStrings.current
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    var acknowledged by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val minLength = ContentCryptoManager.MIN_PASSWORD_LENGTH

    // 口令若已设置（如进程重建后再次进入），直接返回避免重复设置
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch(Dispatchers.IO) {
                    val alreadySetup = runCatching {
                        container.contentCryptoManager.hasPasswordSetup()
                    }.getOrDefault(false)
                    if (alreadySetup) {
                        withContext(Dispatchers.Main) { onDone() }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .background(DeepCharcoal.copy(alpha = 0.97f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .background(SurfaceRaise, RoundedCornerShape(20.dp))
                .padding(24.dp),
        ) {
            Text(text = L.pwSetupTitle, style = TimartType.titleSerif, color = InkPrimary)
            Text(
                text = L.pwSetupDesc,
                style = TimartType.caption,
                color = InkSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )

            PasswordField(
                label = L.pwInputLabelFmt.format(minLength),
                value = password,
                onValueChange = {
                    password = it
                    errorText = null
                },
                enabled = !busy,
                modifier = Modifier.padding(top = 18.dp),
            )
            PasswordField(
                label = L.pwReinputLabel,
                value = confirm,
                onValueChange = { confirm = it },
                enabled = !busy,
                modifier = Modifier.padding(top = 10.dp),
            )

            // 口令提示语（储备池 v6；可选，明文 meta——提示语本就是给"记不起口令的自己"看的）
            Text(
                text = L.pwHintLabel,
                style = TimartType.caption,
                color = InkSecondary,
                modifier = Modifier.padding(top = 12.dp),
            )
            BasicTextField(
                value = hint,
                onValueChange = { hint = it },
                singleLine = true,
                enabled = !busy,
                textStyle = TimartType.body.copy(color = InkPrimary),
                cursorBrush = SolidColor(TimeGold),
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .background(DeepCharcoal.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 13.dp),
            )

            // 知晓勾选（手绘圆圈，不用 emoji/图标库）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .clickable(enabled = !busy) { acknowledged = !acknowledged },
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .border(
                            width = 1.dp,
                            color = if (acknowledged) TimeGold else InkDisabled,
                            shape = CircleShape,
                        )
                        .background(
                            color = if (acknowledged) TimeGold else androidx.compose.ui.graphics.Color.Transparent,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (acknowledged) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(DeepCharcoal, CircleShape),
                        )
                    }
                }
                Text(
                    text = L.pwAck,
                    style = TimartType.caption,
                    color = InkSecondary,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }

            errorText?.let { message ->
                Text(
                    text = message,
                    style = TimartType.caption,
                    color = TimeGold,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            val valid = password.length >= minLength && password == confirm && acknowledged
            Button(
                onClick = {
                    busy = true
                    errorText = null
                    scope.launch(Dispatchers.IO) {
                        val ok = runCatching {
                            container.contentCryptoManager.setupPassword(password.toCharArray())
                        }.isSuccess
                        if (ok) {
                            // 口令提示语（储备池 v6）：可选；空 = 不写键（解锁弹窗不显示）
                            runCatching {
                                val trimmed = hint.trim()
                                val dao = container.database.metaDao()
                                if (trimmed.isEmpty()) {
                                    dao.delete(com.muxiao.timart.utils.RuntimeSettings.KEY_PW_HINT)
                                } else {
                                    dao.put(
                                        com.muxiao.timart.data.local.db.entity.MetaEntity(
                                            com.muxiao.timart.utils.RuntimeSettings.KEY_PW_HINT,
                                            trimmed,
                                        ),
                                    )
                                }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            busy = false
                            if (ok) {
                                onDone()
                            } else {
                                errorText = L.pwSetupFailed
                            }
                        }
                    }
                },
                enabled = valid && !busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TimeGold,
                    contentColor = DeepCharcoal,
                    disabledContainerColor = DeepCharcoal.copy(alpha = 0.6f),
                    disabledContentColor = InkDisabled,
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .padding(top = 20.dp)
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(
                    text = if (busy) L.pwSetupBusy else L.pwSetupCta,
                    style = TimartType.body,
                )
            }
        }
    }
}

/** 口令输入行（密文显示；浮层灰底无边框） */
@Composable
private fun PasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = label, style = TimartType.caption, color = InkSecondary)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            enabled = enabled,
            textStyle = TimartType.body.copy(color = InkPrimary),
            cursorBrush = SolidColor(TimeGold),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
            ),
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .background(DeepCharcoal.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 13.dp),
        )
    }
}

/**
 * 口令解锁弹窗：冷启动后首次阅读时，会话密钥已随进程失效，
 * 阅读内容前需输口令重新派生（ContentCryptoManager.unlock）。
 * 详情页与星库重读共用；错误提示由调用方传入。
 * [hintText] = 口令提示语（储备池 v6；meta `settings.pwHint`，null/空 = 不显示）。
 */
@Composable
fun PasswordUnlockDialog(
    errorText: String?,
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit,
    hintText: String? = null,
) {
    val L = LocalStrings.current
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceRaise,
        title = { Text(text = L.pwUnlockTitle, style = TimartType.titleSerif) },
        text = {
            Column {
                Text(
                    text = L.pwUnlockDesc,
                    style = TimartType.caption,
                    color = InkSecondary,
                )
                if (!hintText.isNullOrBlank()) {
                    Text(
                        text = L.pwUnlockHintFmt.format(hintText),
                        style = TimartType.caption,
                        color = TimeGold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                BasicTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    textStyle = TimartType.body.copy(color = InkPrimary),
                    cursorBrush = SolidColor(TimeGold),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false,
                    ),
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .background(DeepCharcoal.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                )
                if (errorText != null) {
                    Text(
                        text = errorText,
                        style = TimartType.caption,
                        color = TimeGold,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotBlank(),
                colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
            ) {
                Text(text = L.pwUnlockCta)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = L.cancel, color = InkSecondary)
            }
        },
    )
}

/**
 * 修改口令弹窗：旧口令验证 → 新口令两遍输入 → 全库胶囊与图片重加密。
 * 进度期间：输入与按钮禁用、back / 点击外部不可退出（DialogProperties），
 * 完成后展示结果并要求点击确认关闭；错误信息内联展示（旧口令错 / 新口令不一致）。
 */
@Composable
fun ModifyPasswordDialog(
    state: SettingsViewModel.PasswordChangeState?,
    onConfirm: (old: String, new: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val L = LocalStrings.current
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val minLength = ContentCryptoManager.MIN_PASSWORD_LENGTH
    val busy = state?.takeIf { !it.finished }
    val result = state?.takeIf { it.finished }
    val valid = old.isNotBlank() && new.length >= minLength && new == confirm

    AlertDialog(
        onDismissRequest = { if (busy == null) onDismiss() },
        containerColor = SurfaceRaise,
        properties = DialogProperties(
            dismissOnBackPress = busy == null,
            dismissOnClickOutside = busy == null,
        ),
        title = { Text(text = L.pwModifyTitle, style = TimartType.titleSerif) },
        text = {
            Column {
                when {
                    busy != null -> {
                        Text(
                            text = L.pwModifyBusy,
                            style = TimartType.caption,
                            color = InkSecondary,
                        )
                        if (busy.total > 0) {
                            LinearProgressIndicator(
                                progress = { busy.done.toFloat() / busy.total.toFloat() },
                                modifier = Modifier
                                    .padding(top = 12.dp)
                                    .fillMaxWidth(),
                                color = TimeGold,
                                trackColor = DeepCharcoal.copy(alpha = 0.4f),
                            )
                            Text(
                                text = "${busy.done} / ${busy.total}",
                                style = TimartType.caption,
                                color = InkSecondary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        } else {
                            Text(
                                text = L.pwPreparing,
                                style = TimartType.caption,
                                color = InkSecondary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }

                    result != null -> {
                        if (result.error != null) {
                            Text(
                                text = result.error,
                                style = TimartType.caption,
                                color = TimeGold,
                            )
                        } else {
                            Text(
                                text = L.pwModifyDone,
                                style = TimartType.caption,
                                color = InkSecondary,
                            )
                        }
                    }

                    else -> {
                        localError?.let {
                            Text(
                                text = it,
                                style = TimartType.caption,
                                color = TimeGold,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        PasswordField(
                            label = L.pwOldLabel,
                            value = old,
                            onValueChange = {
                                old = it
                                localError = null
                            },
                            enabled = true,
                        )
                        PasswordField(
                            label = L.pwNewLabelFmt.format(minLength),
                            value = new,
                            onValueChange = { new = it },
                            enabled = true,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        PasswordField(
                            label = L.pwReinputLabel,
                            value = confirm,
                            onValueChange = { confirm = it },
                            enabled = true,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        Text(
                            text = L.pwKeepForeground,
                            style = TimartType.caption,
                            color = InkSecondary,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                busy != null -> {
                    TextButton(onClick = {}, enabled = false) {
                        Text(text = L.inProgress, color = InkDisabled)
                    }
                }

                result != null -> {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
                    ) {
                        Text(text = L.done)
                    }
                }

                else -> {
                    TextButton(
                        onClick = {
                            if (new != confirm) {
                                localError = L.pwMismatch
                            } else {
                                onConfirm(old, new)
                            }
                        },
                        enabled = valid,
                        colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
                    ) {
                        Text(text = L.pwStartReencrypt)
                    }
                }
            }
        },
        dismissButton = {
            if (busy == null && result == null) {
                TextButton(onClick = onDismiss) {
                    Text(text = L.cancel, color = InkSecondary)
                }
            }
        },
    )
}
