package com.secondserve.data.wearable.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class StartSessionRequestPayload(
    @Json(name = "type") val type: String = "START_SESSION_REQUEST",
    @Json(name = "ts") val ts: Long,
    @Json(name = "matchFormat") val matchFormat: String,
    @Json(name = "thirdSetRule") val thirdSetRule: String
)
