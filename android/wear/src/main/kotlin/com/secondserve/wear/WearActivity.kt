package com.secondserve.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.secondserve.wear.presentation.match.ScoreScreen
import com.secondserve.wear.presentation.theme.WearTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WearActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearTheme {
                // Story 2.4 : ScoreScreen directement pour tests.
                // Story 2.3 remplacera ceci par une navigation complète
                // (StartSessionScreen → ScoreScreen avec SessionFormat réel).
                ScoreScreen()
            }
        }
    }
}
