package com.secondserve.data.remote.api.dto

import com.squareup.moshi.Json

data class WorkAxisRequest(
    @Json(name = "title") val title: String,
    @Json(name = "created_at") val createdAt: Long
)

data class WorkAxisResponse(
    @Json(name = "id") val id: Long,
    @Json(name = "title") val title: String,
    @Json(name = "created_at") val createdAt: Long,
    @Json(name = "updated_at") val updatedAt: Long
)

data class WorkAxesResponse(
    @Json(name = "items") val items: List<WorkAxisResponse>,
    @Json(name = "total") val total: Int
)
