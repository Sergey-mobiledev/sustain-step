package com.sustain.step.data.repo

import com.sustain.step.data.database.dao.HistoryDao
import com.sustain.step.data.database.entity.HistoryEntity

class HistoryRepo(private val historyDao: HistoryDao) {

    fun getAllHistory() = historyDao.getAllHistory()

    suspend fun addHistory(historyEntity: HistoryEntity) = historyDao.insert(historyEntity)

    suspend fun upsertDailySummary(historyEntity: HistoryEntity) {
        val updatedRows = historyDao.updateDailySummary(
            date = historyEntity.date,
            steps = historyEntity.steps,
            goal = historyEntity.goal,
            task = historyEntity.task
        )
        if (updatedRows == 0) {
            historyDao.insert(historyEntity)
        }
    }

    suspend fun markAllAsPurchased() = historyDao.markAllAsPurchased()

    suspend fun deleteById(id: Long) = historyDao.deleteById(id)

    suspend fun deleteLatestByDateAndTask(date: String, task: String) =
        historyDao.deleteLatestByDateAndTask(date, task)
}