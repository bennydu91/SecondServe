package com.secondserve.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_queue",
    indices = [Index(value = ["status"], name = "idx_sync_queue_status")]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: Long,
    @ColumnInfo(name = "operation") val operation: String,
    @ColumnInfo(name = "status") val status: String = "PENDING",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0
) {
    companion object {
        const val ENTITY_TYPE_SESSION = "SESSION"
        const val OPERATION_UPSERT = "UPSERT"
        const val OPERATION_DELETE = "DELETE"
        const val STATUS_PENDING = "PENDING"
        const val STATUS_DONE = "DONE"
        const val STATUS_FAILED = "FAILED"
    }
}
