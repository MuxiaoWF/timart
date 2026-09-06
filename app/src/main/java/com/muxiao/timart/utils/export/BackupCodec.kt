package com.muxiao.timart.utils.export

import com.muxiao.timart.data.local.crypto.KdfParams
import com.muxiao.timart.data.local.db.mapper.UnlockRuleDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 备份 zip 文档模型（ARCHITECTURE §6，格式 v2：数据段整体加密）：
 *
 * ```
 * manifest.json   元信息（格式版本 / 应用版本 / 导出时间 / 数量统计）
 * meta.json       kdfParams + verifier（口令派生与校验材料，不含任何秘密）
 * data.enc        AES-GCM(nonce‖ct‖tag)：Payload{settings, capsules, destroyed} 整体加密
 *                 （口令经 kdfParams 派生内容密钥；标题/正文/标签/设置无明文）
 * images/{id}/img_N.bin  加密图片原样拷贝（不改名不重加密）
 * ```
 *
 * v1 兼容（导入侧）：v1 的 meta.json 携带 settings、明文 capsules.json / destroyed.json，
 * 导入时按条目存在性自动走旧路径。
 */
object BackupCodec {

    const val FORMAT_VERSION = 2

    const val ENTRY_MANIFEST = "manifest.json"
    const val ENTRY_META = "meta.json"
    const val ENTRY_DATA = "data.enc"

    /** v1 明文段（仅导入旧备份时出现） */
    const val ENTRY_CAPSULES = "capsules.json"
    const val ENTRY_DESTROYED = "destroyed.json"
    const val IMAGE_PREFIX = "images/"

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        isLenient = true
        prettyPrint = false
    }

    // ---- 段一：manifest ----

    @Serializable
    data class Manifest(
        val formatVersion: Int = FORMAT_VERSION,
        val appVersion: String,
        val exportedAt: String,
        val capsuleCount: Int,
        val destroyedCount: Int,
    )

    // ---- 段二：meta ----

    @Serializable
    data class Settings(
        val tier: String? = null,
        val gyro: Boolean? = null,
        val inputSpark: Boolean? = null,
        val sound: Boolean? = null,
    )

    @Serializable
    data class Meta(
        val kdfParams: KdfParams,
        val verifier: String,

        /** v1 遗留：设置快照曾存于此；v2 起移入加密 Payload（导出恒为 null，不序列化） */
        val settings: Settings? = null,
    )

    /** data.enc 解密后的载荷（v2：设置 + 胶囊 + 尘迹档案整体） */
    @Serializable
    data class Payload(
        val settings: Settings? = null,
        val capsules: List<Capsule> = emptyList(),
        val destroyed: List<DestroyedRecord> = emptyList(),
    )

    // ---- 段三：capsules ----

    @Serializable
    data class Weather(
        val cityId: String,
        val cityName: String,
        val weatherType: String,
        val tempC: Double,
        val capturedAt: Long,
    )

    @Serializable
    data class Capsule(
        val id: String,
        val title: String,
        val contentCipher: String? = null,
        val imageFiles: List<String> = emptyList(),
        val createTimestamp: Long,
        val unlockTimestamp: Long? = null,
        val destroyTimestamp: Long? = null,
        val weather: Weather? = null,
        val unlockRule: UnlockRuleDto,
        val state: String,
        val autoDestroyAfterRead: Boolean = false,
        val dependCapsuleId: String? = null,
        val tags: List<String> = emptyList(),
        val createNote: String = "",
        val layoutX: Float? = null,
        val layoutY: Float? = null,
    )

    // ---- 段四：destroyed ----

    @Serializable
    data class DestroyedRecord(
        val id: String,
        val title: String,
        val createdAt: Long,
        val destroyedAt: Long,
    )

    // ---- 编解码便捷函数 ----

    fun <T> encode(serializer: kotlinx.serialization.KSerializer<T>, value: T): String =
        json.encodeToString(serializer, value)

    fun <T> decode(serializer: kotlinx.serialization.KSerializer<T>, raw: String): T =
        json.decodeFromString(serializer, raw)
}
