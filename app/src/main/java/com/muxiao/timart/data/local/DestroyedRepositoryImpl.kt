package com.muxiao.timart.data.local

import com.muxiao.timart.data.local.db.DestroyedDao
import com.muxiao.timart.data.local.db.entity.DestroyedEntity
import com.muxiao.timart.domain.model.DestroyRecord
import com.muxiao.timart.domain.repository.DestroyedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 销毁记录仓库实现 */
class DestroyedRepositoryImpl(private val dao: DestroyedDao) : DestroyedRepository {

    override fun observeAll(): Flow<List<DestroyRecord>> =
        dao.observeAll().map { list ->
            list.map { entity ->
                DestroyRecord(
                    id = entity.id,
                    title = entity.title,
                    createdAt = entity.createdAt,
                    destroyedAt = entity.destroyedAt,
                )
            }
        }

    override suspend fun record(record: DestroyRecord) {
        dao.insert(
            DestroyedEntity(
                id = record.id,
                title = record.title,
                createdAt = record.createdAt,
                destroyedAt = record.destroyedAt,
            ),
        )
    }

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun clear() = dao.clear()
}
