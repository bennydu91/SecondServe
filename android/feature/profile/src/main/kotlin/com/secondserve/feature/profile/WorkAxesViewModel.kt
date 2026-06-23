package com.secondserve.feature.profile

import androidx.lifecycle.ViewModel
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.AxisSuggestion
import com.secondserve.domain.model.MAX_WORK_AXES
import com.secondserve.domain.model.WorkAxis
import com.secondserve.domain.repository.WorkAxisRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

@HiltViewModel
class WorkAxesViewModel @Inject constructor(
    private val workAxisRepository: WorkAxisRepository
) : ViewModel(), ContainerHost<WorkAxesUiState, WorkAxesSideEffect> {

    override val container = container<WorkAxesUiState, WorkAxesSideEffect>(WorkAxesUiState())

    init {
        collectWorkAxes()
        collectSuggestions()
        tryGenerateSuggestionsIfNeeded()
    }

    private fun collectWorkAxes() = intent {
        workAxisRepository.getWorkAxes().collect { axes ->
            reduce {
                state.copy(
                    workAxes = axes,
                    isAtMaxCapacity = axes.size >= MAX_WORK_AXES
                )
            }
        }
    }

    private fun collectSuggestions() = intent {
        workAxisRepository.observePendingSuggestions().collect { suggestions ->
            reduce { state.copy(pendingSuggestions = suggestions) }
        }
    }

    private fun tryGenerateSuggestionsIfNeeded() = intent {
        if (state.isGeneratingSuggestions) return@intent
        if (workAxisRepository.hasPendingSuggestions()) return@intent
        if (!workAxisRepository.hasCoachingData()) return@intent
        reduce { state.copy(isGeneratingSuggestions = true, suggestionsError = null) }
        try {
            val result = workAxisRepository.generateAndSaveSuggestions()
            if (result is AppResult.Error) {
                val isNoData = result.exception.message == "No coaching data available"
                if (!isNoData) reduce { state.copy(suggestionsError = "Suggestions IA indisponibles") }
            }
        } finally {
            reduce { state.copy(isGeneratingSuggestions = false) }
        }
    }

    fun createWorkAxis(title: String) = intent {
        if (state.isAtMaxCapacity) {
            postSideEffect(WorkAxesSideEffect.ShowError("Maximum $MAX_WORK_AXES axes actifs atteint"))
            return@intent
        }
        if (title.isBlank()) {
            postSideEffect(WorkAxesSideEffect.ShowError("Le titre ne peut pas être vide"))
            return@intent
        }
        reduce { state.copy(isSaving = true) }
        when (workAxisRepository.createWorkAxis(title.trim())) {
            is AppResult.Success -> {
                reduce { state.copy(isSaving = false) }
                postSideEffect(WorkAxesSideEffect.WorkAxisCreated)
            }
            is AppResult.Error -> {
                reduce { state.copy(isSaving = false) }
                postSideEffect(WorkAxesSideEffect.ShowError("Erreur lors de la création"))
            }
            AppResult.Loading -> reduce { state.copy(isSaving = false) }
        }
    }

    fun updateWorkAxis(id: Long, newTitle: String) = intent {
        if (newTitle.isBlank()) {
            postSideEffect(WorkAxesSideEffect.ShowError("Le titre ne peut pas être vide"))
            return@intent
        }
        reduce { state.copy(isSaving = true) }
        when (workAxisRepository.updateWorkAxis(id, newTitle.trim())) {
            is AppResult.Success -> {
                reduce { state.copy(isSaving = false) }
                postSideEffect(WorkAxesSideEffect.WorkAxisUpdated)
            }
            is AppResult.Error -> {
                reduce { state.copy(isSaving = false) }
                postSideEffect(WorkAxesSideEffect.ShowError("Erreur lors de la modification"))
            }
            AppResult.Loading -> reduce { state.copy(isSaving = false) }
        }
    }

    fun deleteWorkAxis(id: Long) = intent {
        when (workAxisRepository.deleteWorkAxis(id)) {
            is AppResult.Success ->
                postSideEffect(WorkAxesSideEffect.WorkAxisDeleted)
            is AppResult.Error ->
                postSideEffect(WorkAxesSideEffect.ShowError("Erreur lors de la suppression"))
            AppResult.Loading -> {}
        }
    }

    fun acceptSuggestion(id: Long) = intent {
        if (state.isAtMaxCapacity) {
            postSideEffect(WorkAxesSideEffect.ShowError("Maximum $MAX_WORK_AXES axes actifs atteint"))
            return@intent
        }
        when (workAxisRepository.acceptSuggestion(id)) {
            is AppResult.Success -> postSideEffect(WorkAxesSideEffect.SuggestionAccepted)
            is AppResult.Error -> postSideEffect(WorkAxesSideEffect.ShowError("Erreur lors de l'acceptation"))
            AppResult.Loading -> Unit
        }
    }

    fun ignoreSuggestion(id: Long) = intent {
        if (workAxisRepository.ignoreSuggestion(id) is AppResult.Error) {
            postSideEffect(WorkAxesSideEffect.ShowError("Impossible d'ignorer la suggestion"))
        }
    }
}

data class WorkAxesUiState(
    val workAxes: List<WorkAxis> = emptyList(),
    val isAtMaxCapacity: Boolean = false,
    val isSaving: Boolean = false,
    val pendingSuggestions: List<AxisSuggestion> = emptyList(),
    val isGeneratingSuggestions: Boolean = false,
    val suggestionsError: String? = null
)

sealed class WorkAxesSideEffect {
    data object WorkAxisCreated : WorkAxesSideEffect()
    data object WorkAxisUpdated : WorkAxesSideEffect()
    data object WorkAxisDeleted : WorkAxesSideEffect()
    data object SuggestionAccepted : WorkAxesSideEffect()
    data class ShowError(val message: String) : WorkAxesSideEffect()
}
