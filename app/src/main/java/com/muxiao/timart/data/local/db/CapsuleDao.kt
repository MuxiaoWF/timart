package com.muxiao.timart.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.muxiao.timart.data.local.db.entity.CapsuleEntity
import kotlinx.coroutines.flow.Flow

/**
 * 胶囊 DAO：Flow / suspend / 同步三态查询。
 * 同步版供 DependencyCheckerImpl 的 runBlocking(IO) 适配（全项目唯一允许的 runBlocking 场景）。
 */
@Dao
interface CapsuleDao {

    @Query("SELECT * FROM capsules ORDER BY createTimestamp ASC")
    fun observeAll(): Flow<List<CapsuleEntity>>

    @Query("SELECT * FROM capsules WHERE id = :id")
    fun observeById(id: String): Flow<CapsuleEntity?>

    @Query("SELECT * FROM capsules WHERE id = :id")
    suspend fun byId(id: String): CapsuleEntity?

    @Query("SELECT * FROM capsules WHERE id = :id")
    fun byIdSync(id: String): CapsuleEntity?

    @Query("SELECT * FROM capsules WHERE state = 'LOCKED' ORDER BY createTimestamp ASC")
    suspend fun allLocked(): List<CapsuleEntity>

    @Query("SELECT * FROM capsules WHERE state = 'LOCKED' ORDER BY createTimestamp ASC")
    fun allLockedSync(): List<CapsuleEntity>

    @Query("SELECT * FROM capsules ORDER BY createTimestamp ASC")
    suspend fun all(): List<CapsuleEntity>

    @Upsert
    suspend fun insert(entity: CapsuleEntity)

    @Update
    suspend fun update(entity: CapsuleEntity)

    /**
     * 更新状态并按目标状态回填对应时间戳：
     * UNLOCKED → unlockTimestamp；DESTROYED → destroyTimestamp；LOCKED → 不动时间戳。
     */
    @Query(
        "UPDATE capsules SET state = :state, " +
            "unlockTimestamp = CASE WHEN :state = 'UNLOCKED' THEN :atMillis ELSE unlockTimestamp END, " +
            "destroyTimestamp = CASE WHEN :state = 'DESTROYED' THEN :atMillis ELSE destroyTimestamp END " +
            "WHERE id = :id",
    )
    suspend fun updateState(id: String, state: String, atMillis: Long?)

    /** 更新状态（同步版：销毁流程物理删除密文后与时间戳写入分离时使用） */
    @Query(
        "UPDATE capsules SET state = :state, " +
            "unlockTimestamp = CASE WHEN :state = 'UNLOCKED' THEN :atMillis ELSE unlockTimestamp END, " +
            "destroyTimestamp = CASE WHEN :state = 'DESTROYED' THEN :atMillis ELSE destroyTimestamp END " +
            "WHERE id = :id",
    )
    fun updateStateSync(id: String, state: String, atMillis: Long?)

    @Query("UPDATE capsules SET layoutX = :x, layoutY = :y WHERE id = :id")
    suspend fun updateLayout(id: String, x: Float?, y: Float?)

    /** 切换「阅读后自动销毁」（看后销毁 ↔ 保留 的持久化开关） */
    @Query("UPDATE capsules SET autoDestroyAfterRead = :value WHERE id = :id")
    suspend fun updateAutoDestroyAfterRead(id: String, value: Boolean)

    /** 「后悔药」：定向更新解锁规则 JSON（不触碰 layout/时间戳/密文等其余列） */
    @Query("UPDATE capsules SET unlockRuleJson = :ruleJson WHERE id = :id")
    suspend fun updateUnlockRule(id: String, ruleJson: String)

    @Query("DELETE FROM capsules WHERE id = :id")
    suspend fun delete(id: String)

    /** 清空并重置自增（仅供备份导入等场景按需调用） */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<CapsuleEntity>)
}
