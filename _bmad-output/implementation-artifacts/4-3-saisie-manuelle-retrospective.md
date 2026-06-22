# Story 4.3: Saisie manuelle rétrospective

---
baseline_commit: afed98969a2d76711a700f60ac841f16c14a0b01
---

Status: review

## Story

As a player,
I want to add a match I played without the app by entering it manually,
So that my history and statistics are complete even for matches played before or without SecondServe.

## Acceptance Criteria

1. **Given** je suis sur l'écran Historique  
   **When** je tape "Ajouter un match passé"  
   **Then** un formulaire s'affiche avec les champs : surface (obligatoire), format (obligatoire), adversaire (optionnel), type de compétition (optionnel), tournoi (optionnel), score final en texte libre (ex : "6-3, 4-6, 7-5", optionnel), résultat (VICTORY/DEFEAT, obligatoire), date du match (obligatoire)

2. **When** je soumets le formulaire  
   **Then** la session apparaît dans l'historique (Story 4.1) avec les données saisies  
   **And** elle est incluse dans toutes les statistiques agrégées (Story 4.2) sans aucune modification supplémentaire

3. **And** la session est persistée en Room avec status=COMPLETED et mise en queue de Sync (SyncWorker)

4. **And** la saisie est accessible depuis l'écran Historique sans passer par le Mode Match

5. **And** aucune connexion réseau n'est requise pour créer une session rétrospective

## Tasks / Subtasks

- [x] **T1 — Étendre `SessionRepository` + `SessionRepositoryImpl`** (AC: 3)
  - [x] T1.1 Ajouter `suspend fun createCompletedSession(session: Session): AppResult<Session>` dans `SessionRepository.kt`
  - [x] T1.2 Implémenter dans `SessionRepositoryImpl.kt` : insert Room en transaction + entrée SyncQueue (identique à `closeSession`)

- [x] **T2 — `AddRetroSessionViewModel`** (AC: 1, 2, 3, 5)
  - [x] T2.1 Créer `AddRetroSessionViewModel.kt` dans `:feature:history` (Orbit MVI, SideEffect pour navigation)
  - [x] T2.2 Créer `AddRetroSessionUiState.kt` dans `:feature:history`

- [x] **T3 — `AddRetroSessionScreen`** (AC: 1, 4)
  - [x] T3.1 Créer `AddRetroSessionScreen.kt` dans `:feature:history` (Compose, formulaire défilable)
  - [x] T3.2 DatePickerDialog Material3 pour la date du match
  - [x] T3.3 FAB "Ajouter un match passé" dans `HistoryScreen.kt` → `onNavigateToAddRetroSession`

- [x] **T4 — Navigation** (AC: 4)
  - [x] T4.1 Ajouter `onNavigateToAddRetroSession: () -> Unit` dans `HistoryScreen.kt` + FAB
  - [x] T4.2 Ajouter route `"add_retro_session"` dans `AppNavGraph.kt`

- [x] **T5 — Tests unitaires** (AC: 2, 3, 5)
  - [x] T5.1 `AddRetroSessionViewModelTest.kt` : validation formulaire, soumission succès, soumission erreur

## Dev Notes

### T1 — Nouvelle méthode `createCompletedSession`

Cette méthode est nécessaire car `createSession()` n'enqueue PAS la sync (la session créée est ACTIVE). `closeSession()` enqueue la sync mais nécessite une session déjà existante. Pour une session rétrospective déjà COMPLETED à la création, il faut créer + enqueuer dans la même transaction.

**`SessionRepository.kt`** — ajouter après `createSession` :
```kotlin
suspend fun createCompletedSession(session: Session): AppResult<Session>
```

**`SessionRepositoryImpl.kt`** — implémenter :
```kotlin
override suspend fun createCompletedSession(session: Session): AppResult<Session> = try {
    val now = System.currentTimeMillis()
    database.withTransaction {
        val id = dao.insert(session.toEntity())
        syncQueueDao.insert(SyncQueueEntity(
            entityType = SyncQueueEntity.ENTITY_TYPE_SESSION,
            entityId = id,
            operation = SyncQueueEntity.OPERATION_UPSERT,
            createdAt = now
        ))
        AppResult.Success(session.copy(id = id))
    }
} catch (e: Exception) {
    Timber.e(e, "SessionRepository: createCompletedSession failed")
    AppResult.Error(e)
}
```

⚠️ La session passée en paramètre doit déjà avoir `status = SessionStatus.COMPLETED`, `result` renseigné, et `createdAt` = date saisie par l'utilisateur (epoch ms).

### T2 — `AddRetroSessionUiState` + `AddRetroSessionViewModel`

**`AddRetroSessionUiState.kt`** :
```kotlin
package com.secondserve.feature.history

import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.ThirdSetRule

data class AddRetroSessionUiState(
    val selectedSurface: String? = null,
    val selectedMatchFormat: MatchFormat? = null,
    val selectedThirdSetRule: ThirdSetRule? = null,
    val opponent: String = "",
    val competitionType: String = "",
    val tournament: String = "",
    val scoreText: String = "",
    val selectedResult: String? = null,       // "VICTORY" | "DEFEAT"
    val matchDateMillis: Long? = null,        // epoch ms sélectionné par DatePicker
    val isLoading: Boolean = false
) {
    val canSubmit: Boolean get() =
        selectedSurface != null &&
        selectedMatchFormat != null &&
        (selectedMatchFormat == MatchFormat.BEST_OF_1 || selectedThirdSetRule != null) &&
        selectedResult != null &&
        matchDateMillis != null
}

sealed class AddRetroSessionSideEffect {
    object SessionCreated : AddRetroSessionSideEffect()
    data class ShowError(val message: String) : AddRetroSessionSideEffect()
}
```

**`AddRetroSessionViewModel.kt`** — anti-pattern Orbit prescrit (`viewModelScope.launch` externe INTERDIT ici car action ponctuelle, utiliser `intent {}` directement) :
```kotlin
package com.secondserve.feature.history

import androidx.lifecycle.ViewModel
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.Session
import com.secondserve.domain.model.SessionFormat
import com.secondserve.domain.model.SessionStatus
import com.secondserve.domain.model.ThirdSetRule
import com.secondserve.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

@HiltViewModel
class AddRetroSessionViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel(), ContainerHost<AddRetroSessionUiState, AddRetroSessionSideEffect> {

    override val container = container<AddRetroSessionUiState, AddRetroSessionSideEffect>(
        AddRetroSessionUiState()
    )

    fun onSurfaceSelected(surface: String) = intent {
        reduce { state.copy(selectedSurface = surface) }
    }

    fun onMatchFormatSelected(format: MatchFormat) = intent {
        reduce {
            state.copy(
                selectedMatchFormat = format,
                selectedThirdSetRule = if (format == MatchFormat.BEST_OF_1) null
                                       else state.selectedThirdSetRule
            )
        }
    }

    fun onThirdSetRuleSelected(rule: ThirdSetRule) = intent {
        reduce { state.copy(selectedThirdSetRule = rule) }
    }

    fun onOpponentChanged(value: String) = intent {
        reduce { state.copy(opponent = value) }
    }

    fun onCompetitionTypeChanged(value: String) = intent {
        reduce { state.copy(competitionType = value) }
    }

    fun onTournamentChanged(value: String) = intent {
        reduce { state.copy(tournament = value) }
    }

    fun onScoreTextChanged(value: String) = intent {
        reduce { state.copy(scoreText = value) }
    }

    fun onResultSelected(result: String) = intent {
        reduce { state.copy(selectedResult = result) }
    }

    fun onMatchDateSelected(epochMillis: Long) = intent {
        reduce { state.copy(matchDateMillis = epochMillis) }
    }

    fun submit() = intent {
        val surface = state.selectedSurface ?: return@intent
        val matchFormat = state.selectedMatchFormat ?: return@intent
        val result = state.selectedResult ?: return@intent
        val dateMillis = state.matchDateMillis ?: return@intent

        val thirdSetRule = if (matchFormat == MatchFormat.BEST_OF_3)
            state.selectedThirdSetRule ?: ThirdSetRule.FULL_ADVANTAGE
        else ThirdSetRule.FULL_ADVANTAGE

        reduce { state.copy(isLoading = true) }

        val session = Session(
            surface = surface,
            format = SessionFormat(matchFormat = matchFormat, thirdSetRule = thirdSetRule),
            opponent = state.opponent.takeIf { it.isNotBlank() },
            competitionType = state.competitionType.takeIf { it.isNotBlank() },
            tournament = state.tournament.takeIf { it.isNotBlank() },
            status = SessionStatus.COMPLETED,
            result = result,
            scoreText = state.scoreText.takeIf { it.isNotBlank() },
            createdAt = dateMillis,
            updatedAt = System.currentTimeMillis()
        )

        when (sessionRepository.createCompletedSession(session)) {
            is AppResult.Success -> {
                reduce { state.copy(isLoading = false) }
                postSideEffect(AddRetroSessionSideEffect.SessionCreated)
            }
            is AppResult.Error -> {
                reduce { state.copy(isLoading = false) }
                postSideEffect(AddRetroSessionSideEffect.ShowError("Impossible d'enregistrer la session"))
            }
            AppResult.Loading -> {}
        }
    }
}
```

⚠️ Ici `intent {}` seul est correct (pas de `viewModelScope.launch` externe) car `submit()` est une action utilisateur ponctuelle, pas une collecte de Flow. `HistoryViewModel` et `StatsViewModel` utilisent `viewModelScope.launch` externe car ils collectent un Flow continu.

### T3 — `AddRetroSessionScreen.kt`

Structure identique à `NewMatchScreen` (Scaffold + Column scrollable) avec champs supplémentaires :

```kotlin
package com.secondserve.feature.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.SurfaceConstants
import com.secondserve.domain.model.ThirdSetRule
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddRetroSessionScreen(
    onNavigateBack: () -> Unit,
    viewModel: AddRetroSessionViewModel = hiltViewModel()
) {
    val state by viewModel.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE) }

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is AddRetroSessionSideEffect.SessionCreated -> onNavigateBack()
            is AddRetroSessionSideEffect.ShowError ->
                scope.launch { snackbarHostState.showSnackbar(effect.message) }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { viewModel.onMatchDateSelected(it) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Annuler") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajouter un match passé") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("← Retour") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Surface
            Text("Surface *", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SurfaceConstants.ALL.forEach { surface ->
                    FilterChip(
                        selected = state.selectedSurface == surface,
                        onClick = { viewModel.onSurfaceSelected(surface) },
                        label = { Text(SurfaceConstants.DISPLAY_NAMES[surface] ?: surface) }
                    )
                }
            }

            // Format
            Text("Format *", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.selectedMatchFormat == MatchFormat.BEST_OF_1,
                    onClick = { viewModel.onMatchFormatSelected(MatchFormat.BEST_OF_1) }
                )
                Text("1 set")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.selectedMatchFormat == MatchFormat.BEST_OF_3,
                    onClick = { viewModel.onMatchFormatSelected(MatchFormat.BEST_OF_3) }
                )
                Text("3 sets")
            }

            if (state.selectedMatchFormat == MatchFormat.BEST_OF_3) {
                Text("Règle 3e set *", style = MaterialTheme.typography.titleMedium)
                listOf(
                    ThirdSetRule.FULL_ADVANTAGE to "Avantage complet",
                    ThirdSetRule.SUPER_TIE_BREAK_10 to "Super tie-break à 10",
                    ThirdSetRule.SHORT_DECISIVE_SET to "Set décisif raccourci"
                ).forEach { (rule, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = state.selectedThirdSetRule == rule,
                            onClick = { viewModel.onThirdSetRuleSelected(rule) }
                        )
                        Text(label)
                    }
                }
            }

            // Résultat
            Text("Résultat *", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.selectedResult == "VICTORY",
                    onClick = { viewModel.onResultSelected("VICTORY") }
                )
                Text("Victoire")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.selectedResult == "DEFEAT",
                    onClick = { viewModel.onResultSelected("DEFEAT") }
                )
                Text("Défaite")
            }

            // Date du match
            Text("Date du match *", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    state.matchDateMillis?.let { dateFormat.format(Date(it)) }
                        ?: "Sélectionner une date"
                )
            }

            // Score (optionnel)
            OutlinedTextField(
                value = state.scoreText,
                onValueChange = viewModel::onScoreTextChanged,
                label = { Text("Score final (ex : 6-3, 4-6, 7-5)") },
                modifier = Modifier.fillMaxWidth()
            )

            // Informations optionnelles
            Text("Informations optionnelles", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.opponent,
                onValueChange = viewModel::onOpponentChanged,
                label = { Text("Adversaire") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.competitionType,
                onValueChange = viewModel::onCompetitionTypeChanged,
                label = { Text("Type de compétition") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.tournament,
                onValueChange = viewModel::onTournamentChanged,
                label = { Text("Tournoi") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                Button(
                    onClick = viewModel::submit,
                    enabled = state.canSubmit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enregistrer")
                }
            }
        }
    }
}
```

### T4 — Navigation

**`HistoryScreen.kt`** — ajouter paramètre + FAB :
```kotlin
// Signature : ajouter onNavigateToAddRetroSession
fun HistoryScreen(
    onNavigateToDetail: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToAddRetroSession: () -> Unit,   // ← nouveau
    viewModel: HistoryViewModel = hiltViewModel()
)

// Dans le Scaffold, ajouter :
floatingActionButton = {
    FloatingActionButton(onClick = onNavigateToAddRetroSession) {
        Text("+")
    }
}
```

Les imports `FloatingActionButton` sont dans `androidx.compose.material3.*` déjà importé.

**`AppNavGraph.kt`** — modifier l'appel `HistoryScreen` et ajouter la route :
```kotlin
import com.secondserve.feature.history.AddRetroSessionScreen   // nouveau import

// Modifier composable("history") :
composable("history") {
    HistoryScreen(
        onNavigateToDetail = { sessionId -> navController.navigate("session_detail/$sessionId") },
        onNavigateBack = { navController.popBackStack() },
        onNavigateToAddRetroSession = { navController.navigate("add_retro_session") }  // ← nouveau
    )
}

// Ajouter nouvelle route :
composable("add_retro_session") {
    AddRetroSessionScreen(onNavigateBack = { navController.popBackStack() })
}
```

### T5 — Tests

**`AddRetroSessionViewModelTest.kt`** — pattern identique à `HistoryViewModelTest` (JUnit5 + MockK + `UnconfinedTestDispatcher`) :
```kotlin
package com.secondserve.feature.history

import com.secondserve.domain.AppResult
import com.secondserve.domain.model.*
import com.secondserve.domain.repository.SessionRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlinx.coroutines.flow.first

class AddRetroSessionViewModelTest {

    private val sessionRepository: SessionRepository = mockk()

    @Test
    fun `canSubmit is false with no fields set`() = runTest {
        val vm = AddRetroSessionViewModel(sessionRepository)
        val state = vm.container.stateFlow.first()
        assertFalse(state.canSubmit)
    }

    @Test
    fun `canSubmit is true when required fields are set for BEST_OF_1`() = runTest {
        val vm = AddRetroSessionViewModel(sessionRepository)
        vm.onSurfaceSelected(SurfaceConstants.CLAY)
        vm.onMatchFormatSelected(MatchFormat.BEST_OF_1)
        vm.onResultSelected("VICTORY")
        vm.onMatchDateSelected(1_700_000_000_000L)

        val state = vm.container.stateFlow.first { it.selectedSurface != null && it.matchDateMillis != null }
        assertTrue(state.canSubmit)
    }

    @Test
    fun `canSubmit is false for BEST_OF_3 when thirdSetRule not selected`() = runTest {
        val vm = AddRetroSessionViewModel(sessionRepository)
        vm.onSurfaceSelected(SurfaceConstants.CLAY)
        vm.onMatchFormatSelected(MatchFormat.BEST_OF_3)   // thirdSetRule = null
        vm.onResultSelected("VICTORY")
        vm.onMatchDateSelected(1_700_000_000_000L)

        val state = vm.container.stateFlow.first { it.selectedMatchFormat != null }
        assertFalse(state.canSubmit)
    }

    @Test
    fun `submit success → SessionCreated side effect`() = runTest {
        coEvery { sessionRepository.createCompletedSession(any()) } returns AppResult.Success(
            Session(
                id = 42L, surface = SurfaceConstants.CLAY,
                format = SessionFormat(MatchFormat.BEST_OF_1),
                status = SessionStatus.COMPLETED, result = "VICTORY",
                createdAt = 1_700_000_000_000L, updatedAt = System.currentTimeMillis()
            )
        )

        val vm = AddRetroSessionViewModel(sessionRepository)
        vm.onSurfaceSelected(SurfaceConstants.CLAY)
        vm.onMatchFormatSelected(MatchFormat.BEST_OF_1)
        vm.onResultSelected("VICTORY")
        vm.onMatchDateSelected(1_700_000_000_000L)
        vm.submit()

        val effect = vm.container.sideEffectFlow.first()
        assertTrue(effect is AddRetroSessionSideEffect.SessionCreated)
    }

    @Test
    fun `submit failure → ShowError side effect`() = runTest {
        coEvery { sessionRepository.createCompletedSession(any()) } returns AppResult.Error(
            RuntimeException("DB error")
        )

        val vm = AddRetroSessionViewModel(sessionRepository)
        vm.onSurfaceSelected(SurfaceConstants.CLAY)
        vm.onMatchFormatSelected(MatchFormat.BEST_OF_1)
        vm.onResultSelected("DEFEAT")
        vm.onMatchDateSelected(1_700_000_000_000L)
        vm.submit()

        val effect = vm.container.sideEffectFlow.first()
        assertTrue(effect is AddRetroSessionSideEffect.ShowError)
    }
}
```

### Guardrails critiques

- ❌ **Pas de `StartMatchUseCase`** — ce UseCase n'existe pas dans le code actuel. Utiliser directement `SessionRepository.createCompletedSession()`.
- ❌ **Ne pas modifier `createSession()`** — cette méthode crée une session ACTIVE sans SyncQueue, utilisée par `NewMatchScreen`. La laisser intacte.
- ❌ **Ne pas modifier `closeSession()`** — cette méthode modifie une session existante. Inutile ici.
- ✅ **`createCompletedSession()` = nouvelle méthode** dans `SessionRepository` interface + `SessionRepositoryImpl`
- ✅ **Status toujours `SessionStatus.COMPLETED`** pour les sessions rétrospectives — jamais ACTIVE ou INTERRUPTED
- ✅ **`result` = "VICTORY" ou "DEFEAT" uniquement** — les deux seules options du formulaire
- ✅ **`createdAt` = epoch ms de la date saisie** — c'est la date du match, pas la date de saisie
- ✅ **`updatedAt` = `System.currentTimeMillis()`** — timestamp de la saisie dans l'app
- ❌ **Pas de nouvelle query `SessionDao`** — `dao.insert()` existant est suffisant
- ❌ **Pas de migration Room** — aucun changement de schéma (les champs `result`, `score_text`, `status` existent déjà depuis les versions précédentes)
- ✅ **AC2 automatique** — les sessions créées via `createCompletedSession()` apparaissent dans `getAllSessions()` Flow → HistoryScreen et StatsScreen se mettent à jour sans aucune modification
- ❌ **Pas de `Log.*`** — uniquement `Timber.e()` dans `SessionRepositoryImpl`
- ✅ **`@OptIn(ExperimentalMaterial3Api::class)` requis** pour `DatePickerDialog` et `DatePicker` (déjà utilisé dans `HistoryScreen.kt`)
- ✅ **`@OptIn(ExperimentalLayoutApi::class)` requis** pour `FlowRow` (déjà utilisé dans `NewMatchScreen.kt`)
- ❌ **Pas de `viewModelScope.launch` dans `AddRetroSessionViewModel`** — les actions sont ponctuelles, `intent {}` seul est correct
- ✅ **`coEvery` dans les tests** (pas `every`) — `createCompletedSession()` est `suspend`

### Fichiers à NE PAS modifier

- `SessionDao.kt` — aucune nouvelle query
- `SecondServeDatabase.kt` — aucune migration (schéma inchangé, version reste à 7)
- `SessionEntity.kt`, `Session.kt` — aucun nouveau champ
- `HistoryViewModel.kt` — aucun impact
- `StatsViewModel.kt`, `StatsComputer.kt` — AC2 garanti automatiquement via Flow
- `HomeScreen.kt` — entrée depuis l'historique, pas depuis l'accueil
- `NewMatchScreen.kt`, `NewMatchViewModel.kt` — aucun impact

### Architecture — modules concernés

| Module | Fichiers créés | Fichiers modifiés |
|---|---|---|
| `:domain` | — | `SessionRepository.kt` (+1 méthode) |
| `:data` | — | `SessionRepositoryImpl.kt` (+1 méthode) |
| `:feature:history` | `AddRetroSessionUiState.kt`, `AddRetroSessionViewModel.kt`, `AddRetroSessionScreen.kt`, `AddRetroSessionViewModelTest.kt` | `HistoryScreen.kt` (+FAB +param) |
| `:app` | — | `navigation/AppNavGraph.kt` (+route, +import) |

### Project Structure Notes

```
android/
├── domain/src/main/kotlin/com/secondserve/domain/repository/
│   └── SessionRepository.kt           # UPDATE (+createCompletedSession)
│
├── data/src/main/kotlin/com/secondserve/data/repository/
│   └── SessionRepositoryImpl.kt       # UPDATE (+createCompletedSession)
│
├── feature/history/
│   └── src/main/kotlin/com/secondserve/feature/history/
│       ├── AddRetroSessionUiState.kt  # CREATE (UiState + SideEffect)
│       ├── AddRetroSessionViewModel.kt # CREATE (Orbit MVI)
│       ├── AddRetroSessionScreen.kt   # CREATE (Composable formulaire)
│       └── HistoryScreen.kt           # UPDATE (+FAB + onNavigateToAddRetroSession)
│   └── src/test/kotlin/com/secondserve/feature/history/
│       └── AddRetroSessionViewModelTest.kt  # CREATE (5 tests)
│
└── app/src/main/kotlin/com/secondserve/navigation/
    └── AppNavGraph.kt                 # UPDATE (+route "add_retro_session" + import)
```

### Learnings de Story 4.2 à appliquer

- **Import Orbit MVI** : `org.orbitmvi.orbit.viewmodel.container` (pas `org.orbitmvi.orbit.container`)
- **`coEvery` vs `every` dans les tests** : `createCompletedSession()` est `suspend` → `coEvery`
- **Tests MockK + `UnconfinedTestDispatcher`** : configurer via `Dispatchers.setMain(UnconfinedTestDispatcher())` dans `@BeforeEach`
- **`container.sideEffectFlow.first()`** pour capturer les SideEffect dans les tests
- **`TextButton("← Retour")`** dans la `TopAppBar` — pattern établi dans l'ensemble du projet

### References

- [Source: epics.md#Story 4.3] — AC complets, FR-9
- [Source: architecture.md#FR-9] — mapping `StartMatchUseCase`, `SessionRepositoryImpl`
- [Source: architecture.md#Mapping FRs] — `:feature:history` pour FR-7 à FR-9
- [Source: data/repository/SessionRepositoryImpl.kt] — pattern `createSession()` + pattern SyncQueue de `closeSession()`
- [Source: feature/match/NewMatchScreen.kt] — pattern formulaire (FilterChip, RadioButton, OutlinedTextField)
- [Source: feature/match/NewMatchViewModel.kt] — pattern UiState + `canStartMatch`
- [Source: feature/history/HistoryScreen.kt] — structure existante à étendre (FAB + param)
- [Source: navigation/AppNavGraph.kt] — pattern routes existantes
- [Source: 4-2-statistiques-agregees.md#Dev Notes] — Learnings Orbit MVI, import, tests

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

Aucun blocage rencontré. Implémentation conforme aux guardrails de la story.

### Completion Notes List

- T1 : `createCompletedSession()` ajoutée dans l'interface `SessionRepository` et implémentée dans `SessionRepositoryImpl` avec transaction Room + entrée SyncQueue. La méthode reçoit une session déjà COMPLETED et l'insère atomiquement.
- T2 : `AddRetroSessionUiState` avec `canSubmit` calculé (surface + format + thirdSetRule si BEST_OF_3 + result + date), et `AddRetroSessionViewModel` Orbit MVI avec 10 handlers d'actions utilisateur et `submit()` via `intent {}`.
- T3 : `AddRetroSessionScreen` — formulaire Compose scrollable avec FilterChip pour la surface, RadioButton pour format/thirdSetRule/résultat, DatePickerDialog Material3, champs optionnels OutlinedTextField.
- T4 : `HistoryScreen` étendu avec paramètre `onNavigateToAddRetroSession` + FAB `+`. Route `"add_retro_session"` ajoutée dans `AppNavGraph.kt`.
- T5 : 5 tests unitaires couvrant canSubmit initial, BEST_OF_1 valid, BEST_OF_3 sans thirdSetRule, submit success (SessionCreated), submit failure (ShowError). Pattern identique à `HistoryViewModelTest` (`UnconfinedTestDispatcher`, `coroutineScope + async` pour les SideEffects).
- AC2 garanti automatiquement : les sessions créées via `createCompletedSession()` apparaissent dans `getAllSessions()` Flow sans aucune modification de `HistoryViewModel` ni `StatsViewModel`.
- Suite complète (218 tâches) : BUILD SUCCESSFUL, aucune régression.

### File List

- `android/domain/src/main/kotlin/com/secondserve/domain/repository/SessionRepository.kt` (modifié)
- `android/data/src/main/kotlin/com/secondserve/data/repository/SessionRepositoryImpl.kt` (modifié)
- `android/feature/history/src/main/kotlin/com/secondserve/feature/history/AddRetroSessionUiState.kt` (créé)
- `android/feature/history/src/main/kotlin/com/secondserve/feature/history/AddRetroSessionViewModel.kt` (créé)
- `android/feature/history/src/main/kotlin/com/secondserve/feature/history/AddRetroSessionScreen.kt` (créé)
- `android/feature/history/src/main/kotlin/com/secondserve/feature/history/HistoryScreen.kt` (modifié)
- `android/app/src/main/kotlin/com/secondserve/navigation/AppNavGraph.kt` (modifié)
- `android/feature/history/src/test/kotlin/com/secondserve/feature/history/AddRetroSessionViewModelTest.kt` (créé)
