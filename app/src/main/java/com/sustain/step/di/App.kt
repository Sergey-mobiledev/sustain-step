package com.sustain.step.di

import android.app.Application
import androidx.room.Room
import com.sustain.step.data.database.AppDatabase
import com.sustain.step.data.repo.HistoryRepo
import com.sustain.step.data.repo.StepsRepo
import com.sustain.step.data.repo.audio.AudioLoader
import com.sustain.step.data.repo.billing.Billing
import com.sustain.step.data.repo.settings.Settings

class App : Application() {

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "sustain_step_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }
    private val historyDao by lazy { database.historyDao() }

    val historyRepo by lazy { HistoryRepo(historyDao) }

    val settings by lazy { Settings(this, historyRepo) }

    val stepsRepo by lazy { StepsRepo(this, settings) }

    val billing by lazy { Billing() }

    val audioLoader by lazy { AudioLoader(this) }

}
