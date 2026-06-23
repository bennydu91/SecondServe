---
baseline_commit: 30be0f8
---

# Story 5.3: Synthèse IA multi-matchs

Status: review

## Story

As a player,
I want a multi-match AI synthesis generated automatically after 3 new sessions,
so that I can spot recurring patterns in my game that I can't see match by match.

## Acceptance Criteria

1. **Given** ≥ 3 nouvelles Sessions Match ont été clôturées depuis la dernière synthèse (ou depuis le début si aucune synthèse existante)
   **When** l'app se reconnecte ou un background check se déclenche (PostMatchAnalysisWorker terminé avec succès)
   **Then** une synthèse est générée via `VpsMistralEngine` → VPS → Mistral
   **And** elle contient : patterns récurrents sur la période, évolution par rapport à la synthèse précédente (si existante), axe d'amélioration prioritaire multi-matchs, recommandation structurée
   **And** elle référence des données agrégées des sessions concernées — jamais de contenu générique (FR-11, NFR-C3)
   **And** elle est stockée en Room (table `coaching_syntheses`, migration 8→9) et consultable hors connexion
   **And** elle est visuellement distincte des analyses individuelles dans l'écran Coaching

2. **When** je tape "Générer maintenant" dans l'écran Coaching
   **Then** une synthèse est générée à la demande même si le seuil de 3 sessions n'est pas atteint
   **And** la génération se fait dans le ViewModel (suspend, pas WorkManager) avec indicateur de progression
   **And** la synthèse générée remplace/complète les précédentes dans l'écran

3. **Given** je suis dans l'écran Coaching
   **Then** je vois la synthèse la plus récente (si existante) dans une Card distincte
   **And** je vois la liste de toutes mes analyses post-match individuelles (depuis la Story 5.2)
   **And** l'écran Coaching est accessible depuis l'écran d'accueil via un bouton "Coaching IA"

## Tasks / Subtasks

- [x] **T1 — Domain model `CoachingSynthesis`** (AC: 1, 2, 3)
  - [x] T1.1 Créer `android/domain/src/main/kotlin/com/secondserve/domain/model/CoachingSynthesis.kt` :
    ```kotlin
    data class CoachingSynthesis(
        val id: Long = 0L,
        val content: String,
        val sessionCount: Int,
        val generatedAt: Long
    )
    ```

- [x] **T2 — Extension interface `CoachingRepository`** (AC: 1, 2, 3)
  - [x] T2.1 Ajouter dans `android/domain/src/main/kotlin/com/secondserve/domain/repository/CoachingRepository.kt` :
    ```kotlin
    suspend fun saveSynthesis(content: String, sessionCount: Int): AppResult<CoachingSynthesis>
    suspend fun getLatestSynthesis(): CoachingSynthesis?
    fun observeLatestSynthesis(): Flow<CoachingSynthesis?>
    fun observeAllAnalyses(): Flow<List<CoachingAnalysis>>
    ```

- [x] **T3 — Extension interface `SessionRepository`** (AC: 1, 2)
  - [x] T3.1 Ajouter dans `android/domain/src/main/kotlin/com/secondserve/domain/repository/SessionRepository.kt` :
    ```kotlin
    suspend fun countCompletedSince(afterMs: Long): Int
    suspend fun getCompletedSince(afterMs: Long): List<Session>
    ```

- [x] **T4 — `CoachingSynthesisEntity` + `CoachingSynthesisDao`** (AC: 1, 2)
  - [x] T4.1 Créer `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/CoachingSynthesisEntity.kt` :
    ```kotlin
    @Entity(tableName = "coaching_syntheses")
    data class CoachingSynthesisEntity(
        @PrimaryKey(autoGenerate = true) val id: Long = 0L,
        @ColumnInfo(name = "content") val content: String,
        @ColumnInfo(name = "session_count") val sessionCount: Int,
        @ColumnInfo(name = "generated_at") val generatedAt: Long
    )

    fun CoachingSynthesisEntity.toDomain(): CoachingSynthesis =
        CoachingSynthesis(id = id, content = content, sessionCount = sessionCount, generatedAt = generatedAt)

    fun CoachingSynthesis.toEntity(): CoachingSynthesisEntity =
        CoachingSynthesisEntity(id = id, content = content, sessionCount = sessionCount, generatedAt = generatedAt)
    ```
  - [x] T4.2 Créer `android/data/src/main/kotlin/com/secondserve/data/local/dao/CoachingSynthesisDao.kt` :
    ```kotlin
    @Dao
    interface CoachingSynthesisDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(entity: CoachingSynthesisEntity): Long

        @Query("SELECT * FROM coaching_syntheses ORDER BY generated_at DESC LIMIT 1")
        suspend fun getLatest(): CoachingSynthesisEntity?

        @Query("SELECT * FROM coaching_syntheses ORDER BY generated_at DESC LIMIT 1")
        fun observeLatest(): Flow<CoachingSynthesisEntity?>
    }
    ```

- [x] **T5 — Extension `CoachingAnalysisDao`** (AC: 3)
  - [x] T5.1 Ajouter dans `android/data/src/main/kotlin/com/secondserve/data/local/dao/CoachingAnalysisDao.kt` :
    ```kotlin
    @Query("SELECT * FROM coaching_analyses ORDER BY generated_at DESC")
    fun getAllAnalyses(): Flow<List<CoachingAnalysisEntity>>
    ```

- [x] **T6 — Migration Room 8→9 + mise à jour `SecondServeDatabase`** (AC: 1)
  - [x] T6.1 Ajouter `CoachingSynthesisEntity::class` dans `@Database(entities = [...])`
  - [x] T6.2 Ajouter `abstract fun coachingSynthesisDao(): CoachingSynthesisDao`
  - [x] T6.3 Ajouter `MIGRATION_8_9` :
    ```kotlin
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS coaching_syntheses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    content TEXT NOT NULL,
                    session_count INTEGER NOT NULL,
                    generated_at INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }
    ```
  - [x] T6.4 Incrémenter `version = 9` dans `@Database`

- [x] **T7 — Extension `SessionDao`** (AC: 1, 2)
  - [x] T7.1 Ajouter dans `android/data/src/main/kotlin/com/secondserve/data/local/dao/SessionDao.kt` :
    ```kotlin
    @Query("SELECT COUNT(*) FROM sessions WHERE status = 'COMPLETED' AND updated_at > :afterMs")
    suspend fun countCompletedSince(afterMs: Long): Int

    @Query("SELECT * FROM sessions WHERE status = 'COMPLETED' AND updated_at > :afterMs ORDER BY updated_at DESC")
    suspend fun getCompletedSince(afterMs: Long): List<SessionEntity>
    ```
  - Note : le status DB pour les sessions fermées est la chaîne `"COMPLETED"` (vérifiée dans `SessionRepositoryImpl.kt` ligne 90 : `status = "COMPLETED"`)

- [x] **T8 — Mise à jour `CoachingRepositoryImpl`** (AC: 1, 2, 3)
  - [x] T8.1 Injecter `CoachingSynthesisDao` dans le constructeur
  - [x] T8.2 Implémenter `saveSynthesis` :
    ```kotlin
    override suspend fun saveSynthesis(content: String, sessionCount: Int): AppResult<CoachingSynthesis> = try {
        val entity = CoachingSynthesisEntity(content = content, sessionCount = sessionCount, generatedAt = System.currentTimeMillis())
        val id = synthesisDao.insert(entity)
        AppResult.Success(entity.copy(id = id).toDomain())
    } catch (e: Exception) {
        AppResult.Error(e)
    }
    ```
  - [x] T8.3 Implémenter `getLatestSynthesis` : `synthesisDao.getLatest()?.toDomain()`
  - [x] T8.4 Implémenter `observeLatestSynthesis` : `synthesisDao.observeLatest().map { it?.toDomain() }`
  - [x] T8.5 Implémenter `observeAllAnalyses` : `analysisDao.getAllAnalyses().map { list -> list.map { it.toDomain() } }`

- [x] **T9 — Mise à jour `SessionRepositoryImpl`** (AC: 1, 2)
  - [x] T9.1 Implémenter `countCompletedSince` : `sessionDao.countCompletedSince(afterMs)`
  - [x] T9.2 Implémenter `getCompletedSince` : `sessionDao.getCompletedSince(afterMs).map { it.toDomain() }`
  - Note : `toDomain()` existe déjà sur `SessionEntity` depuis les stories 2.x

- [x] **T10 — `DataModule` — ajouter providers** (AC: 1, 2, 3)
  - [x] T10.1 Dans `DataModule.kt`, ajouter `.addMigrations(SecondServeDatabase.MIGRATION_8_9)` dans le builder Room
  - [x] T10.2 Ajouter provider `CoachingSynthesisDao` :
    ```kotlin
    @Provides @Singleton
    fun provideCoachingSynthesisDao(db: SecondServeDatabase): CoachingSynthesisDao = db.coachingSynthesisDao()
    ```
  - [x] T10.3 Ajouter provider `SynthesisScheduler` :
    ```kotlin
    @Provides @Singleton
    fun provideSynthesisScheduler(@ApplicationContext context: Context): SynthesisScheduler = SynthesisSchedulerImpl(context)
    ```

- [x] **T11 — `SynthesisScheduler` interface + `SynthesisSchedulerImpl`** (AC: 1)
  - [x] T11.1 Créer `android/domain/src/main/kotlin/com/secondserve/domain/synthesis/SynthesisScheduler.kt` :
    ```kotlin
    interface SynthesisScheduler {
        fun schedule()
    }
    ```
  - [x] T11.2 Créer `android/data/src/main/kotlin/com/secondserve/data/worker/SynthesisSchedulerImpl.kt` :
    ```kotlin
    class SynthesisSchedulerImpl(private val context: Context) : SynthesisScheduler {
        override fun schedule() {
            val request = OneTimeWorkRequestBuilder<SynthesisWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("synthesis_check", ExistingWorkPolicy.KEEP, request)
        }
    }
    ```
  - Note : `ExistingWorkPolicy.KEEP` — si un worker est déjà en queue (ex: 2 sessions fermées rapidement), ne pas le remplacer

- [x] **T12 — `SynthesisWorker`** (AC: 1)
  - [x] T12.1 Créer `android/data/src/main/kotlin/com/secondserve/data/worker/SynthesisWorker.kt` :
    ```kotlin
    @HiltWorker
    class SynthesisWorker @AssistedInject constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val coachingRepository: CoachingRepository,
        private val sessionRepository: SessionRepository,
        private val playerProfileRepository: PlayerProfileRepository,
        @VpsMistralEngine private val vpsMistralEngine: InferenceEngine
    ) : CoroutineWorker(context, params) {

        override suspend fun doWork(): Result = runWork()

        internal suspend fun runWork(): Result {
            val lastSynthesis = coachingRepository.getLatestSynthesis()
            val afterMs = lastSynthesis?.generatedAt ?: 0L

            val count = try {
                sessionRepository.countCompletedSince(afterMs)
            } catch (e: Exception) {
                Timber.e(e, "SynthesisWorker: countCompletedSince failed")
                return Result.retry()
            }

            if (count < 3) {
                Timber.d("SynthesisWorker: only %d new sessions, skipping (need 3)", count)
                return Result.success()
            }

            val sessions = try {
                sessionRepository.getCompletedSince(afterMs)
            } catch (e: Exception) {
                Timber.e(e, "SynthesisWorker: getCompletedSince failed")
                return Result.retry()
            }

            val profile = try {
                playerProfileRepository.buildMatchContextProfile()
            } catch (e: Exception) {
                Timber.e(e, "SynthesisWorker: buildMatchContextProfile failed")
                return Result.failure()
            }

            val prompt = buildSynthesisPrompt(sessions, profile, lastSynthesis)

            return when (val result = vpsMistralEngine.generate(prompt)) {
                is AppResult.Success -> {
                    when (val saveResult = coachingRepository.saveSynthesis(result.data, sessions.size)) {
                        is AppResult.Success -> {
                            Timber.d("SynthesisWorker: synthesis saved (%d sessions)", sessions.size)
                            Result.success()
                        }
                        is AppResult.Error -> {
                            Timber.e(saveResult.exception, "SynthesisWorker: DB write failed — will retry")
                            Result.retry()
                        }
                        AppResult.Loading -> Result.retry()
                    }
                }
                is AppResult.Error -> {
                    Timber.e(result.exception, "SynthesisWorker: VPS error — will retry")
                    Result.retry()
                }
                AppResult.Loading -> Result.failure()
            }
        }
    }
    ```
  - [x] T12.2 Ajouter `buildSynthesisPrompt(sessions, profile, lastSynthesis)` dans `SynthesisWorker` (voir Dev Notes pour le format)

- [x] **T13 — Mise à jour `PostMatchAnalysisWorker`** (AC: 1)
  - [x] T13.1 Injecter `SynthesisScheduler` dans le constructeur `PostMatchAnalysisWorker`
  - [x] T13.2 Dans `runWork()`, après le `Result.success()` final (save analysis OK), appeler `synthesisScheduler.schedule()` avant le return
  - [x] T13.3 Mettre à jour `PostMatchAnalysisWorkerTest` : ajouter `mock<SynthesisScheduler>()` dans le constructeur, vérifier `verify { synthesisScheduler.schedule() }` sur succès

- [x] **T14 — `CoachingUiState` + `CoachingSideEffect`** (AC: 2, 3)
  - [x] T14.1 Créer `android/feature/coaching/src/main/kotlin/com/secondserve/feature/coaching/CoachingUiState.kt` :
    ```kotlin
    data class CoachingUiState(
        val isLoading: Boolean = false,
        val analyses: List<CoachingAnalysis> = emptyList(),
        val synthesis: CoachingSynthesis? = null,
        val synthesisInProgress: Boolean = false,
        val error: String? = null
    )

    sealed class CoachingSideEffect {
        data class ShowError(val message: String) : CoachingSideEffect()
    }
    ```

- [x] **T15 — `CoachingViewModel`** (AC: 2, 3)
  - [x] T15.1 Créer `android/feature/coaching/src/main/kotlin/com/secondserve/feature/coaching/CoachingViewModel.kt` :
    - `@HiltViewModel class CoachingViewModel @Inject constructor(coachingRepository, sessionRepository, playerProfileRepository, @VpsMistralEngine vpsMistralEngine)`
    - `init { viewModelScope.launch { merge(observeLatestSynthesis, observeAllAnalyses).collect { ... } } }`
    - `fun generateNow()` — suspend dans intent, appelle directement VpsMistralEngine (voir Dev Notes)
  - Note : le ViewModel observe deux Flows simultanément → utiliser `combine()` de Kotlinx Coroutines (voir Dev Notes)

- [x] **T16 — `CoachingScreen`** (AC: 2, 3)
  - [x] T16.1 Créer `android/feature/coaching/src/main/kotlin/com/secondserve/feature/coaching/CoachingScreen.kt`
  - Scaffold avec TopAppBar "Coaching IA" + back button
  - LazyColumn : d'abord la SynthesisCard (ou un placeholder), puis le bouton "Générer maintenant", puis la liste des analyses individuelles
  - Voir Dev Notes pour le layout détaillé et la convention Orbit collectAsState/collectSideEffect

- [x] **T17 — Navigation** (AC: 3)
  - [x] T17.1 Dans `android/app/src/main/kotlin/com/secondserve/navigation/AppNavGraph.kt` : ajouter route `"coaching"` avec `CoachingScreen(onNavigateBack = ...)`
  - [x] T17.2 Dans `android/app/src/main/kotlin/com/secondserve/HomeScreen.kt` : ajouter paramètre `onNavigateToCoaching: () -> Unit` + bouton `OutlinedButton("Coaching IA")`
  - [x] T17.3 Dans `AppNavGraph`, mettre à jour `HomeScreen(onNavigateToCoaching = { navController.navigate("coaching") })`

- [x] **T18 — Tests** (AC: 1, 2, 3)
  - [x] T18.1 `SynthesisWorkerTest.kt` : via `TestListenableWorkerBuilder` — seuil < 3 (vérifie `Result.success()` sans appel VPS), seuil ≥ 3 (vérifie `saveSynthesis` appelé), erreur VPS (vérifie `Result.retry()`)
  - [x] T18.2 `CoachingRepositoryImplTest.kt` : ajouter tests `saveSynthesis`, `getLatestSynthesis`, `observeLatestSynthesis`, `observeAllAnalyses`
  - [x] T18.3 `PostMatchAnalysisWorkerTest.kt` : ajouter mock `SynthesisScheduler`, vérifier `schedule()` appelé après succès, NOT appelé après échec

## Dev Notes

### Architecture critique — Module `:feature:coaching`

Le module `:feature:coaching` a un `build.gradle.kts` **existant et complet** mais **aucun fichier .kt**. Toutes les dépendances sont déjà déclarées :

```
dependencies:
  implementation(project(":domain"))       ← CoachingRepository, SessionRepository, CoachingSynthesis
  implementation(project(":core:ui"))      ← SecondServeTheme
  implementation(project(":core:ai"))      ← InferenceEngine + @VpsMistralEngine qualifier ← CRITIQUE
  implementation libs.orbit.core/viewmodel/compose
  implementation libs.hilt.android + libs.hilt.navigation.compose
```

**Aucun changement à `build.gradle.kts` de `:feature:coaching` n'est nécessaire.**

### Chaîne d'injection — `SynthesisScheduler` dans `PostMatchAnalysisWorker`

`PostMatchAnalysisWorker` est dans `:data`. `SynthesisScheduler` est défini dans `:domain`. `:data` dépend déjà de `:domain`. **Aucun ajout de dépendance Gradle nécessaire.**

`SynthesisSchedulerImpl` doit être lié via `DataModule.kt` (provider, pas @Binds car pas abstract class).

### `@VpsMistralEngine` qualifier

Déjà défini dans Story 5.1 : `android/core/ai/src/main/kotlin/com/secondserve/core/ai/di/InferenceEngineQualifiers.kt`.

Usage dans `SynthesisWorker` (`:data`) : `@VpsMistralEngine private val vpsMistralEngine: InferenceEngine`
Usage dans `CoachingViewModel` (`:feature:coaching`) : `@VpsMistralEngine private val vpsMistralEngine: InferenceEngine`

Les deux modules ont déjà `implementation(project(":core:ai"))`. ✅

### Pattern Orbit MVI — `CoachingViewModel`

Suivre exactement le même pattern que `HistoryViewModel` :

```kotlin
@HiltViewModel
class CoachingViewModel @Inject constructor(
    private val coachingRepository: CoachingRepository,
    private val sessionRepository: SessionRepository,
    private val playerProfileRepository: PlayerProfileRepository,
    @VpsMistralEngine private val vpsMistralEngine: InferenceEngine
) : ViewModel(), ContainerHost<CoachingUiState, CoachingSideEffect> {

    override val container = container<CoachingUiState, CoachingSideEffect>(CoachingUiState(isLoading = true))

    init {
        viewModelScope.launch {
            combine(
                coachingRepository.observeLatestSynthesis(),
                coachingRepository.observeAllAnalyses()
            ) { synthesis, analyses ->
                CoachingUiState(isLoading = false, synthesis = synthesis, analyses = analyses)
            }
            .catch { e -> intent { reduce { state.copy(isLoading = false, error = e.message) } } }
            .collect { newState -> intent { reduce { newState } } }
        }
    }

    fun generateNow() = intent {
        reduce { state.copy(synthesisInProgress = true, error = null) }
        try {
            val lastSynthesis = coachingRepository.getLatestSynthesis()
            val afterMs = lastSynthesis?.generatedAt ?: 0L
            var sessions = sessionRepository.getCompletedSince(afterMs)
            // Fallback : si aucune session depuis la dernière synthèse, prendre les 3 plus récentes
            if (sessions.isEmpty()) {
                sessions = sessionRepository.getCompletedSince(0L).takeLast(3)
            }
            val profile = playerProfileRepository.buildMatchContextProfile()
            val prompt = buildSynthesisPrompt(sessions, profile, lastSynthesis)
            when (val result = vpsMistralEngine.generate(prompt)) {
                is AppResult.Success -> coachingRepository.saveSynthesis(result.data, sessions.size)
                is AppResult.Error -> reduce { state.copy(error = "Génération échouée — vérifiez la connexion") }
                AppResult.Loading -> Unit
            }
        } catch (e: Exception) {
            reduce { state.copy(error = e.message ?: "Erreur inconnue") }
        } finally {
            reduce { state.copy(synthesisInProgress = false) }
        }
    }
}
```

**`combine()` est `kotlinx.coroutines.flow.combine`** — pas besoin d'import spécial, déjà disponible via `implementation(libs.kotlinx.coroutines.android)` transitif.

### Construction du prompt synthèse

Format de prompt recommandé pour `buildSynthesisPrompt()` (à mettre dans `SynthesisWorker` ET copié/partagé dans `CoachingViewModel`) :

```kotlin
private fun buildSynthesisPrompt(
    sessions: List<Session>,
    profile: MatchContextProfile,
    lastSynthesis: CoachingSynthesis?
): String {
    val sessionsDesc = sessions.joinToString("\n") { s ->
        "- ${s.surface} / ${s.format.matchFormat.name} / Résultat: ${s.result ?: "inconnu"} / Score: ${s.scoreText ?: "?"}"
    }
    val lastSynthesisLine = if (lastSynthesis != null) {
        "\n\nSynthèse précédente (résumé) :\n${lastSynthesis.content.take(300)}..."
    } else ""
    val axesText = profile.activeWorkAxes.joinToString(", ").ifEmpty { "aucun" }

    return """
Tu es un coach tennis. Génère une synthèse transversale sur ${sessions.size} match(s) récent(s). Réponse en 5-7 phrases maximum.

Matchs analysés :
$sessionsDesc

Profil joueur :
- Classement FFT : ${profile.fftSeries ?: "non renseigné"}
- Style de jeu : ${profile.playStyle ?: "non renseigné"}
- Axes de travail actifs : $axesText$lastSynthesisLine

Identifie : les patterns récurrents sur ces matchs, l'évolution depuis la dernière synthèse (si disponible), l'axe d'amélioration prioritaire, une recommandation structurée. Cite les surfaces et résultats. Sois précis, pas générique.
    """.trimIndent()
}
```

**IMPORTANT** : cette fonction `buildSynthesisPrompt` doit être dupliquée dans `SynthesisWorker` ET `CoachingViewModel`. Ne pas créer un use case pour ça — c'est du formatage de prompt, pas de la logique métier.

### Pattern `CoachingScreen`

Suivre la même structure que `HistoryScreen` (Scaffold + TopAppBar + LazyColumn) :

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachingScreen(
    onNavigateBack: () -> Unit,
    viewModel: CoachingViewModel = hiltViewModel()
) {
    val state by viewModel.collectAsState()

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is CoachingSideEffect.ShowError -> { /* Snackbar ou Toast */ }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coaching IA") },
                navigationIcon = { TextButton(onClick = onNavigateBack) { Text("← Retour") } }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Section synthèse
                item {
                    Text("Synthèse multi-matchs", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                }
                item {
                    state.synthesis?.let { synth ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(synth.content, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${synth.sessionCount} matchs · ${synthDateFormat.format(Date(synth.generatedAt))}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    } ?: Text(
                        "Aucune synthèse disponible. Générez-en une ci-dessous.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Bouton Générer maintenant
                item {
                    if (state.synthesisInProgress) {
                        CircularProgressIndicator(Modifier.padding(vertical = 8.dp))
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.generateNow() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Générer maintenant") }
                    }
                }
                state.error?.let { err ->
                    item {
                        Text(err, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                // Section analyses individuelles
                if (state.analyses.isNotEmpty()) {
                    item {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text("Analyses post-match", style = MaterialTheme.typography.titleMedium)
                    }
                    items(state.analyses) { analysis ->
                        AnalysisItem(analysis)
                    }
                }
            }
        }
    }
}
```

`synthDateFormat` et `analysisDateFormat` : `SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)` (idem que `sessionDateFormat` dans `HistoryScreen`).

`AnalysisItem` : Card compacte avec le contenu de l'analyse + date.

`items(state.analyses)` nécessite `import androidx.compose.foundation.lazy.items`.

### Migration Room — exportSchema

Le module `:data` a `arg("room.schemaLocation", "$projectDir/schemas")` dans son `build.gradle.kts`. Room génère automatiquement le JSON de schéma v9. Ne pas modifier les fichiers de schéma manuellement.

### Conformité NFR-C3 — Données envoyées à Mistral

Le prompt de synthèse inclut uniquement : surface, format, résultat, score — **jamais** de numéro de licence FFT ni de données d'identification. `buildMatchContextProfile()` est déjà conforme NFR-C3.

### `PostMatchAnalysisWorker` — mise à jour constructeur

Le constructeur actuel a 4 paramètres : `context, params, sessionRepository, playerProfileRepository, coachingRepository, vpsMistralEngine`. Après T13, il en aura 5 (+ `synthesisScheduler`). Mettre à jour `PostMatchAnalysisWorkerTest` : ajouter `mockk<SynthesisScheduler>()`.

### `CoachingRepositoryImpl` — constructeur mis à jour

Avant : `(dao: CoachingCacheDao, analysisDao: CoachingAnalysisDao)`  
Après : `(dao: CoachingCacheDao, analysisDao: CoachingAnalysisDao, synthesisDao: CoachingSynthesisDao)`

Hilt injecte `synthesisDao` via `@Provides` dans `DataModule`. **Aucune annotation `@Inject` n'est needed sur `CoachingRepositoryImpl`** puisque le binding est via `CoachingModule.kt` (`@Binds`).

Attention : `CoachingModule.kt` utilise `@Binds abstract fun bindCoachingRepository(impl: CoachingRepositoryImpl): CoachingRepository`. Le provider `CoachingSynthesisDao` doit être déclaré dans `DataModule.kt` (pas dans `CoachingModule.kt`).

### Statut DB pour sessions fermées

Le champ `status` dans la table `sessions` contient la chaîne `"COMPLETED"` pour les sessions fermées. Confirmé dans `SessionRepositoryImpl.kt` (closeSession → `status = "COMPLETED"`). Utiliser cette chaîne littérale dans les queries `SessionDao`.

### `HomeScreen` — ajout du bouton

Ajouter un 5ème `OutlinedButton` "Coaching IA" après le bouton "Statistiques". `HomeScreen` nécessite un nouveau paramètre `onNavigateToCoaching: () -> Unit` — mettre à jour `AppNavGraph.kt` où `HomeScreen(...)` est appelé.

### Project Structure Notes

**Fichiers NOUVEAUX :**
```
android/domain/src/main/kotlin/com/secondserve/domain/
  ├── model/CoachingSynthesis.kt                     (T1.1)
  └── synthesis/SynthesisScheduler.kt                (T11.1)

android/data/src/main/kotlin/com/secondserve/data/
  ├── local/db/entity/CoachingSynthesisEntity.kt      (T4.1)
  ├── local/dao/CoachingSynthesisDao.kt               (T4.2)
  └── worker/
      ├── SynthesisWorker.kt                          (T12.1)
      └── SynthesisSchedulerImpl.kt                  (T11.2)

android/feature/coaching/src/main/kotlin/com/secondserve/feature/coaching/
  ├── CoachingUiState.kt                              (T14.1)
  ├── CoachingViewModel.kt                            (T15.1)
  └── CoachingScreen.kt                              (T16.1)

android/data/src/test/kotlin/com/secondserve/data/worker/
  └── SynthesisWorkerTest.kt                          (T18.1)
```

**Fichiers MODIFIÉS :**
```
android/domain/src/main/kotlin/com/secondserve/domain/repository/CoachingRepository.kt   (T2.1)
android/domain/src/main/kotlin/com/secondserve/domain/repository/SessionRepository.kt    (T3.1)
android/data/src/main/kotlin/com/secondserve/data/local/dao/CoachingAnalysisDao.kt       (T5.1)
android/data/src/main/kotlin/com/secondserve/data/local/dao/SessionDao.kt                (T7.1)
android/data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt        (T6.x)
android/data/src/main/kotlin/com/secondserve/data/repository/CoachingRepositoryImpl.kt   (T8.x)
android/data/src/main/kotlin/com/secondserve/data/repository/SessionRepositoryImpl.kt    (T9.x)
android/app/src/main/kotlin/com/secondserve/di/DataModule.kt                             (T10.x)
android/data/src/main/kotlin/com/secondserve/data/worker/PostMatchAnalysisWorker.kt      (T13.x)
android/data/src/test/kotlin/com/secondserve/data/worker/PostMatchAnalysisWorkerTest.kt  (T13.3)
android/app/src/main/kotlin/com/secondserve/navigation/AppNavGraph.kt                    (T17.1, T17.3)
android/app/src/main/kotlin/com/secondserve/HomeScreen.kt                                (T17.2)
```

### References

- `CoachingAnalysis` model (pattern pour `CoachingSynthesis`) : `android/domain/src/main/kotlin/com/secondserve/domain/model/CoachingAnalysis.kt`
- `CoachingAnalysisEntity` (pattern pour `CoachingSynthesisEntity`) : `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/CoachingAnalysisEntity.kt`
- `CoachingAnalysisDao` (pattern pour `CoachingSynthesisDao`) : `android/data/src/main/kotlin/com/secondserve/data/local/dao/CoachingAnalysisDao.kt`
- `CoachingRepositoryImpl` (fichier à modifier) : `android/data/src/main/kotlin/com/secondserve/data/repository/CoachingRepositoryImpl.kt`
- `PostMatchAnalysisWorker` (pattern exact pour `SynthesisWorker`, fichier à modifier) : `android/data/src/main/kotlin/com/secondserve/data/worker/PostMatchAnalysisWorker.kt`
- `AnalysisSchedulerImpl` (pattern exact pour `SynthesisSchedulerImpl`) : `android/data/src/main/kotlin/com/secondserve/data/worker/AnalysisSchedulerImpl.kt`
- `HistoryViewModel` (pattern Orbit MVI pour `CoachingViewModel`) : `android/feature/history/src/main/kotlin/com/secondserve/feature/history/HistoryViewModel.kt`
- `HistoryScreen` (pattern Compose pour `CoachingScreen`) : `android/feature/history/src/main/kotlin/com/secondserve/feature/history/HistoryScreen.kt`
- `DataModule` (providers Room à mettre à jour) : `android/app/src/main/kotlin/com/secondserve/di/DataModule.kt`
- `CoachingModule` (binding CoachingRepository) : `android/data/src/main/kotlin/com/secondserve/data/di/CoachingModule.kt`
- `SecondServeDatabase` (migrations + version actuelle 8) : `android/data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt`
- `AppNavGraph` (navigation à étendre) : `android/app/src/main/kotlin/com/secondserve/navigation/AppNavGraph.kt`
- `HomeScreen` (bouton à ajouter) : `android/app/src/main/kotlin/com/secondserve/HomeScreen.kt`
- `@VpsMistralEngine` qualifier : `android/core/ai/src/main/kotlin/com/secondserve/core/ai/di/InferenceEngineQualifiers.kt`
- Architecture FR-11 : `_bmad-output/planning-artifacts/architecture.md#FR-11`
- Epics Story 5.3, NFR-C3, FR-11 : `_bmad-output/planning-artifacts/epics.md#Story 5.3`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

Aucun blocage — implémentation directe sans régression.

### Completion Notes List

- T1-T3 : modèle domaine `CoachingSynthesis` et extensions des interfaces `CoachingRepository` / `SessionRepository` créés.
- T4-T7 : couche données — `CoachingSynthesisEntity`, `CoachingSynthesisDao`, `CoachingAnalysisDao.getAllAnalyses()`, `SessionDao.countCompletedSince/getCompletedSince` ajoutés.
- T6 : migration Room 8→9 crée la table `coaching_syntheses`, version incrémentée à 9.
- T8-T9 : `CoachingRepositoryImpl` étendu avec `saveSynthesis/getLatestSynthesis/observeLatestSynthesis/observeAllAnalyses` ; `SessionRepositoryImpl` étendu avec `countCompletedSince/getCompletedSince` (réutilise le pattern de mappage avec fallback).
- T10 : `DataModule` mis à jour avec migration 8→9, provider `CoachingSynthesisDao`, provider `SynthesisScheduler`.
- T11-T12 : `SynthesisScheduler` interface + `SynthesisSchedulerImpl` (WorkManager KEEP) + `SynthesisWorker` (logique seuil 3 sessions, génération via VPS, `buildSynthesisPrompt` en top-level internal).
- T13 : `PostMatchAnalysisWorker` injecte `SynthesisScheduler` et appelle `schedule()` après save réussi.
- T14-T16 : feature coaching complète — `CoachingUiState`, `CoachingViewModel` (Orbit MVI + `combine()` + `generateNow()`), `CoachingScreen` (Scaffold + LazyColumn, SynthesisCard distincte, AnalysisItem, bouton Générer).
- T17 : navigation — route `"coaching"` dans `AppNavGraph`, bouton "Coaching IA" dans `HomeScreen`.
- T18 : 6 tests `SynthesisWorkerTest`, 7 nouveaux tests `CoachingRepositoryImplTest`, 3 nouveaux tests `PostMatchAnalysisWorkerTest` — tous verts (31 tests data total, 0 failure).

### File List

**Nouveaux fichiers :**
- `android/domain/src/main/kotlin/com/secondserve/domain/model/CoachingSynthesis.kt`
- `android/domain/src/main/kotlin/com/secondserve/domain/synthesis/SynthesisScheduler.kt`
- `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/CoachingSynthesisEntity.kt`
- `android/data/src/main/kotlin/com/secondserve/data/local/dao/CoachingSynthesisDao.kt`
- `android/data/src/main/kotlin/com/secondserve/data/worker/SynthesisWorker.kt`
- `android/data/src/main/kotlin/com/secondserve/data/worker/SynthesisSchedulerImpl.kt`
- `android/feature/coaching/src/main/kotlin/com/secondserve/feature/coaching/CoachingUiState.kt`
- `android/feature/coaching/src/main/kotlin/com/secondserve/feature/coaching/CoachingViewModel.kt`
- `android/feature/coaching/src/main/kotlin/com/secondserve/feature/coaching/CoachingScreen.kt`
- `android/data/src/test/kotlin/com/secondserve/data/worker/SynthesisWorkerTest.kt`

**Fichiers modifiés :**
- `android/domain/src/main/kotlin/com/secondserve/domain/repository/CoachingRepository.kt`
- `android/domain/src/main/kotlin/com/secondserve/domain/repository/SessionRepository.kt`
- `android/data/src/main/kotlin/com/secondserve/data/local/dao/CoachingAnalysisDao.kt`
- `android/data/src/main/kotlin/com/secondserve/data/local/dao/SessionDao.kt`
- `android/data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt`
- `android/data/src/main/kotlin/com/secondserve/data/repository/CoachingRepositoryImpl.kt`
- `android/data/src/main/kotlin/com/secondserve/data/repository/SessionRepositoryImpl.kt`
- `android/app/src/main/kotlin/com/secondserve/di/DataModule.kt`
- `android/data/src/main/kotlin/com/secondserve/data/worker/PostMatchAnalysisWorker.kt`
- `android/app/src/main/kotlin/com/secondserve/navigation/AppNavGraph.kt`
- `android/app/src/main/kotlin/com/secondserve/HomeScreen.kt`
- `android/data/src/test/kotlin/com/secondserve/data/worker/PostMatchAnalysisWorkerTest.kt`
- `android/data/src/test/kotlin/com/secondserve/data/repository/CoachingRepositoryImplTest.kt`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`

## Change Log

- 2026-06-23 : Implémentation complète story 5.3 — synthèse IA multi-matchs. Room v9, SynthesisWorker, CoachingScreen, navigation Coaching IA. 31 tests unitaires verts, build APK réussi.
