package com.secondserve.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sessions",
    indices = [Index(value = ["surface"], name = "idx_sessions_surface")]
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "surface") val surface: String,
    @ColumnInfo(name = "match_format") val matchFormat: String,
    @ColumnInfo(name = "third_set_rule") val thirdSetRule: String,
    @ColumnInfo(name = "opponent") val opponent: String? = null,
    @ColumnInfo(name = "competition_type") val competitionType: String? = null,
    @ColumnInfo(name = "tournament") val tournament: String? = null,
    @ColumnInfo(name = "status") val status: String = "ACTIVE",
    @ColumnInfo(name = "session_type") val sessionType: String = "MATCH",
    @ColumnInfo(name = "result") val result: String? = null,
    @ColumnInfo(name = "score_text") val scoreText: String? = null,
    @ColumnInfo(name = "feeling_rating") val feelingRating: Int? = null,
    @ColumnInfo(name = "feeling_comment") val feelingComment: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "scheduled_at") val scheduledAt: Long? = null,
    @ColumnInfo(name = "first_serve_percent_self") val firstServePercentSelf: Int? = null,
    @ColumnInfo(name = "first_serve_percent_opponent") val firstServePercentOpponent: Int? = null,
    @ColumnInfo(name = "winners_self") val winnersSelf: Int? = null,
    @ColumnInfo(name = "winners_opponent") val winnersOpponent: Int? = null
)
