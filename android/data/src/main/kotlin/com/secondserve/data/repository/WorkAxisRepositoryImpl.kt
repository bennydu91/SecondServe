package com.secondserve.data.repository

import com.secondserve.core.ai.InferenceEngine
import com.secondserve.core.ai.di.VpsMistralEngine
import com.secondserve.data.local.dao.AxisSuggestionDao
import com.secondserve.data.local.dao.CoachingAnalysisDao
import com.secondserve.data.local.dao.CoachingSynthesisDao
import com.secondserve.data.local.dao.WorkAxisDao
import com.secondserve.data.local.db.entity.AxisSuggestionEntity
import com.secondserve.data.local.db.entity.WorkAxisEntity
import com.secondserve.data.local.db.entity.toDomain
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.data.remote.api.dto.WorkAxisRequest
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.AxisSuggestion
import com.secondserve.domain.model.MAX_WORK_AXES
import com.secondserve.domain.model.WorkAxis
import com.secondserve.domain.repository.WorkAxisRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

class WorkAxisRepositoryImpl(
    private val dao: WorkAxisDao,
    private val suggestionDao: AxisSuggestionDao,
    private val analysisDao: CoachingAnalysisDao,
    private val synthesisDao: CoachingSynthesisDao,
    private val vpsApiService: VpsApiService,
    @VpsMistralEngine private val vpsMistralEngine: InferenceEngine
) : WorkAxisRepository {

    override fun getWorkAxes(): Flow<List<WorkAxis>> =
        dao.getAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun createWorkAxis(title: String): AppResult<Unit> {
        if (dao.count() >= MAX_WORK_AXES)
            return AppResult.Error(IllegalStateException("Maximum $MAX_WORK_AXES axes actifs atteint"))
        return try {
            val now = System.currentTimeMillis()
            val entity = WorkAxisEntity(title = title, createdAt = now, updatedAt = now)
            dao.insert(entity)
            try {
                vpsApiService.createWorkAxis(WorkAxisRequest(title = title, createdAt = now))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.w(e, "VPS work axis create failed — local save succeeded")
            }
            AppResult.Success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppResult.Error(e)
        }
    }

    override suspend fun updateWorkAxis(id: Long, title: String): AppResult<Unit> = try {
        val now = System.currentTimeMillis()
        val existing = dao.getById(id) ?: return AppResult.Error(Exception("WorkAxis $id not found"))
        dao.update(existing.copy(title = title, updatedAt = now))
        try {
            vpsApiService.updateWorkAxis(id, WorkAxisRequest(title = title, createdAt = existing.createdAt))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.w(e, "VPS work axis update failed — local save succeeded")
        }
        AppResult.Success(Unit)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        AppResult.Error(e)
    }

    override suspend fun deleteWorkAxis(id: Long): AppResult<Unit> = try {
        dao.delete(id)
        try {
            vpsApiService.deleteWorkAxis(id)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.w(e, "VPS work axis delete failed — local delete succeeded")
        }
        AppResult.Success(Unit)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        AppResult.Error(e)
    }

    override suspend fun getActiveWorkAxesTitles(): List<String> =
        dao.getAllTitles()

    override fun observePendingSuggestions(): Flow<List<AxisSuggestion>> =
        suggestionDao.observePending().map { list ->
            list.mapNotNull { runCatching { it.toDomain() }.getOrNull() }
        }

    override suspend fun hasPendingSuggestions(): Boolean =
        try { suggestionDao.countPending() > 0 } catch (e: Exception) { if (e is CancellationException) throw e; false }

    override suspend fun generateAndSaveSuggestions(): AppResult<Unit> {
        val latestContent = try {
            synthesisDao.getLatest()?.content ?: analysisDao.getMostRecent()?.content
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return AppResult.Error(e)
        }
        if (latestContent.isNullOrBlank()) return AppResult.Error(IllegalStateException("No coaching data available"))

        val currentAxes = try { dao.getAllTitles() } catch (e: Exception) { if (e is CancellationException) throw e; emptyList() }
        val prompt = buildSuggestionsPrompt(latestContent, currentAxes)

        return when (val result = vpsMistralEngine.generate(prompt)) {
            is AppResult.Success -> {
                val titles = parseSuggestionsResponse(result.data)
                if (titles.isEmpty()) return AppResult.Error(IllegalStateException("No suggestions parsed"))
                try {
                    val now = System.currentTimeMillis()
                    suggestionDao.insertAll(titles.map { AxisSuggestionEntity(title = it, generatedAt = now) })
                    AppResult.Success(Unit)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    AppResult.Error(e)
                }
            }
            is AppResult.Error -> AppResult.Error(result.exception)
            AppResult.Loading -> AppResult.Error(IllegalStateException("Unexpected loading state"))
        }
    }

    override suspend fun acceptSuggestion(id: Long): AppResult<Unit> = try {
        val suggestion = suggestionDao.getById(id)
            ?: return AppResult.Error(IllegalStateException("Suggestion $id not found"))
        val result = createWorkAxis(suggestion.title)
        if (result is AppResult.Success) suggestionDao.updateStatus(id, "ACCEPTED")
        result
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        AppResult.Error(e)
    }

    override suspend fun ignoreSuggestion(id: Long): AppResult<Unit> = try {
        suggestionDao.updateStatus(id, "IGNORED")
        AppResult.Success(Unit)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.e(e, "Failed to ignore suggestion $id")
        AppResult.Error(e)
    }

    override suspend fun hasCoachingData(): Boolean = try {
        synthesisDao.getLatest() != null || analysisDao.getMostRecent() != null
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        false
    }

    private fun buildSuggestionsPrompt(sourceContent: String, currentAxes: List<String>): String {
        val axesText = currentAxes.map { it.replace("\n", " ").replace("\r", "") }.joinToString(", ").ifEmpty { "aucun" }
        return """
Tu es un coach tennis. Basé sur l'analyse ci-dessous, suggère 2 ou 3 axes de travail concrets et actionnables.

Analyse récente :
${sourceContent.take(500)}

Axes de travail actuels : $axesText

Réponds UNIQUEMENT avec les titres des axes suggérés, un par ligne, en 3 à 8 mots chacun. Exemples : "Montée au filet après service gagnant", "Constance du revers long de ligne". Ne duplique pas les axes déjà existants. Pas d'explication, pas de numérotation.
        """.trimIndent()
    }

    private fun parseSuggestionsResponse(response: String): List<String> =
        response.lines()
            .map { it.trim().replace(Regex("^[\\-*•\\d.]+\\s*"), "").trim() }
            .filter { it.isNotBlank() && it.length in 3..150 }
            .take(3)
}

private fun AxisSuggestionEntity.toDomain(): AxisSuggestion =
    AxisSuggestion(id = id, title = title, status = status, generatedAt = generatedAt)
