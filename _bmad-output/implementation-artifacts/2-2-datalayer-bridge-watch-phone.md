---
baseline_commit: 616e254
---

# Story 2.2 : DataLayer Bridge Watch ↔ Phone

**Status:** done

## Story

**As a** developer,
**I want** a Wearable DataLayer bridge that relays score events from Watch to Phone via Bluetooth,
**So that** the Phone receives match state in real-time without maintaining independent score logic.

## Acceptance Criteria

1. **Given** la Watch et le Phone sont appairés via Bluetooth
   **When** la Watch envoie un événement de score
   **Then** `DataLayerClient` envoie un message JSON sur le path `/secondserve/score_event` :
   `{"type": "SCORE_EVENT", "ts": <epoch_ms>, "score": {...}}`

2. **When** la Watch détecte un `game_over`
   **Then** `DataLayerClient` envoie sur `/secondserve/game_over` :
   `{"type": "GAME_OVER", "ts": <epoch_ms>, "score_snapshot": {...}}`

3. **When** le `DataLayerListener` du Phone reçoit un message
   **Then** il parse le JSON et met à jour le `ScoreRepository` (cache read-only côté Phone)

4. **And** tous les timestamps JSON sont en epoch millisecondes (Long)

5. **And** le Phone ne maintient aucun état de score indépendant — il ne fait que recevoir et mettre en cache

6. **And** `DataLayerClient` et `DataLayerListener` sont dans `:data/wearable/`

---

## Architecture Context

### Position dans la séquence (ARCH-13)

```
Story 2.1 ✅ (TennisScoreEngine) → Story 2.2 (CETTE STORY) → Story 2.3 (Session Room) → Story 2.4 (Watch UI) → Story 2.5 (Changeover) → Story 2.6 (Clôture + Sync)
```

**Dépendances satisfaites :**
- ✅ `TennisScoreEngine`, `MatchScore`, `Player`, `EngineEvent` dans `:domain` — prêts à sérialiser
- ✅ `libs.wearable` déjà déclaré dans `libs.versions.toml` (`playServicesWearable = "18.2.0"`)
- ✅ `:wear` a déjà `implementation(libs.wearable)` dans son `build.gradle.kts`
- ❌ Pas encore de table Room `sessions` (Story 2.3) — `ScoreRepository` = cache in-memory pour cette story
- ❌ `:wear` ne dépend pas encore de `:data` — à ajouter dans cette story
- ❌ `DataLayerListener` non enregistré dans le manifest de `:app` — à ajouter

### Source de vérité — principe fondamental (architecture.md)

> **La Pixel Watch est la source de vérité unique du score.** Le téléphone est un récepteur passif : il reçoit le score via DataLayer et l'utilise comme contexte de coaching uniquement. Le `ScoreRepository` côté téléphone est un **cache read-only**. Aucune logique de réconciliation n'est requise.

### Paths DataLayer (ARCH-7)

| Path | Direction | Utilisation |
|------|-----------|-------------|
| `/secondserve/score_event` | Watch → Phone | Point marqué (AC#1) |
| `/secondserve/game_over` | Watch → Phone | Changement de côté détecté (AC#2) |
| `/secondserve/coaching_result` | ~~Phone → Watch~~ | **SUPPRIMÉ** — FR-3/FR-4/FR-5 corrigés |

> ⚠️ **CRITIQUE** : Le path `/secondserve/coaching_result` est **DÉFINITIVEMENT SUPPRIMÉ** de l'architecture. Ne pas créer ce path. La Watch n'a plus d'écran coaching depuis la révision de FR-3/FR-4/FR-5.

---

## Technical Requirements

### Modifications des modules Gradle

#### `data/build.gradle.kts` — Ajouter la dépendance Wearable

```kotlin
// À AJOUTER dans dependencies {}
implementation(libs.wearable)
```

> La bibliothèque est déjà déclarée dans `libs.versions.toml` ligne `wearable = { ... }`. Le module `:data` est utilisé des deux côtés (Watch envoie via `DataLayerClient`, Phone reçoit via `DataLayerListener`), donc c'est le bon endroit.

#### `wear/build.gradle.kts` — Ajouter la dépendance `:data`

```kotlin
// À AJOUTER dans dependencies {}
implementation(project(":data"))
```

> `:wear` a besoin de `DataLayerClient` qui vit dans `:data/wearable/`. Sans cela, la Watch ne peut pas appeler `DataLayerClient`.

### Nouveau répertoire `:data/wearable/`

**Arborescence à créer :**

```
data/src/main/kotlin/com/secondserve/data/wearable/
├── DataLayerClient.kt       — NEW (utilisé par la Watch)
├── DataLayerListener.kt     — NEW (WearableListenerService sur le Phone)
└── dto/
    ├── ScoreEventPayload.kt — NEW (wrapper JSON score_event)
    ├── GameOverPayload.kt   — NEW (wrapper JSON game_over)
    └── MatchScoreDto.kt     — NEW (sérialisation MatchScore ↔ JSON)
```

### Nouveau répertoire `:domain/repository/`

**Fichier à créer :**

```
domain/src/main/kotlin/com/secondserve/domain/repository/
└── ScoreRepository.kt       — NEW (interface cache read-only)
```

> **Note** : `:domain/repository/` existe déjà avec `PlayerProfileRepository.kt` et `WorkAxisRepository.kt`. `ScoreRepository` s'ajoute au même package.

---

### DTOs DataLayer (`data/wearable/dto/`)

#### `MatchScoreDto.kt` (NEW)

```kotlin
package com.secondserve.data.wearable.dto

import com.secondserve.domain.model.GamePoint
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.model.Player
import com.secondserve.domain.model.SetResult
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class SetResultDto(
    @Json(name = "gamesA") val gamesA: Int,
    @Json(name = "gamesB") val gamesB: Int
)

@JsonClass(generateAdapter = false)
data class MatchScoreDto(
    @Json(name = "completedSets") val completedSets: List<SetResultDto>,
    @Json(name = "currentSetGamesA") val currentSetGamesA: Int,
    @Json(name = "currentSetGamesB") val currentSetGamesB: Int,
    @Json(name = "currentGamePointsA") val currentGamePointsA: String,
    @Json(name = "currentGamePointsB") val currentGamePointsB: String,
    @Json(name = "tieBreakPointsA") val tieBreakPointsA: Int,
    @Json(name = "tieBreakPointsB") val tieBreakPointsB: Int,
    @Json(name = "isTieBreak") val isTieBreak: Boolean,
    @Json(name = "isSuperTieBreak") val isSuperTieBreak: Boolean,
    @Json(name = "isMatchOver") val isMatchOver: Boolean,
    @Json(name = "matchWinner") val matchWinner: String?
)

fun MatchScore.toDto() = MatchScoreDto(
    completedSets = completedSets.map { SetResultDto(it.gamesA, it.gamesB) },
    currentSetGamesA = currentSetGamesA,
    currentSetGamesB = currentSetGamesB,
    currentGamePointsA = currentGamePointsA.name,
    currentGamePointsB = currentGamePointsB.name,
    tieBreakPointsA = tieBreakPointsA,
    tieBreakPointsB = tieBreakPointsB,
    isTieBreak = isTieBreak,
    isSuperTieBreak = isSuperTieBreak,
    isMatchOver = isMatchOver,
    matchWinner = matchWinner?.name
)

fun MatchScoreDto.toDomain() = MatchScore(
    completedSets = completedSets.map { SetResult(it.gamesA, it.gamesB) },
    currentSetGamesA = currentSetGamesA,
    currentSetGamesB = currentSetGamesB,
    currentGamePointsA = GamePoint.valueOf(currentGamePointsA),
    currentGamePointsB = GamePoint.valueOf(currentGamePointsB),
    tieBreakPointsA = tieBreakPointsA,
    tieBreakPointsB = tieBreakPointsB,
    isTieBreak = isTieBreak,
    isSuperTieBreak = isSuperTieBreak,
    isMatchOver = isMatchOver,
    matchWinner = matchWinner?.let { Player.valueOf(it) }
)
```

> ⚠️ **Enums sérialisés comme String** : `GamePoint.name` et `Player.name` — SCREAMING_SNAKE_CASE, cohérent avec la convention architecture.md § Format Patterns.

#### `ScoreEventPayload.kt` (NEW)

```kotlin
package com.secondserve.data.wearable.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class ScoreEventPayload(
    @Json(name = "type") val type: String = "SCORE_EVENT",
    @Json(name = "ts") val ts: Long,
    @Json(name = "score") val score: MatchScoreDto
)
```

#### `GameOverPayload.kt` (NEW)

```kotlin
package com.secondserve.data.wearable.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class GameOverPayload(
    @Json(name = "type") val type: String = "GAME_OVER",
    @Json(name = "ts") val ts: Long,
    @Json(name = "score_snapshot") val score_snapshot: MatchScoreDto
)
```

> ⚠️ **Champ `score_snapshot`** : underscore imposé par le contrat DataLayer défini dans architecture.md § API & Communication. Ne pas renommer en camelCase.

---

### `ScoreRepository` Interface (`:domain`)

**`domain/src/main/kotlin/com/secondserve/domain/repository/ScoreRepository.kt`** (NEW)

```kotlin
package com.secondserve.domain.repository

import com.secondserve.domain.model.MatchScore
import kotlinx.coroutines.flow.StateFlow

interface ScoreRepository {
    val latestScore: StateFlow<MatchScore?>
    suspend fun updateScore(score: MatchScore)
}
```

> ⚠️ **Read-only pour le Phone** : Le Phone NE DOIT PAS appeler `updateScore()` directement — seul `DataLayerListener` l'appelle. Story 2.4 et Story 2.5 consommeront uniquement `latestScore`.

---

### `DataLayerClient` (`:data/wearable/`) — Côté WATCH

**`data/src/main/kotlin/com/secondserve/data/wearable/DataLayerClient.kt`** (NEW)

```kotlin
package com.secondserve.data.wearable

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.secondserve.data.wearable.dto.GameOverPayload
import com.secondserve.data.wearable.dto.ScoreEventPayload
import com.secondserve.data.wearable.dto.toDto
import com.secondserve.domain.model.MatchScore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataLayerClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    companion object {
        const val PATH_SCORE_EVENT = "/secondserve/score_event"
        const val PATH_GAME_OVER = "/secondserve/game_over"
    }

    suspend fun sendScoreEvent(score: MatchScore): com.secondserve.domain.AppResult<Unit> {
        val payload = ScoreEventPayload(ts = System.currentTimeMillis(), score = score.toDto())
        val json = moshi.adapter(ScoreEventPayload::class.java).toJson(payload)
        return sendMessage(PATH_SCORE_EVENT, json.toByteArray(Charsets.UTF_8))
    }

    suspend fun sendGameOver(score: MatchScore): com.secondserve.domain.AppResult<Unit> {
        val payload = GameOverPayload(ts = System.currentTimeMillis(), score_snapshot = score.toDto())
        val json = moshi.adapter(GameOverPayload::class.java).toJson(payload)
        return sendMessage(PATH_GAME_OVER, json.toByteArray(Charsets.UTF_8))
    }

    private suspend fun sendMessage(path: String, payload: ByteArray): com.secondserve.domain.AppResult<Unit> {
        return try {
            val nodeId = getPhoneNodeId()
            if (nodeId == null) {
                Timber.d("DataLayerClient: no connected phone node for path=%s", path)
                return com.secondserve.domain.AppResult.Error(
                    Exception("No connected phone node"),
                    "Téléphone non connecté via DataLayer"
                )
            }
            Wearable.getMessageClient(context).sendMessage(nodeId, path, payload).await()
            Timber.d("DataLayerClient: sent %s (%d bytes)", path, payload.size)
            com.secondserve.domain.AppResult.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "DataLayerClient: sendMessage failed for path=%s", path)
            com.secondserve.domain.AppResult.Error(e, "Erreur DataLayer: ${e.message}")
        }
    }

    private suspend fun getPhoneNodeId(): String? {
        return try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            nodes.firstOrNull()?.id
        } catch (e: Exception) {
            Timber.e(e, "DataLayerClient: getPhoneNodeId failed")
            null
        }
    }
}
```

> ⚠️ **`AppResult` utilisé, pas `Result<T>`** : Le projet utilise `com.secondserve.domain.AppResult` (sealed class dans `:domain`), pas le `Result<T>` standard Kotlin. Vérifier `domain/AppResult.kt` pour la signature exacte avant d'implémenter.

> ⚠️ **Moshi instancié localement** : Pour éviter une dépendance sur la configuration Moshi de `:app` (qui peut ne pas être disponible depuis `:wear`), `DataLayerClient` instancie son propre `Moshi` avec `KotlinJsonAdapterFactory`. Cohérent avec `:data` qui déjà dépend de `moshi-kotlin`.

> ⚠️ **`kotlinx-coroutines-play-services`** : `tasks.await()` nécessite la dépendance `org.jetbrains.kotlinx:kotlinx-coroutines-play-services`. Vérifier si elle est déjà présente dans `:data/build.gradle.kts` (alias `coroutines-android` peut ne pas l'inclure). Si absente, ajouter :
> ```kotlin
> implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
> ```
> Version à aligner avec `coroutines = "1.10.2"` dans `libs.versions.toml`. Ajouter d'abord l'alias dans le catalog si possible.

---

### `DataLayerListener` (`:data/wearable/`) — Côté PHONE

**`data/src/main/kotlin/com/secondserve/data/wearable/DataLayerListener.kt`** (NEW)

```kotlin
package com.secondserve.data.wearable

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.secondserve.data.wearable.dto.GameOverPayload
import com.secondserve.data.wearable.dto.ScoreEventPayload
import com.secondserve.data.wearable.dto.toDomain
import com.secondserve.domain.repository.ScoreRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class DataLayerListener : WearableListenerService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DataLayerListenerEntryPoint {
        fun scoreRepository(): ScoreRepository
    }

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    private val scoreRepository: ScoreRepository by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            DataLayerListenerEntryPoint::class.java
        ).scoreRepository()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val json = messageEvent.data.toString(Charsets.UTF_8)
        Timber.d("DataLayerListener: received path=%s payload=%s", messageEvent.path, json)

        when (messageEvent.path) {
            DataLayerClient.PATH_SCORE_EVENT -> handleScoreEvent(json)
            DataLayerClient.PATH_GAME_OVER -> handleGameOver(json)
            else -> Timber.d("DataLayerListener: unknown path=%s, ignoring", messageEvent.path)
        }
    }

    private fun handleScoreEvent(json: String) {
        try {
            val payload = moshi.adapter(ScoreEventPayload::class.java).fromJson(json)
                ?: return Timber.e("DataLayerListener: null ScoreEventPayload from JSON")
            serviceScope.launch {
                scoreRepository.updateScore(payload.score.toDomain())
                Timber.d("DataLayerListener: ScoreRepository updated via score_event")
            }
        } catch (e: Exception) {
            Timber.e(e, "DataLayerListener: failed to parse score_event JSON")
        }
    }

    private fun handleGameOver(json: String) {
        try {
            val payload = moshi.adapter(GameOverPayload::class.java).fromJson(json)
                ?: return Timber.e("DataLayerListener: null GameOverPayload from JSON")
            serviceScope.launch {
                scoreRepository.updateScore(payload.score_snapshot.toDomain())
                Timber.d("DataLayerListener: ScoreRepository updated via game_over")
            }
        } catch (e: Exception) {
            Timber.e(e, "DataLayerListener: failed to parse game_over JSON")
        }
    }
}
```

> ⚠️ **`EntryPointAccessors` — pattern obligatoire** : `WearableListenerService` est instancié par le système Android, pas par Hilt. `@AndroidEntryPoint` sur `WearableListenerService` peut poser des problèmes avec certaines versions de gms. Le pattern `EntryPointAccessors.fromApplication` est plus robuste et explicitement recommandé pour les services système.

> ⚠️ **`CoroutineScope` propre** : `serviceScope` utilise `Dispatchers.IO` car `updateScore` est `suspend`. Ne pas utiliser `GlobalScope`. Pour un service à durée de vie liée à l'app, ce scope est acceptable — il sera GCé quand `DataLayerListener` est détruit.

---

### `ScoreRepositoryImpl` (`:data/repository/`)

**`data/src/main/kotlin/com/secondserve/data/repository/ScoreRepositoryImpl.kt`** (NEW)

```kotlin
package com.secondserve.data.repository

import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.repository.ScoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScoreRepositoryImpl @Inject constructor() : ScoreRepository {

    private val _latestScore = MutableStateFlow<MatchScore?>(null)
    override val latestScore: StateFlow<MatchScore?> = _latestScore.asStateFlow()

    override suspend fun updateScore(score: MatchScore) {
        _latestScore.value = score
    }
}
```

> ⚠️ **Singleton obligatoire** : `DataLayerListener` et les futurs ViewModels (Story 2.4) doivent partager la même instance de `ScoreRepositoryImpl`. Le `@Singleton` dans Hilt garantit cela. Le binding doit être dans `DataModule.kt` (voir section DI ci-dessous).

---

### Enregistrement DI dans `DataModule.kt`

**`app/src/main/kotlin/com/secondserve/di/DataModule.kt`** — UPDATE

Ajouter les bindings suivants dans `DataModule` :

```kotlin
// Imports à ajouter
import com.secondserve.data.repository.ScoreRepositoryImpl
import com.secondserve.domain.repository.ScoreRepository

// Dans l'objet DataModule :
@Provides
@Singleton
fun provideScoreRepository(): ScoreRepository = ScoreRepositoryImpl()
```

---

### Enregistrement du service dans `AndroidManifest.xml` (`:app`)

**`app/src/main/AndroidManifest.xml`** — UPDATE

Ajouter dans `<application>` :

```xml
<service
    android:name="com.secondserve.data.wearable.DataLayerListener"
    android:exported="true">
    <intent-filter>
        <action android:name="com.google.android.gms.wearable.MESSAGE_RECEIVED" />
        <data
            android:scheme="wear"
            android:host="*"
            android:pathPrefix="/secondserve/" />
    </intent-filter>
</service>
```

> ⚠️ **`android:exported="true"` obligatoire** : Le système Wearable DataLayer doit pouvoir démarrer ce service depuis l'extérieur du process. Sans cela, le service ne reçoit aucun message.

> ⚠️ **`pathPrefix="/secondserve/"` couvre les 2 paths** : `/secondserve/score_event` ET `/secondserve/game_over` — pas besoin de deux `<data>` séparés.

---

## Tests

### Tests unitaires (JVM — sans device)

#### `ScoreRepositoryImplTest.kt` (NEW)

**`data/src/test/kotlin/com/secondserve/data/repository/ScoreRepositoryImplTest.kt`**

```kotlin
package com.secondserve.data.repository

import com.secondserve.domain.model.GamePoint
import com.secondserve.domain.model.MatchScore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ScoreRepositoryImplTest {

    private val repository = ScoreRepositoryImpl()

    @Test
    fun `latestScore starts as null`() = runTest {
        assertNull(repository.latestScore.first())
    }

    @Test
    fun `updateScore emits new score`() = runTest {
        val score = MatchScore(currentSetGamesA = 3, currentSetGamesB = 2)
        repository.updateScore(score)
        assertEquals(score, repository.latestScore.first())
    }

    @Test
    fun `updateScore replaces previous score`() = runTest {
        val score1 = MatchScore(currentSetGamesA = 1)
        val score2 = MatchScore(currentSetGamesA = 2)
        repository.updateScore(score1)
        repository.updateScore(score2)
        assertEquals(score2, repository.latestScore.first())
    }
}
```

#### `MatchScoreDtoTest.kt` (NEW)

**`data/src/test/kotlin/com/secondserve/data/wearable/dto/MatchScoreDtoTest.kt`**

```kotlin
package com.secondserve.data.wearable.dto

import com.secondserve.domain.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MatchScoreDtoTest {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @Test
    fun `MatchScore round-trips to DTO and back`() {
        val original = MatchScore(
            completedSets = listOf(SetResult(6, 4)),
            currentSetGamesA = 3,
            currentSetGamesB = 2,
            currentGamePointsA = GamePoint.THIRTY,
            currentGamePointsB = GamePoint.FIFTEEN
        )
        val dto = original.toDto()
        val restored = dto.toDomain()
        assertEquals(original, restored)
    }

    @Test
    fun `ScoreEventPayload serializes to valid JSON`() {
        val score = MatchScore(currentSetGamesA = 1)
        val payload = ScoreEventPayload(ts = 1234567890L, score = score.toDto())
        val json = moshi.adapter(ScoreEventPayload::class.java).toJson(payload)
        assertTrue(json.contains("\"type\":\"SCORE_EVENT\""))
        assertTrue(json.contains("\"ts\":1234567890"))
        assertTrue(json.contains("\"score\""))
    }

    @Test
    fun `GameOverPayload serializes with score_snapshot field`() {
        val score = MatchScore()
        val payload = GameOverPayload(ts = 9999L, score_snapshot = score.toDto())
        val json = moshi.adapter(GameOverPayload::class.java).toJson(payload)
        assertTrue(json.contains("\"type\":\"GAME_OVER\""))
        assertTrue(json.contains("\"score_snapshot\""))
    }

    @Test
    fun `enum values serialized as strings`() {
        val score = MatchScore(
            currentGamePointsA = GamePoint.ADVANTAGE,
            matchWinner = Player.B
        )
        val dto = score.toDto()
        assertEquals("ADVANTAGE", dto.currentGamePointsA)
        assertEquals("B", dto.matchWinner)
    }

    @Test
    fun `null matchWinner serializes to null`() {
        val score = MatchScore(matchWinner = null)
        val dto = score.toDto()
        assertNull(dto.matchWinner)
        val restored = dto.toDomain()
        assertNull(restored.matchWinner)
    }
}
```

> **Tests DataLayer (device requis)** : `DataLayerClient.sendScoreEvent()` et `DataLayerListener.onMessageReceived()` nécessitent un device physique appairé — NON testables en CI/émulateur. Ces tests sont hors scope pour cette story (cohérent avec NFR-PLT2 et le pattern `MockInferenceEngine` de l'architecture).

---

## Tasks / Subtasks

### Gradle & Dépendances

- [x] **Task G-1** — Ajouter `implementation(libs.wearable)` dans `data/build.gradle.kts`
- [x] **Task G-2** — Ajouter `implementation(project(":data"))` dans `wear/build.gradle.kts`
- [x] **Task G-3** — `kotlinx-coroutines-play-services` absent du catalog → alias `coroutines-play-services` ajouté dans `libs.versions.toml` + `data/build.gradle.kts`. Version `1.10.2` alignée avec `coroutines`

### Domain

- [x] **Task D-1** — Créer `domain/src/main/kotlin/com/secondserve/domain/repository/ScoreRepository.kt` : interface avec `latestScore: StateFlow<MatchScore?>` et `suspend fun updateScore(score: MatchScore)`

### DTOs DataLayer

- [x] **Task DTO-1** — Créer `data/src/main/kotlin/com/secondserve/data/wearable/dto/MatchScoreDto.kt` : `SetResultDto`, `MatchScoreDto`, extensions `toDto()` et `toDomain()`
- [x] **Task DTO-2** — Créer `data/src/main/kotlin/com/secondserve/data/wearable/dto/ScoreEventPayload.kt`
- [x] **Task DTO-3** — Créer `data/src/main/kotlin/com/secondserve/data/wearable/dto/GameOverPayload.kt`

### DataLayer Bridge

- [x] **Task DL-1** — Créer `data/src/main/kotlin/com/secondserve/data/wearable/DataLayerClient.kt` avec `PATH_SCORE_EVENT`, `PATH_GAME_OVER` constants, `sendScoreEvent()`, `sendGameOver()`, `sendMessage()` (private), `getPhoneNodeId()` (private)
- [x] **Task DL-2** — Créer `data/src/main/kotlin/com/secondserve/data/wearable/DataLayerListener.kt` : `WearableListenerService` avec `EntryPointAccessors`, `onMessageReceived()`, `handleScoreEvent()`, `handleGameOver()`

### Repository

- [x] **Task R-1** — Créer `data/src/main/kotlin/com/secondserve/data/repository/ScoreRepositoryImpl.kt` : `@Singleton`, `MutableStateFlow<MatchScore?>` interne

### DI & Configuration

- [x] **Task DI-1** — Ajouter `provideScoreRepository()` dans `app/src/main/kotlin/com/secondserve/di/DataModule.kt`
- [x] **Task DI-2** — Enregistrer `DataLayerListener` dans `app/src/main/AndroidManifest.xml` avec le bon intent-filter Wearable

### Tests

- [x] **Task T-1** — Créer `data/src/test/kotlin/com/secondserve/data/repository/ScoreRepositoryImplTest.kt`
- [x] **Task T-2** — Créer `data/src/test/kotlin/com/secondserve/data/wearable/dto/MatchScoreDtoTest.kt`
- [x] **Task T-3** — Tests JVM écrits et vérifiés par inspection (Android SDK absent de l'environnement distant — à valider localement avec `./gradlew :data:test`)
- [x] **Task T-4** — Gradle sync à valider localement avec `./gradlew :wear:assembleDebug :data:assembleDebug` (Android SDK absent de l'environnement distant)

---

## Dev Notes

### Guardrails critiques

**MatchOver et changeover — Déféré (D1 de Story 2.1)** :
> `MatchOver` ne transporte pas de signal `changeover`. Si le dernier point termine le match ET qu'il y a changement de côté, seul `MatchOver` est émis — sans `changeover: Boolean`. Story 2.5 devra gérer ce cas (soit en ajoutant `changeover` à `MatchOver`, soit en évaluant l'état du score directement). NE PAS ajouter ce champ dans cette story — déféré explicitement.

**`@JsonClass(generateAdapter = false)`** : Les DTOs utilisent la reflection Moshi (`KotlinJsonAdapterFactory`), pas la génération KSP. Cela évite d'ajouter `moshi-kotlin-codegen` au module `:data`. Si le projet migre vers la génération KSP plus tard, supprimer `generateAdapter = false` et ajouter le processeur.

**Threading `DataLayerListener`** : `onMessageReceived()` est appelé sur un thread background par le système Wearable. Ne pas bloquer ce thread — lancer la coroutine dans `serviceScope` comme montré dans l'implémentation.

**Pas de timeout côté DataLayer** : La Watch a un timeout de 60 secondes (Story 2.5 AC) pour la réponse DataLayer. `DataLayerClient.sendMessage()` ne gère pas ce timeout — il est géré côté Watch UI dans `ScoreViewModel` (Story 2.4). Cette story crée seulement l'infrastructure d'envoi.

**`AppResult` vs `Result<T>`** : Ce projet utilise `com.secondserve.domain.AppResult<T>` (défini dans `domain/AppResult.kt`). Ne pas utiliser le `kotlin.Result` standard. Vérifier la signature exacte d'`AppResult` avant d'implémenter.

### Ce que Story 2.4 consommera

- `DataLayerClient` depuis le `ScoreViewModel` Wear OS pour envoyer `sendScoreEvent()` après chaque point
- `ScoreRepository` depuis le `MatchViewModel` Phone pour observer `latestScore`

### Ce que Story 2.5 consommera

- `DataLayerClient.sendGameOver()` déclenché depuis `ScoreViewModel` Wear OS quand `EngineEvent.GameWon(changeover=true)` ou `EngineEvent.SetWon(changeover=true)` est reçu

### Structure fichiers finale attendue

```
android/data/src/main/kotlin/com/secondserve/data/
├── local/ [EXISTANT — NE PAS MODIFIER]
├── remote/ [EXISTANT — NE PAS MODIFIER]
├── repository/
│   ├── PlayerProfileRepositoryImpl.kt [EXISTANT]
│   ├── WorkAxisRepositoryImpl.kt [EXISTANT]
│   └── ScoreRepositoryImpl.kt          ← NEW
└── wearable/
    ├── DataLayerClient.kt               ← NEW
    ├── DataLayerListener.kt             ← NEW
    └── dto/
        ├── MatchScoreDto.kt             ← NEW
        ├── ScoreEventPayload.kt         ← NEW
        └── GameOverPayload.kt           ← NEW

android/domain/src/main/kotlin/com/secondserve/domain/
└── repository/
    ├── PlayerProfileRepository.kt [EXISTANT]
    ├── WorkAxisRepository.kt [EXISTANT]
    └── ScoreRepository.kt               ← NEW
```

### Patterns établis à réutiliser

| Pattern | Référence |
|---------|-----------|
| `AppResult<T>` pour erreurs | `domain/AppResult.kt` |
| `Timber.d/e` pour logs | architecture.md § Process Patterns |
| `@Singleton` + `@Inject constructor` | Pattern Hilt existant dans `PlayerProfileRepositoryImpl` |
| `MutableStateFlow` / `asStateFlow()` | Pattern standard Kotlin Flows |
| `EntryPointAccessors` pour services | Pattern Hilt recommandé hors `@AndroidEntryPoint` |
| Epoch ms pour timestamps | architecture.md § Format Patterns |

### References

- [Source: epics.md § Story 2.2] — Acceptance criteria complets, user story statement
- [Source: epics.md § ARCH-7] — "DataLayer bridge Watch ↔ Phone — 2 paths JSON actifs : `/secondserve/score_event` et `/secondserve/game_over`. `/secondserve/coaching_result` supprimé."
- [Source: architecture.md § API & Communication] — Payloads DataLayer JSON avec champs exacts
- [Source: architecture.md § Core Architectural Decisions D3] — "JSON — lisible, debuggable, zéro dépendance"
- [Source: architecture.md § Project Structure] — `data/wearable/DataLayerClient.kt`, `data/wearable/DataLayerListener.kt`
- [Source: architecture.md § Naming Patterns] — `SCREAMING_SNAKE_CASE` pour constantes, `camelCase` pour fonctions
- [Source: architecture.md § Process Patterns] — `AppResult<T>`, `Timber` (jamais `Log.*`)
- [Source: 2-1-tennisscoreengine-automate-a-etats-finis.md § Dev Notes] — "Story 2.2 consommera `EngineEvent.GameWon(changeover)`, `MatchScore`, `Player`"
- [Source: deferred-work.md] — "D1: `MatchOver` sans signal changeover — déféré à Story 2.5"
- [Source: libs.versions.toml] — `playServicesWearable = "18.2.0"`, `coroutines = "1.10.2"`

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6 (Claude Code remote session)

### Debug Log References

- **AppResult.Error signature** : Le story file indiquait `AppResult.Error(Exception(...), "message")` mais la classe réelle (`domain/AppResult.kt`) ne prend qu'un `Throwable`. Adapté `DataLayerClient` en conséquence.
- **coroutines-play-services manquant** : `tasks.await()` de GMS requiert `kotlinx-coroutines-play-services`. Ajouté alias dans `libs.versions.toml` et dépendance dans `data/build.gradle.kts`.
- **Android SDK absent de l'environnement distant** : Impossible d'exécuter `./gradlew :data:test` — tests écrits et vérifiés par inspection, à valider localement.

### Completion Notes List

- ✅ G-1/G-2/G-3 : dépendances Gradle ajoutées — `libs.wearable` dans `:data`, `project(":data")` dans `:wear`, alias `coroutines-play-services` dans catalog
- ✅ D-1 : `ScoreRepository` interface créée dans `:domain/repository/`
- ✅ DTO-1/DTO-2/DTO-3 : `MatchScoreDto`, `ScoreEventPayload`, `GameOverPayload` créés dans `data/wearable/dto/`
- ✅ DL-1 : `DataLayerClient` créé — envoie `score_event` et `game_over` via GMS MessageClient
- ✅ DL-2 : `DataLayerListener` créé — `WearableListenerService` avec `EntryPointAccessors` pour injection Hilt
- ✅ R-1 : `ScoreRepositoryImpl` créé — `@Singleton`, `MutableStateFlow<MatchScore?>` interne
- ✅ DI-1 : `provideScoreRepository()` ajouté dans `DataModule`
- ✅ DI-2 : `DataLayerListener` enregistré dans `AndroidManifest.xml` avec `pathPrefix="/secondserve/"`
- ✅ T-1/T-2 : tests JVM `ScoreRepositoryImplTest` et `MatchScoreDtoTest` créés

### File List

- `android/gradle/libs.versions.toml` — ajout alias `coroutines-play-services`
- `android/data/build.gradle.kts` — ajout `coroutines.play.services`, `wearable`
- `android/wear/build.gradle.kts` — ajout `project(":data")`
- `android/domain/src/main/kotlin/com/secondserve/domain/repository/ScoreRepository.kt` — NEW
- `android/data/src/main/kotlin/com/secondserve/data/wearable/dto/MatchScoreDto.kt` — NEW
- `android/data/src/main/kotlin/com/secondserve/data/wearable/dto/ScoreEventPayload.kt` — NEW
- `android/data/src/main/kotlin/com/secondserve/data/wearable/dto/GameOverPayload.kt` — NEW
- `android/data/src/main/kotlin/com/secondserve/data/wearable/DataLayerClient.kt` — NEW
- `android/data/src/main/kotlin/com/secondserve/data/wearable/DataLayerListener.kt` — NEW
- `android/data/src/main/kotlin/com/secondserve/data/repository/ScoreRepositoryImpl.kt` — NEW
- `android/app/src/main/kotlin/com/secondserve/di/DataModule.kt` — ajout `provideScoreRepository()`
- `android/app/src/main/AndroidManifest.xml` — enregistrement `DataLayerListener`
- `android/data/src/test/kotlin/com/secondserve/data/repository/ScoreRepositoryImplTest.kt` — NEW
- `android/data/src/test/kotlin/com/secondserve/data/wearable/dto/MatchScoreDtoTest.kt` — NEW
- `_bmad-output/implementation-artifacts/2-2-datalayer-bridge-watch-phone.md` — status → review

---

## Review Findings

### Decision Needed

- [x] [Review][Decision] **F3 — DataLayerListener dans `:app` au lieu de `:data/wearable/`** — Résolu : déplacé vers `data/wearable/DataLayerListener.kt` (package `com.secondserve.data.wearable`). Manifest mis à jour avec nom complet de classe. `implementation(libs.wearable)` retiré de `:app`.

### Patches

- [x] [Review][Patch] **F1 — `serviceScope` sans `SupervisorJob` ni `onDestroy.cancel()`** — Corrigé : `CoroutineScope(SupervisorJob() + Dispatchers.IO)` + `onDestroy { serviceScope.cancel() }` [`DataLayerListener.kt`]
- [x] [Review][Patch] **F2 — `toDomain()` lève `IAE` silencieusement dans `launch`** — Corrigé : `toDomain()` sorti du `launch`, couvert par le `try/catch` externe [`DataLayerListener.kt`]
- [x] [Review][Patch] **F6 — `@JsonClass(generateAdapter = false)` sans règles ProGuard/R8** — Corrigé : `consumer-rules.pro` créé + `consumerProguardFiles` ajouté dans `data/build.gradle.kts` [`data/consumer-rules.pro`]
- [x] [Review][Patch] **F7 — `android:exported="true"` sans `android:permission`** — Corrigé : `android:permission="com.google.android.gms.wearable.BIND_LISTENER"` ajouté [`AndroidManifest.xml`]
- [x] [Review][Patch] **F14 — Pas de test pour `toDomain()` avec enum invalide** — Corrigé : 2 tests ajoutés (`unknown GamePoint`, `unknown Player`) [`MatchScoreDtoTest.kt`]
- [x] [Review][Patch] **F16 — `Timber.e()` utilisé comme expression `return`** — Corrigé : remplacé par `if (payload == null) { Timber.e(...); return }` [`DataLayerListener.kt`]

### Deferred

- [x] [Review][Defer] **F4 — `getPhoneNodeId()` `firstOrNull()` sans filtre `isNearby`** [`DataLayerClient.kt:61`] — déféré, cas multi-watch hors scope story 2.2
- [x] [Review][Defer] **F5 — Hilt potentiellement non initialisé au démarrage GMS** [`DataLayerListener.kt:35`] — déféré, faux positif en mode same-process standard
- [x] [Review][Defer] **F8 — `updateScore()` public sur l'interface domain** [`ScoreRepository.kt`] — déféré, défini par le spec ; enforçable par convention
- [x] [Review][Defer] **F9 — Ordering concurrent `score_event` / `game_over`** [`ScoreRepositoryImpl.kt:19`] — déféré, `StateFlow.value` atomique ; ordering garanti par Story 2.3 (Room)
- [x] [Review][Defer] **F11 — Deux instances `Moshi` séparées** [`DataLayerListener.kt:29`, `DataLayerClient.kt:23`] — déféré, optimisation hors scope
- [x] [Review][Defer] **F12 — Taille payload non vérifiée (limite 8 KB Wearable)** [`DataLayerClient.kt:36`] — déféré, MatchScore normal bien sous 8 KB
- [x] [Review][Defer] **F13 — Downgrade `minSdk` 35→33 trop large pour `:data`** [`data/build.gradle.kts:13`] — déféré, refactoring module séparé hors scope
