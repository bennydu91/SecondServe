package com.secondserve.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "points",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["session_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["session_id"], name = "idx_points_session")]
)
data class PointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "scorer") val scorer: String,
    @ColumnInfo(name = "sequence_num") val sequenceNum: Int,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long
)
