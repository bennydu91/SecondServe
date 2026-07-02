package com.secondserve.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.secondserve.data.local.db.entity.LiveShareEntity

@Dao
interface LiveShareDao {
    @Query("SELECT * FROM live_shares WHERE session_id = :sessionId")
    suspend fun getBySessionId(sessionId: Long): LiveShareEntity?

    @Insert
    suspend fun insert(entity: LiveShareEntity): Long
}
