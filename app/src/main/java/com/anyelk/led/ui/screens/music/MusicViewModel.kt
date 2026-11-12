package com.anyelk.led.ui.screens.music

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anyelk.led.ServiceLocator
import com.anyelk.led.data.ble.BleLedRepository
import com.anyelk.led.data.music.AudioVisualizerManager
import com.anyelk.led.data.music.MusicController
import com.anyelk.led.data.music.MusicRepository
import com.anyelk.led.data.music.PlaybackState
import com.anyelk.led.data.music.Track
import com.anyelk.led.data.music.VisualizerState
import com.anyelk.led.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MusicUiState(
    val tracks: List<Track> = emptyList(),
    val playback: PlaybackState = PlaybackState(),
    val visualizer: VisualizerState = VisualizerState(),
    val scanningFolder: Boolean = false,
    val syncToLed: Boolean = false,
    val treeUri: Uri? = null
)

class MusicViewModel(app: Application) : AndroidViewModel(app) {

    private val musicRepo: MusicRepository = ServiceLocator.musicRepository
    private val controller: MusicController = ServiceLocator.musicController
    private val visualizer: AudioVisualizerManager = ServiceLocator.visualizerManager
    private val ble: BleLedRepository = ServiceLocator.bleRepository
    private val settings: SettingsRepository = ServiceLocator.settingsRepository

    private val _scanningFolder = MutableStateFlow(false)
    private val _syncToLed = MutableStateFlow(false)
    private val _treeUri = MutableStateFlow<Uri?>(null)
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    private val _message = MutableStateFlow<String?>(null)

    val message: StateFlow<String?> = _message.asStateFlow()

    private var syncJob: kotlinx.coroutines.Job? = null

    val playback: StateFlow<PlaybackState> = controller.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaybackState())

    val visualizerState: StateFlow<VisualizerState> = visualizer.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VisualizerState())

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Restore the previously picked folder.
            settings.settings.collect { s ->
                val uri = s.musicTreeUri?.let(Uri::parse)
                if (uri != null && _treeUri.value != uri && _tracks.value.isEmpty() &&
                    !_scanningFolder.value
                ) {
                    _treeUri.value = uri
                    loadTree(uri)
                }
            }
        }

        viewModelScope.launch {
            playback.collect { pb ->
                _uiState.value = _uiState.value.copy(playback = pb, tracks = pb.tracks)
                // Attach the FFT visualizer as soon as the player reports a session.
                if (pb.audioSessionId > 0 && _syncToLed.value) {
                    visualizer.attach(pb.audioSessionId)
                }
            }
        }

        viewModelScope.launch {
            visualizerState.collect { vs ->
                _uiState.value = _uiState.value.copy(visualizer = vs)
            }
        }
    }

    fun onFolderPicked(uri: Uri?) {
        if (uri == null) return
        musicRepo.takePersistablePermission(uri)
        viewModelScope.launch {
            settings.setMusicTree(uri.toString())
            _treeUri.value = uri
            loadTree(uri)
        }
    }

    private suspend fun loadTree(uri: Uri) {
        _scanningFolder.value = true
        _uiState.value = _uiState.value.copy(scanningFolder = true, treeUri = uri)
        try {
            val tracks = musicRepo.scanTree(uri)
            _tracks.value = tracks
            _uiState.value = _uiState.value.copy(
                tracks = tracks,
                scanningFolder = false,
                treeUri = uri
            )
            if (tracks.isEmpty()) {
                _message.value = "empty"
            } else {
                controller.setTracksIfChanged(tracks, 0)
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(scanningFolder = false)
            _message.value = e.message ?: "error"
        }
    }

    fun togglePlayPause() = controller.togglePlayPause()
    fun next() = controller.next()
    fun prev() = controller.prev()
    fun playAt(index: Int) = controller.playAt(index)
    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)

    /** Enables/disables streaming of FFT colors to the LED strip. */
    fun setSyncToLed(enabled: Boolean) {
        _syncToLed.value = enabled
        _uiState.value = _uiState.value.copy(syncToLed = enabled)
        if (enabled) {
            val session = controller.state.value.audioSessionId
            if (session > 0) visualizer.attach(session)
            startLedSync()
        } else {
            syncJob?.cancel()
            syncJob = null
            visualizer.detach()
        }
    }

    private fun startLedSync() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            visualizerState.collect { vs ->
                if (!vs.active) return@collect
                if (vs.red == 0 && vs.green == 0 && vs.blue == 0) return@collect
                ble.sendColor(vs.red, vs.green, vs.blue)
            }
        }
    }

    fun onVisualizerPermissionResult(granted: Boolean) {
        if (granted) {
            setSyncToLed(true)
        } else {
            _message.value = "visualizer_permission"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun currentTreeIntent(): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)

    override fun onCleared() {
        syncJob?.cancel()
        visualizer.detach()
        super.onCleared()
    }
}
