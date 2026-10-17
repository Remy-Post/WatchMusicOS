package com.example.music.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryState(
    val tracks: List<Track> = emptyList(),
    val byKey: Map<String, Track> = emptyMap(),
    val isScanning: Boolean = true,
    val hasSharedAccess: Boolean = false,
    val hasScannedOnce: Boolean = false,
)

/** Live view of every song on the watch, rescanned whenever the media library changes. */
class TrackRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) {
    val scanner = TrackScanner(context)

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var observerRegistered = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh(debounceMs = 1_500)
    }

    fun hasSharedAccess(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun refresh(debounceMs: Long = 0) {
        scanJob?.cancel()
        scanJob = scope.launch {
            if (debounceMs > 0) delay(debounceMs)
            val access = hasSharedAccess()
            if (access && !observerRegistered) {
                context.contentResolver.registerContentObserver(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer,
                )
                observerRegistered = true
            }
            _state.value = _state.value.copy(isScanning = true, hasSharedAccess = access)
            val tracks = withContext(ioDispatcher) { scanner.scan(includeShared = access) }
            _state.value = LibraryState(
                tracks = tracks,
                byKey = tracks.associateBy { it.key },
                isScanning = false,
                hasSharedAccess = access,
                hasScannedOnce = true,
            )
        }
    }

    fun track(key: String): Track? = _state.value.byKey[key]
}
