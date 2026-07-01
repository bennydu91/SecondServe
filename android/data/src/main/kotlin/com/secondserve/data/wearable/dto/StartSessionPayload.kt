package com.secondserve.data.wearable.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class StartSessionPayload(
    @Json(name = "type") val type: String = "START_SESSION",
    @Json(name = "ts") val ts: Long,
    @Json(name = "sessionId") val sessionId: Long,
    @Json(name = "matchFormat") val matchFormat: String,
    @Json(name = "thirdSetRule") val thirdSetRule: String,
    @Json(name = "opponent") val opponent: String? = null
)
