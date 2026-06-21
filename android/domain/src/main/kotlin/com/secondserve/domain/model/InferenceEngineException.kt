package com.secondserve.domain.model

class InferenceEngineException(
    val errorCode: ErrorCode,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
