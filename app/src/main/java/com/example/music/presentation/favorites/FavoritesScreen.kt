package com.example.music.presentation.favorites

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.example.music.appGraph
import com.example.music.data.Favorite
import com.example.music.data.LibraryLogic
import com.example.music.data.SmartList
import com.example.music.playback.PlayerConnection
import com.example.music.presentation.components.Icons
import com.example.music.presentation.components.ListScaffold
import com.example.music.presentation.components.TrackChip
import com.example.music.presentation.components.header
import com.example.music.presentation.components.hint
import com.example.music.presentation.components.sectionLabel
import com.example.music.presentation.theme.WatchColors

/** Swipe right from the player: liked songs, newest first, grouped by when they were liked. */
@Composable
fun FavoritesScreen(player: PlayerConnection, onPlayed: () -> Unit) {
    val graph = LocalContext.current.appGraph
    val favorites by graph.library.favorites.collectAsStateWithLifecycle()
    val library by graph.tracks.state.collectAsStateWithLifecycle()
    val now by player.state.collectAsStateWithLifecycle()
    var pendingRemoval by remember { mutableStateOf<Favorite?>(null) }

    // Play order is the list order: newest like first. Missing files are skipped.
    val playable = favorites.mapNotNull { library.byKey[it.trackKey] }
    val groups = LibraryLogic.groupFavorites(favorites, System.currentTimeMillis())

    fun playFrom(fav: Favorite?, shuffle: Boolean = false) {
        if (playable.isEmpty()) return
        val start = fav?.let { f -> playable.indexOfFirst { it.key == f.trackKey } }?.coerceAtLeast(0) ?: 0
        player.play(playable, start, SmartList.FAVORITES.title, shuffle)
        onPlayed()
    }

    ListScaffold(
        edgeButton = if (playable.isNotEmpty()) {
            {
                EdgeButton(onClick = { playFrom(null, shuffle = true) }) {
                    Icon(painterResource(Icons.Shuffle), contentDescription = "Shuffle favorites", modifier = Modifier.size(22.dp))
                }
            }
        } else {
            null
        },
    ) { t ->
        header(t, "Favorites", Icons.Heart, WatchColors.Heart)
        if (favorites.isEmpty()) {
            hint(t, "empty", "No favorites yet.\nTap ♥ on the player to add the song here.")
        }
        groups.forEach { (group, favs) ->
            sectionLabel(t, group.name, group.label)
            items(favs, key = { "fav:" + it.trackKey }) { fav ->
                val track = library.byKey[fav.trackKey]
                with(t) {
                    TrackChip(
                        track = track,
                        title = track?.title ?: fav.title,
                        subtitle = if (track == null) "Not on watch" else track.artist,
                        enabled = track != null,
                        isCurrent = fav.trackKey == now.trackKey,
                        onClick = { playFrom(fav) },
                        onLongClick = { pendingRemoval = fav },
                        modifier = Modifier.fillMaxWidth().morph(this@items),
                        transformation = surface(),
                    )
                }
            }
        }
    }

    AlertDialog(
        visible = pendingRemoval != null,
        onDismissRequest = { pendingRemoval = null },
        icon = { Icon(painterResource(Icons.HeartOutline), null, tint = WatchColors.Heart) },
        title = { Text("Remove from Favorites?") },
        text = { Text(pendingRemoval?.title.orEmpty()) },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(onClick = {
                pendingRemoval?.let { graph.library.removeFavorite(it.trackKey) }
                pendingRemoval = null
            })
        },
    )
}
