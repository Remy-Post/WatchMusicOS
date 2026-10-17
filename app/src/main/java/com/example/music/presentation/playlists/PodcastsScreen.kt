package com.example.music.presentation.playlists

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.example.music.appGraph
import com.example.music.data.LibraryLogic
import com.example.music.data.SmartList
import com.example.music.playback.PlayerConnection
import com.example.music.presentation.components.CoverThumb
import com.example.music.presentation.components.Icons
import com.example.music.presentation.components.ListScaffold
import com.example.music.presentation.components.episodeCount
import com.example.music.presentation.components.header
import com.example.music.presentation.components.hint
import com.example.music.presentation.components.showCount
import com.example.music.presentation.theme.WatchColors

/** The Podcasts folder: shuffle every episode, or open a show. */
@Composable
fun PodcastsScreen(
    player: PlayerConnection,
    onOpenShow: (String) -> Unit,
    onPlayed: () -> Unit,
) {
    val graph = LocalContext.current.appGraph
    val library by graph.tracks.state.collectAsStateWithLifecycle()
    val shows = remember(library.tracks) { LibraryLogic.podcastShows(library.tracks) }
    val allEpisodes = remember(shows) { shows.flatMap { it.episodes } }

    ListScaffold { t ->
        header(t, SmartList.PODCASTS.title, Icons.Podcasts)
        hint(t, "count", "${showCount(shows.size)} · ${episodeCount(allEpisodes.size)}")

        if (allEpisodes.isNotEmpty()) {
            item(key = "shuffle") {
                with(t) {
                    Button(
                        onClick = {
                            player.play(allEpisodes, allEpisodes.indices.random(), SmartList.PODCASTS.title, shuffle = true)
                            onPlayed()
                        },
                        modifier = Modifier.fillMaxWidth().morph(this@item),
                        transformation = surface(),
                        icon = { Icon(painterResource(Icons.Shuffle), contentDescription = null) },
                        label = { Text("Shuffle all") },
                    )
                }
            }
        }

        items(shows, key = { "show:" + it.name }) { show ->
            with(t) {
                Button(
                    onClick = { onOpenShow(SmartList.showId(show.name)) },
                    modifier = Modifier.fillMaxWidth().morph(this@items),
                    transformation = surface(),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = WatchColors.Surface),
                    // The newest episode's cover stands in for the show's artwork.
                    icon = { CoverThumb(show.episodes.firstOrNull(), 32.dp) },
                    secondaryLabel = {
                        Text(episodeCount(show.episodes.size), color = WatchColors.OnSurfaceVariant, maxLines = 1)
                    },
                    label = { Text(show.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                )
            }
        }

        if (shows.isEmpty()) {
            hint(t, "empty", "No podcasts on the watch yet.\nPut episodes in Podcasts/<Show>/ on the watch.")
        }
    }
}
