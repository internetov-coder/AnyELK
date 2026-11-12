package com.anyelk.led.data.music

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackState(
    val tracks: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val audioSessionId: Int = -1
) {
    val currentTrack: Track? get() = tracks.getOrNull(currentIndex)
}

/** Thin MVVM-friendly wrapper around a single ExoPlayer instance. */
class MusicController(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val player: ExoPlayer = ExoPlayer.Builder(appContext).build()

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var pollJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish()
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            publish()
        }
    }

    init {
        player.addListener(listener)
        startPolling()
    }

    fun setTracks(tracks: List<Track>, startIndex: Int = 0) {
        val items = tracks.map { MediaItem.fromUri(it.uri) }
        player.setMediaItems(items, startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)), 0L)
        player.prepare()
        _state.value = _state.value.copy(tracks = tracks, currentIndex = startIndex)
        publish()
    }

    /**
     * Reloads the playlist only when its content actually changed, so that
     * recreating the ViewModel (navigating between tabs) does not restart
     * playback.
     */
    fun setTracksIfChanged(tracks: List<Track>, startIndex: Int = 0) {
        val current = _state.value.tracks
        val changed = current.size != tracks.size ||
            current.indices.any { current[it].uri != tracks[it].uri }
        if (changed) {
            setTracks(tracks, startIndex)
        } else {
            _state.value = _state.value.copy(tracks = tracks)
        }
    }

    fun play() {
        player.play()
        publish()
    }

    fun pause() {
        player.pause()
        publish()
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
        publish()
    }

    fun next() {
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
        publish()
    }

    fun prev() {
        if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
        publish()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        publish()
    }

    fun playAt(index: Int) {
        if (index in 0 until player.mediaItemCount) {
            player.seekTo(index, 0L)
            player.prepare()
            player.play()
            publish()
        }
    }

    fun stop() {
        player.stop()
        publish()
    }

    fun release() {
        pollJob?.cancel()
        player.removeListener(listener)
        player.release()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                publish()
                delay(if (player.isPlaying) 500L else 1000L)
            }
        }
    }

    private fun publish() {
        val index = player.currentMediaItemIndex
        _state.value = PlaybackState(
            tracks = _state.value.tracks,
            currentIndex = if (index >= 0) index else _state.value.currentIndex,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0L } ?: 0L,
            audioSessionId = player.audioSessionId
        )
    }
}
