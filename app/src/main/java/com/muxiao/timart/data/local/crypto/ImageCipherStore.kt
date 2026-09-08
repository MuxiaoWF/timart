package com.muxiao.timart.data.local.crypto

import com.muxiao.timart.l10n.currentStrings
import java.io.File

/**
 * 加密图片文件仓库：`filesDir/images/{capsuleId}/img_{n}.bin`。
 * 加密布局与正文一致（nonce‖ct‖tag）；会话密钥经 [sessionKeyProvider] 注入，保持与
 * ContentCryptoManager 的单一密钥来源。
 *
 * @param baseDir 图片根目录（外部注入 File 以保证可单测）
 */
class ImageCipherStore(
    private val baseDir: File,
    private val cipher: AesGcmCipher,
    private val sessionKeyProvider: () -> ByteArray?,
) {

    private fun dir(capsuleId: String): File = File(baseDir, capsuleId)

    private fun file(capsuleId: String, index: Int): File = File(dir(capsuleId), "img_$index.bin")

    private fun keyOrThrow(): ByteArray =
        sessionKeyProvider() ?: throw CryptoException(currentStrings().errImageLocked)

    /** 加密并保存一张图片（封存提交时调用） */
    fun save(capsuleId: String, index: Int, bytes: ByteArray) {
        val encrypted = cipher.encrypt(keyOrThrow(), bytes)
        dir(capsuleId).mkdirs()
        file(capsuleId, index).writeBytes(encrypted)
    }

    /** 读取并解密一张图片；文件不存在返回 null，密文损坏抛 [CryptoException] */
    fun read(capsuleId: String, index: Int): ByteArray? {
        val target = file(capsuleId, index)
        if (!target.exists()) return null
        return cipher.decrypt(keyOrThrow(), target.readBytes())
    }

    /** 删除胶囊的整个图片目录（销毁流程物理删除，不可逆） */
    fun deleteDir(capsuleId: String) {
        dir(capsuleId).deleteRecursively()
    }

    /** 列出某胶囊现存图片序号（文件名 img_{n}.bin） */
    fun listIndexes(capsuleId: String): List<Int> =
        dir(capsuleId).listFiles()
            ?.mapNotNull { file ->
                INDEX_PATTERN.matchEntire(file.name)?.groupValues?.getOrNull(1)?.toIntOrNull()
            }
            ?.sorted()
            ?: emptyList()

    /** 全部现存图片目录对应的胶囊 id（修改口令全库重加密用） */
    fun allCapsuleIds(): List<String> =
        baseDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()

    /** 读原始密文（修改口令：旧 key 解密前的字节） */
    fun readEncrypted(capsuleId: String, index: Int): ByteArray? {
        val target = file(capsuleId, index)
        return if (target.exists()) target.readBytes() else null
    }

    /** 原样写回密文（修改口令重加密用，不再经过会话密钥） */
    fun writeEncrypted(capsuleId: String, index: Int, blob: ByteArray) {
        dir(capsuleId).mkdirs()
        file(capsuleId, index).writeBytes(blob)
    }

    companion object {
        private val INDEX_PATTERN = Regex("""img_(\d+)\.bin""")
    }
}
