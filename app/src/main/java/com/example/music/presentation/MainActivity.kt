package com.example.music.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.music.appGraph
import com.example.music.playback.PlayerConnection
import com.example.music.presentation.player.BrightnessController
import com.google.android.horologist.audio.ui.VolumeViewModel

class MainActivity : ComponentActivity() {

    private lateinit var player: PlayerConnection
    private val volumeViewModel: VolumeViewModel by viewModels { VolumeViewModel.Factory }
    private val prefs by lazy { getSharedPreferences("app", MODE_PRIVATE) }

    /** Brightness for this app's window, set by sliding along the player's right edge. */
    private val brightness by lazy {
        val saved = prefs.getFloat(KEY_BRIGHTNESS, -1f)
        val initial = if (saved > 0f) {
            saved
        } else {
            runCatching { Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }
                .getOrDefault(0.6f)
        }
        BrightnessController(initial) { level ->
            applyBrightness(level)
            prefs.edit { putFloat(KEY_BRIGHTNESS, level) }
        }
    }

    private fun applyBrightness(level: Float) {
        window.attributes = window.attributes.apply { screenBrightness = level }
    }

    private val accessRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            appGraph.tracks.refresh()
            // Android stops showing the dialog after it was declined twice; fall back to the
            // app's settings page so the user can still switch it on.
            if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_AUDIO) &&
                prefs.getInt(KEY_DENIALS, 0) >= 1
            ) {
                openAppSettings()
            }
            if (!granted) prefs.edit { putInt(KEY_DENIALS, prefs.getInt(KEY_DENIALS, 0) + 1) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        player = PlayerConnection(this)
        prefs.getFloat(KEY_BRIGHTNESS, -1f).takeIf { it > 0f }?.let(::applyBrightness)

        setContent {
            WatchMusicApp(
                player = player,
                volumeViewModel = volumeViewModel,
                brightness = brightness,
                onRequestAccess = ::requestMusicAccess,
            )
        }

        // Ask once, on first launch, so the shared music library just shows up.
        if (!hasMusicAccess() && !prefs.getBoolean(KEY_ASKED, false)) requestMusicAccess()
    }

    override fun onStart() {
        super.onStart()
        player.connect()
        appGraph.tracks.refresh()
    }

    override fun onStop() {
        player.release()
        super.onStop()
    }

    private fun hasMusicAccess() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** Always tries the system dialog first; it may have been dismissed rather than declined. */
    private fun requestMusicAccess() {
        prefs.edit { putBoolean(KEY_ASKED, true) }
        accessRequest.launch(Manifest.permission.READ_MEDIA_AUDIO)
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
        )
    }

    private companion object {
        const val KEY_ASKED = "asked_music_access"
        const val KEY_DENIALS = "music_access_denials"
        const val KEY_BRIGHTNESS = "window_brightness"
    }
}
