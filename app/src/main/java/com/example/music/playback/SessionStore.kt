package com.example.music.playback

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** What was playing, so the app reopens on the same song and the system can resume it after a reboot. */
data class LastSession(
    val queue: List<String>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
    /** Where the queue came from: "smart:favorites", a playlist id, … */
    val contextId: String,
    val contextTitle: String,
)

class SessionStore(dir: File) {
    private val file = File(dir, "session.json")

    @Synchronized
    fun read(): LastSession? = runCatching {
        if (!file.exists()) return null
        val o = JSONObject(file.readText())
        val arr = o.getJSONArray("queue")
        LastSession(
            queue = (0 until arr.length()).map { arr.getString(it) },
            index = o.optInt("index"),
            positionMs = o.optLong("position"),
            shuffle = o.optBoolean("shuffle"),
            repeatMode = o.optInt("repeat"),
            contextId = o.optString("contextId"),
            contextTitle = o.optString("contextTitle"),
        ).takeIf { it.queue.isNotEmpty() }
    }.getOrNull()

    @Synchronized
    fun write(session: LastSession) {
        runCatching {
            file.parentFile?.mkdirs()
            val json = JSONObject()
                .put("queue", JSONArray(session.queue))
                .put("index", session.index)
                .put("position", session.positionMs)
                .put("shuffle", session.shuffle)
                .put("repeat", session.repeatMode)
                .put("contextId", session.contextId)
                .put("contextTitle", session.contextTitle)
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.toString())
            tmp.renameTo(file)
        }
    }
}
