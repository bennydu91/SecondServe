package com.secondserve.domain

sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val exception: Throwable) : AppResult<Nothing>()
    data object Loading : AppResult<Nothing>()
}
