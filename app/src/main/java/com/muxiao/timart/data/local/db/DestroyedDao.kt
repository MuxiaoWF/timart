package com.muxiao.timart.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.muxiao.timart.data.local.db.entity.DestroyedEntity
import kotlinx.coroutines.flow.Flow

/** 销毁记录 DAO：尘迹档案的数据源 */
@Dao
interface DestroyedDao {

    @Query("SELECT * FROM destroyed_records ORDER BY destroyedAt DESC")
    fun observeAll(): Flow<List<DestroyedEntity>>

    /** 尘迹档案总数（DestroyCountAtLeast 条件判定通道，同步） */
    @Query("SELECT COUNT(*) FROM destroyed_records")
    fun countSync(): Int

    @Upsert
    suspend fun insert(entity: DestroyedEntity)

    @Query("DELETE FROM destroyed_records WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM destroyed_records")
    suspend fun clear()
}
