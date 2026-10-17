package com.example.music.presentation.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.wear.compose.material3.LevelIndicator
import androidx.wear.compose.material3.LevelIndicatorDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.Text
import com.example.music.appGraph
import com.example.music.data.LibraryLogic
import com.example.music.data.SmartList
import com.example.music.data.Track
import com.example.music.data.TrackSource
import com.example.music.playback.NowPlayingState
import com.example.music.playback.PlayerConnection
import com.example.music.presentation.components.CoverBackground
import com.example.music.presentation.components.Icons
import com.example.music.presentation.components.rememberArtwork
import com.example.music.presentation.theme.LocalAccent
import com.example.music.presentation.theme.WatchColors
import com.google.android.horologist.audio.AudioOutput
import com.google.android.horologist.audio.ui.VolumeViewModel
import com.google.android.horologist.audio.ui.material3.VolumeLevelIndicator
import com.google.android.horologist.audio.ui.material3.volumeRotaryBehavior
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The player. Deliberately sparse: the song title, previous / play / next, then volume and
 * heart. The cover fills the background and the bezel ring shows progress.
 *
 * Gestures: slide along the left edge for volume, the right edge for brightness, double-tap
 * anywhere to play or pause, tap the title for the Now playing sheet. The small icon at the top
 * shows where the sound is going and opens the output picker.
 */
@Composable
fun NowPlayingScreen(
    player: PlayerConnection,
    volumeViewModel: VolumeViewModel,
    brightness: BrightnessController,
    isActivePage: Boolean,
    onOpenVolume: () -> Unit,
    onOpenOptions: () -> Unit,
    onRequestAccess: () -> Unit,
) {
    val graph = LocalContext.current.appGraph
    val state by player.state.collectAsStateWithLifecycle()
    val library by graph.tracks.state.collectAsStateWithLifecycle()
    val favorites by graph.library.favorites.collectAsStateWithLifecycle()
    val output by volumeViewModel.audioOutput.collectAsStateWithLifecycle()
    val track = state.trackKey?.let { library.byKey[it] }
    val artwork = rememberArtwork(track, 320)
    val liked = state.trackKey != null && favorites.any { it.trackKey == state.trackKey }
    val haptics = LocalHapticFeedback.current

    // Short confirmation in place of the title ("Added to Favorites").
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(1_600)
            notice = null
        }
    }
    var brightnessTouch by remember { mutableIntStateOf(0) }
    var showBrightness by remember { mutableStateOf(false) }
    LaunchedEffect(brightnessTouch) {
        if (brightnessTouch > 0) {
            showBrightness = true
            delay(1_500)
            showBrightness = false
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isActivePage) { if (isActivePage) runCatching { focusRequester.requestFocus() } }

    val progress by produceState(0f, state.trackKey, state.isPlaying) {
        while (true) {
            // Before the first play after a restore the player hasn't probed the file yet; use the tag length.
            val duration = player.durationMs().takeIf { it > 0 } ?: track?.durationMs ?: 0L
            value = if (duration > 0) (player.positionMs().toFloat() / duration).coerceIn(0f, 1f) else 0f
            if (!state.isPlaying) break
            delay(500)
        }
    }

    val noMusic = library.hasScannedOnce && library.tracks.isEmpty()
    val needsAccess = !library.hasSharedAccess && library.tracks.isEmpty()
    val playPause by rememberUpdatedState {
        when {
            state.hasMedia -> player.togglePlayPause()
            needsAccess -> onRequestAccess()
            library.tracks.isNotEmpty() -> {
                val songs = LibraryLogic.music(library.tracks).ifEmpty { library.tracks }
                player.play(songs, songs.indices.random(), SmartList.MUSIC.title, shuffle = true)
            }
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
            )
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    playPause()
                })
            }
            .pointerInput(Unit) {
                // Edge slides: left edge = volume, right edge = brightness.
                val edge = size.width * 0.24f
                val volumeStep = 14.dp.toPx()
                var zone = 0
                var travel = 0f
                detectVerticalDragGestures(
                    onDragStart = { start ->
                        zone = when {
                            start.x < edge -> 1
                            start.x > size.width - edge -> 2
                            else -> 0
                        }
                        travel = 0f
                    },
                    onVerticalDrag = { change, dy ->
                        if (zone == 0) return@detectVerticalDragGestures
                        change.consume()
                        if (zone == 1) {
                            travel -= dy
                            while (travel >= volumeStep) {
                                volumeViewModel.increaseVolume()
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                travel -= volumeStep
                            }
                            while (travel <= -volumeStep) {
                                volumeViewModel.decreaseVolume()
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                travel += volumeStep
                            }
                        } else {
                            brightness.adjustBy(-dy / (size.height * 0.75f))
                            brightnessTouch++
                        }
                    },
                )
            },
    ) {
        val d = maxWidth
        CoverBackground(artwork)

        if (state.hasMedia) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize().padding(3.dp),
                strokeWidth = 4.dp,
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
            Spacer(Modifier.height(d * 0.135f))
            OutputIcon(output, d, onClick = { volumeViewModel.launchOutputSelection() })
            Spacer(Modifier.height(d * 0.02f))

            // Title only. Tap it for the Now playing sheet.
            Box(
                Modifier
                    .width(d * 0.76f)
                    .height(d * 0.17f)
                    // Tap: Now playing sheet. Double-tap: play / pause, same as anywhere else.
                    .pointerInput(needsAccess) {
                        detectTapGestures(
                            onTap = { if (needsAccess) onRequestAccess() else onOpenOptions() },
                            onDoubleTap = {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                playPause()
                            },
                        )
                    }
                    .semantics { contentDescription = "Now playing options" },
                contentAlignment = Alignment.Center,
            ) {
                val title = when {
                    state.hasMedia -> state.title
                    needsAccess -> "Allow music access"
                    noMusic -> "No music yet"
                    else -> "Nothing playing"
                }
                AnimatedContent(
                    targetState = notice to title,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "title",
                ) { (shownNotice, shownTitle) ->
                    Text(
                        text = shownNotice ?: shownTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (shownNotice != null) FontWeight.Medium else FontWeight.SemiBold,
                            fontSize = if (shownNotice != null) 15.sp else 18.sp,
                            letterSpacing = 0.sp,
                        ),
                        color = if (shownNotice != null) LocalAccent.current else WatchColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = if (state.hasMedia && shownNotice == null) {
                            Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1800)
                        } else {
                            Modifier
                        },
                    )
                }
            }

            Spacer(Modifier.height(d * 0.02f))

            TransportRow(
                d = d,
                state = state,
                isPodcast = track?.isPodcast == true,
                onPrevious = { if (track?.isPodcast == true) player.skipBack() else player.previous() },
                onNext = { if (track?.isPodcast == true) player.skipForward() else player.next() },
                onPlayPause = { playPause() },
            )

            Spacer(Modifier.height(d * 0.04f))

            UtilityRow(
                d = d,
                liked = liked,
                onVolume = onOpenVolume,
                onLike = {
                    val key = state.trackKey
                    notice = if (key == null) {
                        "Play a song first"
                    } else {
                        // The library may still be scanning; the player knows enough to save it.
                        val song = track ?: Track(
                            key = key, uri = "", title = state.title, artist = state.artist, album = "",
                            durationMs = 0, dateAddedMs = 0, folder = "", source = TrackSource.SHARED,
                        )
                        if (graph.library.toggleFavorite(song)) "Added to Favorites" else "Removed"
                    }
                },
            )
        }

        // Volume feedback on the left edge, brightness on the right.
        VolumeLevelIndicator(
            volumeUiState = { volumeViewModel.volumeUiState.value },
            displayIndicatorEvents = volumeViewModel.displayIndicatorEvents,
        )
        AnimatedVisibility(visible = showBrightness, enter = fadeIn(), exit = fadeOut()) {
            LevelIndicator(
                value = { brightness.level },
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = -1f },
                colors = LevelIndicatorDefaults.colors(
                    indicatorColor = Color(0xFFFFD54F),
                    trackColor = WatchColors.Surface,
                ),
            )
        }
    }
}

/** Where the sound is going: watch speaker, earbuds, over-ear headphones or a speaker. */
@Composable
private fun OutputIcon(output: AudioOutput, d: Dp, onClick: () -> Unit) {
    val kind = OutputKind.of(output is AudioOutput.WatchSpeaker || output is AudioOutput.None, output.name)
    val icon: ImageVector = when (kind) {
        OutputKind.WATCH -> Icons.Watch
        OutputKind.EARBUDS -> Icons.Earbuds
        OutputKind.HEADPHONES -> Icons.Headphones
        OutputKind.SPEAKER -> Icons.Speaker
    }
    val label = if (kind == OutputKind.WATCH) "Watch speaker" else output.name
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(d * 0.11f).semantics { contentDescription = "Audio output: $label" },
    ) {
        Icon(icon, contentDescription = null, tint = WatchColors.OnSurfaceVariant, modifier = Modifier.size(d * 0.075f))
    }
}

@Composable
private fun TransportRow(
    d: Dp,
    state: NowPlayingState,
    isPodcast: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val sideColors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(d * 0.04f),
    ) {
        IconButton(
            onClick = { haptics.performHapticFeedback(HapticFeedbackType.ContextClick); onPrevious() },
            enabled = state.hasMedia,
            modifier = Modifier.size(d * 0.19f),
            colors = sideColors,
        ) {
            Icon(
                if (isPodcast) Icons.Back10 else Icons.SkipPrevious,
                if (isPodcast) "Back 10 seconds" else "Previous",
                modifier = Modifier.size(d * 0.12f),
            )
        }
        FilledIconButton(
            onClick = { haptics.performHapticFeedback(HapticFeedbackType.ContextClick); onPlayPause() },
            modifier = Modifier.size(d * 0.28f),
            shapes = IconButtonDefaults.animatedShapes(),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.White.copy(alpha = 0.16f),
                contentColor = Color.White,
            ),
        ) {
            Icon(
                if (state.isPlaying) Icons.Pause else Icons.Play,
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(d * 0.13f),
            )
        }
        IconButton(
            onClick = { haptics.performHapticFeedback(HapticFeedbackType.ContextClick); onNext() },
            enabled = state.hasMedia,
            modifier = Modifier.size(d * 0.19f),
            colors = sideColors,
        ) {
            Icon(
                if (isPodcast) Icons.Forward30 else Icons.SkipNext,
                if (isPodcast) "Forward 30 seconds" else "Next",
                modifier = Modifier.size(d * 0.12f),
            )
        }
    }
}

@Composable
private fun UtilityRow(
    d: Dp,
    liked: Boolean,
    onVolume: () -> Unit,
    onLike: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val pop = remember { Animatable(1f) }
    val size = d * 0.17f
    Row(horizontalArrangement = Arrangement.spacedBy(d * 0.1f), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onVolume, modifier = Modifier.size(size)) {
            Icon(Icons.Volume, "Volume", tint = WatchColors.OnSurfaceVariant, modifier = Modifier.size(d * 0.095f))
        }
        IconButton(
            onClick = {
                haptics.performHapticFeedback(if (liked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                onLike()
                scope.launch {
                    pop.snapTo(0.72f)
                    pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium))
                }
            },
            modifier = Modifier
                .size(size)
                .semantics { contentDescription = if (liked) "Remove from Favorites" else "Add to Favorites" },
            shapes = IconButtonDefaults.shapes(CircleShape),
        ) {
            Icon(
                if (liked) Icons.Heart else Icons.HeartOutline,
                contentDescription = null,
                tint = if (liked) WatchColors.Heart else WatchColors.OnSurfaceVariant,
                modifier = Modifier.size(d * 0.1f).scale(pop.value),
            )
        }
    }
}

/** Screen brightness for the app's window, adjusted by sliding along the right edge. */
class BrightnessController(initial: Float, private val apply: (Float) -> Unit) {
    var level by mutableFloatStateOf(initial)
        private set

    fun adjustBy(delta: Float) {
        level = (level + delta).coerceIn(0.05f, 1f)
        apply(level)
    }
}

/** Maps an output device name to an icon: the user's paired gear plus common brands. */
enum class OutputKind {
    WATCH, EARBUDS, HEADPHONES, SPEAKER;

    companion object {
        private val earbuds = listOf("airpods", "buds", "pods", "earbud", "wf-", "freebuds")
        private val speakers = listOf("jbl", "flip", "go ", "charge", "speaker", "sonos", "boom", "soundlink")
        private val headphones = listOf("wh-", "xm", "studio", "beats", "headphone", "bose", "qc", "max")

        fun of(isWatch: Boolean, name: String): OutputKind {
            val n = name.lowercase()
            return when {
                isWatch || n.isBlank() || n == "phone" -> WATCH
                earbuds.any { it in n } -> EARBUDS
                speakers.any { it in n } -> SPEAKER
                headphones.any { it in n } -> HEADPHONES
                else -> HEADPHONES
            }
        }
    }
}
