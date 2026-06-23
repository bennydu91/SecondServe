---
baseline_commit: e25e4127fac18c5eb8f249eb439b93b42d11427f
---

# Story 5.1: VpsMistralEngine & routing Mistral via VPS

Status: done

## Story

As a developer,
I want a `VpsMistralEngine` that proxies all Mistral API calls through the VPS,
so that the Mistral API key never appears in the Android app.

## Acceptance Criteria

1. **Given** `VpsMistralEngine` est initialisé  
   **When** `generate(prompt)` est appelé  
   **Then** il envoie `POST /api/v1/coaching/analyze` au VPS (header JWT inclus via l'OkHttpClient injecté)  
   **And** le VPS appelle `mistral-small-latest` via `mistral_client.py` (httpx async, timeout 15s, 1 retry sur timeout)  
   **And** la réponse est retournée à l'app en ≤ 10 secondes (NFR-P4)  
   **And** la clé Mistral API est stockée uniquement côté VPS (variable d'environnement `MISTRAL_API_KEY` — jamais dans l'APK)  
   **And** le payload envoyé à Mistral ne contient aucun identifiant personnel (NFR-C3, NFR-S5) — la conformité est assurée par la construction du prompt dans `CoachingResolver.buildPrompt()` qui exclut la licence FFT

2. **Given** l'appel VPS échoue ou dépasse le timeout  
   **When** `generate()` retourne  
   **Then** `AppResult.Error(InferenceEngineException(ErrorCode.NETWORK_UNAVAILABLE, ...))` est retourné

3. **And** Hilt fournit `VpsMistralEngine` via qualifier `@VpsMistralEngine InferenceEngine` dans les features hors-match futures (Coaching, Notifications)

4. **And** `CoachingResolver` est mis à jour pour une chaîne Gemini → VpsMistral → Cache → Static (fallback réseau in-match)

## Tasks / Subtasks

- [x] **T1 — Dépendances `:core:ai`** (prérequis)
  - [x] T1.1 Ajouter `implementation(libs.okhttp)`, `implementation(libs.moshi)`, `implementation(libs.moshi.kotlin)` dans `android/core/ai/build.gradle.kts`

- [x] **T2 — Qualifiers Hilt** (AC: 3)
  - [x] T2.1 Créer `android/core/ai/src/main/kotlin/com/secondserve/core/ai/di/InferenceEngineQualifiers.kt` avec `@qualifier @Retention(BINARY) annotation class GeminiEngine` et `@VpsMistralEngine`

- [x] **T3 — `VpsMistralEngine`** (AC: 1, 2)
  - [x] T3.1 Créer `android/core/ai/src/main/kotlin/com/secondserve/core/ai/vps/VpsMistralEngine.kt`
  - [x] T3.2 `@Inject constructor(okHttpClient: OkHttpClient, @Named("vps_base_url") baseUrl: String)`
  - [x] T3.3 Sérialiser `AnalyzeRequest(prompt: String)` via Moshi → POST body
  - [x] T3.4 Désérialiser `AnalyzeResponse(content: String)` via Moshi
  - [x] T3.5 Timeout OkHttp : call avec `timeout(20, SECONDS)` (override OkHttpClient) pour NFR-P4 ≤10s
  - [x] T3.6 Retourner `AppResult.Error(InferenceEngineException(ErrorCode.NETWORK_UNAVAILABLE, ...))` sur IOException, HttpException (non-2xx) et timeout

- [x] **T4 — Mise à jour `AiModule` (release)** (AC: 3)
  - [x] T4.1 Mettre à jour `android/app/src/release/kotlin/com/secondserve/di/AiModule.kt` : qualifier `@GeminiEngine` sur `GeminiNanoEngine`, qualifier `@VpsMistralEngine` sur `VpsMistralEngine`
  - [x] T4.2 Ajouter `@Provides @Named("vps_base_url") fun provideVpsBaseUrl() = BuildConfig.VPS_BASE_URL`
  - [x] T4.3 Ajouter `@Provides @Singleton fun provideMoshiForAi(): Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()` (si non déjà disponible via scope Hilt — vérifier AuthModule)

- [x] **T5 — Mise à jour `AiModule` (debug)** (AC: 3)
  - [x] T5.1 Mettre à jour `android/app/src/debug/kotlin/com/secondserve/di/AiModule.kt` : provides `@GeminiEngine` et `@VpsMistralEngine` avec `MockInferenceEngine()`

- [x] **T6 — Mise à jour `CoachingResolver`** (AC: 4)
  - [x] T6.1 Ajouter `@GeminiEngine` sur `inferenceEngine` dans le constructeur + ajouter `@VpsMistralEngine vpsEngine: InferenceEngine`
  - [x] T6.2 Ajouter un step 2 (VpsMistral) entre GeminiNano et Cache : `withTimeout(5_000L) { vpsEngine.generate(prompt) }` — timeout réduit en match-mode pour ne pas bloquer l'UX
  - [x] T6.3 Mettre à jour log Timber et `CoachingSource` : ajouter `VPS_MISTRAL` si pas déjà présent

- [x] **T7 — Backend VPS : dépendance httpx** (prérequis)
  - [x] T7.1 Ajouter `httpx>=0.27.0` dans les dépendances principales de `backend/pyproject.toml` (pas seulement dev)

- [x] **T8 — Backend VPS : `mistral_client.py`** (AC: 1)
  - [x] T8.1 Implémenter `backend/app/features/coaching/mistral_client.py` : `async def generate(prompt: str, api_key: str) -> str`
  - [x] T8.2 httpx.AsyncClient, POST vers `https://api.mistral.ai/v1/chat/completions`, model `mistral-small-latest`, timeout=15.0
  - [x] T8.3 1 retry sur `httpx.TimeoutException` (pas sur erreurs 4xx/5xx)
  - [x] T8.4 Lever `SecondServeException("MISTRAL_UNAVAILABLE", ..., status_code=503)` sur échec définitif

- [x] **T9 — Backend VPS : `schemas.py`** (AC: 1)
  - [x] T9.1 Implémenter `backend/app/features/coaching/schemas.py` : `AnalyzeRequest(prompt: str)`, `AnalyzeResponse(content: str)`

- [x] **T10 — Backend VPS : `service.py`** (AC: 1)
  - [x] T10.1 Implémenter `backend/app/features/coaching/service.py` : `async def analyze(prompt: str, api_key: str) -> str` qui délègue à `mistral_client.generate()`

- [x] **T11 — Backend VPS : endpoint `coaching.py`** (AC: 1)
  - [x] T11.1 Implémenter `backend/app/api/v1/coaching.py` : `POST /analyze` → `AnalyzeRequest` → `CoachingService.analyze()` → `AnalyzeResponse`
  - [x] T11.2 Injecter `settings.mistral_api_key` (déjà dans `config.py`)

- [x] **T12 — Tests unitaires** (AC: 1, 2)
  - [x] T12.1 `android/core/ai/src/test/kotlin/com/secondserve/core/ai/vps/VpsMistralEngineTest.kt` : MockWebServer — success 200, erreur 500, timeout
  - [x] T12.2 `backend/tests/unit/test_coaching_service.py` : mock `mistral_client.generate()` — success, timeout, erreur Mistral
  - [x] T12.3 `backend/tests/integration/test_coaching_api.py` : mock `mistral_client` — POST /analyze success (200), mock MISTRAL_API_KEY absente (503)

### Review Findings

- [x] [Review][Patch] analyzeUrl construite sans normalisation de la barre oblique finale — URL silencieusement invalide si VPS_BASE_URL sans `/` [android/core/ai/src/main/kotlin/com/secondserve/core/ai/vps/VpsMistralEngine.kt:144]
- [x] [Review][Patch] JsonDataException (Moshi) non catchée dans VpsMistralEngine — propagation crash si réponse JSON malformée [android/core/ai/src/main/kotlin/com/secondserve/core/ai/vps/VpsMistralEngine.kt]
- [x] [Review][Patch] Test generate-timeout non-déterministe — callTimeout(2s) du client de test écrasé à 20s par le constructeur de VpsMistralEngine [android/core/ai/src/test/kotlin/com/secondserve/core/ai/vps/VpsMistralEngineTest.kt]
- [x] [Review][Patch] httpx.ConnectError et exceptions réseau non-httpx non catchées — propagation en 500 non structuré au lieu de SecondServeException 503 [backend/app/features/coaching/mistral_client.py]
- [x] [Review][Patch] response.json() KeyError si Mistral retourne un format inattendu (choices manquant, message manquant) — 500 non structuré [backend/app/features/coaching/mistral_client.py]
- [x] [Review][Patch] Test Python MISTRAL_API_KEY absente manquant — explicitement requis par les Dev Notes (T12.3 dit "mock MISTRAL_API_KEY absente") [backend/tests/integration/test_coaching_api.py]
- [x] [Review][Patch] BuildConfig.VPS_BASE_URL vide non vérifié à l'initialisation — toutes les requêtes VPS échouent silencieusement avec URL invalide [android/app/src/release/kotlin/com/secondserve/di/AiModule.kt]
- [x] [Review][Patch] getSessionById/buildMatchContextProfile déplacés hors du withTimeout Gemini — DB calls sans protection timeout, régression vs code original [android/feature/match/src/main/kotlin/com/secondserve/feature/match/CoachingResolver.kt]
- [x] [Review][Defer] Circuit breaker absent pour VPS en match — 5s × N appels consécutifs = latence cumulative en cas de VPS dégradé — deferred, amélioration post-MVP
- [x] [Review][Defer] MISTRAL_API_KEY passée en paramètre de fonction — visible dans stack traces Python en cas d'exception non gérée — deferred, risque faible (Python ne loggue pas les args automatiquement)
- [x] [Review][Defer] MISTRAL_API_KEY vide → erreur opaque MISTRAL_ERROR (401 Mistral) au lieu de MISTRAL_NOT_CONFIGURED — deferred, diagnostic ops
- [x] [Review][Defer] httpx timeout (15s) > Android withTimeout (5s) — VPS continue à traiter après abandon Android, gaspillage ressources — deferred, architectural
- [x] [Review][Defer] range(2) fragile pour exprimer "1 retry" — sémantiquement correct mais modifiable sans comprendre l'invariant — deferred, code style
- [x] [Review][Defer] buildMatchContextProfile() — vérifier qu'il exclut bien les PII (licence FFT, etc.) — deferred, à valider en revue NFR-C3

## Dev Notes

### Architecture critique — Séparation des responsabilités

**Ne PAS ajouter Retrofit à `:core:ai`.** Utiliser OkHttp directement + Moshi pour sérialisation JSON.

La hiérarchie des modules est : `:core:ai → :domain` uniquement. `:core:ai` ne dépend PAS de `:data`. L'`OkHttpClient` injecté est celui fourni par `AuthModule` dans `SingletonComponent` (avec `JwtInterceptor` + `TokenAuthenticator`). Pas besoin de créer un nouveau client HTTP — le JWT est automatiquement attaché.

**Override du timeout uniquement sur l'appel** (pas sur l'OkHttpClient partagé) :
```kotlin
val request = Request.Builder().url("${baseUrl}api/v1/coaching/analyze").post(body).build()
val response = okHttpClient.newCall(request)
    .execute()  // ou .await() si OkHttp coroutines — voir T3.5
```
Utiliser `okhttp3-coroutines` ou wrapper manuel avec `suspendCancellableCoroutine`.

### Qualifier Hilt — Pourquoi deux qualifiers

`CoachingResolver` dans `:feature:match` injecte déjà `InferenceEngine` sans qualifier (binding actuel = GeminiNanoEngine). Il faut ajouter `@GeminiEngine` ET `@VpsMistralEngine` pour permettre la chaîne à deux moteurs sans casser le binding existant.

**Impact sur `CoachingResolverTest`** : les tests existants utilisent `MockInferenceEngine()` directement (pas de Hilt). Après T6, le constructeur a deux paramètres — mettre à jour les tests pour passer deux `MockInferenceEngine` avec `@GeminiEngine` et `@VpsMistralEngine`.

### Moshi dans `:core:ai` — Risque de doublon

`AuthModule` (dans `:app`) fournit déjà un `Moshi` dans `SingletonComponent`. Ce bean est injectable dans `VpsMistralEngine` **sans** créer un nouveau `Moshi` dans `AiModule` — Hilt résout automatiquement. Ne pas créer de doublon si `AuthModule.provideMoshi()` est déjà en scope.

**Vérifier** : si `AiModule` est dans le même `SingletonComponent`, le `Moshi` d'AuthModule est disponible. Injecter directement `Moshi` dans `VpsMistralEngine` constructor.

### Chaîne in-match — Timeout VpsMistral réduit à 5s

NFR-P4 (≤10s) s'applique aux features hors-match (Stories 5.2+). En match (`CoachingResolver.resolve()`), on ne peut pas bloquer l'UX 10s — timeout de 5s maximum avant de tomber sur le Cache.

```kotlin
// CoachingResolver — Step 2 : VpsMistral fallback
val vpsResult = try {
    withTimeout(5_000L) {
        vpsEngine.generate(prompt)
    }
} catch (e: TimeoutCancellationException) {
    Timber.d("CoachingResolver: VpsMistral timeout, falling to cache")
    AppResult.Error(e)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    AppResult.Error(e)
}
if (vpsResult is AppResult.Success) {
    return CoachingResult(vpsResult.data, CoachingSource.VPS_MISTRAL)
}
```

Vérifier si `CoachingSource` dans `:domain` a déjà `VPS_MISTRAL` — si non, l'ajouter.

### Payload vers VPS et conformité NFR-C3/NFR-S5

`VpsMistralEngine.generate(prompt: String)` reçoit le prompt déjà construit par `CoachingResolver.buildPrompt()`. Ce prompt est conforme NFR-C3 (pas de licence FFT) et NFR-S5 (profil générique). L'app envoie `{ "prompt": "<prompt complet>" }`.

Le VPS n'a pas besoin de reconstruire le prompt — il passe directement le `prompt` reçu à `mistral-small-latest` via un message `role: user`.

```python
# mistral_client.py — format message Mistral
messages = [{"role": "user", "content": prompt}]
```

### VPS — Format appel API Mistral

```python
# POST https://api.mistral.ai/v1/chat/completions
{
    "model": "mistral-small-latest",
    "messages": [{"role": "user", "content": prompt}],
    "max_tokens": 200,
    "temperature": 0.7
}
# Réponse : choices[0].message.content
```

Headers Mistral : `Authorization: Bearer {MISTRAL_API_KEY}`, `Content-Type: application/json`.

### VPS — Retry sur timeout uniquement

```python
for attempt in range(2):  # 0 = premier essai, 1 = retry
    try:
        response = await client.post(url, ...)
        response.raise_for_status()
        return response.json()["choices"][0]["message"]["content"]
    except httpx.TimeoutException:
        if attempt == 1:
            raise SecondServeException("MISTRAL_UNAVAILABLE", "Mistral timeout after retry", 503)
        continue
    except httpx.HTTPStatusError as e:
        raise SecondServeException("MISTRAL_ERROR", f"Mistral API error: {e.response.status_code}", 503)
```

### `VpsApiService.kt` — Endpoint à ajouter (obligatoire)

L'endpoint `/api/v1/coaching/analyze` n'est **pas encore** dans `VpsApiService.kt`. Il devra être ajouté dans une story ultérieure (5.2) quand d'autres features Android appelleront directement ce service. Pour **cette story 5.1**, `VpsMistralEngine` utilise OkHttp brut — pas Retrofit/VpsApiService. Ne pas modifier `VpsApiService.kt` dans cette story.

### Tests Android — OkHttp MockWebServer

```kotlin
// build.gradle.kts (:core:ai) — ajouter en testImplementation
testImplementation(libs.okhttp.mockwebserver)

// VpsMistralEngineTest.kt
class VpsMistralEngineTest {
    private val server = MockWebServer()
    
    @Test fun `generate success returns AppResult Success`() { ... }
    @Test fun `generate 500 returns NETWORK_UNAVAILABLE`() { ... }
    @Test fun `generate timeout returns NETWORK_UNAVAILABLE`() { 
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        ... 
    }
}
```

Vérifier si `libs.okhttp.mockwebserver` est déjà dans `libs.versions.toml`. Si non, ajouter `mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }`.

### VPS — `MISTRAL_API_KEY` absente en tests

En CI, `MISTRAL_API_KEY` sera vide (`""`). Les tests d'intégration doivent mocker `mistral_client.generate()` pour ne pas faire d'appels réels. Utiliser `pytest-mock` / `unittest.mock.patch`.

### Project Structure Notes

**Fichiers NOUVEAUX :**
```
android/core/ai/src/main/kotlin/com/secondserve/core/ai/
  ├── di/
  │   └── InferenceEngineQualifiers.kt       (T2.1)
  └── vps/
      └── VpsMistralEngine.kt                (T3.x)
android/core/ai/src/test/kotlin/com/secondserve/core/ai/
  └── vps/
      └── VpsMistralEngineTest.kt            (T12.1)

backend/app/features/coaching/
  ├── mistral_client.py                      (T8.x)  — était vide
  ├── schemas.py                             (T9.1)  — était vide
  └── service.py                             (T10.1) — était vide
backend/tests/
  ├── unit/test_coaching_service.py          (T12.2)
  └── integration/test_coaching_api.py       (T12.3)
```

**Fichiers MODIFIÉS :**
```
android/core/ai/build.gradle.kts            (T1.1) — ajouter OkHttp + Moshi
android/app/src/release/kotlin/.../di/AiModule.kt  (T4.x) — qualifiers
android/app/src/debug/kotlin/.../di/AiModule.kt    (T5.1) — qualifiers
android/feature/match/src/main/kotlin/.../CoachingResolver.kt  (T6.x)
android/feature/match/src/test/kotlin/.../CoachingResolverTest.kt  (adapter constructeur 2 params)
backend/pyproject.toml                      (T7.1) — ajouter httpx
backend/app/api/v1/coaching.py             (T11.x) — était vide
```

### References

- Interface `InferenceEngine` : `android/core/ai/src/main/kotlin/com/secondserve/core/ai/InferenceEngine.kt`
- `CoachingResolver` existant (chaîne actuelle Gemini → Cache → Static) : `android/feature/match/src/main/kotlin/com/secondserve/feature/match/CoachingResolver.kt`
- `AiModule` release (binding actuel sans qualifier) : `android/app/src/release/kotlin/com/secondserve/di/AiModule.kt`
- `AiModule` debug (MockInferenceEngine) : `android/app/src/debug/kotlin/com/secondserve/di/AiModule.kt`
- `AuthModule` (OkHttpClient + Moshi disponibles via SingletonComponent) : `android/app/src/main/kotlin/com/secondserve/di/AuthModule.kt`
- `ErrorCode.NETWORK_UNAVAILABLE` : `android/domain/src/main/kotlin/com/secondserve/domain/model/ErrorCode.kt`
- `InferenceEngineException` : `android/domain/src/main/kotlin/com/secondserve/domain/model/InferenceEngineException.kt`
- `SecondServeException` (VPS) : `backend/app/shared/exceptions.py`
- `config.py` (mistral_api_key déjà présent) : `backend/app/core/config.py`
- Architecture — ARCH-8 InferenceEngine, VPS Mistral routing : `_bmad-output/planning-artifacts/architecture.md#:core:ai`
- Épics — Story 5.1, NFR-P4, NFR-C3, NFR-S5 : `_bmad-output/planning-artifacts/epics.md#Story 5.1`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

- Toutes les tâches T1–T12 implémentées et validées (2026-06-23)
- T4.3 : Moshi injecté directement depuis AuthModule (SingletonComponent) — pas de doublon créé dans AiModule
- `CoachingCachePrefetcher` conserve le binding InferenceEngine sans qualifier (backward compat) ; CoachingResolver utilise désormais @GeminiEngine et @VpsMistralEngine
- Prompt extrait avant les timeouts Gemini/VPS pour permettre la réutilisation sans ré-fetch
- Tests Android : 438 tâches BUILD SUCCESSFUL (core:ai + feature:match + suite globale)
- Tests Python : 87 passed (6 nouveaux + 81 existants sans régression)
- `pytest-mock` ajouté dans pyproject.toml dev group pour les mocks async

### File List

**Nouveaux :**
- android/gradle/libs.versions.toml (ajout okhttp-mockwebserver)
- android/core/ai/src/main/kotlin/com/secondserve/core/ai/di/InferenceEngineQualifiers.kt
- android/core/ai/src/main/kotlin/com/secondserve/core/ai/vps/VpsMistralEngine.kt
- android/core/ai/src/test/kotlin/com/secondserve/core/ai/vps/VpsMistralEngineTest.kt
- backend/tests/unit/test_coaching_service.py
- backend/tests/integration/test_coaching_api.py

**Modifiés :**
- android/core/ai/build.gradle.kts
- android/app/src/release/kotlin/com/secondserve/di/AiModule.kt
- android/app/src/debug/kotlin/com/secondserve/di/AiModule.kt
- android/domain/src/main/kotlin/com/secondserve/domain/model/CoachingResult.kt
- android/feature/match/src/main/kotlin/com/secondserve/feature/match/CoachingResolver.kt
- android/feature/match/src/test/kotlin/com/secondserve/feature/match/CoachingResolverTest.kt
- backend/pyproject.toml
- backend/app/features/coaching/mistral_client.py
- backend/app/features/coaching/schemas.py
- backend/app/features/coaching/service.py
- backend/app/api/v1/coaching.py

## Change Log

- 2026-06-23 : Implémentation complète Story 5.1 — VpsMistralEngine + routing Mistral via VPS (Android + backend)
