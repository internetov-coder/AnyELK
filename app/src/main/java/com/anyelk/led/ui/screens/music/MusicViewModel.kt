package com.anyelk.led.ui.screens.music

import android.app.Application
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
                // Keep the FFT visualizer attached whenever the player reports a
                // session, so the on-screen spectrum works independently of the
                // "send colors to strip" toggle.
                if (pb.audioSessionId > 0) {
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
                // Empty folder state is rendered from treeUri/tracks in the UI.
            } else {
                controller.setTracksIfChanged(tracks, 0)
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(scanningFolder = false)
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
            // Stop streaming to the strip but keep the visualizer alive so the
            // on-screen spectrum bars continue to animate.
            syncJob?.cancel()
            syncJob = null
        }
    }

    /** Retry attaching after RECORD_AUDIO was granted from the sync toggle path. */
    fun onVisualizerPermissionResult(granted: Boolean) {
        if (!granted) return
        val session = controller.state.value.audioSessionId
        if (session > 0) visualizer.attach(session)
        if (_syncToLed.value) startLedSync()
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

    override fun onCleared() {
        syncJob?.cancel()
        visualizer.detach()
        super.onCleared()
    }
}
