package com.secondserve.data.repository

import app.cash.turbine.test
import com.secondserve.core.ai.InferenceEngine
import com.secondserve.data.local.dao.AxisSuggestionDao
import com.secondserve.data.local.dao.CoachingAnalysisDao
import com.secondserve.data.local.dao.CoachingSynthesisDao
import com.secondserve.data.local.dao.WorkAxisDao
import com.secondserve.data.local.db.entity.WorkAxisEntity
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.data.remote.api.dto.WorkAxisResponse
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
import kotlin.test.assertTrue

class WorkAxisRepositoryImplTest {

    private lateinit var dao: WorkAxisDao
    private lateinit var vpsApiService: VpsApiService
    private val suggestionDao: AxisSuggestionDao = mockk(relaxed = true)
    private val analysisDao: CoachingAnalysisDao = mockk(relaxed = true)
    private val synthesisDao: CoachingSynthesisDao = mockk(relaxed = true)
    private val vpsMistralEngine: InferenceEngine = mockk(relaxed = true)
    private lateinit var repository: WorkAxisRepositoryImpl

    @BeforeEach
    fun setup() {
        dao = mockk()
        vpsApiService = mockk()
        repository = WorkAxisRepositoryImpl(dao, suggestionDao, analysisDao, synthesisDao, vpsApiService, vpsMistralEngine)
    }

    private fun axisEntity(
        id: Long = 1L,
        title: String = "Travail du revers",
        createdAt: Long = 1000L,
        updatedAt: Long = 1000L
    ) = WorkAxisEntity(id = id, title = title, createdAt = createdAt, updatedAt = updatedAt)

    @Test
    fun `createWorkAxis inserts locally and syncs to VPS`() = runTest {
        coEvery { dao.count() } returns 0
        val entitySlot = slot<WorkAxisEntity>()
        coEvery { dao.insert(capture(entitySlot)) } returns 1L
        coEvery { vpsApiService.createWorkAxis(any()) } returns WorkAxisResponse(1L, "Revers", 1000L, 1000L)

        val result = repository.createWorkAxis("Revers")

        assertIs<AppResult.Success<Unit>>(result)
        assertEquals("Revers", entitySlot.captured.title)
        coVerify(exactly = 1) { dao.insert(any()) }
        coVerify(exactly = 1) { vpsApiService.createWorkAxis(any()) }
    }

    @Test
    fun `createWorkAxis when VPS fails local save still succeeds`() = runTest {
        coEvery { dao.count() } returns 0
        coEvery { dao.insert(any()) } returns 1L
        coEvery { vpsApiService.createWorkAxis(any()) } throws RuntimeException("network error")

        val result = repository.createWorkAxis("Service")

        assertIs<AppResult.Success<Unit>>(result)
        coVerify(exactly = 1) { dao.insert(any()) }
    }

    @Test
    fun `createWorkAxis when at max capacity returns Error`() = runTest {
        coEvery { dao.count() } returns 3

        val result = repository.createWorkAxis("Quatrième axe")

        assertIs<AppResult.Error>(result)
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun `updateWorkAxis preserves createdAt and updates title`() = runTest {
        val existing = axisEntity(id = 1L, title = "Ancien titre", createdAt = 1000L)
        val updatedSlot = slot<WorkAxisEntity>()
        coEvery { dao.getById(1L) } returns existing
        coEvery { dao.update(capture(updatedSlot)) } returns Unit
        coEvery { vpsApiService.updateWorkAxis(any(), any()) } returns WorkAxisResponse(1L, "Nouveau titre", 1000L, 2000L)

        val result = repository.updateWorkAxis(1L, "Nouveau titre")

        assertIs<AppResult.Success<Unit>>(result)
        assertEquals("Nouveau titre", updatedSlot.captured.title)
        assertEquals(1000L, updatedSlot.captured.createdAt)
    }

    @Test
    fun `updateWorkAxis when VPS fails local update still succeeds`() = runTest {
        val existing = axisEntity(id = 1L)
        coEvery { dao.getById(1L) } returns existing
        coEvery { dao.update(any()) } returns Unit
        coEvery { vpsApiService.updateWorkAxis(any(), any()) } throws RuntimeException("network error")

        val result = repository.updateWorkAxis(1L, "Nouveau titre")

        assertIs<AppResult.Success<Unit>>(result)
    }

    @Test
    fun `updateWorkAxis returns Error when axis not found`() = runTest {
        coEvery { dao.getById(99L) } returns null

        val result = repository.updateWorkAxis(99L, "Titre")

        assertIs<AppResult.Error>(result)
    }

    @Test
    fun `deleteWorkAxis deletes locally and syncs to VPS`() = runTest {
        coEvery { dao.delete(1L) } returns Unit
        coEvery { vpsApiService.deleteWorkAxis(1L) } returns Unit

        val result = repository.deleteWorkAxis(1L)

        assertIs<AppResult.Success<Unit>>(result)
        coVerify(exactly = 1) { dao.delete(1L) }
        coVerify(exactly = 1) { vpsApiService.deleteWorkAxis(1L) }
    }

    @Test
    fun `deleteWorkAxis when VPS fails local delete still succeeds`() = runTest {
        coEvery { dao.delete(any()) } returns Unit
        coEvery { vpsApiService.deleteWorkAxis(any()) } throws RuntimeException("network error")

        val result = repository.deleteWorkAxis(1L)

        assertIs<AppResult.Success<Unit>>(result)
    }

    @Test
    fun `getWorkAxes returns mapped domain objects in chronological order`() = runTest {
        val entities = listOf(
            axisEntity(id = 1L, title = "Revers", createdAt = 1000L),
            axisEntity(id = 2L, title = "Service", createdAt = 2000L)
        )
        every { dao.getAll() } returns flowOf(entities)

        repository.getWorkAxes().test {
            val items = awaitItem()
            assertEquals(2, items.size)
            assertEquals("Revers", items[0].title)
            assertEquals("Service", items[1].title)
            awaitComplete()
        }
    }

    @Test
    fun `getActiveWorkAxesTitles returns titles in chronological order`() = runTest {
        coEvery { dao.getAllTitles() } returns listOf("Revers", "Service")

        val titles = repository.getActiveWorkAxesTitles()

        assertEquals(listOf("Revers", "Service"), titles)
    }
}
