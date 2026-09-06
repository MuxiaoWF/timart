package com.muxiao.timart.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 元信息表 `meta`：key-value 存储。
 * 内容：KDF 参数（kdf.params）、Verifier（kdf.verifier）、是否已设口令（app.hasPassword）、
 * 全部设置项（settings.*）、上次使用城市（app.lastCityId）。
 * 会话密钥绝不落盘。
 */
@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
