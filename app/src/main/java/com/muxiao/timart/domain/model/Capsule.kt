package com.muxiao.timart.domain.model

import com.muxiao.timart.domain.model.unlock.UnlockRule

/**
 * 胶囊领域模型（字段同 PRD §2.1）。
 *
 * [contentCipher] 始终保持 AES-256-GCM 密文形态；
 * 明文只在 [com.muxiao.timart.domain.usecase.ReadCapsuleUseCase] 输出的内容对象中短暂存在。
 * [layoutX]/[layoutY] 为用户自定义星图坐标（归一化，仅视觉，不改变时间数据）。
 * [blindBox] 盲盒封存（seal-time 标志位）：LOCKED 期间预览面（预览卡/时轨球题）遮蔽标题与条件句，
 * 解锁前连自己也保密；解锁后恢复正常展示。
 * [shardSalt]/[shardParams]/[shardVerifier]/[shardThreshold]/[shardTotal] 口令分片（体验储备池 §7.1 落地）：
 * 五者同非 null = 分片胶囊。正文双层加密——外层会话密钥（现有链路）、内层 k2 = KDF(S, salt₂)；
 * S 由封存者拆成 [shardTotal] 份 Shamir 分片（阈值 [shardThreshold]）线下分发，设备只留
 * salt₂ / KDF 参数 / verifier₂（= SHA-256(k2)），**任何分片份额永不落设备**。全 null = 普通胶囊。
 */
data class Capsule(
    val id: String,
    val title: String,
    val contentCipher: ByteArray?,
    val imageFiles: List<String> = emptyList(),
    val createTimestamp: Long,
    val unlockTimestamp: Long? = null,
    val weather: WeatherSnapshot? = null,
    val unlockRule: UnlockRule,
    val state: CapsuleState = CapsuleState.LOCKED,
    val autoDestroyAfterRead: Boolean = false,
    val dependCapsuleId: String? = null,
    val tags: List<String> = emptyList(),
    val createNote: String = "",
    val layoutX: Float? = null,
    val layoutY: Float? = null,
    val blindBox: Boolean = false,
    val shardSalt: String? = null,
    val shardParams: String? = null,
    val shardVerifier: String? = null,
    val shardThreshold: Int? = null,
    val shardTotal: Int? = null,
) {
    /** 覆盖 ByteArray 的内容比较，保证密文变化可被正确感知 */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Capsule) return false
        return id == other.id &&
            title == other.title &&
            contentCipher.contentEquals(other.contentCipher) &&
            imageFiles == other.imageFiles &&
            createTimestamp == other.createTimestamp &&
            unlockTimestamp == other.unlockTimestamp &&
            weather == other.weather &&
            unlockRule == other.unlockRule &&
            state == other.state &&
            autoDestroyAfterRead == other.autoDestroyAfterRead &&
            dependCapsuleId == other.dependCapsuleId &&
            tags == other.tags &&
            createNote == other.createNote &&
            layoutX == other.layoutX &&
            layoutY == other.layoutY &&
            blindBox == other.blindBox &&
            shardSalt == other.shardSalt &&
            shardParams == other.shardParams &&
            shardVerifier == other.shardVerifier &&
            shardThreshold == other.shardThreshold &&
            shardTotal == other.shardTotal
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + contentCipher.contentHashCode()
        result = 31 * result + imageFiles.hashCode()
        result = 31 * result + createTimestamp.hashCode()
        result = 31 * result + (unlockTimestamp?.hashCode() ?: 0)
        result = 31 * result + (weather?.hashCode() ?: 0)
        result = 31 * result + unlockRule.hashCode()
        result = 31 * result + state.hashCode()
        result = 31 * result + autoDestroyAfterRead.hashCode()
        result = 31 * result + (dependCapsuleId?.hashCode() ?: 0)
        result = 31 * result + tags.hashCode()
        result = 31 * result + createNote.hashCode()
        result = 31 * result + (layoutX?.hashCode() ?: 0)
        result = 31 * result + (layoutY?.hashCode() ?: 0)
        result = 31 * result + blindBox.hashCode()
        result = 31 * result + (shardSalt?.hashCode() ?: 0)
        result = 31 * result + (shardParams?.hashCode() ?: 0)
        result = 31 * result + (shardVerifier?.hashCode() ?: 0)
        result = 31 * result + (shardThreshold?.hashCode() ?: 0)
        result = 31 * result + (shardTotal?.hashCode() ?: 0)
        return result
    }
}

/** 胶囊状态流转：LOCKED → UNLOCKED →（可选销毁）DESTROYED */
enum class CapsuleState {
    LOCKED,
    UNLOCKED,
    DESTROYED,
}
