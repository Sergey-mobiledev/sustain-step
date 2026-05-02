package com.sustain.step.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sustain.step.data.database.dao.HistoryDao
import com.sustain.step.data.database.entity.HistoryEntity

@Database(entities = [HistoryEntity::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
