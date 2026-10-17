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
        music(tracks).sortedByDescending { it.dateAddedMs }.take(limit)

    /** Every song (no podcasts), A to Z. */
    fun music(tracks: List<Track>): List<Track> =
        tracks.filter { !it.isPodcast }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

    /** Podcast shows A to Z, each with its episodes newest first. */
    fun podcastShows(tracks: List<Track>): List<PodcastShow> =
        tracks.filter { it.isPodcast }
            .groupBy { it.show.ifBlank { it.album } }
            .map { (name, eps) -> PodcastShow(name, eps.sortedWith(newestEpisodeFirst)) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    fun episodes(tracks: List<Track>, show: String): List<Track> =
        podcastShows(tracks).firstOrNull { it.name == show }?.episodes.orEmpty()

    /** Episodes with a date sort newest first; undated ones follow, by their numbered file order. */
    val newestEpisodeFirst: Comparator<Track> =
        compareBy<Track> { it.releaseDate == null }
            .thenByDescending { it.releaseDate }
            .thenBy { it.trackNumber }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }

    private val datePattern = Regex("""(\d{4})-(\d{2})-(\d{2})""")

    /** Pulls a `yyyy-MM-dd` date out of a file name like `01 - 2026-09-14 - Title.m4a`. */
    fun dateFromName(name: String): String? = datePattern.find(name)?.value

    /** Leading episode number in names like `07 - Title.mp3`. */
    fun numberFromName(name: String): Int =
        Regex("""^(\d{1,4})\s*-""").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    /** "01 - 2026-09-14 - Title" → "Title": numbering and dates are shown separately. */
    fun episodeTitle(raw: String): String =
        raw.replace(Regex("""^\d{1,4}\s*-\s*"""), "")
            .replace(Regex("""^\d{4}-\d{2}-\d{2}\s*-\s*"""), "")
            .trim()
            .ifEmpty { raw }

    /** Whether a path (MediaStore RELATIVE_PATH or app-folder path) is inside a `Podcasts/` folder. */
    fun isPodcastPath(relativePath: String): Boolean =
        relativePath.split('/').any { it.equals("Podcasts", ignoreCase = true) }

    /** Show name: the folder right under `Podcasts/`. */
    fun showFromPath(relativePath: String): String? {
        val parts = relativePath.split('/').filter { it.isNotBlank() }
        val i = parts.indexOfFirst { it.equals("Podcasts", ignoreCase = true) }
        return parts.getOrNull(i + 1)?.takeIf { i >= 0 }
    }

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
