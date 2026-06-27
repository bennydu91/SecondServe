package com.secondserve.wear

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.secondserve.wear.navigation.WearNavGraph
import com.secondserve.wear.presentation.theme.WearTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class WearActivity : ComponentActivity() {

    private val pendingStartIntent = mutableStateOf<Intent?>(null)

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Non bloquant : si l'utilisateur refuse, l'ouverture auto depuis le téléphone
        // ne fonctionnera pas (la notification de lancement sera supprimée).
        Timber.d("WearActivity: POST_NOTIFICATIONS granted=%s", granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkIncomingIntent(intent)
        ensureLaunchPermissions()
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

    /**
     * Quand un match est démarré depuis le téléphone, [WearDataLayerListener] (réveillé en
     * arrière-plan par GMS) ne peut pas faire un startActivity() direct : l'OS le bloque
     * (Background Activity Launch). Il poste donc une notification full-screen-intent, seul
     * chemin de lancement autorisé depuis l'arrière-plan.
     *
     * Sur une install fraîche (Wear OS 4+ / Android 13+), deux autorisations manquent et font
     * échouer SILENCIEUSEMENT ce lancement (notif postée, mais aucune activité ouverte) :
     *  - POST_NOTIFICATIONS : sans elle, la notification est supprimée → rien ne se lance.
     *  - USE_FULL_SCREEN_INTENT : sans elle (non auto-accordée depuis Android 14), le
     *    full-screen est rétrogradé en notification passive → pas d'ouverture automatique.
     * On les demande ici, au premier lancement de l'app montre, pour éviter de devoir passer
     * par adb.
     */
    private fun ensureLaunchPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm != null && !nm.canUseFullScreenIntent()) {
                // Renvoie vers l'écran système d'autorisation full-screen-intent. runCatching :
                // l'action peut être absente sur certaines builds Wear OS — on ne crashe pas.
                runCatching {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                            .setData(Uri.parse("package:$packageName"))
                    )
                }.onFailure {
                    Timber.w(it, "WearActivity: écran full-screen-intent indisponible")
                }
            }
        }
    }
}
