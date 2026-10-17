package com.example.music.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Finds every song on the watch:
 *
 * 1. The shared music library through MediaStore: `Music/` (where Galaxy Wearable puts tracks
 *    you send from the phone, and what Samsung's own Music app reads) and `Podcasts/`, one folder
 *    per show. Needs `READ_MEDIA_AUDIO`.
 * 2. The app's own folder (`Android/data/<package>/files/Music`), walked directly. No permission
 *    needed; tags are read once and cached in `files/library/folder_index.json`.
 */
class TrackScanner(private val context: Context) {

    val appMusicDir: File? by lazy {
        context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.also { it.mkdirs() }
    }

    private val indexFile = File(context.filesDir, "library/folder_index.json")

    fun scan(includeShared: Boolean): List<Track> {
        val shared = if (includeShared) runCatching { scanMediaStore() }.onFailure { Log.w(TAG, "MediaStore scan failed", it) }.getOrDefault(emptyList()) else emptyList()
        val local = runCatching { scanAppFolder() }.onFailure { Log.w(TAG, "Folder scan failed", it) }.getOrDefault(emptyList())
        return (shared + local).distinctBy { it.key }
    }

    private fun scanMediaStore(): List<Track> {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.CD_TRACK_NUMBER,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.IS_PODCAST,
        )
        // Freshly copied files are listed before their tags are read (flags still NULL); count
        // anything in Music/ or Podcasts/ so new songs appear right away.
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.IS_PODCAST} != 0 OR " +
            "(${MediaStore.Audio.Media.IS_MUSIC} IS NULL AND (${MediaStore.Audio.Media.RELATIVE_PATH} LIKE 'Music/%' OR " +
            "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE 'Podcasts/%'))"
        val tracks = ArrayList<Track>()
        context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val trackNoCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.CD_TRACK_NUMBER)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val podcastCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_PODCAST)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val relPath = c.getString(pathCol).orEmpty()
                val name = c.getString(nameCol).orEmpty()
                val podcast = c.getInt(podcastCol) != 0 || LibraryLogic.isPodcastPath(relPath)
                val album = c.getString(albumCol).cleanOr(Track.UNKNOWN_ALBUM)
                tracks += Track(
                    key = "media:$relPath$name",
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    title = LibraryLogic.completeTitle(c.getString(titleCol).cleanOr(name.substringBeforeLast('.')), name)
                        .let { if (podcast) LibraryLogic.episodeTitle(it) else it },
                    artist = c.getString(artistCol).cleanOr(LibraryLogic.artistFromPath(relPath) ?: Track.UNKNOWN_ARTIST),
                    album = album,
                    durationMs = c.getLong(durationCol),
                    dateAddedMs = c.getLong(addedCol) * 1000,
                    folder = relPath,
                    source = TrackSource.SHARED,
                    filePath = c.getString(dataCol),
                    trackNumber = parseTrackNumber(c.getString(trackNoCol)).takeIf { it > 0 }
                        ?: LibraryLogic.numberFromName(name),
                    kind = if (podcast) TrackKind.PODCAST else TrackKind.MUSIC,
                    show = if (podcast) LibraryLogic.showFromPath(relPath) ?: album else "",
                    releaseDate = if (podcast) LibraryLogic.dateFromName(name) else null,
                )
            }
        }
        return tracks
    }

    private fun scanAppFolder(): List<Track> {
        val root = appMusicDir ?: return emptyList()
        val cache = readIndex()
        val fresh = HashMap<String, JSONObject>()
        val tracks = ArrayList<Track>()
        root.walkTopDown()
            .onEnter { !it.name.startsWith('.') }
            .filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }
            .forEach { file ->
                val rel = file.relativeTo(root).invariantSeparatorsPath
                val stamp = "${file.length()}:${file.lastModified()}"
                val entry = cache[rel]?.takeIf { it.optString("stamp") == stamp } ?: readTags(file, stamp)
                fresh[rel] = entry
                val folder = "Music/" + (rel.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" })
                val podcast = LibraryLogic.isPodcastPath(folder)
                val album = entry.optString("album").cleanOr(Track.UNKNOWN_ALBUM)
                tracks += Track(
                    key = "file:$rel",
                    uri = Uri.fromFile(file).toString(),
                    title = entry.optString("title").cleanOr(file.nameWithoutExtension)
                        .let { if (podcast) LibraryLogic.episodeTitle(it) else it },
                    artist = entry.optString("artist").cleanOr(Track.UNKNOWN_ARTIST),
                    album = album,
                    durationMs = entry.optLong("duration"),
                    dateAddedMs = file.lastModified(),
                    folder = folder,
                    source = TrackSource.APP_FOLDER,
                    filePath = file.absolutePath,
                    trackNumber = entry.optInt("track").takeIf { it > 0 } ?: LibraryLogic.numberFromName(file.name),
                    kind = if (podcast) TrackKind.PODCAST else TrackKind.MUSIC,
                    show = if (podcast) LibraryLogic.showFromPath(folder) ?: album else "",
                    releaseDate = if (podcast) LibraryLogic.dateFromName(file.name) else null,
                )
            }
        if (fresh.keys != cache.keys || fresh.any { (k, v) -> cache[k] !== v }) writeIndex(fresh)
        return tracks
    }

    private fun readTags(file: File, stamp: String): JSONObject {
        val o = JSONObject().put("stamp", stamp)
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.absolutePath)
            o.put("title", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty())
            o.put(
                "artist",
                (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)).orEmpty(),
            )
            o.put("album", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty())
            o.put("duration", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L)
            o.put("track", parseTrackNumber(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)))
        } catch (e: Exception) {
            Log.w(TAG, "Could not read tags of ${file.name}", e)
        } finally {
            r.release()
        }
        return o
    }

    private fun readIndex(): Map<String, JSONObject> = runCatching {
        if (!indexFile.exists()) return emptyMap()
        val arr = JSONObject(indexFile.readText()).optJSONArray("files") ?: return emptyMap()
        (0 until arr.length()).associate { i ->
            val o = arr.getJSONObject(i)
            o.getString("path") to o
        }
    }.getOrDefault(emptyMap())

    private fun writeIndex(entries: Map<String, JSONObject>) {
        runCatching {
            indexFile.parentFile?.mkdirs()
            val arr = JSONArray()
            entries.forEach { (path, o) -> arr.put(o.put("path", path)) }
            val tmp = File(indexFile.parentFile, indexFile.name + ".tmp")
            tmp.writeText(JSONObject().put("files", arr).toString())
            tmp.renameTo(indexFile)
        }
    }

    private fun String?.cleanOr(fallback: String): String {
        val s = this?.trim().orEmpty()
        return if (s.isEmpty() || s == "<unknown>") fallback else s
    }

    private fun parseTrackNumber(raw: String?): Int =
        raw?.substringBefore('/')?.trim()?.toIntOrNull()?.let { if (it > 1000) it % 1000 else it } ?: 0

    companion object {
        private const val TAG = "TrackScanner"
        val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "amr", "3gp", "mka", "mp4")
    }
}
