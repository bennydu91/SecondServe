package com.secondserve.feature.profile

import androidx.lifecycle.ViewModel
import com.secondserve.data.local.PlayerDataStore
import com.secondserve.domain.AppResult
import com.secondserve.domain.constants.FftConstants
import com.secondserve.domain.model.RankingEntry
import com.secondserve.domain.repository.PlayerProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: PlayerProfileRepository,
    private val playerDataStore: PlayerDataStore
) : ViewModel(), ContainerHost<ProfileUiState, ProfileSideEffect> {

    override val container = container<ProfileUiState, ProfileSideEffect>(ProfileUiState())

    init {
        loadProfile()
        collectRankingHistory()
        collectMatchSessionCount()
        loadFftLicense()
    }

    fun loadProfile() = intent {
        reduce { state.copy(isLoading = true) }
        when (val result = profileRepository.getProfile()) {
            is AppResult.Success -> reduce {
                state.copy(
                    isLoading = false,
                    displayName = result.data?.displayName,
                    club = result.data?.club,
                    currentSeries = result.data?.currentSeries,
                    currentPoints = result.data?.currentPoints,
                    playStyle = result.data?.playStyle,
                    preferredSurfaces = result.data?.preferredSurfaces ?: emptyList(),
                    coachInstruction1 = result.data?.coachInstruction1,
                    coachInstruction2 = result.data?.coachInstruction2,
                    coachInstruction3 = result.data?.coachInstruction3
                )
            }
            is AppResult.Error -> {
                reduce { state.copy(isLoading = false, error = "Impossible de charger le profil") }
                postSideEffect(ProfileSideEffect.ShowError("Impossible de charger le profil"))
            }
            AppResult.Loading -> {}
        }
    }

    private fun collectRankingHistory() = intent {
        profileRepository.getRankingHistory().collect { history ->
            reduce { state.copy(rankingHistory = history) }
        }
    }

    private fun collectMatchSessionCount() = intent {
        profileRepository.observeMatchSessionCount().collect { count ->
            reduce { state.copy(matchSessionCount = count) }
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
                reduce { state.copy(isSaving = false, currentSeries = series, currentPoints = points) }
                postSideEffect(ProfileSideEffect.RankingSaved)
            }
            is AppResult.Error -> {
                reduce { state.copy(isSaving = false) }
                postSideEffect(ProfileSideEffect.ShowError("Erreur lors de la sauvegarde"))
            }
            AppResult.Loading -> {}
        }
    }

    fun saveProfileDetails(
        playStyle: String?,
        preferredSurfaces: List<String>,
        coachInstruction1: String?,
        coachInstruction2: String?,
        coachInstruction3: String?
    ) = intent {
        reduce { state.copy(isDetailsSaving = true) }
        when (val result = profileRepository.saveProfileDetails(
            playStyle, preferredSurfaces,
            coachInstruction1, coachInstruction2, coachInstruction3
        )) {
            is AppResult.Success -> {
                reduce {
                    state.copy(
                        isDetailsSaving = false,
                        playStyle = playStyle,
                        preferredSurfaces = preferredSurfaces,
                        coachInstruction1 = coachInstruction1,
                        coachInstruction2 = coachInstruction2,
                        coachInstruction3 = coachInstruction3
                    )
                }
                postSideEffect(ProfileSideEffect.ProfileDetailsSaved)
            }
            is AppResult.Error -> {
                reduce { state.copy(isDetailsSaving = false) }
                postSideEffect(ProfileSideEffect.ShowError("Erreur lors de la sauvegarde"))
            }
            AppResult.Loading -> {}
        }
    }

    fun saveIdentity(displayName: String?, club: String?) = intent {
        reduce { state.copy(isIdentitySaving = true) }
        when (profileRepository.saveIdentity(displayName, club)) {
            is AppResult.Success -> {
                reduce { state.copy(isIdentitySaving = false, displayName = displayName, club = club) }
                postSideEffect(ProfileSideEffect.IdentitySaved)
            }
            is AppResult.Error -> {
                reduce { state.copy(isIdentitySaving = false) }
                postSideEffect(ProfileSideEffect.ShowError("Erreur lors de la sauvegarde"))
            }
            AppResult.Loading -> {}
        }
    }

    fun saveFftLicense(licenseNumber: String) = intent {
        playerDataStore.saveFftLicenseNumber(licenseNumber)
        reduce { state.copy(fftLicenseNumber = licenseNumber) }
    }

    fun loadFftLicense() = intent {
        val license = playerDataStore.getFftLicenseNumber()
        reduce { state.copy(fftLicenseNumber = license) }
    }
}

data class ProfileUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDetailsSaving: Boolean = false,
    val isIdentitySaving: Boolean = false,
    val displayName: String? = null,
    val club: String? = null,
    val currentSeries: String? = null,
    val currentPoints: Int? = null,
    val rankingHistory: List<RankingEntry> = emptyList(),
    val matchSessionCount: Int = 0,
    val playStyle: String? = null,
    val preferredSurfaces: List<String> = emptyList(),
    val coachInstruction1: String? = null,
    val coachInstruction2: String? = null,
    val coachInstruction3: String? = null,
    val fftLicenseNumber: String? = null,
    val error: String? = null
)

sealed class ProfileSideEffect {
    data object RankingSaved : ProfileSideEffect()
    data object ProfileDetailsSaved : ProfileSideEffect()
    data object IdentitySaved : ProfileSideEffect()
    data class ShowError(val message: String) : ProfileSideEffect()
}
