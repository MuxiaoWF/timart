package com.muxiao.timart.data.local.crypto

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import androidx.core.content.edit

/**
 * 会话密钥保险库（冷启动免口令窗口）：
 *
 * 会话密钥本身仍不落盘——这里存的是「经 Android Keystore 硬件密钥包裹后的密文 + 过期时间」。
 * Keystore 私钥不可导出，包裹密文离开本机即无效；用户在 TTL 内冷启动时
 * 由 [restoreIfValid] 解包恢复会话，超过 TTL 或用户主动锁定后必须重输口令。
 *
 * - 任何 Keystore / 解包异常都静默降级为「需重新输口令」（fail closed，不放宽安全边界）；
 * - TTL 内的会话恢复仅恢复内存密钥，Verifier 规则不变（口令本体永不存储）。
 */
class SessionKeyVault(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** 包裹存储会话密钥（失败返回 false，调用方忽略即可） */
    fun store(key: ByteArray, expireAtMillis: Long): Boolean = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, obtainKey())
        val sealed = cipher.doFinal(key)
        prefs.edit {
            putString(KEY_SEALED, Base64.getEncoder().encodeToString(sealed))
                .putString(KEY_IV, Base64.getEncoder().encodeToString(cipher.iv))
                .putLong(KEY_EXPIRE_AT, expireAtMillis)
        }
        true
    }.getOrDefault(false)

    /** TTL 内解包恢复会话密钥；过期 / 损坏 / Keystore 异常返回 null */
    fun restoreIfValid(nowMillis: Long): ByteArray? = runCatching {
        if (nowMillis >= prefs.getLong(KEY_EXPIRE_AT, 0L)) return@runCatching null
        val sealed = prefs.getString(KEY_SEALED, null)?.let { Base64.getDecoder().decode(it) }
            ?: return@runCatching null
        val iv = prefs.getString(KEY_IV, null)?.let { Base64.getDecoder().decode(it) }
            ?: return@runCatching null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, obtainKey(), GCMParameterSpec(128, iv))
        cipher.doFinal(sealed)
    }.getOrNull()

    /** 清除包裹密文（主动锁定 / 改口令 / 会话作废） */
    fun clear() {
        prefs.edit { clear() }
    }

    /** 取（或首次生成）Keystore 内 AES-256 密钥：不可导出，仅本机可用 */
    private fun obtainKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREF_NAME = "timart_session_vault"
        const val KEY_SEALED = "sealed_session_key"
        const val KEY_IV = "sealed_session_iv"
        const val KEY_EXPIRE_AT = "session_expire_at"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "timart.session.vault"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
