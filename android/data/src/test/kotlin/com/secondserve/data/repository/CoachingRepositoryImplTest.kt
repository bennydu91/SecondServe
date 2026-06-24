package com.secondserve.data.repository

import app.cash.turbine.test
import com.secondserve.data.local.dao.CoachingAnalysisDao
import com.secondserve.data.local.dao.CoachingCacheDao
import com.secondserve.data.local.dao.CoachingSynthesisDao
import com.secondserve.data.local.db.entity.CoachingAnalysisEntity
import com.secondserve.data.local.db.entity.CoachingSynthesisEntity
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.CoachingAnalysis
import com.secondserve.domain.model.CoachingSynthesis
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class CoachingRepositoryImplTest {

    private lateinit var cacheDao: CoachingCacheDao
    private lateinit var analysisDao: CoachingAnalysisDao
    private lateinit var synthesisDao: CoachingSynthesisDao
    private lateinit var repository: CoachingRepositoryImpl

    @BeforeEach
    fun setup() {
        cacheDao = mockk(relaxed = true)
        analysisDao = mockk(relaxed = true)
        synthesisDao = mockk(relaxed = true)
        repository = CoachingRepositoryImpl(cacheDao, analysisDao, synthesisDao)
    }

    private fun aSynthesisEntity() = CoachingSynthesisEntity(
        id = 5L,
        content = "Patterns récurrents : bon service.",
        sessionCount = 3,
        generatedAt = 9_000_000L
    )

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

    @Test
    fun `saveAnalysis returns existing analysis when row already exists (IGNORE strategy)`() = runTest {
        coEvery { analysisDao.insert(any()) } returns -1L
        coEvery { analysisDao.getBySessionId(1L) } returns anAnalysisEntity()

        val result = repository.saveAnalysis(1L, "Nouveau contenu ignoré")

        assertIs<AppResult.Success<CoachingAnalysis>>(result)
        assertEquals(10L, result.data.id)
        assertEquals("Points forts : bon service. Points faibles : retour.", result.data.content)
    }

    @Test
    fun `saveSynthesis inserts entity and returns Success with domain model`() = runTest {
        coEvery { synthesisDao.insert(any()) } returns 5L

        val result = repository.saveSynthesis("Patterns récurrents : bon service.", 3)

        assertIs<AppResult.Success<CoachingSynthesis>>(result)
        assertEquals(5L, result.data.id)
        assertEquals("Patterns récurrents : bon service.", result.data.content)
        assertEquals(3, result.data.sessionCount)
        coVerify(exactly = 1) { synthesisDao.insert(any()) }
    }

    @Test
    fun `saveSynthesis returns Error when dao throws`() = runTest {
        coEvery { synthesisDao.insert(any()) } throws RuntimeException("DB error")

        val result = repository.saveSynthesis("content", 3)

        assertIs<AppResult.Error>(result)
    }

    @Test
    fun `getLatestSynthesis returns domain model when found`() = runTest {
        coEvery { synthesisDao.getLatest() } returns aSynthesisEntity()

        val result = repository.getLatestSynthesis()

        assertEquals(5L, result?.id)
        assertEquals("Patterns récurrents : bon service.", result?.content)
        assertEquals(3, result?.sessionCount)
    }

    @Test
    fun `getLatestSynthesis returns null when not found`() = runTest {
        coEvery { synthesisDao.getLatest() } returns null

        val result = repository.getLatestSynthesis()

        assertNull(result)
    }

    @Test
    fun `observeLatestSynthesis emits domain model when entity present`() = runTest {
        coEvery { synthesisDao.observeLatest() } returns flowOf(aSynthesisEntity())

        repository.observeLatestSynthesis().test {
            val item = awaitItem()
            assertEquals(5L, item?.id)
            assertEquals("Patterns récurrents : bon service.", item?.content)
            awaitComplete()
        }
    }

    @Test
    fun `observeLatestSynthesis emits null when no entity`() = runTest {
        coEvery { synthesisDao.observeLatest() } returns flowOf(null)

        repository.observeLatestSynthesis().test {
            assertNull(awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `saveAnalysis rethrows CancellationException`() = runTest {
        coEvery { analysisDao.insert(any()) } throws CancellationException("cancelled")
        assertFailsWith<CancellationException> {
            repository.saveAnalysis(1L, "content")
        }
    }

    @Test
    fun `saveSynthesis rethrows CancellationException`() = runTest {
        coEvery { synthesisDao.insert(any()) } throws CancellationException("cancelled")
        assertFailsWith<CancellationException> {
            repository.saveSynthesis("content", 3)
        }
    }

    @Test
    fun `observeAllAnalyses emits mapped domain list`() = runTest {
        coEvery { analysisDao.getAllAnalyses() } returns flowOf(listOf(anAnalysisEntity()))

        repository.observeAllAnalyses().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(10L, list[0].id)
            awaitComplete()
        }
    }
}
