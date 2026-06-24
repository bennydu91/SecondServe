package com.secondserve.data.remote.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PendingNotificationResponse(val content: String)
