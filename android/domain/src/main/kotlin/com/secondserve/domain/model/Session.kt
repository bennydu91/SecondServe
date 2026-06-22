package com.secondserve.domain.model

enum class SessionStatus { ACTIVE, COMPLETED, INTERRUPTED }
enum class SessionType { MATCH, TRAINING }

data class Session(
    val id: Long = 0L,
    val surface: String,
    val format: SessionFormat,
    val opponent: String? = null,
    val competitionType: String? = null,
    val tournament: String? = null,
    val status: SessionStatus = SessionStatus.ACTIVE,
    val sessionType: SessionType = SessionType.MATCH,
    val result: String? = null,
    val scoreText: String? = null,
    val feelingRating: Int? = null,
    val feelingComment: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
