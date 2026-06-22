---
baseline_commit: e04d252
---

# Story 4.1: Historique des Sessions

Status: review

## Story

As a player,
I want to browse all my sessions sorted by most recent first with key info visible at a glance,
So that I can review my match history anytime, including offline.

## Acceptance Criteria

1. **Given** je suis sur l'écran Historique  
   **When** la liste se charge  
   **Then** toutes les Sessions sont affichées, triées par date décroissante (plus récente en premier)  
   **And** chaque entrée affiche : date, adversaire (si renseigné), surface, score final (si clôturée), résultat (Victoire / Défaite / N/A), type de compétition (si renseigné)  
   **And** les Sessions incomplètes (ACTIVE ou INTERRUPTED) sont visibles avec un indicateur de statut distinct ("En cours" / "Interrompue")  
   **And** le chargement de l'historique avec < 200 Sessions prend ≤ 1 seconde (NFR-P3)  
   **And** l'historique est entièrement consultable hors connexion (données Room locales — NFR-OFF2)

2. **When** je tape sur une Session  
   **Then** un écran de détail affiche toutes ses métadonnées (surface, format, adversaire, type compétition, tournoi, ressenti) et les Conseils générés pendant ce match (lus depuis `coaching_cache` Room via `sessionId`)

## ⚠️ Gap critique : `score_text` absent de `SessionEntity`

Le champ `Session.result` stocke `"VICTORY"` / `"DEFEAT"` / `"DRAW"` / `"ABANDONED"` (calculé par `MatchScore.calculateResult()`). **Il n'y a aucun champ textuel du score (ex. "6-4, 7-5")** dans `SessionEntity` ni dans `Session` (domain model).

**Décision obligatoire pour cette story :**  
Ajouter `score_text: String?` à `SessionEntity` via **MIGRATION_6_7**, et mettre à jour `CloseMatchUseCase` pour calculer et stocker le texte du score à la clôture. Les sessions antérieures auront `score_text = null` (acceptable — affichage "—").

## Tasks / Subtasks

- [x] **T1 — MIGRATION_6_7 : ajouter `score_text` à `sessions`** (AC: 1)
  - [x] T1.1 Ajouter `score_text TEXT` à `SessionEntity` (nullable, par défaut null)
  - [x] T1.2 Ajouter la colonne correspondante dans `Session` (domain model)
  - [x] T1.3 Créer `MIGRATION_6_7` dans `SecondServeDatabase` : `ALTER TABLE sessions ADD COLUMN score_text TEXT`
  - [x] T1.4 Incrémenter la version DB à 7 et ajouter `MIGRATION_6_7` dans `addMigrations()`
  - [x] T1.5 Ajouter `MatchScore.toScoreText(): String` dans `:domain`
  - [x] T1.6 Mettre à jour `CloseMatchUseCase` pour calculer et passer `scoreText` à `SessionRepository.closeSession()`
  - [x] T1.7 Mettre à jour `SessionRepository` (interface) et `SessionRepositoryImpl` pour accepter `scoreText: String?`
  - [x] T1.8 Mettre à jour les mappers `toDomain()` / `toEntity()` sur `SessionEntity`

- [x] **T2 — Étendre `CoachingRepository` pour la vue détail** (AC: 2)
  - [x] T2.1 Ajouter `suspend fun getAdvicesForSession(sessionId: Long): List<CoachingCacheEntry>` à `CoachingRepository` (interface)
  - [x] T2.2 Implémenter dans `CoachingRepositoryImpl` via `CoachingCacheDao.getAllForMatch(sessionId)` (déjà disponible)

- [x] **T3 — `HistoryViewModel` & `HistoryScreen`** (AC: 1)
  - [x] T3.1 Créer `HistoryUiState.kt` (sealed class : Loading, Content, Error) dans `:feature:history`
  - [x] T3.2 Créer `HistoryViewModel.kt` (Orbit MVI, injecte `SessionRepository`, expose Flow `getAllSessions()`)
  - [x] T3.3 Créer `HistoryScreen.kt` (Compose `LazyColumn`, affichage de chaque session avec les infos requises)

- [x] **T4 — `SessionDetailViewModel` & `SessionDetailScreen`** (AC: 2)
  - [x] T4.1 Créer `SessionDetailUiState.kt` (sealed class) dans `:feature:history`
  - [x] T4.2 Créer `SessionDetailViewModel.kt` (Orbit MVI, injecte `SessionRepository` + `CoachingRepository`, `SavedStateHandle` pour `sessionId`)
  - [x] T4.3 Créer `SessionDetailScreen.kt` (Compose, affiche métadonnées + liste des conseils coaching)

- [x] **T5 — Navigation & HomeScreen** (AC: 1, 2)
  - [x] T5.1 Ajouter le bouton "Historique" dans `HomeScreen.kt` (callback `onNavigateToHistory`)
  - [x] T5.2 Ajouter les routes `"history"` et `"session_detail/{sessionId}"` dans `AppNavGraph.kt`

- [x] **T6 — Tests unitaires** (AC: 1, 2)
  - [x] T6.1 `HistoryViewModelTest.kt` : état initial Loading → Content (flow sessions), state avec liste vide, état Error
  - [x] T6.2 `SessionDetailViewModelTest.kt` : chargement session + conseils, session introuvable → Error
  - [x] T6.3 `CloseMatchUseCaseTest.kt` : vérifier que `scoreText` est bien calculé et passé au repository

---

## Dev Notes

### T1 — Gap `score_text` : détails d'implémentation

**Extension à ajouter dans `:domain` (fichier `MatchScore.kt` ou nouveau fichier `matchScoreExtensions.kt`) :**

```kotlin
fun MatchScore.toScoreText(): String =
    completedSets.joinToString(", ") { "${it.gamesA}-${it.gamesB}" }
        .ifEmpty { "" }
```

**Mise à jour de `CloseMatchUseCase` :**

```kotlin
class CloseMatchUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(
        sessionId: Long,
        finalScore: MatchScore,
        feelingRating: Int?,
        feelingComment: String?
    ): AppResult<Unit> {
        val result = finalScore.calculateResult()
        val scoreText = finalScore.toScoreText().takeIf { it.isNotEmpty() }
        return sessionRepository.closeSession(sessionId, result, scoreText, feelingRating, feelingComment)
    }
}
```

**Mise à jour de `SessionRepository` (interface) :**

```kotlin
suspend fun closeSession(
    sessionId: Long,
    result: String,
    scoreText: String?,
    feelingRating: Int?,
    feelingComment: String?
): AppResult<Unit>
```

**`SessionEntity` — ajouter le champ :**

```kotlin
@ColumnInfo(name = "score_text") val scoreText: String? = null,
```

**MIGRATION_6_7 :**

```kotlin
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE sessions ADD COLUMN score_text TEXT")
    }
}
```

### T2 — `CoachingRepository.getAdvicesForSession`

`CoachingCacheDao.getAllForMatch(matchId)` existe déjà — simple délégation dans `CoachingRepositoryImpl`.

```kotlin
// CoachingRepository (interface)
suspend fun getAdvicesForSession(sessionId: Long): List<CoachingCacheEntry>

// CoachingRepositoryImpl
override suspend fun getAdvicesForSession(sessionId: Long): List<CoachingCacheEntry> =
    dao.getAllForMatch(sessionId).map { it.toDomain() }
```

### T3 — `HistoryViewModel` : pattern Orbit MVI

**États mutuellement exclusifs → sealed class** (règle architecture.md) :

```kotlin
sealed class HistoryUiState {
    object Loading : HistoryUiState()
    data class Content(val sessions: List<Session>) : HistoryUiState()
    data class Error(val message: String) : HistoryUiState()
}

sealed class HistorySideEffect {
    data class NavigateToDetail(val sessionId: Long) : HistorySideEffect()
}
```

**`HistoryViewModel` :**

```kotlin
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel(), ContainerHost<HistoryUiState, HistorySideEffect> {

    override val container = container<HistoryUiState, HistorySideEffect>(HistoryUiState.Loading)

    init {
        viewModelScope.launch {
            sessionRepository.getAllSessions().collect { sessions ->
                intent { reduce { HistoryUiState.Content(sessions) } }
            }
        }
    }

    fun onSessionClicked(sessionId: Long) = intent {
        postSideEffect(HistorySideEffect.NavigateToDetail(sessionId))
    }
}
```

`SessionRepository.getAllSessions()` retourne `Flow<List<Session>>` (trié `DESC` par `created_at` dans `SessionDao` — la query Room `ORDER BY created_at DESC` est déjà en place). **Pas besoin de reSort côté ViewModel.**

### T3 — `HistoryScreen` : affichage de chaque session

Chaque item doit afficher (règle AC 1) :

| Champ | Source | Affichage si null |
|---|---|---|
| Date | `session.createdAt` (epoch ms) | — |
| Adversaire | `session.opponent` | non affiché |
| Surface | `session.surface` | toujours présent |
| Score final | `session.scoreText` | "—" |
| Résultat | `session.result` → mapper vers "Victoire" / "Défaite" / "N/A" | "N/A" |
| Type compétition | `session.competitionType` | non affiché |
| Statut session | `session.status` si `ACTIVE` ou `INTERRUPTED` | badge distinct |

**Mapper résultat :**
```kotlin
fun Session.resultLabel(): String = when (result) {
    "VICTORY" -> "Victoire"
    "DEFEAT" -> "Défaite"
    "DRAW" -> "Nul"
    "ABANDONED" -> "Abandonné"
    else -> "N/A"
}
```

**Mapper statut (pour sessions non COMPLETED) :**
```kotlin
fun Session.statusBadge(): String? = when (status) {
    SessionStatus.ACTIVE -> "En cours"
    SessionStatus.INTERRUPTED -> "Interrompue"
    SessionStatus.COMPLETED -> null
}
```

**Date d'affichage** : `createdAt` est en epoch ms — utiliser `java.text.SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date(session.createdAt))`.

### T4 — `SessionDetailViewModel`

```kotlin
@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val coachingRepository: CoachingRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel(), ContainerHost<SessionDetailUiState, Nothing> {

    private val sessionId: Long = checkNotNull(savedStateHandle["sessionId"])

    override val container = container<SessionDetailUiState, Nothing>(SessionDetailUiState.Loading)

    init {
        load()
    }

    private fun load() = intent {
        val session = sessionRepository.getSessionById(sessionId)
        if (session == null) {
            reduce { SessionDetailUiState.Error("Session introuvable") }
            return@intent
        }
        val advices = coachingRepository.getAdvicesForSession(sessionId)
        reduce { SessionDetailUiState.Content(session, advices) }
    }
}
```

**États :**
```kotlin
sealed class SessionDetailUiState {
    object Loading : SessionDetailUiState()
    data class Content(val session: Session, val advices: List<CoachingCacheEntry>) : SessionDetailUiState()
    data class Error(val message: String) : SessionDetailUiState()
}
```

### T5 — Navigation

**`AppNavGraph.kt`** — ajouter :

```kotlin
composable("history") {
    HistoryScreen(
        onNavigateToDetail = { sessionId -> navController.navigate("session_detail/$sessionId") },
        onNavigateBack = { navController.popBackStack() }
    )
}
composable(
    route = "session_detail/{sessionId}",
    arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
) {
    SessionDetailScreen(
        onNavigateBack = { navController.popBackStack() }
    )
}
```

**`HomeScreen.kt`** — ajouter un bouton après "Mon profil" :

```kotlin
// Ajouter le paramètre callback
fun HomeScreen(
    onNavigateToNewMatch: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToHistory: () -> Unit    // ← nouveau
) {
    // ...
    OutlinedButton(onClick = onNavigateToHistory, modifier = Modifier.fillMaxWidth()) {
        Text("Historique")
    }
}
```

**Mettre à jour l'appel dans `AppNavGraph.kt` :**
```kotlin
composable("home") {
    HomeScreen(
        onNavigateToNewMatch = { navController.navigate("new_match") },
        onNavigateToProfile = { navController.navigate("profile") },
        onNavigateToHistory = { navController.navigate("history") }  // ← nouveau
    )
}
```

### T6 — Tests unitaires : patterns à respecter

- **Framework** : JUnit5 + MockK + `coroutines-test` (UnconfinedTestDispatcher)
- **Pattern** : identique à `MatchViewModelTest.kt` — voir `android/feature/match/src/test/kotlin/com/secondserve/feature/match/MatchViewModelTest.kt`
- **Orbit MVI test** : utiliser `container.stateFlow.first()` ou `collectAsState()` pour observer les états
- **MockK** : `mockk<SessionRepository>()`, `mockk<CoachingRepository>()`
- ❌ **Jamais** `Dispatchers.IO` dans les tests — toujours `UnconfinedTestDispatcher`

**`HistoryViewModelTest.kt` :**
```kotlin
@Test
fun `initial state is Loading then Content when sessions flow emits`() = runTest {
    val sessions = listOf(fakeSession())
    val sessionsFlow = MutableStateFlow(sessions)
    coEvery { sessionRepository.getAllSessions() } returns sessionsFlow
    
    val vm = HistoryViewModel(sessionRepository)
    val state = vm.container.stateFlow.first { it is HistoryUiState.Content }
    assertEquals(1, (state as HistoryUiState.Content).sessions.size)
}
```

### Guardrails critiques

- **`SessionRepository.getAllSessions()` retourne un `Flow`** — ne pas appeler `collect {}` dans `Coroutine` sans `viewModelScope`. Dans les ViewModels, toujours utiliser `viewModelScope.launch { flow.collect { ... } }`.
- **Pas de `Log.d()` direct** — uniquement `Timber.d()` / `Timber.e()`.
- **`AppResult<T>` requis** — toute méthode `suspend` sur repository qui peut échouer doit retourner `AppResult<T>`, sauf `Flow<>` et les getters simples.
- **`SessionDetailViewModel.sessionId`** : utiliser `checkNotNull(savedStateHandle["sessionId"])` — pas de valeur par défaut `0L` (contrairement au bug potentiel de MatchViewModel).
- **Migrations Room** : pas de `fallbackToDestructiveMigration` — une migration manquante crashera le build. Vérifier que `MIGRATION_6_7` est dans `addMigrations()` dans `DataModule`.
- **`CoachingCacheEntry.pattern` est un `MatchPattern`** — pour afficher le conseil sur l'écran détail, utiliser `entry.content` (le texte brut) et `entry.pattern.name` si le label du pattern est nécessaire.

### Fichiers **à NE PAS modifier**

- `MatchViewModel.kt` — aucun impact
- `SyncWorker.kt` — aucun impact (pas de sync dans cette story)
- `CoachingCachePrefetcher.kt` — aucun impact
- `CoachingResolver.kt` — aucun impact
- Tables Room existantes (sauf `sessions` via MIGRATION_6_7)

### Architecture — modules concernés

| Module | Fichiers modifiés | Fichiers créés |
|---|---|---|
| `:domain` | `Session.kt`, `SessionRepository.kt`, `CoachingRepository.kt`, `CloseMatchUseCase.kt` | `MatchScore.toScoreText()` (extension dans `CloseMatchUseCase.kt` ou nouveau fichier) |
| `:data` | `SessionEntity.kt`, `SessionRepositoryImpl.kt`, `SecondServeDatabase.kt`, `CoachingRepositoryImpl.kt` | — |
| `:feature:history` | — | `HistoryUiState.kt`, `HistoryViewModel.kt`, `HistoryScreen.kt`, `SessionDetailUiState.kt`, `SessionDetailViewModel.kt`, `SessionDetailScreen.kt` |
| `:app` | `AppNavGraph.kt`, `HomeScreen.kt`, `DataModule.kt` (MIGRATION_6_7) | — |

### Performances (NFR-P3)

La query Room `getAllSessions()` est `ORDER BY created_at DESC` sans index sur `created_at`. Avec < 200 sessions et SQLite, le full-scan est ≤ 1s. **Pas besoin d'index supplémentaire dans cette story** — l'index `idx_sessions_surface` (story 2.3) est déjà présent pour les stats futures.

### Hors scope de cette story

- ❌ Saisie manuelle rétrospective (Story 4.3)
- ❌ Statistiques agrégées (Story 4.2)
- ❌ Backend VPS (pas d'appels réseau)
- ❌ Scroll infini / pagination (< 200 sessions en V1)
- ❌ Filtrage ou recherche dans l'historique

---

## Project Structure Notes

### Localisation exacte des fichiers à créer

```
android/
├── domain/
│   └── src/main/kotlin/com/secondserve/domain/
│       ├── model/
│       │   └── Session.kt                          # UPDATE : + scoreText
│       ├── repository/
│       │   ├── SessionRepository.kt                # UPDATE : + scoreText param
│       │   └── CoachingRepository.kt               # UPDATE : + getAdvicesForSession
│       └── usecase/match/
│           └── CloseMatchUseCase.kt                # UPDATE : + toScoreText()
│
├── data/
│   └── src/main/kotlin/com/secondserve/data/
│       ├── local/db/
│       │   ├── SecondServeDatabase.kt              # UPDATE : v6→v7, MIGRATION_6_7
│       │   └── entity/
│       │       └── SessionEntity.kt                # UPDATE : + score_text colonne
│       └── repository/
│           ├── SessionRepositoryImpl.kt            # UPDATE : + scoreText param
│           └── CoachingRepositoryImpl.kt           # UPDATE : + getAdvicesForSession
│
├── feature/history/
│   └── src/main/kotlin/com/secondserve/feature/history/
│       ├── HistoryUiState.kt                       # CREATE
│       ├── HistoryViewModel.kt                     # CREATE
│       ├── HistoryScreen.kt                        # CREATE
│       ├── SessionDetailUiState.kt                 # CREATE
│       ├── SessionDetailViewModel.kt               # CREATE
│       └── SessionDetailScreen.kt                  # CREATE
│   └── src/test/kotlin/com/secondserve/feature/history/
│       ├── HistoryViewModelTest.kt                 # CREATE
│       └── SessionDetailViewModelTest.kt           # CREATE
│
└── app/
    └── src/main/kotlin/com/secondserve/
        ├── HomeScreen.kt                           # UPDATE : + onNavigateToHistory
        └── navigation/
            └── AppNavGraph.kt                      # UPDATE : + history + session_detail routes
```

### Dépendances du module `:feature:history`

Le `build.gradle.kts` de `:feature:history` est **déjà configuré** correctement avec `project(":domain")`, `compose.bom`, `compose.material3`, `hilt`, `orbit`. **Aucune modification de `build.gradle.kts` n'est nécessaire** pour cette story.

⚠️ `:feature:history` n'a pas de dépendance sur `:data` — les accès aux données passent **uniquement par les interfaces `:domain`** (SessionRepository, CoachingRepository) injectées par Hilt via `:data/src/main/kotlin/com/secondserve/data/di/`.

### Conventions de nommage à respecter

- `SessionDetailScreen` et `SessionDetailViewModel` : la spec architecture nomme les fichiers du module history `HistoryScreen.kt`, `HistoryViewModel.kt`, `StatsScreen.kt`. `SessionDetailScreen.kt` n'est pas explicitement mentionné mais suit le même pattern et est requis par l'AC 2.
- Routes de navigation : `"history"`, `"session_detail/{sessionId}"` (snake_case) — cohérent avec `"new_match"`, `"work_axes"` existants.

### References

- [Source: architecture.md#feature:history] — HistoryScreen.kt, HistoryViewModel.kt, StatsScreen.kt
- [Source: architecture.md#SessionDao] — `getAllSessions()` : `SELECT * FROM sessions ORDER BY created_at DESC`
- [Source: architecture.md#CoachingCacheDao] — `getAllForMatch(matchId)` : existant, utilisé dans T2
- [Source: architecture.md#Communication Patterns] — règle sealed class pour états mutuellement exclusifs
- [Source: architecture.md#Process Patterns] — AppResult\<T\>, Timber, pas de Log.*
- [Source: architecture.md#Naming Patterns] — PascalCase Kotlin, snake_case DB
- [Source: epics.md#Story 4.1] — AC complets, NFR-P3, NFR-OFF2
- [Source: deferred-work.md#D7] — "getAllSessions() existe mais aucun écran historique n'est implémenté"
- [Source: deferred-work.md#D5 story 2.3] — "SessionsResponse défini mais inutilisé — Sera utilisé dans une story future" → hors scope 4.1

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- Erreur `capture(slot<String?>())` : MockK ne supporte pas `capture` sur les types nullables (T : Any). Résolution : utilisation de `coVerify` avec valeurs exactes.
- Erreur `ThirdSetRule.FULL_SET` inexistante dans les tests. Valeurs disponibles : FULL_ADVANTAGE, SUPER_TIE_BREAK_10, SHORT_DECISIVE_SET.
- Erreur import `Icons.AutoMirrored.Filled.ArrowBack` : dépendance `material.icons.extended` absente dans le projet. Résolution : bouton texte `TextButton("← Retour")`.
- Smart cast cross-module impossible sur `Session.competitionType`, `opponent`, etc. Résolution : `?.let { }` au lieu de `if (x != null)`.
- `container` Orbit MVI dans ViewModel nécessite import `org.orbitmvi.orbit.viewmodel.container` (pas `org.orbitmvi.orbit.container`).
- `async` Kotlin deprecated sans scope explicite dans tests : encapsulé dans `coroutineScope { }`.
- Tests `SessionRepositoryImplTest.kt` existants utilisaient l'ancienne signature 4-params de `closeSession` : mis à jour vers 5-params.

### Completion Notes List

- T1 : Ajout `score_text TEXT` (nullable) dans `SessionEntity` + `Session` + migration Room v6→v7 + `MIGRATION_6_7` dans `DataModule`. Extension `MatchScore.toScoreText()` dans `CloseMatchUseCase.kt`. `CloseMatchUseCase` calcule et passe `scoreText` (null si aucun set). Signature `closeSession` étendue de 4 à 5 paramètres (`scoreText: String?`). Mappers `toDomain`/`toEntity` mis à jour.
- T2 : `CoachingRepository.getAdvicesForSession(sessionId)` ajouté à l'interface et implémenté dans `CoachingRepositoryImpl` via `dao.getAllForMatch(sessionId)`.
- T3 : `HistoryUiState` (sealed : Loading/Content/Error) + `HistorySideEffect.NavigateToDetail`. `HistoryViewModel` (Orbit MVI) collecte `getAllSessions()` flow et expose `onSessionClicked`. `HistoryScreen` compose avec `LazyColumn`, items cliquables affichant date/surface/score/résultat/adversaire/statut.
- T4 : `SessionDetailUiState` (sealed : Loading/Content/Error). `SessionDetailViewModel` charge `getSessionById` + `getAdvicesForSession` parallèlement. `SessionDetailScreen` affiche métadonnées complètes + liste des conseils coaching.
- T5 : `HomeScreen` étendu avec paramètre `onNavigateToHistory`. `AppNavGraph` ajoute routes `"history"` et `"session_detail/{sessionId}"` avec `NavType.LongType`.
- T6 : `HistoryViewModelTest` (4 tests), `SessionDetailViewModelTest` (3 tests), `CloseMatchUseCaseTest` étendu (2 nouveaux tests toScoreText/scoreText null). Ajout dépendances test + `useJUnitPlatform()` dans `feature:history/build.gradle.kts`.

### File List

**Créés :**
- `android/feature/history/src/main/kotlin/com/secondserve/feature/history/HistoryUiState.kt`
- `android/feature/history/src/main/kotlin/com/secondserve/feature/history/HistoryViewModel.kt`
- `android/feature/history/src/main/kotlin/com/secondserve/feature/history/HistoryScreen.kt`
- `android/feature/history/src/main/kotlin/com/secondserve/feature/history/SessionDetailUiState.kt`
- `android/feature/history/src/main/kotlin/com/secondserve/feature/history/SessionDetailViewModel.kt`
- `android/feature/history/src/main/kotlin/com/secondserve/feature/history/SessionDetailScreen.kt`
- `android/feature/history/src/test/kotlin/com/secondserve/feature/history/HistoryViewModelTest.kt`
- `android/feature/history/src/test/kotlin/com/secondserve/feature/history/SessionDetailViewModelTest.kt`

**Modifiés :**
- `android/domain/src/main/kotlin/com/secondserve/domain/model/Session.kt` (+ scoreText)
- `android/domain/src/main/kotlin/com/secondserve/domain/repository/SessionRepository.kt` (+ scoreText param)
- `android/domain/src/main/kotlin/com/secondserve/domain/repository/CoachingRepository.kt` (+ getAdvicesForSession)
- `android/domain/src/main/kotlin/com/secondserve/domain/usecase/match/CloseMatchUseCase.kt` (+ toScoreText, scoreText passé)
- `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/SessionEntity.kt` (+ scoreText)
- `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/Mappers.kt` (+ scoreText dans toDomain/toEntity)
- `android/data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt` (v7, MIGRATION_6_7)
- `android/data/src/main/kotlin/com/secondserve/data/repository/SessionRepositoryImpl.kt` (+ scoreText param)
- `android/data/src/main/kotlin/com/secondserve/data/repository/CoachingRepositoryImpl.kt` (+ getAdvicesForSession)
- `android/app/src/main/kotlin/com/secondserve/HomeScreen.kt` (+ onNavigateToHistory)
- `android/app/src/main/kotlin/com/secondserve/navigation/AppNavGraph.kt` (+ history routes)
- `android/app/src/main/kotlin/com/secondserve/di/DataModule.kt` (+ MIGRATION_6_7)
- `android/feature/history/build.gradle.kts` (+ test dependencies, useJUnitPlatform)
- `android/domain/src/test/kotlin/com/secondserve/domain/usecase/CloseMatchUseCaseTest.kt` (+ 2 tests scoreText, signature 5-params)
- `android/data/src/test/kotlin/com/secondserve/data/repository/SessionRepositoryImplTest.kt` (signature closeSession 5-params)

## Change Log

- 2026-06-22 : Implémentation complète story 4.1 — historique des sessions. Migration Room v6→v7 (`score_text`), écran `HistoryScreen` + `SessionDetailScreen`, navigation depuis `HomeScreen`, 9 nouveaux tests unitaires.
