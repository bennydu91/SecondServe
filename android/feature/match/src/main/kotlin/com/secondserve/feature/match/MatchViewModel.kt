package com.secondserve.feature.match

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondserve.domain.AppResult
import com.secondserve.domain.analysis.AnalysisScheduler
import com.secondserve.domain.event.DataLayerEventBus
import com.secondserve.domain.model.CoachingResult
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.model.Player
import com.secondserve.domain.repository.ScoreRepository
import com.secondserve.domain.repository.SessionRepository
import com.secondserve.domain.sync.SyncScheduler
import com.secondserve.domain.usecase.match.CloseMatchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MatchViewModel @Inject constructor(
    private val scoreRepository: ScoreRepository,
    private val sessionRepository: SessionRepository,
    private val closeMatchUseCase: CloseMatchUseCase,
    private val syncScheduler: SyncScheduler,
    private val analysisScheduler: AnalysisScheduler,
    private val dataLayerEventBus: DataLayerEventBus,
    private val coachingCachePrefetcher: CoachingCachePrefetcher,
    private val coachingResolver: CoachingResolver,
    savedStateHandle: SavedStateHandle
) : ViewModel(), ContainerHost<MatchUiState, MatchSideEffect> {

    val sessionId: Long = savedStateHandle.get<Long>(ARG_SESSION_ID) ?: 0L

    override val container = container<MatchUiState, MatchSideEffect>(MatchUiState())

    val currentScore = scoreRepository.latestScore

    init {
        coachingCachePrefetcher.initMatch(sessionId)

        viewModelScope.launch {
            val session = sessionRepository.getSessionById(sessionId)
            if (session != null) {
                intent {
                    reduce {
                        state.copy(opponentName = session.opponent, sessionStartedAt = session.createdAt)
                    }
                }
            }
        }

        viewModelScope.launch {
            dataLayerEventBus.closeSessionRequests.collect {
                onCloseRequested()
            }
        }

        viewModelScope.launch {
            dataLayerEventBus.gameOverEvents.collect { score ->
                val result = coachingResolver.resolve(sessionId, score)
                result?.let { advice ->
                    // coachingAdviceSeq s'incrémente à chaque changement de côté pour garantir
                    // une nouvelle émission d'état (même si le texte est identique) et permettre
                    // à l'UI de ré-animer la carte → chaque conseil est visiblement annoncé.
                    intent { reduce { state.copy(coachingAdvice = advice, coachingAdviceSeq = state.coachingAdviceSeq + 1) } }
                    coachingCachePrefetcher.refreshPostChangeover(sessionId, score)
                }
            }
        }

        // Déroulé jeu-par-jeu du set en cours + momentum : dérivés côté client à partir du flux
        // de score déjà observé (pas de changement du protocole watch↔téléphone ni de
        // TennisScoreEngine). Limite connue : si l'écran est rouvert en cours de set, le
        // déroulé/momentum repartent de zéro (pas d'historique persistant).
        viewModelScope.launch {
            var previous: MatchScore? = null
            scoreRepository.latestScore.collect { score ->
                val prev = previous
                previous = score
                if (score == null) return@collect

                if (prev == null) return@collect

                val currentLog = container.stateFlow.value.currentSetGameLog
                val newLog = when {
                    score.completedSets.size > prev.completedSets.size -> emptyList()
                    score.currentSetGamesA > prev.currentSetGamesA -> currentLog + Player.A
                    score.currentSetGamesB > prev.currentSetGamesB -> currentLog + Player.B
                    else -> currentLog
                }
                if (newLog != currentLog) {
                    val momentum = if (newLog.isEmpty()) {
                        50
                    } else {
                        newLog.count { it == Player.A } * 100 / newLog.size
                    }
                    intent {
                        reduce { state.copy(currentSetGameLog = newLog, momentumPercent = momentum) }
                    }
                }
            }
        }
    }

    fun onCloseRequested() = intent {
        reduce { state.copy(showCloseDialog = true) }
    }

    fun onCloseDialogDismissed() = intent {
        reduce { state.copy(showCloseDialog = false) }
    }

    fun onFeelingRatingSelected(rating: Int) = intent {
        reduce { state.copy(feelingRating = rating) }
    }

    fun onFeelingCommentChanged(comment: String) = intent {
        reduce { state.copy(feelingComment = comment) }
    }

    fun confirmClose() = intent {
        reduce { state.copy(isClosing = true, showCloseDialog = false) }
        val score = scoreRepository.latestScore.value ?: MatchScore()
        val result = closeMatchUseCase(
            sessionId = sessionId,
            finalScore = score,
            feelingRating = state.feelingRating,
            feelingComment = state.feelingComment.takeIf { it.isNotBlank() }
        )
        when (result) {
            is AppResult.Success -> {
                syncScheduler.scheduleImmediate()
                analysisScheduler.schedule(sessionId)
                postSideEffect(MatchSideEffect.SessionClosed)
            }
            is AppResult.Error -> {
                Timber.e(result.exception, "MatchViewModel: closeSession failed")
                reduce { state.copy(isClosing = false) }
                postSideEffect(MatchSideEffect.ShowError("Impossible de clôturer la session"))
            }
            AppResult.Loading -> {}
        }
    }

    companion object {
        const val ARG_SESSION_ID = "sessionId"
    }
}

data class MatchUiState(
    val showCloseDialog: Boolean = false,
    val feelingRating: Int? = null,
    val feelingComment: String = "",
    val isClosing: Boolean = false,
    val coachingAdvice: CoachingResult? = null,
    val coachingAdviceSeq: Int = 0,
    val opponentName: String? = null,
    val sessionStartedAt: Long = 0L,
    val currentSetGameLog: List<Player> = emptyList(),
    val momentumPercent: Int = 50
)

sealed class MatchSideEffect {
    data object SessionClosed : MatchSideEffect()
    data class ShowError(val message: String) : MatchSideEffect()
}
