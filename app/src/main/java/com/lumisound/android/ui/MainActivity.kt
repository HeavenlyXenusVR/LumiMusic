package com.lumisound.android.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.LumiMusicApp
import com.lumisound.android.ui.theme.LumiMusicTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as LumiMusicApp).container

        // The playback notification is how a media session is visible at all on
        // API 33+; asked for once, and playback still works if declined.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Presence follows the activity: friends see this account online while it is on
        // screen (or still playing), and offline promptly once it is not.
        //
        // A snapshot on return to the foreground: a repeating timer does not run while
        // the process is suspended, so without this the periodic tick would in practice
        // only ever produce launch-time samples -- exactly what happened on iOS.
        lifecycle.addObserver(
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_START -> {
                        container.diagnostics.noteForeground()
                        container.presence.onForeground()
                    }
                    androidx.lifecycle.Lifecycle.Event.ON_STOP -> container.presence.onBackground()
                    else -> Unit
                }
            }
        )

        setContent {
            val accent by container.appearance.accent.collectAsStateWithLifecycle()
            LumiMusicTheme(accentHex = accent) {
                LumiMusicRoot(container)
            }
        }
    }
}
