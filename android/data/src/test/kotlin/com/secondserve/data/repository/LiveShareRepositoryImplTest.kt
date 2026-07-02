package com.secondserve.data.repository

import com.secondserve.data.local.dao.LiveShareDao
import com.secondserve.data.local.db.entity.LiveShareEntity
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.data.remote.api.dto.CreateShareResponse
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.LiveShareContext
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.model.Player
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LiveShareRepositoryImplTest {

    private lateinit var dao: LiveShareDao
    private lateinit var vpsApiService: VpsApiService
    private lateinit var repository: LiveShareRepositoryImpl

    private val context = LiveShareContext(
        playerAName = "Benjamin",
        playerBName = "Marceau",
        surface = "CLAY",
        tournament = "Tournoi du club",
        competitionType = "CLUB",
        startedAt = 1000L
    )

    @BeforeEach
    fun setup() {
        dao = mockk()
        vpsApiService = mockk()
        repository = LiveShareRepositoryImpl(dao, vpsApiService)
    }

    @Test
    fun `getOrCreateShare returns cached share without calling API`() = runTest {
        coEvery { dao.getBySessionId(10L) } returns LiveShareEntity(
            id = 1L, sessionId = 10L, token = "abc", url = "https://secondserve.app/live/abc", createdAt = 500L
        )

        val result = repository.getOrCreateShare(10L)

        assertTrue(result is AppResult.Success)
        assertEquals("abc", (result as AppResult.Success).data.token)
        coVerify(exactly = 0) { vpsApiService.createLiveShare(any()) }
    }

    @Test
    fun `getOrCreateShare calls API and caches result when no share exists`() = runTest {
        coEvery { dao.getBySessionId(11L) } returns null
        coEvery { vpsApiService.createLiveShare(any()) } returns CreateShareResponse(
            token = "xyz", url = "https://secondserve.app/live/xyz"
        )
        coEvery { dao.insert(any()) } returns 1L

        val result = repository.getOrCreateShare(11L)

        assertTrue(result is AppResult.Success)
        assertEquals("xyz", (result as AppResult.Success).data.token)
        coVerify(exactly = 1) { dao.insert(match { it.sessionId == 11L && it.token == "xyz" }) }
    }

    @Test
    fun `getOrCreateShare returns Error when API call fails`() = runTest {
        coEvery { dao.getBySessionId(12L) } returns null
        coEvery { vpsApiService.createLiveShare(any()) } throws RuntimeException("network down")

        val result = repository.getOrCreateShare(12L)

        assertTrue(result is AppResult.Error)
    }

    @Test
    fun `pushScore swallows network failures without throwing`() = runTest {
        // Le repository ne fait aucune hypothèse sur l'existence d'un partage actif — c'est au
        // ViewModel de ne l'appeler que lorsque state.shareInfo est non-null (cf. plan ViewModel).
        coEvery { vpsApiService.pushLiveScore(any(), any()) } throws RuntimeException("timeout")

        repository.pushScore(13L, MatchScore(), context = context)

        coVerify(exactly = 1) { vpsApiService.pushLiveScore(eq(13L), any()) }
        // L'absence d'exception levée jusqu'ici est l'assertion : le test échouerait si
        // pushScore laissait remonter l'exception au lieu de la capturer.
    }

    @Test
    fun `pushScore sends currentSetPointLog mapped to A_B strings`() = runTest {
        coEvery { vpsApiService.pushLiveScore(any(), any()) } returns Unit

        repository.pushScore(
            14L,
            MatchScore(currentSetPointLog = listOf(Player.A, Player.B, Player.A)),
            context = context
        )

        coVerify {
            vpsApiService.pushLiveScore(
                eq(14L),
                match { it.currentSetPointLog == listOf("A", "B", "A") && it.playerAName == "Benjamin" }
            )
        }
    }
}
