package com.example.music.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

/** Album art plus the colors pulled from it. */
class Artwork(
    val image: ImageBitmap,
    /** Bright color for the progress ring and highlights. */
    val accent: Color,
)

/**
 * Loads embedded cover art (or a `cover.jpg` / `folder.jpg` next to the file), downsampled for
 * the watch, and caches it in memory. Tracks from the same album share one cache entry.
 */
class ArtworkLoader(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val cache = object : LruCache<String, Artwork>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Artwork): Int = value.image.width * value.image.height * 4
    }
    private val missing: MutableSet<String> = Collections.synchronizedSet(HashSet())

    fun cached(track: Track, sizePx: Int): Artwork? = cache.get(cacheKey(track, sizePx))

    suspend fun load(track: Track, sizePx: Int): Artwork? {
        val key = cacheKey(track, sizePx)
        cache.get(key)?.let { return it }
        if (key in missing) return null
        return withContext(ioDispatcher) {
            val bitmap = runCatching { decode(track, sizePx) }.getOrNull()
            if (bitmap == null) {
                missing += key
                null
            } else {
                Artwork(bitmap.asImageBitmap(), accentOf(bitmap)).also { cache.put(key, it) }
            }
        }
    }

    private fun cacheKey(track: Track, sizePx: Int) = "${track.artworkKey}@${bucket(sizePx)}"

    private fun bucket(sizePx: Int) = if (sizePx <= 128) 128 else 320

    private fun decode(track: Track, sizePx: Int): Bitmap? {
        val target = bucket(sizePx)
        val uri = Uri.parse(track.uri)
        if (track.source == TrackSource.SHARED) {
            runCatching { context.contentResolver.loadThumbnail(uri, Size(target, target), null) }
                .getOrNull()?.let { return it }
        }
        embedded(track, uri)?.let { return decodeSampled(it, target) }
        folderImage(track)?.let { file ->
            return decodeSampled(file.readBytes(), target)
        }
        return null
    }

    private fun embedded(track: Track, uri: Uri): ByteArray? {
        val r = MediaMetadataRetriever()
        return try {
            if (track.source == TrackSource.APP_FOLDER && track.filePath != null) {
                r.setDataSource(track.filePath)
            } else {
                r.setDataSource(context, uri)
            }
            r.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            r.release()
        }
    }

    private fun folderImage(track: Track): File? {
        val dir = track.filePath?.let { File(it).parentFile } ?: return null
        val names = listOf("cover", "folder", "front", "album", "albumart")
        return dir.listFiles()?.firstOrNull { f ->
            f.isFile && f.nameWithoutExtension.lowercase() in names &&
                f.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp")
        }
    }

    private fun decodeSampled(bytes: ByteArray, target: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun accentOf(bitmap: Bitmap): Color {
        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
        val swatch = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch
            ?: return DefaultAccent
        // Keep the accent readable on a dark, blurred background.
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(swatch.rgb, hsl)
        hsl[1] = hsl[1].coerceAtLeast(0.45f)
        hsl[2] = hsl[2].coerceIn(0.55f, 0.75f)
        return Color(ColorUtils.HSLToColor(hsl))
    }

    companion object {
        /** One UI's signature blue. */
        val DefaultAccent = Color(0xFF3E91FF)
    }
}
