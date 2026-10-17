package com.example.music.presentation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.PagerState
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.example.music.appGraph
import com.example.music.data.ArtworkLoader
import com.example.music.data.SmartList
import com.example.music.playback.PlayerConnection
import com.example.music.presentation.components.rememberArtwork
import com.example.music.presentation.favorites.FavoritesScreen
import com.example.music.presentation.player.EqualizerScreen
import com.example.music.presentation.player.NowPlayingScreen
import com.example.music.presentation.player.PlayerOptionsScreen
import com.example.music.presentation.player.QueueScreen
import com.example.music.presentation.playlists.AddSongsScreen
import com.example.music.presentation.playlists.AddToPlaylistScreen
import com.example.music.presentation.playlists.PlaylistDetailScreen
import com.example.music.presentation.playlists.PlaylistsScreen
import com.example.music.presentation.playlists.PodcastsScreen
import com.example.music.presentation.theme.MusicTheme
import com.google.android.horologist.audio.ui.VolumeViewModel
import com.google.android.horologist.audio.ui.material3.VolumeScreen
import kotlinx.coroutines.launch

/** Home pager pages, left to right. The app opens on the player in the middle. */
object HomePage {
    const val FAVORITES = 0
    const val PLAYER = 1
    const val PLAYLISTS = 2
    const val COUNT = 3
}

private object Routes {
    const val HOME = "home"
    const val VOLUME = "volume"
    const val OPTIONS = "options"
    const val QUEUE = "queue"
    const val EQUALIZER = "equalizer"
    const val PLAYLIST = "playlist/{id}"
    const val ADD_SONGS = "addSongs/{id}"
    const val ADD_TO_PLAYLIST = "addToPlaylist/{key}"

    fun playlist(id: String) = "playlist/${Uri.encode(id)}"
    fun addSongs(id: String) = "addSongs/${Uri.encode(id)}"
    fun addToPlaylist(key: String) = "addToPlaylist/${Uri.encode(key)}"
}

@Composable
fun WatchMusicApp(
    player: PlayerConnection,
    volumeViewModel: VolumeViewModel,
    onRequestAccess: () -> Unit,
) {
    val graph = LocalContext.current.appGraph
    val now by player.state.collectAsStateWithLifecycle()
    val library by graph.tracks.state.collectAsStateWithLifecycle()
    val currentTrack = now.trackKey?.let { library.byKey[it] }
    val accent = rememberArtwork(currentTrack, 320)?.accent ?: ArtworkLoader.DefaultAccent

    val nav = rememberSwipeDismissableNavController()
    val pager = rememberPagerState(initialPage = HomePage.PLAYER) { HomePage.COUNT }
    val scope = rememberCoroutineScope()

    /** Back to the player page from anywhere, after the user picked something to play. */
    val showPlayer: () -> Unit = {
        nav.popBackStack(Routes.HOME, inclusive = false)
        scope.launch { pager.animateScrollToPage(HomePage.PLAYER) }
    }

    MusicTheme(accent = accent) {
        AppScaffold {
            SwipeDismissableNavHost(navController = nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomePager(
                        pager = pager,
                        player = player,
                        volumeViewModel = volumeViewModel,
                        nav = nav,
                        showPlayer = showPlayer,
                        onRequestAccess = onRequestAccess,
                    )
                }
                composable(Routes.VOLUME) {
                    VolumeScreen(volumeViewModel = volumeViewModel)
                }
                composable(Routes.OPTIONS) {
                    PlayerOptionsScreen(
                        player = player,
                        volumeViewModel = volumeViewModel,
                        onAddToPlaylist = { nav.navigate(Routes.addToPlaylist(it)) },
                        onOpenQueue = { nav.navigate(Routes.QUEUE) },
                        onOpenEqualizer = { nav.navigate(Routes.EQUALIZER) },
                        onPlayed = showPlayer,
                    )
                }
                composable(Routes.EQUALIZER) {
                    EqualizerScreen()
                }
                composable(Routes.QUEUE) {
                    QueueScreen(player = player, onPlayed = showPlayer)
                }
                composable(Routes.PLAYLIST) { entry ->
                    val id = entry.arguments?.getString("id")?.let(Uri::decode).orEmpty()
                    if (id == SmartList.PODCASTS.id) {
                        PodcastsScreen(
                            player = player,
                            onOpenShow = { nav.navigate(Routes.playlist(it)) },
                            onPlayed = showPlayer,
                        )
                        return@composable
                    }
                    PlaylistDetailScreen(
                        playlistId = id,
                        player = player,
                        onPlayed = showPlayer,
                        onAddSongs = { nav.navigate(Routes.addSongs(it)) },
                        onDeleted = { nav.popBackStack() },
                    )
                }
                composable(Routes.ADD_SONGS) { entry ->
                    AddSongsScreen(playlistId = entry.arguments?.getString("id")?.let(Uri::decode).orEmpty())
                }
                composable(Routes.ADD_TO_PLAYLIST) { entry ->
                    AddToPlaylistScreen(
                        trackKey = entry.arguments?.getString("key")?.let(Uri::decode).orEmpty(),
                        onDone = { nav.popBackStack() },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomePager(
    pager: PagerState,
    player: PlayerConnection,
    volumeViewModel: VolumeViewModel,
    nav: NavHostController,
    showPlayer: () -> Unit,
    onRequestAccess: () -> Unit,
) {
    HorizontalPagerScaffold(pagerState = pager) {
        HorizontalPager(state = pager) { page ->
            AnimatedPage(pageIndex = page, pagerState = pager) {
                when (page) {
                    HomePage.FAVORITES -> FavoritesScreen(player = player, onPlayed = showPlayer)
                    HomePage.PLAYER -> NowPlayingScreen(
                        player = player,
                        volumeViewModel = volumeViewModel,
                        isActivePage = pager.currentPage == HomePage.PLAYER,
                        onOpenVolume = { nav.navigate(Routes.VOLUME) },
                        onOpenOptions = { nav.navigate(Routes.OPTIONS) },
                        onOpenQueue = { nav.navigate(Routes.QUEUE) },
                        onRequestAccess = onRequestAccess,
                    )
                    HomePage.PLAYLISTS -> PlaylistsScreen(
                        onOpenPlaylist = { nav.navigate(Routes.playlist(it)) },
                        onRequestAccess = onRequestAccess,
                    )
                }
            }
        }
    }
}
