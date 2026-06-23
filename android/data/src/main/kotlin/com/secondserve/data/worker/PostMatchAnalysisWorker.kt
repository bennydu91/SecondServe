package com.secondserve.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.secondserve.core.ai.InferenceEngine
import com.secondserve.core.ai.di.VpsMistralEngine
import com.secondserve.domain.AppResult
import com.secondserve.domain.repository.CoachingRepository
import com.secondserve.domain.repository.PlayerProfileRepository
import com.secondserve.domain.repository.SessionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class PostMatchAnalysisWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sessionRepository: SessionRepository,
    private val playerProfileRepository: PlayerProfileRepository,
    private val coachingRepository: CoachingRepository,
    @VpsMistralEngine private val vpsMistralEngine: InferenceEngine
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_SESSION_ID = "session_id"
    }

    override suspend fun doWork(): Result {
        val sessionId = inputData.getLong(KEY_SESSION_ID, -1L)
        if (sessionId == -1L) {
            Timber.e("PostMatchAnalysisWorker: missing session_id")
            return Result.failure()
        }

        val session = sessionRepository.getSessionById(sessionId)
        if (session == null) {
            Timber.e("PostMatchAnalysisWorker: session %d not found", sessionId)
            return Result.failure()
        }

        val profile = playerProfileRepository.buildMatchContextProfile()
        val (selfPoints, opponentPoints) = sessionRepository.getPointSummaryForSession(sessionId)

        val prompt = buildPrompt(session, profile, selfPoints, opponentPoints)

        return when (val result = vpsMistralEngine.generate(prompt)) {
            is AppResult.Success -> {
                coachingRepository.saveAnalysis(sessionId, result.data)
                Timber.d("PostMatchAnalysisWorker: analysis saved for session %d", sessionId)
                Result.success()
            }
            is AppResult.Error -> {
                Timber.e(result.exception, "PostMatchAnalysisWorker: VPS error — will retry")
                Result.retry()
            }
            AppResult.Loading -> Result.retry()
        }
    }

    private fun buildPrompt(
        session: com.secondserve.domain.model.Session,
        profile: com.secondserve.domain.model.MatchContextProfile,
        selfPoints: Int,
        opponentPoints: Int
    ): String {
        val axesText = profile.activeWorkAxes.joinToString(", ").ifEmpty { "aucun" }
        val instructionsLine = if (profile.coachInstructions.isNotEmpty()) {
            "\n- Instructions coaching : ${profile.coachInstructions.joinToString("; ")}"
        } else ""

        return """
Tu es un coach tennis. Analyse ce match de façon concrète et personnalisée. Réponse en 4-6 phrases maximum.

Match :
- Surface : ${session.surface}
- Format : ${session.format.matchFormat.name}
- Score : ${session.scoreText ?: "inconnu"}
- Résultat : ${session.result ?: "inconnu"}
- Points : $selfPoints gagnés / $opponentPoints perdus

Profil joueur :
- Classement FFT : ${profile.fftSeries ?: "non renseigné"}
- Style de jeu : ${profile.playStyle ?: "non renseigné"}
- Axes de travail actifs : $axesText$instructionsLine

Fournis une analyse structurée : points forts observés dans ce match, points faibles, écart avec les axes de travail, et 1-2 recommandations concrètes. Cite le score et la surface. Sois précis, pas générique.
        """.trimIndent()
    }
}
