# Watch ↔ Phone Coordination Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Coordonner automatiquement le lancement de la montre depuis le téléphone et permettre à la montre de démarrer un match en mode dégradé, avec le bon format transmis dans les deux sens via DataLayer.

**Architecture:** Le téléphone envoie `PATH_START_SESSION` après création d'une session → un `WearDataLayerListener` (WearableListenerService côté montre) reçoit le message et démarre `WearActivity` avec le format en extras. La montre affiche d'abord un `StartMatchScreen` (sélection format + règle 3e set) qui envoie `PATH_START_SESSION_REQUEST` → le téléphone crée une session par défaut, répond avec `PATH_START_SESSION`, et ouvre `MatchScreen` automatiquement. La navigation interne de la montre passe au `ScoreScreen` dès qu'un format est confirmé (via nav args → `ScoreViewModel.savedStateHandle`).

**Tech Stack:** Kotlin, Android Wearable DataLayer (play-services-wearable 18.2.0), Compose Navigation, Hilt, Orbit MVI, Moshi, Timber

## Global Constraints

- `applicationId = "com.secondserve"` dans BOTH `:app` et `:wear` (déjà corrigé en working tree, doit être committé)
- Paths DataLayer dans `DataLayerClient.companion object` — ne pas les déclarer ailleurs
- Pattern fire-and-forget pour tout appel DataLayer : `viewModelScope.launch { }` ou `serviceScope.launch { }`
- `AppResult<T>` du domaine — jamais `kotlin.Result`
- `Timber.d/e` uniquement — jamais `Log.*`
- Tests JVM uniquement (pas d'Android SDK en CI) — les intégrations DataLayer et WearableListenerService ne sont pas testables en JVM
- Tout DTO DataLayer : `@JsonClass(generateAdapter = false)` + `KotlinJsonAdapterFactory` (pas de KSP codegen)
- Format thirdSetRule si BEST_OF_1 : toujours `ThirdSetRule.FULL_ADVANTAGE` (la règle n'a pas de sens en 1 set)

---

## File Map

### Nouveaux fichiers

| Fichier | Responsabilité |
|---|---|
| `data/wearable/dto/StartSessionPayload.kt` | DTO pour `PATH_START_SESSION` (phone→watch) |
| `data/wearable/dto/StartSessionRequestPayload.kt` | DTO pour `PATH_START_SESSION_REQUEST` (watch→phone) |
| `wear/presentation/match/StartMatchScreen.kt` | Écran sélection format sur la montre |
| `wear/WearDataLayerListener.kt` | WearableListenerService côté montre (reçoit phone→watch) |
| `wear/navigation/WearNavGraph.kt` | Navigation interne montre (start_match → score) |

### Fichiers modifiés

| Fichier | Changement |
|---|---|
| `data/wearable/DataLayerClient.kt` | + `PATH_START_SESSION`, `PATH_START_SESSION_REQUEST`, `sendStartSession()`, `sendStartSessionRequest()` |
| `data/wearable/DataLayerListener.kt` | + handler `PATH_START_SESSION_REQUEST`, `SessionRepository` dans EntryPoint |
| `data/di/DataLayerModule.kt` | **Nouveau module** — ou mise à jour EntryPoint dans DataLayerListener |
| `wear/AndroidManifest.xml` | + `WearDataLayerListener` service |
| `wear/WearActivity.kt` | Navigation Compose, `onNewIntent` avec `MutableState<Intent?>` |
| `wear/presentation/match/ScoreScreen.kt` | + `collectSideEffect` pour `ScoreSideEffect.Close` |
| `feature/match/build.gradle.kts` | + `implementation(project(":data"))` |
| `feature/match/NewMatchViewModel.kt` | + inject `DataLayerClient`, appelle `sendStartSession()` après session créée |
| `app/MainActivity.kt` | + `onNewIntent`, `pendingSessionId: MutableState<Long?>` |
| `app/navigation/AppNavGraph.kt` | + `LaunchedEffect` sur `pendingSessionId` → navigue vers `match/{sessionId}` |
| `wear/build.gradle.kts` | + `wear.navigation.compose` dependency |

---

## Task 1 — DTOs `StartSessionPayload` et `StartSessionRequestPayload`

**Files:**
- Create: `android/data/src/main/kotlin/com/secondserve/data/wearable/dto/StartSessionPayload.kt`
- Create: `android/data/src/main/kotlin/com/secondserve/data/wearable/dto/StartSessionRequestPayload.kt`
- Test: `android/data/src/test/kotlin/com/secondserve/data/wearable/dto/StartSessionPayloadTest.kt`

**Interfaces:**
- Consumes: rien (DTO pur)
- Produces:
  - `StartSessionPayload(type, ts, sessionId, matchFormat, thirdSetRule)` — sérialisé dans `PATH_START_SESSION`
  - `StartSessionRequestPayload(type, ts, matchFormat, thirdSetRule)` — sérialisé dans `PATH_START_SESSION_REQUEST`

- [ ] **Step 1: Écrire le test qui échoue**

```kotlin
// android/data/src/test/kotlin/com/secondserve/data/wearable/dto/StartSessionPayloadTest.kt
package com.secondserve.data.wearable.dto

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StartSessionPayloadTest {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @Test
    fun `StartSessionPayload serializes with correct type field`() {
        val payload = StartSessionPayload(
            ts = 1000L, sessionId = 42L,
            matchFormat = "BEST_OF_3", thirdSetRule = "FULL_ADVANTAGE"
        )
        val json = moshi.adapter(StartSessionPayload::class.java).toJson(payload)
        assertTrue(json.contains("\"type\":\"START_SESSION\""))
        assertTrue(json.contains("\"sessionId\":42"))
        assertTrue(json.contains("\"matchFormat\":\"BEST_OF_3\""))
        assertTrue(json.contains("\"thirdSetRule\":\"FULL_ADVANTAGE\""))
    }

    @Test
    fun `StartSessionPayload round-trips through Moshi`() {
        val original = StartSessionPayload(
            ts = 9999L, sessionId = 7L,
            matchFormat = "BEST_OF_1", thirdSetRule = "FULL_ADVANTAGE"
        )
        val json = moshi.adapter(StartSessionPayload::class.java).toJson(original)
        val restored = moshi.adapter(StartSessionPayload::class.java).fromJson(json)!!
        assertEquals(original.sessionId, restored.sessionId)
        assertEquals(original.matchFormat, restored.matchFormat)
        assertEquals(original.type, restored.type)
    }

    @Test
    fun `StartSessionRequestPayload serializes with correct type field`() {
        val payload = StartSessionRequestPayload(
            ts = 1000L, matchFormat = "BEST_OF_3", thirdSetRule = "SUPER_TIE_BREAK_10"
        )
        val json = moshi.adapter(StartSessionRequestPayload::class.java).toJson(payload)
        assertTrue(json.contains("\"type\":\"START_SESSION_REQUEST\""))
        assertTrue(json.contains("\"thirdSetRule\":\"SUPER_TIE_BREAK_10\""))
    }
}
```

- [ ] **Step 2: Run le test pour vérifier qu'il échoue**

```bash
cd /root/SecondServe/android && rtk gradle :data:test --tests "*.StartSessionPayloadTest"
```
Expected: FAIL — classes non trouvées

- [ ] **Step 3: Créer `StartSessionPayload.kt`**

```kotlin
// android/data/src/main/kotlin/com/secondserve/data/wearable/dto/StartSessionPayload.kt
package com.secondserve.data.wearable.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class StartSessionPayload(
    @Json(name = "type") val type: String = "START_SESSION",
    @Json(name = "ts") val ts: Long,
    @Json(name = "sessionId") val sessionId: Long,
    @Json(name = "matchFormat") val matchFormat: String,
    @Json(name = "thirdSetRule") val thirdSetRule: String
)
```

- [ ] **Step 4: Créer `StartSessionRequestPayload.kt`**

```kotlin
// android/data/src/main/kotlin/com/secondserve/data/wearable/dto/StartSessionRequestPayload.kt
package com.secondserve.data.wearable.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class StartSessionRequestPayload(
    @Json(name = "type") val type: String = "START_SESSION_REQUEST",
    @Json(name = "ts") val ts: Long,
    @Json(name = "matchFormat") val matchFormat: String,
    @Json(name = "thirdSetRule") val thirdSetRule: String
)
```

- [ ] **Step 5: Run le test**

```bash
cd /root/SecondServe/android && rtk gradle :data:test --tests "*.StartSessionPayloadTest"
```
Expected: PASS (3 tests verts)

- [ ] **Step 6: Commit**

```bash
rtk git add android/data/src/main/kotlin/com/secondserve/data/wearable/dto/StartSessionPayload.kt android/data/src/main/kotlin/com/secondserve/data/wearable/dto/StartSessionRequestPayload.kt android/data/src/test/kotlin/com/secondserve/data/wearable/dto/StartSessionPayloadTest.kt
rtk git commit -m "feat(data): DTOs StartSessionPayload et StartSessionRequestPayload pour coordination watch-phone"
```

---

## Task 2 — `DataLayerClient` : nouveaux paths et méthodes

**Files:**
- Modify: `android/data/src/main/kotlin/com/secondserve/data/wearable/DataLayerClient.kt`
- Test: `android/data/src/test/kotlin/com/secondserve/data/wearable/DataLayerClientConstantsTest.kt`

**Interfaces:**
- Consumes: `StartSessionPayload`, `StartSessionRequestPayload` (Task 1)
- Produces:
  - `DataLayerClient.PATH_START_SESSION: String = "/secondserve/start_session"`
  - `DataLayerClient.PATH_START_SESSION_REQUEST: String = "/secondserve/start_session_request"`
  - `DataLayerClient.sendStartSession(sessionId: Long, matchFormat: MatchFormat, thirdSetRule: ThirdSetRule): AppResult<Unit>`
  - `DataLayerClient.sendStartSessionRequest(matchFormat: MatchFormat, thirdSetRule: ThirdSetRule): AppResult<Unit>`

- [ ] **Step 1: Écrire le test des constantes**

```kotlin
// android/data/src/test/kotlin/com/secondserve/data/wearable/DataLayerClientConstantsTest.kt
package com.secondserve.data.wearable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DataLayerClientConstantsTest {
    @Test
    fun `PATH_START_SESSION has correct value`() {
        assertEquals("/secondserve/start_session", DataLayerClient.PATH_START_SESSION)
    }

    @Test
    fun `PATH_START_SESSION_REQUEST has correct value`() {
        assertEquals("/secondserve/start_session_request", DataLayerClient.PATH_START_SESSION_REQUEST)
    }
}
```

- [ ] **Step 2: Run le test pour vérifier qu'il échoue**

```bash
cd /root/SecondServe/android && rtk gradle :data:test --tests "*.DataLayerClientConstantsTest"
```
Expected: FAIL

- [ ] **Step 3: Modifier `DataLayerClient.kt`**

Ajouter dans `companion object` et deux nouvelles méthodes publiques :

```kotlin
// Dans companion object — après les constantes existantes :
const val PATH_START_SESSION = "/secondserve/start_session"
const val PATH_START_SESSION_REQUEST = "/secondserve/start_session_request"
```

```kotlin
// Méthodes publiques à ajouter après sendCloseRequest() :

suspend fun sendStartSession(
    sessionId: Long,
    matchFormat: MatchFormat,
    thirdSetRule: ThirdSetRule
): AppResult<Unit> {
    val payload = StartSessionPayload(
        ts = System.currentTimeMillis(),
        sessionId = sessionId,
        matchFormat = matchFormat.name,
        thirdSetRule = thirdSetRule.name
    )
    val json = moshi.adapter(StartSessionPayload::class.java).toJson(payload)
    return sendMessage(PATH_START_SESSION, json.toByteArray(Charsets.UTF_8))
}

suspend fun sendStartSessionRequest(
    matchFormat: MatchFormat,
    thirdSetRule: ThirdSetRule
): AppResult<Unit> {
    val payload = StartSessionRequestPayload(
        ts = System.currentTimeMillis(),
        matchFormat = matchFormat.name,
        thirdSetRule = thirdSetRule.name
    )
    val json = moshi.adapter(StartSessionRequestPayload::class.java).toJson(payload)
    return sendMessage(PATH_START_SESSION_REQUEST, json.toByteArray(Charsets.UTF_8))
}
```

Ajouter les imports nécessaires en haut du fichier :
```kotlin
import com.secondserve.data.wearable.dto.StartSessionPayload
import com.secondserve.data.wearable.dto.StartSessionRequestPayload
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.ThirdSetRule
```

- [ ] **Step 4: Run le test**

```bash
cd /root/SecondServe/android && rtk gradle :data:test --tests "*.DataLayerClientConstantsTest"
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
rtk git add android/data/src/main/kotlin/com/secondserve/data/wearable/DataLayerClient.kt android/data/src/test/kotlin/com/secondserve/data/wearable/DataLayerClientConstantsTest.kt
rtk git commit -m "feat(data): DataLayerClient — sendStartSession et sendStartSessionRequest"
```

---

## Task 3 — `DataLayerEventBus` : événement `startSessionRequests`

**Files:**
- Modify: `android/domain/src/main/kotlin/com/secondserve/domain/event/DataLayerEventBus.kt`
- Test: `android/domain/src/test/kotlin/com/secondserve/domain/event/DataLayerEventBusTest.kt`

**Interfaces:**
- Consumes: rien
- Produces:
  - `DataLayerEventBus.startSessionRequests: SharedFlow<Long>` (sessionId)
  - `DataLayerEventBus.emitStartSession(sessionId: Long)`

- [ ] **Step 1: Écrire le test**

```kotlin
// android/domain/src/test/kotlin/com/secondserve/domain/event/DataLayerEventBusTest.kt
package com.secondserve.domain.event

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DataLayerEventBusTest {

    @Test
    fun `emitStartSession emits sessionId on startSessionRequests`() = runTest {
        val bus = DataLayerEventBus()
        var received: Long? = null
        val job = launch { received = bus.startSessionRequests.first() }
        bus.emitStartSession(42L)
        job.join()
        assertEquals(42L, received)
    }
}
```

- [ ] **Step 2: Run le test pour vérifier qu'il échoue**

```bash
cd /root/SecondServe/android && rtk gradle :domain:test --tests "*.DataLayerEventBusTest"
```

- [ ] **Step 3: Modifier `DataLayerEventBus.kt`**

Ajouter après les déclarations existantes (closeSessionRequests et gameOverEvents) :

```kotlin
private val _startSessionRequests = MutableSharedFlow<Long>(extraBufferCapacity = 1)
val startSessionRequests: SharedFlow<Long> = _startSessionRequests

fun emitStartSession(sessionId: Long) {
    _startSessionRequests.tryEmit(sessionId)
}
```

- [ ] **Step 4: Run le test**

```bash
cd /root/SecondServe/android && rtk gradle :domain:test --tests "*.DataLayerEventBusTest"
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
rtk git add android/domain/src/main/kotlin/com/secondserve/domain/event/DataLayerEventBus.kt android/domain/src/test/kotlin/com/secondserve/domain/event/DataLayerEventBusTest.kt
rtk git commit -m "feat(domain): DataLayerEventBus — startSessionRequests pour navigation téléphone depuis montre"
```

---

## Task 4 — `DataLayerListener` côté téléphone : gestion `PATH_START_SESSION_REQUEST`

**Files:**
- Modify: `android/data/src/main/kotlin/com/secondserve/data/wearable/DataLayerListener.kt`

**Interfaces:**
- Consumes:
  - `DataLayerClient.PATH_START_SESSION_REQUEST` (Task 2)
  - `DataLayerEventBus.emitStartSession(sessionId)` (Task 3)
  - `StartSessionRequestPayload` (Task 1)
  - `DataLayerClient.sendStartSession()` (Task 2)
  - `SessionRepository.createSession(session: Session): AppResult<Session>` — interface domaine existante
- Produces: démarre `MainActivity` avec `ACTION_OPEN_MATCH` + sessionId, renvoie `PATH_START_SESSION` à la montre

> ⚠️ **Note sur les tests** : `DataLayerListener` est un `WearableListenerService` non testable en JVM. Validation par inspection.

- [ ] **Step 1: Ajouter `SessionRepository` dans `DataLayerListenerEntryPoint`**

Dans `DataLayerListener.kt`, modifier l'interface `DataLayerListenerEntryPoint` :

```kotlin
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DataLayerListenerEntryPoint {
    fun scoreRepository(): ScoreRepository
    fun dataLayerEventBus(): DataLayerEventBus
    fun sessionRepository(): SessionRepository          // NOUVEAU
    fun dataLayerClient(): DataLayerClient              // NOUVEAU
}
```

- [ ] **Step 2: Ajouter le champ lazy pour les nouvelles dépendances**

Après la déclaration de `dataLayerEventBus`, ajouter :

```kotlin
private val sessionRepository: SessionRepository by lazy {
    EntryPointAccessors.fromApplication(
        applicationContext,
        DataLayerListenerEntryPoint::class.java
    ).sessionRepository()
}

private val dataLayerClient: DataLayerClient by lazy {
    EntryPointAccessors.fromApplication(
        applicationContext,
        DataLayerListenerEntryPoint::class.java
    ).dataLayerClient()
}
```

- [ ] **Step 3: Enregistrer le handler dans `onMessageReceived`**

Dans le `when` de `onMessageReceived`, ajouter avant le `else` :

```kotlin
DataLayerClient.PATH_START_SESSION_REQUEST -> handleStartSessionRequest(json)
```

- [ ] **Step 4: Implémenter `handleStartSessionRequest`**

Ajouter la méthode privée à la fin de la classe :

```kotlin
private fun handleStartSessionRequest(json: String) {
    try {
        val payload = moshi.adapter(StartSessionRequestPayload::class.java).fromJson(json)
        if (payload == null) {
            Timber.e("DataLayerListener: null StartSessionRequestPayload from JSON")
            return
        }
        val matchFormat = runCatching { MatchFormat.valueOf(payload.matchFormat) }
            .getOrElse { Timber.e("DataLayerListener: unknown matchFormat %s", payload.matchFormat); return }
        val thirdSetRule = runCatching { ThirdSetRule.valueOf(payload.thirdSetRule) }
            .getOrElse { ThirdSetRule.FULL_ADVANTAGE }

        serviceScope.launch {
            val session = Session(
                surface = "",
                format = SessionFormat(matchFormat = matchFormat, thirdSetRule = thirdSetRule),
                status = SessionStatus.ACTIVE,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val result = sessionRepository.createSession(session)
            if (result is AppResult.Error) {
                Timber.e(result.exception, "DataLayerListener: failed to create session from watch request")
                return@launch
            }
            val createdSession = (result as AppResult.Success).data
            val sessionId = createdSession.id

            dataLayerEventBus.emitStartSession(sessionId)

            dataLayerClient.sendStartSession(sessionId, matchFormat, thirdSetRule)
                .also { if (it is AppResult.Error) Timber.d("DataLayerListener: sendStartSession to watch failed") }

            val intent = applicationContext.packageManager
                .getLaunchIntentForPackage(applicationContext.packageName)
                ?.apply {
                    action = "com.secondserve.ACTION_OPEN_MATCH"
                    putExtra("sessionId", sessionId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            if (intent != null) applicationContext.startActivity(intent)
            Timber.d("DataLayerListener: session %d created from watch request, phone notified", sessionId)
        }
    } catch (e: Exception) {
        Timber.e(e, "DataLayerListener: failed to handle start_session_request")
    }
}
```

- [ ] **Step 5: Ajouter les imports manquants**

```kotlin
import android.content.Intent
import com.secondserve.data.wearable.dto.StartSessionRequestPayload
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.Session
import com.secondserve.domain.model.SessionFormat
import com.secondserve.domain.model.SessionStatus
import com.secondserve.domain.model.ThirdSetRule
import com.secondserve.domain.repository.SessionRepository
```

- [ ] **Step 6: Vérifier que le binding `DataLayerClient` est accessible depuis le `SingletonComponent`**

`DataLayerClient` est `@Singleton` avec `@Inject constructor` — Hilt le fournit automatiquement via son `SingletonComponent`. Aucun module supplémentaire requis.

- [ ] **Step 7: Commit**

```bash
rtk git add android/data/src/main/kotlin/com/secondserve/data/wearable/DataLayerListener.kt
rtk git commit -m "feat(data): DataLayerListener — gestion PATH_START_SESSION_REQUEST depuis montre"
```

---

## Task 5 — `NewMatchViewModel` : envoi `PATH_START_SESSION` à la montre

**Files:**
- Modify: `android/feature/match/build.gradle.kts`
- Modify: `android/feature/match/src/main/kotlin/com/secondserve/feature/match/NewMatchViewModel.kt`
- Modify: `android/feature/match/src/test/kotlin/com/secondserve/feature/match/NewMatchViewModelTest.kt` (si le fichier existe)

**Interfaces:**
- Consumes: `DataLayerClient.sendStartSession(sessionId, matchFormat, thirdSetRule)` (Task 2)
- Produces: après `SessionStarted`, la montre reçoit `PATH_START_SESSION` avec le bon format

- [ ] **Step 1: Ajouter la dépendance `:data` dans `feature/match/build.gradle.kts`**

Dans le bloc `dependencies {}`, ajouter :

```kotlin
implementation(project(":data"))
```

- [ ] **Step 2: Injecter `DataLayerClient` dans `NewMatchViewModel`**

Modifier le constructeur :

```kotlin
@HiltViewModel
class NewMatchViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val notificationScheduler: NotificationScheduler,
    private val dataLayerClient: DataLayerClient          // NOUVEAU
) : ViewModel(), ContainerHost<NewMatchUiState, NewMatchSideEffect> {
```

Ajouter l'import :
```kotlin
import com.secondserve.data.wearable.DataLayerClient
```

- [ ] **Step 3: Envoyer `sendStartSession` après création réussie**

Dans `startMatch()`, dans le bloc `is AppResult.Success ->`, après `reduce { state.copy(isLoading = false) }` et AVANT `postSideEffect(NewMatchSideEffect.SessionStarted(...))` :

```kotlin
is AppResult.Success -> {
    reduce { state.copy(isLoading = false) }
    val createdSession = result.data
    // Notifier la montre en fire-and-forget
    viewModelScope.launch {
        dataLayerClient.sendStartSession(
            sessionId = createdSession.id,
            matchFormat = matchFormat,
            thirdSetRule = thirdSetRule
        ).also { r ->
            if (r is AppResult.Error)
                Timber.d("NewMatchViewModel: sendStartSession to watch failed — %s", r.exception.message)
        }
    }
    val createdScheduledAt = createdSession.scheduledAt
    // ... reste du code existant inchangé
```

- [ ] **Step 4: Commit**

```bash
rtk git add android/feature/match/build.gradle.kts android/feature/match/src/main/kotlin/com/secondserve/feature/match/NewMatchViewModel.kt
rtk git commit -m "feat(match): NewMatchViewModel notifie la montre au démarrage du match"
```

---

## Task 6 — `MainActivity` : `onNewIntent` + deep link vers `MatchScreen`

**Files:**
- Modify: `android/app/src/main/kotlin/com/secondserve/MainActivity.kt`
- Modify: `android/app/src/main/kotlin/com/secondserve/navigation/AppNavGraph.kt`

**Interfaces:**
- Consumes: `DataLayerEventBus.startSessionRequests: SharedFlow<Long>` (Task 3)
- Produces:
  - `MainActivity` expose `pendingSessionId: State<Long?>` passé à `AppNavGraph`
  - `AppNavGraph` reçoit `pendingSessionId` et navigue vers `match/{sessionId}` via `LaunchedEffect`

- [ ] **Step 1: Modifier `MainActivity` pour gérer `onNewIntent`**

Ajouter dans `MainActivity` une propriété et un override :

```kotlin
// Après les @Inject :
@Inject lateinit var dataLayerEventBus: DataLayerEventBus

// Avant onCreate :
private val _pendingSessionId = mutableStateOf<Long?>(null)
```

Dans `onCreate`, modifier `AppNavGraph()` → `AppNavGraph(pendingSessionId = _pendingSessionId)`.

Dans `setContent { SecondServeTheme { ... } }`, à l'intérieur du bloc `AuthState.Authenticated ->`, remplacer `AppNavGraph()` par :

```kotlin
AuthState.Authenticated -> {
    LaunchedEffect(Unit) {
        dataLayerEventBus.startSessionRequests.collect { sessionId ->
            _pendingSessionId.value = sessionId
        }
    }
    AppNavGraph(pendingSessionId = _pendingSessionId)
}
```

Ajouter le override :

```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    if (intent.action == "com.secondserve.ACTION_OPEN_MATCH") {
        val sessionId = intent.getLongExtra("sessionId", -1L)
        if (sessionId != -1L) _pendingSessionId.value = sessionId
    }
}
```

Ajouter les imports :
```kotlin
import android.content.Intent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.secondserve.domain.event.DataLayerEventBus
```

- [ ] **Step 2: Modifier `AppNavGraph` pour accepter et consommer `pendingSessionId`**

Modifier la signature :
```kotlin
@Composable
fun AppNavGraph(pendingSessionId: State<Long?> = mutableStateOf(null))
```

À l'intérieur du bloc `NavHost`, juste après la déclaration du `NavHost`, ajouter :
```kotlin
LaunchedEffect(pendingSessionId.value) {
    val sessionId = pendingSessionId.value ?: return@LaunchedEffect
    navController.navigate("match/$sessionId") {
        popUpTo("home") { saveState = false }
    }
    (pendingSessionId as? androidx.compose.runtime.MutableState)?.value = null
}
```

Ajouter les imports :
```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
```

- [ ] **Step 3: Ajouter `android:launchMode="singleTop"` à `MainActivity` dans le manifest**

Dans `app/src/main/AndroidManifest.xml`, modifier :
```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:launchMode="singleTop">
```

Sans `singleTop`, `FLAG_ACTIVITY_SINGLE_TOP` n'est pas respecté et une nouvelle instance est créée.

- [ ] **Step 4: Commit**

```bash
rtk git add android/app/src/main/kotlin/com/secondserve/MainActivity.kt android/app/src/main/kotlin/com/secondserve/navigation/AppNavGraph.kt android/app/src/main/AndroidManifest.xml
rtk git commit -m "feat(app): MainActivity gère onNewIntent depuis montre et navigue vers MatchScreen"
```

---

## Task 7 — Navigation côté montre + `WearDataLayerListener`

**Files:**
- Create: `android/wear/src/main/kotlin/com/secondserve/wear/navigation/WearNavGraph.kt`
- Create: `android/wear/src/main/kotlin/com/secondserve/wear/WearDataLayerListener.kt`
- Modify: `android/wear/src/main/AndroidManifest.xml`
- Modify: `android/wear/src/main/kotlin/com/secondserve/wear/WearActivity.kt`
- Modify: `android/wear/build.gradle.kts`

**Interfaces:**
- Consumes: `DataLayerClient.PATH_START_SESSION` (Task 2), `StartSessionPayload` (Task 1)
- Produces:
  - `WearNavGraph(pendingStartIntent: State<Intent?>)` — nav host montre avec routes `start_match` et `score/{matchFormat}/{thirdSetRule}`
  - `WearDataLayerListener` reçoit `PATH_START_SESSION`, démarre `WearActivity` avec extras

> ⚠️ `WearDataLayerListener` n'est pas testable en JVM — validation par inspection du code.

- [ ] **Step 1: Ajouter `wear.navigation.compose` dans `wear/build.gradle.kts`**

Dans `dependencies {}` :
```kotlin
implementation(libs.wear.compose.navigation)
```

(`libs.wear.compose.navigation` est déjà déclaré dans `libs.versions.toml` avec l'alias existant — vérifier que l'alias existe sinon ajouter `wearComposeNavigation = { group = "androidx.wear.compose", name = "compose-navigation", version.ref = "wearCompose" }`)

- [ ] **Step 2: Créer `WearNavGraph.kt`**

```kotlin
// android/wear/src/main/kotlin/com/secondserve/wear/navigation/WearNavGraph.kt
package com.secondserve.wear.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.secondserve.wear.presentation.match.ScoreScreen
import com.secondserve.wear.presentation.match.StartMatchScreen

@Composable
fun WearNavGraph(pendingStartIntent: State<Intent?> = mutableStateOf(null)) {
    val navController = rememberSwipeDismissableNavController()

    LaunchedEffect(pendingStartIntent.value) {
        val intent = pendingStartIntent.value ?: return@LaunchedEffect
        val format = intent.getStringExtra("matchFormat") ?: return@LaunchedEffect
        val rule = intent.getStringExtra("thirdSetRule") ?: "FULL_ADVANTAGE"
        navController.navigate("score/$format/$rule") {
            popUpTo("start_match") { inclusive = true }
        }
        (pendingStartIntent as? MutableState)?.value = null
    }

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = "start_match"
    ) {
        composable("start_match") {
            StartMatchScreen(
                onSessionStarted = { matchFormat, thirdSetRule ->
                    navController.navigate("score/${matchFormat.name}/${thirdSetRule.name}") {
                        popUpTo("start_match") { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = "score/{matchFormat}/{thirdSetRule}",
            arguments = listOf(
                navArgument("matchFormat") { type = NavType.StringType },
                navArgument("thirdSetRule") { type = NavType.StringType }
            )
        ) {
            ScoreScreen(
                onClose = { navController.navigate("start_match") {
                    popUpTo("start_match") { inclusive = true }
                }}
            )
        }
    }
}
```

- [ ] **Step 3: Créer `WearDataLayerListener.kt`**

```kotlin
// android/wear/src/main/kotlin/com/secondserve/wear/WearDataLayerListener.kt
package com.secondserve.wear

import android.content.Intent
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.secondserve.data.wearable.DataLayerClient
import com.secondserve.data.wearable.dto.StartSessionPayload
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import timber.log.Timber

class WearDataLayerListener : WearableListenerService() {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val json = messageEvent.data.toString(Charsets.UTF_8)
        Timber.d("WearDataLayerListener: received path=%s", messageEvent.path)

        when (messageEvent.path) {
            DataLayerClient.PATH_START_SESSION -> handleStartSession(json)
            else -> Timber.d("WearDataLayerListener: unknown path=%s, ignoring", messageEvent.path)
        }
    }

    private fun handleStartSession(json: String) {
        try {
            val payload = moshi.adapter(StartSessionPayload::class.java).fromJson(json)
            if (payload == null) {
                Timber.e("WearDataLayerListener: null StartSessionPayload")
                return
            }
            val intent = Intent(applicationContext, WearActivity::class.java).apply {
                putExtra("matchFormat", payload.matchFormat)
                putExtra("thirdSetRule", payload.thirdSetRule)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            applicationContext.startActivity(intent)
            Timber.d("WearDataLayerListener: launching WearActivity with format=%s", payload.matchFormat)
        } catch (e: Exception) {
            Timber.e(e, "WearDataLayerListener: failed to handle start_session")
        }
    }
}
```

- [ ] **Step 4: Mettre à jour `WearActivity.kt`**

```kotlin
// android/wear/src/main/kotlin/com/secondserve/wear/WearActivity.kt
package com.secondserve.wear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.secondserve.wear.navigation.WearNavGraph
import com.secondserve.wear.presentation.theme.WearTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WearActivity : ComponentActivity() {

    private val pendingStartIntent = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkIncomingIntent(intent)
        setContent {
            WearTheme {
                WearNavGraph(pendingStartIntent = pendingStartIntent)
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
}
```

- [ ] **Step 5: Enregistrer `WearDataLayerListener` dans `wear/AndroidManifest.xml`**

Dans `<application>`, ajouter après `<activity>` :

```xml
<service
    android:name="com.secondserve.wear.WearDataLayerListener"
    android:exported="true"
    android:permission="com.google.android.gms.wearable.BIND_LISTENER">
    <intent-filter>
        <action android:name="com.google.android.gms.wearable.MESSAGE_RECEIVED" />
        <data
            android:scheme="wear"
            android:host="*"
            android:pathPrefix="/secondserve/" />
    </intent-filter>
</service>
```

Et ajouter `android:launchMode="singleTop"` à `<activity>` :

```xml
<activity
    android:name=".WearActivity"
    android:exported="true"
    android:label="@string/app_name"
    android:launchMode="singleTop">
```

- [ ] **Step 6: Commit**

```bash
rtk git add android/wear/src/main/kotlin/com/secondserve/wear/navigation/WearNavGraph.kt android/wear/src/main/kotlin/com/secondserve/wear/WearDataLayerListener.kt android/wear/src/main/kotlin/com/secondserve/wear/WearActivity.kt android/wear/src/main/AndroidManifest.xml android/wear/build.gradle.kts
rtk git commit -m "feat(wear): WearDataLayerListener + WearNavGraph — auto-lancement depuis téléphone"
```

---

## Task 8 — `StartMatchScreen` sur la montre

**Files:**
- Create: `android/wear/src/main/kotlin/com/secondserve/wear/presentation/match/StartMatchScreen.kt`

**Interfaces:**
- Consumes: `DataLayerClient.sendStartSessionRequest(matchFormat, thirdSetRule)` (Task 2)
- Produces: `StartMatchScreen(onSessionStarted: (MatchFormat, ThirdSetRule) -> Unit)` — callback vers `WearNavGraph` quand format confirmé

> ⚠️ Le mode dégradé : si le téléphone n'est pas connecté, `sendStartSessionRequest` retourne `AppResult.Error`. Dans ce cas, la montre navigue quand même vers `ScoreScreen` avec le format local (pas de session créée sur le téléphone, pas de coaching — mais le suivi de score fonctionne).

- [ ] **Step 1: Créer `StartMatchScreen.kt`**

```kotlin
// android/wear/src/main/kotlin/com/secondserve/wear/presentation/match/StartMatchScreen.kt
package com.secondserve.wear.presentation.match

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ChipDefaults
import androidx.wear.compose.material3.CompactChip
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.ThirdSetRule

@Composable
fun StartMatchScreen(
    onSessionStarted: (MatchFormat, ThirdSetRule) -> Unit,
    viewModel: StartMatchViewModel = hiltViewModel()
) {
    var selectedFormat by remember { mutableStateOf(MatchFormat.BEST_OF_3) }
    var selectedRule by remember { mutableStateOf(ThirdSetRule.FULL_ADVANTAGE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Nouveau match",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        // Format
        FormatChip(
            label = "1 set",
            selected = selectedFormat == MatchFormat.BEST_OF_1,
            onClick = { selectedFormat = MatchFormat.BEST_OF_1 }
        )
        FormatChip(
            label = "3 sets",
            selected = selectedFormat == MatchFormat.BEST_OF_3,
            onClick = { selectedFormat = MatchFormat.BEST_OF_3 }
        )

        // Règle 3e set (visible uniquement si 3 sets)
        if (selectedFormat == MatchFormat.BEST_OF_3) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "3e set :",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            FormatChip(
                label = "Avantage",
                selected = selectedRule == ThirdSetRule.FULL_ADVANTAGE,
                onClick = { selectedRule = ThirdSetRule.FULL_ADVANTAGE }
            )
            FormatChip(
                label = "Super TB",
                selected = selectedRule == ThirdSetRule.SUPER_TIE_BREAK_10,
                onClick = { selectedRule = ThirdSetRule.SUPER_TIE_BREAK_10 }
            )
            FormatChip(
                label = "Set court",
                selected = selectedRule == ThirdSetRule.SHORT_DECISIVE_SET,
                onClick = { selectedRule = ThirdSetRule.SHORT_DECISIVE_SET }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                val rule = if (selectedFormat == MatchFormat.BEST_OF_1)
                    ThirdSetRule.FULL_ADVANTAGE else selectedRule
                viewModel.startMatch(selectedFormat, rule, onSessionStarted)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Démarrer", fontSize = 12.sp)
        }
    }
}

@Composable
private fun FormatChip(label: String, selected: Boolean, onClick: () -> Unit) {
    CompactChip(
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp) },
        colors = if (selected)
            ChipDefaults.filledChipColors()
        else
            ChipDefaults.outlinedChipColors(),
        modifier = Modifier.fillMaxWidth()
    )
}
```

- [ ] **Step 2: Créer `StartMatchViewModel.kt`**

```kotlin
// android/wear/src/main/kotlin/com/secondserve/wear/presentation/match/StartMatchViewModel.kt
package com.secondserve.wear.presentation.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondserve.data.wearable.DataLayerClient
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.ThirdSetRule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class StartMatchViewModel @Inject constructor(
    private val dataLayerClient: DataLayerClient
) : ViewModel() {

    fun startMatch(
        matchFormat: MatchFormat,
        thirdSetRule: ThirdSetRule,
        onSessionStarted: (MatchFormat, ThirdSetRule) -> Unit
    ) {
        viewModelScope.launch {
            val result = dataLayerClient.sendStartSessionRequest(matchFormat, thirdSetRule)
            if (result is AppResult.Error) {
                // Mode dégradé : téléphone non connecté, on démarre quand même en local
                Timber.d("StartMatchViewModel: téléphone non disponible, mode dégradé — %s", result.exception.message)
            }
            // Dans tous les cas, on navigue vers ScoreScreen
            onSessionStarted(matchFormat, thirdSetRule)
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
rtk git add android/wear/src/main/kotlin/com/secondserve/wear/presentation/match/StartMatchScreen.kt android/wear/src/main/kotlin/com/secondserve/wear/presentation/match/StartMatchViewModel.kt
rtk git commit -m "feat(wear): StartMatchScreen — sélection format + mode dégradé sans téléphone"
```

---

## Task 9 — `ScoreScreen` : gestion `ScoreSideEffect.Close`

**Files:**
- Modify: `android/wear/src/main/kotlin/com/secondserve/wear/presentation/match/ScoreScreen.kt`

**Interfaces:**
- Consumes: `ScoreSideEffect.Close` (déjà dans `ScoreViewModel`, changement unstaged)
- Produces: `ScoreScreen(onClose: () -> Unit)` — callback vers `WearNavGraph` pour revenir à `StartMatchScreen`

- [ ] **Step 1: Modifier la signature de `ScoreScreen`**

```kotlin
@Composable
fun ScoreScreen(
    onClose: () -> Unit = {},
    viewModel: ScoreViewModel = hiltViewModel()
) {
```

- [ ] **Step 2: Ajouter `collectSideEffect` dans `ScoreScreen`**

Juste après la déclaration de `state` et avant le `when` :

```kotlin
viewModel.collectSideEffect { effect ->
    when (effect) {
        is ScoreSideEffect.Close -> onClose()
    }
}
```

Ajouter l'import :
```kotlin
import org.orbitmvi.orbit.compose.collectSideEffect
```

- [ ] **Step 3: Commit les changements unstaged existants + ce fix**

```bash
rtk git add android/wear/src/main/kotlin/com/secondserve/wear/presentation/match/ScoreScreen.kt android/wear/src/main/kotlin/com/secondserve/wear/presentation/match/ScoreViewModel.kt android/wear/build.gradle.kts android/data/src/main/kotlin/com/secondserve/data/wearable/DataLayerListener.kt
rtk git commit -m "fix(wear): ScoreScreen collecte ScoreSideEffect.Close + applicationId unifié"
```

---

## Task 10 — Commit final : applicationId + validation

> Cette tâche s'assure que tous les changements unstaged du git status initial sont commitués proprement.

- [ ] **Step 1: Vérifier l'état du working tree**

```bash
rtk git status
```

- [ ] **Step 2: Vérifier que `wear/build.gradle.kts` a `applicationId = "com.secondserve"`**

Ouvrir `android/wear/build.gradle.kts` et confirmer :
```kotlin
applicationId = "com.secondserve"
```

- [ ] **Step 3: Build de validation (optionnel — nécessite Android SDK local)**

```bash
cd /root/SecondServe/android && rtk gradle :wear:assembleDebug :app:assembleDebug
```

- [ ] **Step 4: Commit final de nettoyage si nécessaire**

```bash
rtk git status
# Committer tout ce qui reste
```

---

## Checklist de validation fonctionnelle (sur device)

Après installation sur Pixel 9 Pro + Pixel Watch :

- [ ] **Téléphone → Montre** : Démarrer un match sur le téléphone → l'app montre s'ouvre automatiquement sur `ScoreScreen` avec le bon format
- [ ] **Montre → Téléphone** : Ouvrir la montre seule → `StartMatchScreen` → choisir "3 sets / Super TB" → "Démarrer" → le téléphone ouvre `MatchScreen` automatiquement
- [ ] **Mode dégradé** : Même sans téléphone connecté → montre navigue sur `ScoreScreen` et le score est trackable
- [ ] **Score en direct** : Taper un point sur la montre → score mis à jour sur le téléphone (via `score_event`)
- [ ] **Coaching** : Fin d'un jeu avec changement de côté → conseil IA affiché sur le téléphone (via `game_over`)
- [ ] **Fermeture** : Taper "Terminer" sur la montre → montre revient sur `StartMatchScreen`, téléphone reçoit `close_session`
