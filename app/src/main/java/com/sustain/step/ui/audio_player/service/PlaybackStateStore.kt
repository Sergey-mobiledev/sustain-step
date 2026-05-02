package com.sustain.step.ui.audio_player.service

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackState(
    val currentUri: Uri? = null,
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0
)

object PlaybackStateStore {
    private val _state = MutableStateFlow(PlaybackState())
    val state = _state.asStateFlow()

    fun update(newState: PlaybackState) {
        _state.value = newState
    }
}
