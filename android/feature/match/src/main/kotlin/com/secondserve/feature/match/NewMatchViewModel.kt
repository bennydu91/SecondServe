package com.secondserve.feature.match

import androidx.lifecycle.ViewModel
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.Session
import com.secondserve.domain.model.SessionFormat
import com.secondserve.domain.model.SessionStatus
import com.secondserve.domain.model.ThirdSetRule
import com.secondserve.domain.notification.NotificationScheduler
import com.secondserve.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

@HiltViewModel
class NewMatchViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val notificationScheduler: NotificationScheduler
) : ViewModel(), ContainerHost<NewMatchUiState, NewMatchSideEffect> {

    override val container = container<NewMatchUiState, NewMatchSideEffect>(NewMatchUiState())

    fun onSurfaceSelected(surface: String) = intent {
        reduce { state.copy(selectedSurface = surface) }
    }

    fun onMatchFormatSelected(format: MatchFormat) = intent {
        reduce {
            state.copy(
                selectedMatchFormat = format,
                selectedThirdSetRule = if (format == MatchFormat.BEST_OF_1) null
                                       else state.selectedThirdSetRule
            )
        }
    }

    fun onThirdSetRuleSelected(rule: ThirdSetRule) = intent {
        reduce { state.copy(selectedThirdSetRule = rule) }
    }

    fun onOpponentChanged(value: String) = intent {
        reduce { state.copy(opponent = value) }
    }

    fun onCompetitionTypeChanged(value: String) = intent {
        reduce { state.copy(competitionType = value) }
    }

    fun onTournamentChanged(value: String) = intent {
        reduce { state.copy(tournament = value) }
    }

    fun onScheduledToggled(enabled: Boolean) = intent {
        reduce { state.copy(isScheduled = enabled, scheduledAt = if (!enabled) null else state.scheduledAt) }
    }

    fun onScheduledAtChanged(epochMs: Long) = intent {
        reduce { state.copy(scheduledAt = epochMs) }
    }

    fun startMatch() = intent {
        val surface = state.selectedSurface ?: return@intent
        val matchFormat = state.selectedMatchFormat ?: return@intent
        val thirdSetRule = if (matchFormat == MatchFormat.BEST_OF_3)
            state.selectedThirdSetRule ?: ThirdSetRule.FULL_ADVANTAGE
        else ThirdSetRule.FULL_ADVANTAGE

        reduce { state.copy(isLoading = true) }

        val now = System.currentTimeMillis()
        val scheduledAtMs = state.scheduledAt
        val isPlanned = state.isScheduled && scheduledAtMs != null && scheduledAtMs > now
        val session = Session(
            surface = surface,
            format = SessionFormat(matchFormat = matchFormat, thirdSetRule = thirdSetRule),
            opponent = state.opponent.takeIf { it.isNotBlank() },
            competitionType = state.competitionType.takeIf { it.isNotBlank() },
            tournament = state.tournament.takeIf { it.isNotBlank() },
            status = if (isPlanned) SessionStatus.PLANNED else SessionStatus.ACTIVE,
            scheduledAt = if (isPlanned) scheduledAtMs else null,
            createdAt = now,
            updatedAt = now
        )

        when (val result = sessionRepository.createSession(session)) {
            is AppResult.Success -> {
                reduce { state.copy(isLoading = false) }
                val createdSession = result.data
                val createdScheduledAt = createdSession.scheduledAt
                if (isPlanned && createdScheduledAt != null) {
                    val triggerMs = createdScheduledAt - 2 * 60 * 60 * 1000L
                    notificationScheduler.schedulePreMatchReminder(createdSession.id, triggerMs)
                }
                if (isPlanned) {
                    postSideEffect(NewMatchSideEffect.SessionPlanned(createdSession.id))
                } else {
                    postSideEffect(NewMatchSideEffect.SessionStarted(createdSession.id))
                }
            }
            is AppResult.Error -> {
                reduce { state.copy(isLoading = false) }
                postSideEffect(NewMatchSideEffect.ShowError("Impossible de créer la session"))
            }
            AppResult.Loading -> {}
        }
    }
}

data class NewMatchUiState(
    val selectedSurface: String? = null,
    val selectedMatchFormat: MatchFormat? = null,
    val selectedThirdSetRule: ThirdSetRule? = null,
    val opponent: String = "",
    val competitionType: String = "",
    val tournament: String = "",
    val isLoading: Boolean = false,
    val isScheduled: Boolean = false,
    val scheduledAt: Long? = null
) {
    val canStartMatch: Boolean get() =
        selectedSurface != null && selectedMatchFormat != null &&
        (selectedMatchFormat == MatchFormat.BEST_OF_1 || selectedThirdSetRule != null) &&
        (if (isScheduled) scheduledAt != null && scheduledAt > System.currentTimeMillis() else true)
}

sealed class NewMatchSideEffect {
    data class SessionStarted(val sessionId: Long) : NewMatchSideEffect()
    data class SessionPlanned(val sessionId: Long) : NewMatchSideEffect()
    data class ShowError(val message: String) : NewMatchSideEffect()
}
