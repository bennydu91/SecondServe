package com.secondserve.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.secondserve.domain.AppResult
import com.secondserve.domain.repository.CoachingRepository
import com.secondserve.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collect
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

sealed class SessionDetailSideEffect {
    object SessionDeleted : SessionDetailSideEffect()
}

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val coachingRepository: CoachingRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel(), ContainerHost<SessionDetailUiState, SessionDetailSideEffect> {

    private val sessionId: Long = savedStateHandle.get<Long>("sessionId") ?: -1L

    override val container = container<SessionDetailUiState, SessionDetailSideEffect>(SessionDetailUiState.Loading)

    init {
        load()
    }

    private fun load() = intent {
        try {
            val session = sessionRepository.getSessionById(sessionId)
            if (session == null) {
                reduce { SessionDetailUiState.Error("Session introuvable") }
                return@intent
            }
            val advices = coachingRepository.getAdvicesForSession(sessionId)
            coachingRepository.observeAnalysisForSession(sessionId).collect { analysis ->
                reduce { SessionDetailUiState.Content(session, advices, analysis) }
            }
        } catch (e: Exception) {
            reduce { SessionDetailUiState.Error(e.message ?: "Erreur de chargement") }
        }
    }

    fun deleteSession() = intent {
        val s = (state as? SessionDetailUiState.Content) ?: return@intent
        when (sessionRepository.deleteSession(s.session.id)) {
            is AppResult.Success -> postSideEffect(SessionDetailSideEffect.SessionDeleted)
            is AppResult.Error -> {}
            AppResult.Loading -> {}
        }
    }
}
