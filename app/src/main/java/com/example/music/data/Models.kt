package com.example.music.data

/** Where a track lives on the watch. */
enum class TrackSource {
    /** Shared `Music/` library indexed by MediaStore (Galaxy Wearable sync, adb, other apps). */
    SHARED,

    /** The app's own folder, `Android/data/<package>/files/Music`. Readable without permission. */
    APP_FOLDER,
}

/**
 * One playable song. Pure Kotlin so the playlist and favorite logic stays unit-testable.
 *
 * [key] is stable across rescans and reinstalls: it is derived from the file's location, not from
 * the MediaStore row id (which changes whenever the media database is rebuilt).
 */
data class Track(
    val key: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val dateAddedMs: Long,
    /** Folder path relative to the storage root, e.g. `Music/Workout/`. */
    val folder: String,
    val source: TrackSource,
    /** Absolute file path when known; used for folder artwork lookup. */
    val filePath: String? = null,
    val trackNumber: Int = 0,
) {
    /** Album identity used for artwork caching: tracks on the same album share one cover. */
    val artworkKey: String
        get() = if (album.isNotBlank() && album != UNKNOWN_ALBUM) "$artist|$album|$folder" else key

    companion object {
        const val UNKNOWN_ARTIST = "Unknown artist"
        const val UNKNOWN_ALBUM = "Unknown album"
    }
}

/** A liked song. Title and artist are snapshotted so the list still reads well if a file goes missing. */
data class Favorite(
    val trackKey: String,
    val likedAtMs: Long,
    val title: String,
    val artist: String,
)

/** Matching rules for preconfigured playlists. Any rule that matches includes the track. */
data class PlaylistRules(
    val folders: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
    val albums: List<String> = emptyList(),
    val titles: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = folders.isEmpty() && artists.isEmpty() && albums.isEmpty() && titles.isEmpty()
}

enum class PlaylistOrigin { PRESET, USER }

data class Playlist(
    val id: String,
    val name: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    /** Tracks added by hand, in order. */
    val trackKeys: List<String> = emptyList(),
    /** Preset rules; tracks matching them are appended after [trackKeys]. */
    val rules: PlaylistRules = PlaylistRules(),
    /** Rule matches the user removed by hand. */
    val excludedKeys: Set<String> = emptySet(),
    val origin: PlaylistOrigin = PlaylistOrigin.USER,
)

/** Recency buckets for the Favorites page. */
enum class RecencyGroup(val label: String) {
    TODAY("Today"),
    THIS_WEEK("This week"),
    THIS_MONTH("This month"),
    EARLIER("Earlier"),
}

/** Smart lists computed from the library rather than stored. */
enum class SmartList(val id: String, val title: String) {
    ALL_SONGS("smart:all", "All songs"),
    RECENTLY_ADDED("smart:recent", "Recently added"),
    FAVORITES("smart:favorites", "Favorites"),
    ;

    companion object {
        fun fromId(id: String): SmartList? = entries.firstOrNull { it.id == id }
    }
}
