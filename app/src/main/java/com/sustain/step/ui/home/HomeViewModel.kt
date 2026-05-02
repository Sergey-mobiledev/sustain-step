package com.sustain.step.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.sustain.step.data.repo.StepsRepo
import com.sustain.step.data.repo.StepsRepositoryData
import com.sustain.step.data.repo.settings.Settings
import com.sustain.step.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeViewModel(private val settings: Settings, private val stepsRepo: StepsRepo) :
    BaseViewModel() {

    private val _stepsData = MutableLiveData<StepsRepositoryData>()
    val stepsData: LiveData<StepsRepositoryData> = _stepsData

    private val _ecoTaskData = MutableLiveData<EcoTaskData>()
    val ecoTaskData: LiveData<EcoTaskData> = _ecoTaskData

    init {
        viewModelScope.launch(Dispatchers.IO) {
            stepsRepo.stepsData.collect {
                _stepsData.postValue(it)
            }
        }
        updateEcoTaskData()
    }

    fun startCounting() {
        viewModelScope.launch(Dispatchers.IO) {
            stepsRepo.emitActualData()
            stepsRepo.startCounting()
        }
    }

    fun loadActualData() {
        viewModelScope.launch(Dispatchers.IO) {
            stepsRepo.emitActualData()
        }
    }

    fun isActivityPermissionPrePromptShown(): Boolean = settings.activityPermissionPrePromptShown

    fun markActivityPermissionPrePromptShown() {
        settings.activityPermissionPrePromptShown = true
    }

    fun wasActivityPermissionSystemRequested(): Boolean =
        settings.activityPermissionSystemRequestAsked

    fun markActivityPermissionSystemRequested() {
        settings.activityPermissionSystemRequestAsked = true
    }

    fun getDailyStepsGoal(): Int = settings.dailyStepsGoal

    fun saveDailyStepsGoal(goal: Int) {
        settings.dailyStepsGoal = goal
        viewModelScope.launch(Dispatchers.IO) {
            settings.upsertTodayHistorySummary()
        }
    }

    fun stopCounting() = viewModelScope.launch(Dispatchers.IO) {
        stepsRepo.stopCounting()
    }

    private fun updateEcoTaskData(){
        viewModelScope.launch(Dispatchers.Main) {
            _ecoTaskData.value = EcoTaskData(settings.ecoTask, settings.isCompletedEcoTask)
        }
    }


    fun setDoneToCurrentTask() {
        viewModelScope.launch(Dispatchers.IO) {
            settings.completeCurrentEcoTask()
            updateEcoTaskData()
        }
    }

    fun undoDoneToCurrentTask() {
        viewModelScope.launch(Dispatchers.IO) {
            settings.undoCurrentEcoTask()
            updateEcoTaskData()
        }
    }

    fun skipCurrentTask() {
        viewModelScope.launch(Dispatchers.IO) {
            settings.skipCurrentEcoTask()
            updateEcoTaskData()
        }
    }

}

data class EcoTaskData(
    val ecoTask: String,
    val isCompleted: Boolean
)

