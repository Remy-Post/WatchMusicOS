package com.example.music.presentation.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.Text
import com.example.music.appGraph
import com.example.music.data.LibraryLogic
import com.example.music.data.SmartList
import com.example.music.playback.NowPlayingState
import com.example.music.playback.PlayerConnection
import com.example.music.presentation.components.CoverBackground
import com.example.music.presentation.components.Icons
import com.example.music.presentation.components.rememberArtwork
import com.example.music.presentation.theme.LocalAccent
import com.example.music.presentation.theme.WatchColors
import com.google.android.horologist.audio.ui.VolumeViewModel
import com.google.android.horologist.audio.ui.material3.VolumeLevelIndicator
import com.google.android.horologist.audio.ui.material3.volumeRotaryBehavior
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The player, laid out for a 1.47" round screen the way One UI Watch's Music app is:
 * progress around the bezel, title and artist in the middle, transport controls below, and a
 * utility row (volume · like · more) hugging the bottom curve. Turning the bezel changes volume.
 */
@Composable
fun NowPlayingScreen(
    player: PlayerConnection,
    volumeViewModel: VolumeViewModel,
    isActivePage: Boolean,
    onOpenVolume: () -> Unit,
    onOpenOptions: () -> Unit,
    onOpenQueue: () -> Unit,
    onRequestAccess: () -> Unit,
) {
    val graph = LocalContext.current.appGraph
    val state by player.state.collectAsStateWithLifecycle()
    val library by graph.tracks.state.collectAsStateWithLifecycle()
    val favorites by graph.library.favorites.collectAsStateWithLifecycle()
    val contextTitle by graph.playbackContextTitle.collectAsStateWithLifecycle()
    val track = state.trackKey?.let { library.byKey[it] }
    val artwork = rememberArtwork(track, 320)
    val liked = state.trackKey != null && favorites.any { it.trackKey == state.trackKey }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isActivePage) { if (isActivePage) runCatching { focusRequester.requestFocus() } }

    val progress by produceState(0f, state.trackKey, state.isPlaying) {
        while (true) {
            // Before the first play after a restore the player hasn't probed the file yet; use the tag length.
            val duration = player.durationMs().takeIf { it > 0 } ?: track?.durationMs ?: 0L
            value = if (duration > 0) (player.positionMs().toFloat() / duration).coerceIn(0f, 1f) else 0f
            if (!state.isPlaying) break
            delay(250)
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .rotaryScrollable(
                volumeRotaryBehavior(
                    volumeUiStateProvider = { volumeViewModel.volumeUiState.value },
                    onRotaryVolumeInput = { volumeViewModel.setVolume(it) },
                ),
                focusRequester = focusRequester,
            ),
    ) {
        val d = maxWidth
        CoverBackground(artwork)

        // Song progress around the bezel.
        if (state.hasMedia) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize().padding(3.dp),
                strokeWidth = 5.dp,
                colors = ProgressIndicatorDefaults.colors(
                    indicatorColor = LocalAccent.current,
                    trackColor = WatchColors.RingTrack,
                ),
            )
        }

        Column(
            Modifier.fillMaxSize().padding(horizontal = d * 0.1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(d * 0.14f))
            SourceLabel(if (state.hasMedia) contextTitle.ifBlank { "Watch" } else "Watch")
            Spacer(Modifier.height(d * 0.025f))

            val noMusic = library.hasScannedOnce && library.tracks.isEmpty()
            Column(
                Modifier
                    .width(d * 0.74f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = state.hasMedia,
                        onClick = onOpenQueue,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContent(
                    targetState = state.title to state.artist,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "title",
                ) { (title, artist) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = when {
                                state.hasMedia -> title
                                noMusic -> "No music on watch"
                                else -> "Nothing playing"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1500),
                        )
                        Text(
                            text = when {
                                state.hasMedia -> artist
                                !library.hasSharedAccess && noMusic -> "Allow access to see your songs"
                                noMusic -> "Add songs with Galaxy Wearable"
                                else -> "Tap play to shuffle"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = WatchColors.OnSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Spacer(Modifier.height(d * 0.04f))

            TransportRow(
                d = d,
                state = state,
                onPrevious = player::previous,
                onNext = player::next,
                onPlayPause = {
                    when {
                        state.hasMedia -> player.togglePlayPause()
                        !library.hasSharedAccess && library.tracks.isEmpty() -> onRequestAccess()
                        library.tracks.isNotEmpty() -> player.play(
                            LibraryLogic.allSongs(library.tracks),
                            startIndex = (library.tracks.indices).random(),
                            contextTitle = SmartList.ALL_SONGS.title,
                            shuffle = true,
                        )
                    }
                },
            )

            Spacer(Modifier.height(d * 0.025f))

            UtilityRow(
                d = d,
                liked = liked,
                enabled = state.hasMedia,
                onVolume = onOpenVolume,
                onLike = { track?.let { graph.library.toggleFavorite(it) } },
                onMore = onOpenOptions,
            )
        }

        // Bezel volume feedback on the left edge, like the system's own indicator.
        VolumeLevelIndicator(
            volumeUiState = { volumeViewModel.volumeUiState.value },
            displayIndicatorEvents = volumeViewModel.displayIndicatorEvents,
        )
    }
}

@Composable
private fun SourceLabel(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(Icons.Watch),
            contentDescription = null,
            tint = WatchColors.OnSurfaceVariant,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = WatchColors.OnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TransportRow(
    d: Dp,
    state: NowPlayingState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(d * 0.035f),
    ) {
        IconButton(
            onClick = { haptics.performHapticFeedback(HapticFeedbackType.ContextClick); onPrevious() },
            enabled = state.hasMedia,
            modifier = Modifier.size(d * 0.2f),
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
        ) {
            Icon(painterResource(Icons.SkipPrevious), "Previous", modifier = Modifier.size(d * 0.14f))
        }
        FilledIconButton(
            onClick = { haptics.performHapticFeedback(HapticFeedbackType.ContextClick); onPlayPause() },
            modifier = Modifier.size(d * 0.27f),
            shapes = IconButtonDefaults.animatedShapes(),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.White.copy(alpha = 0.18f),
                contentColor = Color.White,
            ),
        ) {
            Icon(
                painterResource(if (state.isPlaying) Icons.Pause else Icons.Play),
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(d * 0.16f),
            )
        }
        IconButton(
            onClick = { haptics.performHapticFeedback(HapticFeedbackType.ContextClick); onNext() },
            enabled = state.hasMedia,
            modifier = Modifier.size(d * 0.2f),
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
        ) {
            Icon(painterResource(Icons.SkipNext), "Next", modifier = Modifier.size(d * 0.14f))
        }
    }
}

@Composable
private fun UtilityRow(
    d: Dp,
    liked: Boolean,
    enabled: Boolean,
    onVolume: () -> Unit,
    onLike: () -> Unit,
    onMore: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val pop = remember { Animatable(1f) }
    val small = d * 0.17f
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(d * 0.06f),
    ) {
        // Side buttons sit a little higher than the heart to follow the bottom curve.
        IconButton(onClick = onVolume, modifier = Modifier.size(small)) {
            Icon(painterResource(Icons.Volume), "Volume", tint = WatchColors.OnSurfaceVariant, modifier = Modifier.size(d * 0.1f))
        }
        Box(Modifier.padding(top = d * 0.02f)) {
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(if (liked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    onLike()
                    scope.launch {
                        pop.snapTo(0.7f)
                        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium))
                    }
                },
                enabled = enabled,
                modifier = Modifier
                    .size(small)
                    .semantics { contentDescription = if (liked) "Remove from Favorites" else "Add to Favorites" },
                shapes = IconButtonDefaults.shapes(CircleShape),
            ) {
                Icon(
                    painterResource(if (liked) Icons.Heart else Icons.HeartOutline),
                    contentDescription = null,
                    tint = if (liked) WatchColors.Heart else WatchColors.OnSurfaceVariant,
                    modifier = Modifier.size(d * 0.105f).scale(pop.value),
                )
            }
        }
        IconButton(onClick = onMore, enabled = enabled, modifier = Modifier.size(small)) {
            Icon(painterResource(Icons.More), "More options", tint = WatchColors.OnSurfaceVariant, modifier = Modifier.size(d * 0.1f))
        }
    }
}
