package com.example.music.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Favorites and playlists, stored as small JSON files in the app's private storage on the watch.
 *
 * - `favorites.json`: liked songs with the time they were liked
 * - `playlists.json`: user and preconfigured playlists
 * - `state.json`: which preconfigured playlists were already installed
 *
 * State lives in memory as [StateFlow]s and updates instantly. Each change is then written in the
 * background with a write-to-temp-and-rename, so a crash or a dead battery never leaves a
 * half-written file.
 */
class LibraryStore(
    private val dir: File,
    private val presetJson: () -> String?,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val writeMutex = Mutex()

    private val favoritesFile = File(dir, "favorites.json")
    private val playlistsFile = File(dir, "playlists.json")
    private val stateFile = File(dir, "state.json")

    private val _favorites = MutableStateFlow<List<Favorite>>(emptyList())

    /** Newest first. */
    val favorites: StateFlow<List<Favorite>> = _favorites.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())

    /** In display order: preconfigured first (in asset order), then user playlists newest first. */
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private var installedPresets: Set<String> = emptySet()

    /** Loads everything from disk and installs any preconfigured playlists not yet seen. Call off the main thread. */
    fun load() {
        dir.mkdirs()
        synchronized(lock) {
            _favorites.value = readFavorites().sortedByDescending { it.likedAtMs }
            installedPresets = readInstalledPresets()
            var playlists = readPlaylists()
            val presets = parsePresets(presetJson())
            val newPresets = presets.filter { it.id !in installedPresets }
            if (newPresets.isNotEmpty()) {
                playlists = playlists + newPresets
                installedPresets = installedPresets + newPresets.map { it.id }
                writeNow(stateFile, stateToJson(installedPresets))
                writeNow(playlistsFile, playlistsToJson(playlists))
            }
            _playlists.value = sortPlaylists(playlists, presets.map { it.id })
        }
    }

    // region Favorites

    fun isFavorite(trackKey: String): Boolean = _favorites.value.any { it.trackKey == trackKey }

    /** Likes or unlikes [track]. Returns the new liked state. */
    fun toggleFavorite(track: Track): Boolean {
        val nowLiked = !isFavorite(track.key)
        setFavorite(track, nowLiked)
        return nowLiked
    }

    /** Unlikes by key, which works even when the file is no longer on the watch. */
    fun removeFavorite(trackKey: String) {
        synchronized(lock) { _favorites.update { list -> list.filterNot { it.trackKey == trackKey } } }
        persist(favoritesFile) { favoritesToJson(_favorites.value) }
    }

    fun setFavorite(track: Track, liked: Boolean) {
        synchronized(lock) {
            _favorites.update { current ->
                val without = current.filterNot { it.trackKey == track.key }
                if (liked) listOf(Favorite(track.key, clock(), track.title, track.artist)) + without else without
            }
        }
        persist(favoritesFile) { favoritesToJson(_favorites.value) }
    }

    // endregion

    // region Playlists

    fun playlist(id: String): Playlist? = _playlists.value.firstOrNull { it.id == id }

    fun createPlaylist(name: String, trackKeys: List<String> = emptyList()): Playlist {
        val now = clock()
        val playlist = Playlist(
            id = "user-" + UUID.randomUUID().toString().take(8),
            name = name.trim().ifBlank { LibraryLogic.nextPlaylistName(_playlists.value) },
            createdAtMs = now,
            updatedAtMs = now,
            trackKeys = trackKeys.distinct(),
            origin = PlaylistOrigin.USER,
        )
        mutatePlaylists { it + playlist }
        return playlist
    }

    fun renamePlaylist(id: String, name: String) {
        if (name.isBlank()) return
        mutatePlaylist(id) { it.copy(name = name.trim()) }
    }

    fun deletePlaylist(id: String) {
        mutatePlaylists { list -> list.filterNot { it.id == id } }
    }

    fun addToPlaylist(id: String, trackKeys: List<String>) {
        mutatePlaylist(id) { p ->
            val merged = (p.trackKeys + trackKeys).distinct()
            p.copy(trackKeys = merged, excludedKeys = p.excludedKeys - trackKeys.toSet())
        }
    }

    fun removeFromPlaylist(id: String, trackKey: String) {
        mutatePlaylist(id) { p ->
            p.copy(
                trackKeys = p.trackKeys - trackKey,
                // A preset rule would re-add it on the next scan; remember the user removed it.
                excludedKeys = if (p.rules.isEmpty) p.excludedKeys else p.excludedKeys + trackKey,
            )
        }
    }

    fun contains(id: String, trackKey: String): Boolean =
        playlist(id)?.trackKeys?.contains(trackKey) == true

    private fun mutatePlaylist(id: String, transform: (Playlist) -> Playlist) {
        mutatePlaylists { list ->
            list.map { if (it.id == id) transform(it).copy(updatedAtMs = clock()) else it }
        }
    }

    private fun mutatePlaylists(transform: (List<Playlist>) -> List<Playlist>) {
        synchronized(lock) {
            val presetOrder = _playlists.value.filter { it.origin == PlaylistOrigin.PRESET }.map { it.id }
            _playlists.value = sortPlaylists(transform(_playlists.value), presetOrder)
        }
        persist(playlistsFile) { playlistsToJson(_playlists.value) }
    }

    private fun sortPlaylists(list: List<Playlist>, presetOrder: List<String>): List<Playlist> {
        val (presets, user) = list.partition { it.origin == PlaylistOrigin.PRESET }
        return presets.sortedBy { presetOrder.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } } +
            user.sortedByDescending { it.createdAtMs }
    }

    // endregion

    // region Persistence

    private fun persist(file: File, snapshot: () -> String) {
        scope.launch(ioDispatcher) {
            writeMutex.withLock {
                // Serialize at write time so the last write always carries the latest state.
                val json = synchronized(lock) { snapshot() }
                writeNow(file, json)
            }
        }
    }

    private fun writeNow(file: File, json: String) {
        dir.mkdirs()
        val tmp = File(dir, file.name + ".tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun readJson(file: File): JSONObject? =
        runCatching { if (file.exists()) JSONObject(file.readText()) else null }.getOrNull()

    private fun readFavorites(): List<Favorite> {
        val arr = readJson(favoritesFile)?.optJSONArray("favorites") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Favorite(
                trackKey = o.optString("key").ifEmpty { return@mapNotNull null },
                likedAtMs = o.optLong("likedAt"),
                title = o.optString("title"),
                artist = o.optString("artist"),
            )
        }
    }

    private fun readPlaylists(): List<Playlist> {
        val arr = readJson(playlistsFile)?.optJSONArray("playlists") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::playlistFromJson) }
    }

    private fun readInstalledPresets(): Set<String> =
        readJson(stateFile)?.optJSONArray("presetsInstalled")?.strings()?.toSet() ?: emptySet()

    internal fun parsePresets(json: String?): List<Playlist> {
        if (json.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONObject(json).optJSONArray("playlists") }.getOrNull() ?: return emptyList()
        val now = clock()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").ifEmpty { return@mapNotNull null }
            Playlist(
                id = id,
                name = o.optString("name", id),
                createdAtMs = now,
                updatedAtMs = now,
                trackKeys = o.optJSONArray("tracks")?.strings().orEmpty(),
                rules = o.optJSONObject("rules")?.let(::rulesFromJson) ?: PlaylistRules(),
                origin = PlaylistOrigin.PRESET,
            )
        }
    }

    private fun favoritesToJson(list: List<Favorite>): String = JSONObject()
        .put("version", 1)
        .put(
            "favorites",
            JSONArray().apply {
                list.forEach {
                    put(
                        JSONObject()
                            .put("key", it.trackKey)
                            .put("likedAt", it.likedAtMs)
                            .put("title", it.title)
                            .put("artist", it.artist),
                    )
                }
            },
        )
        .toString(2)

    private fun playlistsToJson(list: List<Playlist>): String = JSONObject()
        .put("version", 1)
        .put("playlists", JSONArray().apply { list.forEach { put(playlistToJson(it)) } })
        .toString(2)

    private fun playlistToJson(p: Playlist): JSONObject = JSONObject()
        .put("id", p.id)
        .put("name", p.name)
        .put("origin", p.origin.name.lowercase())
        .put("createdAt", p.createdAtMs)
        .put("updatedAt", p.updatedAtMs)
        .put("tracks", JSONArray(p.trackKeys))
        .put("excluded", JSONArray(p.excludedKeys.toList()))
        .put(
            "rules",
            JSONObject()
                .put("folders", JSONArray(p.rules.folders))
                .put("artists", JSONArray(p.rules.artists))
                .put("albums", JSONArray(p.rules.albums))
                .put("titles", JSONArray(p.rules.titles)),
        )

    private fun playlistFromJson(o: JSONObject): Playlist? {
        val id = o.optString("id").ifEmpty { return null }
        return Playlist(
            id = id,
            name = o.optString("name", id),
            createdAtMs = o.optLong("createdAt"),
            updatedAtMs = o.optLong("updatedAt"),
            trackKeys = o.optJSONArray("tracks")?.strings().orEmpty(),
            rules = o.optJSONObject("rules")?.let(::rulesFromJson) ?: PlaylistRules(),
            excludedKeys = o.optJSONArray("excluded")?.strings()?.toSet().orEmpty(),
            origin = if (o.optString("origin") == "preset") PlaylistOrigin.PRESET else PlaylistOrigin.USER,
        )
    }

    private fun rulesFromJson(o: JSONObject) = PlaylistRules(
        folders = o.optJSONArray("folders")?.strings().orEmpty(),
        artists = o.optJSONArray("artists")?.strings().orEmpty(),
        albums = o.optJSONArray("albums")?.strings().orEmpty(),
        titles = o.optJSONArray("titles")?.strings().orEmpty(),
    )

    private fun stateToJson(installed: Set<String>): String =
        JSONObject().put("presetsInstalled", JSONArray(installed.toList())).toString(2)

    private fun JSONArray.strings(): List<String> =
        (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotEmpty() } }

    // endregion
}
