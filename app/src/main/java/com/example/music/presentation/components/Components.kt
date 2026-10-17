package com.example.music.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import com.example.music.appGraph
import com.example.music.data.Artwork
import com.example.music.data.Track
import com.example.music.presentation.theme.WatchColors

/** Loads (and caches) the cover for [track]; null while loading or when the file has none. */
@Composable
fun rememberArtwork(track: Track?, sizePx: Int): Artwork? {
    val loader = LocalContext.current.appGraph.artwork
    var art by remember(track?.artworkKey, sizePx) {
        mutableStateOf(track?.let { loader.cached(it, sizePx) })
    }
    LaunchedEffect(track?.artworkKey, sizePx) {
        if (track != null && art == null) art = loader.load(track, sizePx)
    }
    return art
}

/**
 * Full-screen backdrop: the album cover, blurred and darkened so white text stays readable.
 * Crossfades when the song changes. Without a cover it falls back to a deep accent glow.
 */
@Composable
fun CoverBackground(artwork: Artwork?, modifier: Modifier = Modifier, blur: Dp = 18.dp, dim: Float = 0.5f) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(targetState = artwork, animationSpec = tween(700), label = "cover") { art ->
            if (art != null) {
                Image(
                    bitmap = art.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(blur),
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.radialGradient(
                            listOf(WatchColors.OneUiBlue.copy(alpha = 0.35f), Color.Black),
                        ),
                    ),
                )
            }
        }
        // Even dim plus a vignette that deepens toward the bezel, like One UI's media backdrop.
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    0f to Color.Black.copy(alpha = dim * 0.7f),
                    0.75f to Color.Black.copy(alpha = dim),
                    1f to Color.Black.copy(alpha = (dim + 0.3f).coerceAtMost(0.95f)),
                ),
            ),
        )
    }
}

/**
 * Square cover thumbnail with rounded corners. Without embedded art it shows [monogram] (a show's
 * initials on a color picked from its name) or a music-note tile.
 */
@Composable
fun CoverThumb(track: Track?, size: Dp, modifier: Modifier = Modifier, monogram: String? = null) {
    val art = rememberArtwork(track, 128)
    Box(
        modifier.size(size).clip(RoundedCornerShape(size / 4)).background(WatchColors.SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(art.image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (monogram != null) {
            val hue = (monogram.hashCode() and 0x7fffffff) % 360
            Box(
                Modifier.fillMaxSize().background(Color.hsl(hue.toFloat(), 0.45f, 0.32f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    initials(monogram),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
            }
        } else {
            Icon(
                painterResource(Icons.MusicNote),
                contentDescription = null,
                tint = WatchColors.OnSurfaceVariant,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

/** Song row used in every list: cover, title, artist. */
@Composable
fun TrackChip(
    track: Track?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    isCurrent: Boolean = false,
    enabled: Boolean = true,
    transformation: SurfaceTransformation? = null,
    titleLines: Int = 1,
    trailing: (@Composable () -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        transformation = transformation,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else WatchColors.Surface,
        ),
        icon = { CoverThumb(track, 32.dp) },
        secondaryLabel = {
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = WatchColors.OnSurfaceVariant)
        },
        label = {
            Text(
                title,
                maxLines = titleLines,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else WatchColors.OnSurface,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        },
    )
}

/** Navigation row (playlist, smart list, action) with a round leading icon. */
@Composable
fun NavChip(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    transformation: SurfaceTransformation? = null,
) {
    Button(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        transformation = transformation,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = WatchColors.Surface),
        icon = {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
        },
        secondaryLabel = subtitle?.let {
            { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, color = WatchColors.OnSurfaceVariant) }
        },
        label = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

/** "Flow State with Harry Mack" -> "FS"; skips small words. */
fun initials(name: String): String =
    name.split(' ', '-', '&').filter { it.isNotBlank() && it.lowercase() !in setOf("the", "with", "and", "of", "a") }
        .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { name.take(1).uppercase() }

fun songCount(n: Int): String = if (n == 1) "1 song" else "$n songs"

fun episodeCount(n: Int): String = if (n == 1) "1 episode" else "$n episodes"

fun showCount(n: Int): String = if (n == 1) "1 show" else "$n shows"

/** "2h 36m" / "48m". */
fun formatDuration(ms: Long): String {
    val totalMin = (ms / 60_000).toInt()
    return if (totalMin >= 60) "${totalMin / 60}h ${totalMin % 60}m" else "${totalMin.coerceAtLeast(1)}m"
}

/** Episode subtitle: "Sep 14 · 2h 36m", or how much is left when partly played. */
fun episodeSubtitle(track: Track, resumeMs: Long): String {
    val date = track.releaseDate?.let {
        runCatching {
            val day = java.time.LocalDate.parse(it)
            val pattern = if (day.year == java.time.LocalDate.now().year) "MMM d" else "MMM d, yyyy"
            day.format(java.time.format.DateTimeFormatter.ofPattern(pattern))
        }.getOrNull()
    }
    val length = when {
        resumeMs > 0 && track.durationMs > resumeMs -> formatDuration(track.durationMs - resumeMs) + " left"
        track.durationMs > 0 -> formatDuration(track.durationMs)
        else -> null
    }
    return listOfNotNull(date, length).joinToString(" · ").ifEmpty { track.show }
}
