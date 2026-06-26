package com.secondserve.wear.presentation.match

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondserve.data.wearable.DataLayerClient
import com.secondserve.domain.AppResult
import com.secondserve.wear.monitoring.WearMonitoringQueue
import com.secondserve.domain.engine.EngineEvent
import com.secondserve.domain.engine.TennisScoreEngine
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.model.Player
import com.secondserve.domain.model.SessionFormat
import com.secondserve.domain.model.ThirdSetRule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ScoreViewModel @Inject constructor(
    private val dataLayerClient: DataLayerClient,
    private val monitoringQueue: WearMonitoringQueue,
    savedStateHandle: SavedStateHandle
) : ViewModel(), ContainerHost<ScoreUiState, ScoreSideEffect> {

    override val container = container<ScoreUiState, ScoreSideEffect>(ScoreUiState())

    private val matchFormat: MatchFormat = savedStateHandle
        .get<String>(ARG_MATCH_FORMAT)
        ?.let { raw ->
            runCatching { MatchFormat.valueOf(raw) }
                .onFailure { Timber.w("ScoreViewModel: invalid matchFormat '%s', using BEST_OF_3", raw) }
                .getOrNull()
        }
        ?: MatchFormat.BEST_OF_3

    private val thirdSetRule: ThirdSetRule = savedStateHandle
        .get<String>(ARG_THIRD_SET_RULE)
        ?.let { raw ->
            runCatching { ThirdSetRule.valueOf(raw) }
                .onFailure { Timber.w("ScoreViewModel: invalid thirdSetRule '%s', using FULL_ADVANTAGE", raw) }
                .getOrNull()
        }
        ?: ThirdSetRule.FULL_ADVANTAGE

    private val engine = TennisScoreEngine(SessionFormat(matchFormat, thirdSetRule))
    private var pointCount = 0

    fun recordPoint(scorer: Player) = intent {
        if (engine.currentScore.isMatchOver) return@intent
        val event = engine.recordPoint(scorer)
        val changeover = event.isChangeover()
        pointCount++
        val snapshot = engine.currentScore
        reduce {
            state.copy(
                score = snapshot,
                canUndo = pointCount > 0
            )
        }
        viewModelScope.launch { sendScoreEvent(snapshot) }
        if (changeover) viewModelScope.launch { sendGameOver(snapshot) }
        monitoringQueue.enqueueEvent("wear.score.updated", mapOf("points" to pointCount.toString()))
    }

    fun undo() = intent {
        if (engine.currentScore.isMatchOver) return@intent
        if (pointCount <= 0) return@intent
        val undone = engine.undo()
        if (undone) {
            pointCount--
            val snapshot = engine.currentScore
            reduce {
                state.copy(
                    score = snapshot,
                    canUndo = pointCount > 0
                )
            }
            viewModelScope.launch { sendScoreEvent(snapshot) }
        }
    }

    fun requestClose() = intent {
        val result = dataLayerClient.sendCloseRequest()
        if (result is AppResult.Error) {
            Timber.d("ScoreViewModel: sendCloseRequest failed — %s", result.exception.message)
        }
        postSideEffect(ScoreSideEffect.Close)
    }

    // Annule le point final ayant déclenché la fin du match (action explicite avec confirmation UI).
    // Distinct de undo() qui est réservé aux points en cours de match.
    fun cancelMatchOver() = intent {
        if (!engine.currentScore.isMatchOver) return@intent
        val undone = engine.undo()
        if (undone) {
            pointCount--
            val snapshot = engine.currentScore
            reduce {
                state.copy(
                    score = snapshot,
                    canUndo = pointCount > 0
                )
            }
            viewModelScope.launch { sendScoreEvent(snapshot) }
        }
    }

    private suspend fun sendScoreEvent(score: MatchScore) {
        val result = dataLayerClient.sendScoreEvent(score)
        val connected = result !is AppResult.Error
        if (!connected) Timber.d("ScoreViewModel: sendScoreEvent failed — %s", (result as AppResult.Error).exception.message)
        intent { reduce { state.copy(phoneConnected = connected) } }
    }

    private suspend fun sendGameOver(score: MatchScore) {
        val result = dataLayerClient.sendGameOver(score)
        if (result is AppResult.Error) {
            Timber.d("ScoreViewModel: sendGameOver failed — %s", result.exception.message)
        }
    }

    companion object {
        const val ARG_MATCH_FORMAT = "matchFormat"
        const val ARG_THIRD_SET_RULE = "thirdSetRule"
    }
}

data class ScoreUiState(
    val score: MatchScore = MatchScore(),
    val canUndo: Boolean = false,
    val phoneConnected: Boolean = true
)

sealed class ScoreSideEffect {
    data object Close : ScoreSideEffect()
}

private fun EngineEvent.isChangeover(): Boolean = when (this) {
    is EngineEvent.PointScored -> false
    is EngineEvent.GameWon -> this.changeover
    is EngineEvent.SetWon -> this.changeover
    is EngineEvent.MatchOver -> false
}
