package com.secondserve.core.ai.mock

import com.secondserve.core.ai.InferenceEngine
import com.secondserve.domain.AppResult
import javax.inject.Inject

class MockInferenceEngine @Inject constructor(
    private val fixedResponse: String = DEFAULT_RESPONSE,
    private val simulateError: Boolean = false,
    private val errorMessage: String = "MockInferenceEngine simulated error"
) : InferenceEngine {

    override suspend fun generate(prompt: String): AppResult<String> {
        return if (simulateError) {
            AppResult.Error(RuntimeException(errorMessage))
        } else {
            AppResult.Success(fixedResponse)
        }
    }

    companion object {
        const val DEFAULT_RESPONSE = "Conseil mock : reste concentré sur le prochain point."
    }
}
