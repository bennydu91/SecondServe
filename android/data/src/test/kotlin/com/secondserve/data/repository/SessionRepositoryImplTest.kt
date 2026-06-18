package com.secondserve.data.repository

import app.cash.turbine.test
import com.secondserve.data.local.dao.SessionDao
import com.secondserve.data.local.db.entity.SessionEntity
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.Session
import com.secondserve.domain.model.SessionFormat
import com.secondserve.domain.model.SessionStatus
import com.secondserve.domain.model.SessionType
import com.secondserve.domain.model.ThirdSetRule
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
import kotlin.test.assertNull

class SessionRepositoryImplTest {

    private lateinit var dao: SessionDao
    private lateinit var repository: SessionRepositoryImpl

    @BeforeEach
    fun setup() {
        dao = mockk()
        repository = SessionRepositoryImpl(dao)
    }

    private fun aSession(
        id: Long = 0L,
        surface: String = "CLAY",
        matchFormat: MatchFormat = MatchFormat.BEST_OF_3,
        thirdSetRule: ThirdSetRule = ThirdSetRule.FULL_ADVANTAGE,
        opponent: String? = null,
        createdAt: Long = 1_000_000L,
        updatedAt: Long = 1_000_000L
    ) = Session(
        id = id,
        surface = surface,
        format = SessionFormat(matchFormat = matchFormat, thirdSetRule = thirdSetRule),
        opponent = opponent,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun anEntity(
        id: Long = 1L,
        surface: String = "CLAY",
        matchFormat: String = "BEST_OF_3",
        thirdSetRule: String = "FULL_ADVANTAGE",
        opponent: String? = null,
        status: String = "ACTIVE",
        sessionType: String = "MATCH",
        createdAt: Long = 1_000_000L,
        updatedAt: Long = 1_000_000L
    ) = SessionEntity(
        id = id,
        surface = surface,
        matchFormat = matchFormat,
        thirdSetRule = thirdSetRule,
        opponent = opponent,
        status = status,
        sessionType = sessionType,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    @Test
    fun `createSession inserts entity and returns session with generated id`() = runTest {
        val slot = slot<SessionEntity>()
        coEvery { dao.insert(capture(slot)) } returns 42L

        val result = repository.createSession(aSession())

        assertIs<AppResult.Success<Session>>(result)
        assertEquals(42L, result.data.id)
        assertEquals("CLAY", slot.captured.surface)
        assertEquals("BEST_OF_3", slot.captured.matchFormat)
        coVerify(exactly = 1) { dao.insert(any()) }
    }

    @Test
    fun `createSession with optional fields stores them correctly`() = runTest {
        val slot = slot<SessionEntity>()
        coEvery { dao.insert(capture(slot)) } returns 1L

        repository.createSession(aSession(opponent = "Dupont", surface = "HARD"))

        assertEquals("Dupont", slot.captured.opponent)
        assertEquals("HARD", slot.captured.surface)
    }

    @Test
    fun `createSession when dao throws returns AppResult Error`() = runTest {
        coEvery { dao.insert(any()) } throws RuntimeException("DB error")

        val result = repository.createSession(aSession())

        assertIs<AppResult.Error>(result)
    }

    @Test
    fun `getAllSessions maps entities to domain objects in order`() = runTest {
        val entities = listOf(
            anEntity(id = 2L, surface = "GRASS", createdAt = 2_000_000L),
            anEntity(id = 1L, surface = "CLAY", createdAt = 1_000_000L)
        )
        every { dao.getAllSessions() } returns flowOf(entities)

        repository.getAllSessions().test {
            val sessions = awaitItem()
            assertEquals(2, sessions.size)
            assertEquals("GRASS", sessions[0].surface)
            assertEquals("CLAY", sessions[1].surface)
            awaitComplete()
        }
    }

    @Test
    fun `getAllSessions maps status and sessionType correctly`() = runTest {
        val entities = listOf(
            anEntity(id = 1L, status = "INTERRUPTED", sessionType = "MATCH")
        )
        every { dao.getAllSessions() } returns flowOf(entities)

        repository.getAllSessions().test {
            val sessions = awaitItem()
            assertEquals(SessionStatus.INTERRUPTED, sessions[0].status)
            assertEquals(SessionType.MATCH, sessions[0].sessionType)
            awaitComplete()
        }
    }

    @Test
    fun `getAllSessions maps MatchFormat and ThirdSetRule correctly`() = runTest {
        val entities = listOf(
            anEntity(id = 1L, matchFormat = "BEST_OF_1", thirdSetRule = "SUPER_TIE_BREAK_10")
        )
        every { dao.getAllSessions() } returns flowOf(entities)

        repository.getAllSessions().test {
            val sessions = awaitItem()
            assertEquals(MatchFormat.BEST_OF_1, sessions[0].format.matchFormat)
            assertEquals(ThirdSetRule.SUPER_TIE_BREAK_10, sessions[0].format.thirdSetRule)
            awaitComplete()
        }
    }

    @Test
    fun `getSessionById returns domain session when entity exists`() = runTest {
        coEvery { dao.getById(1L) } returns anEntity(id = 1L, surface = "HARD")

        val session = repository.getSessionById(1L)

        assertEquals(1L, session?.id)
        assertEquals("HARD", session?.surface)
    }

    @Test
    fun `getSessionById returns null when not found`() = runTest {
        coEvery { dao.getById(99L) } returns null

        val session = repository.getSessionById(99L)

        assertNull(session)
    }

    @Test
    fun `createSession BEST_OF_1 stores FULL_ADVANTAGE as default thirdSetRule`() = runTest {
        val slot = slot<SessionEntity>()
        coEvery { dao.insert(capture(slot)) } returns 1L

        repository.createSession(
            aSession(matchFormat = MatchFormat.BEST_OF_1, thirdSetRule = ThirdSetRule.FULL_ADVANTAGE)
        )

        assertEquals("BEST_OF_1", slot.captured.matchFormat)
        assertEquals("FULL_ADVANTAGE", slot.captured.thirdSetRule)
    }

    @Test
    fun `getAllSessions filters out session with invalid matchFormat enum`() = runTest {
        val invalidEntity = anEntity(id = 99L, matchFormat = "INVALID_FORMAT")
        val validEntity = anEntity(id = 1L)
        every { dao.getAllSessions() } returns flowOf(listOf(invalidEntity, validEntity))

        repository.getAllSessions().test {
            val sessions = awaitItem()
            assertEquals(1, sessions.size)
            assertEquals(1L, sessions[0].id)
            awaitComplete()
        }
    }

    @Test
    fun `getSessionById returns null when entity has invalid enum value`() = runTest {
        val invalidEntity = anEntity(id = 1L, status = "UNKNOWN_STATUS")
        coEvery { dao.getById(1L) } returns invalidEntity

        val session = repository.getSessionById(1L)

        assertNull(session)
    }
}
