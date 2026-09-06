package com.muxiao.timart.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.muxiao.timart.data.local.db.entity.MetaEntity
import kotlinx.coroutines.flow.Flow

/** 元信息 DAO：KDF 参数、Verifier、设置项与上次城市的读写通道 */
@Dao
interface MetaDao {

    @Query("SELECT value FROM meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM meta WHERE `key` = :key")
    fun getSync(key: String): String?

    @Query("SELECT value FROM meta WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Upsert
    suspend fun put(entity: MetaEntity)

    @Upsert
    fun putSync(entity: MetaEntity)
}
