// android/app/src/main/kotlin/com/secondserve/auth/GoogleSignInHelper.kt
package com.secondserve.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

class GoogleSignInHelper(private val webClientId: String) {

    suspend fun signIn(activity: Activity): String {
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
    ): String {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
            .setServerClientId(webClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(activity, request)
        val googleIdCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
        return googleIdCredential.idToken
    }
}
