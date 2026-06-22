package com.secondserve.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.secondserve.data.local.dao.CoachingCacheDao
import com.secondserve.data.local.dao.PlayerProfileDao
import com.secondserve.data.local.dao.SessionDao
import com.secondserve.data.local.dao.SyncQueueDao
import com.secondserve.data.local.dao.WorkAxisDao
import com.secondserve.data.local.db.entity.CoachingCacheEntity
import com.secondserve.data.local.db.entity.PlayerProfileEntity
import com.secondserve.data.local.db.entity.PointEntity
import com.secondserve.data.local.db.entity.RankingHistoryEntity
import com.secondserve.data.local.db.entity.SessionEntity
import com.secondserve.data.local.db.entity.SyncQueueEntity
import com.secondserve.data.local.db.entity.WorkAxisEntity

@Database(
    entities = [
        PlayerProfileEntity::class,
        RankingHistoryEntity::class,
        WorkAxisEntity::class,
        SessionEntity::class,
        PointEntity::class,
        SyncQueueEntity::class,
        CoachingCacheEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class SecondServeDatabase : RoomDatabase() {
    abstract fun playerProfileDao(): PlayerProfileDao
    abstract fun workAxisDao(): WorkAxisDao
    abstract fun sessionDao(): SessionDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun coachingCacheDao(): CoachingCacheDao

    companion object {
        const val DB_NAME = "secondserve_db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE player_profiles ADD COLUMN play_style TEXT")
                database.execSQL("ALTER TABLE player_profiles ADD COLUMN preferred_surfaces TEXT")
                database.execSQL("ALTER TABLE player_profiles ADD COLUMN coach_instruction_1 TEXT")
                database.execSQL("ALTER TABLE player_profiles ADD COLUMN coach_instruction_2 TEXT")
                database.execSQL("ALTER TABLE player_profiles ADD COLUMN coach_instruction_3 TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS work_axes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )"""
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        surface TEXT NOT NULL,
                        match_format TEXT NOT NULL,
                        third_set_rule TEXT NOT NULL,
                        opponent TEXT,
                        competition_type TEXT,
                        tournament TEXT,
                        status TEXT NOT NULL DEFAULT 'ACTIVE',
                        session_type TEXT NOT NULL DEFAULT 'MATCH',
                        result TEXT,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_sessions_surface ON sessions (surface)"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS points (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        session_id INTEGER NOT NULL,
                        scorer TEXT NOT NULL,
                        sequence_num INTEGER NOT NULL,
                        recorded_at INTEGER NOT NULL,
                        FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_points_session ON points (session_id)"
                )

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS sync_queue (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        entity_type TEXT NOT NULL,
                        entity_id INTEGER NOT NULL,
                        operation TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        created_at INTEGER NOT NULL,
                        retry_count INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_sync_queue_status ON sync_queue (status)"
                )

                database.execSQL("ALTER TABLE sessions ADD COLUMN feeling_rating INTEGER")
                database.execSQL("ALTER TABLE sessions ADD COLUMN feeling_comment TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS coaching_cache (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        match_id INTEGER NOT NULL,
                        pattern TEXT NOT NULL,
                        content TEXT NOT NULL,
                        generated_at INTEGER NOT NULL,
                        is_stale INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                database.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_coaching_cache_match_pattern
                    ON coaching_cache (match_id, pattern)
                """.trimIndent())
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sessions ADD COLUMN score_text TEXT")
            }
        }
    }
}
