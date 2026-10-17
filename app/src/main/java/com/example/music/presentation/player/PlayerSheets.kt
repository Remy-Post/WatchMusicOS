package com.example.music.presentation.player

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.example.music.playback.SleepMode
import com.example.music.presentation.components.Icons
import com.example.music.presentation.components.ListScaffold
import com.example.music.presentation.components.NavChip
import com.example.music.presentation.components.TrackChip
import com.example.music.presentation.components.episodeCount
import com.example.music.presentation.components.header
import com.example.music.presentation.components.hint
import com.example.music.presentation.components.sectionLabel
import com.example.music.presentation.components.songCount
import com.example.music.presentation.theme.WatchColors
import com.google.android.horologist.audio.AudioOutput
import com.google.android.horologist.audio.ui.VolumeViewModel
import kotlinx.coroutines.delay

/** One "Switch playlist" choice. */
private data class Source(val title: String, val tracks: List<Track>, val shuffle: Boolean, val countLabel: String)

/**
 * The Now playing sheet (tap the title on the player): switch playlist first, then playback
 * controls. Works whether or not something is playing.
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
    val sleep by graph.sleepTimer.state.collectAsStateWithLifecycle()
    val contextTitle by graph.playbackContextTitle.collectAsStateWithLifecycle()
    val isPodcast = state.trackKey?.let { library.byKey[it] }?.isPodcast == true
    val accent = MaterialTheme.colorScheme.primary
    val muted = WatchColors.OnSurfaceVariant

    val sources = remember(library, playlists, favorites) {
        val music = LibraryLogic.music(library.tracks)
        val episodes = LibraryLogic.podcastShows(library.tracks).flatMap { it.episodes }
        buildList {
            add(Source(SmartList.MUSIC.title, music, true, songCount(music.size)))
            playlists.forEach { p ->
                val tracks = LibraryLogic.resolve(p, library.byKey, library.tracks)
                add(Source(p.name, tracks, false, songCount(tracks.size)))
            }
            val favs = favorites.mapNotNull { library.byKey[it.trackKey] }
            add(Source(SmartList.FAVORITES.title, favs, false, songCount(favs.size)))
            add(Source(SmartList.PODCASTS.title, episodes, true, episodeCount(episodes.size)))
        }
    }
    // Refresh the sleep timer's minutes-left while the sheet is open.
    val now by produceState(System.currentTimeMillis(), sleep) {
        while (true) {
            value = System.currentTimeMillis()
            delay(15_000)
        }
    }

    ListScaffold { t ->
        header(t, if (state.hasMedia) "Now playing" else "Options")
        if (state.title.isNotBlank()) hint(t, "title", state.title)

        sectionLabel(t, "switch", "Switch playlist")
        sources.forEach { source ->
            val current = state.hasMedia && contextTitle == source.title
            item(key = "src:" + source.title) {
                with(t) {
                    NavChip(
                        title = source.title,
                        subtitle = when {
                            current -> "Playing"
                            source.tracks.isEmpty() -> "Empty"
                            source.shuffle -> "Shuffle " + source.countLabel
                            else -> source.countLabel
                        },
                        cover = source.tracks.firstOrNull(),
                        coverMonogram = source.title,
                        selected = current,
                        onClick = {
                            if (source.tracks.isNotEmpty()) {
                                val start = if (source.shuffle) source.tracks.indices.random() else 0
                                player.play(source.tracks, start, source.title, source.shuffle)
                                onPlayed()
                            }
                        },
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
                    title = "Shuffle",
                    subtitle = if (state.shuffle) "On" else "Off",
                    icon = Icons.Shuffle,
                    iconTint = if (state.shuffle) accent else muted,
                    onClick = player::toggleShuffle,
                    modifier = Modifier.fillMaxWidth().morph(this@item),
                    transformation = surface(),
                )
            }
        }
        item(key = "repeat") {
            with(t) {
                NavChip(
                    title = "Repeat",
                    subtitle = when (state.repeatMode) {
                        Player.REPEAT_MODE_ONE -> "This song"
                        Player.REPEAT_MODE_ALL -> "All"
                        else -> "Off"
                    },
                    icon = if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.RepeatOne else Icons.Repeat,
                    iconTint = if (state.repeatMode != Player.REPEAT_MODE_OFF) accent else muted,
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
                        title = "Speed",
                        subtitle = "${"%.2f".format(state.speed).trimEnd('0').trimEnd('.')}x",
                        icon = Icons.Speed,
                        iconTint = if (state.speed != 1f) accent else muted,
                        onClick = player::cycleSpeed,
                        modifier = Modifier.fillMaxWidth().morph(this@item),
                        transformation = surface(),
                    )
                }
            }
        }
        item(key = "sleep") {
            with(t) {
                NavChip(
                    title = "Sleep timer",
                    subtitle = graph.sleepTimer.describe(sleep, now),
                    icon = Icons.SleepTimer,
                    iconTint = if (sleep.mode != SleepMode.OFF) accent else muted,
                    onClick = graph.sleepTimer::cycle,
                    modifier = Modifier.fillMaxWidth().morph(this@item),
                    transformation = surface(),
                )
            }
        }
        item(key = "eq") {
            with(t) {
                NavChip(
                    title = "Equalizer",
                    subtitle = if (eq.enabled) eq.preset.label else "Off",
                    icon = Icons.Equalizer,
                    iconTint = if (eq.enabled) accent else muted,
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
                        title = "Add to playlist",
                        subtitle = null,
                        icon = Icons.PlaylistAdd,
                        onClick = { state.trackKey?.let(onAddToPlaylist) },
                        modifier = Modifier.fillMaxWidth().morph(this@item),
                        transformation = surface(),
                    )
                }
            }
            item(key = "queue") {
                with(t) {
                    NavChip(
                        title = "Up next",
                        subtitle = if (state.count > 0) "${state.index + 1} of ${state.count}" else null,
                        icon = Icons.Queue,
                        onClick = onOpenQueue,
                        modifier = Modifier.fillMaxWidth().morph(this@item),
                        transformation = surface(),
                    )
                }
            }
        }
        item(key = "output") {
            with(t) {
                // Android names the device's own speaker "Phone", even on a watch.
                val kind = OutputKind.of(output is AudioOutput.WatchSpeaker || output is AudioOutput.None, output.name)
                NavChip(
                    title = "Audio output",
                    subtitle = if (kind == OutputKind.WATCH) "Watch speaker" else output.name,
                    icon = when (kind) {
                        OutputKind.WATCH -> Icons.Watch
                        OutputKind.EARBUDS -> Icons.Earbuds
                        OutputKind.HEADPHONES -> Icons.Headphones
                        OutputKind.SPEAKER -> Icons.Speaker
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
        header(t, "Up next")
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
