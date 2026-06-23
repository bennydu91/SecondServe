package com.secondserve.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.secondserve.core.ai.InferenceEngine
import com.secondserve.core.ai.di.VpsMistralEngine
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.CoachingSynthesis
import com.secondserve.domain.model.MatchContextProfile
import com.secondserve.domain.model.Session
import com.secondserve.domain.repository.CoachingRepository
import com.secondserve.domain.repository.PlayerProfileRepository
import com.secondserve.domain.repository.SessionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class SynthesisWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val coachingRepository: CoachingRepository,
    private val sessionRepository: SessionRepository,
    private val playerProfileRepository: PlayerProfileRepository,
    @VpsMistralEngine private val vpsMistralEngine: InferenceEngine
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runWork()

    internal suspend fun runWork(): Result {
        val lastSynthesis = coachingRepository.getLatestSynthesis()
        val afterMs = lastSynthesis?.generatedAt ?: 0L

        val count = try {
            sessionRepository.countCompletedSince(afterMs)
        } catch (e: Exception) {
            Timber.e(e, "SynthesisWorker: countCompletedSince failed")
            return Result.retry()
        }

        if (count < 3) {
            Timber.d("SynthesisWorker: only %d new sessions, skipping (need 3)", count)
            return Result.success()
        }

        val sessions = try {
            sessionRepository.getCompletedSince(afterMs)
        } catch (e: Exception) {
            Timber.e(e, "SynthesisWorker: getCompletedSince failed")
            return Result.retry()
        }

        val profile = try {
            playerProfileRepository.buildMatchContextProfile()
        } catch (e: Exception) {
            Timber.e(e, "SynthesisWorker: buildMatchContextProfile failed")
            return Result.failure()
        }

        val prompt = buildSynthesisPrompt(sessions, profile, lastSynthesis)

        return when (val result = vpsMistralEngine.generate(prompt)) {
            is AppResult.Success -> {
                when (val saveResult = coachingRepository.saveSynthesis(result.data, sessions.size)) {
                    is AppResult.Success -> {
                        Timber.d("SynthesisWorker: synthesis saved (%d sessions)", sessions.size)
                        Result.success()
                    }
                    is AppResult.Error -> {
                        Timber.e(saveResult.exception, "SynthesisWorker: DB write failed — will retry")
                        Result.retry()
                    }
                    AppResult.Loading -> Result.retry()
                }
            }
            is AppResult.Error -> {
                Timber.e(result.exception, "SynthesisWorker: VPS error — will retry")
                Result.retry()
            }
            AppResult.Loading -> Result.failure()
        }
    }
}

internal fun buildSynthesisPrompt(
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
