package com.example.music.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.Heart
import com.adamglin.phosphoricons.fill.Pause
import com.adamglin.phosphoricons.fill.Play
import com.adamglin.phosphoricons.fill.SkipBack
import com.adamglin.phosphoricons.fill.SkipForward
import com.adamglin.phosphoricons.regular.Check
import com.adamglin.phosphoricons.regular.Equalizer
import com.adamglin.phosphoricons.regular.Gauge
import com.adamglin.phosphoricons.regular.Headphones
import com.adamglin.phosphoricons.regular.ListPlus
import com.adamglin.phosphoricons.regular.Minus
import com.adamglin.phosphoricons.regular.MusicNoteSimple
import com.adamglin.phosphoricons.regular.PencilSimple
import com.adamglin.phosphoricons.regular.Plus
import com.adamglin.phosphoricons.regular.Queue
import com.adamglin.phosphoricons.regular.Repeat
import com.adamglin.phosphoricons.regular.RepeatOnce
import com.adamglin.phosphoricons.regular.Shuffle
import com.adamglin.phosphoricons.regular.SpeakerHifi
import com.adamglin.phosphoricons.regular.SpeakerHigh
import com.adamglin.phosphoricons.regular.Timer
import com.adamglin.phosphoricons.regular.Trash
import com.adamglin.phosphoricons.regular.Watch
import com.composables.icons.materialsymbols.roundedfilled.R as Symbols
import com.adamglin.phosphoricons.regular.Heart as HeartOutlineGlyph

/**
 * Every icon in the app, from Phosphor (MIT): the fill weight for transport controls and the
 * active heart, the regular weight for everything else. Phosphor has no earbuds glyph and no
 * numbered skip arrows, so those three come from Google's Material Symbols.
 */
object Icons {
    val Play: ImageVector get() = PhosphorIcons.Fill.Play
    val Pause: ImageVector get() = PhosphorIcons.Fill.Pause
    val SkipNext: ImageVector get() = PhosphorIcons.Fill.SkipForward
    val SkipPrevious: ImageVector get() = PhosphorIcons.Fill.SkipBack
    val Heart: ImageVector get() = PhosphorIcons.Fill.Heart
    val HeartOutline: ImageVector get() = PhosphorIcons.Regular.HeartOutlineGlyph
    val Volume: ImageVector get() = PhosphorIcons.Regular.SpeakerHigh
    val Shuffle: ImageVector get() = PhosphorIcons.Regular.Shuffle
    val Repeat: ImageVector get() = PhosphorIcons.Regular.Repeat
    val RepeatOne: ImageVector get() = PhosphorIcons.Regular.RepeatOnce
    val Queue: ImageVector get() = PhosphorIcons.Regular.Queue
    val PlaylistAdd: ImageVector get() = PhosphorIcons.Regular.ListPlus
    val Add: ImageVector get() = PhosphorIcons.Regular.Plus
    val Remove: ImageVector get() = PhosphorIcons.Regular.Minus
    val Check: ImageVector get() = PhosphorIcons.Regular.Check
    val Edit: ImageVector get() = PhosphorIcons.Regular.PencilSimple
    val Delete: ImageVector get() = PhosphorIcons.Regular.Trash
    val Equalizer: ImageVector get() = PhosphorIcons.Regular.Equalizer
    val SleepTimer: ImageVector get() = PhosphorIcons.Regular.Timer
    val Speed: ImageVector get() = PhosphorIcons.Regular.Gauge
    val MusicNote: ImageVector get() = PhosphorIcons.Regular.MusicNoteSimple

    // Output devices.
    val Watch: ImageVector get() = PhosphorIcons.Regular.Watch
    val Headphones: ImageVector get() = PhosphorIcons.Regular.Headphones
    val Speaker: ImageVector get() = PhosphorIcons.Regular.SpeakerHifi
    val Earbuds: ImageVector
        @Composable get() = ImageVector.vectorResource(Symbols.drawable.materialsymbols_ic_earbuds_rounded_filled)

    // Podcast skips.
    val Back10: ImageVector
        @Composable get() = ImageVector.vectorResource(Symbols.drawable.materialsymbols_ic_replay_10_rounded_filled)
    val Forward30: ImageVector
        @Composable get() = ImageVector.vectorResource(Symbols.drawable.materialsymbols_ic_forward_30_rounded_filled)
}
