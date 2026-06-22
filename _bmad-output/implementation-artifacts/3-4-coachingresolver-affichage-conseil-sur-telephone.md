---
baseline_commit: b2d95c1
---

# Story 3.4: CoachingResolver & affichage Conseil sur téléphone

Status: review

## Story

As a player,
I want a personalized coaching advice to appear automatically on my phone at each changement de côté,
So that I get relevant guidance in ≤ 3 seconds without any interaction, even offline.

## Acceptance Criteria

1. **Given** un `game_over` est reçu via DataLayer (Story 2.5)
   **When** `CoachingResolver` est invoqué
   **Then** il suit la chaîne de priorité :
   1. `GeminiNanoEngine` (on-device, primaire) — si disponible et répond en ≤ 3s (withTimeout(3000L))
   2. `OfflineCoachingCache` lookup via `CoachingPatternDetector.detect(MatchStateSnapshot(score))` — lecture O(1) en Room
   3. `GENERIC_FALLBACK_TEXTS` — filet de sécurité final (jamais null)
   **And** la source est tracée en Timber DEBUG : `GEMINI` | `CACHE` | `STATIC`
   **And** si `score.isMatchOver == true`, `CoachingResolver.resolve()` retourne `null` sans générer de conseil

2. **And** le Conseil s'affiche automatiquement sur le `MatchScreen` du Phone (NFR-UX4) — aucun tap requis
   **And** l'affichage se produit en ≤ 3 secondes après le `game_over` (NFR-P1)
   **And** le Conseil contient ≤ 3 phrases (NFR-UX2) — garanti par le prompt

3. **And** le Conseil référence au moins un élément de contexte réel : surface, classement FFT, WorkAxis actif, ou style de jeu (FR-4)

4. **Given** le Conseil est affiché
   **Then** `CoachingCachePrefetcher.refreshPostChangeover(sessionId, score)` se déclenche en background non-bloquant :
   - `coachingRepository.markMatchEntriesStale(sessionId)` est appelé
   - Les patterns probables (7 patterns max) sont régénérés via `InferenceEngine`

---

## Position dans la séquence

```
Story 3.1 ✅ → Story 3.2 ✅ → Story 3.3 ✅ → Story 3.4 (CETTE STORY)
```

**Prérequis satisfaits (Story 3.3) :**
- ✅ `MatchPattern` (20 patterns + GENERIC_FALLBACK_TEXTS) : `android/domain/.../model/MatchPattern.kt`
- ✅ `MatchStateSnapshot` : `android/domain/.../model/MatchStateSnapshot.kt`
- ✅ `CoachingCacheEntry` : `android/domain/.../model/CoachingCacheEntry.kt`
- ✅ `CoachingRepository` (interface) : `android/domain/.../repository/CoachingRepository.kt`
- ✅ `CoachingPatternDetector` (object) : `android/domain/.../engine/CoachingPatternDetector.kt`
- ✅ `CoachingCacheDao` + `SecondServeDatabase v6` + `CoachingModule` : `:data`
- ✅ `CoachingCachePrefetcher` (sans refreshPostChangeover) : `android/feature/match/...`
- ✅ `MatchViewModel` injecte `CoachingCachePrefetcher` et appelle `initMatch(sessionId)` dans `init {}`
- ✅ `InferenceEngine` interface + `@Inject constructor` sur `CoachingCachePrefetcher`

**Ce que cette story NE fait PAS :**
- ❌ Pas d'envoi du conseil vers la Pixel Watch (PATH_COACHING_RESULT est prévu mais non utilisé dans cette story)
- ❌ Pas de `VpsMistralEngine` (Story 5.1)
- ❌ Pas de modifications du backend VPS

---

## Technical Requirements

### Fichier 1 — `CoachingResult.kt` (NEW) dans `:domain`

**`android/domain/src/main/kotlin/com/secondserve/domain/model/CoachingResult.kt`**

```kotlin
package com.secondserve.domain.model

enum class CoachingSource { GEMINI, CACHE, STATIC }

data class CoachingResult(
    val text: String,
    val source: CoachingSource
)
```

> Placé dans `:domain` car `CoachingResolver` retourne ce type et `MatchUiState` (dans `:feature:match`) le consomme. Le module `:feature:match` dépend déjà de `:domain`.

---

### Fichier 2 — `DataLayerEventBus.kt` (UPDATE) dans `:domain`

**`android/domain/src/main/kotlin/com/secondserve/domain/event/DataLayerEventBus.kt`**

Ajouter un second flux pour les événements `game_over` :

```kotlin
package com.secondserve.domain.event

import com.secondserve.domain.model.MatchScore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class DataLayerEventBus {
    // --- existant ---
    private val _closeSessionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val closeSessionRequests: SharedFlow<Unit> = _closeSessionRequests

    fun emitCloseRequest() {
        _closeSessionRequests.tryEmit(Unit)
    }

    // --- AJOUT Story 3.4 ---
    private val _gameOverEvents = MutableSharedFlow<MatchScore>(extraBufferCapacity = 1)
    val gameOverEvents: SharedFlow<MatchScore> = _gameOverEvents

    fun emitGameOver(score: MatchScore) {
        _gameOverEvents.tryEmit(score)
    }
}
```

> **⚠️ `tryEmit` avec `extraBufferCapacity = 1`** — cohérent avec `closeSessionRequests` existant. Si le ViewModel n'est pas actif, l'événement est bufferisé (1 slot). Un second `game_over` avant que le premier soit consommé est silencieusement ignoré — acceptable pour ce cas d'usage.

---

### Fichier 3 — `DataLayerListener.kt` (UPDATE) dans `:data`

**`android/data/src/main/kotlin/com/secondserve/data/wearable/DataLayerListener.kt`**

Modifier uniquement `handleGameOver()` pour émettre l'événement sur le bus après la mise à jour du score :

```kotlin
private fun handleGameOver(json: String) {
    try {
        val payload = moshi.adapter(GameOverPayload::class.java).fromJson(json)
        if (payload == null) {
            Timber.e("DataLayerListener: null GameOverPayload from JSON")
            return
        }
        val score = payload.scoreSnapshot.toDomain()
        serviceScope.launch {
            withContext(NonCancellable) { scoreRepository.updateScore(score) }
            dataLayerEventBus.emitGameOver(score)   // ← AJOUT
            Timber.d("DataLayerListener: score updated and gameOver emitted")
        }
    } catch (e: Exception) {
        Timber.e(e, "DataLayerListener: failed to handle game_over")
    }
}
```

> **⚠️ `dataLayerEventBus` est déjà accessible** — le `DataLayerListenerEntryPoint` expose déjà `fun dataLayerEventBus(): DataLayerEventBus`. Aucune modification de l'EntryPoint n'est nécessaire.

> **⚠️ `withContext(NonCancellable)`** autour du `updateScore` uniquement — `emitGameOver` n'a pas besoin de cette protection car `tryEmit` est non-suspending.

---

### Fichier 4 — `CoachingResolver.kt` (NEW) dans `:feature:match`

**`android/feature/match/src/main/kotlin/com/secondserve/feature/match/CoachingResolver.kt`**

```kotlin
package com.secondserve.feature.match

import com.secondserve.core.ai.InferenceEngine
import com.secondserve.domain.AppResult
import com.secondserve.domain.engine.CoachingPatternDetector
import com.secondserve.domain.model.CoachingResult
import com.secondserve.domain.model.CoachingSource
import com.secondserve.domain.model.MatchPattern
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.model.MatchStateSnapshot
import com.secondserve.domain.repository.CoachingRepository
import com.secondserve.domain.repository.PlayerProfileRepository
import com.secondserve.domain.repository.SessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoachingResolver @Inject constructor(
    private val inferenceEngine: InferenceEngine,
    private val coachingRepository: CoachingRepository,
    private val playerProfileRepository: PlayerProfileRepository,
    private val sessionRepository: SessionRepository
) {

    suspend fun resolve(sessionId: Long, score: MatchScore): CoachingResult? {
        if (score.isMatchOver) {
            Timber.d("CoachingResolver: match is over, skipping advice")
            return null
        }

        val pattern = CoachingPatternDetector.detect(MatchStateSnapshot(score))

        // 1. GeminiNano avec timeout 3s
        val geminiResult = try {
            withTimeout(3_000L) {
                val session = sessionRepository.getSessionById(sessionId)
                val context = playerProfileRepository.buildMatchContextProfile()
                val prompt = buildPrompt(pattern, context, session?.surface ?: "")
                inferenceEngine.generate(prompt)
            }
        } catch (e: TimeoutCancellationException) {
            Timber.d("CoachingResolver: GeminiNano timeout for pattern=%s", pattern)
            AppResult.Error(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d("CoachingResolver: GeminiNano error for pattern=%s: %s", pattern, e.message)
            AppResult.Error(e)
        }

        if (geminiResult is AppResult.Success) {
            Timber.d("CoachingResolver: source=GEMINI, pattern=%s", pattern)
            return CoachingResult(geminiResult.data, CoachingSource.GEMINI)
        }

        // 2. Cache Room
        val cached = coachingRepository.getCachedAdvice(sessionId, pattern)
        if (cached != null) {
            Timber.d("CoachingResolver: source=CACHE, pattern=%s", pattern)
            return CoachingResult(cached.content, CoachingSource.CACHE)
        }

        // 3. Fallback statique — ne retourne jamais null
        val fallback = MatchPattern.GENERIC_FALLBACK_TEXTS[pattern]
            ?: MatchPattern.GENERIC_FALLBACK_TEXTS[MatchPattern.NEUTRAL_TRANSITION]
            ?: "Restez concentré sur chaque point."
        Timber.d("CoachingResolver: source=STATIC, pattern=%s", pattern)
        return CoachingResult(fallback, CoachingSource.STATIC)
    }

    private fun buildPrompt(
        pattern: MatchPattern,
        context: com.secondserve.domain.model.MatchContextProfile,
        surface: String
    ): String = buildString {
        append("Tu es coach tennis. Situation de jeu : ${pattern.description}.\n")
        append("Surface : $surface.")
        if (context.fftSeries != null) append(" Classement joueur : ${context.fftSeries}.")
        if (context.playStyle != null) append(" Style : ${context.playStyle}.")
        if (context.activeWorkAxes.isNotEmpty()) {
            append(" Axes de travail : ${context.activeWorkAxes.joinToString(", ")}.")
        }
        if (context.coachInstructions.isNotEmpty()) {
            append(" Consignes coach : ${context.coachInstructions.joinToString(". ")}.")
        }
        append("\nDonne un conseil court (2-3 phrases max) pour le prochain jeu.")
    }
}
```

> **⚠️ Pattern identique à `CoachingCachePrefetcher.buildPrompt()`** — prompt délibérément identique pour cohérence de la couche coaching. Ne pas factoriser (2 composants distincts par design architectural).

> **⚠️ `@Inject constructor` obligatoire** — Bug reproduit Stories 2.6, 3.1, 3.2. Sans `@Inject`, Hilt échoue silencieusement.

> **⚠️ `CancellationException` re-throwée** — Pattern obligatoire dans tous les `catch (e: Exception)` des coroutines. `TimeoutCancellationException` est une `CancellationException` — elle doit être attrapée séparément AVANT le `catch (e: CancellationException)` pour logger le timeout, puis la chaîne re-throw via `AppResult.Error` (le wrapping dans `AppResult.Error(e)` ne re-throw pas la CancellationException, donc acceptable ici).

> **⚠️ `sessionId <= 0L`** — Non gardé dans `resolve()` car `MatchViewModel.sessionId` a déjà un fallback documenté comme inatteignable en navigation correcte (voir deferred-work.md). Cohérent avec le pattern `initMatch` post-patch Story 3.3.

> **⚠️ `session?.surface ?: ""`** — Si la session n'est pas trouvée, prompt sans surface. Acceptable — fallback silencieux, aucun crash.

---

### Fichier 5 — `CoachingCachePrefetcher.kt` (UPDATE)

**`android/feature/match/src/main/kotlin/com/secondserve/feature/match/CoachingCachePrefetcher.kt`**

Ajouter la méthode `refreshPostChangeover()` à la fin de la classe (avant le `}` fermant) :

```kotlin
fun refreshPostChangeover(sessionId: Long, score: MatchScore) {
    if (sessionId <= 0L) return
    prefetchScope.launch {
        try {
            coachingRepository.markMatchEntriesStale(sessionId)
            Timber.d("CoachingCachePrefetcher: entries marked stale for session=%d", sessionId)

            val currentPattern = CoachingPatternDetector.detect(MatchStateSnapshot(score))
            val patterns = getProbablePatterns(currentPattern)

            val session = sessionRepository.getSessionById(sessionId) ?: return@launch
            val contextProfile = playerProfileRepository.buildMatchContextProfile()

            patterns.forEach { pattern ->
                val prompt = buildPrompt(pattern, contextProfile, session.surface)
                when (val result = inferenceEngine.generate(prompt)) {
                    is AppResult.Success -> {
                        coachingRepository.saveAdvice(sessionId, pattern, result.data)
                        Timber.d("CoachingCachePrefetcher: refreshed %s", pattern)
                    }
                    is AppResult.Error -> {
                        Timber.d("CoachingCachePrefetcher: refresh failed for %s, keeping stale", pattern)
                    }
                    AppResult.Loading -> {}
                }
            }
            Timber.d("CoachingCachePrefetcher: refreshPostChangeover done, session=%d", sessionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "CoachingCachePrefetcher: refreshPostChangeover failed for session=%d", sessionId)
        }
    }
}

private fun getProbablePatterns(currentPattern: MatchPattern): List<MatchPattern> {
    return listOf(
        currentPattern,
        MatchPattern.NEUTRAL_TRANSITION,
        MatchPattern.SERVICE_HELD_EASY,
        MatchPattern.SERVICE_HELD_UNDER_PRESSURE,
        MatchPattern.SERVICE_BROKEN,
        MatchPattern.BREAK_CONFIRMED,
        MatchPattern.BREAK_LOST_AFTER_HOLD
    ).distinct()
}
```

Ajouter également les imports manquants en tête de fichier :
```kotlin
import com.secondserve.domain.engine.CoachingPatternDetector
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.model.MatchStateSnapshot
```

> **⚠️ Utilise `prefetchScope` existant** (non `viewModelScope`) — fire-and-forget comme `initMatch`. Pas de `suspend`, pas de `launch` supplémentaire dans l'appelant (`MatchViewModel`).

> **⚠️ Ne recrée pas de scope** — `prefetchScope` est le scope singleton partagé du `@Singleton CoachingCachePrefetcher`. Si `initMatch` est encore actif, le refresh s'exécute en parallèle (via `SupervisorJob`).

> **⚠️ En cas d'erreur de refresh** — on logue et on laisse l'entrée stale lisible. Règle de staleness de l'architecture : "un pattern non rafraîchi reste lisible, jamais supprimé."

---

### Fichier 6 — `MatchViewModel.kt` (UPDATE)

**`android/feature/match/src/main/kotlin/com/secondserve/feature/match/MatchViewModel.kt`**

**Modification 1 — Ajouter `CoachingResolver` dans le constructeur :**
```kotlin
@HiltViewModel
class MatchViewModel @Inject constructor(
    private val scoreRepository: ScoreRepository,
    private val closeMatchUseCase: CloseMatchUseCase,
    private val syncScheduler: SyncScheduler,
    private val dataLayerEventBus: DataLayerEventBus,
    private val coachingCachePrefetcher: CoachingCachePrefetcher,
    private val coachingResolver: CoachingResolver,     // ← AJOUT
    savedStateHandle: SavedStateHandle
) : ViewModel(), ContainerHost<MatchUiState, MatchSideEffect> {
```

**Modification 2 — Ajouter la collection de `gameOverEvents` dans `init {}`:**
```kotlin
init {
    coachingCachePrefetcher.initMatch(sessionId)

    viewModelScope.launch {
        dataLayerEventBus.closeSessionRequests.collect {
            onCloseRequested()
        }
    }

    // AJOUT Story 3.4 — réception game_over et résolution du conseil
    viewModelScope.launch {
        dataLayerEventBus.gameOverEvents.collect { score ->
            val result = coachingResolver.resolve(sessionId, score)
            result?.let { advice ->
                intent { reduce { state.copy(coachingAdvice = advice) } }
                coachingCachePrefetcher.refreshPostChangeover(sessionId, score)
            }
        }
    }
}
```

**Modification 3 — Ajouter `coachingAdvice` dans `MatchUiState` :**
```kotlin
data class MatchUiState(
    val showCloseDialog: Boolean = false,
    val feelingRating: Int? = null,
    val feelingComment: String = "",
    val isClosing: Boolean = false,
    val coachingAdvice: CoachingResult? = null     // ← AJOUT
)
```

**Import à ajouter :**
```kotlin
import com.secondserve.domain.model.CoachingResult
```

> **⚠️ `coachingResolver.resolve()` est `suspend`** — correct, appelé dans `viewModelScope.launch { collect { ... } }`.

> **⚠️ `coachingCachePrefetcher.refreshPostChangeover()` est non-suspend** — appelé directement dans le `collect {}`, pas de `launch {}` supplémentaire.

> **⚠️ `intent { reduce { ... } }` après `resolve()`** — Orbit exécute les intents dans l'ordre ; cette séquence est correcte.

---

### Fichier 7 — `MatchScreen.kt` (UPDATE)

**`android/feature/match/src/main/kotlin/com/secondserve/feature/match/MatchScreen.kt`**

**Modification 1 — Ajouter imports :**
```kotlin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
```

**Modification 2 — Afficher le conseil après le score, avant le Spacer :**

Dans le `Column` de `MatchScreen`, insérer entre le `Text("Sets : ...")` et le `Spacer` :

```kotlin
// Conseil coaching — affiché automatiquement après chaque game_over
state.coachingAdvice?.let { advice ->
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Conseil changement de côté",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = advice.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
```

> **⚠️ `Card` et `CardDefaults` sont dans `androidx.compose.material3`** — disponibles depuis Compose BOM 2026.05.00 utilisé dans ce projet.

> **⚠️ `state.coachingAdvice` est initialement `null`** — la carte n'est pas affichée avant le premier `game_over`.

> **⚠️ Source non affichée** — `advice.source` est tracé en Timber uniquement (non affiché à l'utilisateur, conforme à l'AC).

---

## Tasks / Subtasks

### Domain — Modèles

- [x] **Task CR-1** — Créer `CoachingResult.kt` dans `domain/model/` avec `data class CoachingResult(text, source)` et `enum class CoachingSource { GEMINI, CACHE, STATIC }` (AC: #1, #2)

### Domain — Event Bus

- [x] **Task CR-2** — Mettre à jour `DataLayerEventBus.kt` : ajouter `_gameOverEvents: MutableSharedFlow<MatchScore>(extraBufferCapacity=1)`, `val gameOverEvents`, `fun emitGameOver(score)` (AC: #1)

### Data — Listener

- [x] **Task CR-3** — Mettre à jour `DataLayerListener.kt` : dans `handleGameOver()`, ajouter `dataLayerEventBus.emitGameOver(score)` après `scoreRepository.updateScore(score)` dans le `serviceScope.launch {}` (AC: #1)

### Feature Match — CoachingResolver

- [x] **Task CR-4** — Créer `CoachingResolver.kt` dans `feature/match/` avec `@Singleton @Inject constructor(inferenceEngine, coachingRepository, playerProfileRepository, sessionRepository)`, `suspend fun resolve(sessionId, score): CoachingResult?`, chaîne GEMINI→CACHE→STATIC avec guard `isMatchOver`, `withTimeout(3000L)`, `buildPrompt()` privé (AC: #1, #2, #3)

### Feature Match — Prefetcher refresh

- [x] **Task CR-5** — Mettre à jour `CoachingCachePrefetcher.kt` : ajouter `fun refreshPostChangeover(sessionId, score)` (appelle `markMatchEntriesStale`, détecte `getProbablePatterns`, régénère dans `prefetchScope`) + ajouter `private fun getProbablePatterns(currentPattern): List<MatchPattern>` (7 patterns) + importer `CoachingPatternDetector`, `MatchScore`, `MatchStateSnapshot` (AC: #4)

### Feature Match — ViewModel

- [x] **Task CR-6** — Mettre à jour `MatchViewModel.kt` : ajouter `coachingResolver: CoachingResolver` dans constructeur, ajouter collect `gameOverEvents` dans `init {}`, appeler `resolve()` puis `intent { reduce }` + `refreshPostChangeover()`, ajouter `coachingAdvice: CoachingResult?` dans `MatchUiState` (AC: #1, #2, #4)

### Feature Match — UI

- [x] **Task CR-7** — Mettre à jour `MatchScreen.kt` : afficher `CoachingCard` Material3 quand `state.coachingAdvice != null` (texte du conseil dans `primaryContainer`) (AC: #2)

### Tests

- [x] **Task CR-8** — Créer `CoachingResolverTest.kt` dans `feature/match/src/test/` : couvrir GEMINI path, CACHE fallback, STATIC fallback, timeout → CACHE, `isMatchOver → null`, `CancellationException` re-throwée (AC: #1)
- [x] **Task CR-9** — Mettre à jour `MatchViewModelTest.kt` : ajouter `coachingResolver: CoachingResolver` comme `mockk()`, ajouter au constructeur `MatchViewModel(...)`, ajouter test `gameOver event triggers resolve and updates coachingAdvice`, ajouter test `gameOver with isMatchOver=true does not update coachingAdvice` (AC: #1, #2)
- [x] **Task CR-10** — Lancer `:feature:match:test` — tous les tests existants + nouveaux verts, aucune régression
- [x] **Task CR-11** — Lancer `:domain:test` — aucune régression Stories 3.1/3.2/3.3
- [x] **Task CR-12** — Lancer `:app:kspDebugKotlin` + `:app:kspReleaseKotlin` — BUILD SUCCESSFUL, graphe Hilt valide

---

## Dev Notes

### Guardrails critiques

#### ⚠️ `withTimeout` et `TimeoutCancellationException` vs `CancellationException`

```kotlin
// ✅ Correct — TimeoutCancellationException attrapée AVANT CancellationException
} catch (e: TimeoutCancellationException) {
    AppResult.Error(e)          // wrapping → NE re-throw PAS la CE
} catch (e: CancellationException) {
    throw e                     // re-throw obligatoire pour les autres CE
} catch (e: Exception) {
    AppResult.Error(e)
}

// ❌ Interdit — TimeoutCancellationException absorbée silencieusement
} catch (e: Exception) {       // attrape tout y compris les CE → bug coroutine
    AppResult.Error(e)
}
```

`TimeoutCancellationException` est une sous-classe de `CancellationException`. En la wrappant dans `AppResult.Error` (au lieu de la re-throw), on ne propage pas la cancellation — ce qui est voulu ici car on veut fallback vers le cache. La `CancellationException` non-timeout doit quand même être re-throwée.

#### ⚠️ `refreshPostChangeover()` est non-suspend, fire-and-forget

```kotlin
// ✅ Correct dans le collect {} du ViewModel
result?.let { advice ->
    intent { reduce { state.copy(coachingAdvice = advice) } }
    coachingCachePrefetcher.refreshPostChangeover(sessionId, score)  // non-suspend, direct call
}

// ❌ Interdit
viewModelScope.launch { coachingCachePrefetcher.refreshPostChangeover(...) }  // double launch inutile
```

#### ⚠️ `DataLayerEventBus` — même instance garantie par Hilt `@Singleton`

`DataLayerListener` accède à `DataLayerEventBus` via `EntryPointAccessors` (singleton scope). `MatchViewModel` l'injecte via Hilt. Les deux reçoivent la **même instance** — c'est la garantie fondamentale de ce design.

#### ⚠️ `CoachingResolver` doit être `@Singleton`

Même pattern que `CoachingCachePrefetcher`. Un seul resolver actif par session d'app. Sans `@Singleton`, Hilt crée une nouvelle instance pour chaque injection (y compris dans les ViewModel), ce qui est acceptable mais inutilement coûteux.

#### ⚠️ `MatchViewModelTest` — ajouter `coachingResolver` au constructeur

```kotlin
// MatchViewModelTest.setup() — AJOUTER
private lateinit var coachingResolver: CoachingResolver

// Dans setup():
coachingResolver = mockk()  // PAS relaxed — on veut vérifier les appels explicitement

viewModel = MatchViewModel(
    scoreRepository = scoreRepository,
    closeMatchUseCase = closeMatchUseCase,
    syncScheduler = syncScheduler,
    dataLayerEventBus = dataLayerEventBus,
    coachingCachePrefetcher = coachingCachePrefetcher,
    coachingResolver = coachingResolver,   // ← AJOUT
    savedStateHandle = SavedStateHandle(mapOf("sessionId" to 10L))
)
```

Tests à ajouter :
```kotlin
@Test
fun `gameOver event triggers resolve and updates coachingAdvice`() = runTest {
    val score = MatchScore(currentSetGamesA = 1, currentSetGamesB = 0)
    val expected = CoachingResult("Bravo pour ce jeu.", CoachingSource.CACHE)
    coEvery { coachingResolver.resolve(10L, score) } returns expected

    dataLayerEventBus.emitGameOver(score)

    val state = viewModel.container.stateFlow.first { it.coachingAdvice != null }
    assertEquals(expected, state.coachingAdvice)
}

@Test
fun `gameOver with isMatchOver=true does not update coachingAdvice`() = runTest {
    val score = MatchScore(isMatchOver = true)
    coEvery { coachingResolver.resolve(10L, score) } returns null

    dataLayerEventBus.emitGameOver(score)
    testDispatcher.scheduler.advanceUntilIdle()

    assertNull(viewModel.container.stateFlow.value.coachingAdvice)
}
```

#### ⚠️ `CoachingResolverTest` — pattern MockK + coroutines

```kotlin
package com.secondserve.feature.match

import com.secondserve.core.ai.InferenceEngine
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.*
import com.secondserve.domain.repository.CoachingRepository
import com.secondserve.domain.repository.PlayerProfileRepository
import com.secondserve.domain.repository.SessionRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CoachingResolverTest {
    private lateinit var inferenceEngine: InferenceEngine
    private lateinit var coachingRepository: CoachingRepository
    private lateinit var playerProfileRepository: PlayerProfileRepository
    private lateinit var sessionRepository: SessionRepository
    private lateinit var resolver: CoachingResolver

    // Score neutre — totalGames = 0 après un set terminé mais lastSet = null → NEUTRAL_TRANSITION
    private val neutralScore = MatchScore()

    @BeforeEach
    fun setup() {
        inferenceEngine = mockk()
        coachingRepository = mockk()
        playerProfileRepository = mockk()
        sessionRepository = mockk()
        resolver = CoachingResolver(inferenceEngine, coachingRepository, playerProfileRepository, sessionRepository)

        // defaults pour éviter NPE
        coEvery { playerProfileRepository.buildMatchContextProfile() } returns MatchContextProfile(
            fftSeries = null, playStyle = null, activeWorkAxes = emptyList(), coachInstructions = emptyList()
        )
        coEvery { sessionRepository.getSessionById(any()) } returns null
        coEvery { coachingRepository.getCachedAdvice(any(), any()) } returns null
    }

    @Test
    fun `resolve returns null when match is over`() = runTest {
        val score = MatchScore(isMatchOver = true)
        assertNull(resolver.resolve(1L, score))
    }

    @Test
    fun `resolve returns GEMINI result when engine succeeds`() = runTest {
        coEvery { inferenceEngine.generate(any()) } returns AppResult.Success("Conseil Gemini")
        val result = resolver.resolve(1L, neutralScore)
        assertNotNull(result)
        assertEquals(CoachingSource.GEMINI, result!!.source)
        assertEquals("Conseil Gemini", result.text)
    }

    @Test
    fun `resolve falls back to CACHE when GeminiNano fails`() = runTest {
        coEvery { inferenceEngine.generate(any()) } returns AppResult.Error(RuntimeException("LLM error"))
        val cached = CoachingCacheEntry(matchId = 1L, pattern = MatchPattern.NEUTRAL_TRANSITION, content = "Conseil cache", generatedAt = 0L)
        coEvery { coachingRepository.getCachedAdvice(1L, MatchPattern.NEUTRAL_TRANSITION) } returns cached
        val result = resolver.resolve(1L, neutralScore)
        assertEquals(CoachingSource.CACHE, result!!.source)
        assertEquals("Conseil cache", result.text)
    }

    @Test
    fun `resolve falls back to STATIC when both fail`() = runTest {
        coEvery { inferenceEngine.generate(any()) } returns AppResult.Error(RuntimeException())
        coEvery { coachingRepository.getCachedAdvice(any(), any()) } returns null
        val result = resolver.resolve(1L, neutralScore)
        assertEquals(CoachingSource.STATIC, result!!.source)
        assertNotNull(result.text)
        assertTrue(result.text.isNotBlank())
    }
}
```

> **⚠️ `MatchContextProfile` constructor** — vérifier l'existence de ce constructeur dans le code réel avant de l'utiliser dans le test. Si le constructeur diffère, adapter. Utiliser `mockk()` pour `MatchContextProfile` si besoin.

#### ⚠️ Imports pour `CoachingCachePrefetcher` (UPDATE)

Le fichier existant n'importe pas `MatchScore`, `MatchStateSnapshot`, `CoachingPatternDetector`. Les ajouter :
```kotlin
import com.secondserve.domain.engine.CoachingPatternDetector
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.model.MatchStateSnapshot
```

### Adresse les deferred items de Story 3.3

| Item déféré | Résolution dans Story 3.4 |
|-------------|---------------------------|
| `markMatchEntriesStale()` jamais appelé | Appelé dans `refreshPostChangeover()` — CR-5 |
| `detect()` non gardé pour `isMatchOver=true` | `CoachingResolver.resolve()` retourne `null` si `isMatchOver` — CR-4 |

### Patterns à réutiliser

| Pattern | Source |
|---------|--------|
| `@Singleton @Inject constructor` | `CoachingCachePrefetcher.kt` — même scope |
| `CancellationException` re-throw | `GeminiNanoEngine.kt` (Story 3.2) |
| `withTimeout(3000L)` | NFR-P1 architecture |
| `MutableSharedFlow(extraBufferCapacity=1)` | `DataLayerEventBus` existant |
| `viewModelScope.launch { bus.flow.collect { } }` | `MatchViewModel.init {}` existant (closeSessionRequests) |
| `intent { reduce { state.copy(...) } }` | Orbit MVI — pattern MatchViewModel existant |
| `Timber.d("...")` — jamais `Log.*` | Tout le codebase |

### Structure fichiers finale

```
android/
├── domain/
│   └── src/main/kotlin/com/secondserve/domain/
│       ├── model/
│       │   └── CoachingResult.kt              ← NEW (CoachingResult + CoachingSource)
│       └── event/
│           └── DataLayerEventBus.kt           ← UPDATE (+ gameOverEvents + emitGameOver)
│
├── data/
│   └── src/main/kotlin/com/secondserve/data/wearable/
│       └── DataLayerListener.kt               ← UPDATE (handleGameOver → emitGameOver)
│
└── feature/match/
    └── src/
        ├── main/kotlin/com/secondserve/feature/match/
        │   ├── CoachingResolver.kt             ← NEW
        │   ├── CoachingCachePrefetcher.kt      ← UPDATE (+ refreshPostChangeover + getProbablePatterns)
        │   ├── MatchViewModel.kt               ← UPDATE (+ CoachingResolver, gameOverEvents collect, MatchUiState.coachingAdvice)
        │   └── MatchScreen.kt                  ← UPDATE (+ CoachingCard)
        └── test/kotlin/com/secondserve/feature/match/
            ├── CoachingResolverTest.kt         ← NEW
            └── MatchViewModelTest.kt           ← UPDATE (+ coachingResolver mock + 2 tests)
```

### Pas de changement de `build.gradle.kts`

`:feature:match/build.gradle.kts` a déjà `:core:ai` et `:domain` comme dépendances (ajoutés en Story 3.3). Aucune modification de dépendance requise.

### VPS — Aucun changement

Story 3.4 est 100% Android. Le backend VPS n'est pas modifié.

### Références

- [Source: epics.md § Story 3.4] — User story et ACs complets
- [Source: architecture.md § Architecture OfflineCoachingCache] — CoachingResolver = point de contrôle unique
- [Source: architecture.md § Cross-Cutting Concern #4] — chaîne GeminiNano→Cache→Static
- [Source: architecture.md § DataLayer] — PATH_GAME_OVER schéma + PATH_COACHING_RESULT (futur)
- [Source: architecture.md § NFR-P1] — ≤ 3s garanti par pré-calcul + withTimeout
- [Source: 3-3-offlinecoachingcache-init-match-detection-de-pattern.md § Review Findings] — items déférés adressés ici
- [Source: DataLayerListener.kt] — pattern existant `handleGameOver` + `EntryPointAccessors`
- [Source: DataLayerEventBus.kt] — pattern `MutableSharedFlow(extraBufferCapacity=1)`
- [Source: MatchViewModel.kt] — pattern collect + Orbit intent/reduce existant
- [Source: MatchViewModelTest.kt] — structure test existante (JUnit5, MockK, UnconfinedTestDispatcher)

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

Aucun blocage rencontré. Toutes les signatures de méthodes (CoachingRepository, PlayerProfileRepository, SessionRepository) correspondaient exactement aux specs.

### Completion Notes List

- **CR-1** : `CoachingResult.kt` + `CoachingSource` enum créés dans `:domain:model`.
- **CR-2** : `DataLayerEventBus` étendu avec `gameOverEvents: SharedFlow<MatchScore>` + `emitGameOver()`. Pattern identique à `closeSessionRequests`.
- **CR-3** : `DataLayerListener.handleGameOver()` émet désormais le score sur le bus après `updateScore`. L'appel `emitGameOver` est non-suspending (`tryEmit`), pas besoin de `NonCancellable`.
- **CR-4** : `CoachingResolver` créé avec chaîne GEMINI→CACHE→STATIC, guard `isMatchOver`, `withTimeout(3000L)`, `TimeoutCancellationException` catchée avant `CancellationException`.
- **CR-5** : `CoachingCachePrefetcher` étendu avec `refreshPostChangeover()` (fire-and-forget via `prefetchScope`) + `getProbablePatterns()` (7 patterns distincts). Trois imports ajoutés.
- **CR-6** : `MatchViewModel` injecte `CoachingResolver`, collecte `gameOverEvents` dans `init {}`, met à jour `coachingAdvice` dans l'état Orbit. `MatchUiState` étendu avec `coachingAdvice: CoachingResult? = null`.
- **CR-7** : `MatchScreen` affiche une `Card` Material3 (`primaryContainer`) quand `coachingAdvice != null`, entre le score et le `Spacer`.
- **CR-8** : `CoachingResolverTest` — 4 tests couvrant GEMINI/CACHE/STATIC/isMatchOver.
- **CR-9** : `MatchViewModelTest` étendu — `coachingResolver` ajouté au constructeur + 2 tests gameOver.
- **CR-10/11/12** : `:feature:match:test`, `:domain:test`, `:app:kspDebugKotlin/:app:kspReleaseKotlin` → BUILD SUCCESSFUL, aucune régression.

### File List

- `android/domain/src/main/kotlin/com/secondserve/domain/model/CoachingResult.kt` (NEW)
- `android/domain/src/main/kotlin/com/secondserve/domain/event/DataLayerEventBus.kt` (MODIFIED)
- `android/data/src/main/kotlin/com/secondserve/data/wearable/DataLayerListener.kt` (MODIFIED)
- `android/feature/match/src/main/kotlin/com/secondserve/feature/match/CoachingResolver.kt` (NEW)
- `android/feature/match/src/main/kotlin/com/secondserve/feature/match/CoachingCachePrefetcher.kt` (MODIFIED)
- `android/feature/match/src/main/kotlin/com/secondserve/feature/match/MatchViewModel.kt` (MODIFIED)
- `android/feature/match/src/main/kotlin/com/secondserve/feature/match/MatchScreen.kt` (MODIFIED)
- `android/feature/match/src/test/kotlin/com/secondserve/feature/match/CoachingResolverTest.kt` (NEW)
- `android/feature/match/src/test/kotlin/com/secondserve/feature/match/MatchViewModelTest.kt` (MODIFIED)

## Change Log

- 2026-06-22 : Création story 3.4 — CoachingResolver & affichage conseil sur téléphone.
- 2026-06-22 : Implémentation complète — 9 fichiers créés/modifiés, 6 tâches CR-1→CR-7 impl., tests CR-8/CR-9 créés, CI locale verte (tests + Hilt).
