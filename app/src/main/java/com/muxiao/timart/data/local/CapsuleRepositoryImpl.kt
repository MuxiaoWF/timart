package com.muxiao.timart.data.local

import com.muxiao.timart.data.local.db.CapsuleDao
import com.muxiao.timart.data.local.db.mapper.CapsuleMapper
import com.muxiao.timart.domain.context.DependencyChecker
import com.muxiao.timart.domain.context.DependencyStatus
import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.domain.repository.CapsuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * 胶囊仓库实现。
 *
 * 内含 [DependencyCheckerImpl]：判定上下文的依赖状态查询通道，
 * 是全项目唯一允许 runBlocking 的位置（同步接口适配 DAO 同步读，包一层 IO 派发避免占用调用线程）。
 */
class CapsuleRepositoryImpl(private val dao: CapsuleDao) : CapsuleRepository {

    override fun observeAll(): Flow<List<Capsule>> =
        dao.observeAll().map { list -> list.map(CapsuleMapper::toDomain) }

    override fun observeById(id: String): Flow<Capsule?> =
        dao.observeById(id).map { it?.let(CapsuleMapper::toDomain) }

    override fun byIdSync(id: String): Capsule? = dao.byIdSync(id)?.let(CapsuleMapper::toDomain)

    override suspend fun allSync(): List<Capsule> =
        dao.all().map(CapsuleMapper::toDomain)

    override suspend fun allLockedSync(): List<Capsule> =
        dao.allLocked().map(CapsuleMapper::toDomain)

    override suspend fun insert(capsule: Capsule) = dao.insert(CapsuleMapper.toEntity(capsule))

    /** 更新（layoutX/Y 随领域模型往返；destroyTimestamp 不在领域模型上，按库中既有值保留） */
    override suspend fun update(capsule: Capsule) {
        val existing = dao.byId(capsule.id)
        dao.update(
            CapsuleMapper.toEntity(
                capsule,
                destroyTimestamp = existing?.destroyTimestamp,
            ),
        )
    }

    override suspend fun updateState(id: String, state: CapsuleState, atMillis: Long?) =
        dao.updateState(id, state.name, atMillis)

    override suspend fun updateLayout(id: String, layoutX: Float?, layoutY: Float?) =
        dao.updateLayout(id, layoutX, layoutY)

    override suspend fun updateAutoDestroyAfterRead(id: String, value: Boolean) =
        dao.updateAutoDestroyAfterRead(id, value)

    override suspend fun updateUnlockRule(
        id: String,
        rule: com.muxiao.timart.domain.model.unlock.UnlockRule,
    ) = dao.updateUnlockRule(id, com.muxiao.timart.data.local.db.mapper.UnlockRuleJson.toJson(rule))

    override suspend fun delete(id: String) = dao.delete(id)

    /** 判定上下文的依赖状态查询实现（应用层校验，无 FK 兜底分支） */
    val dependencyChecker: DependencyChecker = DependencyCheckerImpl(dao)
}

/** 同步接口 → DAO 同步读的适配（runBlocking 仅允许出现在此处） */
internal class DependencyCheckerImpl(private val dao: CapsuleDao) : DependencyChecker {

    override fun statusOf(id: String): DependencyStatus = runBlocking(Dispatchers.IO) {
        val entity = dao.byIdSync(id)
        when {
            entity == null -> DependencyStatus.NOT_FOUND
            entity.state == CapsuleState.LOCKED.name -> DependencyStatus.LOCKED
            entity.state == CapsuleState.UNLOCKED.name -> DependencyStatus.UNLOCKED
            entity.state == CapsuleState.DESTROYED.name -> DependencyStatus.DESTROYED
            else -> DependencyStatus.NOT_FOUND
        }
    }
}
