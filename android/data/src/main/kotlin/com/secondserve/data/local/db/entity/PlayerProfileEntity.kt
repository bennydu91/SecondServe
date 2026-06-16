package com.secondserve.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "player_profiles")
data class PlayerProfileEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "current_series") val currentSeries: String?,
    @ColumnInfo(name = "current_points") val currentPoints: Int?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
