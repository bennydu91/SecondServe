package com.secondserve

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.secondserve.core.ui.theme.SecondServeTheme
import com.secondserve.navigation.AppNavGraph
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import com.secondserve.data.remote.auth.AuthRepository
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SecondServeTheme {
                LaunchedEffect(Unit) {
                    authRepository.initAuthIfNeeded()
                        .onFailure {
                            Timber.e(it, "Failed to initialize auth")
                        }
                }
                AppNavGraph()
            }
        }
    }
}
