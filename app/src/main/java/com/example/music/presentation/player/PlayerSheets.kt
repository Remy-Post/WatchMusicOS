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
import com.example.music.playback.PlayerConnection
import com.example.music.presentation.components.Icons
import com.example.music.presentation.components.ListScaffold
import com.example.music.presentation.components.NavChip
import com.example.music.presentation.components.TrackChip
import com.example.music.presentation.components.header
import com.example.music.presentation.components.hint
import com.google.android.horologist.audio.ui.VolumeViewModel

/** "More" from the player: shuffle, repeat, add to playlist, up next, audio output. */
@Composable
fun PlayerOptionsScreen(
    player: PlayerConnection,
    volumeViewModel: VolumeViewModel,
    onAddToPlaylist: (String) -> Unit,
    onOpenQueue: () -> Unit,
) {
    val state by player.state.collectAsStateWithLifecycle()
    val output by volumeViewModel.audioOutput.collectAsStateWithLifecycle()
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    ListScaffold { t ->
        header(t, "Now playing")
        if (state.title.isNotBlank()) hint(t, "title", state.title)

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
        item(key = "output") {
            with(t) {
                NavChip(
                    icon = Icons.Headphones,
                    title = "Audio output",
                    subtitle = output.name.ifBlank { null },
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
