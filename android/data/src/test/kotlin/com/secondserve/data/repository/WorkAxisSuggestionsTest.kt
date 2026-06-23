package com.secondserve.data.repository

import com.secondserve.core.ai.InferenceEngine
import com.secondserve.data.local.dao.AxisSuggestionDao
import com.secondserve.data.local.dao.CoachingAnalysisDao
import com.secondserve.data.local.dao.CoachingSynthesisDao
import com.secondserve.data.local.dao.WorkAxisDao
import com.secondserve.data.local.db.entity.AxisSuggestionEntity
import com.secondserve.data.local.db.entity.CoachingAnalysisEntity
import com.secondserve.data.local.db.entity.CoachingSynthesisEntity
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.domain.AppResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkAxisSuggestionsTest {

    private val dao: WorkAxisDao = mockk(relaxed = true)
    private val suggestionDao: AxisSuggestionDao = mockk(relaxed = true)
    private val analysisDao: CoachingAnalysisDao = mockk(relaxed = true)
    private val synthesisDao: CoachingSynthesisDao = mockk(relaxed = true)
    private val vpsApiService: VpsApiService = mockk(relaxed = true)
    private val vpsMistralEngine: InferenceEngine = mockk()

    private lateinit var repository: WorkAxisRepositoryImpl

    @BeforeEach
    fun setup() {
        every { dao.getAll() } returns flowOf(emptyList())
        coEvery { dao.getAllTitles() } returns emptyList()
        repository = WorkAxisRepositoryImpl(
            dao, suggestionDao, analysisDao, synthesisDao, vpsApiService, vpsMistralEngine
        )
    }

    @Test
    fun `generateAndSaveSuggestions whenNoCoachingData returnsError`() = runTest {
        coEvery { synthesisDao.getLatest() } returns null
        coEvery { analysisDao.getMostRecent() } returns null

        val result = repository.generateAndSaveSuggestions()

        assertIs<AppResult.Error>(result)
        assertEquals("No coaching data available", result.exception.message)
    }

    @Test
    fun `generateAndSaveSuggestions whenSynthesisExists savesParsesSuggestions`() = runTest {
        val synthesis = CoachingSynthesisEntity(id = 1L, content = "Ton revers est faible en fond de court.", sessionCount = 3, generatedAt = 1000L)
        coEvery { synthesisDao.getLatest() } returns synthesis
        coEvery { vpsMistralEngine.generate(any()) } returns AppResult.Success("Travail du revers\nMontée au filet\nService kicker")
        val insertedSlot = slot<List<AxisSuggestionEntity>>()
        coEvery { suggestionDao.insertAll(capture(insertedSlot)) } returns Unit

        val result = repository.generateAndSaveSuggestions()

        assertIs<AppResult.Success<Unit>>(result)
        assertEquals(3, insertedSlot.captured.size)
        assertEquals("Travail du revers", insertedSlot.captured[0].title)
        assertEquals("PENDING", insertedSlot.captured[0].status)
    }

    @Test
    fun `generateAndSaveSuggestions whenAnalysisExistsNoSynthesis usesAnalysis`() = runTest {
        val analysis = CoachingAnalysisEntity(id = 1L, sessionId = 10L, content = "Analyse du match.", generatedAt = 1000L)
        coEvery { synthesisDao.getLatest() } returns null
        coEvery { analysisDao.getMostRecent() } returns analysis
        coEvery { vpsMistralEngine.generate(any()) } returns AppResult.Success("Améliorer le service")
        coEvery { suggestionDao.insertAll(any()) } returns Unit

        val result = repository.generateAndSaveSuggestions()

        assertIs<AppResult.Success<Unit>>(result)
        coVerify { vpsMistralEngine.generate(match { it.contains("Analyse du match.") }) }
    }

    @Test
    fun `generateAndSaveSuggestions whenVpsError returnsError`() = runTest {
        val synthesis = CoachingSynthesisEntity(id = 1L, content = "Contenu.", sessionCount = 1, generatedAt = 1000L)
        coEvery { synthesisDao.getLatest() } returns synthesis
        coEvery { vpsMistralEngine.generate(any()) } returns AppResult.Error(RuntimeException("network error"))

        val result = repository.generateAndSaveSuggestions()

        assertIs<AppResult.Error>(result)
        assertEquals("network error", result.exception.message)
    }

    @Test
    fun `generateAndSaveSuggestions whenBlankResponse returnsError`() = runTest {
        val synthesis = CoachingSynthesisEntity(id = 1L, content = "Contenu.", sessionCount = 1, generatedAt = 1000L)
        coEvery { synthesisDao.getLatest() } returns synthesis
        coEvery { vpsMistralEngine.generate(any()) } returns AppResult.Success("   \n  ")

        val result = repository.generateAndSaveSuggestions()

        assertIs<AppResult.Error>(result)
        assertEquals("No suggestions parsed", result.exception.message)
    }

    @Test
    fun `acceptSuggestion createsWorkAxisAndMarksAccepted`() = runTest {
        val entity = AxisSuggestionEntity(id = 5L, title = "Montée au filet", generatedAt = 1000L)
        coEvery { suggestionDao.getById(5L) } returns entity
        coEvery { dao.insert(any()) } returns 10L

        val result = repository.acceptSuggestion(5L)

        assertIs<AppResult.Success<Unit>>(result)
        coVerify { dao.insert(match { it.title == "Montée au filet" }) }
        coVerify { suggestionDao.updateStatus(5L, "ACCEPTED") }
    }

    @Test
    fun `ignoreSuggestion marksIgnored`() = runTest {
        coEvery { suggestionDao.updateStatus(3L, "IGNORED") } returns Unit

        repository.ignoreSuggestion(3L)

        coVerify { suggestionDao.updateStatus(3L, "IGNORED") }
    }

    @Test
    fun `hasPendingSuggestions whenNone returnsFalse`() = runTest {
        coEvery { suggestionDao.countPending() } returns 0

        assertFalse(repository.hasPendingSuggestions())
    }

    @Test
    fun `hasPendingSuggestions whenSome returnsTrue`() = runTest {
        coEvery { suggestionDao.countPending() } returns 2

        assertTrue(repository.hasPendingSuggestions())
    }

    @Test
    fun `parseSuggestionsResponse stripsNumerationAndBullets`() = runTest {
        val synthesis = CoachingSynthesisEntity(id = 1L, content = "Contenu.", sessionCount = 1, generatedAt = 1000L)
        coEvery { synthesisDao.getLatest() } returns synthesis
        coEvery { vpsMistralEngine.generate(any()) } returns AppResult.Success(
            "1. Revers long de ligne\n- Service kicker\n• Montée au filet"
        )
        val insertedSlot = slot<List<AxisSuggestionEntity>>()
        coEvery { suggestionDao.insertAll(capture(insertedSlot)) } returns Unit

        repository.generateAndSaveSuggestions()

        assertEquals("Revers long de ligne", insertedSlot.captured[0].title)
        assertEquals("Service kicker", insertedSlot.captured[1].title)
        assertEquals("Montée au filet", insertedSlot.captured[2].title)
    }

    @Test
    fun `parseSuggestionsResponse limitsToThreeSuggestions`() = runTest {
        val synthesis = CoachingSynthesisEntity(id = 1L, content = "Contenu.", sessionCount = 1, generatedAt = 1000L)
        coEvery { synthesisDao.getLatest() } returns synthesis
        coEvery { vpsMistralEngine.generate(any()) } returns AppResult.Success(
            "Axe un\nAxe deux\nAxe trois\nAxe quatre\nAxe cinq"
        )
        val insertedSlot = slot<List<AxisSuggestionEntity>>()
        coEvery { suggestionDao.insertAll(capture(insertedSlot)) } returns Unit

        repository.generateAndSaveSuggestions()

        assertEquals(3, insertedSlot.captured.size)
    }
}
