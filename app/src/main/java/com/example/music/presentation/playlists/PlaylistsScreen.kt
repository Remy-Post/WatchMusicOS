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
import com.example.music.appGraph
import com.example.music.data.LibraryLogic
import com.example.music.data.PlaylistOrigin
import com.example.music.data.SmartList
import com.example.music.presentation.components.Icons
import com.example.music.presentation.components.ListScaffold
import com.example.music.presentation.components.NavChip
import com.example.music.presentation.components.header
import com.example.music.presentation.components.hint
import com.example.music.presentation.components.rememberTextInput
import com.example.music.presentation.components.sectionLabel
import com.example.music.presentation.components.songCount
import com.example.music.presentation.theme.WatchColors

/** Swipe left from the player: smart lists, then preconfigured and on-watch playlists. */
@Composable
fun PlaylistsScreen(
    onOpenPlaylist: (String) -> Unit,
    onRequestAccess: () -> Unit,
) {
    val graph = LocalContext.current.appGraph
    val library by graph.tracks.state.collectAsStateWithLifecycle()
    val playlists by graph.library.playlists.collectAsStateWithLifecycle()
    val favorites by graph.library.favorites.collectAsStateWithLifecycle()

    val newPlaylist = rememberTextInput("Playlist name") { name ->
        val created = graph.library.createPlaylist(name)
        onOpenPlaylist(created.id)
    }

    ListScaffold(
        edgeButton = {
            EdgeButton(onClick = newPlaylist) {
                Icon(painterResource(Icons.Add), contentDescription = "New playlist", modifier = Modifier.size(24.dp))
            }
        },
    ) { t ->
        header(t, "Playlists", Icons.Queue)

        if (!library.hasSharedAccess) {
            item(key = "access") {
                with(t) {
                    NavChip(
                        icon = Icons.Unlock,
                        title = "Allow music access",
                        subtitle = "Show songs saved on the watch",
                        onClick = onRequestAccess,
                        modifier = Modifier.fillMaxWidth().morph(this@item),
                        transformation = surface(),
                    )
                }
            }
        }

        item(key = SmartList.ALL_SONGS.id) {
            with(t) {
                NavChip(
                    icon = Icons.Library,
                    title = SmartList.ALL_SONGS.title,
                    subtitle = songCount(library.tracks.size),
                    onClick = { onOpenPlaylist(SmartList.ALL_SONGS.id) },
                    modifier = Modifier.fillMaxWidth().morph(this@item),
                    transformation = surface(),
                )
            }
        }
        item(key = SmartList.RECENTLY_ADDED.id) {
            with(t) {
                NavChip(
                    icon = Icons.Recent,
                    title = SmartList.RECENTLY_ADDED.title,
                    subtitle = songCount(LibraryLogic.recentlyAdded(library.tracks).size),
                    onClick = { onOpenPlaylist(SmartList.RECENTLY_ADDED.id) },
                    modifier = Modifier.fillMaxWidth().morph(this@item),
                    transformation = surface(),
                )
            }
        }
        item(key = SmartList.FAVORITES.id) {
            with(t) {
                NavChip(
                    icon = Icons.Heart,
                    iconTint = WatchColors.Heart,
                    title = SmartList.FAVORITES.title,
                    subtitle = songCount(favorites.count { it.trackKey in library.byKey }),
                    onClick = { onOpenPlaylist(SmartList.FAVORITES.id) },
                    modifier = Modifier.fillMaxWidth().morph(this@item),
                    transformation = surface(),
                )
            }
        }

        if (playlists.isNotEmpty()) sectionLabel(t, "mine", "My playlists")
        items(playlists, key = { it.id }) { playlist ->
            val count = LibraryLogic.resolve(playlist, library.byKey, library.tracks).size
            with(t) {
                NavChip(
                    icon = if (playlist.origin == PlaylistOrigin.PRESET) Icons.PlaylistPlay else Icons.Queue,
                    title = playlist.name,
                    subtitle = songCount(count),
                    onClick = { onOpenPlaylist(playlist.id) },
                    modifier = Modifier.fillMaxWidth().morph(this@items),
                    transformation = surface(),
                )
            }
        }
        if (playlists.isEmpty()) hint(t, "none", "Tap + to make a playlist")
    }
}
