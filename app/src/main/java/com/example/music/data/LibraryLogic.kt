package com.example.music.data

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Pure functions over the library: no Android, no I/O. */
object LibraryLogic {

    /** Tracks of [playlist] in play order: hand-added first, then preset rule matches. */
    fun resolve(playlist: Playlist, tracksByKey: Map<String, Track>, allTracks: List<Track>): List<Track> {
        val seen = LinkedHashSet<String>()
        val result = ArrayList<Track>()
        for (key in playlist.trackKeys) {
            val track = tracksByKey[key] ?: continue
            if (seen.add(key)) result += track
        }
        if (!playlist.rules.isEmpty) {
            allTracks
                .asSequence()
                .filter { it.key !in seen && it.key !in playlist.excludedKeys && matches(playlist.rules, it) }
                .sortedWith(albumOrder)
                .forEach { seen.add(it.key); result += it }
        }
        return result
    }

    fun matches(rules: PlaylistRules, track: Track): Boolean {
        if (rules.folders.isNotEmpty()) {
            val segments = track.folder.split('/').filter { it.isNotBlank() }
            if (rules.folders.any { rule -> segments.any { it.equals(rule.trim('/'), ignoreCase = true) } }) {
                return true
            }
        }
        if (rules.artists.any { track.artist.equals(it, ignoreCase = true) || track.artist.contains(it, ignoreCase = true) }) {
            return true
        }
        if (rules.albums.any { track.album.equals(it, ignoreCase = true) }) return true
        if (rules.titles.any { track.title.equals(it, ignoreCase = true) }) return true
        return false
    }

    /** Favorites newest first, bucketed into Today / This week / This month / Earlier. */
    fun groupFavorites(
        favorites: List<Favorite>,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Pair<RecencyGroup, List<Favorite>>> {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        return favorites
            .sortedByDescending { it.likedAtMs }
            .groupBy { fav ->
                val date = Instant.ofEpochMilli(fav.likedAtMs).atZone(zone).toLocalDate()
                val days = ChronoUnit.DAYS.between(date, today)
                when {
                    days <= 0 -> RecencyGroup.TODAY
                    days < 7 -> RecencyGroup.THIS_WEEK
                    days < 30 -> RecencyGroup.THIS_MONTH
                    else -> RecencyGroup.EARLIER
                }
            }
            .toList()
            .sortedBy { it.first.ordinal }
    }

    fun recentlyAdded(tracks: List<Track>, limit: Int = 100): List<Track> =
        tracks.sortedByDescending { it.dateAddedMs }.take(limit)

    fun allSongs(tracks: List<Track>): List<Track> =
        tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

    /** Album → disc/track order, the way an album is meant to be heard. */
    val albumOrder: Comparator<Track> =
        compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.album }
            .thenBy { it.trackNumber }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }

    /** Next free "Playlist N" name. */
    fun nextPlaylistName(existing: List<Playlist>): String {
        val names = existing.map { it.name.lowercase() }.toSet()
        var n = existing.size + 1
        while ("playlist $n" in names) n++
        return "Playlist $n"
    }
}
