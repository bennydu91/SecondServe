package com.secondserve.data.repository

import com.secondserve.data.local.dao.WorkAxisDao
import com.secondserve.data.local.db.entity.WorkAxisEntity
import com.secondserve.data.local.db.entity.toDomain
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.data.remote.api.dto.WorkAxisRequest
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.WorkAxis
import com.secondserve.domain.repository.WorkAxisRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

class WorkAxisRepositoryImpl(
    private val dao: WorkAxisDao,
    private val vpsApiService: VpsApiService
) : WorkAxisRepository {

    override fun getWorkAxes(): Flow<List<WorkAxis>> =
        dao.getAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun createWorkAxis(title: String): AppResult<Unit> = try {
        val now = System.currentTimeMillis()
        val entity = WorkAxisEntity(title = title, createdAt = now, updatedAt = now)
        dao.insert(entity)
        try {
            vpsApiService.createWorkAxis(WorkAxisRequest(title = title, createdAt = now))
        } catch (e: Exception) {
            Timber.w(e, "VPS work axis create failed — local save succeeded")
        }
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(e)
    }

    override suspend fun updateWorkAxis(id: Long, title: String): AppResult<Unit> = try {
        val now = System.currentTimeMillis()
        val existing = dao.getById(id) ?: return AppResult.Error(Exception("WorkAxis $id not found"))
        dao.update(existing.copy(title = title, updatedAt = now))
        try {
            vpsApiService.updateWorkAxis(id, WorkAxisRequest(title = title, createdAt = existing.createdAt))
        } catch (e: Exception) {
            Timber.w(e, "VPS work axis update failed — local save succeeded")
        }
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(e)
    }

    override suspend fun deleteWorkAxis(id: Long): AppResult<Unit> = try {
        dao.delete(id)
        try {
            vpsApiService.deleteWorkAxis(id)
        } catch (e: Exception) {
            Timber.w(e, "VPS work axis delete failed — local delete succeeded")
        }
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(e)
    }

    override suspend fun getActiveWorkAxesTitles(): List<String> =
        dao.getAllTitles()
}
