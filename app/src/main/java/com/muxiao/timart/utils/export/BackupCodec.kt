package com.muxiao.timart.utils.export

import com.muxiao.timart.data.local.crypto.KdfParams
import com.muxiao.timart.data.local.db.mapper.UnlockRuleDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 备份 zip 文档模型（ARCHITECTURE §6，格式 v3：备份口令独立于主口令）：
 *
 * ```
 * manifest.json   元信息（格式版本 / 应用版本 / 导出时间 / 数量统计）
 * meta.json       kdfParams + verifier（**备份口令**的派生与校验材料，不含任何秘密；
 *                 v2 及更早为主口令材料，导入侧按包内材料统一验证，无需区分）
 * data.enc        AES-GCM(nonce‖ct‖tag)：Payload{settings, capsules, destroyed} 整体加密
 *                 （备份口令经 kdfParams 派生内容密钥；标题/正文/标签/设置无明文）
 * images/{id}/img_N.bin  图片密文（v3：已按备份口令重加密；旧包为主口令密文，导入侧统一重加密）
 * ```
 *
 * v2 兼容（导入侧）：包内材料为主口令 KDF 参数 + Verifier，导入输导出时的主口令；
 * v3 起 **主口令材料不再入包**（缩小主口令校验暴露面——离线爆破只能命中备份口令）。
 * v1 兼容（导入侧）：v1 的 meta.json 携带 settings、明文 capsules.json / destroyed.json，
 * 导入时按条目存在性自动走旧路径。
 */
object BackupCodec {

    const val FORMAT_VERSION = 3

    const val ENTRY_MANIFEST = "manifest.json"
    const val ENTRY_META = "meta.json"
    const val ENTRY_DATA = "data.enc"

    /** v1 明文段（仅导入旧备份时出现） */
    const val ENTRY_CAPSULES = "capsules.json"
    const val ENTRY_DESTROYED = "destroyed.json"
    const val IMAGE_PREFIX = "images/"

    /** 加密语音条目前缀（体验储备池 §1 声音留言；audios/{id}/audio_0.bin，v2 包可选出现） */
    const val AUDIO_PREFIX = "audios/"

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
        /** 包类型标记：null = 整库备份；"gift" = 单胶囊赠予（导入侧仅作展示区分，格式同 v2） */
        val kind: String? = null,
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
        /** 盲盒封存标志（v2 载荷向后兼容：旧包无此字段按 false 解析） */
        val blindBox: Boolean = false,
        /** 口令分片材料（体验储备池 §7.1；五者同非 null = 分片胶囊。分片份额永不入包/入设备） */
        val shardSalt: String? = null,
        val shardParams: String? = null,
        val shardVerifier: String? = null,
        val shardThreshold: Int? = null,
        val shardTotal: Int? = null,
    )

    // ---- 段四：destroyed ----

    @Serializable
    data class DestroyedRecord(
        val id: String,
        val title: String,
        val createdAt: Long,
        val destroyedAt: Long,
    )
}
