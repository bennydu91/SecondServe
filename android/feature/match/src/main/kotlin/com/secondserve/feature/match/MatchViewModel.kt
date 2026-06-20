package com.secondserve.feature.match

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondserve.domain.AppResult
import com.secondserve.domain.event.DataLayerEventBus
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.repository.ScoreRepository
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
    private val closeMatchUseCase: CloseMatchUseCase,
    private val syncScheduler: SyncScheduler,
    private val dataLayerEventBus: DataLayerEventBus,
    savedStateHandle: SavedStateHandle
) : ViewModel(), ContainerHost<MatchUiState, MatchSideEffect> {

    val sessionId: Long = savedStateHandle.get<Long>(ARG_SESSION_ID) ?: 0L

    override val container = container<MatchUiState, MatchSideEffect>(MatchUiState())

    val currentScore = scoreRepository.latestScore

    init {
        viewModelScope.launch {
            dataLayerEventBus.closeSessionRequests.collect {
                onCloseRequested()
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
    val isClosing: Boolean = false
)

sealed class MatchSideEffect {
    object SessionClosed : MatchSideEffect()
    data class ShowError(val message: String) : MatchSideEffect()
}
