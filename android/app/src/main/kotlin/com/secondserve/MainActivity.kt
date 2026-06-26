// android/app/src/main/kotlin/com/secondserve/MainActivity.kt
package com.secondserve

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.secondserve.auth.GoogleSignInHelper
import com.secondserve.core.ui.theme.SecondServeTheme
import com.secondserve.data.remote.auth.AuthRepository
import com.secondserve.domain.event.DataLayerEventBus
import com.secondserve.navigation.AppNavGraph
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private enum class AuthState { Authenticated, Unauthenticated }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var googleSignInHelper: GoogleSignInHelper
    @Inject lateinit var dataLayerEventBus: DataLayerEventBus

    private val _pendingSessionId = mutableStateOf<Long?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            SecondServeTheme {
                var authState by remember {
                    mutableStateOf(
                        if (authRepository.hasToken()) AuthState.Authenticated else AuthState.Unauthenticated
                    )
                }
                val scope = rememberCoroutineScope()

                when (authState) {
                    AuthState.Authenticated -> {
                        LaunchedEffect(Unit) {
                            dataLayerEventBus.startSessionRequests.collect { sessionId ->
                                _pendingSessionId.value = sessionId
                            }
                        }
                        AppNavGraph(
                            pendingSessionId = _pendingSessionId.value,
                            onPendingSessionConsumed = { _pendingSessionId.value = null }
                        )
                    }
                    AuthState.Unauthenticated -> {
                        var isLoading by remember { mutableStateOf(false) }
                        var error by remember { mutableStateOf<String?>(null) }

                        Column(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("SecondServe", style = MaterialTheme.typography.headlineLarge)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Votre coach tennis IA",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(48.dp))
                            if (isLoading) {
                                CircularProgressIndicator()
                            } else {
                                Button(onClick = {
                                    isLoading = true
                                    error = null
                                    scope.launch {
                                        try {
                                            val idToken = googleSignInHelper.signIn(this@MainActivity)
                                            authRepository.initAuth(idToken)
                                                .onSuccess {
                                                    isLoading = false
                                                    authState = AuthState.Authenticated
                                                }
                                                .onFailure {
                                                    Timber.e(it, "Auth exchange failed")
                                                    error = "Connexion refusée. Vérifiez votre compte."
                                                    isLoading = false
                                                }
                                        } catch (e: Exception) {
                                            Timber.e(e, "Google sign-in failed")
                                            error = "Connexion annulée ou impossible."
                                            isLoading = false
                                        }
                                    }
                                }) {
                                    Text("Se connecter avec Google")
                                }
                            }
                            error?.let {
                                Spacer(Modifier.height(16.dp))
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == "com.secondserve.ACTION_OPEN_MATCH") {
            val sessionId = intent.getLongExtra("sessionId", -1L)
            if (sessionId != -1L) _pendingSessionId.value = sessionId
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    AlertDialog.Builder(this)
                        .setTitle("Conseils de coaching")
                        .setMessage("SecondServe vous envoie un conseil de tennis personnalisé selon votre fréquence choisie. Activez les notifications pour ne pas les manquer.")
                        .setPositiveButton("Autoriser") { _, _ -> requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        .setNegativeButton("Plus tard", null)
                        .show()
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}
