package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Task
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<Task>): List<Long>

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Long): Task?

    @Query("SELECT * FROM tasks ORDER BY isPinned DESC, createdAt DESC")
    fun getAllTasksFlow(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE sourceScreenshotId = :screenshotId ORDER BY id ASC")
    fun getTasksByScreenshotId(screenshotId: Long): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE sourceScreenshotId = :screenshotId ORDER BY id ASC")
    suspend fun getTasksByScreenshotIdSync(screenshotId: Long): List<Task>

    @Query("SELECT * FROM tasks WHERE isDone = 0 AND remindAt IS NOT NULL AND remindAt > :now ORDER BY remindAt ASC")
    suspend fun getUpcomingReminders(now: Long): List<Task>

    @Query("SELECT * FROM tasks WHERE isDone = 0 ORDER BY dueAt ASC")
    suspend fun getActiveTasksSync(): List<Task>
}
