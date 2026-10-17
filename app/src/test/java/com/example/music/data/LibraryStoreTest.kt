package com.example.music.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.ZoneOffset

class LibraryStoreTest {

    private lateinit var dir: File
    private var now = 1_000_000L

    private val presets = """
        {"playlists":[
          {"id":"preset-workout","name":"Workout","rules":{"folders":["Workout"]}},
          {"id":"preset-chill","name":"Chill","rules":{"artists":["Nujabes"]}}
        ]}
    """.trimIndent()

    private fun store(presetJson: String? = presets) = LibraryStore(
        dir = dir,
        presetJson = { presetJson },
        // Unconfined runs the background write immediately, so files are on disk when we read them.
        scope = CoroutineScope(Dispatchers.Unconfined),
        ioDispatcher = Dispatchers.Unconfined,
        clock = { now },
    ).also { it.load() }

    private fun track(key: String, folder: String = "Music/", artist: String = "A", title: String = key) = Track(
        key = key, uri = "content://$key", title = title, artist = artist, album = "Album",
        durationMs = 1000, dateAddedMs = 0, folder = folder, source = TrackSource.SHARED,
    )

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("library").toFile()
    }

    @Test
    fun `favorites are newest first and survive a restart`() {
        val s = store()
        now = 10; s.setFavorite(track("a"), true)
        now = 20; s.setFavorite(track("b"), true)
        now = 30; s.setFavorite(track("c"), true)
        assertEquals(listOf("c", "b", "a"), s.favorites.value.map { it.trackKey })

        val reloaded = store()
        assertEquals(listOf("c", "b", "a"), reloaded.favorites.value.map { it.trackKey })
        assertEquals(30L, reloaded.favorites.value.first().likedAtMs)
    }

    @Test
    fun `liking again moves the song to the top`() {
        val s = store()
        now = 10; s.setFavorite(track("a"), true)
        now = 20; s.setFavorite(track("b"), true)
        now = 30; s.setFavorite(track("a"), true)
        assertEquals(listOf("a", "b"), s.favorites.value.map { it.trackKey })
    }

    @Test
    fun `toggle likes then unlikes`() {
        val s = store()
        assertTrue(s.toggleFavorite(track("a")))
        assertTrue(s.isFavorite("a"))
        assertFalse(s.toggleFavorite(track("a")))
        assertFalse(s.isFavorite("a"))
        assertTrue(store().favorites.value.isEmpty())
    }

    @Test
    fun `presets install once and are not reinstalled after deletion`() {
        val s = store()
        assertEquals(listOf("Workout", "Chill"), s.playlists.value.map { it.name })
        s.deletePlaylist("preset-workout")
        val reloaded = store()
        assertEquals(listOf("Chill"), reloaded.playlists.value.map { it.name })
    }

    @Test
    fun `new presets added in an update get installed`() {
        store()
        val updated = presets.replace("]}", ",{\"id\":\"preset-focus\",\"name\":\"Focus\"}]}")
        val s = store(updated)
        assertEquals(listOf("Workout", "Chill", "Focus"), s.playlists.value.map { it.name })
    }

    @Test
    fun `user playlists persist edits`() {
        val s = store(presetJson = null)
        now = 100
        val p = s.createPlaylist("Road trip", listOf("a"))
        s.addToPlaylist(p.id, listOf("b", "a", "c"))
        s.removeFromPlaylist(p.id, "b")
        s.renamePlaylist(p.id, "Summer")
        val reloaded = store(presetJson = null).playlist(p.id)!!
        assertEquals("Summer", reloaded.name)
        assertEquals(listOf("a", "c"), reloaded.trackKeys)
    }

    @Test
    fun `blank playlist name gets a numbered default`() {
        val s = store(presetJson = null)
        assertEquals("Playlist 1", s.createPlaylist("  ").name)
        assertEquals("Playlist 2", s.createPlaylist("").name)
    }

    @Test
    fun `preset rules pull in matching songs and respect removals`() {
        val s = store()
        val tracks = listOf(
            track("w1", folder = "Music/Workout/"),
            track("w2", folder = "Music/Gym/Workout/"),
            track("x", folder = "Music/Other/"),
            track("n", artist = "Nujabes"),
        )
        val byKey = tracks.associateBy { it.key }
        val workout = s.playlist("preset-workout")!!
        assertEquals(setOf("w1", "w2"), LibraryLogic.resolve(workout, byKey, tracks).map { it.key }.toSet())

        s.removeFromPlaylist("preset-workout", "w1")
        val after = LibraryLogic.resolve(s.playlist("preset-workout")!!, byKey, tracks).map { it.key }
        assertEquals(listOf("w2"), after)

        // Adding it back by hand clears the exclusion.
        s.addToPlaylist("preset-workout", listOf("w1"))
        assertTrue("w1" in LibraryLogic.resolve(s.playlist("preset-workout")!!, byKey, tracks).map { it.key })

        val chill = LibraryLogic.resolve(s.playlist("preset-chill")!!, byKey, tracks).map { it.key }
        assertEquals(listOf("n"), chill)
    }

    @Test
    fun `corrupt files do not crash and start empty`() {
        dir.mkdirs()
        File(dir, "favorites.json").writeText("{not json")
        File(dir, "playlists.json").writeText("")
        val s = store(presetJson = null)
        assertTrue(s.favorites.value.isEmpty())
        assertTrue(s.playlists.value.isEmpty())
    }

    @Test
    fun `favorites group by recency`() {
        val day = 24 * 60 * 60 * 1000L
        val today = 100 * day + 12 * 60 * 60 * 1000L
        val favs = listOf(
            Favorite("old", today - 90 * day, "", ""),
            Favorite("t1", today - 1000, "", ""),
            Favorite("w", today - 3 * day, "", ""),
            Favorite("m", today - 12 * day, "", ""),
            Favorite("t2", today, "", ""),
        )
        val groups = LibraryLogic.groupFavorites(favs, today, ZoneOffset.UTC)
        assertEquals(
            listOf(RecencyGroup.TODAY, RecencyGroup.THIS_WEEK, RecencyGroup.THIS_MONTH, RecencyGroup.EARLIER),
            groups.map { it.first },
        )
        assertEquals(listOf("t2", "t1"), groups.first().second.map { it.trackKey })
    }
}
