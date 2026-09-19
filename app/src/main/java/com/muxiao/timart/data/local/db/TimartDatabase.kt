package com.muxiao.timart.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.muxiao.timart.data.local.db.entity.CapsuleEntity
import com.muxiao.timart.data.local.db.entity.DestroyedEntity
import com.muxiao.timart.data.local.db.entity.MetaEntity

/**
 * 应用唯一 Room 数据库：capsules / meta / destroyed_records 三表，version=3，不导出 schema。
 * 迁移只做加列（见 [MIGRATION_1_2] / [MIGRATION_2_3]），绝不破坏性重建。
 */
@Database(
    entities = [CapsuleEntity::class, MetaEntity::class, DestroyedEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class TimartDatabase : RoomDatabase() {

    abstract fun capsuleDao(): CapsuleDao

    abstract fun metaDao(): MetaDao

    abstract fun destroyedDao(): DestroyedDao

    companion object {
        @Volatile
        private var instance: TimartDatabase? = null

        /** v1 → v2：capsules 加盲盒封存标志列（与 @ColumnInfo(defaultValue = "0") 配套） */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE capsules ADD COLUMN blindBox INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v2 → v3：capsules 加口令分片五列（体验储备池 §7.1；均可空，无 DEFAULT 子句） */
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE capsules ADD COLUMN shardSalt TEXT")
                db.execSQL("ALTER TABLE capsules ADD COLUMN shardParams TEXT")
                db.execSQL("ALTER TABLE capsules ADD COLUMN shardVerifier TEXT")
                db.execSQL("ALTER TABLE capsules ADD COLUMN shardThreshold INTEGER")
                db.execSQL("ALTER TABLE capsules ADD COLUMN shardTotal INTEGER")
            }
        }

        /** 进程级单例（双检锁） */
        fun get(context: Context): TimartDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TimartDatabase::class.java,
                    "timart.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
