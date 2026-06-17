package com.secondserve.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.secondserve.data.local.db.entity.WorkAxisEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkAxisDao {
    @Query("SELECT * FROM work_axes ORDER BY created_at ASC")
    fun getAll(): Flow<List<WorkAxisEntity>>

    @Query("SELECT COUNT(*) FROM work_axes")
    suspend fun count(): Int

    @Query("SELECT title FROM work_axes ORDER BY created_at ASC")
    suspend fun getAllTitles(): List<String>

    @Query("SELECT * FROM work_axes WHERE id = :id")
    suspend fun getById(id: Long): WorkAxisEntity?

    @Insert
    suspend fun insert(entity: WorkAxisEntity): Long

    @Update
    suspend fun update(entity: WorkAxisEntity)

    @Query("DELETE FROM work_axes WHERE id = :id")
    suspend fun delete(id: Long)
}
