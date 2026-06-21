package com.secondserve.core.ai.gemini

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.secondserve.core.ai.InferenceEngine
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.ErrorCode
import com.secondserve.domain.model.InferenceEngineException
import timber.log.Timber
import javax.inject.Inject

class GeminiNanoEngine @Inject constructor() : InferenceEngine {

    private val model by lazy { Generation.getClient() }

    override suspend fun generate(prompt: String): AppResult<String> {
        return try {
            val status = model.checkStatus()
            if (status != FeatureStatus.AVAILABLE) {
                Timber.d("GeminiNanoEngine unavailable, falling back")
                return AppResult.Error(
                    InferenceEngineException(
                        ErrorCode.INFERENCE_FAILED,
                        "AICore not available — FeatureStatus: $status"
                    )
                )
            }
            val response = model.generateContent(prompt)
            val text = response.candidates.firstOrNull()?.text
                ?: return AppResult.Error(
                    InferenceEngineException(
                        ErrorCode.INFERENCE_FAILED,
                        "GenerateContentResponse returned no candidates"
                    )
                )
            AppResult.Success(text)
        } catch (e: Exception) {
            Timber.d("GeminiNanoEngine unavailable, falling back")
            AppResult.Error(
                InferenceEngineException(ErrorCode.INFERENCE_FAILED, e.message ?: "Unknown error", e)
            )
        }
    }
}
