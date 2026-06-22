package com.secondserve.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondserve.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel(), ContainerHost<HistoryUiState, HistorySideEffect> {

    override val container = container<HistoryUiState, HistorySideEffect>(HistoryUiState.Loading)

    init {
        viewModelScope.launch {
            sessionRepository.getAllSessions().collect { sessions ->
                intent { reduce { HistoryUiState.Content(sessions) } }
            }
        }
    }

    fun onSessionClicked(sessionId: Long) = intent {
        postSideEffect(HistorySideEffect.NavigateToDetail(sessionId))
    }
}
