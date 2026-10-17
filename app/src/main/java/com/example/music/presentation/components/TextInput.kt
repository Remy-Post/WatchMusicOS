package com.example.music.presentation.components

import android.app.RemoteInput
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender

private const val KEY_TEXT = "text"

/**
 * Opens the watch's system text input (keyboard, handwriting or voice, whichever the user
 * prefers) and returns what was entered. Same flow Samsung's apps use for naming things.
 */
@Composable
fun rememberTextInput(label: String, onResult: (String) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.let { RemoteInput.getResultsFromIntent(it) }?.getCharSequence(KEY_TEXT)
        if (!text.isNullOrBlank()) callback.value(text.toString().trim())
    }
    return {
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        val input = RemoteInput.Builder(KEY_TEXT)
            .setLabel(label)
            .wearableExtender {
                setEmojisAllowed(false)
                setInputActionType(EditorInfo.IME_ACTION_DONE)
            }
            .build()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(input))
        launcher.launch(intent)
    }
}
