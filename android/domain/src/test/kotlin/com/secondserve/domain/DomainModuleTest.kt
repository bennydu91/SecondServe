package com.secondserve.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DomainModuleTest {

    @Test
    fun `AppResult Success wraps data correctly`() {
        val result: AppResult<String> = AppResult.Success("ok")
        assertTrue(result is AppResult.Success)
        assertEquals("ok", (result as AppResult.Success).data)
    }

    @Test
    fun `AppResult Error wraps exception correctly`() {
        val ex = RuntimeException("fail")
        val result: AppResult<String> = AppResult.Error(ex)
        assertTrue(result is AppResult.Error)
        assertEquals("fail", (result as AppResult.Error).exception.message)
    }
}
