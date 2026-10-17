package com.example.music.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.music.appGraph
import com.example.music.data.Track
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Snapshot of the player for the UI. Position is read on demand through [PlayerConnection.positionMs]. */
data class NowPlayingState(
    val isConnected: Boolean = false,
    val trackKey: String? = null,
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = C.TIME_UNSET,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val index: Int = 0,
    val count: Int = 0,
    val speed: Float = 1f,
) {
    val hasMedia: Boolean get() = trackKey != null
}

/** UI-side handle to [PlaybackService] through a Media3 [MediaController]. */
class PlayerConnection(private val context: Context) {

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            publish(c)
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
        _state.value = _state.value.copy(isConnected = false)
    }

    private fun publish(p: Player) {
        val item = p.currentMediaItem
        _state.value = NowPlayingState(
            isConnected = true,
            trackKey = item?.mediaId,
            title = item?.mediaMetadata?.title?.toString().orEmpty(),
            artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
            isPlaying = p.isPlaying,
            isBuffering = p.playbackState == Player.STATE_BUFFERING,
            durationMs = p.duration,
            shuffle = p.shuffleModeEnabled,
            repeatMode = p.repeatMode,
            index = p.currentMediaItemIndex,
            count = p.mediaItemCount,
            speed = p.playbackParameters.speed,
        )
    }

    fun positionMs(): Long = controller?.currentPosition ?: 0L

    fun durationMs(): Long = controller?.duration?.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L

    /** Replaces the queue with [tracks] and starts at [startIndex]. */
    fun play(tracks: List<Track>, startIndex: Int, contextTitle: String, shuffle: Boolean = false) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        val graph = context.appGraph
        // Leaving an episode for something else: remember exactly where it was.
        c.currentMediaItem?.mediaId?.let { key ->
            graph.tracks.track(key)?.takeIf { it.isPodcast }?.let {
                graph.progress.save(key, c.currentPosition, c.duration.takeIf { d -> d > 0 } ?: it.durationMs)
            }
        }
        graph.playbackContextTitle.value = contextTitle
        c.shuffleModeEnabled = shuffle
        val start = startIndex.coerceIn(0, tracks.lastIndex)
        // Episodes resume where they were left off.
        val startPosition = tracks[start].takeIf { it.isPodcast }?.let { graph.progress.position(it.key) } ?: 0L
        c.setMediaItems(tracks.map { it.toMediaItem() }, start, startPosition)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.play()
        }
    }

    fun next() = controller?.seekToNext() ?: Unit

    /** Restarts the song if more than 3 s in, otherwise goes back. Same as One UI. */
    fun previous() = controller?.seekToPrevious() ?: Unit

    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs) ?: Unit

    /** Back 10 s (podcasts). */
    fun skipBack() = controller?.seekBack() ?: Unit

    /** Forward 30 s (podcasts). */
    fun skipForward() = controller?.seekForward() ?: Unit

    /** 1× → 1.25× → 1.5× → 2× → 1×. */
    fun cycleSpeed() {
        val c = controller ?: return
        val speeds = listOf(1f, 1.25f, 1.5f, 2f)
        val next = speeds.firstOrNull { it > c.playbackParameters.speed + 0.01f } ?: speeds.first()
        c.setPlaybackSpeed(next)
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    /** Off → all → one → off. */
    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Media ids of the current queue in play order. */
    fun queueKeys(): List<String> {
        val c = controller ?: return emptyList()
        return (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }
    }

    fun jumpTo(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            c.seekToDefaultPosition(index)
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }
}
