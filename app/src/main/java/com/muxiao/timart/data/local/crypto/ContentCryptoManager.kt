package com.muxiao.timart.data.local.crypto

import com.muxiao.timart.l10n.currentStrings

import com.muxiao.timart.data.local.db.MetaDao
import com.muxiao.timart.data.local.db.entity.MetaEntity
import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.repository.CapsuleRepository

/**
 * 会话级加密编排器。
 *
 * - 首次设置口令（两遍输入 + 知晓确认由 UI 保证）→ 生成盐 / KDF 参数 → 派生 key →
 *   写 meta.kdf.params、meta.kdf.verifier（用 key 加密固定明文）；
 * - [unlock] 校验通过后，会话 key 仅保存在内存字段，**绝不明文落盘**：
 *   另经 [SessionKeyVault]（Android Keystore 包裹）保留一份加密副本，
 *   TTL 内冷启动经 [tryRestoreSession] 免口令恢复会话；过期或 [lock] 后必须重输口令；
 * - Verifier 是唯一口令校验通道（不存口令本身、不存哈希对比表）；
 * - [changePassword] 旧口令校验 → 新参数派生 → 全库解密重加密 + 图片文件重加密（IO 协程调用）。
 */
class ContentCryptoManager(
    private val cipher: AesGcmCipher,
    private val metaDao: MetaDao,
    private val capsuleRepository: CapsuleRepository,
    private val imageStore: ImageCipherStore,
    /** Keystore 包裹的会话保险库（null = 每次冷启动都需口令） */
    private val sessionVault: SessionKeyVault? = null,
) {

    /** 会话密钥：仅内存，进程结束即失效（TTL 内冷启动经保险库恢复） */
    @Volatile
    private var sessionKey: ByteArray? = null

    /** 口令是否已解锁（Verifier 校验通过） */
    val isUnlocked: Boolean get() = sessionKey != null

    /** 图片存储需要的会话密钥访问器（内部装配用） */
    internal fun sessionKeyOrNull(): ByteArray? = sessionKey

    /** 是否已设置口令（meta.app.hasPassword） */
    suspend fun hasPasswordSetup(): Boolean =
        metaDao.get(KEY_HAS_PASSWORD) == "true"

    /**
     * 首次设置口令：派生密钥 + 写入 KDF 参数 / Verifier / hasPassword 标记。
     * 调用前 UI 必须完成两遍输入一致性检查与「遗忘即永久丢失」确认。
     */
    suspend fun setupPassword(password: CharArray) {
        require(password.size >= MIN_PASSWORD_LENGTH) { currentStrings().errPwMinFmt.format(MIN_PASSWORD_LENGTH) }
        val derived = KdfEngines.deriveWithFallback(password, KdfEngines.defaultParams(KdfEngines.newSalt()))
        persistParamsAndVerifier(derived.params, derived.key)
        sessionKey = derived.key
        stashSession(derived.key)
    }

    /**
     * 口令解锁：按持久化参数派生并解密 Verifier 对比。
     * @return true = 校验通过且会话已解锁；false = 口令错误
     */
    suspend fun unlock(password: CharArray): Boolean {
        val paramsRaw = metaDao.get(KEY_KDF_PARAMS) ?: return false
        val verifierBlob = metaDao.get(KEY_VERIFIER)?.let { KdfEngines.b64Decode(it) } ?: return false
        val params = try {
            KdfEngines.paramsFromJson(paramsRaw)
        } catch (_: Exception) {
            return false
        }
        val key = try {
            KdfEngines.byAlgo(params.algo).derive(password, params)
        } catch (_: Exception) {
            return false
        }
        val plain = try {
            cipher.decrypt(key, verifierBlob)
        } catch (_: CryptoException) {
            return false
        }
        return if (plain.decodeToString() == VERIFIER_PLAINTEXT) {
            sessionKey = key
            stashSession(key)
            true
        } else {
            false
        }
    }

    /**
     * 冷启动会话恢复：TTL 内且保险库包裹密文可解 → 恢复内存会话密钥（免输口令）。
     * @return true = 会话已就绪（原本就解锁或恢复成功）；false = 需要口令
     */
    fun tryRestoreSession(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (sessionKey != null) return true
        val vault = sessionVault ?: return false
        val restored = vault.restoreIfValid(nowMillis) ?: return false
        sessionKey = restored
        return true
    }

    /** 会话密钥加密副本落保险库（Keystore 包裹，TTL [SESSION_TTL_MS]；失败静默降级为下次需口令） */
    private fun stashSession(key: ByteArray) {
        sessionVault?.store(key, System.currentTimeMillis() + SESSION_TTL_MS)
    }

    /** 同步便捷判断（UI 层在已 await 解锁协程后使用） */
    fun ensureUnlocked(): Boolean = sessionKey != null

    /** 用会话密钥加密正文（封存提交时调用；未解锁抛 [CryptoException]） */
    fun encryptContent(plaintext: ByteArray): ByteArray {
        val key = sessionKey ?: throw CryptoException(currentStrings().errPwNotUnlockedEnc)
        return cipher.encrypt(key, plaintext)
    }

    /** 用会话密钥解密正文（阅读时调用；失败抛 [CryptoException]） */
    fun decryptContent(blob: ByteArray): ByteArray {
        val key = sessionKey ?: throw CryptoException(currentStrings().errPwNotUnlockedDec)
        return cipher.decrypt(key, blob)
    }

    /**
     * 修改口令：旧口令校验 → 新参数派生 → 全库胶囊密文与图片文件重加密 → 持久化新参数/Verifier。
     * 必须在 IO 协程调用；[onProgress] 上报 (已完成, 总数)。
     * 新参数/Verifier 最后持久化；重加密中途异常时回滚已处理项（旧口令仍可解全库）后原样上抛。
     */
    suspend fun changePassword(
        old: CharArray,
        new: CharArray,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        require(new.size >= MIN_PASSWORD_LENGTH) { currentStrings().errNewPwMinFmt.format(MIN_PASSWORD_LENGTH) }
        if (!unlock(old)) throw CryptoException(currentStrings().errOldPwWrong)
        val oldKey = checkNotNull(sessionKey) { currentStrings().errSessionKeyLost }

        // 新参数派生（Argon2 失败自动回退 PBKDF2）
        val derived = KdfEngines.deriveWithFallback(new, KdfEngines.defaultParams(KdfEngines.newSalt()))
        val newKey = derived.key

        // 收集待重加密对象：全部含密文胶囊 + 全部图片文件
        val capsules = capsuleRepository.allSync().filter { it.contentCipher != null }
        val imageIds = imageStore.allCapsuleIds()
        val imageTotal = imageIds.sumOf { imageStore.listIndexes(it).size }
        val total = capsules.size + imageTotal
        var done = 0

        // 逐胶囊重加密（记录已处理项；中途失败回滚到旧口令可解状态——旧 Verifier 此时仍未被覆盖）
        val processedCapsules = mutableListOf<Capsule>()
        val processedImages = mutableListOf<Pair<String, Int>>()
        try {
            for (capsule in capsules) {
                val blob = checkNotNull(capsule.contentCipher)
                val plain = cipher.decrypt(oldKey, blob)
                capsuleRepository.update(capsule.copy(contentCipher = cipher.encrypt(newKey, plain)))
                processedCapsules += capsule
                onProgress(++done, total)
            }

            // 逐图片重加密（读原始密文 → 旧 key 解密 → 新 key 加密 → 原样写回）
            for (id in imageIds) {
                for (index in imageStore.listIndexes(id)) {
                    val blob = imageStore.readEncrypted(id, index) ?: continue
                    val plain = cipher.decrypt(oldKey, blob)
                    imageStore.writeEncrypted(id, index, cipher.encrypt(newKey, plain))
                    processedImages += id to index
                    onProgress(++done, total)
                }
            }
        } catch (e: Throwable) {
            // 回滚：胶囊直接恢复原实体（旧密文）；图片用旧 key 反向重加密写回。
            // 回滚自身失败已无更优解（旧 Verifier 仍在、数据半新半旧），只能如实上抛原始异常。
            runCatching {
                for (original in processedCapsules) capsuleRepository.update(original)
                for ((id, index) in processedImages) {
                    val blob = imageStore.readEncrypted(id, index) ?: continue
                    val plain = cipher.decrypt(newKey, blob)
                    imageStore.writeEncrypted(id, index, cipher.encrypt(oldKey, plain))
                }
            }
            throw e
        }

        // 持久化新参数与 Verifier，切换会话密钥并清除旧密钥
        persistParamsAndVerifier(derived.params, newKey)
        sessionKey = newKey
        oldKey.fill(0)
        stashSession(newKey)
    }

    /** 退出解锁态（设置页主动锁定等场景）：清除内存密钥与保险库包裹副本 */
    fun lock() {
        sessionVault?.clear()
        sessionKey?.fill(0)
        sessionKey = null
    }

    private suspend fun persistParamsAndVerifier(params: KdfParams, key: ByteArray) {
        metaDao.put(MetaEntity(KEY_KDF_PARAMS, KdfEngines.paramsToJson(params)))
        metaDao.put(MetaEntity(KEY_VERIFIER, KdfEngines.encodeSalt(cipher.encrypt(key, VERIFIER_PLAINTEXT.toByteArray()))))
        metaDao.put(MetaEntity(KEY_HAS_PASSWORD, "true"))
    }

    companion object {
        const val KEY_KDF_PARAMS = "kdf.params"
        const val KEY_VERIFIER = "kdf.verifier"
        const val KEY_HAS_PASSWORD = "app.hasPassword"

        /** Verifier 固定已知明文 */
        const val VERIFIER_PLAINTEXT = "TIMART-VERIFIER-V1"

        /** PRD：口令 6 位以上，可含中文 */
        const val MIN_PASSWORD_LENGTH = 6

        /** 冷启动免口令窗口：口令解锁后 72h 内的冷启动免重输（Keystore 包裹，本机有效）。
         *  2026-09 评审定稿：维持 72h 实现口径，PRD/文档已同步改为 72h */
        const val SESSION_TTL_MS = 72L * 60 * 60 * 1000
    }
}
