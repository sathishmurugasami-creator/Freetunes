package com.example.tunes

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = YouTubeRepository()
    private var controller: MediaController? = null

    var query by mutableStateOf("")
    var results by mutableStateOf<List<Song>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var nowPlaying by mutableStateOf<Song?>(null); private set
    var isPlaying by mutableStateOf(false); private set
    var buffering by mutableStateOf(false); private set

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            controller = future.get().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) { this@MainViewModel.isPlaying = playing }
                    override fun onPlaybackStateChanged(state: Int) {
                        buffering = state == Player.STATE_BUFFERING
                    }
                })
            }
        }, ContextCompat.getMainExecutor(app))
    }

    fun search() {
        if (query.isBlank()) return
        viewModelScope.launch {
            loading = true; error = null
            runCatching { repo.search(query.trim()) }
                .onSuccess { results = it }
                .onFailure { error = it.message ?: "Search failed" }
            loading = false
        }
    }

    fun play(song: Song) {
        nowPlaying = song
        buffering = true
        error = null
        viewModelScope.launch {
            runCatching { repo.audioUrl(song.url) }
                .onSuccess { streamUrl ->
                    val item = MediaItem.Builder()
                        .setMediaId(song.url)
                        .setUri(streamUrl)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(song.title)
                                .setArtist(song.artist)
                                .setArtworkUri(song.thumbnail?.let(Uri::parse))
                                .build()
                        )
                        .build()
                    controller?.run { setMediaItem(item); prepare(); play() }
                }
                .onFailure { error = it.message ?: "Couldn't play this track"; buffering = false }
        }
    }

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    override fun onCleared() {
        controller?.release()
    }
}
