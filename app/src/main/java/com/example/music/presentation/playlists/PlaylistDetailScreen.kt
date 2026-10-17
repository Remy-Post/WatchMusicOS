package com.example.music.presentation.playlists

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
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
import com.example.music.presentation.components.hint
import com.example.music.presentation.components.rememberTextInput
import com.example.music.presentation.components.episodeCount
import com.example.music.presentation.components.episodeSubtitle
import com.example.music.presentation.components.songCount
import com.example.music.presentation.theme.WatchColors

/** Songs of one playlist or smart list, with play/shuffle and (for stored playlists) editing. */
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    player: PlayerConnection,
    onPlayed: () -> Unit,
    onAddSongs: (String) -> Unit,
    onDeleted: () -> Unit,
) {
    val graph = LocalContext.current.appGraph
    val library by graph.tracks.state.collectAsStateWithLifecycle()
    val playlists by graph.library.playlists.collectAsStateWithLifecycle()
    val favorites by graph.library.favorites.collectAsStateWithLifecycle()
    val now by player.state.collectAsStateWithLifecycle()

    val progress by graph.progress.positions.collectAsStateWithLifecycle()

    val smart = SmartList.fromId(playlistId)
    val show = SmartList.showFromId(playlistId)
    val playlist = playlists.firstOrNull { it.id == playlistId }
    val title = smart?.title ?: show ?: playlist?.name ?: return
    val tracks: List<Track> = remember(library, favorites, playlist, playlistId) {
        when {
            show != null -> LibraryLogic.episodes(library.tracks, show)
            smart == SmartList.MUSIC -> LibraryLogic.music(library.tracks)
            smart == SmartList.RECENTLY_ADDED -> LibraryLogic.recentlyAdded(library.tracks)
            smart == SmartList.FAVORITES -> favorites.mapNotNull { library.byKey[it.trackKey] }
            playlist != null -> LibraryLogic.resolve(playlist, library.byKey, library.tracks)
            else -> emptyList()
        }
    }
    val editable = playlist != null

    var pendingRemoval by remember { mutableStateOf<Track?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val rename = rememberTextInput("Playlist name") { graph.library.renamePlaylist(playlistId, it) }

    fun play(start: Int, shuffle: Boolean) {
        if (tracks.isEmpty()) return
        player.play(tracks, start, title, shuffle)
        onPlayed()
    }

    ListScaffold { t ->
        header(t, title, if (show != null) Icons.Podcasts else null)
        hint(t, "count", if (show != null) episodeCount(tracks.size) else songCount(tracks.size))

        if (tracks.isNotEmpty()) {
            // Shuffle sits on top; tapping any row below plays in order from there.
            item(key = "shuffle") {
                with(t) {
                    Button(
                        onClick = { play(tracks.indices.random(), shuffle = true) },
                        modifier = Modifier.fillMaxWidth().morph(this@item),
                        transformation = surface(),
                        icon = { Icon(painterResource(Icons.Shuffle), contentDescription = null) },
                        label = { Text("Shuffle") },
                    )
                }
            }
        }

        itemsIndexed(tracks, key = { _, track -> "track:" + track.key }) { index, track ->
            with(t) {
                TrackChip(
                    track = track,
                    title = track.title,
                    subtitle = if (track.isPodcast) episodeSubtitle(track, progress[track.key] ?: 0L) else track.artist,
                    isCurrent = track.key == now.trackKey,
                    // Episode titles are long; give them two lines.
                    titleLines = if (track.isPodcast) 2 else 1,
                    onClick = { play(index, shuffle = false) },
                    onLongClick = if (editable) ({ pendingRemoval = track }) else null,
                    modifier = Modifier.fillMaxWidth().morph(this@itemsIndexed),
                    transformation = surface(),
                )
            }
        }

        if (tracks.isEmpty()) {
            hint(
                t,
                "empty",
                when {
                    editable -> "This playlist is empty."
                    smart == SmartList.FAVORITES -> "Tap the heart on the player to add songs."
                    else -> "No songs on the watch yet."
                },
            )
        }

        if (editable) {
            item(key = "add") {
                with(t) {
                    NavChip(Icons.PlaylistAdd, "Add songs", null, { onAddSongs(playlistId) },
                        Modifier.fillMaxWidth().morph(this@item), transformation = surface())
                }
            }
            item(key = "rename") {
                with(t) {
                    NavChip(Icons.Edit, "Rename", null, rename,
                        Modifier.fillMaxWidth().morph(this@item), transformation = surface())
                }
            }
            item(key = "delete") {
                with(t) {
                    NavChip(Icons.Delete, "Delete playlist", null, { confirmDelete = true },
                        Modifier.fillMaxWidth().morph(this@item), iconTint = WatchColors.Heart, transformation = surface())
                }
            }
        }
    }

    AlertDialog(
        visible = pendingRemoval != null,
        onDismissRequest = { pendingRemoval = null },
        icon = { Icon(painterResource(Icons.Remove), null) },
        title = { Text("Remove from $title?") },
        text = { Text(pendingRemoval?.title.orEmpty()) },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(onClick = {
                pendingRemoval?.let { graph.library.removeFromPlaylist(playlistId, it.key) }
                pendingRemoval = null
            })
        },
    )

    AlertDialog(
        visible = confirmDelete,
        onDismissRequest = { confirmDelete = false },
        icon = { Icon(painterResource(Icons.Delete), null, tint = WatchColors.Heart) },
        title = { Text("Delete $title?") },
        text = { Text("Songs stay on your watch.") },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(onClick = {
                confirmDelete = false
                graph.library.deletePlaylist(playlistId)
                onDeleted()
            })
        },
    )
}
