# Google Sign-In Authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remplacer l'endpoint `/auth/init` anonyme par une authentification Google OAuth2 : l'app Android utilise le Credential Manager pour obtenir un Google ID Token, l'envoie au backend qui le vérifie via les JWKS Google et n'émet un JWT SecondServe que si l'email correspond à `ben.finot@gmail.com`.

**Architecture:** L'Android app utilise `androidx.credentials` + `com.google.android.libraries.identity.googleid` pour obtenir un ID Token signé par Google. Le backend vérifie ce token directement via l'endpoint JWKS public de Google (`https://www.googleapis.com/oauth2/v3/certs`) avec `httpx` + `pyjwt` (déjà dans les dépendances), sans Firebase. Une fois l'email vérifié, le backend émet son propre JWT court (30 jours) comme avant.

**Tech Stack:** Python `httpx` + `PyJWT` (RSA), Android Credential Manager API, Google Identity Services (`googleid:1.1.1`), `androidx.credentials:1.3.0`

## Global Constraints

- `authorized_email` hardcodé à `ben.finot@gmail.com` dans la config backend
- Python ≥ 3.12, FastAPI, aucune nouvelle dépendance backend (httpx + pyjwt déjà présents)
- Android `minSdk = 35` pour `:app`, `minSdk = 33` pour `:data`
- Hilt pour toute l'injection de dépendances Android
- Tests backend : `pytest-asyncio`, `unittest.mock` (pas de `respx`)
- Tests Android : `mockk` + `kotlinx-coroutines-test` + JUnit 5

---

## Prérequis : Configuration Google Cloud Console (étape manuelle)

Ces étapes sont à réaliser **une seule fois** avant d'exécuter les tâches de code.

- [ ] **Étape A : Créer un projet Google Cloud**
  - Aller sur https://console.cloud.google.com
  - Créer un projet "SecondServe" (ou utiliser un projet existant)

- [ ] **Étape B : Activer l'API Google Identity**
  - Dans le projet → APIs & Services → Library → chercher "Google Identity" → activer

- [ ] **Étape C : Configurer l'écran de consentement OAuth**
  - APIs & Services → OAuth consent screen
  - User Type : External (ou Internal si compte Google Workspace)
  - App name : "SecondServe", email support : `ben.finot@gmail.com`
  - Ajouter `ben.finot@gmail.com` dans "Test users"

- [ ] **Étape D : Créer le Web Client ID (pour le backend)**
  - APIs & Services → Credentials → Create Credentials → OAuth 2.0 Client ID
  - Application type : **Web application**
  - Name : "SecondServe Backend"
  - → Copier le **Client ID** (format `XXXXXX.apps.googleusercontent.com`)

- [ ] **Étape E : Créer l'Android Client ID**
  - Create Credentials → OAuth 2.0 Client ID
  - Application type : **Android**
  - Package name : `com.secondserve`
  - SHA-1 fingerprint (debug) : obtenir avec `./gradlew signingReport` dans `android/`
  - → Copier ce Client ID aussi

- [ ] **Étape F : Configurer le backend**
  - Ajouter dans `backend/.env` :
    ```
    GOOGLE_CLIENT_ID=<le Web Client ID de l'étape D>
    ```

- [ ] **Étape G : Configurer l'Android**
  - Dans `android/app/src/main/res/values/strings.xml`, noter qu'on ajoutera `google_web_client_id` avec la valeur du Web Client ID (étape D). Ce fichier sera créé/modifié en Task 6.

---

## Task 1 : Backend — Module de vérification du Google ID Token

**Files:**
- Create: `backend/app/core/google_auth.py`
- Modify: `backend/app/core/config.py`
- Modify: `backend/tests/conftest.py`
- Create: `backend/tests/unit/test_google_auth.py`

**Interfaces:**
- Produces: `async def verify_google_id_token(id_token: str, client_id: str) -> dict` — lève `jwt.InvalidTokenError` si le token est invalide (signature, expiration, audience, issuer), retourne le payload décodé (contient `email`, `email_verified`, `sub`)

- [ ] **Step 1 : Mettre à jour `config.py`**

```python
# backend/app/core/config.py
from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

_WEAK_DEFAULT_SECRET = "changeme-in-production"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    jwt_secret: str = _WEAK_DEFAULT_SECRET
    mistral_api_key: str = ""
    database_url: str = "sqlite+aiosqlite:///./secondserve.db"
    debug: bool = False
    port: int = 8000
    google_client_id: str = ""
    authorized_email: str = "ben.finot@gmail.com"

    @model_validator(mode="after")
    def validate_jwt_secret(self) -> "Settings":
        if not self.debug:
            if self.jwt_secret == _WEAK_DEFAULT_SECRET:
                raise ValueError(
                    "JWT_SECRET must be changed from the default value. "
                    "Set a strong secret (32+ chars) in your .env file."
                )
            if len(self.jwt_secret) < 32:
                raise ValueError(
                    "JWT_SECRET must be at least 32 characters long."
                )
        return self


settings = Settings()
```

- [ ] **Step 2 : Écrire le test unitaire (failing)**

```python
# backend/tests/unit/test_google_auth.py
import pytest
from unittest.mock import patch, AsyncMock, MagicMock
import jwt
from app.core.google_auth import verify_google_id_token


@pytest.mark.asyncio
async def test_verify_raises_invalid_token_when_no_matching_kid():
    mock_response = MagicMock()
    mock_response.json.return_value = {"keys": []}
    mock_response.raise_for_status = MagicMock()

    mock_client = AsyncMock()
    mock_client.get.return_value = mock_response

    with patch("app.core.google_auth.httpx.AsyncClient") as mock_cls:
        mock_cls.return_value.__aenter__ = AsyncMock(return_value=mock_client)
        mock_cls.return_value.__aexit__ = AsyncMock(return_value=False)

        with pytest.raises(jwt.InvalidTokenError, match="No matching signing key"):
            # Token with kid "unknown" — aucune clé correspondante dans le JWKS vide
            import base64, json as _json
            header = base64.urlsafe_b64encode(
                _json.dumps({"alg": "RS256", "kid": "unknown"}).encode()
            ).rstrip(b"=").decode()
            fake_token = f"{header}.payload.signature"
            await verify_google_id_token(fake_token, "test-client-id")
```

- [ ] **Step 3 : Vérifier que le test échoue**

```bash
cd /root/SecondServe/backend && uv run pytest tests/unit/test_google_auth.py -v
```
Attendu : `ModuleNotFoundError: No module named 'app.core.google_auth'`

- [ ] **Step 4 : Créer `backend/app/core/google_auth.py`**

```python
# backend/app/core/google_auth.py
import httpx
import jwt
from jwt.algorithms import RSAAlgorithm

GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs"
GOOGLE_ISSUERS = ["https://accounts.google.com", "accounts.google.com"]


async def verify_google_id_token(id_token: str, client_id: str) -> dict:
    header = jwt.get_unverified_header(id_token)
    kid = header.get("kid")

    async with httpx.AsyncClient() as client:
        response = await client.get(GOOGLE_JWKS_URL)
        response.raise_for_status()
        jwks = response.json()

    signing_key = None
    for jwk_data in jwks["keys"]:
        if jwk_data.get("kid") == kid:
            signing_key = RSAAlgorithm.from_jwk(jwk_data)
            break

    if signing_key is None:
        raise jwt.InvalidTokenError("No matching signing key")

    return jwt.decode(
        id_token,
        signing_key,
        algorithms=["RS256"],
        audience=client_id,
        issuer=GOOGLE_ISSUERS,
    )
```

- [ ] **Step 5 : Vérifier que le test passe**

```bash
cd /root/SecondServe/backend && uv run pytest tests/unit/test_google_auth.py -v
```
Attendu : `PASSED`

- [ ] **Step 6 : Mettre à jour `conftest.py` pour exposer les nouvelles variables d'env aux tests**

```python
# backend/tests/conftest.py  (modifier les 3 premières lignes os.environ)
import os

os.environ.setdefault("JWT_SECRET", "test-only-secret-do-not-use-in-production")
os.environ.setdefault("GOOGLE_CLIENT_ID", "test-google-client-id.apps.googleusercontent.com")
os.environ.setdefault("AUTHORIZED_EMAIL", "ben.finot@gmail.com")

# ... reste du fichier inchangé
```

- [ ] **Step 7 : Commit**

```bash
cd /root/SecondServe && rtk git add backend/app/core/google_auth.py backend/app/core/config.py backend/tests/conftest.py backend/tests/unit/test_google_auth.py && rtk git commit -m "feat(backend): add Google ID token verification via JWKS"
```

---

## Task 2 : Backend — Mettre à jour l'endpoint `/auth/init` + tests d'intégration

**Files:**
- Modify: `backend/app/api/v1/auth.py`
- Modify: `backend/tests/integration/test_auth_api.py`

**Interfaces:**
- Consumes: `verify_google_id_token(id_token, client_id)` de Task 1, `settings.authorized_email`, `settings.google_client_id`
- Produces: `POST /api/v1/auth/init` attend `{"google_id_token": "..."}`, retourne `{"token": "..."}` (200), ou 401/403/422

- [ ] **Step 1 : Écrire les nouveaux tests d'intégration (failing)**

Remplacer entièrement `backend/tests/integration/test_auth_api.py` :

```python
# backend/tests/integration/test_auth_api.py
import pytest
import jwt
from unittest.mock import patch, AsyncMock
from app.core.config import settings


VALID_GOOGLE_PAYLOAD = {
    "email": "ben.finot@gmail.com",
    "email_verified": True,
    "sub": "1234567890",
    "iss": "https://accounts.google.com",
    "aud": settings.google_client_id,
}

UNAUTHORIZED_GOOGLE_PAYLOAD = {
    "email": "hacker@gmail.com",
    "email_verified": True,
    "sub": "9999999999",
}


@pytest.mark.asyncio
async def test_init_auth_with_valid_google_token_returns_jwt(client):
    with patch(
        "app.api.v1.auth.verify_google_id_token",
        new=AsyncMock(return_value=VALID_GOOGLE_PAYLOAD),
    ):
        response = await client.post(
            "/api/v1/auth/init",
            json={"google_id_token": "fake.google.token"},
        )
    assert response.status_code == 200
    data = response.json()
    assert "token" in data
    decoded = jwt.decode(data["token"], settings.jwt_secret, algorithms=["HS256"])
    assert "exp" in decoded
    assert "iat" in decoded


@pytest.mark.asyncio
async def test_init_auth_with_invalid_google_token_returns_401(client):
    with patch(
        "app.api.v1.auth.verify_google_id_token",
        new=AsyncMock(side_effect=jwt.InvalidTokenError("bad signature")),
    ):
        response = await client.post(
            "/api/v1/auth/init",
            json={"google_id_token": "invalid.token.here"},
        )
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_init_auth_with_unauthorized_email_returns_403(client):
    with patch(
        "app.api.v1.auth.verify_google_id_token",
        new=AsyncMock(return_value=UNAUTHORIZED_GOOGLE_PAYLOAD),
    ):
        response = await client.post(
            "/api/v1/auth/init",
            json={"google_id_token": "fake.token.for.hacker"},
        )
    assert response.status_code == 403


@pytest.mark.asyncio
async def test_init_auth_missing_body_returns_422(client):
    response = await client.post("/api/v1/auth/init")
    assert response.status_code == 422


@pytest.mark.asyncio
async def test_init_auth_missing_token_field_returns_422(client):
    response = await client.post("/api/v1/auth/init", json={})
    assert response.status_code == 422


@pytest.mark.asyncio
async def test_missing_auth_header_returns_401(client):
    response = await client.get("/api/v1/auth/verify")
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_invalid_token_returns_401(client):
    response = await client.get(
        "/api/v1/auth/verify",
        headers={"Authorization": "Bearer invalid.token.here"},
    )
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_valid_token_returns_200(client):
    with patch(
        "app.api.v1.auth.verify_google_id_token",
        new=AsyncMock(return_value=VALID_GOOGLE_PAYLOAD),
    ):
        init_response = await client.post(
            "/api/v1/auth/init",
            json={"google_id_token": "fake.google.token"},
        )
    token = init_response.json()["token"]

    response = await client.get(
        "/api/v1/auth/verify",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 200
    assert response.json()["message"] == "Token is valid"
```

- [ ] **Step 2 : Vérifier que les tests échouent**

```bash
cd /root/SecondServe/backend && uv run pytest tests/integration/test_auth_api.py -v
```
Attendu : plusieurs `FAILED` (422 au lieu de 200, etc.)

- [ ] **Step 3 : Mettre à jour `backend/app/api/v1/auth.py`**

```python
# backend/app/api/v1/auth.py
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from app.core.security import JWTManager, verify_jwt
from app.core.config import settings
from app.core.google_auth import verify_google_id_token
import jwt

router = APIRouter()


class GoogleAuthRequest(BaseModel):
    google_id_token: str


class TokenResponse(BaseModel):
    token: str


@router.post("/init")
async def init_auth(request: GoogleAuthRequest) -> TokenResponse:
    try:
        payload = await verify_google_id_token(request.google_id_token, settings.google_client_id)
    except (jwt.InvalidTokenError, Exception):
        raise HTTPException(status_code=401, detail="Invalid Google token")

    if payload.get("email") != settings.authorized_email:
        raise HTTPException(status_code=403, detail="Unauthorized")

    manager = JWTManager(settings.jwt_secret)
    token = manager.create_token()
    return TokenResponse(token=token)


@router.get("/verify")
async def verify_auth(token_payload: dict = Depends(verify_jwt)) -> dict:
    return {"message": "Token is valid"}
```

- [ ] **Step 4 : Vérifier que tous les tests passent**

```bash
cd /root/SecondServe/backend && uv run pytest tests/integration/test_auth_api.py -v
```
Attendu : tous `PASSED`

- [ ] **Step 5 : Lancer tous les tests backend**

```bash
cd /root/SecondServe/backend && uv run pytest -v
```
Attendu : tous `PASSED`

- [ ] **Step 6 : Commit**

```bash
cd /root/SecondServe && rtk git add backend/app/api/v1/auth.py backend/tests/integration/test_auth_api.py && rtk git commit -m "feat(backend): require Google ID token on /auth/init"
```

---

## Task 3 : Android — Ajouter les dépendances Credential Manager

**Files:**
- Modify: `android/gradle/libs.versions.toml`
- Modify: `android/app/build.gradle.kts`

**Interfaces:**
- Produces: `libs.credentials`, `libs.credentials.play.services`, `libs.googleid` disponibles pour les modules Android

- [ ] **Step 1 : Ajouter les versions et entrées dans `libs.versions.toml`**

Dans la section `[versions]`, ajouter après `securityCrypto = "1.1.0"` :
```toml
credentials = "1.3.0"
googleid = "1.1.1"
```

Dans la section `[libraries]`, ajouter après `security-crypto = ...` :
```toml
credentials = { group = "androidx.credentials", name = "credentials", version.ref = "credentials" }
credentials-play-services = { group = "androidx.credentials", name = "credentials-play-services-auth", version.ref = "credentials" }
googleid = { group = "com.google.android.libraries.identity.googleid", name = "googleid", version.ref = "googleid" }
```

- [ ] **Step 2 : Ajouter les dépendances dans `android/app/build.gradle.kts`**

Dans le bloc `dependencies { ... }`, ajouter après `implementation(libs.timber)` :
```kotlin
    // Google Sign-In via Credential Manager
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)
```

- [ ] **Step 3 : Vérifier que le projet compile**

```bash
cd /root/SecondServe/android && ./gradlew :app:assembleDebug --quiet 2>&1 | tail -5
```
Attendu : `BUILD SUCCESSFUL`

- [ ] **Step 4 : Commit**

```bash
cd /root/SecondServe && rtk git add android/gradle/libs.versions.toml android/app/build.gradle.kts && rtk git commit -m "feat(android): add Credential Manager + googleid dependencies"
```

---

## Task 4 : Android — Mettre à jour la couche data (AuthService, AuthRepository, VpsApiService)

**Files:**
- Modify: `android/data/src/main/kotlin/com/secondserve/data/remote/api/AuthApiSchema.kt`
- Modify: `android/data/src/main/kotlin/com/secondserve/data/remote/api/VpsApiService.kt`
- Modify: `android/data/src/main/kotlin/com/secondserve/data/remote/auth/AuthService.kt`
- Modify: `android/data/src/main/kotlin/com/secondserve/data/remote/auth/AuthRepository.kt`
- Modify: `android/data/src/main/kotlin/com/secondserve/data/remote/api/TokenAuthenticator.kt`
- Modify: `android/data/src/test/kotlin/com/secondserve/data/remote/auth/AuthServiceTest.kt`

**Interfaces:**
- Consumes: `TokenStore` (inchangé), `VpsApiService` (modifié)
- Produces:
  - `AuthService.initAuth(googleIdToken: String): Result<String>`
  - `AuthRepository.initAuth(googleIdToken: String): Result<Unit>`
  - `AuthRepository.hasToken(): Boolean`

- [ ] **Step 1 : Mettre à jour les tests `AuthServiceTest.kt` (failing)**

```kotlin
// android/data/src/test/kotlin/com/secondserve/data/remote/auth/AuthServiceTest.kt
package com.secondserve.data.remote.auth

import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.secondserve.data.remote.api.GoogleAuthRequest
import com.secondserve.data.remote.api.TokenResponse
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.data.remote.security.TokenStore

class AuthServiceTest {

    private lateinit var vpsApiService: VpsApiService
    private lateinit var tokenStore: TokenStore
    private lateinit var authService: AuthService

    @BeforeEach
    fun setup() {
        vpsApiService = mockk()
        tokenStore = mockk(relaxed = true)
        authService = AuthService(vpsApiService, tokenStore)
    }

    @Test
    fun testInitAuthSuccessfullySavesToken() = runTest {
        val googleIdToken = "google.id.token"
        val jwtToken = "test.jwt.token"
        coEvery { vpsApiService.initAuth(GoogleAuthRequest(googleIdToken)) } returns TokenResponse(jwtToken)

        val result = authService.initAuth(googleIdToken)

        assertTrue(result.isSuccess)
        assertEquals(jwtToken, result.getOrNull())
        coVerify { tokenStore.saveToken(jwtToken) }
    }

    @Test
    fun testInitAuthFailureReturnsException() = runTest {
        val googleIdToken = "google.id.token"
        val exception = Exception("Network error")
        coEvery { vpsApiService.initAuth(GoogleAuthRequest(googleIdToken)) } throws exception

        val result = authService.initAuth(googleIdToken)

        assertTrue(result.isFailure)
        assertEquals(exception.message, result.exceptionOrNull()?.message)
    }

    @Test
    fun testInitAuthBlankTokenReturnsFailure() = runTest {
        val googleIdToken = "google.id.token"
        coEvery { vpsApiService.initAuth(GoogleAuthRequest(googleIdToken)) } returns TokenResponse("")

        val result = authService.initAuth(googleIdToken)

        assertTrue(result.isFailure)
    }
}
```

- [ ] **Step 2 : Vérifier que les tests échouent**

```bash
cd /root/SecondServe/android && ./gradlew :data:test --tests "com.secondserve.data.remote.auth.AuthServiceTest" 2>&1 | tail -15
```
Attendu : erreurs de compilation (méthode `initAuth()` sans paramètre, `GoogleAuthRequest` inexistant)

- [ ] **Step 3 : Mettre à jour `AuthApiSchema.kt`**

```kotlin
// android/data/src/main/kotlin/com/secondserve/data/remote/api/AuthApiSchema.kt
package com.secondserve.data.remote.api

data class GoogleAuthRequest(val google_id_token: String)

data class TokenResponse(val token: String)

data class HealthResponse(val status: String)
```

- [ ] **Step 4 : Mettre à jour `VpsApiService.kt`**

Remplacer la ligne `initAuth()` :
```kotlin
    @POST("api/v1/auth/init")
    suspend fun initAuth(@Body request: GoogleAuthRequest): TokenResponse
```

- [ ] **Step 5 : Mettre à jour `AuthService.kt`**

```kotlin
// android/data/src/main/kotlin/com/secondserve/data/remote/auth/AuthService.kt
package com.secondserve.data.remote.auth

import com.secondserve.data.remote.api.GoogleAuthRequest
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.data.remote.security.TokenStore

class AuthService(
    private val vpsApiService: VpsApiService,
    private val tokenStore: TokenStore
) {
    suspend fun initAuth(googleIdToken: String): Result<String> = try {
        val response = vpsApiService.initAuth(GoogleAuthRequest(googleIdToken))
        val token = response.token
        if (token.isBlank()) {
            Result.failure(IllegalStateException("Received blank token from server"))
        } else {
            tokenStore.saveToken(token)
            Result.success(token)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

- [ ] **Step 6 : Mettre à jour `AuthRepository.kt`**

```kotlin
// android/data/src/main/kotlin/com/secondserve/data/remote/auth/AuthRepository.kt
package com.secondserve.data.remote.auth

import com.secondserve.data.remote.security.TokenStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AuthRepository {
    suspend fun initAuth(googleIdToken: String): Result<Unit>
    fun hasToken(): Boolean
}

class AuthRepositoryImpl(
    private val authService: AuthService,
    private val tokenStore: TokenStore
) : AuthRepository {

    private val mutex = Mutex()

    override suspend fun initAuth(googleIdToken: String): Result<Unit> = mutex.withLock {
        authService.initAuth(googleIdToken).map { }
    }

    override fun hasToken(): Boolean = tokenStore.hasToken()
}
```

- [ ] **Step 7 : Mettre à jour `TokenAuthenticator.kt`**

```kotlin
// android/data/src/main/kotlin/com/secondserve/data/remote/api/TokenAuthenticator.kt
package com.secondserve.data.remote.api

import com.secondserve.data.remote.security.TokenStore
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val tokenStore: TokenStore
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        tokenStore.clearToken()
        return null
    }
}
```

- [ ] **Step 8 : Vérifier que les tests passent**

```bash
cd /root/SecondServe/android && ./gradlew :data:test --tests "com.secondserve.data.remote.auth.AuthServiceTest" 2>&1 | tail -15
```
Attendu : `BUILD SUCCESSFUL`, tous les tests `PASSED`

- [ ] **Step 9 : Commit**

```bash
cd /root/SecondServe && rtk git add android/data/src/ && rtk git commit -m "feat(android): update data layer for Google ID token auth"
```

---

## Task 5 : Android — `GoogleSignInHelper` + wiring DI + écran de login dans `MainActivity`

**Files:**
- Create: `android/app/src/main/kotlin/com/secondserve/auth/GoogleSignInHelper.kt`
- Create: `android/app/src/main/res/values/google_auth.xml`
- Modify: `android/app/src/main/kotlin/com/secondserve/di/AuthModule.kt`
- Modify: `android/app/src/main/kotlin/com/secondserve/MainActivity.kt`

**Interfaces:**
- Consumes: `AuthRepository.initAuth(googleIdToken)`, `AuthRepository.hasToken()`
- Produces: `GoogleSignInHelper.signIn(activity: Activity): String` — lève une exception si l'utilisateur annule ou si aucun compte Google n'est disponible

- [ ] **Step 1 : Créer le fichier de ressource pour le Web Client ID**

Créer `android/app/src/main/res/values/google_auth.xml` :
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Web Client ID créé dans Google Cloud Console (étape D des prérequis) -->
    <string name="google_web_client_id" translatable="false">REMPLACER_PAR_VOTRE_WEB_CLIENT_ID.apps.googleusercontent.com</string>
</resources>
```

> ⚠️ Remplacer `REMPLACER_PAR_VOTRE_WEB_CLIENT_ID` par la valeur réelle obtenue à l'étape D des prérequis.

- [ ] **Step 2 : Créer `GoogleSignInHelper.kt`**

```kotlin
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
```

- [ ] **Step 3 : Mettre à jour `AuthModule.kt`**

Remplacer entièrement `android/app/src/main/kotlin/com/secondserve/di/AuthModule.kt` :
```kotlin
package com.secondserve.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.secondserve.BuildConfig
import com.secondserve.R
import com.secondserve.auth.GoogleSignInHelper
import com.secondserve.data.remote.api.JwtInterceptor
import com.secondserve.data.remote.api.TokenAuthenticator
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.data.remote.auth.AuthService
import com.secondserve.data.remote.auth.AuthRepository
import com.secondserve.data.remote.auth.AuthRepositoryImpl
import com.secondserve.data.remote.security.JwtTokenStore
import com.secondserve.data.remote.security.TokenStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthClient

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideTokenStore(@ApplicationContext context: Context): TokenStore {
        return JwtTokenStore(context)
    }

    @Provides
    @Singleton
    fun provideJwtInterceptor(tokenStore: TokenStore): JwtInterceptor {
        return JwtInterceptor(tokenStore)
    }

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    @Singleton
    @AuthClient
    fun provideAuthOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @AuthClient
    fun provideAuthVpsApiService(@AuthClient okHttpClient: OkHttpClient, moshi: Moshi): VpsApiService {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.VPS_BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(okHttpClient)
            .build()
            .create(VpsApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthService(
        @AuthClient vpsApiService: VpsApiService,
        tokenStore: TokenStore
    ): AuthService {
        return AuthService(vpsApiService, tokenStore)
    }

    @Provides
    @Singleton
    fun provideTokenAuthenticator(tokenStore: TokenStore): TokenAuthenticator =
        TokenAuthenticator(tokenStore)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        jwtInterceptor: JwtInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .authenticator(tokenAuthenticator)
            .addInterceptor(jwtInterceptor)
            .addInterceptor(logging)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideVpsApiService(okHttpClient: OkHttpClient, moshi: Moshi): VpsApiService {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.VPS_BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(okHttpClient)
            .build()
            .create(VpsApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        authService: AuthService,
        tokenStore: TokenStore
    ): AuthRepository {
        return AuthRepositoryImpl(authService, tokenStore)
    }

    @Provides
    @Singleton
    fun provideGoogleSignInHelper(@ApplicationContext context: Context): GoogleSignInHelper {
        return GoogleSignInHelper(context.getString(R.string.google_web_client_id))
    }
}
```

- [ ] **Step 4 : Mettre à jour `MainActivity.kt`**

```kotlin
// android/app/src/main/kotlin/com/secondserve/MainActivity.kt
package com.secondserve

import android.Manifest
import android.app.AlertDialog
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
import androidx.compose.runtime.Composable
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
import com.secondserve.navigation.AppNavGraph
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var googleSignInHelper: GoogleSignInHelper

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op : l'app fonctionne sans notif si refusé */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    AlertDialog.Builder(this)
                        .setTitle("Conseils de coaching")
                        .setMessage("SecondServe vous envoie un conseil de tennis personnalisé selon votre fréquence choisie. Activez les notifications pour ne pas les manquer.")
                        .setPositiveButton("Autoriser") { _, _ ->
                            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        .setNegativeButton("Plus tard", null)
                        .show()
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        setContent {
            SecondServeTheme {
                if (authRepository.hasToken()) {
                    AppNavGraph()
                } else {
                    LoginScreen(
                        onSignInClick = { signIn() }
                    )
                }
            }
        }
    }

    private fun signIn() {
        // Le recompose géré par le state dans LoginScreen
    }
}

@Composable
private fun LoginScreen(onSignInClick: () -> Unit) {
    // Géré via Activity pour accéder au CredentialManager (nécessite un contexte Activity)
    // Ce composable déclenche le flux via le callback
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SecondServe",
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Votre coach tennis IA",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(48.dp))
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(onClick = {
                isLoading = true
                errorMessage = null
                onSignInClick()
            }) {
                Text("Se connecter avec Google")
            }
        }
        errorMessage?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
```

> **Note :** L'architecture Composable + Activity pour Google Sign-In nécessite de passer le contexte Activity au `CredentialManager`. La solution propre est d'utiliser un `ViewModel` avec un `SharedFlow` pour les événements. Voici l'implémentation correcte pour `MainActivity` en remplaçant le bloc `setContent` :

```kotlin
// Remplacer le bloc setContent dans onCreate par :
        setContent {
            SecondServeTheme {
                var authState by remember { mutableStateOf(if (authRepository.hasToken()) AuthState.Authenticated else AuthState.Unauthenticated) }
                val scope = rememberCoroutineScope()

                when (authState) {
                    AuthState.Authenticated -> AppNavGraph()
                    AuthState.Unauthenticated -> {
                        LoginScreen(
                            onSignInClick = {
                                scope.launch {
                                    try {
                                        val idToken = googleSignInHelper.signIn(this@MainActivity)
                                        authRepository.initAuth(idToken)
                                            .onSuccess { authState = AuthState.Authenticated }
                                            .onFailure { Timber.e(it, "Auth failed") }
                                    } catch (e: Exception) {
                                        Timber.e(e, "Google sign-in failed")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
```

```kotlin
// Ajouter dans MainActivity.kt (hors de la classe ou comme sealed class dans le même fichier) :
private enum class AuthState { Authenticated, Unauthenticated }
```

> Et simplifier `signIn()` à une fonction vide (le callback est directement dans le Composable via `scope.launch`). Ou supprimer `signIn()` complètement.

**Voici le `MainActivity.kt` final complet et correct :**

```kotlin
// android/app/src/main/kotlin/com/secondserve/MainActivity.kt
package com.secondserve

import android.Manifest
import android.app.AlertDialog
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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    AuthState.Authenticated -> AppNavGraph()
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
                                                .onSuccess { authState = AuthState.Authenticated }
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
```

- [ ] **Step 5 : Vérifier que le projet compile**

```bash
cd /root/SecondServe/android && ./gradlew :app:assembleDebug 2>&1 | tail -10
```
Attendu : `BUILD SUCCESSFUL`

- [ ] **Step 6 : Lancer tous les tests Android**

```bash
cd /root/SecondServe/android && ./gradlew :data:test 2>&1 | tail -15
```
Attendu : `BUILD SUCCESSFUL`

- [ ] **Step 7 : Commit**

```bash
cd /root/SecondServe && rtk git add android/app/src/ && rtk git commit -m "feat(android): Google Sign-In flow with Credential Manager"
```

---

## Task 6 : Test d'intégration manuel end-to-end

Cette tâche ne peut être exécutée qu'avec un appareil/émulateur Android et les clés Google Cloud configurées.

- [ ] **Step 1 : Vérifier la configuration backend**

```bash
grep -E "GOOGLE_CLIENT_ID|AUTHORIZED_EMAIL" /root/SecondServe/backend/.env
```
Attendu : les deux variables sont présentes et renseignées.

- [ ] **Step 2 : Démarrer le backend**

```bash
cd /root/SecondServe/backend && uv run uvicorn app.main:app --reload --port 8000
```

- [ ] **Step 3 : Tester le refus sans token Google**

```bash
curl -s -X POST http://localhost:8000/api/v1/auth/init | python3 -m json.tool
```
Attendu : `{"detail": "Unprocessable Entity"}` avec status 422 (body manquant)

- [ ] **Step 4 : Tester le refus avec un faux token**

```bash
curl -s -X POST http://localhost:8000/api/v1/auth/init \
  -H "Content-Type: application/json" \
  -d '{"google_id_token": "fake.token.here"}' | python3 -m json.tool
```
Attendu : `{"detail": "Invalid Google token"}` avec status 401

- [ ] **Step 5 : Déployer l'app Android sur le Pixel 9 Pro et tester le flux complet**

- Lancer l'app → l'écran "Se connecter avec Google" s'affiche
- Cliquer "Se connecter avec Google" → le sélecteur de compte Google s'ouvre
- Sélectionner `ben.finot@gmail.com` → l'app passe à l'écran principal
- Fermer et rouvrir l'app → l'écran de login ne s'affiche plus (token en cache)
- Vérifier dans les logs backend : la requête `/auth/init` a abouti avec status 200

---

## Récapitulatif des fichiers modifiés

| Fichier | Action |
|---------|--------|
| `backend/app/core/google_auth.py` | Créé |
| `backend/app/core/config.py` | Modifié (+ `google_client_id`, `authorized_email`) |
| `backend/app/api/v1/auth.py` | Modifié (accepte `GoogleAuthRequest`) |
| `backend/tests/conftest.py` | Modifié (+ vars d'env test) |
| `backend/tests/unit/test_google_auth.py` | Créé |
| `backend/tests/integration/test_auth_api.py` | Remplacé |
| `android/gradle/libs.versions.toml` | Modifié (+ credentials, googleid) |
| `android/app/build.gradle.kts` | Modifié (+ 3 deps) |
| `android/data/.../api/AuthApiSchema.kt` | Modifié (+ `GoogleAuthRequest`) |
| `android/data/.../api/VpsApiService.kt` | Modifié (`initAuth(@Body)`) |
| `android/data/.../api/TokenAuthenticator.kt` | Simplifié (clear + null) |
| `android/data/.../auth/AuthService.kt` | Modifié (`initAuth(googleIdToken)`) |
| `android/data/.../auth/AuthRepository.kt` | Modifié (nouvelle interface) |
| `android/data/.../auth/AuthServiceTest.kt` | Mis à jour |
| `android/app/.../auth/GoogleSignInHelper.kt` | Créé |
| `android/app/.../di/AuthModule.kt` | Modifié (+ `GoogleSignInHelper`) |
| `android/app/.../MainActivity.kt` | Modifié (login screen + flow) |
| `android/app/src/main/res/values/google_auth.xml` | Créé |
