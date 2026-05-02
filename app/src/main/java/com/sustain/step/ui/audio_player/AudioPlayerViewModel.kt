package com.sustain.step.ui.audio_player

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.sustain.step.data.repo.audio.AudioLoader
import com.sustain.step.data.repo.settings.Settings
import com.sustain.step.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AudioPlayerViewModel(
    private val audioLoader: AudioLoader,
    private val settings: Settings
) : BaseViewModel() {

    private val _liveDataSongs = MutableLiveData<List<AudioData>>()
    val liveDataSongs: LiveData<List<AudioData>> = _liveDataSongs

    fun getAudio() {
        try {
            viewModelScope.launch(Dispatchers.IO) {
                val favoriteUris = settings.favoriteAudioUris
                val allSongs = audioLoader.loadAllMusicFiles().map {
                    AudioData(
                        name = it.name,
                        artist = it.artist,
                        title = it.title,
                        uri = it.uri,
                        duration = it.duration,
                        folderName = it.folderName,
                        folderPath = it.folderPath,
                        dateAddedSeconds = it.dateAddedSeconds,
                        isFavorite = favoriteUris.contains(it.uri.toString())
                    )
                }.toTypedArray()
                _liveDataSongs.postValue(allSongs.toList())
            }
        } catch (e: Exception) {
            return
        }
    }

    fun playSong(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = _liveDataSongs.value?.toTypedArray() ?: return@launch
            val oldIndex = list.indexOfFirst { it.isPlaying }
            if (oldIndex != -1) {
                val newItem = list[oldIndex].copy(isPlaying = false)
                list[oldIndex] = newItem
            }
            val newIndex = list.indexOfFirst { it.uri == uri }
            if (newIndex != -1) {
                val newItem = list[newIndex].copy(isPlaying = true)
                list[newIndex] = newItem
            }
            _liveDataSongs.postValue(list.toList())
        }
    }

    fun toggleFavorite(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = _liveDataSongs.value?.toTypedArray() ?: return@launch
            val index = list.indexOfFirst { it.uri == uri }
            if (index == -1) return@launch
            val updatedFavorite = !list[index].isFavorite
            settings.setAudioFavorite(uri.toString(), updatedFavorite)
            list[index] = list[index].copy(isFavorite = updatedFavorite)
            _liveDataSongs.postValue(list.toList())
        }
    }

    fun isAudioPermissionPrePromptShown(): Boolean = settings.audioPermissionPrePromptShown

    fun markAudioPermissionPrePromptShown() {
        settings.audioPermissionPrePromptShown = true
    }

    fun wasAudioPermissionSystemRequested(): Boolean = settings.audioPermissionSystemRequestAsked

    fun markAudioPermissionSystemRequested() {
        settings.audioPermissionSystemRequestAsked = true
    }

}

data class AudioData(
    val name: String,
    val artist: String,
    val title: String,
    val uri: Uri,
    val duration: Long,
    val folderName: String,
    val folderPath: String,
    val dateAddedSeconds: Long,
    val isPlaying: Boolean = false,
    val isFavorite: Boolean = false
)