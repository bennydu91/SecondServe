package com.secondserve.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "player_profiles")
data class PlayerProfileEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "current_series") val currentSeries: String?,
    @ColumnInfo(name = "current_points") val currentPoints: Int?,
    @ColumnInfo(name = "play_style") val playStyle: String?,
    @ColumnInfo(name = "preferred_surfaces") val preferredSurfaces: String?,
    @ColumnInfo(name = "coach_instruction_1") val coachInstruction1: String?,
    @ColumnInfo(name = "coach_instruction_2") val coachInstruction2: String?,
    @ColumnInfo(name = "coach_instruction_3") val coachInstruction3: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
