---
baseline_commit: 8304fb8
---

# Story 4.2: Statistiques agrégées

Status: done

## Story

As a player,
I want to see my aggregated match stats (win rate, surface breakdown, streaks),
So that I can track my progression at a glance without needing to count manually.

## Acceptance Criteria

1. **Given** je suis sur l'écran Statistiques  
   **When** les stats se chargent  
   **Then** le win rate global est affiché (Sessions Match clôturées avec résultat VICTORY ou DEFEAT uniquement)  
   **And** si aucun match clôturé avec résultat : afficher "Aucun match terminé"

2. **And** le win rate par surface est affiché pour chaque surface jouée (≥ 1 match scoré)  
   — si ≥ 3 matchs scorés sur la surface : afficher le pourcentage  
   — si < 3 matchs scorés : afficher "Données insuffisantes"

3. **And** le nombre de Sessions par type est affiché : "Matchs : X | Entraînements : Y" (toutes sessions confondues, tous statuts)

4. **And** la séquence active de victoires ou de défaites consécutives est affichée  
   — si aucune séquence détectable : afficher "Aucune séquence"

5. **When** une nouvelle Session Match est clôturée  
   **Then** toutes les stats se recalculent automatiquement (réactivité via Flow)

6. **And** les statistiques sont consultables hors connexion (calculées depuis Room — NFR-OFF2)

7. **And** les stats incluent les Sessions saisies manuellement (Story 4.3) sans aucune modification supplémentaire (automatique via `getAllSessions()`)

## Tasks / Subtasks

- [x] **T1 — Modèles de données stats** (AC: 1, 2, 4)
  - [x] T1.1 Créer `AggregatedStats.kt` dans `:feature:history` : data classes `AggregatedStats`, `SurfaceWinRate`, `ActiveStreak` (sealed class)
  - [x] T1.2 Créer `StatsComputer.kt` dans `:feature:history` : fonction pure `fun computeStats(sessions: List<Session>): AggregatedStats`

- [x] **T2 — `StatsUiState` & `StatsViewModel`** (AC: 1–6)
  - [x] T2.1 Créer `StatsUiState.kt` (sealed class : Loading, Content, Error) dans `:feature:history`
  - [x] T2.2 Créer `StatsViewModel.kt` (Orbit MVI, collecte `getAllSessions()` Flow, appelle `computeStats()`)

- [x] **T3 — `StatsScreen`** (AC: 1–4)
  - [x] T3.1 Créer `StatsScreen.kt` dans `:feature:history` (Compose, `LazyColumn` avec sections win rate global, par surface, séquence, compteurs par type)
  - [x] T3.2 État `Loading` → `CircularProgressIndicator()`, état `Error` → message texte

- [x] **T4 — Navigation & HomeScreen** (AC: 1)
  - [x] T4.1 Ajouter paramètre `onNavigateToStats: () -> Unit` dans `HomeScreen.kt` + bouton "Statistiques"
  - [x] T4.2 Ajouter route `"stats"` dans `AppNavGraph.kt` + import `StatsScreen`
  - [x] T4.3 Mettre à jour l'appel `HomeScreen(...)` dans `AppNavGraph.kt` avec le nouveau callback

- [x] **T5 — Tests unitaires** (AC: 1–4)
  - [x] T5.1 `StatsViewModelTest.kt` : état Loading → Content, win rate calculé, Error state
  - [x] T5.2 `StatsComputerTest.kt` : cas de bord du calcul de stats (0 sessions, surface < 3 matchs, streak)

### Review Findings

- [x] [Review][Patch] P0 — AC-4 : streak élargie à toutes sessions MATCH avec result in [VICTORY, DEFEAT] (statut INTERRUPTED inclus) — décision : utiliser `allWithResult` au lieu de `scored` dans `computeStreak` [StatsComputer.kt:33]
- [x] [Review][Patch] P1 — Exception dans `collect{}` non attrapée par `.catch` → ViewModel bloqué sur Loading [StatsViewModel.kt:24]
- [x] [Review][Patch] P2 — Streak non déterministe quand deux sessions ont le même `createdAt` [StatsComputer.kt:33]
- [x] [Review][Patch] P3 — Surface vide `""` produit une ligne avec label vide dans la card "Par surface" [StatsComputer.kt:21]
- [x] [Review][Patch] P4 — Troncature float : `toInt()` → `roundToInt()` pour l'affichage du win rate [StatsScreen.kt:86,107]
- [x] [Review][Patch] P5 — AC-3 : card Sessions affiche deux lignes `Text` séparées au lieu de `"Matchs : X | Entraînements : Y"` [StatsScreen.kt:130]
- [x] [Review][Patch] P6 — État Error : `Text` aligné en haut à gauche au lieu d'être centré comme Loading [StatsScreen.kt:68]
- [x] [Review][Defer] D1 — `computeStats()` appelée sur le thread principal — borné par NFR-P3 (<200 sessions) [StatsViewModel.kt:23] — deferred, bounded by spec
- [x] [Review][Defer] D2 — Normalisation de casse des surfaces (ex. "Clay" vs "clay") — problème de qualité de données pré-existant [StatsComputer.kt] — deferred, pre-existing
- [x] [Review][Defer] D3 — Pas de mécanisme retry après Error (Flow terminé) — hors scope MVP, pas dans les AC [StatsViewModel.kt+StatsScreen.kt] — deferred, pre-existing
- [x] [Review][Defer] D4 — Message d'exception brut exposé dans l'UI (`e.message`) — pattern cohérent avec le reste du projet [StatsViewModel.kt:22] — deferred, pre-existing
- [x] [Review][Defer] D5 — `StatsViewModelTest` accède à `container.stateFlow` directement — fragilité potentielle sur changement Orbit [StatsViewModelTest.kt] — deferred, pre-existing
- [x] [Review][Defer] D6 — Route `"stats"` en magic string définie à 3 endroits — pattern pré-existant du projet [AppNavGraph.kt] — deferred, pre-existing
- [x] [Review][Defer] D7 — `computeStreak` accepte `List<Session>` non filtrée — précondition non enforced par le type [StatsComputer.kt:48] — deferred, pre-existing

---

## Dev Notes

### Modèles de données — à créer dans `:feature:history`

Ces types sont propres à la couche présentation ; ils **ne vont pas dans `:domain`**.

**`AggregatedStats.kt`** :
```kotlin
package com.secondserve.feature.history

data class AggregatedStats(
    val totalMatchSessions: Int,        // toutes sessions MATCH (tous statuts)
    val totalTrainingSessions: Int,     // toutes sessions TRAINING (tous statuts)
    val completedMatchSessions: Int,    // MATCH + COMPLETED + result in [VICTORY, DEFEAT]
    val victories: Int,
    val defeats: Int,
    val winRateGlobal: Float?,          // null si completedMatchSessions == 0
    val winRateBySurface: List<SurfaceWinRate>,  // toutes surfaces avec ≥ 1 match scoré
    val activeStreak: ActiveStreak?
)

data class SurfaceWinRate(
    val surface: String,
    val matchCount: Int,    // victories + defeats (matchs scorés uniquement)
    val victories: Int,
    val winRate: Float?     // null si matchCount < 3 → "Données insuffisantes"
)

sealed class ActiveStreak {
    data class Victories(val count: Int) : ActiveStreak()
    data class Defeats(val count: Int) : ActiveStreak()
}
```

### Calcul des stats — `StatsComputer.kt`

Fonction pure, testable sans dépendance Android. Créer dans `:feature:history` :

```kotlin
package com.secondserve.feature.history

import com.secondserve.domain.model.Session
import com.secondserve.domain.model.SessionStatus
import com.secondserve.domain.model.SessionType

internal fun computeStats(sessions: List<Session>): AggregatedStats {
    val allMatch = sessions.filter { it.sessionType == SessionType.MATCH }
    val allTraining = sessions.filter { it.sessionType == SessionType.TRAINING }

    // Matchs scorés : MATCH + COMPLETED + result in [VICTORY, DEFEAT]
    val scored = allMatch.filter {
        it.status == SessionStatus.COMPLETED && it.result in listOf("VICTORY", "DEFEAT")
    }
    val victories = scored.count { it.result == "VICTORY" }
    val defeats = scored.count { it.result == "DEFEAT" }

    val winRateGlobal = if (scored.isEmpty()) null
                        else victories.toFloat() / scored.size

    // Win rate par surface — toutes surfaces avec ≥ 1 match scoré
    val bySurface = scored
        .groupBy { it.surface }
        .map { (surface, list) ->
            val v = list.count { it.result == "VICTORY" }
            SurfaceWinRate(
                surface = surface,
                matchCount = list.size,
                victories = v,
                winRate = if (list.size >= 3) v.toFloat() / list.size else null
            )
        }
        .sortedByDescending { it.matchCount }

    // Séquence active : sessions scorées triées par date décroissante
    val sortedScored = scored.sortedByDescending { it.createdAt }
    val streak = computeStreak(sortedScored)

    return AggregatedStats(
        totalMatchSessions = allMatch.size,
        totalTrainingSessions = allTraining.size,
        completedMatchSessions = scored.size,
        victories = victories,
        defeats = defeats,
        winRateGlobal = winRateGlobal,
        winRateBySurface = bySurface,
        activeStreak = streak
    )
}

private fun computeStreak(sortedSessions: List<Session>): ActiveStreak? {
    if (sortedSessions.isEmpty()) return null
    val firstResult = sortedSessions.first().result ?: return null
    val count = sortedSessions.takeWhile { it.result == firstResult }.size
    return when (firstResult) {
        "VICTORY" -> ActiveStreak.Victories(count)
        "DEFEAT" -> ActiveStreak.Defeats(count)
        else -> null
    }
}
```

### T2 — `StatsUiState` & `StatsViewModel`

**`StatsUiState.kt`** :
```kotlin
package com.secondserve.feature.history

sealed class StatsUiState {
    object Loading : StatsUiState()
    data class Content(val stats: AggregatedStats) : StatsUiState()
    data class Error(val message: String) : StatsUiState()
}
```

**`StatsViewModel.kt`** — pattern identique à `HistoryViewModel` (anti-pattern prescrit : `viewModelScope.launch` externe + `intent {}` interne) :
```kotlin
package com.secondserve.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondserve.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel(), ContainerHost<StatsUiState, Nothing> {

    override val container = container<StatsUiState, Nothing>(StatsUiState.Loading)

    init {
        viewModelScope.launch {
            sessionRepository.getAllSessions()
                .catch { e -> intent { reduce { StatsUiState.Error(e.message ?: "Erreur de chargement") } } }
                .collect { sessions ->
                    val stats = computeStats(sessions)
                    intent { reduce { StatsUiState.Content(stats) } }
                }
        }
    }
}
```

⚠️ `Nothing` comme type SideEffect — cet écran n'a pas de navigation secondaire depuis les stats.

### T3 — `StatsScreen.kt`

Structure de l'écran (Compose) :
- `Scaffold` avec `TopAppBar` ("Statistiques") et bouton retour `TextButton("← Retour")`
- État Loading → `CircularProgressIndicator()` centré
- État Error → `Text(message)`
- État Content → `LazyColumn` avec sections Card :

```kotlin
package com.secondserve.feature.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.orbitmvi.orbit.compose.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val state by viewModel.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistiques") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("← Retour") }
                }
            )
        }
    ) { padding ->
        when (val s = state) {
            is StatsUiState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is StatsUiState.Error -> Text(
                text = s.message,
                modifier = Modifier.padding(padding).padding(16.dp)
            )

            is StatsUiState.Content -> StatsContent(stats = s.stats, modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun StatsContent(stats: AggregatedStats, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Section 1: Win rate global
        item {
            StatsCard(title = "Win rate global") {
                if (stats.winRateGlobal != null) {
                    Text("${stats.victories} victoires / ${stats.completedMatchSessions} matchs")
                    Text("${(stats.winRateGlobal * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium)
                } else {
                    Text("Aucun match terminé")
                }
            }
        }

        // Section 2: Par surface
        item {
            StatsCard(title = "Par surface") {
                if (stats.winRateBySurface.isEmpty()) {
                    Text("Aucune donnée")
                } else {
                    stats.winRateBySurface.forEach { surfaceStat ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(surfaceStat.surface)
                            if (surfaceStat.winRate != null) {
                                Text("${(surfaceStat.winRate * 100).toInt()}% (${surfaceStat.matchCount} matchs)")
                            } else {
                                Text("Données insuffisantes")
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }

        // Section 3: Séquence active
        item {
            StatsCard(title = "Séquence active") {
                when (val streak = stats.activeStreak) {
                    is ActiveStreak.Victories -> Text("${streak.count} victoire(s) consécutive(s)")
                    is ActiveStreak.Defeats -> Text("${streak.count} défaite(s) consécutive(s)")
                    null -> Text("Aucune séquence")
                }
            }
        }

        // Section 4: Sessions par type
        item {
            StatsCard(title = "Sessions") {
                Text("Matchs : ${stats.totalMatchSessions}")
                Text("Entraînements : ${stats.totalTrainingSessions}")
            }
        }
    }
}

@Composable
private fun StatsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
```

### T4 — Navigation

**`HomeScreen.kt`** — ajouter le paramètre et le bouton :
```kotlin
// Ajouter paramètre
fun HomeScreen(
    onNavigateToNewMatch: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToStats: () -> Unit   // ← nouveau
)

// Ajouter bouton après "Historique" :
Spacer(modifier = Modifier.height(12.dp))
OutlinedButton(
    onClick = onNavigateToStats,
    modifier = Modifier.fillMaxWidth()
) {
    Text("Statistiques")
}
```

**`AppNavGraph.kt`** — ajouter import et route :
```kotlin
import com.secondserve.feature.history.StatsScreen

// Dans HomeScreen composable :
composable("home") {
    HomeScreen(
        onNavigateToNewMatch = { navController.navigate("new_match") },
        onNavigateToProfile = { navController.navigate("profile") },
        onNavigateToHistory = { navController.navigate("history") },
        onNavigateToStats = { navController.navigate("stats") }   // ← nouveau
    )
}

// Nouvelle route :
composable("stats") {
    StatsScreen(onNavigateBack = { navController.popBackStack() })
}
```

### T5 — Tests unitaires

**Framework** : JUnit5 + MockK + `coroutines-test` (UnconfinedTestDispatcher) — identique à `HistoryViewModelTest`.

**`StatsComputerTest.kt`** — test de la logique pure (pas de ViewModel, pas de MockK) :
```kotlin
package com.secondserve.feature.history

// Helpers
private fun fakeSession(
    id: Long,
    sessionType: SessionType = SessionType.MATCH,
    status: SessionStatus = SessionStatus.COMPLETED,
    result: String? = "VICTORY",
    surface: String = "Clay",
    createdAt: Long = System.currentTimeMillis()
) = Session(id = id, surface = surface, format = SessionFormat(MatchFormat.BEST_OF_3, ThirdSetRule.FULL_ADVANTAGE),
            sessionType = sessionType, status = status, result = result,
            createdAt = createdAt, updatedAt = createdAt)

@Test
fun `empty sessions → zero counts, null win rate, null streak`() {
    val stats = computeStats(emptyList())
    assertEquals(0, stats.totalMatchSessions)
    assertNull(stats.winRateGlobal)
    assertNull(stats.activeStreak)
    assertTrue(stats.winRateBySurface.isEmpty())
}

@Test
fun `3 victories on Clay → win rate 100%, surface shown, streak Victories(3)`() {
    val sessions = listOf(
        fakeSession(1, result = "VICTORY", surface = "Clay", createdAt = 3000L),
        fakeSession(2, result = "VICTORY", surface = "Clay", createdAt = 2000L),
        fakeSession(3, result = "VICTORY", surface = "Clay", createdAt = 1000L)
    )
    val stats = computeStats(sessions)
    assertEquals(1.0f, stats.winRateGlobal)
    assertEquals(1, stats.winRateBySurface.size)
    assertEquals(1.0f, stats.winRateBySurface[0].winRate)
    assertTrue(stats.activeStreak is ActiveStreak.Victories)
    assertEquals(3, (stats.activeStreak as ActiveStreak.Victories).count)
}

@Test
fun `2 matches on surface → winRate null (Données insuffisantes)`() {
    val sessions = listOf(
        fakeSession(1, result = "VICTORY", surface = "Hard"),
        fakeSession(2, result = "DEFEAT", surface = "Hard")
    )
    val stats = computeStats(sessions)
    assertEquals(1, stats.winRateBySurface.size)
    assertNull(stats.winRateBySurface[0].winRate)
}

@Test
fun `streak breaks correctly — 1 defeat after 2 victories → streak Defeats(1)`() {
    val sessions = listOf(
        fakeSession(1, result = "DEFEAT", createdAt = 3000L),   // most recent
        fakeSession(2, result = "VICTORY", createdAt = 2000L),
        fakeSession(3, result = "VICTORY", createdAt = 1000L)
    )
    val stats = computeStats(sessions)
    assertTrue(stats.activeStreak is ActiveStreak.Defeats)
    assertEquals(1, (stats.activeStreak as ActiveStreak.Defeats).count)
}

@Test
fun `TRAINING sessions counted in totalTrainingSessions but not in win rate`() {
    val sessions = listOf(
        fakeSession(1, sessionType = SessionType.TRAINING, result = null),
        fakeSession(2, result = "VICTORY")
    )
    val stats = computeStats(sessions)
    assertEquals(1, stats.totalTrainingSessions)
    assertEquals(1, stats.totalMatchSessions)
    assertEquals(1, stats.completedMatchSessions)
}

@Test
fun `DRAW and ABANDONED not counted in win rate`() {
    val sessions = listOf(
        fakeSession(1, result = "DRAW"),
        fakeSession(2, result = "ABANDONED"),
        fakeSession(3, result = "VICTORY")
    )
    val stats = computeStats(sessions)
    assertEquals(1, stats.completedMatchSessions)
    assertEquals(1.0f, stats.winRateGlobal)
}
```

**`StatsViewModelTest.kt`** — pattern identique à `HistoryViewModelTest` :
```kotlin
@Test
fun `initial Loading then Content when flow emits`() = runTest {
    val sessionsFlow = MutableStateFlow(listOf(fakeSession(1)))
    every { sessionRepository.getAllSessions() } returns sessionsFlow

    val vm = StatsViewModel(sessionRepository)
    val state = vm.container.stateFlow.first { it is StatsUiState.Content }
    assertTrue(state is StatsUiState.Content)
}

@Test
fun `Error state when flow throws`() = runTest {
    every { sessionRepository.getAllSessions() } returns flow { throw RuntimeException("DB") }

    val vm = StatsViewModel(sessionRepository)
    val state = vm.container.stateFlow.first { it is StatsUiState.Error }
    assertTrue(state is StatsUiState.Error)
}

@Test
fun `stats recalculate automatically when flow emits new list`() = runTest {
    val sessionsFlow = MutableStateFlow(emptyList<Session>())
    every { sessionRepository.getAllSessions() } returns sessionsFlow

    val vm = StatsViewModel(sessionRepository)
    vm.container.stateFlow.first { it is StatsUiState.Content }

    sessionsFlow.value = listOf(fakeSession(1))
    val state = vm.container.stateFlow.first { it is StatsUiState.Content && it.stats.totalMatchSessions == 1 }
    assertEquals(1, (state as StatsUiState.Content).stats.totalMatchSessions)
}
```

### Guardrails critiques

- ❌ **Pas de nouvelles requêtes Room dans `SessionDao`** — in-memory via `getAllSessions()` Flow est suffisant pour < 200 sessions (NFR-P3)
- ❌ **`AggregatedStats`, `SurfaceWinRate`, `ActiveStreak` ne vont pas dans `:domain`** — types de présentation dans `:feature:history`
- ❌ **Pas de `UseCase` séparé pour les stats** — logique dans `StatsComputer.kt` appelée directement par le ViewModel
- ✅ **`computeStats()` est une fonction pure** — testable sans dépendance Android
- ✅ **Anti-pattern Orbit MVI prescrit** : `viewModelScope.launch` + `intent {}` interne (identique à `HistoryViewModel`)
- ❌ **`Nothing` comme SideEffect** dans `StatsViewModel` — aucune navigation secondaire depuis cet écran
- ❌ **Pas de `Log.*`** — uniquement `Timber.d()` / `Timber.e()` si besoin
- ❌ **Pas de `Dispatchers.IO` dans les tests** — toujours `UnconfinedTestDispatcher`
- ✅ **Win rate = victoires / (victoires + défaites)** — DRAW et ABANDONED exclus du calcul
- ✅ **Streak calculée sur sessions triées `createdAt DESC`** — `getAllSessions()` retourne déjà en DESC, mais `computeStats()` re-trie explicitement pour garantir l'ordre

### Fichiers à NE PAS modifier

- `SessionRepository.kt` — pas de nouvelle méthode nécessaire
- `SessionDao.kt` — pas de nouvelle query SQL
- `SecondServeDatabase.kt` — pas de migration (aucun changement de schéma)
- `SessionEntity.kt`, `Session.kt` — aucun nouveau champ
- `HistoryViewModel.kt`, `HistoryScreen.kt` — aucun impact

### Architecture — modules concernés

| Module | Fichiers créés | Fichiers modifiés |
|---|---|---|
| `:feature:history` | `AggregatedStats.kt`, `StatsComputer.kt`, `StatsUiState.kt`, `StatsViewModel.kt`, `StatsScreen.kt`, `StatsViewModelTest.kt`, `StatsComputerTest.kt` | — |
| `:app` | — | `HomeScreen.kt`, `navigation/AppNavGraph.kt` |

**Aucune modification de `build.gradle.kts`** — le module `:feature:history` a déjà toutes les dépendances nécessaires (Orbit, Hilt, Compose, JUnit5, MockK, coroutines-test) depuis la story 4.1.

### Project Structure Notes

```
android/
├── feature/history/
│   └── src/main/kotlin/com/secondserve/feature/history/
│       ├── AggregatedStats.kt           # CREATE (types de données stats)
│       ├── StatsComputer.kt             # CREATE (logique pure de calcul)
│       ├── StatsUiState.kt              # CREATE (sealed class états)
│       ├── StatsViewModel.kt            # CREATE (Orbit MVI)
│       └── StatsScreen.kt              # CREATE (Composable)
│   └── src/test/kotlin/com/secondserve/feature/history/
│       ├── StatsComputerTest.kt         # CREATE (tests logique pure)
│       └── StatsViewModelTest.kt        # CREATE (tests ViewModel)
│
└── app/
    └── src/main/kotlin/com/secondserve/
        ├── HomeScreen.kt                # UPDATE (+ onNavigateToStats + bouton)
        └── navigation/
            └── AppNavGraph.kt           # UPDATE (+ route "stats", + import StatsScreen)
```

### Learnings de Story 4.1 à appliquer

- **Import Orbit MVI** : utiliser `org.orbitmvi.orbit.viewmodel.container` (pas `org.orbitmvi.orbit.container`)
- **Smart cast cross-module** : utiliser `?.let {}` sur les champs nullable de `Session` au lieu de `if (x != null)`
- **Icons extended** : bibliothèque absente du projet — utiliser `TextButton("← Retour")` (déjà établi dans `HistoryScreen`, `SessionDetailScreen`)
- **Tests MockK** : `every { repo.getAllSessions() } returns flow { ... }` (pas `coEvery` — `getAllSessions()` n'est pas `suspend`)
- **`async` dans tests** : encapsuler dans `coroutineScope { }` si besoin pour éviter le warning deprecated

### References

- [Source: epics.md#Story 4.2] — AC complets, NFR-OFF2
- [Source: architecture.md#feature:history] — `StatsScreen.kt` dans `:feature:history`
- [Source: architecture.md#Data Architecture] — index `idx_sessions_surface`, in-memory pour < 200 sessions
- [Source: architecture.md#FR-8] — `StatsScreen`, `SessionDao` (index `idx_sessions_surface`) → in-memory via Flow
- [Source: 4-1-historique-des-sessions.md#Dev Notes] — patterns Orbit MVI, tests, imports
- [Source: 4-1-historique-des-sessions.md#Debug Log] — imports Orbit, smart cast, Icons extended

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

Aucun blocage — story straightforward avec tous les patterns établis en 4.1.

### Completion Notes List

- T1 : `AggregatedStats.kt` + `StatsComputer.kt` créés en `:feature:history`. Fonction pure sans dépendance Android, testable directement.
- T2 : `StatsUiState.kt` + `StatsViewModel.kt` — pattern anti-prescrit Orbit MVI identique à `HistoryViewModel`. SideEffect = `Nothing` (aucune navigation secondaire).
- T3 : `StatsScreen.kt` — 4 sections Card dans `LazyColumn` : win rate global, par surface, séquence active, compteurs par type. États Loading/Error/Content gérés.
- T4 : `HomeScreen.kt` étendu avec `onNavigateToStats` + bouton "Statistiques". `AppNavGraph.kt` mis à jour avec route `"stats"` et import `StatsScreen`.
- T5 : 9 tests `StatsComputerTest` (logique pure) + 4 tests `StatsViewModelTest` — tous verts. Régression zéro.
- AC1–7 satisfaits via `getAllSessions()` Flow réactif, Room offline, sans nouveau DAO.

### File List

- `android/feature/history/src/main/kotlin/com/secondserve/feature/history/AggregatedStats.kt` (créé)
- `android/feature/history/src/main/kotlin/com/secondserve/feature/history/StatsComputer.kt` (créé)
- `android/feature/history/src/main/kotlin/com/secondserve/feature/history/StatsUiState.kt` (créé)
- `android/feature/history/src/main/kotlin/com/secondserve/feature/history/StatsViewModel.kt` (créé)
- `android/feature/history/src/main/kotlin/com/secondserve/feature/history/StatsScreen.kt` (créé)
- `android/feature/history/src/test/kotlin/com/secondserve/feature/history/StatsComputerTest.kt` (créé)
- `android/feature/history/src/test/kotlin/com/secondserve/feature/history/StatsViewModelTest.kt` (créé)
- `android/app/src/main/kotlin/com/secondserve/HomeScreen.kt` (modifié)
- `android/app/src/main/kotlin/com/secondserve/navigation/AppNavGraph.kt` (modifié)
