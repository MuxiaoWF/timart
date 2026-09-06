package com.muxiao.timart.domain.model

/** 销毁后的元记录（仅保留标题与时间，不含任何内容字段，对应 PRD §2.3） */
data class DestroyRecord(
    val id: String,
    val title: String,
    val createdAt: Long,
    val destroyedAt: Long,
)
