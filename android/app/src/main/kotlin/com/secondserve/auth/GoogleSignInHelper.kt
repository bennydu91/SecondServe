// android/app/src/main/kotlin/com/secondserve/auth/GoogleSignInHelper.kt
package com.secondserve.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** Résultat du sign-in : le token à échanger côté backend + le nom affiché par le compte Google
 *  (simple valeur de pré-remplissage pour le profil, l'utilisateur peut la changer ensuite). */
data class GoogleSignInResult(val idToken: String, val displayName: String?)

class GoogleSignInHelper(private val webClientId: String) {

    suspend fun signIn(activity: Activity): GoogleSignInResult {
        val credentialManager = CredentialManager.create(activity)

        // Tenter d'abord avec les comptes déjà autorisés (silent sign-in)
        return try {
            requestCredential(credentialManager, activity, filterByAuthorizedAccounts = true)
        } catch (_: NoCredentialException) {
            // Aucun compte autorisé → afficher le sélecteur de compte
            requestCredential(credentialManager, activity, filterByAuthorizedAccounts = false)
        }
    }

    private suspend fun requestCredential(
        credentialManager: CredentialManager,
        activity: Activity,
        filterByAuthorizedAccounts: Boolean,
    ): GoogleSignInResult {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
            .setServerClientId(webClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(activity, request)
        val googleIdCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
        return GoogleSignInResult(
            idToken = googleIdCredential.idToken,
            displayName = googleIdCredential.displayName
        )
    }
}
