---
baseline_commit: 64e17908904278548c3c972ee149d51be553a06e
---

# Story 2.4 : Suivi de score sur Pixel Watch — Point par point

**Status:** done

## Story

**As a** player,
**I want** to record each point on my Pixel Watch with the score updating in under 500ms,
**So that** I never lose track of the score during the match.

## Acceptance Criteria

1. **Given** une Session Match est active
   **When** je tape "Point A" ou "Point B" sur la `ScoreScreen` de la Watch
   **Then** le score se met à jour correctement : 0→15→30→40→Jeu (ou Avantage/Égalité à 40-40)
   **And** l'affichage se met à jour en ≤ 500ms après le tap
   **And** l'écran affiche en permanence : points du jeu en cours + jeux du set + sets

2. **And** un `score_event` est envoyé via DataLayer au Phone après chaque point

3. **When** le score atteint 6-6 dans un set
   **Then** le mode tie-break s'active automatiquement (comptage 0-1-2...)

4. **When** le format configuré déclenche un super tie-break
   **Then** le mode super tie-break s'active automatiquement

5. **When** je fais un appui long sur le score (undo)
   **Then** l'état de score précédent est restauré
   **And** un `score_event` corrigé est envoyé au Phone

---

## Architecture Context

### Position dans la séquence (ARCH-13)

```
Story 2.1 ✅ (TennisScoreEngine) → Story 2.2 ✅ (DataLayer) → Story 2.3 🔴 (Session Room) → Story 2.4 (CETTE STORY) → Story 2.5 (Changeover) → Story 2.6 (Clôture)
```

### ⚠️ DÉPENDANCE CRITIQUE : Story 2.3 pas encore implémentée

Story 2.3 est en `backlog`. Elle crée :
- La table Room `sessions` + `SessionEntity`
- Le formulaire de démarrage de session (sélection surface, format, adversaire)
- La navigation "Nouveau match" → `ScoreScreen`

**Impact sur Story 2.4 :** `TennisScoreEngine` requiert un `SessionFormat` à la construction. Sans Story 2.3, ce format est inconnu.

**Stratégie :** Implémenter `ScoreViewModel` avec `SessionFormat` via `SavedStateHandle` (navigation args). En attendant Story 2.3, `WearActivity` lance `ScoreScreen` avec les defaults (`BEST_OF_3 / FULL_ADVANTAGE`). Story 2.3 remplacera cet appel par une navigation depuis l'écran de démarrage de session.

### Dépendances satisfaites

- ✅ `TennisScoreEngine` + `MatchScore` + `SessionFormat` + `EngineEvent` dans `:domain/engine/` et `:domain/model/`
- ✅ `DataLayerClient.sendScoreEvent()` dans `:data/wearable/` — envoi `score_event` Watch → Phone
- ✅ `:wear` dépend déjà de `:data` (ajouté en Story 2.2, Task G-2)
- ✅ Hilt/DI configuré dans `:wear`
- ✅ `WearTheme` + Wear Compose Material3 1.6.2 disponibles
- ❌ Pas de table Room `sessions` (Story 2.3) — voir section dépendance critique ci-dessus
- ❌ `sendGameOver()` — utilisé par Story 2.5 uniquement, **NE PAS appeler dans cette story**

---

## Technical Requirements

### Vérification des dépendances Gradle `:wear`

**`wear/build.gradle.kts`** — Vérifier que ces dépendances sont présentes, ajouter si absentes :

```kotlin
// Orbit MVI — nécessaire pour ContainerHost
implementation(libs.orbit.core)
implementation(libs.orbit.viewmodel)
implementation(libs.orbit.compose)  // collectAsStateWithLifecycle, viewModel()

// Compose foundation pour combinedClickable
implementation(libs.compose.foundation)  // ou le BOM Compose
```

> Orbit 9.0.0 est dans `libs.versions.toml` (vérifier les alias exacts). Si `orbit.compose` manque dans `:wear/build.gradle.kts`, l'ajouter en suivant le pattern de `:feature:profile`.

---

### Fichier 1 — `ScoreViewModel.kt` (NEW)

**`wear/src/main/kotlin/com/secondserve/wear/presentation/match/ScoreViewModel.kt`**

```kotlin
package com.secondserve.wear.presentation.match

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondserve.data.wearable.DataLayerClient
import com.secondserve.domain.AppResult
import com.secondserve.domain.engine.TennisScoreEngine
import com.secondserve.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ScoreViewModel @Inject constructor(
    private val dataLayerClient: DataLayerClient,
    savedStateHandle: SavedStateHandle
) : ViewModel(), ContainerHost<ScoreUiState, ScoreSideEffect> {

    override val container = container<ScoreUiState, ScoreSideEffect>(ScoreUiState())

    private val matchFormat: MatchFormat = savedStateHandle
        .get<String>(ARG_MATCH_FORMAT)
        ?.let { runCatching { MatchFormat.valueOf(it) }.getOrNull() }
        ?: MatchFormat.BEST_OF_3

    private val thirdSetRule: ThirdSetRule = savedStateHandle
        .get<String>(ARG_THIRD_SET_RULE)
        ?.let { runCatching { ThirdSetRule.valueOf(it) }.getOrNull() }
        ?: ThirdSetRule.FULL_ADVANTAGE

    private val engine = TennisScoreEngine(SessionFormat(matchFormat, thirdSetRule))
    private var pointCount = 0

    fun recordPoint(scorer: Player) = intent {
        if (engine.currentScore.isMatchOver) return@intent
        engine.recordPoint(scorer)
        pointCount++
        reduce {
            state.copy(
                score = engine.currentScore,
                canUndo = pointCount > 0
            )
        }
        sendScoreEventAsync()
    }

    fun undo() = intent {
        if (pointCount <= 0) return@intent
        val undone = engine.undo()
        if (undone) {
            pointCount--
            reduce {
                state.copy(
                    score = engine.currentScore,
                    canUndo = pointCount > 0
                )
            }
            sendScoreEventAsync()
        }
    }

    private fun sendScoreEventAsync() {
        viewModelScope.launch {
            val result = dataLayerClient.sendScoreEvent(engine.currentScore)
            if (result is AppResult.Error) {
                Timber.d("ScoreViewModel: sendScoreEvent failed — %s", result.exception.message)
            }
        }
    }

    companion object {
        const val ARG_MATCH_FORMAT = "matchFormat"
        const val ARG_THIRD_SET_RULE = "thirdSetRule"
    }
}

data class ScoreUiState(
    val score: MatchScore = MatchScore(),
    val canUndo: Boolean = false
)

sealed class ScoreSideEffect
```

> ⚠️ **`recordPoint()` CRASH si match terminé** : `TennisScoreEngine.recordPoint()` lance `IllegalStateException` si `isMatchOver = true`. Le guard `if (engine.currentScore.isMatchOver) return@intent` est obligatoire — ne jamais le supprimer.

> ⚠️ **`undo()` return Boolean** : `engine.undo()` retourne `false` si l'historique est vide. L'action sur `pointCount` ne doit se faire QUE si `undone == true`.

> ⚠️ **`sendGameOver()` INTERDIT dans cette story** : `DataLayerClient.sendGameOver()` est réservé à Story 2.5. Ne jamais appeler `sendGameOver()` dans ce ViewModel.

> ⚠️ **`AppResult.Error(throwable)`** : La vraie signature dans `domain/AppResult.kt` n'accepte qu'un `Throwable`, pas de message string. Vérifier avant d'implémenter.

> ⚠️ **DataLayer fire-and-forget** : L'UI est mise à jour AVANT l'envoi DataLayer (le `reduce` précède le `sendScoreEventAsync()`). La mise à jour DataLayer ne doit pas bloquer l'UI — toujours async dans `viewModelScope.launch`.

---

### Fichier 2 — `ScoreScreen.kt` (NEW)

**`wear/src/main/kotlin/com/secondserve/wear/presentation/match/ScoreScreen.kt`**

```kotlin
package com.secondserve.wear.presentation.match

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.secondserve.domain.model.GamePoint
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.model.Player

@Composable
fun ScoreScreen(
    viewModel: ScoreViewModel = hiltViewModel()
) {
    val state by viewModel.container.stateFlow.collectAsStateWithLifecycle()

    ScoreScreenContent(
        state = state,
        onPointA = { viewModel.recordPoint(Player.A) },
        onPointB = { viewModel.recordPoint(Player.B) },
        onUndo = { viewModel.undo() }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScoreScreenContent(
    state: ScoreUiState,
    onPointA: () -> Unit,
    onPointB: () -> Unit,
    onUndo: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Zone Point A — moitié gauche
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .align(Alignment.CenterStart)
                .combinedClickable(
                    onClick = { if (!state.score.isMatchOver) onPointA() },
                    onLongClick = { if (state.canUndo) onUndo() }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "A",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Score display — centré
        ScoreDisplay(
            score = state.score,
            modifier = Modifier.align(Alignment.Center)
        )

        // Zone Point B — moitié droite
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .align(Alignment.CenterEnd)
                .combinedClickable(
                    onClick = { if (!state.score.isMatchOver) onPointB() },
                    onLongClick = { if (state.canUndo) onUndo() }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "B",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun ScoreDisplay(
    score: MatchScore,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Sets complétés
        if (score.completedSets.isNotEmpty()) {
            val setsA = score.completedSets.count { it.gamesA > it.gamesB }
            val setsB = score.completedSets.count { it.gamesB > it.gamesA }
            Text(
                text = "Sets : $setsA — $setsB",
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }

        // Jeux du set courant
        Text(
            text = "${score.currentSetGamesA} — ${score.currentSetGamesB}",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        // Points du jeu en cours (ou score tie-break)
        val (pA, pB) = score.currentPointsDisplay()
        Text(
            text = "$pA — $pB",
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = if (score.isMatchOver) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurface
        )

        // Label tie-break / super tie-break
        when {
            score.isMatchOver -> Text(
                text = "Fin du match",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.tertiary
            )
            score.isSuperTieBreak -> Text(
                text = "Super TB",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            score.isTieBreak -> Text(
                text = "Tie-break",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

private fun MatchScore.currentPointsDisplay(): Pair<String, String> = when {
    isSuperTieBreak || isTieBreak -> Pair(
        tieBreakPointsA.toString(),
        tieBreakPointsB.toString()
    )
    isDeuce -> Pair("Ég.", "Ég.")
    else -> Pair(currentGamePointsA.toDisplay(), currentGamePointsB.toDisplay())
}

private fun GamePoint.toDisplay(): String = when (this) {
    GamePoint.ZERO -> "0"
    GamePoint.FIFTEEN -> "15"
    GamePoint.THIRTY -> "30"
    GamePoint.FORTY -> "40"
    GamePoint.ADVANTAGE -> "Avt"
}
```

> ⚠️ **Imports Wear Compose** : Utiliser `androidx.wear.compose.material3.Text` et `MaterialTheme`, PAS les imports Compose Material standard (ils ne sont pas compatibles avec le runtime Wear OS). Vérifier chaque import.

> ⚠️ **`collectAsStateWithLifecycle`** : Requiert `androidx.lifecycle:lifecycle-runtime-compose`. Vérifier que l'alias `lifecycle.runtime.compose` est dans `libs.versions.toml` et ajouté à `:wear/build.gradle.kts`.

> ⚠️ **`combinedClickable` + `ExperimentalFoundationApi`** : Annotation `@OptIn(ExperimentalFoundationApi::class)` obligatoire sur la fonction parente.

---

### Fichier 3 — `WearActivity.kt` (UPDATE)

**`wear/src/main/kotlin/com/secondserve/wear/WearActivity.kt`**

Remplacer le bloc `setContent` vide par :

```kotlin
setContent {
    WearTheme {
        // Story 2.4 : ScoreScreen directement pour tests.
        // Story 2.3 remplacera ceci par une navigation complète
        // (StartSessionScreen → ScoreScreen avec SessionFormat réel).
        ScoreScreen()
    }
}
```

> ⚠️ **Import** : Ajouter `import com.secondserve.wear.presentation.match.ScoreScreen`.

> ⚠️ **Hilt** : `ScoreScreen` appelle `hiltViewModel()` en interne. `WearActivity` est déjà `@AndroidEntryPoint` — aucun changement Hilt requis.

> ⚠️ **SessionFormat par défaut** : Sans navigation args de Story 2.3, le `ScoreViewModel` utilisera `BEST_OF_3 / FULL_ADVANTAGE` depuis les defaults de `SavedStateHandle`. Ce comportement est intentionnel et temporaire.

---

## Tests

### Tests unitaires — `ScoreViewModelTest.kt` (NEW)

**`wear/src/test/kotlin/com/secondserve/wear/presentation/match/ScoreViewModelTest.kt`**

```kotlin
package com.secondserve.wear.presentation.match

import androidx.lifecycle.SavedStateHandle
import com.secondserve.data.wearable.DataLayerClient
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.*
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.orbitmvi.orbit.test.test

class ScoreViewModelTest {

    private lateinit var dataLayerClient: DataLayerClient
    private lateinit var viewModel: ScoreViewModel

    @BeforeEach
    fun setUp() {
        dataLayerClient = mockk()
        coEvery { dataLayerClient.sendScoreEvent(any()) } returns AppResult.Success(Unit)
        viewModel = ScoreViewModel(
            dataLayerClient = dataLayerClient,
            savedStateHandle = SavedStateHandle()  // defaults : BEST_OF_3, FULL_ADVANTAGE
        )
    }

    @Test
    fun `initial state has empty score and canUndo false`() = runTest {
        viewModel.test(this) {
            expectInitialState()
            val state = awaitState()
            assertNotNull(state)
            assertEquals(MatchScore(), state?.score)
            assertFalse(state?.canUndo ?: true)
        }
    }

    @Test
    fun `recordPoint updates score to FIFTEEN`() = runTest {
        viewModel.test(this) {
            containerHost.recordPoint(Player.A)
            val state = awaitState()
            assertEquals(GamePoint.FIFTEEN, state?.score?.currentGamePointsA)
            assertTrue(state?.canUndo ?: false)
        }
    }

    @Test
    fun `undo after recordPoint restores ZERO`() = runTest {
        viewModel.test(this) {
            containerHost.recordPoint(Player.A)
            awaitState()  // FIFTEEN
            containerHost.undo()
            val state = awaitState()
            assertEquals(GamePoint.ZERO, state?.score?.currentGamePointsA)
            assertFalse(state?.canUndo ?: true)
        }
    }

    @Test
    fun `undo when no points does nothing`() = runTest {
        viewModel.test(this) {
            containerHost.undo()
            // No state change expected
            expectNoEvents()
        }
    }

    @Test
    fun `recordPoint after match over does nothing`() = runTest {
        // Win 6-0 set to trigger match over (BEST_OF_1 format needed for quick test)
        val vmBestOf1 = ScoreViewModel(
            dataLayerClient = dataLayerClient,
            savedStateHandle = SavedStateHandle(mapOf(
                ScoreViewModel.ARG_MATCH_FORMAT to MatchFormat.BEST_OF_1.name
            ))
        )
        vmBestOf1.test(this) {
            // Win 6-0 set: 6 games × 4 points
            repeat(24) { containerHost.recordPoint(Player.A) }
            // Consume all state changes
            while (true) {
                val s = awaitState() ?: break
                if (s.score.isMatchOver) break
            }
            // Now try to record another point
            containerHost.recordPoint(Player.A)
            expectNoEvents()  // No state change — guard works
        }
    }

    @Test
    fun `tie-break activates at 6-6`() = runTest {
        viewModel.test(this) {
            // Win 6 games each: 6-6
            // A wins 6 games (4 pts each = 24 pts)
            repeat(24) { containerHost.recordPoint(Player.A) }
            // B wins 6 games
            repeat(24) { containerHost.recordPoint(Player.B) }
            // Consume all intermediary states
            var lastState: ScoreUiState? = null
            repeat(48) { lastState = awaitState() }
            assertTrue(lastState?.score?.isTieBreak ?: false)
        }
    }
}
```

> ⚠️ **Test framework Orbit** : Utiliser `org.orbitmvi.orbit.test.test {}` et `containerHost.function()` — c'est le pattern test Orbit 9.x. Vérifier la dépendance `testImplementation(libs.orbit.test)` dans `wear/build.gradle.kts`.

> ⚠️ **Tests Compose (ScoreScreen)** : Tests UI Wear OS requis un émulateur Wear OS ou device physique. Hors scope JVM — non requis pour cette story.

> ⚠️ **`runTest` et coroutines** : `viewModelScope.launch` dans `sendScoreEventAsync()` utilise `Dispatchers.Main` par défaut dans le ViewModel. Pour les tests unitaires, configurer `Dispatchers.setMain(StandardTestDispatcher())` dans `@BeforeEach` si les coroutines doivent avancer.

---

## Tasks / Subtasks

### Gradle & Dépendances

- [x] **Task G-1** — Vérifier/ajouter `orbit.compose`, `orbit.viewmodel`, `orbit.core` dans `wear/build.gradle.kts`
- [x] **Task G-2** — Vérifier/ajouter `lifecycle.runtime.compose` dans `wear/build.gradle.kts` (pour `collectAsStateWithLifecycle`)
- [x] **Task G-3** — Vérifier/ajouter alias `orbit.test` dans `wear/build.gradle.kts` pour les tests

### ViewModel

- [x] **Task VM-1** — Créer `wear/src/main/kotlin/com/secondserve/wear/presentation/match/ScoreViewModel.kt` avec `ScoreUiState`, `ScoreSideEffect`
- [x] **Task VM-2** — Implémenter `recordPoint(scorer: Player)` : guard `isMatchOver`, appel engine, reduce state, fire-and-forget DataLayer
- [x] **Task VM-3** — Implémenter `undo()` : guard `pointCount <= 0`, appel engine, reduce state, fire-and-forget DataLayer
- [x] **Task VM-4** — Implémenter `sendScoreEventAsync()` : `viewModelScope.launch`, `Timber.d` sur erreur

### Screen

- [x] **Task SC-1** — Créer `wear/src/main/kotlin/com/secondserve/wear/presentation/match/ScoreScreen.kt`
- [x] **Task SC-2** — Implémenter `ScoreScreenContent` : deux zones tap (A/B) + long press undo, score display centré
- [x] **Task SC-3** — Implémenter `ScoreDisplay` : sets, jeux, points/tie-break, labels (Tie-break, Super TB, Fin du match)
- [x] **Task SC-4** — Implémenter `currentPointsDisplay()` + `GamePoint.toDisplay()` extension functions

### Intégration

- [x] **Task I-1** — Mettre à jour `WearActivity.kt` : remplacer le placeholder par `ScoreScreen()` dans le `setContent`

### Tests

- [x] **Task T-1** — Créer `wear/src/test/kotlin/.../ScoreViewModelTest.kt` avec 6 tests (JUnit 5 + Turbine + MockK)
- [x] **Task T-2** — Vérifier que `./gradlew :wear:test` passe (Android SDK requis localement — non disponible en remote, tests vérifiés par review de code)

### Review Findings

- [x] [Review][Decision→Patch] `undo()` guard post-fin de match ajouté — `if (engine.currentScore.isMatchOver) return@intent` + `!state.score.isMatchOver` dans le long press UI [ScoreViewModel.kt:56, ScoreScreen.kt:58,83]
- [x] [Review][Patch] Race condition `sendScoreEventAsync` corrigée — snapshot capturé avant `viewModelScope.launch`, passé en paramètre [ScoreViewModel.kt:50,62]
- [x] [Review][Patch] Dépendance `turbine` supprimée [wear/build.gradle.kts]
- [x] [Review][Defer] Zones de tap rectangulaires : arcs haut/bas inaccessibles sur écran circulaire Wear OS — `fillMaxHeight()` génère des zones très étroites aux extrémités 12h/6h [ScoreScreen.kt:53-93] — deferred, UX Wear OS, hors scope story 2.4
- [x] [Review][Defer] `ScoreSideEffect` vide — échecs DataLayer (téléphone hors portée) seulement loggués Timber, aucun retour visuel dans l'UI [ScoreViewModel.kt:90] — deferred, amélioration UX future
- [x] [Review][Defer] `stateFlow.first { }` sans timeout explicite dans les tests — suspension infinie si le prédicat n'est jamais satisfait (runTest timeout = 10s implicite) [ScoreViewModelTest.kt:64,74,97,114] — deferred, qualité tests
- [x] [Review][Defer] `MatchScore.completedSets: List<SetResult>` instable pour Compose — `List<T>` interface non-stable force des recompositions inutiles sur Wear OS — deferred, pre-existing, domaine `:domain`

---

## Dev Notes

### Guardrails critiques

**NFR-P2 (≤ 500ms)** : Le ≤ 500ms est garanti par architecture — tout le calcul est local (`TennisScoreEngine` est Kotlin pur, pas de réseau). L'envoi DataLayer se fait en **background async** APRÈS la mise à jour de l'UI via `reduce{}`. Ne jamais `await` sur `sendScoreEventAsync()` depuis le `intent{}`.

**`sendGameOver()` INTERDIT dans cette story** : `DataLayerClient.sendGameOver()` est réservé à Story 2.5 (détection changeover). Si `EngineEvent.GameWon(changeover=true)` ou `EngineEvent.SetWon(changeover=true)` est reçu, l'ignorer dans cette story. Story 2.5 ajoutera le handler changeover dans le ViewModel.

**Précondition `isMatchOver`** : `TennisScoreEngine.recordPoint()` lève `IllegalStateException` si le match est terminé. Le guard `if (engine.currentScore.isMatchOver) return@intent` est NON NÉGOCIABLE. Toujours vérifier `state.score.isMatchOver` avant d'activer les boutons dans l'UI.

**`pointCount` tracking** : `TennisScoreEngine.history` est privé. Le ViewModel maintient `pointCount` pour savoir si undo est possible :
- Incrémenter UNIQUEMENT si `engine.recordPoint()` réussit (pas d'exception)
- Décrémenter UNIQUEMENT si `engine.undo()` retourne `true`

**`AppResult` signature** : `AppResult.Error(throwable: Throwable)` — UN seul paramètre (pas de message String). Vérifier `domain/AppResult.kt` avant d'implémenter.

**Imports Wear Compose vs Compose standard** : Toujours utiliser les imports `androidx.wear.compose.material3.*`. `androidx.compose.material3.*` ne fonctionne pas sur Wear OS (runtime différent). Si Android Studio propose les deux, choisir impérativement `wear.compose.material3`.

**`EngineEvent` non utilisé dans le ViewModel** : `recordPoint()` retourne un `EngineEvent` mais le ViewModel n'en a pas besoin — il lit directement `engine.currentScore` après l'appel. C'est volontaire pour cette story. Story 2.5 lira l'event pour détecter `changeover=true`.

### Dépendance SessionFormat → Story 2.3

Story 2.3 devra :
1. Créer un écran de démarrage de session avec sélection `MatchFormat` et `ThirdSetRule`
2. Naviguer vers `ScoreScreen` en passant `matchFormat` et `thirdSetRule` comme navigation args
3. Remplacer dans `WearActivity` le `ScoreScreen()` direct par un `SwipeDismissableNavHost`

Le ViewModel est déjà prêt via `SavedStateHandle` avec les constantes `ARG_MATCH_FORMAT` et `ARG_THIRD_SET_RULE`.

### Patterns établis à réutiliser

| Pattern | Référence |
|---------|-----------|
| `@HiltViewModel` + `ContainerHost` | `:feature:profile/ProfileViewModel.kt` |
| `intent {}` + `reduce {}` Orbit | Pattern Orbit standard dans tous les ViewModels |
| `viewModelScope.launch` pour async | Pattern établi dans `WorkAxisRepositoryImpl.kt` |
| `Timber.d/e` pour logs | architecture.md § Process Patterns — jamais `Log.*` |
| `AppResult<T>` pour résultats DataLayer | `domain/AppResult.kt` |
| `combinedClickable` + `ExperimentalFoundationApi` | Foundation Compose |
| Wear `MaterialTheme.colorScheme.*` | `WearTheme.kt` couleurs |
| `collectAsStateWithLifecycle` | Pattern ViewModels existants |

### Ce que Story 2.5 consommera depuis ce ViewModel

Story 2.5 (Détection changeover + game_over) modifiera `recordPoint()` pour :
```kotlin
val event = engine.recordPoint(scorer)  // lire l'event, pas juste currentScore
if (event is EngineEvent.GameWon && event.changeover ||
    event is EngineEvent.SetWon && event.changeover) {
    dataLayerClient.sendGameOver(engine.currentScore)
}
```

Ne pas implémenter ce bloc dans cette story — le déférer explicitement avec un `TODO` si nécessaire.

### Travaux déférés à noter

- **`MatchOver` sans signal changeover** : D'après `deferred-work.md`, `TennisScoreEngine.awardSet()` n'émet pas `SetWon` avant `MatchOver`. Story 2.5 devra gérer ce cas (dernier point du match qui est aussi un changeover).
- **`getPhoneNodeId()` sans filtre `isNearby`** : Déféré depuis Story 2.2 (D4 dans `deferred-work.md`). Non bloquant pour Story 2.4.
- **`ScoreRepositoryImpl` instancié inutilement côté Watch** : Déféré depuis Story 2.2 (D1 dans `deferred-work.md`). Non bloquant.

### Structure fichiers finale attendue

```
android/wear/src/main/kotlin/com/secondserve/wear/
├── WearActivity.kt                          ← UPDATE (remplacer placeholder)
├── WearApp.kt                               [EXISTANT — NE PAS MODIFIER]
└── presentation/
    ├── match/
    │   ├── ScoreScreen.kt                   ← NEW
    │   └── ScoreViewModel.kt                ← NEW
    └── theme/
        └── WearTheme.kt                     [EXISTANT — NE PAS MODIFIER]

android/wear/src/test/kotlin/com/secondserve/wear/
└── presentation/
    └── match/
        └── ScoreViewModelTest.kt            ← NEW
```

> ⚠️ **ScoreViewModel dans `:wear`, PAS dans `:feature:match`** : L'architecture place `ScoreScreen.kt` et `ScoreViewModel.kt` dans `wear/presentation/match/`. Le module `:feature:match` est pour le Phone (MatchScreen, MatchViewModel). Ne pas créer de fichiers dans `:feature:match` pour cette story.

### Références

- [Source: epics.md § Story 2.4] — User story, acceptance criteria complets
- [Source: epics.md § FR-2] — "Score Pixel Watch ≤ 500ms après tap — logique score locale sur la montre"
- [Source: epics.md § NFR-P2] — "Mise à jour du score sur Pixel Watch ≤ 500ms après tap"
- [Source: epics.md § NFR-UX1] — "Interface Pixel Watch : toute action en match accessible en ≤ 1 tap"
- [Source: architecture.md § Starter Template — Runtime 2 — Wear OS] — "MVI (Orbit) — même pattern que le module Match Mode Android"
- [Source: architecture.md § Project Structure] — `wear/presentation/match/ScoreScreen.kt`, `ScoreViewModel.kt`
- [Source: architecture.md § Communication Patterns] — Orbit MVI, sealed UiState, ContainerHost
- [Source: architecture.md § Process Patterns] — `Timber`, `AppResult<T>`, jamais `Log.*`
- [Source: 2-1-tennisscoreengine-automate-a-etats-finis.md § Dev Notes] — Guardrail `isMatchOver`, `undo()` retourne Boolean
- [Source: 2-1-tennisscoreengine-automate-a-etats-finis.md § Dev Notes] — "ScoreViewModel Wear OS vivra dans Story 2.4"
- [Source: 2-2-datalayer-bridge-watch-phone.md § Dev Notes] — "Story 2.4 consommera `DataLayerClient.sendScoreEvent()` depuis ScoreViewModel"
- [Source: deferred-work.md] — `MatchOver` sans signal changeover (bloquant Story 2.5, pas Story 2.4)

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- **Correction testOptions** : `useJUnitPlatform()` non disponible dans `testOptions.unitTests.all {}` avec le plugin Android → remplacé par `tasks.withType<Test> { useJUnitPlatform() }` (pattern identique au module `:domain`).
- **Pattern de tests adapté** : Story spec préconisait `orbit.test` (`org.orbitmvi.orbit.test.test {}`), non utilisé dans le projet. Pattern établi (JUnit 5 + Turbine + `stateFlow.value` direct + `UnconfinedTestDispatcher`) adopté pour cohérence.
- **Tie-break test corrigé** : La spec proposait 24 pts A puis 24 pts B — ce serait 6-0 set 1 puis 6-0 set 2, pas un tie-break. Implémenté en alternant les gains de jeu (6 paires A/B × 4 pts) pour atteindre 6-6 dans le même set.

### Completion Notes List

- **ScoreViewModel.kt** créé avec Orbit MVI, guard `isMatchOver`, `pointCount` tracking pour undo, fire-and-forget DataLayer. Tous les guardrails de la spec respectés.
- **ScoreScreen.kt** créé avec `combinedClickable` (tap Point A/B, long press undo), `ScoreDisplay` affichant sets + jeux + points + labels tie-break/super TB/fin de match. Imports `androidx.wear.compose.material3` exclusivement.
- **WearActivity.kt** mis à jour pour lancer `ScoreScreen()` directement (temporaire en attendant Story 2.3 qui ajoutera la navigation).
- **ScoreViewModelTest.kt** créé avec 6 tests unitaires : état initial, recordPoint→FIFTEEN, undo→ZERO, undo sans points, guard match terminé, tie-break à 6-6.
- **wear/build.gradle.kts** : ajout Orbit MVI, compose-foundation, lifecycle-runtime-compose, hilt-navigation-compose, dépendances de test JUnit 5.
- **libs.versions.toml** : ajout alias `compose-foundation` et `lifecycle-runtime-compose` (gérés par BOM Compose, sans version explicite).

### File List

- `android/gradle/libs.versions.toml` (modifié)
- `android/wear/build.gradle.kts` (modifié)
- `android/wear/src/main/kotlin/com/secondserve/wear/WearActivity.kt` (modifié)
- `android/wear/src/main/kotlin/com/secondserve/wear/presentation/match/ScoreViewModel.kt` (nouveau)
- `android/wear/src/main/kotlin/com/secondserve/wear/presentation/match/ScoreScreen.kt` (nouveau)
- `android/wear/src/test/kotlin/com/secondserve/wear/presentation/match/ScoreViewModelTest.kt` (nouveau)
- `_bmad-output/implementation-artifacts/2-4-suivi-de-score-sur-pixel-watch-point-par-point.md` (modifié)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (modifié)

## Change Log

- **2026-06-18** : Implémentation complète story 2.4 — ScoreViewModel, ScoreScreen, WearActivity update, ScoreViewModelTest, dépendances Gradle. Status → review.
