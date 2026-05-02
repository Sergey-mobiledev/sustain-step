package com.sustain.step.ui.history

import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.sustain.step.data.database.entity.HistoryEntity
import com.sustain.step.data.repo.HistoryRepo
import com.sustain.step.ui.base.BaseViewModel
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val historyRepo: HistoryRepo
) :
    BaseViewModel() {

    val history = liveData {
        historyRepo.getAllHistory().collect {
            emit(it)
        }
    }

    fun deleteHistoryItem(item: HistoryEntity) {
        viewModelScope.launch {
            historyRepo.deleteById(item.id)
        }
    }

    fun restoreHistoryItem(item: HistoryEntity) {
        viewModelScope.launch {
            historyRepo.addHistory(item)
        }
    }
}
