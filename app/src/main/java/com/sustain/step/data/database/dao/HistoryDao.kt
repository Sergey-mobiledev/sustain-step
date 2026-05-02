package com.sustain.step.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sustain.step.data.database.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: HistoryEntity)

    @Query("SELECT * FROM history ORDER BY id DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Query(
        "UPDATE history SET steps = :steps, goal = :goal, task = :task, isPurchased = 1 " +
            "WHERE date = :date"
    )
    suspend fun updateDailySummary(
        date: String,
        steps: Int,
        goal: Int,
        task: String
    ): Int

    @Query("UPDATE history SET isPurchased = 1")
    suspend fun markAllAsPurchased()

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "DELETE FROM history WHERE id = (" +
            "SELECT id FROM history WHERE date = :date AND task = :task ORDER BY id DESC LIMIT 1" +
            ")"
    )
    suspend fun deleteLatestByDateAndTask(date: String, task: String)

}
