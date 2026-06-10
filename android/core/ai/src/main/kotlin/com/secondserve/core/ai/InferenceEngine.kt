package com.secondserve.core.ai

import com.secondserve.domain.Result

interface InferenceEngine {
    suspend fun generate(prompt: String): Result<String>
}
