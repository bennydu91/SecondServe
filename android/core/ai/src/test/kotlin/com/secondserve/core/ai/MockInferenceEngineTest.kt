package com.secondserve.core.ai.mock

import com.secondserve.core.ai.mock.MockInferenceEngine
import com.secondserve.domain.AppResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MockInferenceEngineTest {

    @Test
    fun `generate returns default response when no args`() = runTest {
        val mock = MockInferenceEngine()
        val result = mock.generate("any prompt")
        assertIs<AppResult.Success<String>>(result)
        assertEquals(MockInferenceEngine.DEFAULT_RESPONSE, result.data)
    }

    @Test
    fun `generate returns fixed response when configured`() = runTest {
        val expected = "Conseil personnalisé de test"
        val mock = MockInferenceEngine(fixedResponse = expected)
        val result = mock.generate("any prompt")
        assertIs<AppResult.Success<String>>(result)
        assertEquals(expected, result.data)
    }

    @Test
    fun `generate returns error when simulateError is true`() = runTest {
        val mock = MockInferenceEngine(simulateError = true)
        val result = mock.generate("any prompt")
        assertIs<AppResult.Error>(result)
        assertTrue(result.exception is RuntimeException)
    }

    @Test
    fun `generate returns error with custom message when simulateError is true`() = runTest {
        val errorMsg = "Test error message"
        val mock = MockInferenceEngine(simulateError = true, errorMessage = errorMsg)
        val result = mock.generate("any prompt")
        assertIs<AppResult.Error>(result)
        assertEquals(errorMsg, result.exception.message)
    }

    @Test
    fun `generate is deterministic — same prompt returns same response`() = runTest {
        val mock = MockInferenceEngine(fixedResponse = "réponse fixe")
        val result1 = mock.generate("prompt A")
        val result2 = mock.generate("prompt B")
        assertEquals(result1, result2)
    }
}
