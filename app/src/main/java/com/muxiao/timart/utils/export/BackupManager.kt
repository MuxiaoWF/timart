package com.muxiao.timart.utils.export

import com.muxiao.timart.l10n.currentStrings

import android.content.Context
import android.net.Uri
import com.muxiao.timart.data.local.crypto.AesGcmCipher
import com.muxiao.timart.data.local.crypto.ContentCryptoManager
import com.muxiao.timart.data.local.crypto.CryptoException
import com.muxiao.timart.data.local.crypto.ImageCipherStore
import com.muxiao.timart.data.local.crypto.KdfEngines
import com.muxiao.timart.data.local.db.MetaDao
import com.muxiao.timart.data.local.db.mapper.UnlockRuleDto
import com.muxiao.timart.data.local.db.mapper.UnlockRuleJson
import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.domain.model.DestroyRecord
import com.muxiao.timart.domain.model.WeatherSnapshot
import com.muxiao.timart.domain.model.WeatherType
import com.muxiao.timart.domain.repository.CapsuleRepository
import com.muxiao.timart.domain.repository.DestroyedRepository
import com.muxiao.timart.utils.RuntimeSettings
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.OffsetDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer

/** 备份/导入业务失败（message 直接面向用户展示；[wrongPassword] 供对话框计数锁定） */
class BackupException(
    message: String,
    val wrongPassword: Boolean = false,
) : Exception(message)

/**
 * 备份导出与导入（ARCHITECTURE §6 / 格式 v2：数据段整体加密）：
 *
 * - 导出：`data.enc` = AES-GCM（口令经包内 KDF 参数派生的内容密钥）加密的
 *   Payload{settings, capsules, destroyed}——标题/正文/标签/设置不再有任何明文；
 *   图片本就是密文原样拷贝；口令密钥来源 = 会话已解锁用会话密钥（免输），
 *   否则由调用方传入口令本地派生并经 Verifier 校验；
 * - 导入（追加语义）：包内 KDF 参数派生 → Verifier 校验（口令错 →
 *   [BackupException.wrongPassword]；连续 5 错 30s 锁的计数在对话框内存态）→
 *   解密 data.enc（v2）或读取明文段（v1 兼容）→ 逐胶囊解密校验 → id 冲突跳过插入；
 * - 全程不需要 AndroidKeyStore，跨设备可恢复（密文自包含）。
 */
class BackupManager(
    private val context: Context,
    private val cipher: AesGcmCipher,
    private val crypto: ContentCryptoManager,
    private val metaDao: MetaDao,
    private val capsuleRepository: CapsuleRepository,
    private val imageStore: ImageCipherStore,
    private val destroyedRepository: DestroyedRepository,
) {

    /** 会话是否已解锁（导出弹窗据此决定是否要口令输入步） */
    fun isSessionUnlocked(): Boolean = crypto.isUnlocked

    // ================= 导出 =================

    /** 导出结果：文件 + 规则中本机硬件无法达成的条件名（对话框据此提示） */
    data class ExportResult(
        val file: File,
        val unsupportedLabels: List<String>,
    )

    /**
     * 导出备份 zip（IO 派发，数据段整体加密）。
     * @param password 会话未解锁时的口令（本地派生并校验，不改变会话状态）；会话已解锁传 null 用会话密钥
     * @throws BackupException 未设置口令 / 口令错误 / 材料缺失 / 打包失败
     */
    suspend fun exportBackup(password: CharArray? = null): ExportResult = withContext(Dispatchers.IO) {
        val capsules = capsuleRepository.allSync()
        val destroyed = destroyedRepository.observeAll().first()

        // 设备能力提示：仅扫 LOCKED 态（已解锁/已销毁的条件不再有意义）；
        // 备份内含本机硬件永远无法达成的条件时，完成页明示
        val unsupportedLabels = com.muxiao.timart.utils.device.unsupportedConditionNames(
            context,
            capsules.filter { it.state == CapsuleState.LOCKED }.flatMap { it.unlockRule.conditionList },
        )

        val kdfParamsRaw = metaDao.get(ContentCryptoManager.KEY_KDF_PARAMS)
            ?: throw BackupException(currentStrings().bkErrNoPw)
        val verifier = metaDao.get(ContentCryptoManager.KEY_VERIFIER)
            ?: throw BackupException(currentStrings().bkErrNoVerifier)
        val kdfParams = runCatching { KdfEngines.paramsFromJson(kdfParamsRaw) }
            .getOrElse { throw BackupException(currentStrings().bkErrPwParams) }

        // 加密密钥：会话密钥优先；否则本地派生 + Verifier 校验（不改会话状态）
        val key: ByteArray = if (password != null) {
            val derived = try {
                KdfEngines.byAlgo(kdfParams.algo).derive(password, kdfParams)
            } catch (t: Throwable) {
                throw BackupException(currentStrings().bkErrKdfFmt.format(t.message ?: currentStrings().bkErrKdfUnsupported))
            }
            val ok = try {
                cipher.decrypt(derived, KdfEngines.b64Decode(verifier)).decodeToString() ==
                    ContentCryptoManager.VERIFIER_PLAINTEXT
            } catch (_: CryptoException) {
                false
            }
            if (!ok) throw BackupException(currentStrings().bkErrWrongPw, wrongPassword = true)
            derived
        } else {
            crypto.sessionKeyOrNull()
                ?: throw BackupException(currentStrings().bkErrSessionLocked)
        }

        val settings = BackupCodec.Settings(
            tier = metaDao.get(RuntimeSettings.KEY_TIER),
            gyro = metaDao.get(RuntimeSettings.KEY_GYRO)?.toBooleanStrictOrNull(),
            inputSpark = metaDao.get(RuntimeSettings.KEY_INPUT_SPARK)?.toBooleanStrictOrNull(),
            sound = metaDao.get(RuntimeSettings.KEY_SOUND)?.toBooleanStrictOrNull(),
        )

        val appVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"

        val capsulesDto = capsules.map { it.toDto() }
        val destroyedDto = destroyed.map {
            BackupCodec.DestroyedRecord(
                id = it.id,
                title = it.title,
                createdAt = it.createdAt,
                destroyedAt = it.destroyedAt,
            )
        }
        val manifest = BackupCodec.Manifest(
            appVersion = appVersion,
            exportedAt = OffsetDateTime.now().toString(),
            capsuleCount = capsulesDto.size,
            destroyedCount = destroyedDto.size,
        )
        val payload = BackupCodec.Payload(
            settings = settings,
            capsules = capsulesDto,
            destroyed = destroyedDto,
        )
        val payloadBytes = BackupCodec.json
            .encodeToString(BackupCodec.Payload.serializer(), payload)
            .toByteArray()

        val dir = File(context.filesDir, "backup").apply { mkdirs() }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val outFile = File(dir, "timart-backup-$stamp.zip")

        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zip ->
                zip.putEntry(
                    BackupCodec.ENTRY_MANIFEST,
                    BackupCodec.json.encodeToString(BackupCodec.Manifest.serializer(), manifest).toByteArray(),
                )
                zip.putEntry(
                    BackupCodec.ENTRY_META,
                    BackupCodec.json.encodeToString(
                        BackupCodec.Meta.serializer(),
                        BackupCodec.Meta(kdfParams = kdfParams, verifier = verifier),
                    ).toByteArray(),
                )
                zip.putEntry(BackupCodec.ENTRY_DATA, cipher.encrypt(key, payloadBytes))

                // 加密图片原样拷贝（不改名不重加密）
                for (dto in capsulesDto) {
                    for (name in dto.imageFiles) {
                        val index = IMAGE_NAME.matchEntire(name)?.groupValues?.getOrNull(1)?.toIntOrNull()
                            ?: continue
                        val blob = imageStore.readEncrypted(dto.id, index) ?: continue
                        zip.putEntry("${BackupCodec.IMAGE_PREFIX}${dto.id}/$name", blob)
                    }
                }
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            outFile.delete()
            throw BackupException(currentStrings().bkErrZipFmt.format(e.message ?: currentStrings().bkErrIo))
        }
        ExportResult(file = outFile, unsupportedLabels = unsupportedLabels)
    }

    // ================= 导入 =================

    data class ImportResult(
        val importedCapsules: Int,
        val skippedCapsules: Int,
        val importedDestroyed: Int,

        /** 本次新导入胶囊规则中，本机硬件无法达成的条件名（对话框据此提示） */
        val unsupportedLabels: List<String> = emptyList(),
    )

    /**
     * 从 Uri 导入备份（追加语义；v2 数据段整体加密，自动兼容 v1 明文段）。
     * @throws BackupException 结构无效 / 版本过新 / 口令错（wrongPassword=true） / 密文损坏
     */
    suspend fun importBackup(source: Uri, password: CharArray): ImportResult = withContext(Dispatchers.IO) {
        val entries = readZipEntries(source)

        val manifest = runCatching {
            BackupCodec.json.decodeFromString(
                BackupCodec.Manifest.serializer(),
                entries.getValue(BackupCodec.ENTRY_MANIFEST).decodeToString(),
            )
        }.getOrElse { throw BackupException(currentStrings().bkErrNotTimart) }
        if (manifest.formatVersion > BackupCodec.FORMAT_VERSION) {
            throw BackupException(currentStrings().bkErrNewerVersion)
        }

        val meta = runCatching {
            BackupCodec.json.decodeFromString(
                BackupCodec.Meta.serializer(),
                entries.getValue(BackupCodec.ENTRY_META).decodeToString(),
            )
        }.getOrElse { throw BackupException(currentStrings().bkErrMaterialParse) }

        // 口令校验：包内 KDF 参数派生 → 解密 Verifier 对比固定明文
        val key = try {
            KdfEngines.byAlgo(meta.kdfParams.algo).derive(password, meta.kdfParams)
        } catch (t: Throwable) {
            throw BackupException(currentStrings().bkErrKdfFmt.format(t.message ?: currentStrings().bkErrKdfUnsupported))
        }
        val verifierPlain = try {
            cipher.decrypt(key, KdfEngines.b64Decode(meta.verifier))
        } catch (_: CryptoException) {
            throw BackupException(currentStrings().bkErrWrongPw, wrongPassword = true)
        }
        if (verifierPlain.decodeToString() != ContentCryptoManager.VERIFIER_PLAINTEXT) {
            throw BackupException(currentStrings().bkErrWrongPw, wrongPassword = true)
        }

        // 数据段：v2 = 解密 data.enc；v1 = 明文 capsules/destroyed 段（兼容导入）
        val settings: BackupCodec.Settings?
        val capsulesDto: List<BackupCodec.Capsule>
        val destroyedDto: List<BackupCodec.DestroyedRecord>
        val dataEnc = entries[BackupCodec.ENTRY_DATA]
        if (dataEnc != null) {
            val plain = try {
                cipher.decrypt(key, dataEnc)
            } catch (_: CryptoException) {
                throw BackupException(currentStrings().bkErrWrongPwOrCorrupt, wrongPassword = true)
            }
            val payload = runCatching {
                BackupCodec.json.decodeFromString(BackupCodec.Payload.serializer(), plain.decodeToString())
            }.getOrElse { throw BackupException(currentStrings().bkErrDtoParse) }
            settings = payload.settings
            capsulesDto = payload.capsules
            destroyedDto = payload.destroyed
        } else {
            val legacyCapsules = entries[BackupCodec.ENTRY_CAPSULES]
                ?: throw BackupException(currentStrings().bkErrNotTimart)
            capsulesDto = runCatching {
                BackupCodec.json.decodeFromString(
                    ListSerializer(BackupCodec.Capsule.serializer()),
                    legacyCapsules.decodeToString(),
                )
            }.getOrElse { throw BackupException(currentStrings().bkErrCapsuleParse) }
            destroyedDto = entries[BackupCodec.ENTRY_DESTROYED]?.let { raw ->
                runCatching {
                    BackupCodec.json.decodeFromString(
                        ListSerializer(BackupCodec.DestroyedRecord.serializer()),
                        raw.decodeToString(),
                    )
                }.getOrElse { emptyList() }
            } ?: emptyList()
            settings = meta.settings
        }

        // 逐胶囊解密校验（含内容密文的才校验；失败视为数据损坏，中止导入）
        for (dto in capsulesDto) {
            val blob = dto.contentCipher?.let { KdfEngines.b64Decode(it) } ?: continue
            try {
                cipher.decrypt(key, blob)
            } catch (_: CryptoException) {
                throw BackupException(currentStrings().bkErrContentVerifyFmt.format(dto.title))
            }
        }

        val imageEntries = entries.filterKeys { it.startsWith(BackupCodec.IMAGE_PREFIX) }

        var imported = 0
        var skipped = 0
        val importedConditions = ArrayList<com.muxiao.timart.domain.model.unlock.UnlockCondition>()
        for (dto in capsulesDto) {
            if (capsuleRepository.byIdSync(dto.id) != null) {
                // 追加语义：id 冲突跳过
                skipped++
                continue
            }
            val domain = dto.toDomain()
            capsuleRepository.insert(domain)
            // 设备能力提示仅针对 LOCKED 态（已解锁/已销毁的条件不再有意义）
            if (domain.state == CapsuleState.LOCKED) {
                importedConditions.addAll(domain.unlockRule.conditionList)
            }

            // 该胶囊的图片 blob 原样落盘
            for ((entryName, blob) in imageEntries) {
                val relative = entryName.removePrefix(BackupCodec.IMAGE_PREFIX)
                val ownerId = relative.substringBefore('/')
                val fileName = relative.substringAfter('/')
                if (ownerId != dto.id) continue
                val index = IMAGE_NAME.matchEntire(fileName)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: continue
                imageStore.writeEncrypted(dto.id, index, blob)
            }
            imported++
        }

        var destroyedImported = 0
        for (record in destroyedDto) {
            destroyedRepository.record(
                DestroyRecord(
                    id = record.id,
                    title = record.title,
                    createdAt = record.createdAt,
                    destroyedAt = record.destroyedAt,
                ),
            )
            destroyedImported++
        }

        ImportResult(
            importedCapsules = imported,
            skippedCapsules = skipped,
            importedDestroyed = destroyedImported,
            unsupportedLabels = com.muxiao.timart.utils.device.unsupportedConditionNames(context, importedConditions),
        )
    }

    // ================= 内部工具 =================

    /** zip 全条目读入内存（备份体量小，简单可靠） */
    private fun readZipEntries(source: Uri): Map<String, ByteArray> {
        val stream = context.contentResolver.openInputStream(source)
            ?: throw BackupException(currentStrings().bkErrReadFile)
        val result = LinkedHashMap<String, ByteArray>()
        try {
            ZipInputStream(stream.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) {
                        result[entry.name] = zip.readBytes()
                    }
                    zip.closeEntry()
                }
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException(currentStrings().bkErrReadFailFmt.format(e.message ?: currentStrings().bkErrFormat))
        }
        return result
    }

    private fun ZipOutputStream.putEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun Capsule.toDto(): BackupCodec.Capsule = BackupCodec.Capsule(
        id = id,
        title = title,
        contentCipher = contentCipher?.let { KdfEngines.b64Encode(it) },
        imageFiles = imageFiles,
        createTimestamp = createTimestamp,
        unlockTimestamp = unlockTimestamp,
        destroyTimestamp = null, // 领域模型不携带销毁时间；归尘时间在 destroyed.json
        weather = weather?.let {
            BackupCodec.Weather(
                cityId = it.cityId,
                cityName = it.cityName,
                weatherType = it.weatherType.name,
                tempC = it.tempC,
                capturedAt = it.capturedAt,
            )
        },
        unlockRule = BackupCodec.json.decodeFromString(
            UnlockRuleDto.serializer(),
            UnlockRuleJson.toJson(unlockRule),
        ),
        state = state.name,
        autoDestroyAfterRead = autoDestroyAfterRead,
        dependCapsuleId = dependCapsuleId,
        tags = tags,
        createNote = createNote,
        layoutX = layoutX,
        layoutY = layoutY,
    )

    private fun BackupCodec.Capsule.toDomain(): Capsule = Capsule(
        id = id,
        title = title,
        contentCipher = contentCipher?.let { KdfEngines.b64Decode(it) },
        imageFiles = imageFiles,
        createTimestamp = createTimestamp,
        unlockTimestamp = unlockTimestamp,
        weather = weather?.let {
            WeatherSnapshot(
                cityId = it.cityId,
                cityName = it.cityName,
                weatherType = runCatching { WeatherType.valueOf(it.weatherType) }
                    .getOrDefault(WeatherType.CLEAR),
                tempC = it.tempC,
                capturedAt = it.capturedAt,
            )
        },
        unlockRule = UnlockRuleJson.fromJson(
            BackupCodec.json.encodeToString(UnlockRuleDto.serializer(), unlockRule),
        ),
        state = runCatching { CapsuleState.valueOf(state) }.getOrDefault(CapsuleState.LOCKED),
        autoDestroyAfterRead = autoDestroyAfterRead,
        dependCapsuleId = dependCapsuleId,
        tags = tags,
        createNote = createNote,
        layoutX = layoutX,
        layoutY = layoutY,
    )

    companion object {
        private val IMAGE_NAME = Regex("""img_(\d+)\.bin""")
    }
}
