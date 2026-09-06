package com.muxiao.timart.domain.model

import com.muxiao.timart.domain.model.unlock.UnlockRule

/**
 * 胶囊领域模型（字段同 PRD §2.1）。
 *
 * [contentCipher] 始终保持 AES-256-GCM 密文形态；
 * 明文只在 [com.muxiao.timart.domain.usecase.ReadCapsuleUseCase] 输出的内容对象中短暂存在。
 * [layoutX]/[layoutY] 为用户自定义星图坐标（归一化，仅视觉，不改变时间数据）。
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
            layoutY == other.layoutY
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
        return result
    }
}

/** 胶囊状态流转：LOCKED → UNLOCKED →（可选销毁）DESTROYED */
enum class CapsuleState {
    LOCKED,
    UNLOCKED,
    DESTROYED,
}
