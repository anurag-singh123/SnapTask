package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ScreenshotRecord
import com.example.data.model.ScreenshotStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenshotDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: ScreenshotRecord): Long

    @Update
    suspend fun update(record: ScreenshotRecord)

    @Delete
    suspend fun delete(record: ScreenshotRecord)

    @Query("DELETE FROM screenshot_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM screenshot_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ScreenshotRecord?

    @Query("SELECT * FROM screenshot_records WHERE mediaStoreId = :mediaStoreId LIMIT 1")
    suspend fun getByMediaStoreId(mediaStoreId: Long): ScreenshotRecord?

    @Query("SELECT * FROM screenshot_records WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): ScreenshotRecord?

    @Query("SELECT * FROM screenshot_records ORDER BY processedAt DESC")
    fun getAllFlow(): Flow<List<ScreenshotRecord>>

    @Query("SELECT * FROM screenshot_records WHERE status = 'PROCESSED' ORDER BY processedAt DESC")
    fun getInboxRecordsFlow(): Flow<List<ScreenshotRecord>>

    @Query("UPDATE screenshot_records SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ScreenshotStatus)

    @Query("UPDATE screenshot_records SET ocrText = NULL WHERE processedAt < :threshold")
    suspend fun clearOcrTextOlderThan(threshold: Long): Int
}
