package com.secondserve.wear.presentation.start

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondserve.data.wearable.DataLayerClient
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.ThirdSetRule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class StartMatchViewModel @Inject constructor(
    private val dataLayerClient: DataLayerClient
) : ViewModel(), ContainerHost<StartMatchUiState, StartMatchSideEffect> {

    override val container = container<StartMatchUiState, StartMatchSideEffect>(StartMatchUiState())

    private var phoneResponseTimeoutJob: Job? = null

    fun selectFormat(matchFormat: MatchFormat) = intent {
        reduce { state.copy(matchFormat = matchFormat) }
    }

    fun selectThirdSetRule(rule: ThirdSetRule) = intent {
        reduce { state.copy(thirdSetRule = rule) }
    }

    fun confirmStart() = intent {
        val format = state.matchFormat
        val rule = state.thirdSetRule
        reduce { state.copy(isLoading = true, errorMessage = null) }

        val result = dataLayerClient.sendStartSessionRequest(format, rule)
        when (result) {
            is AppResult.Success -> {
                Timber.d("StartMatchViewModel: start session request sent to phone")
                phoneResponseTimeoutJob?.cancel()
                phoneResponseTimeoutJob = viewModelScope.launch {
                    delay(PHONE_RESPONSE_TIMEOUT_MS)
                    Timber.d("StartMatchViewModel: timeout — téléphone n'a pas répondu, mode dégradé")
                    intent {
                        reduce { state.copy(isLoading = false) }
                        postSideEffect(StartMatchSideEffect.StartLocal(format, rule))
                    }
                }
                postSideEffect(StartMatchSideEffect.StartRemote)
            }
            is AppResult.Error -> {
                Timber.d(
                    "StartMatchViewModel: phone unavailable, mode dégradé — %s",
                    result.exception.message
                )
                reduce { state.copy(isLoading = false) }
                postSideEffect(StartMatchSideEffect.StartLocal(format, rule))
            }
            is AppResult.Loading -> {
                // Not expected for this call
                reduce { state.copy(isLoading = false) }
            }
        }
    }

    companion object {
        const val PHONE_RESPONSE_TIMEOUT_MS = 30_000L
    }
}

data class StartMatchUiState(
    val matchFormat: MatchFormat = MatchFormat.BEST_OF_3,
    val thirdSetRule: ThirdSetRule = ThirdSetRule.FULL_ADVANTAGE,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed class StartMatchSideEffect {
    data class StartLocal(
        val matchFormat: MatchFormat,
        val thirdSetRule: ThirdSetRule
    ) : StartMatchSideEffect()

    data object StartRemote : StartMatchSideEffect()
}
