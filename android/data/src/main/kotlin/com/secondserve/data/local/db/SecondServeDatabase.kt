package com.secondserve.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.secondserve.data.local.dao.PlayerProfileDao
import com.secondserve.data.local.db.entity.PlayerProfileEntity
import com.secondserve.data.local.db.entity.RankingHistoryEntity

@Database(
    entities = [PlayerProfileEntity::class, RankingHistoryEntity::class],
    version = 1,
    exportSchema = true
)
abstract class SecondServeDatabase : RoomDatabase() {
    abstract fun playerProfileDao(): PlayerProfileDao

    companion object {
        const val DB_NAME = "secondserve_db"
    }
}
