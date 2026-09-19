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

    /** 已开启（读过内容）胶囊的 meta key 列表（`capsule.read.<id>`；契约见 DetailViewModel.readMarkKey） */
    @Query("SELECT `key` FROM meta WHERE `key` LIKE 'capsule.read.%'")
    fun observeReadKeys(): Flow<List<String>>

    /** 已开启（读过内容）胶囊总数（ReadCountAtLeast 条件判定通道，同步） */
    @Query("SELECT COUNT(*) FROM meta WHERE `key` LIKE 'capsule.read.%'")
    fun readCountSync(): Int

    /**
     * 按前缀观察整行（key+value）——体验储备池增量契约的通用扫描通道：
     * `capsule.seedOf.%`（嵌套休眠过滤，HomeViewModel）、`capsule.puzzle.%`（合信视图，DetailViewModel）。
     * 前缀常量与 key 语义见 CapsuleMetaKeys（跨 VM 契约，坑 #26）。
     */
    @Query("SELECT * FROM meta WHERE `key` LIKE :prefix || '%'")
    fun observeLike(prefix: String): Flow<List<MetaEntity>>

    /** [observeLike] 的一次性读取版（拼图/种子反查等按需扫描） */
    @Query("SELECT * FROM meta WHERE `key` LIKE :prefix || '%'")
    suspend fun listLike(prefix: String): List<MetaEntity>

    /** 删除单条 meta（物理删除胶囊时清理拼图占位 / 嵌套归属等分组语义 key） */
    @Query("DELETE FROM meta WHERE `key` = :key")
    suspend fun delete(key: String)

    @Upsert
    suspend fun put(entity: MetaEntity)

    @Upsert
    fun putSync(entity: MetaEntity)
}
