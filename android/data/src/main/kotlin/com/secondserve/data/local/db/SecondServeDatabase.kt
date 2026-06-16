package com.secondserve.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.secondserve.data.local.dao.PlayerProfileDao
import com.secondserve.data.local.db.entity.PlayerProfileEntity
import com.secondserve.data.local.db.entity.RankingHistoryEntity

@Database(
    entities = [PlayerProfileEntity::class, RankingHistoryEntity::class],
    version = 2,
    exportSchema = true
)
abstract class SecondServeDatabase : RoomDatabase() {
    abstract fun playerProfileDao(): PlayerProfileDao

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
    }
}
