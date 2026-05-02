package com.sustain.step.ui.splash

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.sustain.step.data.repo.settings.Settings
import com.sustain.step.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class SplashViewModel(private val settings: Settings) : BaseViewModel() {

    companion object {
        private const val NOTIFICATION_PROMPT_THRESHOLD = 35
    }

    private val _liveDataLoadingAppState = MutableLiveData<Int>()
    val liveDataLoadingAppState: LiveData<Int> = _liveDataLoadingAppState

    private val _showNotificationsPermissionPrompt = MutableLiveData(false)
    val showNotificationsPermissionPrompt: LiveData<Boolean> = _showNotificationsPermissionPrompt
    private var promptTriggered = false
    @Volatile
    private var isLoadingPaused = false

    init {
        loadApp()
    }

    val notificationsAsked: Boolean
        get() = settings.notificationsAsked

    fun markNotificationsAsked() {
        settings.notificationsAsked = true
    }

    fun maybeTriggerNotificationsPrompt(progress: Int, shouldAskPermission: Boolean) {
        if (!shouldAskPermission || promptTriggered || notificationsAsked) return
        if (progress >= NOTIFICATION_PROMPT_THRESHOLD) {
            promptTriggered = true
            _showNotificationsPermissionPrompt.value = true
        }
    }

    fun consumeNotificationsPrompt() {
        _showNotificationsPermissionPrompt.value = false
    }

    fun pauseLoading() {
        isLoadingPaused = true
    }

    fun resumeLoading() {
        isLoadingPaused = false
    }

    fun loadApp() {
        viewModelScope.coroutineContext.cancelChildren()
        promptTriggered = false
        isLoadingPaused = false
        _showNotificationsPermissionPrompt.postValue(false)
        viewModelScope.launch(Dispatchers.IO) {
            for (i in 0..103 step Random.nextInt(1,4)) {
                while (isLoadingPaused) {
                    delay(16)
                }
                val time = Random.nextLong(1, 100)
                delay(time)
                if (i >= 100) delay(500)
                _liveDataLoadingAppState.postValue(i)
            }
        }
    }
}