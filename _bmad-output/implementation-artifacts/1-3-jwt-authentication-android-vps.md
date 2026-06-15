---
baseline_commit: "TBD"
status: "ready-for-dev"
---

# Story 1.3: JWT Authentication Android ↔ VPS

**Status:** ready-for-dev

## Story

**As a** user,
**I want** the app to silently authenticate with my VPS on first launch,
**So that** my personal data stays secure on my private server.

## Acceptance Criteria

1. **Given** l'app se lance pour la première fois (aucun token stocké)
   **When** l'app s'initialise
   **Then** `POST /api/v1/auth/init` est appelé et retourne un JWT signé (`JWT_SECRET` côté VPS)

2. **And** le token est stocké dans `EncryptedSharedPreferences` (Android Keystore backed)

3. **And** les appels suivants incluent le header `Authorization: Bearer <token>`

4. **Given** un appel API est effectué sans token valide
   **When** le middleware `JWTBearer` évalue la requête
   **Then** le VPS retourne HTTP 401

5. **And** l'app détecte le 401 et déclenche une ré-authentification silencieuse

6. **And** toutes les routes `/api/v1/**` (sauf `/auth/init`) sont protégées par le middleware

## Architecture Context

### JWT Architecture (from architecture.md)

**Decision D2: JWT Token (EncryptedSharedPreferences)**
- Révocable sans rebuild
- Adapté pour mono-utilisateur
- Secret géré par variable d'environnement côté VPS

**Authentication Flow:**
- Premier lancement: `POST /api/v1/auth/init` → VPS génère et retourne un JWT signé
- Token stocké dans `EncryptedSharedPreferences` (Android Keystore backed)
- Header `Authorization: Bearer <token>` sur tous les appels REST
- Middleware `JWTBearer` sur toutes les routes `/api/v1/**` sauf `/auth/init`
- Secret JWT: variable d'environnement `JWT_SECRET` sur le VPS

**API Routes Established:**
- `POST /api/v1/auth/init` — initialisation JWT (déjà acceptée en AC1)
- Toutes les autres routes `/api/v1/**` protégées par JWTBearer

**Communication Pattern:**
- Phone ↔ VPS: REST/JWT via `VpsApiService.kt` + `JwtTokenStore.kt`
- Header format: `Authorization: Bearer <token>`

## Implementation Sequence (ARCH-13)

Cette story (ARCH-3) est **troisième** dans la chaîne obligatoire:
**ARCH-1 (done) → ARCH-2 (done) → ARCH-3 (cette story) → ARCH-6 (TennisScoreEngine) → ...**

Dépendances:
- ✅ Story 1.1: Gradle multi-module + Hilt DI configuré (`:app`, `:data`, `:core:ui`, etc.)
- ✅ Story 1.2: Backend FastAPI + `/api/v1/health` + structure feature-based + `Settings` avec `jwt_secret`
- ❌ Story 1.4+: Room database (créée après cette story)

**Pas de dépendance sur les autres stories — celle-ci est autonome.**

## Technical Requirements

### Android-Side Implementation

**Module Structure:**
- `:data/remote/api/` → HTTP client et services
  - `VpsApiService.kt` — client HTTP typé avec intercepteur JWT
  - `JwtTokenStore.kt` — persistent token storage via EncryptedSharedPreferences
  - `AuthService.kt` — logique d'authentification initiale et ré-authentification
  - `JwtInterceptor.kt` — okhttp intercepteur qui ajoute le header `Authorization: Bearer <token>`

- `:app/` → Hilt modules et app initialization
  - `SecondServeApp.kt` — trigger l'auth init au lancement (via repository)
  - `AuthModule.kt` (Hilt) — bindings pour AuthRepository, AuthService, VpsApiService

**Key Classes & Responsibilities:**

| Classe | Package | Responsabilité |
|--------|---------|-----------------|
| `JwtTokenStore` | `:data/remote/security/` | Stockage/récupération du JWT dans EncryptedSharedPreferences (Android Keystore) |
| `VpsApiService` | `:data/remote/api/` | Client HTTP (OkHttp + Retrofit) avec intercepteur JWT |
| `JwtInterceptor` | `:data/remote/api/` | okhttp Interceptor: ajoute `Authorization: Bearer <token>` à chaque requête |
| `AuthService` | `:data/remote/auth/` | Logique métier: `initAuth()` (POST /auth/init), `reauthenticate()`, gestion des 401 |
| `AuthRepository` | `:data/remote/auth/` | Interface repository; `AuthRepositoryImpl` intègre AuthService + JwtTokenStore |
| `AuthModule` (Hilt) | `:app/` | Bindings Hilt pour AuthRepository, OkHttp, Retrofit |

**Dependencies (Kotlin, à ajouter au libs.versions.toml + build.gradle.kts :data):**
- `retrofit` (HTTP client: 2.11.0+)
- `okhttp` (HTTP layer: 4.12.0+)
- `okhttp-logging-interceptor` (debug logging)
- `androidx.security:security-crypto` (EncryptedSharedPreferences)
- `moshi` (JSON serialization, Retrofit adapter: 1.15.0+)
- `kotlinx-serialization` (alternative à Moshi si choix)

**Testing:**
- Unittests: `JwtTokenStore`, `AuthService` (mock VpsApiService)
- Integration: `VpsApiService` avec mock HTTP server (MockWebServer)
- Config: EncryptedSharedPreferences non testable en CI (requires hardware) → utilisé MockSharedPreferences en tests

### VPS-Side Implementation (Backend FastAPI)

**VPS Routes:**
- `POST /api/v1/auth/init` (NOUVEAU) → retourne JWT
  - Request body: `{}` ou vide
  - Response: `{ "token": "<jwt-string>" }` (HTTP 200)
  - Aucun paramètre requis — le VPS génère un token unique par appel (accepte plusieurs clients simultanés, chacun reçoit son propre token)

**Middleware JWT:**
- Route `POST /api/v1/auth/init` **NOT protected** (généralement public)
- Toutes les autres routes `/api/v1/**` → décorateur `@verify_token` ou middleware `JWTBearer`
- Signature: `JWT.encode(payload={}, key=JWT_SECRET, algorithm="HS256")`
- Claims optionnels: `iat`, `exp` — **pas d'`aud`, `iss`, `sub` requis** pour le MVP mono-utilisateur

**Implementation Pattern:**
```python
# Exemple: app/core/security.py
from datetime import datetime, timedelta
import jwt

class JWTManager:
    def __init__(self, secret: str):
        self.secret = secret
    
    def create_token(self) -> str:
        payload = {
            "iat": datetime.utcnow(),
            "exp": datetime.utcnow() + timedelta(days=30)  # token valide 30 jours
        }
        return jwt.encode(payload, self.secret, algorithm="HS256")
    
    def verify_token(self, token: str) -> dict:
        try:
            return jwt.decode(token, self.secret, algorithms=["HS256"])
        except jwt.InvalidTokenError:
            raise HTTPException(status_code=401, detail="Invalid token")

# Middleware dans app/main.py ou app/core/security.py
class JWTBearer:
    def __init__(self, secret: str):
        self.secret = secret
        self.jwt_manager = JWTManager(secret)
    
    async def __call__(self, request: Request) -> dict:
        auth_header = request.headers.get("Authorization")
        if not auth_header or not auth_header.startswith("Bearer "):
            raise HTTPException(status_code=401, detail="Missing or invalid Authorization header")
        token = auth_header.split(" ")[1]
        return self.jwt_manager.verify_token(token)

# Usage: @app.get("/api/v1/sessions", dependencies=[Depends(jwt_bearer)])
```

**Error Responses:**
- `POST /api/v1/auth/init` failure: `{ "error_code": "AUTH_INIT_FAILED", "message": "...", "detail": "..." }` (HTTP 500, rare)
- Protected route without token: `{ "error_code": "UNAUTHORIZED", "message": "Missing or invalid token", "detail": null }` (HTTP 401)
- Invalid token: `{ "error_code": "INVALID_TOKEN", "message": "Token expired or tampered", "detail": null }` (HTTP 401)

**Libraries:**
- `pyjwt` (already available, added in story 1.2 or earlier)
- No additional FastAPI-specific JWT library required — `JWTBearer` can be implemented manually

### Coordination: Android ↔ VPS

**Flow Diagram:**

```
App Launch:
1. SecondServeApp.onCreate() → AuthRepository.initAuthIfNeeded()
2. AuthRepository checks JwtTokenStore.hasToken()
3. If NO token:
   - Calls AuthService.initAuth()
   - POST /api/v1/auth/init (body empty)
   - VPS responds: { "token": "eyJ..." }
   - Stores token in EncryptedSharedPreferences via JwtTokenStore.saveToken()
   - Continues app initialization

4. Future API calls:
   - JwtInterceptor.intercept() → adds "Authorization: Bearer <token>"
   - If VPS returns 401:
     - AuthService.reauthenticate() → retry POST /api/v1/auth/init
     - Save new token
     - Retry original request

5. If reauthenticate() fails:
   - App shows error or navigates to onboarding (TBD in later story)
```

## Development Context

### Previous Story Learnings (Story 1.2 — FastAPI Backend)

**What worked:**
- Pydantic v2 `BaseSettings` for config management
- Feature-based structure (`app/features/auth/`, etc.) scales well
- Alembic async setup proven stable
- `FastAPI` dependency injection (`Depends()`) works seamlessly
- Test fixture pattern with SQLite in-memory database

**Applied here:**
- JWT_SECRET already available in `settings` (Story 1.2) ✅
- Auth feature structure already stubbed at `app/features/auth/` ✅
- `app/api/v1/auth.py` router already included in main router ✅
- Error format already defined (`SecondServeException` handler in main.py) ✅

**Avoid:**
- ❌ Hardcoded secrets in code — use `settings.jwt_secret`
- ❌ Plaintext tokens — use `EncryptedSharedPreferences` (Android Keystore backed)
- ❌ Weak default JWT secret — validate in `Settings` that it's ≥32 characters in production

### Git Intelligence

**Recent commits (Stories 1.1 & 1.2):**
- Pattern: Large initial commit for scaffold, then incremental feature commits
- Gradle setup: Multi-module with `libs.versions.toml` centralized
- Backend setup: Feature-based structure established, no shared state monoliths
- Test pattern: pytest + AsyncClient for integration testing
- Naming: `snake_case` for Python, `PascalCase` for Kotlin classes

**Conventions to follow:**
- Python logging: `logger = logging.getLogger(__name__)` (not print)
- Kotlin: Hilt `@Module`, `@Provides`, `@Inject` for DI
- JWT secret must be validated at startup (≥32 chars) in `Settings`
- Token storage on Android uses `EncryptedSharedPreferences` (never SharedPreferences)

### File Structure — Updated State After Story 1.3

**New Android files (`:data` module):**
```
android/data/src/main/kotlin/com/secondserve/data/
├── remote/
│   ├── api/
│   │   ├── VpsApiService.kt          (Retrofit service)
│   │   ├── JwtInterceptor.kt         (okhttp Interceptor)
│   │   └── AuthApiSchema.kt          (Pydantic-like DTOs)
│   ├── auth/
│   │   ├── AuthService.kt            (business logic)
│   │   ├── AuthRepository.kt         (interface)
│   │   └── AuthRepositoryImpl.kt      (implementation)
│   └── security/
│       └── JwtTokenStore.kt          (EncryptedSharedPreferences)
```

**New VPS files (backend):**
```
backend/
├── app/
│   ├── core/
│   │   └── security.py               (JWTManager, JWTBearer)
│   ├── api/v1/
│   │   └── auth.py                   (router with POST /auth/init endpoint)
│   └── features/auth/
│       ├── service.py                (initiate token logic)
│       └── schemas.py                (TokenResponse DTO)
└── tests/
    └── integration/
        └── test_auth_api.py          (test POST /auth/init, JWT validation)
```

## Dev Notes

### Android JWT Implementation Details

**EncryptedSharedPreferences Setup:**
```kotlin
// JwtTokenStore.kt
private val encryptedSharedPreferences: EncryptedSharedPreferences by lazy {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    EncryptedSharedPreferences.create(
        context,
        "jwt_store",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

fun saveToken(token: String) {
    encryptedSharedPreferences.edit().putString("jwt_token", token).apply()
}

fun getToken(): String? = encryptedSharedPreferences.getString("jwt_token", null)

fun clearToken() {
    encryptedSharedPreferences.edit().remove("jwt_token").apply()
}
```

**OkHttp + Retrofit Setup:**
```kotlin
// Hilt AuthModule.kt
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides
    @Singleton
    fun provideJwtInterceptor(tokenStore: JwtTokenStore): JwtInterceptor =
        JwtInterceptor(tokenStore)
    
    @Provides
    @Singleton
    fun provideOkHttpClient(jwtInterceptor: JwtInterceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor()
            .apply { level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE }
        return OkHttpClient.Builder()
            .addInterceptor(jwtInterceptor)
            .addInterceptor(logging)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideVpsApiService(okHttpClient: OkHttpClient): VpsApiService {
        return Retrofit.Builder()
            .baseUrl("https://<vps-domain>/")
            .addConverterFactory(MoshiConverterFactory.create())
            .client(okHttpClient)
            .build()
            .create(VpsApiService::class.java)
    }
}
```

**JwtInterceptor Logic:**
```kotlin
class JwtInterceptor(private val tokenStore: JwtTokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = tokenStore.getToken()
        
        // Skip if no token (e.g., POST /auth/init)
        if (token == null) return chain.proceed(originalRequest)
        
        // Add Authorization header
        val authorizedRequest = originalRequest.newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .build()
        
        val response = chain.proceed(authorizedRequest)
        
        // If 401, try to re-authenticate
        if (response.code == 401) {
            return handleUnauthorized(chain, authorizedRequest)
        }
        
        return response
    }
    
    private fun handleUnauthorized(chain: Interceptor.Chain, originalRequest: Request): Response {
        // Note: This is simplified; in production, you'd inject AuthService here
        // For now, return 401 and let the app handle it at service level
        return chain.proceed(originalRequest.newBuilder()
            .removeHeader("Authorization")
            .build())
    }
}
```

**AuthService & Repository:**
```kotlin
// AuthService.kt
class AuthService(
    private val vpsApiService: VpsApiService,
    private val tokenStore: JwtTokenStore
) {
    suspend fun initAuth(): Result<String> = try {
        val response = vpsApiService.initAuth()  // POST /api/v1/auth/init
        val token = response.token
        tokenStore.saveToken(token)
        Result.success(token)
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    suspend fun reauthenticate(): Result<String> = initAuth()
}

// AuthRepository.kt — interface
interface AuthRepository {
    suspend fun initAuthIfNeeded(): Result<Unit>
    suspend fun reauthenticate(): Result<Unit>
}

// AuthRepositoryImpl.kt
class AuthRepositoryImpl(
    private val authService: AuthService,
    private val tokenStore: JwtTokenStore
) : AuthRepository {
    override suspend fun initAuthIfNeeded(): Result<Unit> {
        if (tokenStore.hasToken()) return Result.success(Unit)
        return authService.initAuth().map { }
    }
    
    override suspend fun reauthenticate(): Result<Unit> =
        authService.reauthenticate().map { }
}

// VpsApiService.kt
interface VpsApiService {
    @POST("api/v1/auth/init")
    suspend fun initAuth(): TokenResponse
    
    @GET("api/v1/health")
    suspend fun health(): HealthResponse
}

// AuthApiSchema.kt
data class TokenResponse(val token: String)
data class HealthResponse(val status: String)
```

**Integration in SecondServeApp.kt:**
```kotlin
@HiltAndroidApp
class SecondServeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // AuthRepository will be injected in MainActivity or via App startup Workers
        // For now: initialize in MainActivity.onCreate()
    }
}

// MainActivity.kt (or App startup flow)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val authRepository: AuthRepository by hiltViewModel() // or inject()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            authRepository.initAuthIfNeeded()
                .onFailure { 
                    // Show error or retry
                    Log.e("Auth", "Failed to initialize auth", it)
                }
        }
        // Continue with rest of UI
    }
}
```

### VPS JWT Implementation Details

**VPS Setup (backend/app/core/security.py):**
```python
from datetime import datetime, timedelta
import jwt
from fastapi import Depends, HTTPException, Request
from app.core.config import settings

class JWTManager:
    ALGORITHM = "HS256"
    
    def __init__(self, secret: str):
        if len(secret) < 32:
            raise ValueError("JWT_SECRET must be at least 32 characters")
        self.secret = secret
    
    def create_token(self, expires_delta: timedelta | None = None) -> str:
        if expires_delta is None:
            expires_delta = timedelta(days=30)
        
        expire = datetime.utcnow() + expires_delta
        payload = {
            "exp": expire.timestamp(),
            "iat": datetime.utcnow().timestamp(),
        }
        return jwt.encode(payload, self.secret, algorithm=self.ALGORITHM)
    
    def verify_token(self, token: str) -> dict:
        try:
            return jwt.decode(token, self.secret, algorithms=[self.ALGORITHM])
        except jwt.ExpiredSignatureError:
            raise HTTPException(status_code=401, detail="Token expired")
        except jwt.InvalidTokenError:
            raise HTTPException(status_code=401, detail="Invalid token")

async def verify_jwt(request: Request) -> dict:
    """Dependency for protected routes"""
    auth_header = request.headers.get("Authorization")
    if not auth_header or not auth_header.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing Authorization header")
    
    token = auth_header.split(" ", 1)[1]
    manager = JWTManager(settings.jwt_secret)
    return manager.verify_token(token)
```

**Auth Router (backend/app/api/v1/auth.py):**
```python
from fastapi import APIRouter
from pydantic import BaseModel
from app.core.security import JWTManager
from app.core.config import settings

router = APIRouter()

class TokenResponse(BaseModel):
    token: str

@router.post("/init")
async def init_auth() -> TokenResponse:
    """
    Initialize JWT authentication.
    Called once per client on first launch.
    """
    manager = JWTManager(settings.jwt_secret)
    token = manager.create_token()
    return TokenResponse(token=token)
```

**Main app integration (backend/app/main.py):**
```python
from fastapi import Depends
from app.api.v1.router import api_router
from app.core.security import verify_jwt

# Include auth router (NO protection on /auth/init)
app.include_router(api_router, prefix="/api/v1")

# For future protected routes, use:
# @app.get("/api/v1/sessions", dependencies=[Depends(verify_jwt)])
# async def get_sessions(token_payload: dict = Depends(verify_jwt)):
#     return {"sessions": []}
```

**Test (backend/tests/integration/test_auth_api.py):**
```python
import pytest
import jwt
from app.core.config import settings

@pytest.mark.asyncio
async def test_init_auth_returns_token(client):
    response = await client.post("/api/v1/auth/init")
    assert response.status_code == 200
    data = response.json()
    assert "token" in data
    assert isinstance(data["token"], str)
    
    # Verify JWT is valid
    decoded = jwt.decode(data["token"], settings.jwt_secret, algorithms=["HS256"])
    assert "exp" in decoded
    assert "iat" in decoded

@pytest.mark.asyncio
async def test_missing_auth_header_returns_401(client):
    # Create a protected endpoint for testing
    @app.get("/api/v1/test", dependencies=[Depends(verify_jwt)])
    async def protected():
        return {"data": "test"}
    
    response = await client.get("/api/v1/test")
    assert response.status_code == 401

@pytest.mark.asyncio
async def test_invalid_token_returns_401(client):
    response = await client.get("/api/v1/test", headers={"Authorization": "Bearer invalid.token.here"})
    assert response.status_code == 401
```

### Base URL Configuration

**Android (:app/AndroidManifest.xml or buildConfig):**
- VPS_DOMAIN must be configured as a BuildConfig or resource
- Example: `BuildConfig.VPS_BASE_URL = "https://secondserve.example.com/"`
- Will be injected into Retrofit base URL during Hilt module setup

**This story assumes VPS is already deployed — actual domain configuration deferred to deployment task**

### Testing Strategy

**Android:**
- Unit tests: `JwtTokenStore` (mock EncryptedSharedPreferences)
- Unit tests: `JwtInterceptor` (mock OkHttp chain)
- Integration tests: `VpsApiService` (MockWebServer — mock `/api/v1/auth/init` endpoint)
- Manual: Full flow on real device (Pixel 9 Pro) with real VPS

**VPS:**
- Unit tests: `JWTManager` (encode/decode tokens)
- Integration tests: `POST /api/v1/auth/init` → verify token validity
- Integration tests: `GET /api/v1/health` with invalid token → 401
- Integration tests: Protected route with valid token → 200

### Dependencies to Add

**Android (libs.versions.toml + :data/build.gradle.kts):**
```toml
[versions]
retrofit = "2.11.0"
okhttp = "4.12.0"
moshi = "1.15.0"
security-crypto = "1.1.0"

[libraries]
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-moshi = { group = "com.squareup.retrofit2", name = "converter-moshi", version.ref = "retrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
moshi = { group = "com.squareup.moshi", name = "moshi", version.ref = "moshi" }
moshi-kotlin = { group = "com.squareup.moshi", name = "moshi-kotlin", version.ref = "moshi" }
security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "security-crypto" }
```

**VPS (backend/pyproject.toml):**
- `pyjwt` — likely already available from earlier setup; if not: `uv add pyjwt>=2.8.0`

### Key Design Decisions

1. **No token refresh flow yet** — 30-day expiry acceptable for MVP. Refresh token pattern deferred to post-MVP.
2. **Stateless VPS** — No session table or token revocation DB. Tokens valid until expiration or VPS restart.
3. **Single token per client** — Android generates one token on first launch and reuses it. Logout (future) would clear local token only.
4. **No encryption in transit beyond HTTPS** — JWT is base64-encoded, not encrypted (standard practice).
5. **EncryptedSharedPreferences is sufficient** — No need for Hardware Security Module (HSM) for MVP.

### Anti-Patterns to Avoid

- ❌ Hardcoding `JWT_SECRET` or `vps_domain` in code → use `Settings` (Android BuildConfig)
- ❌ Storing JWT in regular SharedPreferences → must use EncryptedSharedPreferences (Android Keystore)
- ❌ Plaintext printing of JWT in logs → use `HttpLoggingInterceptor.Level.BASIC` or debug mode only
- ❌ Token validation without checking expiry → use `jwt.decode()` with exception handling
- ❌ Retry loop without exponential backoff — will implement in future if needed

## Acceptance Criteria Checklist

- [ ] AC1: `POST /api/v1/auth/init` returns JWT signed with `JWT_SECRET`
- [ ] AC2: Token stored in EncryptedSharedPreferences (Android Keystore backed)
- [ ] AC3: All subsequent requests include `Authorization: Bearer <token>` header
- [ ] AC4: Invalid/missing token → VPS returns HTTP 401
- [ ] AC5: App detects 401 and triggers silent re-authentication
- [ ] AC6: All routes `/api/v1/**` except `/auth/init` protected by middleware

## Tasks / Subtasks

### Backend VPS Tasks

- [ ] **Task VPS-1** — Implement JWT security layer (`app/core/security.py`)
  - [ ] `JWTManager` class with `create_token()` and `verify_token()` methods
  - [ ] `verify_jwt` dependency for protected routes
  - [ ] Validate `JWT_SECRET` at app startup (≥32 characters)
  - [ ] Add `pyjwt` to dependencies if missing

- [ ] **Task VPS-2** — Implement auth init endpoint (`backend/app/api/v1/auth.py`)
  - [ ] `TokenResponse` schema with `token: str` field
  - [ ] `POST /api/v1/auth/init` endpoint returning `TokenResponse`
  - [ ] Update `app/api/v1/router.py` to include auth router

- [ ] **Task VPS-3** — Apply JWT protection to existing routes (app/main.py)
  - [ ] Add `dependencies=[Depends(verify_jwt)]` to all routes except `/auth/init` and `/health` (optional)
  - [ ] Test protected routes return 401 without token

- [ ] **Task VPS-4** — Write integration tests (backend/tests/integration/test_auth_api.py)
  - [ ] Test `POST /api/v1/auth/init` returns valid token
  - [ ] Test JWT decode is successful
  - [ ] Test protected route without token → 401
  - [ ] Test protected route with invalid token → 401
  - [ ] Test protected route with valid token → 200 (depends on route implementation)

### Android Tasks

- [ ] **Task Android-1** — Create JwtTokenStore and security module (`:data/remote/security/`)
  - [ ] `JwtTokenStore.kt` with EncryptedSharedPreferences setup
  - [ ] `saveToken()`, `getToken()`, `hasToken()`, `clearToken()` methods
  - [ ] Add `androidx.security:security-crypto` dependency

- [ ] **Task Android-2** — Create HTTP client and interceptor (`:data/remote/api/`)
  - [ ] `JwtInterceptor.kt` — OkHttp interceptor that adds `Authorization: Bearer <token>`
  - [ ] `VpsApiService.kt` — Retrofit interface with `initAuth()` and `health()` endpoints
  - [ ] `AuthApiSchema.kt` — DTOs for `TokenResponse` and `HealthResponse`
  - [ ] Add Retrofit, OkHttp, Moshi dependencies to libs.versions.toml

- [ ] **Task Android-3** — Implement auth business logic (`:data/remote/auth/`)
  - [ ] `AuthService.kt` — calls `VpsApiService.initAuth()`, handles errors
  - [ ] `AuthRepository.kt` — interface
  - [ ] `AuthRepositoryImpl.kt` — implementation with `initAuthIfNeeded()` and `reauthenticate()`

- [ ] **Task Android-4** — Wire up Hilt DI (`:app/`)
  - [ ] `AuthModule.kt` — Hilt module with `@Provides` for OkHttpClient, Retrofit, AuthRepository, JwtTokenStore
  - [ ] Verify Hilt can build without errors
  - [ ] Update `SecondServeApp.kt` to be discoverable by Hilt

- [ ] **Task Android-5** — Integrate into app startup (`:app/MainActivity`)
  - [ ] Call `authRepository.initAuthIfNeeded()` in `MainActivity.onCreate()`
  - [ ] Handle success/failure gracefully
  - [ ] Log errors (not crashes)

- [ ] **Task Android-6** — Unit and integration tests (`:data/test/` and `:app/test/`)
  - [ ] Unit test `JwtTokenStore` with mock EncryptedSharedPreferences
  - [ ] Unit test `JwtInterceptor` with mock OkHttp chain
  - [ ] Integration test `VpsApiService` with MockWebServer (mock `/api/v1/auth/init`)
  - [ ] Integration test 401 handling (mock 401 response)

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| EncryptedSharedPreferences requires hardware | Use MockSharedPreferences in CI tests; hardware test on real device |
| VPS unreachable on app first launch | Set timeout (5s), show friendly error, allow retry |
| JWT_SECRET too weak in production | Validate `len(jwt_secret) >= 32` at startup (already in story 1.2 validation) |
| Token expires during session | Implement reauthentication on 401; refresh token pattern deferred |
| OkHttp interceptor retry loops | Keep interceptor simple; error handling at service layer only |
| Retrofit base URL configuration | Will be set via BuildConfig or Hilt injection; docs in auth module README |

## Success Criteria

- ✅ `POST /api/v1/auth/init` returns valid JWT (testable via `pytest` + `httpx`)
- ✅ Android app initializes auth on first launch without user interaction
- ✅ All HTTP requests include `Authorization: Bearer <token>` header
- ✅ Protected routes return 401 without valid token
- ✅ App detects 401 and re-authenticates silently
- ✅ All tests pass (unit + integration, both platforms)
- ✅ No hardcoded secrets in code

## References

- [Source: architecture.md § Authentication & Security] — JWT flow, EncryptedSharedPreferences, Bearer token pattern
- [Source: architecture.md § Decision D2] — JWT token rationale, mono-user design
- [Source: architecture.md § Implementation Patterns] — Security conventions, error schemas
- [Source: epics.md § Story 1.3] — Acceptance criteria, user story
- [Source: _bmad-output/implementation-artifacts/1-2-setup-fastapi-backend.md] — Backend patterns, test structure, Pydantic settings
- [Android Security Practices] — EncryptedSharedPreferences, Android Keystore
- [Retrofit + OkHttp] — HTTP client setup, interceptors
- [PyJWT] — JWT encode/decode Python library

