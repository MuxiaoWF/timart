package com.muxiao.timart.domain.repository

import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import kotlinx.coroutines.flow.Flow

/** 胶囊仓库接口（T4/T5/T6 全部面向本接口编程） */
interface CapsuleRepository {

    /** 全量胶囊 Flow（按创建时间升序），首页时轨与星库共用 */
    fun observeAll(): Flow<List<Capsule>>

    /** 单颗胶囊 Flow（详情页） */
    fun observeById(id: String): Flow<Capsule?>

    /** 单颗胶囊同步查询（依赖状态检查等同步路径） */
    fun byIdSync(id: String): Capsule?

    /** 全量胶囊同步查询（修改口令时的全库重加密） */
    suspend fun allSync(): List<Capsule>

    /** 全部 LOCKED 胶囊同步查询（判定入口） */
    suspend fun allLockedSync(): List<Capsule>

    suspend fun insert(capsule: Capsule)

    suspend fun update(capsule: Capsule)

    /**
     * 更新状态；UNLOCKED 回填解锁时间、DESTROYED 回填销毁时间（epoch millis）。
     */
    suspend fun updateState(id: String, state: CapsuleState, atMillis: Long?)

    /** 用户自定义星图坐标（不改变时间数据，仅视觉） */
    suspend fun updateLayout(id: String, layoutX: Float?, layoutY: Float?)

    /** 切换「阅读后自动销毁」（看后销毁 ↔ 保留 的持久化开关） */
    suspend fun updateAutoDestroyAfterRead(id: String, value: Boolean)

    /**
     * 「后悔药」：修改解锁规则（条件列表 + AND/OR 逻辑）。
     * 仅应在本机 LOCKED 态调用；每胶囊一次的使用标记由调用方另存 meta。
     */
    suspend fun updateUnlockRule(id: String, rule: com.muxiao.timart.domain.model.unlock.UnlockRule)

    suspend fun delete(id: String)
}
