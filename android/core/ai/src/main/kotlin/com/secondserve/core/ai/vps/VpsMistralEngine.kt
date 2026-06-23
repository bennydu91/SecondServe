package com.secondserve.core.ai.vps

import com.secondserve.core.ai.InferenceEngine
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.ErrorCode
import com.secondserve.domain.model.InferenceEngineException
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class VpsMistralEngine @Inject constructor(
    okHttpClient: OkHttpClient,
    @Named("vps_base_url") baseUrl: String,
    private val moshi: Moshi
) : InferenceEngine {

    private val analyzeUrl = "${baseUrl}api/v1/coaching/analyze"
    private val requestAdapter = moshi.adapter(AnalyzeRequest::class.java)
    private val responseAdapter = moshi.adapter(AnalyzeResponse::class.java)
    private val client = okHttpClient.newBuilder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun generate(prompt: String): AppResult<String> = withContext(Dispatchers.IO) {
        try {
            val body = requestAdapter.toJson(AnalyzeRequest(prompt))
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(analyzeUrl)
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.d("VpsMistralEngine: HTTP %d", response.code)
                    return@withContext AppResult.Error(
                        InferenceEngineException(ErrorCode.NETWORK_UNAVAILABLE, "HTTP ${response.code}")
                    )
                }
                val bodyStr = response.body?.string()
                    ?: return@withContext AppResult.Error(
                        InferenceEngineException(ErrorCode.NETWORK_UNAVAILABLE, "Empty response")
                    )
                val parsed = responseAdapter.fromJson(bodyStr)
                if (parsed?.content != null) {
                    AppResult.Success(parsed.content)
                } else {
                    AppResult.Error(InferenceEngineException(ErrorCode.NETWORK_UNAVAILABLE, "Invalid response"))
                }
            }
        } catch (e: IOException) {
            Timber.d("VpsMistralEngine: network error: %s", e.message)
            AppResult.Error(InferenceEngineException(ErrorCode.NETWORK_UNAVAILABLE, e.message ?: "Network error", e))
        }
    }
}

private data class AnalyzeRequest(@Json(name = "prompt") val prompt: String)
private data class AnalyzeResponse(@Json(name = "content") val content: String)
