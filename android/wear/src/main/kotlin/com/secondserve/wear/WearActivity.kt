package com.secondserve.wear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.secondserve.wear.navigation.WearNavGraph
import com.secondserve.wear.presentation.theme.WearTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WearActivity : ComponentActivity() {

    private val pendingStartIntent = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkIncomingIntent(intent)
        setContent {
            WearTheme {
                WearNavGraph(
                    pendingStartIntent = pendingStartIntent.value,
                    onStartIntentConsumed = { pendingStartIntent.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkIncomingIntent(intent)
    }

    private fun checkIncomingIntent(intent: Intent) {
        if (intent.hasExtra("matchFormat")) {
            pendingStartIntent.value = intent
        }
    }
}
