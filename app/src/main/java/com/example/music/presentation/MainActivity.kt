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
import com.google.android.horologist.audio.ui.VolumeViewModel

class MainActivity : ComponentActivity() {

    private lateinit var player: PlayerConnection
    private val volumeViewModel: VolumeViewModel by viewModels { VolumeViewModel.Factory }
    private val prefs by lazy { getSharedPreferences("app", MODE_PRIVATE) }

    private val accessRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { appGraph.tracks.refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        player = PlayerConnection(this)

        setContent {
            WatchMusicApp(
                player = player,
                volumeViewModel = volumeViewModel,
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

    private fun requestMusicAccess() {
        val askedBefore = prefs.getBoolean(KEY_ASKED, false)
        if (askedBefore && !shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_AUDIO)) {
            // The system won't show the dialog again; send the user to the app's settings page.
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
            )
            return
        }
        prefs.edit { putBoolean(KEY_ASKED, true) }
        accessRequest.launch(Manifest.permission.READ_MEDIA_AUDIO)
    }

    private companion object {
        const val KEY_ASKED = "asked_music_access"
    }
}
