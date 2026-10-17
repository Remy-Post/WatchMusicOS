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

/** Where each podcast episode was left off, so long episodes resume in place. `progress.json`. */
class EpisodeProgressStore(
    private val dir: File,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val file = File(dir, "progress.json")
    private val writeMutex = Mutex()

    private val _positions = MutableStateFlow<Map<String, Long>>(emptyMap())
    val positions: StateFlow<Map<String, Long>> = _positions.asStateFlow()

    fun load() {
        _positions.value = runCatching {
            if (!file.exists()) return@runCatching emptyMap()
            val o = JSONObject(file.readText())
            o.keys().asSequence().associateWith { o.getLong(it) }
        }.getOrDefault(emptyMap())
    }

    fun position(key: String): Long = _positions.value[key] ?: 0L

    /** Saves [positionMs] for an episode. Positions near the end count as finished and are cleared. */
    fun save(key: String, positionMs: Long, durationMs: Long) {
        val finished = durationMs > 0 && positionMs >= durationMs - FINISHED_MARGIN_MS
        val value = if (finished || positionMs < MIN_RESUME_MS) null else positionMs
        if (_positions.value[key] == value) return
        _positions.update { if (value == null) it - key else it + (key to value) }
        persist()
    }

    fun clear(key: String) {
        if (key !in _positions.value) return
        _positions.update { it - key }
        persist()
    }

    private fun persist() {
        scope.launch(ioDispatcher) {
            writeMutex.withLock {
                dir.mkdirs()
                val json = JSONObject().apply { _positions.value.forEach { (k, v) -> put(k, v) } }
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
