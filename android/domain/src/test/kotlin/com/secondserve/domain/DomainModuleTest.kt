package com.secondserve.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DomainModuleTest {

    @Test
    fun `Result Success wraps data correctly`() {
        val result: Result<String> = Result.Success("ok")
        assertTrue(result is Result.Success)
        assertEquals("ok", (result as Result.Success).data)
    }

    @Test
    fun `Result Error wraps exception correctly`() {
        val ex = RuntimeException("fail")
        val result: Result<String> = Result.Error(ex)
        assertTrue(result is Result.Error)
        assertEquals("fail", (result as Result.Error).exception.message)
    }
}
