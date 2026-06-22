package com.secondserve.feature.history

import androidx.lifecycle.ViewModel
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.Session
import com.secondserve.domain.model.SessionFormat
import com.secondserve.domain.model.SessionStatus
import com.secondserve.domain.model.ThirdSetRule
import com.secondserve.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

@HiltViewModel
class AddRetroSessionViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel(), ContainerHost<AddRetroSessionUiState, AddRetroSessionSideEffect> {

    override val container = container<AddRetroSessionUiState, AddRetroSessionSideEffect>(
        AddRetroSessionUiState()
    )

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

    fun onScoreTextChanged(value: String) = intent {
        reduce { state.copy(scoreText = value) }
    }

    fun onResultSelected(result: String) = intent {
        reduce { state.copy(selectedResult = result) }
    }

    fun onMatchDateSelected(epochMillis: Long) = intent {
        reduce { state.copy(matchDateMillis = epochMillis) }
    }

    fun submit() = intent {
        val surface = state.selectedSurface ?: return@intent
        val matchFormat = state.selectedMatchFormat ?: return@intent
        val result = state.selectedResult ?: return@intent
        val dateMillis = state.matchDateMillis ?: return@intent

        val thirdSetRule = if (matchFormat == MatchFormat.BEST_OF_3)
            state.selectedThirdSetRule ?: ThirdSetRule.FULL_ADVANTAGE
        else ThirdSetRule.FULL_ADVANTAGE

        reduce { state.copy(isLoading = true) }

        val session = Session(
            surface = surface,
            format = SessionFormat(matchFormat = matchFormat, thirdSetRule = thirdSetRule),
            opponent = state.opponent.takeIf { it.isNotBlank() },
            competitionType = state.competitionType.takeIf { it.isNotBlank() },
            tournament = state.tournament.takeIf { it.isNotBlank() },
            status = SessionStatus.COMPLETED,
            result = result,
            scoreText = state.scoreText.takeIf { it.isNotBlank() },
            createdAt = dateMillis,
            updatedAt = System.currentTimeMillis()
        )

        when (sessionRepository.createCompletedSession(session)) {
            is AppResult.Success -> {
                reduce { state.copy(isLoading = false) }
                postSideEffect(AddRetroSessionSideEffect.SessionCreated)
            }
            is AppResult.Error -> {
                reduce { state.copy(isLoading = false) }
                postSideEffect(AddRetroSessionSideEffect.ShowError("Impossible d'enregistrer la session"))
            }
            AppResult.Loading -> {}
        }
    }
}
