package com.secondserve.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondserve.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel(), ContainerHost<StatsUiState, Nothing> {

    override val container = container<StatsUiState, Nothing>(StatsUiState.Loading)

    init {
        viewModelScope.launch {
            sessionRepository.getAllSessions()
                .catch { e -> intent { reduce { StatsUiState.Error(e.message ?: "Erreur de chargement") } } }
                .collect { sessions ->
                    try {
                        val stats = computeStats(sessions)
                        intent { reduce { StatsUiState.Content(stats) } }
                    } catch (e: Exception) {
                        intent { reduce { StatsUiState.Error(e.message ?: "Erreur de chargement") } }
                    }
                }
        }
    }
}
