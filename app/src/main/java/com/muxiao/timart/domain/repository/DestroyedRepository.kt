package com.muxiao.timart.domain.repository

import com.muxiao.timart.domain.model.DestroyRecord
import kotlinx.coroutines.flow.Flow

/** 销毁记录仓库接口（尘迹档案数据源） */
interface DestroyedRepository {

    /** 全部销毁记录 Flow（按销毁时间倒序） */
    fun observeAll(): Flow<List<DestroyRecord>>

    /** 写入一条销毁元记录 */
    suspend fun record(record: DestroyRecord)

    suspend fun delete(id: String)

    suspend fun clear()
}
