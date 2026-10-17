package com.example.music

import android.app.Application
import android.content.Context
import com.example.music.data.ArtworkLoader
import com.example.music.data.LibraryStore
import com.example.music.data.TrackRepository
import com.example.music.playback.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Single-process object graph shared by the UI and the playback service. */
class AppGraph(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val libraryDir = File(context.filesDir, "library")

    val tracks = TrackRepository(context, scope, Dispatchers.IO)
    val library = LibraryStore(
        dir = libraryDir,
        presetJson = {
            runCatching { context.assets.open("default_playlists.json").bufferedReader().use { it.readText() } }
                .getOrNull()
        },
        scope = scope,
        ioDispatcher = Dispatchers.IO,
    )
    val artwork = ArtworkLoader(context, Dispatchers.IO)
    val sessions = SessionStore(libraryDir)

    /** Name of what is playing ("Favorites", a playlist name, …), shown above the title. */
    val playbackContextTitle = MutableStateFlow("")

    init {
        scope.launch(Dispatchers.IO) { library.load() }
        tracks.refresh()
    }
}

class MusicApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

val Context.appGraph: AppGraph
    get() = (applicationContext as MusicApplication).graph
