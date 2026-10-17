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
import org.json.JSONObject
import java.io.File

/** Where an episode was left off, and when it was last played. */
data class EpisodeProgress(val positionMs: Long, val updatedAtMs: Long)

/**
 * Where each podcast episode was left off, so long episodes resume in place even after music
 * played in between. Stored in `progress.json`.
 */
class EpisodeProgressStore(
    private val dir: File,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val file = File(dir, "progress.json")
    private val writeMutex = Mutex()

    private val _entries = MutableStateFlow<Map<String, EpisodeProgress>>(emptyMap())
    val entries: StateFlow<Map<String, EpisodeProgress>> = _entries.asStateFlow()

    fun load() {
        _entries.value = runCatching {
            if (!file.exists()) return@runCatching emptyMap()
            val o = JSONObject(file.readText())
            o.keys().asSequence().associateWith { key ->
                // Older files stored only the position.
                o.optJSONObject(key)?.let { EpisodeProgress(it.optLong("pos"), it.optLong("at")) }
                    ?: EpisodeProgress(o.optLong(key), 0L)
            }
        }.getOrDefault(emptyMap())
    }

    fun position(key: String): Long = _entries.value[key]?.positionMs ?: 0L

    /** Episodes started but not finished, most recently played first. */
    fun inProgress(): List<Pair<String, EpisodeProgress>> =
        _entries.value.entries.sortedByDescending { it.value.updatedAtMs }.map { it.key to it.value }

    /** Saves [positionMs] for an episode. Positions near the end count as finished and are cleared. */
    fun save(key: String, positionMs: Long, durationMs: Long) {
        val finished = durationMs > 0 && positionMs >= durationMs - FINISHED_MARGIN_MS
        if (finished || positionMs < MIN_RESUME_MS) {
            clear(key)
            return
        }
        if (_entries.value[key]?.positionMs == positionMs) return
        _entries.update { it + (key to EpisodeProgress(positionMs, System.currentTimeMillis())) }
        persist()
    }

    fun clear(key: String) {
        if (key !in _entries.value) return
        _entries.update { it - key }
        persist()
    }

    private fun persist() {
        scope.launch(ioDispatcher) {
            writeMutex.withLock {
                dir.mkdirs()
                val json = JSONObject().apply {
                    _entries.value.forEach { (k, v) -> put(k, JSONObject().put("pos", v.positionMs).put("at", v.updatedAtMs)) }
                }
                val tmp = File(dir, file.name + ".tmp")
                tmp.writeText(json.toString())
                tmp.renameTo(file)
            }
        }
    }

    private companion object {
        const val FINISHED_MARGIN_MS = 30_000L
        const val MIN_RESUME_MS = 15_000L
    }
}
