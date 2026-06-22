package com.secondserve.feature.match

import com.secondserve.core.ai.InferenceEngine
import com.secondserve.domain.AppResult
import com.secondserve.domain.engine.CoachingPatternDetector
import com.secondserve.domain.model.CoachingResult
import com.secondserve.domain.model.CoachingSource
import com.secondserve.domain.model.MatchContextProfile
import com.secondserve.domain.model.MatchPattern
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.model.MatchStateSnapshot
import com.secondserve.domain.repository.CoachingRepository
import com.secondserve.domain.repository.PlayerProfileRepository
import com.secondserve.domain.repository.SessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoachingResolver @Inject constructor(
    private val inferenceEngine: InferenceEngine,
    private val coachingRepository: CoachingRepository,
    private val playerProfileRepository: PlayerProfileRepository,
    private val sessionRepository: SessionRepository
) {

    suspend fun resolve(sessionId: Long, score: MatchScore): CoachingResult? {
        if (score.isMatchOver) {
            Timber.d("CoachingResolver: match is over, skipping advice")
            return null
        }

        val pattern = CoachingPatternDetector.detect(MatchStateSnapshot(score))

        // 1. GeminiNano avec timeout 3s
        val geminiResult = try {
            withTimeout(3_000L) {
                val session = sessionRepository.getSessionById(sessionId)
                val context = playerProfileRepository.buildMatchContextProfile()
                val prompt = buildPrompt(pattern, context, session?.surface ?: "")
                inferenceEngine.generate(prompt)
            }
        } catch (e: TimeoutCancellationException) {
            Timber.d("CoachingResolver: GeminiNano timeout for pattern=%s", pattern)
            AppResult.Error(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d("CoachingResolver: GeminiNano error for pattern=%s: %s", pattern, e.message)
            AppResult.Error(e)
        }

        if (geminiResult is AppResult.Success) {
            Timber.d("CoachingResolver: source=GEMINI, pattern=%s", pattern)
            return CoachingResult(geminiResult.data, CoachingSource.GEMINI)
        }

        // 2. Cache Room
        val cached = coachingRepository.getCachedAdvice(sessionId, pattern)
        if (cached != null) {
            Timber.d("CoachingResolver: source=CACHE, pattern=%s", pattern)
            return CoachingResult(cached.content, CoachingSource.CACHE)
        }

        // 3. Fallback statique — ne retourne jamais null
        val fallback = MatchPattern.GENERIC_FALLBACK_TEXTS[pattern]
            ?: MatchPattern.GENERIC_FALLBACK_TEXTS[MatchPattern.NEUTRAL_TRANSITION]
            ?: "Restez concentré sur chaque point."
        Timber.d("CoachingResolver: source=STATIC, pattern=%s", pattern)
        return CoachingResult(fallback, CoachingSource.STATIC)
    }

    private fun buildPrompt(
        pattern: MatchPattern,
        context: MatchContextProfile,
        surface: String
    ): String = buildString {
        append("Tu es coach tennis. Situation de jeu : ${pattern.description}.\n")
        append("Surface : $surface.")
        if (context.fftSeries != null) append(" Classement joueur : ${context.fftSeries}.")
        if (context.playStyle != null) append(" Style : ${context.playStyle}.")
        if (context.activeWorkAxes.isNotEmpty()) {
            append(" Axes de travail : ${context.activeWorkAxes.joinToString(", ")}.")
        }
        if (context.coachInstructions.isNotEmpty()) {
            append(" Consignes coach : ${context.coachInstructions.joinToString(". ")}.")
        }
        append("\nDonne un conseil court (2-3 phrases max) pour le prochain jeu.")
    }
}
