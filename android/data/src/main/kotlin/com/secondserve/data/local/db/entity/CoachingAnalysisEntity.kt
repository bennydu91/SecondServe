package com.secondserve.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.secondserve.domain.model.CoachingAnalysis

@Entity(
    tableName = "coaching_analyses",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["session_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["session_id"], unique = true)]
)
data class CoachingAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "generated_at") val generatedAt: Long
)

fun CoachingAnalysisEntity.toDomain(): CoachingAnalysis = CoachingAnalysis(
    id = id,
    sessionId = sessionId,
    content = content,
    generatedAt = generatedAt
)

fun CoachingAnalysis.toEntity(): CoachingAnalysisEntity = CoachingAnalysisEntity(
    id = id,
    sessionId = sessionId,
    content = content,
    generatedAt = generatedAt
)
