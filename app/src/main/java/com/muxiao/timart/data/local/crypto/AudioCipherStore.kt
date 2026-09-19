package com.muxiao.timart.data.local.crypto

import com.muxiao.timart.l10n.currentStrings
import java.io.File

/**
 * 加密语音仓库（体验储备池 §1 声音留言）：`filesDir/audio/{capsuleId}/audio_{n}.bin`。
 * 加密布局与正文/图片一致（nonce‖ct‖tag）；会话密钥经 [sessionKeyProvider] 注入。
 * 不落 Room：胶囊是否带语音由目录是否在位推导（单条语音，index 固定 0），
 * 备份/赠予以 `audios/{id}/audio_0.bin` 条目随包走（见 BackupManager）。
 *
 * @param baseDir 音频根目录（外部注入 File 以保证可单测）
 */
class AudioCipherStore(
    private val baseDir: File,
    private val cipher: AesGcmCipher,
    private val sessionKeyProvider: () -> ByteArray?,
) {

    private fun dir(capsuleId: String): File = File(baseDir, capsuleId)

    private fun file(capsuleId: String, index: Int): File = File(dir(capsuleId), "audio_$index.bin")

    private fun keyOrThrow(): ByteArray =
        sessionKeyProvider() ?: throw CryptoException(currentStrings().errImageLocked)

    /** 加密并保存语音（封存提交时调用；当前只支持一条，index = 0） */
    fun save(capsuleId: String, index: Int, bytes: ByteArray) {
        val encrypted = cipher.encrypt(keyOrThrow(), bytes)
        dir(capsuleId).mkdirs()
        file(capsuleId, index).writeBytes(encrypted)
    }

    /** 读取并解密一条语音；文件不存在返回 null，密文损坏抛 [CryptoException] */
    fun read(capsuleId: String, index: Int): ByteArray? {
        val target = file(capsuleId, index)
        if (!target.exists()) return null
        return cipher.decrypt(keyOrThrow(), target.readBytes())
    }

    /** 删除胶囊的整个语音目录（销毁流程物理删除，不可逆） */
    fun deleteDir(capsuleId: String) {
        dir(capsuleId).deleteRecursively()
    }

    /** 列出某胶囊现存语音序号（文件名 audio_{n}.bin） */
    fun listIndexes(capsuleId: String): List<Int> =
        dir(capsuleId).listFiles()
            ?.mapNotNull { file ->
                INDEX_PATTERN.matchEntire(file.name)?.groupValues?.getOrNull(1)?.toIntOrNull()
            }
            ?.sorted()
            ?: emptyList()

    /** 全部现存语音目录对应的胶囊 id（修改口令全库重加密 / 备份导出扫描用） */
    fun allCapsuleIds(): List<String> =
        baseDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()

    /** 读原始密文（修改口令：旧 key 解密前的字节；备份导出原样拷贝用） */
    fun readEncrypted(capsuleId: String, index: Int): ByteArray? {
        val target = file(capsuleId, index)
        return if (target.exists()) target.readBytes() else null
    }

    /** 原样写回密文（整库导入不重加密语义用） */
    fun writeEncrypted(capsuleId: String, index: Int, blob: ByteArray) {
        dir(capsuleId).mkdirs()
        file(capsuleId, index).writeBytes(blob)
    }

    companion object {
        private val INDEX_PATTERN = Regex("""audio_(\d+)\.bin""")
    }
}
