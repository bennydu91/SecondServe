package com.secondserve.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.secondserve.domain.model.AxisSuggestion

@Entity(tableName = "axis_suggestions")
data class AxisSuggestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "status") val status: String = "PENDING",
    @ColumnInfo(name = "generated_at") val generatedAt: Long
)

fun AxisSuggestionEntity.toDomain(): AxisSuggestion =
    AxisSuggestion(id = id, title = title, status = status, generatedAt = generatedAt)

fun AxisSuggestion.toEntity(): AxisSuggestionEntity =
    AxisSuggestionEntity(id = id, title = title, status = status, generatedAt = generatedAt)
