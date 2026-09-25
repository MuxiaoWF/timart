package com.muxiao.timart.utils.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import com.muxiao.timart.data.local.crypto.ContentCryptoManager
import com.muxiao.timart.data.local.db.MetaDao
import com.muxiao.timart.data.local.db.entity.MetaEntity
import androidx.core.net.toUri

/**
 * 自动定期本地备份（本地优先红线内：密文包写入**用户指定的 SAF 目录**，零网络）：
 *
 * - 触发模型：MainActivity ON_RESUME 调 [maybeRun]——启用且已选目录、口令会话已解锁
 *   （fail-closed：未解锁不弹口令、不打断打开流程，仅跳过）、已设置备份口令、
 *   距上次备份达到周期才真正执行；
 * - 备份体 = [BackupManager.exportBackup]（格式 v3：全部内容重加密到**独立备份口令**，
 *   主口令材料不入包；口令 = 设置页「备份口令」存档，复用导出主链路，不另写打包逻辑）；
 * - 落盘：zip 先在 `filesDir/backup` 生成（FileProvider 已含该路径），再原样拷贝到用户
 *   所选目录（ACTION_OPEN_TREE 授予的持久化授权），本地临时件即删；
 * - 轮转：目标目录中 `timart-backup-*.zip` 按（文件名内时间戳）保留最近 [KEEP_COPIES] 份。
 *
 * meta 键（契约见 `docs/spec-storage.md §3.2`）：
 * `settings.autoBackup.enabled / .periodDays / .treeUri`、`settings.backupPw`（备份口令，
 * 见 BackupManager）、`app.autobackup.lastAt`、`app.healthcheck.lastAt / .findings`（随备份体检摘要）。
 */
class AutoBackupManager(
    private val context: Context,
    private val backupManager: BackupManager,
    private val crypto: ContentCryptoManager,
    private val metaDao: MetaDao,
    /** 备份成功后顺带执行的整库体检扫描（N22；AppContainer 注入，复用 LibraryHealthCheck 纯逻辑） */
    private val healthScan: (suspend () -> List<com.muxiao.timart.domain.usecase.LibraryHealthCheck.Finding>)? = null,
) {

    // ---- 配置读写 ----

    data class Config(
        val enabled: Boolean,
        val periodDays: Int,
        val treeUri: String?,
        val lastAt: Long?,
    )

    suspend fun readConfig(): Config = Config(
        enabled = metaDao.get(KEY_ENABLED) == "true",
        periodDays = metaDao.get(KEY_PERIOD_DAYS)?.toIntOrNull()
            ?.takeIf { it in MIN_PERIOD_DAYS..MAX_PERIOD_DAYS }
            ?: DEFAULT_PERIOD_DAYS,
        treeUri = metaDao.get(KEY_TREE_URI),
        lastAt = metaDao.get(KEY_LAST_AT)?.toLongOrNull(),
    )

    suspend fun setEnabled(value: Boolean) {
        metaDao.put(MetaEntity(KEY_ENABLED, value.toString()))
    }

    suspend fun setPeriodDays(days: Int) {
        metaDao.put(MetaEntity(KEY_PERIOD_DAYS, days.coerceIn(MIN_PERIOD_DAYS, MAX_PERIOD_DAYS).toString()))
    }

    /** 记录用户所选目录并取得持久化授权（选择器回调线程调用） */
    fun setTreeUri(uri: Uri): Boolean = runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        metaDao.putSync(MetaEntity(KEY_TREE_URI, uri.toString()))
        true
    }.getOrDefault(false)

    // ---- 执行 ----

    /** 备份口令读取（BackupManager 同源；未设置 / 长度不足返回 null） */
    private fun backupPasswordOrNull(): CharArray? =
        runCatching { metaDao.getSync(BackupManager.KEY_BACKUP_PW) }.getOrNull()
            ?.takeIf { it.length >= ContentCryptoManager.MIN_PASSWORD_LENGTH }
            ?.toCharArray()

    /** 执行结果（UI 据此映射文案；自动路径的失败不打断应用打开） */
    enum class Status {
        /** 已完成一次备份 */
        RAN,

        /** 未启用 / 未选目录 / 未到周期，静默跳过 */
        SKIPPED,

        /** 口令会话未解锁（fail-closed：不弹口令，等下次解锁后打开再备） */
        SKIPPED_LOCKED,

        /** 备份口令未设置（v3 导出链路必备；设置页引导先设置备份口令） */
        SKIPPED_NO_PW,

        /** 执行失败（目录不可写 / 打包异常等） */
        FAILED,
    }

    /**
     * 打开应用时的周期检查入口：未启用/未到周期/会话锁定/备份口令未设置均静默跳过。
     * @return 是否真正完成了一次备份
     */
    suspend fun maybeRun(): Status {
        val config = readConfig()
        if (!config.enabled || config.treeUri == null) return Status.SKIPPED
        if (!crypto.isUnlocked) return Status.SKIPPED_LOCKED
        if (backupPasswordOrNull() == null) return Status.SKIPPED_NO_PW
        val last = config.lastAt ?: 0L
        if (System.currentTimeMillis() - last < config.periodDays * DAY_MS) return Status.SKIPPED
        return runNow()
    }

    /** 设置页「立即备份一次」手动触发（同样要求会话已解锁与备份口令已设置） */
    suspend fun runNow(): Status {
        val config = readConfig()
        val treeUri = config.treeUri?.let { runCatching { it.toUri() }.getOrNull() }
            ?: return Status.FAILED
        if (!crypto.isUnlocked) return Status.SKIPPED_LOCKED
        val backupPassword = backupPasswordOrNull() ?: return Status.SKIPPED_NO_PW
        val export = runCatching { backupManager.exportBackup(backupPassword) }.getOrElse { return Status.FAILED }
        backupPassword.fill('\u0000')
        val copied = runCatching { copyToTree(treeUri, export.file) }.getOrDefault(false)
        // 本地临时件无论拷贝成败都清理（filesDir/backup 不作为自动备份的留存位）
        runCatching { export.file.delete() }
        if (!copied) return Status.FAILED
        runCatching {
            metaDao.put(MetaEntity(KEY_LAST_AT, System.currentTimeMillis().toString()))
        }
        runCatching { rotate(treeUri) }
        // 随备份体检（N22）：自动与手动路径共此入口；扫描/落摘要任何异常都不影响备份结果
        healthScan?.let { scan ->
            runCatching {
                val findings = scan.invoke()
                metaDao.put(MetaEntity(KEY_HEALTH_LAST_AT, System.currentTimeMillis().toString()))
                metaDao.put(MetaEntity(KEY_HEALTH_FINDINGS, findings.size.toString()))
            }
        }
        return Status.RAN
    }

    // ---- SAF 目录操作（直接走 DocumentsContract，不新增 documentfile 依赖） ----

    /** 历史备份条目（N4：轮转目录中现存的一份备份包；timestamp 从文件名解析，解析失败为 null） */
    data class HistoryCopy(
        val uri: Uri,
        val name: String,
        val timestamp: Long?,
        val sizeBytes: Long?,
    )

    /**
     * 列出目标目录中现存的自动备份包（`timart-backup-*.zip`，按时间倒序，最多 [KEEP_COPIES] 份）。
     * 目录未配置 / 查询失败返回空表（fail-closed：UI 显示「暂无历史」而非报错）。
     */
    suspend fun listHistory(): List<HistoryCopy> {
        val treeUri = readConfig().treeUri?.let { runCatching { it.toUri() }.getOrNull() }
            ?: return emptyList()
        return runCatching {
            val resolver = context.contentResolver
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
            )
            val copies = mutableListOf<HistoryCopy>()
            resolver.query(childrenUri(treeUri), projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1) ?: continue
                    if (!name.startsWith(BACKUP_NAME_PREFIX) || !name.endsWith(".zip")) continue
                    val docId = cursor.getString(0) ?: continue
                    copies.add(
                        HistoryCopy(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                            name = name,
                            timestamp = parseStamp(name),
                            sizeBytes = if (cursor.isNull(2)) null else cursor.getLong(2),
                        ),
                    )
                }
            }
            copies.sortByDescending { it.timestamp ?: 0L }
            copies.take(KEEP_COPIES)
        }.getOrDefault(emptyList())
    }

    /** `timart-backup-yyyyMMdd-HHmmss.zip` → epoch ms（与 BackupManager 导出命名一致；解析失败 null） */
    private fun parseStamp(name: String): Long? = runCatching {
        val stamp = name.removePrefix(BACKUP_NAME_PREFIX).removeSuffix(".zip")
        java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).parse(stamp)?.time
    }.getOrNull()

    private fun treeDirUri(treeUri: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))

    private fun childrenUri(treeUri: Uri): Uri =
        DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))

    private fun copyToTree(treeUri: Uri, file: File): Boolean = runCatching {
        val resolver = context.contentResolver
        val docUri = DocumentsContract.createDocument(
            resolver,
            treeDirUri(treeUri),
            "application/zip",
            file.name,
        ) ?: return@runCatching false
        resolver.openOutputStream(docUri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        } ?: return@runCatching false
        true
    }.getOrDefault(false)

    /** 轮转：目标目录 `timart-backup-*.zip` 只保留最近 [KEEP_COPIES] 份（文件名含时间戳，名称序 = 时间序） */
    private fun rotate(treeUri: Uri) {
        runCatching {
            val resolver = context.contentResolver
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            )
            val backups = mutableListOf<Pair<Uri, String>>()
            resolver.query(childrenUri(treeUri), projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1) ?: continue
                    if (!name.startsWith(BACKUP_NAME_PREFIX) || !name.endsWith(".zip")) continue
                    val docId = cursor.getString(0) ?: continue
                    backups.add(
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, docId) to name,
                    )
                }
            }
            backups.sortByDescending { it.second }
            backups.drop(KEEP_COPIES).forEach { (uri, _) ->
                runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            }
        }
    }

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000

        /** SAF 目录中自动备份包的文件名前缀（与 BackupManager 导出文件名一致） */
        private const val BACKUP_NAME_PREFIX = "timart-backup-"

        /** 目标目录保留份数（轮转） */
        private const val KEEP_COPIES = 3

        /** 周期取值边界与默认值（设置页单选：7 / 14 / 30 天） */
        const val MIN_PERIOD_DAYS = 7
        const val MAX_PERIOD_DAYS = 30
        const val DEFAULT_PERIOD_DAYS = 7

        // meta 键（契约见 docs/spec-storage.md §3.2；写入点本类 + SettingsViewModel）
        const val KEY_ENABLED = "settings.autoBackup.enabled"
        const val KEY_PERIOD_DAYS = "settings.autoBackup.periodDays"
        const val KEY_TREE_URI = "settings.autoBackup.treeUri"
        const val KEY_LAST_AT = "app.autobackup.lastAt"

        // 随备份体检摘要（N22；写入点仅本类，读取点 SettingsViewModel）
        const val KEY_HEALTH_LAST_AT = "app.healthcheck.lastAt"
        const val KEY_HEALTH_FINDINGS = "app.healthcheck.findings"
    }
}
