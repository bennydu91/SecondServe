package com.secondserve.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.secondserve.domain.model.CoachingCacheEntry
import com.secondserve.domain.model.MatchPattern

@Entity(
    tableName = "coaching_cache",
    indices = [Index(value = ["match_id", "pattern"], unique = true)]
)
data class CoachingCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "match_id") val matchId: Long,
    @ColumnInfo(name = "pattern") val pattern: String,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "generated_at") val generatedAt: Long,
    @ColumnInfo(name = "is_stale") val isStale: Boolean = false
)

fun CoachingCacheEntity.toDomain(): CoachingCacheEntry = CoachingCacheEntry(
    id = id,
    matchId = matchId,
    pattern = MatchPattern.valueOf(pattern),
    content = content,
    generatedAt = generatedAt,
    isStale = isStale
)

fun CoachingCacheEntry.toEntity(): CoachingCacheEntity = CoachingCacheEntity(
    id = id,
    matchId = matchId,
    pattern = pattern.name,
    content = content,
    generatedAt = generatedAt,
    isStale = isStale
)
