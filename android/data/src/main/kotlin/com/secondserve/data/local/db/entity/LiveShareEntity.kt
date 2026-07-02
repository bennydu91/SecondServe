package com.secondserve.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "live_shares",
    indices = [Index(value = ["session_id"], name = "idx_live_shares_session_id", unique = true)]
)
data class LiveShareEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "token") val token: String,
    @ColumnInfo(name = "url") val url: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
