package com.secondserve.wear.presentation.match

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ScoreScreenLabelTest {

    @Test
    fun `null falls back to generic label`() {
        assertEquals(FALLBACK_OPPONENT_LABEL, sanitizeOpponentLabel(null))
    }

    @Test
    fun `blank string falls back to generic label`() {
        assertEquals(FALLBACK_OPPONENT_LABEL, sanitizeOpponentLabel("   "))
    }

    @Test
    fun `empty string falls back to generic label`() {
        assertEquals(FALLBACK_OPPONENT_LABEL, sanitizeOpponentLabel(""))
    }

    @Test
    fun `valid name is preserved as-is (no forced uppercase)`() {
        assertEquals("Marceau", sanitizeOpponentLabel("Marceau"))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("Marceau", sanitizeOpponentLabel("  Marceau  "))
    }

    @Test
    fun `name at the length limit is not truncated`() {
        val name = "A".repeat(MAX_OPPONENT_LABEL_LENGTH)
        assertEquals(name, sanitizeOpponentLabel(name))
    }

    @Test
    fun `overly long name is truncated with an ellipsis`() {
        val name = "A".repeat(MAX_OPPONENT_LABEL_LENGTH + 10)
        val result = sanitizeOpponentLabel(name)
        assertEquals(MAX_OPPONENT_LABEL_LENGTH, result.length)
        assertEquals("…", result.takeLast(1))
    }
}
