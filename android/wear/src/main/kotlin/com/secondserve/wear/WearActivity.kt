package com.secondserve.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.secondserve.wear.presentation.theme.WearTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WearActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearTheme {
                // Contenu Wear OS — sera complété en stories suivantes
            }
        }
    }
}
