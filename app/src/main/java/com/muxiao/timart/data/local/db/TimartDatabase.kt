package com.muxiao.timart.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.muxiao.timart.data.local.db.entity.CapsuleEntity
import com.muxiao.timart.data.local.db.entity.DestroyedEntity
import com.muxiao.timart.data.local.db.entity.MetaEntity

/**
 * 应用唯一 Room 数据库：capsules / meta / destroyed_records 三表，version=1，不导出 schema。
 */
@Database(
    entities = [CapsuleEntity::class, MetaEntity::class, DestroyedEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class TimartDatabase : RoomDatabase() {

    abstract fun capsuleDao(): CapsuleDao

    abstract fun metaDao(): MetaDao

    abstract fun destroyedDao(): DestroyedDao

    companion object {
        @Volatile
        private var instance: TimartDatabase? = null

        /** 进程级单例（双检锁） */
        fun get(context: Context): TimartDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TimartDatabase::class.java,
                    "timart.db",
                ).build().also { instance = it }
            }
    }
}
