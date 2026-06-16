package com.secondserve.domain.model

const val MAX_WORK_AXES = 3

data class WorkAxis(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)
