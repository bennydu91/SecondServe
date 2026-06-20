package com.secondserve.domain.usecase

import com.secondserve.domain.AppResult
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.model.SetResult
import com.secondserve.domain.model.SessionFormat
import com.secondserve.domain.model.ThirdSetRule
import com.secondserve.domain.repository.SessionRepository
import com.secondserve.domain.usecase.match.CloseMatchUseCase
import com.secondserve.domain.usecase.match.calculateResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CloseMatchUseCaseTest {

    private lateinit var sessionRepository: SessionRepository
    private lateinit var useCase: CloseMatchUseCase

    @BeforeEach
    fun setup() {
        sessionRepository = mockk()
        useCase = CloseMatchUseCase(sessionRepository)
    }

    private fun matchScore(setsA: Int, setsB: Int): MatchScore {
        val sets = (1..setsA).map { SetResult(6, 4) } + (1..setsB).map { SetResult(4, 6) }
        return MatchScore(completedSets = sets, isMatchOver = true)
    }

    @Test
    fun `calculateResult returns VICTORY when player A wins more sets`() {
        val score = matchScore(setsA = 2, setsB = 1)
        assertEquals("VICTORY", score.calculateResult())
    }

    @Test
    fun `calculateResult returns DEFEAT when player B wins more sets`() {
        val score = matchScore(setsA = 0, setsB = 2)
        assertEquals("DEFEAT", score.calculateResult())
    }

    @Test
    fun `calculateResult returns DRAW when sets are equal`() {
        val score = matchScore(setsA = 1, setsB = 1)
        assertEquals("DRAW", score.calculateResult())
    }

    @Test
    fun `calculateResult returns ABANDONED when no sets completed`() {
        val score = MatchScore(completedSets = emptyList(), isMatchOver = false)
        assertEquals("ABANDONED", score.calculateResult())
    }

    @Test
    fun `invoke calls closeSession with correct result string`() = runTest {
        val score = matchScore(setsA = 2, setsB = 0)
        val resultSlot = slot<String>()
        coEvery {
            sessionRepository.closeSession(any(), capture(resultSlot), any(), any())
        } returns AppResult.Success(Unit)

        useCase(sessionId = 1L, finalScore = score, feelingRating = null, feelingComment = null)

        assertEquals("VICTORY", resultSlot.captured)
        coVerify(exactly = 1) { sessionRepository.closeSession(1L, "VICTORY", null, null) }
    }

    @Test
    fun `invoke passes feelingRating and feelingComment to repository`() = runTest {
        val score = matchScore(setsA = 0, setsB = 2)
        coEvery {
            sessionRepository.closeSession(any(), any(), any(), any())
        } returns AppResult.Success(Unit)

        useCase(sessionId = 5L, finalScore = score, feelingRating = 4, feelingComment = "Bon match")

        coVerify(exactly = 1) {
            sessionRepository.closeSession(5L, "DEFEAT", 4, "Bon match")
        }
    }

    @Test
    fun `invoke propagates repository error`() = runTest {
        val score = matchScore(setsA = 2, setsB = 1)
        coEvery {
            sessionRepository.closeSession(any(), any(), any(), any())
        } returns AppResult.Error(RuntimeException("DB error"))

        val result = useCase(sessionId = 1L, finalScore = score, feelingRating = null, feelingComment = null)

        assertTrue(result is AppResult.Error)
    }

    @Test
    fun `D9 fix - closeSession is called which updates updatedAt via repository`() = runTest {
        val score = matchScore(setsA = 2, setsB = 0)
        coEvery {
            sessionRepository.closeSession(any(), any(), any(), any())
        } returns AppResult.Success(Unit)

        val result = useCase(sessionId = 42L, finalScore = score, feelingRating = 3, feelingComment = null)

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 1) { sessionRepository.closeSession(42L, "VICTORY", 3, null) }
    }
}
