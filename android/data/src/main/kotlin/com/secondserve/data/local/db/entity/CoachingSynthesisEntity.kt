package com.secondserve.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.secondserve.domain.model.CoachingSynthesis

@Entity(tableName = "coaching_syntheses")
data class CoachingSynthesisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "session_count") val sessionCount: Int,
    @ColumnInfo(name = "generated_at") val generatedAt: Long
)

fun CoachingSynthesisEntity.toDomain(): CoachingSynthesis =
    CoachingSynthesis(id = id, content = content, sessionCount = sessionCount, generatedAt = generatedAt)

fun CoachingSynthesis.toEntity(): CoachingSynthesisEntity =
    CoachingSynthesisEntity(id = id, content = content, sessionCount = sessionCount, generatedAt = generatedAt)
