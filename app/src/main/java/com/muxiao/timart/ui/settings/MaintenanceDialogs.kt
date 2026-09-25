package com.muxiao.timart.ui.settings

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.muxiao.timart.AppContainer
import com.muxiao.timart.data.local.crypto.ContentCryptoManager
import com.muxiao.timart.domain.usecase.LibraryHealthCheck
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.theme.DeepCharcoal
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.utils.export.BackupException
import com.muxiao.timart.utils.format.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

/**
 * 维护类弹窗：整库体检 + 跨设备迁移向导（设置页「信息类」卡入口）。
 * 两者均为**只读/追加语义**：体检只报告 + 可选清理孤儿记录（二次确认）；
 * 迁移复用 BackupManager 的 v2 导出/导入主链路，不另写打包与校验逻辑。
 */

/** 单条体检发现的 UI 模型（orphanKeys 非空时显示「清理」入口） */
private data class HealthRow(
    val text: String,
    val orphanKeys: List<String> = emptyList(),
)

/**
 * 整库体检弹窗：打开即扫（纯逻辑 + 文件在位核对，不改任何数据）——
 * 孤儿 meta 键 / 依赖死链 / 销毁锁死 / 依赖环 / 分片材料 / 缺失图片文件 + 上次备份时间。
 * 「清理孤儿记录」需二次确认后逐键删除（不可恢复，弹窗文案明示）。
 */
@Composable
fun HealthCheckDialog(
    container: AppContainer,
    onDismiss: () -> Unit,
) {
    val L = LocalStrings.current
    var running by remember { mutableStateOf(true) }
    var rows by remember { mutableStateOf<List<HealthRow>>(emptyList()) }
    var lastBackupLine by remember { mutableStateOf<String?>(null) }
    var confirmCleanup by remember { mutableStateOf(false) }
    var cleanupNote by remember { mutableStateOf<String?>(null) }

    // 防止重组期重复扫描（LaunchedEffect(Unit) 已保证，双保险）
    val scanning = remember { AtomicBoolean(false) }

    fun scan(scope: kotlinx.coroutines.CoroutineScope) {
        if (!scanning.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            val capsules = runCatching { container.capsuleRepository.allSync() }.getOrDefault(emptyList())
            val metaRows = runCatching { container.database.metaDao().listLike("capsule.") }
                .getOrDefault(emptyList())
            val findings = LibraryHealthCheck.scan(
                capsules = capsules,
                metaKeys = metaRows.map { it.key },
                condMetPrefix = com.muxiao.timart.data.local.db.CapsuleMetaKeys.COND_MET_KEY_PREFIX,
                prefixes = com.muxiao.timart.data.local.db.CapsuleMetaKeys.ORPHAN_SCAN_PREFIXES,
            )

            // 图片文件在位核对（imageFiles 清单 vs 磁盘密文文件，不触碰密文本体）
            val missingImages = capsules.flatMap { capsule ->
                capsule.imageFiles.mapNotNull { name ->
                    val index = name.removePrefix("img_").removeSuffix(".bin").toIntOrNull()
                        ?: return@mapNotNull null
                    if (container.imageCipherStore.blobExists(capsule.id, index)) {
                        null
                    } else {
                        "${capsule.title} · $name"
                    }
                }
            }

            val orphanKeys = findings
                .filter { it.kind == LibraryHealthCheck.Kind.ORPHAN_META }
                .flatMap { it.detail.split("\n") }
            val newRows = buildList {
                findings.forEach { finding ->
                    when (finding.kind) {
                        LibraryHealthCheck.Kind.ORPHAN_META -> Unit // 汇总为 orphanKeys 行
                        LibraryHealthCheck.Kind.DEAD_DEPENDENCY ->
                            add(HealthRow(L.healthDeadDepFmt.format(finding.detail)))
                        LibraryHealthCheck.Kind.DESTROYED_DEPENDENCY ->
                            add(HealthRow(L.healthDestroyedDepFmt.format(finding.detail)))
                        LibraryHealthCheck.Kind.CYCLE ->
                            add(HealthRow(L.healthCycleFmt.format(finding.detail)))
                        LibraryHealthCheck.Kind.SHARD_MATERIAL ->
                            add(HealthRow(L.healthShardFmt.format(finding.detail)))
                        LibraryHealthCheck.Kind.RULE_ANOMALY ->
                            add(HealthRow(L.healthRuleFmt.format(finding.detail)))
                    }
                }
                if (orphanKeys.isNotEmpty()) {
                    val shown = orphanKeys.take(5).joinToString(" · ") +
                        if (orphanKeys.size > 5) " …" else ""
                    add(
                        HealthRow(
                            text = L.healthOrphanMetaFmt.format(orphanKeys.size, shown),
                            orphanKeys = orphanKeys,
                        ),
                    )
                }
                if (missingImages.isNotEmpty()) {
                    add(HealthRow(L.healthMissingImageFmt.format(missingImages.joinToString(" · "))))
                }
            }
            val config = runCatching { container.autoBackupManager.readConfig() }.getOrNull()
            withContext(Dispatchers.Main) {
                rows = newRows
                lastBackupLine = config?.lastAt?.let { L.healthBackupAgeFmt.format(TimeFormatter.dateTime(it)) }
                running = false
            }
            scanning.set(false)
        }
    }

    val dialogScope = rememberCoroutineScope()
    LaunchedEffect(Unit) { scan(dialogScope) }

    if (confirmCleanup) {
        AlertDialog(
            onDismissRequest = { confirmCleanup = false },
            containerColor = SurfaceRaise,
            title = { Text(text = L.healthCleanup, style = TimartType.titleSerif) },
            text = {
                Text(
                    text = L.healthCleanupConfirmBody.format(rows.sumOf { it.orphanKeys.size }),
                    style = TimartType.caption,
                    color = InkSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCleanup = false
                        val keys = rows.flatMap { it.orphanKeys }
                        dialogScope.launch(Dispatchers.IO) {
                            keys.forEach { key ->
                                runCatching { container.database.metaDao().delete(key) }
                            }
                            withContext(Dispatchers.Main) {
                                cleanupNote = L.healthCleanupDoneFmt.format(keys.size)
                            }
                            scanning.set(false)
                            scan(dialogScope)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
                ) {
                    Text(text = L.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCleanup = false }) {
                    Text(text = L.cancel, color = InkSecondary)
                }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceRaise,
        title = { Text(text = L.setHealthCheck, style = TimartType.titleSerif) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                when {
                    running -> InfoLine(text = L.healthRunning)

                    rows.isEmpty() -> {
                        InfoLine(text = L.healthAllGood)
                        lastBackupLine?.let { InfoLine(text = it) }
                    }

                    else -> {
                        rows.forEach { row ->
                            Text(
                                text = row.text,
                                style = TimartType.caption,
                                color = InkSecondary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        lastBackupLine?.let { InfoLine(text = it) }
                        cleanupNote?.let { WarnLine(text = it) }
                    }
                }
            }
        },
        confirmButton = {
            if (!running && rows.any { it.orphanKeys.isNotEmpty() }) {
                TextButton(
                    onClick = { confirmCleanup = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
                ) {
                    Text(text = L.healthCleanup)
                }
            } else {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
                ) {
                    Text(text = L.ok)
                }
            }
        },
        dismissButton = {
            if (!running && rows.isNotEmpty()) {
                TextButton(onClick = onDismiss) {
                    Text(text = L.cancel, color = InkSecondary)
                }
            }
        },
    )
}

/**
 * 跨设备迁移向导（本地优先内：迁移包 = v3 备份 zip，密文自包含、全程不经网络）：
 *
 * - 旧设备：生成迁移包（会话已解锁免输主口令，否则输主口令仅用于取得内容解密密钥；
 *   备份口令未设置时内联设置）→ 分享面板发送；
 * - 新设备：解锁本机口令 → 选迁移包 → 输**迁移包口令**（旧设备导出时设置的备份口令，
 *   Verifier 随包校验）→ 重加密追加导入 → 报告核对清单。导入后无需旧设备的主口令。
 * 导入与导出复用 BackupManager 主链路；Keystore 会话副本不随迁（新设备用自己的口令）在说明中明示。
 */
@Composable
fun MigrationWizardDialog(
    container: AppContainer,
    onDismiss: () -> Unit,
) {
    val L = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = container.backupManager

    // 模式：null 选角色 / EXPORT 旧设备 / IMPORT 新设备
    var mode by remember { mutableStateOf<String?>(null) }

    // 备份口令状态（弹窗开启时 IO 读一次快照；meta 为主线程禁查的 Room 库，composition 内不直读）
    var backupPwSet by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            backupPwSet = manager.isBackupPasswordSet()
        }
    }

    // ---- 导出分支状态 ----
    var exportStep by remember { mutableIntStateOf(0) } // 0 口令/确认 1 进行中 2 完成
    var exportPassword by remember { mutableStateOf("") }
    var backupPassword by remember { mutableStateOf("") }
    var backupPasswordConfirm by remember { mutableStateOf("") }
    var exportError by remember { mutableStateOf<String?>(null) }
    var exportedFile by remember { mutableStateOf<java.io.File?>(null) }
    var exportFailedAttempts by remember { mutableIntStateOf(0) }

    // ---- 导入分支状态 ----
    var importStep by remember { mutableIntStateOf(0) } // 0 选文件 1 输口令 2 进行中 3 报告 4 失败
    var importedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var importPassword by remember { mutableStateOf("") }
    var importResult by remember { mutableStateOf("") }
    var importUnsupported by remember { mutableStateOf(emptyList<String>()) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importFailedAttempts by remember { mutableIntStateOf(0) }
    var importLockUntil by remember { mutableLongStateOf(0L) }
    var importNowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val importLocked = importNowTick < importLockUntil
    LaunchedEffect(importLocked) {
        while (importLocked) {
            kotlinx.coroutines.delay(500.milliseconds)
            importNowTick = System.currentTimeMillis()
        }
    }

    val pickZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importedUri = uri
            importStep = 1
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (exportStep != 1 && importStep != 2) onDismiss()
        },
        containerColor = SurfaceRaise,
        title = { Text(text = L.migrateTitle, style = TimartType.titleSerif) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                when (mode) {
                    null -> {
                        InfoLine(text = L.migrateIntro1)
                        WarnLine(text = L.migrateIntro2)
                        ModeRow(
                            label = L.migrateRoleExport,
                            description = L.migrateRoleExportDesc,
                            onClick = {
                                mode = "EXPORT"
                                exportStep = 0
                                exportError = null
                            },
                        )
                        ModeRow(
                            label = L.migrateRoleImport,
                            description = L.migrateRoleImportDesc,
                            onClick = {
                                mode = "IMPORT"
                                importStep = 0
                            },
                        )
                    }

                    "EXPORT" -> when (exportStep) {
                        0 -> {
                            InfoLine(text = L.migrateRoleExportDesc)
                            if (manager.isSessionUnlocked()) {
                                InfoLine(text = L.bkExportSessionInfo)
                            } else {
                                InfoLine(text = L.bkExportAskPw)
                                BasicTextField(
                                    value = exportPassword,
                                    onValueChange = { exportPassword = it },
                                    singleLine = true,
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
                            if (backupPwSet == null) {
                                InfoLine(text = L.inProgress)
                            } else if (backupPwSet == true) {
                                InfoLine(text = L.bkExportUseStored)
                            } else {
                                InfoLine(text = L.bkExportSetBkPw)
                                BasicTextField(
                                    value = backupPassword,
                                    onValueChange = { backupPassword = it },
                                    singleLine = true,
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
                                if (backupPasswordConfirm.isNotEmpty() && backupPassword != backupPasswordConfirm) {
                                    WarnLine(text = L.bkPwMismatch)
                                }
                            }
                            exportError?.let { WarnLine(text = it) }
                            if (exportFailedAttempts > 0) {
                                WarnLine(text = L.bkAttemptsLeftFmt.format(5 - exportFailedAttempts))
                            }
                        }

                        1 -> InfoLine(text = L.bkPacking)

                        else -> {
                            InfoLine(text = L.migrateExportDone)
                            MonoLine(text = exportedFile?.absolutePath ?: "")
                            if (exportedFile == null) {
                                WarnLine(text = exportError ?: L.bkExportFailed)
                            }
                        }
                    }

                    else -> when (importStep) {
                        0 -> InfoLine(text = L.migrateRoleImportDesc)

                        1 -> {
                            InfoLine(text = L.bkImportAskPw)
                            BasicTextField(
                                value = importPassword,
                                onValueChange = { importPassword = it },
                                singleLine = true,
                                enabled = !importLocked,
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
                            if (!manager.isSessionUnlocked()) {
                                WarnLine(text = L.bkImportNeedUnlock)
                            }
                            if (importLocked) {
                                val remain = (importLockUntil - importNowTick) / 1000 + 1
                                WarnLine(text = L.bkCooldownFmt.format(remain))
                            } else if (importFailedAttempts > 0) {
                                WarnLine(text = L.bkAttemptsLeftFmt.format(5 - importFailedAttempts))
                            }
                        }

                        2 -> InfoLine(text = L.bkVerifying)

                        3 -> {
                            InfoLine(text = importResult)
                            if (importUnsupported.isNotEmpty()) {
                                WarnLine(
                                    text = L.bkDeviceUnsupportedFmt.format(
                                        importUnsupported.joinToString(" · "),
                                    ),
                                )
                            }
                            WarnLine(text = L.migrateImportChecklist)
                        }

                        else -> WarnLine(text = importError ?: L.bkImportFailed)
                    }
                }
            }
        },
        confirmButton = {
            when (mode) {
                null -> Unit

                "EXPORT" -> when (exportStep) {
                    0 -> GoldTextButton(
                        label = L.continueWord,
                        enabled = backupPwSet != null &&
                            (!manager.isSessionUnlocked() || exportPassword.isNotBlank()) &&
                            (backupPwSet == true ||
                                (backupPassword.length >= ContentCryptoManager.MIN_PASSWORD_LENGTH &&
                                    (backupPasswordConfirm.isEmpty() || backupPassword == backupPasswordConfirm))),
                        onClick = {
                            val usePassword = !manager.isSessionUnlocked()
                            if (usePassword &&
                                exportPassword.length < ContentCryptoManager.MIN_PASSWORD_LENGTH
                            ) {
                                exportError = L.bkWrongPw
                                return@GoldTextButton
                            }
                            exportError = null
                            exportStep = 1
                            scope.launch(Dispatchers.IO) {
                                // 备份口令未设置：先存档（meta putSync 须 IO）再导出（自动备份与后续导出共用）
                                var bkPw: CharArray? = null
                                var saved = true
                                if (backupPwSet != true) {
                                    bkPw = backupPassword.toCharArray()
                                    saved = manager.setBackupPassword(bkPw)
                                }
                                if (!saved) {
                                    withContext(Dispatchers.Main) {
                                        exportError = L.bkPwSaveFailed
                                        exportStep = 0
                                    }
                                    return@launch
                                }
                                withContext(Dispatchers.Main) { backupPwSet = true }
                                val result = runCatching {
                                    manager.exportBackup(
                                        backupPassword = bkPw,
                                        mainPassword = if (usePassword) exportPassword.toCharArray() else null,
                                    )
                                }
                                withContext(Dispatchers.Main) {
                                    result.fold(
                                        onSuccess = { export ->
                                            exportedFile = export.file
                                            exportStep = 2
                                        },
                                        onFailure = { throwable ->
                                            exportError = throwable.message ?: L.bkExportFailed
                                            if (throwable is BackupException && throwable.wrongPassword) {
                                                exportFailedAttempts++
                                                exportStep = 0
                                            } else {
                                                exportedFile = null
                                                exportStep = 2
                                            }
                                        },
                                    )
                                }
                            }
                        },
                    )

                    1 -> TextButton(onClick = {}, enabled = false) {
                        Text(text = L.inProgress, color = InkSecondary)
                    }

                    else -> if (exportedFile != null) {
                        GoldTextButton(label = L.migrateShare, onClick = {
                            val file = exportedFile ?: return@GoldTextButton
                            runCatching {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file,
                                )
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    // ClipData 让授权随 Chooser 传递（部分 ROM 仅 EXTRA_STREAM 不传播）
                                    clipData = ClipData.newRawUri("backup", uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(send, L.migrateShare))
                            }
                            onDismiss()
                        })
                    } else {
                        GoldTextButton(label = L.done, onClick = onDismiss)
                    }
                }

                "IMPORT" -> when (importStep) {
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
                        label = L.migrateRoleImport,
                        enabled = importPassword.isNotBlank() && !importLocked && manager.isSessionUnlocked(),
                        onClick = {
                            val uri = importedUri ?: return@GoldTextButton
                            importStep = 2
                            scope.launch(Dispatchers.IO) {
                                val result = runCatching {
                                    manager.importBackup(uri, importPassword.toCharArray())
                                }
                                withContext(Dispatchers.Main) {
                                    result.fold(
                                        onSuccess = { r ->
                                            importResult = L.bkImportDoneFmt.format(
                                                r.importedCapsules,
                                                r.skippedCapsules,
                                                r.importedDestroyed,
                                            )
                                            importUnsupported = r.unsupportedLabels
                                            importStep = 3
                                        },
                                        onFailure = { throwable ->
                                            val wrongPassword = throwable is BackupException &&
                                                throwable.wrongPassword
                                            importError = throwable.message ?: L.bkImportFailed
                                            if (wrongPassword) {
                                                importFailedAttempts++
                                                if (importFailedAttempts >= 5) {
                                                    // 5 次错 → 30s 锁定（与导入弹窗同规则）
                                                    importLockUntil =
                                                        System.currentTimeMillis() + LOCK_MILLIS
                                                    importFailedAttempts = 0
                                                    importPassword = ""
                                                }
                                                importStep = 1
                                            } else {
                                                importStep = 4
                                            }
                                        },
                                    )
                                }
                            }
                        },
                    )

                    2 -> TextButton(onClick = {}, enabled = false) {
                        Text(text = L.inProgress, color = InkSecondary)
                    }

                    else -> GoldTextButton(label = L.done, onClick = onDismiss)
                }

                else -> Unit
            }
        },
        dismissButton = {
            val busy = exportStep == 1 || importStep == 2
            if (!busy) {
                TextButton(
                    onClick = {
                        val exportDone = mode == "EXPORT" && exportStep == 2 && exportedFile != null
                        val importDone = mode == "IMPORT" && importStep >= 3
                        if (mode == null || exportDone || importDone) {
                            onDismiss()
                        } else {
                            mode = null // 从分支回到角色选择
                        }
                    },
                ) {
                    Text(
                        text = if (mode == null) L.cancel else L.batchCancel,
                        color = InkSecondary,
                    )
                }
            }
        },
    )
}

/** 迁移向导角色选择行（弹窗内简易入口行，深底圆角块） */
@Composable
private fun ModeRow(label: String, description: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .background(DeepCharcoal.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = TimartType.body,
            color = TimeGold,
        )
        Text(
            text = description,
            style = TimartType.caption,
            color = InkSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
