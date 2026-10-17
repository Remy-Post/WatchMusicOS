package com.example.music.presentation.player

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material3.MaterialTheme
import com.example.music.appGraph
import com.example.music.data.LibraryLogic
import com.example.music.data.SmartList
import com.example.music.data.Track
import com.example.music.playback.PlayerConnection
import com.example.music.presentation.components.Icons
import com.example.music.presentation.components.ListScaffold
import com.example.music.presentation.components.NavChip
import com.example.music.presentation.components.TrackChip
import com.example.music.presentation.components.header
import com.example.music.presentation.components.episodeCount
import com.example.music.presentation.components.hint
import com.example.music.presentation.components.sectionLabel
import com.example.music.presentation.components.songCount
import com.example.music.presentation.theme.WatchColors
import com.google.android.horologist.audio.AudioOutput
import com.google.android.horologist.audio.ui.VolumeViewModel

/**
 * "More" from the player: switch to another playlist, then playback controls (shuffle, repeat,
 * speed for podcasts, equalizer), add to playlist, up next and audio output. Works with or
 * without something playing.
 */
@Composable
fun PlayerOptionsScreen(
    player: PlayerConnection,
    volumeViewModel: VolumeViewModel,
    onAddToPlaylist: (String) -> Unit,
    onOpenQueue: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onPlayed: () -> Unit,
) {
    val graph = LocalContext.current.appGraph
    val state by player.state.collectAsStateWithLifecycle()
    val output by volumeViewModel.audioOutput.collectAsStateWithLifecycle()
    val library by graph.tracks.state.collectAsStateWithLifecycle()
    val playlists by graph.library.playlists.collectAsStateWithLifecycle()
    val favorites by graph.library.favorites.collectAsStateWithLifecycle()
    val eq by graph.equalizer.state.collectAsStateWithLifecycle()
    val contextTitle by graph.playbackContextTitle.collectAsStateWithLifecycle()
    val isPodcast = state.trackKey?.let { library.byKey[it] }?.isPodcast == true
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val music = remember(library.tracks) { LibraryLogic.music(library.tracks) }
    val episodes = remember(library.tracks) { LibraryLogic.podcastShows(library.tracks).flatMap { it.episodes } }
    val favoriteTracks = remember(library, favorites) { favorites.mapNotNull { library.byKey[it.trackKey] } }
    val resolved = remember(library, playlists) {
        playlists.map { it to LibraryLogic.resolve(it, library.byKey, library.tracks) }
    }

    fun start(tracks: List<Track>, title: String, shuffle: Boolean) {
        if (tracks.isEmpty()) return
        player.play(tracks, if (shuffle) tracks.indices.random() else 0, title, shuffle)
        onPlayed()
    }

    ListScaffold { t ->
        header(t, if (state.hasMedia) "Now playing" else "Options")
        if (state.title.isNotBlank()) hint(t, "title", state.title)

        // Switch playlist: the current one is marked with a check.
        sectionLabel(t, "switch", "Switch playlist")
        val sources = buildList {
            add(Triple(SmartList.MUSIC.title, Icons.Library, music to true))
            resolved.forEach { (p, tracks) -> add(Triple(p.name, Icons.PlaylistPlay, tracks to false)) }
            add(Triple(SmartList.FAVORITES.title, Icons.Heart, favoriteTracks to false))
            add(Triple(SmartList.PODCASTS.title, Icons.Podcasts, episodes to true))
        }
        sources.forEach { (title, icon, payload) ->
            val (tracks, shuffle) = payload
            val current = state.hasMedia && contextTitle == title
            item(key = "src:$title") {
                with(t) {
                    NavChip(
                        icon = if (current) Icons.Check else icon,
                        title = title,
                        subtitle = when {
                            current -> "Playing"
                            tracks.isEmpty() -> "Empty"
                            shuffle -> "Shuffle " + if (title == SmartList.PODCASTS.title) episodeCount(tracks.size) else songCount(tracks.size)
                            else -> songCount(tracks.size)
                        },
                        iconTint = when {
                            current -> primary
                            title == SmartList.FAVORITES.title -> WatchColors.Heart
                            else -> muted
                        },
                        onClick = { start(tracks, title, shuffle) },
                        modifier = Modifier.fillMaxWidth().morph(this@item),
                        transformation = surface(),
                    )
                }
            }
        }

        sectionLabel(t, "playback", "Playback")
        item(key = "shuffle") {
            with(t) {
                NavChip(
                    icon = if (state.shuffle) Icons.ShuffleOn else Icons.Shuffle,
                    title = "Shuffle",
                    subtitle = if (state.shuffle) "On" else "Off",
                    iconTint = if (state.shuffle) primary else muted,
                    onClick = player::toggleShuffle,
                    modifier = Modifier.fillMaxWidth().morph(this@item),
                    transformation = surface(),
                )
            }
        }
        item(key = "repeat") {
            with(t) {
                NavChip(
                    icon = when (state.repeatMode) {
                        Player.REPEAT_MODE_ONE -> Icons.RepeatOneOn
                        Player.REPEAT_MODE_ALL -> Icons.RepeatOn
                        else -> Icons.Repeat
                    },
                    title = "Repeat",
                    subtitle = when (state.repeatMode) {
                        Player.REPEAT_MODE_ONE -> "Current song"
                        Player.REPEAT_MODE_ALL -> "All songs"
                        else -> "Off"
                    },
                    iconTint = if (state.repeatMode != Player.REPEAT_MODE_OFF) primary else muted,
                    onClick = player::cycleRepeat,
                    modifier = Modifier.fillMaxWidth().morph(this@item),
                    transformation = surface(),
                )
            }
        }
        if (isPodcast) {
            item(key = "speed") {
                with(t) {
                    NavChip(
                        icon = Icons.Speed,
                        title = "Playback speed",
                        subtitle = "${"%.2f".format(state.speed).trimEnd('0').trimEnd('.')}x",
                        iconTint = if (state.speed != 1f) primary else muted,
                        onClick = player::cycleSpeed,
                        modifier = Modifier.fillMaxWidth().morph(this@item),
                        transformation = surface(),
                    )
                }
            }
        }
        item(key = "eq") {
            with(t) {
                NavChip(
                    icon = Icons.Equalizer,
                    title = "Equalizer",
                    subtitle = if (eq.enabled) eq.preset.label else "Off",
                    iconTint = if (eq.enabled) primary else muted,
                    onClick = onOpenEqualizer,
                    modifier = Modifier.fillMaxWidth().morph(this@item),
                    transformation = surface(),
                )
            }
        }
        if (state.hasMedia) {
            item(key = "playlist") {
                with(t) {
                    NavChip(
                        icon = Icons.PlaylistAdd,
                        title = "Add to playlist",
                        subtitle = null,
                        onClick = { state.trackKey?.let(onAddToPlaylist) },
                        modifier = Modifier.fillMaxWidth().morph(this@item),
                        transformation = surface(),
                    )
                }
            }
            item(key = "queue") {
                with(t) {
                    NavChip(
                        icon = Icons.Queue,
                        title = "Up next",
                        subtitle = if (state.count > 0) "${state.index + 1} of ${state.count}" else null,
                        onClick = onOpenQueue,
                        modifier = Modifier.fillMaxWidth().morph(this@item),
                        transformation = surface(),
                    )
                }
            }
        }
        item(key = "output") {
            with(t) {
                NavChip(
                    icon = Icons.Headphones,
                    title = "Audio output",
                    // Android names the device's own speaker "Phone", even on a watch.
                    subtitle = when {
                        output is AudioOutput.WatchSpeaker || output.name == "Phone" -> "Watch speaker"
                        else -> output.name.ifBlank { null }
                    },
                    onClick = { volumeViewModel.launchOutputSelection() },
                    modifier = Modifier.fillMaxWidth().morph(this@item),
                    transformation = surface(),
                )
            }
        }
    }
}

/** The play queue; tap a song to jump to it. */
@Composable
fun QueueScreen(player: PlayerConnection, onPlayed: () -> Unit) {
    val graph = LocalContext.current.appGraph
    val library by graph.tracks.state.collectAsStateWithLifecycle()
    val state by player.state.collectAsStateWithLifecycle()
    var keys by remember { mutableStateOf(player.queueKeys()) }
    LaunchedEffect(state.count, state.shuffle) { keys = player.queueKeys() }

    ListScaffold { t ->
        header(t, "Up next", Icons.Queue)
        if (keys.isEmpty()) hint(t, "empty", "Nothing queued")
        itemsIndexed(keys, key = { i, k -> "q:$i:$k" }) { index, key ->
            val track = library.byKey[key]
            with(t) {
                TrackChip(
                    track = track,
                    title = track?.title ?: "Unavailable",
                    subtitle = track?.artist.orEmpty(),
                    isCurrent = index == state.index,
                    onClick = { player.jumpTo(index); onPlayed() },
                    modifier = Modifier.fillMaxWidth().morph(this@itemsIndexed),
                    transformation = surface(),
                )
            }
        }
    }
}
