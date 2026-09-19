package com.muxiao.timart.domain.usecase

import com.muxiao.timart.domain.model.Lang
import com.muxiao.timart.data.local.crypto.ContentCryptoManager
import com.muxiao.timart.data.local.crypto.CryptoException
import com.muxiao.timart.data.local.crypto.ImageCipherStore
import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.domain.model.WeatherSnapshot

/**
 * 已解锁内容阅读：仅 UNLOCKED 可读。
 * 解密正文 + 逐张解密图片（原始字节，UI 层再经 BitmapFactory 解码为 Bitmap，
 * 以保持 domain 层零 Android 依赖）；口令未验证 / 解密失败抛 [CryptoException] 由 UI 呈现。
 */
class ReadCapsuleUseCase(
    private val crypto: ContentCryptoManager,
    private val imageStore: ImageCipherStore,
) {

    /** 阅读输出：明文只在内存短暂存在，不落盘 */
    data class CapsuleContent(
        val title: String,
        val paragraphs: List<String>,
        val images: List<ByteArray>,
        val snapshot: WeatherSnapshot?,
        val tags: List<String>,
        val note: String,
        val createdAt: Long,
        /**
         * 口令分片胶囊（体验储备池 §7.1）专用：外层已解、内层仍锁定的密文块。
         * 非 null = 需要集齐分片重构 k2 才能解出正文（paragraphs 恒为空）；
         * 调用方解出明文后自行拼装 CapsuleContent。明文/内层密文均不落盘。
         */
        val lockedInner: ByteArray? = null,
    ) {
        override fun equals(other: Any?): Boolean =
            other is CapsuleContent &&
                other.title == title &&
                other.paragraphs == paragraphs &&
                other.images.size == images.size &&
                other.images.zip(images).all { (a, b) -> a.contentEquals(b) } &&
                other.snapshot == snapshot &&
                other.tags == tags &&
                other.note == note &&
                other.createdAt == createdAt &&
                other.lockedInner.contentEquals(lockedInner)

        override fun hashCode(): Int = title.hashCode() * 31 + createdAt.hashCode()
    }

    /** 阅读胶囊内容 */
    fun read(capsule: Capsule, lang: Lang = Lang.ZH_HANS): CapsuleContent {
        if (capsule.state != CapsuleState.UNLOCKED) {
            throw IllegalStateException(readMsgs(lang).notUnlocked)
        }
        val cipher = capsule.contentCipher
            ?: throw CryptoException(readMsgs(lang).contentGone)
        val outerPlain = crypto.decryptContent(cipher)
        // 分片胶囊：外层会话密钥解出的只是内层密文——集分片界面重构 k2 后再解内层
        if (capsule.shardVerifier != null) {
            return CapsuleContent(
                title = capsule.title,
                paragraphs = emptyList(),
                images = emptyList(),
                snapshot = capsule.weather,
                tags = capsule.tags,
                note = capsule.createNote,
                createdAt = capsule.createTimestamp,
                lockedInner = outerPlain,
            )
        }
        val plainText = outerPlain.decodeToString()
        val images = capsule.imageFiles.indices.mapNotNull { index ->
            imageStore.read(capsule.id, index)
        }
        return CapsuleContent(
            title = capsule.title,
            paragraphs = splitParagraphs(plainText),
            images = images,
            snapshot = capsule.weather,
            tags = capsule.tags,
            note = capsule.createNote,
            createdAt = capsule.createTimestamp,
        )
    }

    /** 正文按空行分段（保持单换行）；无正文返回单段空文案由 UI 兜底 */
    private fun splitParagraphs(text: String): List<String> {
        val paragraphs = text.split("\n\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return paragraphs.ifEmpty { listOf("") }
    }
}


private data class ReadMsgs(val notUnlocked: String, val contentGone: String)

private fun readMsgs(lang: Lang) = when (lang) {
    Lang.ZH_HANS -> ReadMsgs("胶囊尚未解锁，无法阅读", "内容已销毁或丢失，无法解密")
    Lang.ZH_HANT -> ReadMsgs("膠囊尚未解鎖，無法閱讀", "內容已銷毀或遺失，無法解密")
    Lang.EN -> ReadMsgs("Capsule not unlocked yet; cannot read", "Content was destroyed or lost; cannot decrypt")
}
