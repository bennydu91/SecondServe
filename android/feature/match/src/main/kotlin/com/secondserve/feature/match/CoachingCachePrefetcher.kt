package com.secondserve.feature.match

import com.secondserve.core.ai.InferenceEngine
import com.secondserve.domain.AppResult
import com.secondserve.domain.engine.CoachingPatternDetector
import com.secondserve.domain.model.MatchContextProfile
import com.secondserve.domain.model.MatchPattern
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.model.MatchStateSnapshot
import com.secondserve.domain.repository.CoachingRepository
import com.secondserve.domain.repository.PlayerProfileRepository
import com.secondserve.domain.repository.SessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoachingCachePrefetcher @Inject constructor(
    private val inferenceEngine: InferenceEngine,
    private val coachingRepository: CoachingRepository,
    private val playerProfileRepository: PlayerProfileRepository,
    private val sessionRepository: SessionRepository
) {
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefetchJob: Job? = null

    fun initMatch(sessionId: Long) {
        if (sessionId <= 0L) {
            Timber.e("CoachingCachePrefetcher: invalid sessionId=%d, abort", sessionId)
            return
        }
        if (prefetchJob?.isActive == true) return
        prefetchJob = prefetchScope.launch {
            try {
                val session = sessionRepository.getSessionById(sessionId)
                    ?: run {
                        Timber.d("CoachingCachePrefetcher: session %d not found, abort", sessionId)
                        return@launch
                    }
                val contextProfile = playerProfileRepository.buildMatchContextProfile()

                MatchPattern.entries.forEach { pattern ->
                    val prompt = buildPrompt(pattern, contextProfile, session.surface)
                    when (val result = inferenceEngine.generate(prompt)) {
                        is AppResult.Success -> {
                            coachingRepository.saveAdvice(sessionId, pattern, result.data)
                            Timber.d("CoachingCachePrefetcher: cached %s", pattern)
                        }
                        is AppResult.Error -> {
                            Timber.d("CoachingCachePrefetcher: %s → fallback (LLM error)", pattern)
                            val fallback = MatchPattern.GENERIC_FALLBACK_TEXTS[pattern] ?: return@forEach
                            coachingRepository.saveAdvice(sessionId, pattern, fallback)
                        }
                        AppResult.Loading -> {
                            Timber.w("CoachingCachePrefetcher: unexpected Loading for %s, using fallback", pattern)
                            val fallback = MatchPattern.GENERIC_FALLBACK_TEXTS[pattern] ?: return@forEach
                            coachingRepository.saveAdvice(sessionId, pattern, fallback)
                        }
                    }
                }
                Timber.d("CoachingCachePrefetcher: initMatch done, session=%d", sessionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "CoachingCachePrefetcher: initMatch failed for session=%d", sessionId)
            }
        }
    }

    fun refreshPostChangeover(sessionId: Long, score: MatchScore) {
        if (sessionId <= 0L) return
        prefetchScope.launch {
            try {
                coachingRepository.markMatchEntriesStale(sessionId)
                Timber.d("CoachingCachePrefetcher: entries marked stale for session=%d", sessionId)

                val currentPattern = CoachingPatternDetector.detect(MatchStateSnapshot(score))
                val patterns = getProbablePatterns(currentPattern)

                val session = sessionRepository.getSessionById(sessionId) ?: return@launch
                val contextProfile = playerProfileRepository.buildMatchContextProfile()

                patterns.forEach { pattern ->
                    val prompt = buildPrompt(pattern, contextProfile, session.surface)
                    when (val result = inferenceEngine.generate(prompt)) {
                        is AppResult.Success -> {
                            coachingRepository.saveAdvice(sessionId, pattern, result.data)
                            Timber.d("CoachingCachePrefetcher: refreshed %s", pattern)
                        }
                        is AppResult.Error -> {
                            Timber.d("CoachingCachePrefetcher: refresh failed for %s, keeping stale", pattern)
                        }
                        AppResult.Loading -> {}
                    }
                }
                Timber.d("CoachingCachePrefetcher: refreshPostChangeover done, session=%d", sessionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "CoachingCachePrefetcher: refreshPostChangeover failed for session=%d", sessionId)
            }
        }
    }

    private fun getProbablePatterns(currentPattern: MatchPattern): List<MatchPattern> {
        return listOf(
            currentPattern,
            MatchPattern.NEUTRAL_TRANSITION,
            MatchPattern.SERVICE_HELD_EASY,
            MatchPattern.SERVICE_HELD_UNDER_PRESSURE,
            MatchPattern.SERVICE_BROKEN,
            MatchPattern.BREAK_CONFIRMED,
            MatchPattern.BREAK_LOST_AFTER_HOLD
        ).distinct()
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
