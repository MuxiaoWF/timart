package com.muxiao.timart.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 销毁记录表 `destroyed_records`：销毁后保留的元记录（ARCHITECTURE §3.3）。
 * 主键 = 原胶囊 id；正文与图片已在销毁确认时物理删除。
 */
@Entity(tableName = "destroyed_records")
data class DestroyedEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val destroyedAt: Long,
)
