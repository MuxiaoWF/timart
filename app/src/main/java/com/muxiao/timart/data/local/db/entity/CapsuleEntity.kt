package com.muxiao.timart.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 胶囊表 `capsules`（字段严格按 ARCHITECTURE §3.1）。
 * 时间一律 epoch millis 存储；列表列（imageFiles/tags）存 JSON 字符串。
 */
@Entity(tableName = "capsules")
data class CapsuleEntity(
    @PrimaryKey val id: String,
    val title: String,
    /** AES-256-GCM 密文（12B nonce 前缀）；销毁时置 null */
    val contentCipher: ByteArray?,
    /** JSON 数组：["img_0.bin", ...]，文件本体在 filesDir/images/{id}/（加密） */
    val imageFiles: String,
    val createTimestamp: Long,
    val unlockTimestamp: Long?,
    /** 销毁时刻（updateState 写入 DESTROYED 时回填；销毁元记录另存 destroyed_records） */
    val destroyTimestamp: Long?,
    val weatherCityId: String?,
    val weatherCityName: String?,
    /** 应用内 WeatherType 枚举名 */
    val weatherType: String?,
    val weatherTempC: Double?,
    /** ARCHITECTURE §5 格式的解锁规则序列化 */
    val unlockRuleJson: String,
    /** LOCKED / UNLOCKED / DESTROYED */
    val state: String,
    val autoDestroyAfterRead: Boolean,
    /** 不加 FK 约束，依赖校验全部在应用层 */
    val dependCapsuleId: String?,
    /** JSON 数组（1–5 个） */
    val tags: String,
    val createNote: String,
    /** 用户自定义星图坐标（仅视觉，不改变时间数据） */
    val layoutX: Float?,
    val layoutY: Float?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CapsuleEntity) return false
        return id == other.id && contentCipher.contentEquals(other.contentCipher)
    }

    override fun hashCode(): Int = id.hashCode() * 31 + contentCipher.contentHashCode()
}
