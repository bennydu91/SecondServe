package com.secondserve

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondserve.domain.model.Session
import com.secondserve.domain.model.SessionStatus
import com.secondserve.domain.model.SessionType
import com.secondserve.domain.repository.PlayerProfileRepository
import com.secondserve.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val currentSeries: String? = null,
    val currentPoints: Int? = null,
    val lastSession: Session? = null,
    val winRatePercent: Int? = null,
    val streakLabel: String? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val playerProfileRepository: PlayerProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                sessionRepository.getAllSessions(),
                playerProfileRepository.getRankingHistory()
            ) { sessions, rankingHistory ->
                val completedMatches = sessions.filter {
                    it.status == SessionStatus.COMPLETED && it.sessionType == SessionType.MATCH
                }
                val lastSession = sessions
                    .filter { it.status == SessionStatus.COMPLETED }
                    .maxByOrNull { it.updatedAt }

                val victories = completedMatches.count { it.result == "VICTORY" }
                val winRate = if (completedMatches.isNotEmpty()) {
                    victories * 100 / completedMatches.size
                } else null

                val sortedByDate = completedMatches.sortedByDescending { it.updatedAt }
                val streak = computeStreak(sortedByDate)
                val latestRanking = rankingHistory.maxByOrNull { it.recordedAt }

                HomeUiState(
                    currentSeries = latestRanking?.series,
                    currentPoints = latestRanking?.points,
                    lastSession = lastSession,
                    winRatePercent = winRate,
                    streakLabel = streak,
                    isLoading = false
                )
            }.collect { _uiState.value = it }
        }
    }

    private fun computeStreak(sortedSessions: List<Session>): String? {
        if (sortedSessions.isEmpty()) return null
        val firstResult = sortedSessions.first().result ?: return null
        var count = 0
        for (session in sortedSessions) {
            if (session.result == firstResult) count++ else break
        }
        if (count < 2) return null
        return when (firstResult) {
            "VICTORY" -> "$count victoires consécutives"
            "DEFEAT" -> "$count défaites consécutives"
            else -> null
        }
    }
}
