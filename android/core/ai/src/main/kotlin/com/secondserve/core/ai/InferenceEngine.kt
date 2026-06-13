package com.secondserve.core.ai

import com.secondserve.domain.AppResult

interface InferenceEngine {
    suspend fun generate(prompt: String): AppResult<String>
}
