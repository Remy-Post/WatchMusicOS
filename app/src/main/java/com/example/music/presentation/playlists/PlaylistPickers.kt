package com.example.music.presentation.playlists

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import com.example.music.appGraph
import com.example.music.data.LibraryLogic
import com.example.music.presentation.components.Icons
import com.example.music.presentation.components.ListScaffold
import com.example.music.presentation.components.NavChip
import com.example.music.presentation.components.TrackChip
import com.example.music.presentation.components.header
import com.example.music.presentation.components.hint
import com.example.music.presentation.components.rememberTextInput
import com.example.music.presentation.components.songCount

/** "Add to playlist" for one song: tap a playlist to add or remove it; + makes a new one. */
@Composable
fun AddToPlaylistScreen(trackKey: String, onDone: () -> Unit) {
    val graph = LocalContext.current.appGraph
    val library by graph.tracks.state.collectAsStateWithLifecycle()
    val playlists by graph.library.playlists.collectAsStateWithLifecycle()
    val track = library.byKey[trackKey]

    val create = rememberTextInput("Playlist name") { name ->
        graph.library.createPlaylist(name, listOf(trackKey))
        onDone()
    }

    ListScaffold(
        edgeButton = {
            EdgeButton(onClick = create) {
                Icon(painterResource(Icons.Add), contentDescription = "New playlist", modifier = Modifier.size(24.dp))
            }
        },
    ) { t ->
        header(t, "Add to playlist", Icons.PlaylistAdd)
        track?.let { hint(t, "song", it.title) }
        items(playlists, key = { it.id }) { playlist ->
            val keys = LibraryLogic.resolve(playlist, library.byKey, library.tracks).map { it.key }
            val inside = trackKey in keys
            with(t) {
                NavChip(
                    icon = if (inside) Icons.PlaylistAdded else Icons.Queue,
                    title = playlist.name,
                    subtitle = if (inside) "Added" else songCount(keys.size),
                    iconTint = if (inside) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = {
                        if (inside) graph.library.removeFromPlaylist(playlist.id, trackKey)
                        else graph.library.addToPlaylist(playlist.id, listOf(trackKey))
                    },
                    modifier = Modifier.fillMaxWidth().morph(this@items),
                    transformation = surface(),
                )
            }
        }
        if (playlists.isEmpty()) hint(t, "none", "Tap + to create one")
    }
}

/** Pick songs for a playlist: every song on the watch, with a check on the ones inside. */
@Composable
fun AddSongsScreen(playlistId: String) {
    val graph = LocalContext.current.appGraph
    val library by graph.tracks.state.collectAsStateWithLifecycle()
    val playlists by graph.library.playlists.collectAsStateWithLifecycle()
    val playlist = playlists.firstOrNull { it.id == playlistId } ?: return
    val inside = LibraryLogic.resolve(playlist, library.byKey, library.tracks).map { it.key }.toSet()
    val all = LibraryLogic.allSongs(library.tracks)

    ListScaffold { t ->
        header(t, playlist.name)
        hint(t, "count", "${inside.size} of ${all.size} selected")
        items(all, key = { "pick:" + it.key }) { track ->
            val selected = track.key in inside
            with(t) {
                TrackChip(
                    track = track,
                    title = track.title,
                    subtitle = track.artist,
                    isCurrent = selected,
                    onClick = {
                        if (selected) graph.library.removeFromPlaylist(playlistId, track.key)
                        else graph.library.addToPlaylist(playlistId, listOf(track.key))
                    },
                    modifier = Modifier.fillMaxWidth().morph(this@items),
                    transformation = surface(),
                    trailing = {
                        Icon(
                            painterResource(if (selected) Icons.Check else Icons.Add),
                            contentDescription = if (selected) "Selected" else "Not selected",
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
            }
        }
    }
}
