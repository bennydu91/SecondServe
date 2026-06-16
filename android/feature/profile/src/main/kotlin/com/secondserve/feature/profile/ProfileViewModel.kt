package com.secondserve.feature.profile

import androidx.lifecycle.ViewModel
import com.secondserve.domain.AppResult
import com.secondserve.domain.constants.FftConstants
import com.secondserve.domain.model.RankingEntry
import com.secondserve.domain.repository.PlayerProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container
import org.orbitmvi.orbit.syntax.simple.intent
import org.orbitmvi.orbit.syntax.simple.postSideEffect
import org.orbitmvi.orbit.syntax.simple.reduce
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: PlayerProfileRepository
) : ViewModel(), ContainerHost<ProfileUiState, ProfileSideEffect> {

    override val container = container<ProfileUiState, ProfileSideEffect>(ProfileUiState())

    init {
        loadProfile()
    }

    fun loadProfile() = intent {
        reduce { state.copy(isLoading = true) }
        when (val result = profileRepository.getProfile()) {
            is AppResult.Success -> reduce {
                state.copy(
                    isLoading = false,
                    currentSeries = result.data?.currentSeries,
                    currentPoints = result.data?.currentPoints
                )
            }
            is AppResult.Error -> reduce {
                state.copy(isLoading = false, error = "Impossible de charger le profil")
            }
            AppResult.Loading -> {}
        }
        profileRepository.getRankingHistory().collect { history ->
            reduce { state.copy(rankingHistory = history) }
        }
    }

    fun saveRanking(series: String, points: Int) = intent {
        if (series !in FftConstants.VALID_SERIES) {
            postSideEffect(ProfileSideEffect.ShowError("Série FFT invalide : $series"))
            return@intent
        }
        if (points <= 0) {
            postSideEffect(ProfileSideEffect.ShowError("Le nombre de points doit être positif"))
            return@intent
        }
        reduce { state.copy(isSaving = true) }
        when (val result = profileRepository.saveRanking(series, points)) {
            is AppResult.Success -> {
                reduce { state.copy(isSaving = false) }
                postSideEffect(ProfileSideEffect.RankingSaved)
            }
            is AppResult.Error -> {
                reduce { state.copy(isSaving = false) }
                postSideEffect(ProfileSideEffect.ShowError("Erreur lors de la sauvegarde"))
            }
            AppResult.Loading -> {}
        }
    }
}

data class ProfileUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val currentSeries: String? = null,
    val currentPoints: Int? = null,
    val rankingHistory: List<RankingEntry> = emptyList(),
    val error: String? = null
)

sealed class ProfileSideEffect {
    data object RankingSaved : ProfileSideEffect()
    data class ShowError(val message: String) : ProfileSideEffect()
}
