package com.sustain.step.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sustain.step.ui.audio_player.AudioPlayerViewModel
import com.sustain.step.ui.history.HistoryViewModel
import com.sustain.step.ui.home.HomeViewModel
import com.sustain.step.ui.splash.SplashViewModel

class ViewModelFactory(
    private val app: App
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel = when (modelClass) {
            SplashViewModel::class.java -> {
                SplashViewModel(app.settings)
            }

            HomeViewModel::class.java -> {
                HomeViewModel(app.settings, app.stepsRepo)
            }

            HistoryViewModel::class.java -> {
                HistoryViewModel(app.historyRepo)
            }

            AudioPlayerViewModel::class.java -> {
                AudioPlayerViewModel(app.audioLoader, app.settings)
            }

            else -> {
                throw IllegalStateException("Unknown viewModel class")
            }
        }
        return viewModel as T
    }
}

fun Fragment.factory() = ViewModelFactory(requireContext().applicationContext as App)