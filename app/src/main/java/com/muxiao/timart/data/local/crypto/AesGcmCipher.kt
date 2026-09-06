package com.muxiao.timart.data.local.crypto

import com.muxiao.timart.l10n.currentStrings

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** 加解密统一异常：口令错误 / 密文篡改 / 解密失败均包装为此类，由 UI 呈现 */
class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * AES-256-GCM 加解密。
 * 密文布局：`nonce(12B) ‖ ciphertext ‖ tag(16B)`；
 * 每胶囊、每图片独立随机 nonce（SecureRandom 每次新取）。
 */
class AesGcmCipher(private val random: SecureRandom = SecureRandom()) {

    /** 加密明文为 nonce‖ct‖tag 布局 */
    fun encrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        require(key.size == KEY_LENGTH_BYTES) { "密钥长度必须为 32 字节" }
        val nonce = ByteArray(NONCE_LENGTH_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LENGTH_BITS, nonce))
        val cipherText = cipher.doFinal(plaintext) // doFinal 输出已含 16B tag
        return nonce + cipherText
    }

    /**
     * 解密 nonce‖ct‖tag 布局密文；
     * tag 校验失败（口令错误 / 密文被篡改）统一包装为 [CryptoException]。
     */
    fun decrypt(key: ByteArray, blob: ByteArray): ByteArray = try {
        require(key.size == KEY_LENGTH_BYTES) { "密钥长度必须为 32 字节" }
        require(blob.size > NONCE_LENGTH_BYTES) { "密文过短" }
        val nonce = blob.copyOfRange(0, NONCE_LENGTH_BYTES)
        val cipherText = blob.copyOfRange(NONCE_LENGTH_BYTES, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LENGTH_BITS, nonce))
        cipher.doFinal(cipherText)
    } catch (e: Exception) {
        throw CryptoException(currentStrings().errDecryptFailed, e)
    }

    companion object {
        const val KEY_LENGTH_BYTES = 32
        const val NONCE_LENGTH_BYTES = 12
        const val TAG_LENGTH_BITS = 128
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
