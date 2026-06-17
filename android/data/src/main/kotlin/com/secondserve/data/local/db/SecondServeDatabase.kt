package com.secondserve.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.secondserve.data.local.dao.PlayerProfileDao
import com.secondserve.data.local.dao.SessionDao
import com.secondserve.data.local.dao.WorkAxisDao
import com.secondserve.data.local.db.entity.PlayerProfileEntity
import com.secondserve.data.local.db.entity.RankingHistoryEntity
import com.secondserve.data.local.db.entity.SessionEntity
import com.secondserve.data.local.db.entity.WorkAxisEntity

@Database(
    entities = [
        PlayerProfileEntity::class,
        RankingHistoryEntity::class,
        WorkAxisEntity::class,
        SessionEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class SecondServeDatabase : RoomDatabase() {
    abstract fun playerProfileDao(): PlayerProfileDao
    abstract fun workAxisDao(): WorkAxisDao
    abstract fun sessionDao(): SessionDao

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
    }
}
