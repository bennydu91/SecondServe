package com.secondserve.data.repository

import app.cash.turbine.test
import com.secondserve.data.local.dao.CoachingAnalysisDao
import com.secondserve.data.local.dao.CoachingCacheDao
import com.secondserve.data.local.db.entity.CoachingAnalysisEntity
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.CoachingAnalysis
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CoachingRepositoryImplTest {

    private lateinit var cacheDao: CoachingCacheDao
    private lateinit var analysisDao: CoachingAnalysisDao
    private lateinit var repository: CoachingRepositoryImpl

    @BeforeEach
    fun setup() {
        cacheDao = mockk(relaxed = true)
        analysisDao = mockk(relaxed = true)
        repository = CoachingRepositoryImpl(cacheDao, analysisDao)
    }

    private fun anAnalysisEntity(sessionId: Long = 1L) = CoachingAnalysisEntity(
        id = 10L,
        sessionId = sessionId,
        content = "Points forts : bon service. Points faibles : retour.",
        generatedAt = 1_000_000L
    )

    @Test
    fun `saveAnalysis inserts entity and returns Success with domain model`() = runTest {
        coEvery { analysisDao.insert(any()) } returns 10L

        val result = repository.saveAnalysis(1L, "Points forts : bon service.")

        assertIs<AppResult.Success<CoachingAnalysis>>(result)
        assertEquals(10L, result.data.id)
        assertEquals(1L, result.data.sessionId)
        assertEquals("Points forts : bon service.", result.data.content)
        coVerify(exactly = 1) { analysisDao.insert(any()) }
    }

    @Test
    fun `saveAnalysis returns Error when dao throws`() = runTest {
        coEvery { analysisDao.insert(any()) } throws RuntimeException("DB error")

        val result = repository.saveAnalysis(1L, "content")

        assertIs<AppResult.Error>(result)
    }

    @Test
    fun `getAnalysisForSession returns domain model when found`() = runTest {
        coEvery { analysisDao.getBySessionId(1L) } returns anAnalysisEntity()

        val result = repository.getAnalysisForSession(1L)

        assertEquals(10L, result?.id)
        assertEquals(1L, result?.sessionId)
        assertEquals("Points forts : bon service. Points faibles : retour.", result?.content)
    }

    @Test
    fun `getAnalysisForSession returns null when not found`() = runTest {
        coEvery { analysisDao.getBySessionId(99L) } returns null

        val result = repository.getAnalysisForSession(99L)

        assertNull(result)
    }

    @Test
    fun `observeAnalysisForSession emits domain model when entity present`() = runTest {
        coEvery { analysisDao.observeBySessionId(1L) } returns flowOf(anAnalysisEntity())

        repository.observeAnalysisForSession(1L).test {
            val item = awaitItem()
            assertEquals(10L, item?.id)
            assertEquals("Points forts : bon service. Points faibles : retour.", item?.content)
            awaitComplete()
        }
    }

    @Test
    fun `observeAnalysisForSession emits null when no entity`() = runTest {
        coEvery { analysisDao.observeBySessionId(2L) } returns flowOf(null)

        repository.observeAnalysisForSession(2L).test {
            assertNull(awaitItem())
            awaitComplete()
        }
    }
}
