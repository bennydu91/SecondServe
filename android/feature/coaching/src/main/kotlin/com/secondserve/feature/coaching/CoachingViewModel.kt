package com.secondserve.feature.coaching

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondserve.core.ai.InferenceEngine
import com.secondserve.core.ai.di.VpsMistralEngine
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.CoachingSynthesis
import com.secondserve.domain.model.MatchContextProfile
import com.secondserve.domain.model.Session
import com.secondserve.domain.repository.CoachingRepository
import com.secondserve.domain.repository.PlayerProfileRepository
import com.secondserve.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

@HiltViewModel
class CoachingViewModel @Inject constructor(
    private val coachingRepository: CoachingRepository,
    private val sessionRepository: SessionRepository,
    private val playerProfileRepository: PlayerProfileRepository,
    @VpsMistralEngine private val vpsMistralEngine: InferenceEngine
) : ViewModel(), ContainerHost<CoachingUiState, CoachingSideEffect> {

    override val container = container<CoachingUiState, CoachingSideEffect>(CoachingUiState(isLoading = true))

    init {
        viewModelScope.launch {
            combine(
                coachingRepository.observeLatestSynthesis(),
                coachingRepository.observeAllAnalyses()
            ) { synthesis, analyses ->
                CoachingUiState(isLoading = false, synthesis = synthesis, analyses = analyses)
            }
            .catch { e -> intent { reduce { state.copy(isLoading = false, error = e.message) } } }
            .collect { newState -> intent { reduce { newState } } }
        }
    }

    fun generateNow() = intent {
        reduce { state.copy(synthesisInProgress = true, error = null) }
        try {
            val lastSynthesis = coachingRepository.getLatestSynthesis()
            val afterMs = lastSynthesis?.generatedAt ?: 0L
            var sessions = sessionRepository.getCompletedSince(afterMs)
            if (sessions.isEmpty()) {
                sessions = sessionRepository.getCompletedSince(0L).takeLast(3)
            }
            val profile = playerProfileRepository.buildMatchContextProfile()
            val prompt = buildSynthesisPrompt(sessions, profile, lastSynthesis)
            when (val result = vpsMistralEngine.generate(prompt)) {
                is AppResult.Success -> coachingRepository.saveSynthesis(result.data, sessions.size)
                is AppResult.Error -> reduce { state.copy(error = "Génération échouée — vérifiez la connexion") }
                AppResult.Loading -> Unit
            }
        } catch (e: Exception) {
            reduce { state.copy(error = e.message ?: "Erreur inconnue") }
        } finally {
            reduce { state.copy(synthesisInProgress = false) }
        }
    }

    private fun buildSynthesisPrompt(
        sessions: List<Session>,
        profile: MatchContextProfile,
        lastSynthesis: CoachingSynthesis?
    ): String {
        val sessionsDesc = sessions.joinToString("\n") { s ->
            "- ${s.surface} / ${s.format.matchFormat.name} / Résultat: ${s.result ?: "inconnu"} / Score: ${s.scoreText ?: "?"}"
        }
        val lastSynthesisLine = if (lastSynthesis != null) {
            "\n\nSynthèse précédente (résumé) :\n${lastSynthesis.content.take(300)}..."
        } else ""
        val axesText = profile.activeWorkAxes.joinToString(", ").ifEmpty { "aucun" }

        return """
Tu es un coach tennis. Génère une synthèse transversale sur ${sessions.size} match(s) récent(s). Réponse en 5-7 phrases maximum.

Matchs analysés :
$sessionsDesc

Profil joueur :
- Classement FFT : ${profile.fftSeries ?: "non renseigné"}
- Style de jeu : ${profile.playStyle ?: "non renseigné"}
- Axes de travail actifs : $axesText$lastSynthesisLine

Identifie : les patterns récurrents sur ces matchs, l'évolution depuis la dernière synthèse (si disponible), l'axe d'amélioration prioritaire, une recommandation structurée. Cite les surfaces et résultats. Sois précis, pas générique.
        """.trimIndent()
    }
}
