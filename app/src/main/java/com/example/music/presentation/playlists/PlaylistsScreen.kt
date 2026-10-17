package com.example.music.presentation.playlists

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import com.example.music.appGraph
import com.example.music.data.LibraryLogic
import com.example.music.data.SmartList
import com.example.music.playback.PlayerConnection
import com.example.music.presentation.components.Icons
import com.example.music.presentation.components.ListScaffold
import com.example.music.presentation.components.NavChip
import com.example.music.presentation.components.header
import com.example.music.presentation.components.hint
import com.example.music.presentation.components.rememberTextInput
import com.example.music.presentation.components.showCount
import com.example.music.presentation.components.songCount

/** Route id of the Playlists folder (Favorites plus every playlist). */
const val PLAYLISTS_FOLDER_ID = "folder:playlists"

/**
 * Swipe left from the player: three folders. Music is every song, Podcasts holds a folder per
 * show, Playlists holds Favorites and the playlists. Rows show album art from what's inside.
 */
@Composable
fun LibraryScreen(
    player: PlayerConnection,
    onOpen: (String) -> Unit,
    onRequestAccess: () -> Unit,
) {
    val graph = LocalContext.current.appGraph
    val library by graph.tracks.state.collectAsStateWithLifecycle()
    val playlists by graph.library.playlists.collectAsStateWithLifecycle()
    val favorites by graph.library.favorites.collectAsStateWithLifecycle()
    val now by player.state.collectAsStateWithLifecycle()

    val music = remember(library.tracks) { library.tracks.filter { !it.isPodcast } }
    val shows = remember(library.tracks) { LibraryLogic.podcastShows(library.tracks) }
    val current = now.trackKey?.let { library.byKey[it] }
    // Covers reflect what you're listening to: the current song or episode when there is one.
    val musicCover = current?.takeIf { !it.isPodcast }
        ?: favorites.firstNotNullOfOrNull { library.byKey[it.trackKey] }
        ?: music.firstOrNull()
    val podcastCover = current?.takeIf { it.isPodcast }
        ?: graph.progress.inProgress().firstNotNullOfOrNull { library.byKey[it.first] }
        ?: shows.firstOrNull()?.episodes?.firstOrNull()
    val playlistsCover = favorites.firstNotNullOfOrNull { library.byKey[it.trackKey] } ?: musicCover

    ListScaffold { t ->
        header(t, "Library")

        if (!library.hasSharedAccess) {
            item(key = "access") {
                with(t) {
                    NavChip(
                        title = "Allow music access",
                        subtitle = "Show the songs on this watch",
                        onClick = onRequestAccess,
                        modifier = Modifier.fillMaxWidth().morph(this@item),
                        transformation = surface(),
                    )
                }
            }
        }
        item(key = SmartList.MUSIC.id) {
            with(t) {
                NavChip(
                    title = SmartList.MUSIC.title,
                    subtitle = songCount(music.size),
                    cover = musicCover,
                    onClick = { onOpen(SmartList.MUSIC.id) },
                    modifier = Modifier.fillMaxWidth().morph(this@item),
                    transformation = surface(),
                )
            }
        }
        item(key = SmartList.PODCASTS.id) {
            with(t) {
                NavChip(
                    title = SmartList.PODCASTS.title,
                    subtitle = showCount(shows.size),
                    cover = podcastCover,
                    onClick = { onOpen(SmartList.PODCASTS.id) },
                    modifier = Modifier.fillMaxWidth().morph(this@item),
                    transformation = surface(),
                )
            }
        }
        item(key = PLAYLISTS_FOLDER_ID) {
            with(t) {
                NavChip(
                    title = "Playlists",
                    subtitle = (playlists.size + 1).let { if (it == 1) "1 playlist" else "$it playlists" },
                    cover = playlistsCover,
                    onClick = { onOpen(PLAYLISTS_FOLDER_ID) },
                    modifier = Modifier.fillMaxWidth().morph(this@item),
                    transformation = surface(),
                )
            }
        }
    }
}

/** The Playlists folder: Favorites first, then preconfigured and your own; + makes a new one. */
@Composable
fun PlaylistsFolderScreen(onOpenPlaylist: (String) -> Unit) {
    val graph = LocalContext.current.appGraph
    val library by graph.tracks.state.collectAsStateWithLifecycle()
    val playlists by graph.library.playlists.collectAsStateWithLifecycle()
    val favorites by graph.library.favorites.collectAsStateWithLifecycle()
    val resolved = remember(library, playlists) {
        playlists.map { it to LibraryLogic.resolve(it, library.byKey, library.tracks) }
    }
    val favoriteTracks = remember(library, favorites) { favorites.mapNotNull { library.byKey[it.trackKey] } }

    val newPlaylist = rememberTextInput("Playlist name") { name ->
        onOpenPlaylist(graph.library.createPlaylist(name).id)
    }

    ListScaffold(
        edgeButton = {
            EdgeButton(onClick = newPlaylist) {
                Icon(Icons.Add, contentDescription = "New playlist", modifier = Modifier.size(22.dp))
            }
        },
    ) { t ->
        header(t, "Playlists")
        item(key = SmartList.FAVORITES.id) {
            with(t) {
                NavChip(
                    title = SmartList.FAVORITES.title,
                    subtitle = songCount(favoriteTracks.size),
                    cover = favoriteTracks.firstOrNull(),
                    coverMonogram = SmartList.FAVORITES.title,
                    onClick = { onOpenPlaylist(SmartList.FAVORITES.id) },
                    modifier = Modifier.fillMaxWidth().morph(this@item),
                    transformation = surface(),
                )
            }
        }
        items(resolved, key = { it.first.id }) { (playlist, tracks) ->
            with(t) {
                NavChip(
                    title = playlist.name,
                    subtitle = songCount(tracks.size),
                    cover = tracks.firstOrNull(),
                    coverMonogram = playlist.name,
                    onClick = { onOpenPlaylist(playlist.id) },
                    modifier = Modifier.fillMaxWidth().morph(this@items),
                    transformation = surface(),
                )
            }
        }
        if (playlists.isEmpty()) hint(t, "none", "Tap + below to make a playlist")
    }
}
