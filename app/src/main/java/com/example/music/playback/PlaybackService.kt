package com.example.music.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.music.AppGraph
import com.example.music.appGraph
import com.example.music.data.Track
import com.example.music.presentation.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Background playback through Media3. The session powers the watch's system media controls, the
 * One UI Now bar, and Bluetooth headset buttons. It also adds a heart button that likes the
 * current song.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var graph: AppGraph
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val favoriteCommand = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)

    override fun onCreate() {
        super.onCreate()
        graph = appGraph

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            // Podcast-style skips: back 10 s, forward 30 s.
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()

        // Equalizer follows the player's audio session.
        graph.equalizer.attach(player.audioSessionId)
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
                graph.equalizer.attach(audioSessionId)
            }
        })

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .setSessionActivity(openApp)
            .setMediaButtonPreferences(listOf(heartButton(liked = false)))
            .build()

        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                        Player.EVENT_REPEAT_MODE_CHANGED,
                        Player.EVENT_TIMELINE_CHANGED,
                    )
                ) {
                    saveSession()
                }
                if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) updateHeart()
            }

            /** Remember where the episode we're leaving was; finished ones are cleared. */
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                val oldKey = oldPosition.mediaItem?.mediaId ?: return
                if (oldKey == newPosition.mediaItem?.mediaId) return
                val old = graph.tracks.track(oldKey) ?: return
                if (!old.isPodcast) return
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    graph.progress.clear(oldKey)
                } else {
                    graph.progress.save(oldKey, oldPosition.positionMs, old.durationMs)
                }
            }

            /** Episodes pick up where they were left off. */
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val key = mediaItem?.mediaId ?: return
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) return
                if (graph.tracks.track(key)?.isPodcast != true) return
                val resume = graph.progress.position(key)
                if (resume > 0 && player.currentPosition < 5_000) player.seekTo(resume)
            }
        })

        // Keep the heart in the system controls in sync with likes made anywhere in the app.
        scope.launch { graph.library.favorites.collect { updateHeart() } }

        // Reopen on the last song, paused, the way Samsung's player does.
        scope.launch {
            graph.tracks.state.first { it.hasScannedOnce }
            val last = withContext(Dispatchers.IO) { graph.sessions.read() } ?: return@launch
            if (player.mediaItemCount > 0) return@launch
            val items = itemsFor(last.queue)
            if (items.isEmpty()) return@launch
            val index = last.queue.getOrNull(last.index)
                ?.let { key -> items.indexOfFirst { it.mediaId == key } }
                ?.takeIf { it >= 0 } ?: 0
            graph.playbackContextTitle.value = last.contextTitle
            player.shuffleModeEnabled = last.shuffle
            player.repeatMode = last.repeatMode
            player.setMediaItems(items, index, last.positionMs)
        }

        // Remember the position while playing, so a reboot or a crash resumes in the right place.
        scope.launch {
            while (isActive) {
                delay(10_000)
                if (player.isPlaying) saveSession()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        saveSession()
        scope.cancel()
        graph.equalizer.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private fun heartButton(liked: Boolean): CommandButton =
        CommandButton.Builder(if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
            .setDisplayName(if (liked) "Remove from Favorites" else "Add to Favorites")
            .setSessionCommand(favoriteCommand)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()

    private fun updateHeart() {
        val s = session ?: return
        val key = s.player.currentMediaItem?.mediaId
        val liked = key != null && graph.library.isFavorite(key)
        s.setMediaButtonPreferences(listOf(heartButton(liked)))
    }

    private fun toggleFavoriteOfCurrent() {
        val s = session ?: return
        val item = s.player.currentMediaItem ?: return
        val track = graph.tracks.track(item.mediaId) ?: item.toFallbackTrack()
        graph.library.toggleFavorite(track)
        updateHeart()
    }

    private fun saveSession() {
        val player = session?.player ?: return
        if (player.mediaItemCount == 0) return
        player.currentMediaItem?.mediaId?.let { key ->
            graph.tracks.track(key)?.takeIf { it.isPodcast }?.let {
                graph.progress.save(key, player.currentPosition, player.duration.takeIf { d -> d > 0 } ?: it.durationMs)
            }
        }
        val queue = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        val snapshot = LastSession(
            queue = queue,
            index = player.currentMediaItemIndex,
            positionMs = player.currentPosition,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            contextId = "",
            contextTitle = graph.playbackContextTitle.value,
        )
        scope.launch(Dispatchers.IO) { graph.sessions.write(snapshot) }
    }

    private fun itemsFor(keys: List<String>): List<MediaItem> =
        keys.mapNotNull { graph.tracks.track(it)?.toMediaItem() }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(favoriteCommand)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_TOGGLE_FAVORITE) {
                toggleFavoriteOfCurrent()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        /** Controllers outside the app may send bare media ids; resolve them to files on the watch. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.mapNotNull { item ->
                if (item.localConfiguration != null) item else graph.tracks.track(item.mediaId)?.toMediaItem()
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            // After a reboot the library may not be scanned yet; answer once it is.
            val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                val last = withContext(Dispatchers.IO) { graph.sessions.read() }
                graph.tracks.state.first { it.hasScannedOnce }
                val items = last?.let { itemsFor(it.queue) }.orEmpty()
                if (last == null || items.isEmpty()) {
                    result.setException(UnsupportedOperationException("Nothing to resume"))
                    return@launch
                }
                val index = last.queue.getOrNull(last.index)
                    ?.let { key -> items.indexOfFirst { it.mediaId == key } }
                    ?.takeIf { it >= 0 } ?: 0
                graph.playbackContextTitle.value = last.contextTitle
                mediaSession.player.shuffleModeEnabled = last.shuffle
                mediaSession.player.repeatMode = last.repeatMode
                result.set(MediaSession.MediaItemsWithStartPosition(items, index, last.positionMs))
            }
            return result
        }
    }

    companion object {
        const val ACTION_TOGGLE_FAVORITE = "com.example.music.TOGGLE_FAVORITE"
    }
}

fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(key)
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setDurationMs(durationMs.takeIf { it > 0 })
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(if (isPodcast) MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE else MediaMetadata.MEDIA_TYPE_MUSIC)
            .build(),
    )
    .build()

private fun MediaItem.toFallbackTrack() = Track(
    key = mediaId,
    uri = localConfiguration?.uri?.toString().orEmpty(),
    title = mediaMetadata.title?.toString().orEmpty(),
    artist = mediaMetadata.artist?.toString().orEmpty(),
    album = mediaMetadata.albumTitle?.toString().orEmpty(),
    durationMs = 0,
    dateAddedMs = 0,
    folder = "",
    source = com.example.music.data.TrackSource.SHARED,
)
