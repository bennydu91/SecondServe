package com.secondserve.data.repository

import com.secondserve.domain.model.MatchScore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ScoreRepositoryImplTest {

    private val repository = ScoreRepositoryImpl()

    @Test
    fun `latestScore starts as null`() = runTest {
        assertNull(repository.latestScore.first())
    }

    @Test
    fun `updateScore emits new score`() = runTest {
        val score = MatchScore(currentSetGamesA = 3, currentSetGamesB = 2)
        repository.updateScore(score)
        assertEquals(score, repository.latestScore.first())
    }

    @Test
    fun `updateScore replaces previous score`() = runTest {
        val score1 = MatchScore(currentSetGamesA = 1)
        val score2 = MatchScore(currentSetGamesA = 2)
        repository.updateScore(score1)
        repository.updateScore(score2)
        assertEquals(score2, repository.latestScore.first())
    }
}
