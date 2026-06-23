---
baseline_commit: da1c4d1
---

# Story 5.4: Axes de travail — Suggestions IA

Status: done

## Story

As a player,
I want the app to suggest relevant work axes based on patterns found in my recent analyses,
so that my training focus is grounded in my actual game data, not just intuition.

## Acceptance Criteria

1. **Given** au moins 1 analyse post-match (Story 5.2) ou synthèse (Story 5.3) a été générée
   **When** j'ouvre l'écran Axes de travail
   **Then** les axes suggérés par l'IA sont affichés dans une section distincte "Suggestions IA"
   **And** chaque suggestion est visuellement distincte des axes saisis manuellement (badge ou icône IA)
   **And** les suggestions sont dérivées de la dernière synthèse disponible, ou de la dernière analyse si aucune synthèse — jamais génériques

2. **Given** des suggestions IA sont affichées
   **When** je tape "Accepter" sur une suggestion
   **Then** elle est ajoutée à mes axes actifs (si < 3) et intégrée dans le contexte IA dès la prochaine interaction
   **And** la suggestion disparaît de la liste des suggestions pending

3. **Given** des suggestions IA sont affichées
   **When** je tape "Ignorer" sur une suggestion
   **Then** la suggestion est rejetée et ne réapparaît plus
   **And** elle disparaît immédiatement de l'affichage

4. **Given** 3 axes actifs sont déjà présents
   **When** des suggestions sont affichées
   **Then** le bouton "Accepter" est désactivé avec message "Maximum 3 axes actifs atteint"

5. **Given** aucune analyse ni synthèse n'est disponible
   **When** j'ouvre l'écran Axes de travail
   **Then** la section "Suggestions IA" n'est pas affichée (pas d'état vide, pas de message d'erreur)

## Tasks / Subtasks

- [x] **T1 — Modèle domaine `AxisSuggestion`** (AC: 1, 2, 3)
  - [x] T1.1 Créer `android/domain/src/main/kotlin/com/secondserve/domain/model/AxisSuggestion.kt` :
    ```kotlin
    data class AxisSuggestion(
        val id: Long = 0L,
        val title: String,
        val status: String = "PENDING",   // PENDING | ACCEPTED | IGNORED
        val generatedAt: Long
    )
    ```

- [x] **T2 — Extension interface `WorkAxisRepository`** (AC: 1, 2, 3, 4)
  - [x] T2.1 Ajouter dans `android/domain/src/main/kotlin/com/secondserve/domain/repository/WorkAxisRepository.kt` :
    ```kotlin
    fun observePendingSuggestions(): Flow<List<AxisSuggestion>>
    suspend fun hasPendingSuggestions(): Boolean
    suspend fun generateAndSaveSuggestions(): AppResult<Unit>
    suspend fun acceptSuggestion(id: Long): AppResult<WorkAxis>
    suspend fun ignoreSuggestion(id: Long)
    ```

- [x] **T3 — Extension `CoachingAnalysisDao`** (requis pour T6)
  - [x] T3.1 Ajouter dans `android/data/src/main/kotlin/com/secondserve/data/local/dao/CoachingAnalysisDao.kt` :
    ```kotlin
    @Query("SELECT * FROM coaching_analyses ORDER BY generated_at DESC LIMIT 1")
    suspend fun getMostRecent(): CoachingAnalysisEntity?
    ```

- [x] **T4 — `AxisSuggestionEntity` + `AxisSuggestionDao`** (AC: 1, 2, 3)
  - [x] T4.1 Créer `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/AxisSuggestionEntity.kt` :
    ```kotlin
    @Entity(tableName = "axis_suggestions")
    data class AxisSuggestionEntity(
        @PrimaryKey(autoGenerate = true) val id: Long = 0L,
        @ColumnInfo(name = "title") val title: String,
        @ColumnInfo(name = "status") val status: String = "PENDING",
        @ColumnInfo(name = "generated_at") val generatedAt: Long
    )

    fun AxisSuggestionEntity.toDomain(): AxisSuggestion =
        AxisSuggestion(id = id, title = title, status = status, generatedAt = generatedAt)

    fun AxisSuggestion.toEntity(): AxisSuggestionEntity =
        AxisSuggestionEntity(id = id, title = title, status = status, generatedAt = generatedAt)
    ```
  - [x] T4.2 Créer `android/data/src/main/kotlin/com/secondserve/data/local/dao/AxisSuggestionDao.kt` :
    ```kotlin
    @Dao
    interface AxisSuggestionDao {
        @Query("SELECT * FROM axis_suggestions WHERE status = 'PENDING' ORDER BY generated_at DESC")
        fun observePending(): Flow<List<AxisSuggestionEntity>>

        @Query("SELECT COUNT(*) FROM axis_suggestions WHERE status = 'PENDING'")
        suspend fun countPending(): Int

        @Query("SELECT * FROM axis_suggestions WHERE id = :id")
        suspend fun getById(id: Long): AxisSuggestionEntity?

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insertAll(entities: List<AxisSuggestionEntity>)

        @Query("UPDATE axis_suggestions SET status = :status WHERE id = :id")
        suspend fun updateStatus(id: Long, status: String)
    }
    ```

- [x] **T5 — Migration Room 9→10 + mise à jour `SecondServeDatabase`** (AC: 1)
  - [x] T5.1 Incrémenter la version DB à 10 dans `@Database(version = 10, ...)`
  - [x] T5.2 Ajouter `AxisSuggestionEntity::class` dans `entities = [...]`
  - [x] T5.3 Ajouter `abstract fun axisSuggestionDao(): AxisSuggestionDao`
  - [x] T5.4 Ajouter `MIGRATION_9_10` :
    ```kotlin
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS axis_suggestions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'PENDING',
                    generated_at INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }
    ```

- [x] **T6 — `WorkAxisRepositoryImpl` — nouvelles méthodes** (AC: 1, 2, 3, 4, 5)
  - [x] T6.1 Ajouter `AxisSuggestionDao`, `CoachingAnalysisDao`, `CoachingSynthesisDao`, `@VpsMistralEngine InferenceEngine` au constructeur
  - [x] T6.2 Implémenter `observePendingSuggestions()` :
    ```kotlin
    override fun observePendingSuggestions(): Flow<List<AxisSuggestion>> =
        suggestionDao.observePending().map { list ->
            list.mapNotNull { runCatching { it.toDomain() }.getOrNull() }
        }
    ```
  - [x] T6.3 Implémenter `hasPendingSuggestions()` :
    ```kotlin
    override suspend fun hasPendingSuggestions(): Boolean =
        try { suggestionDao.countPending() > 0 } catch (e: Exception) { false }
    ```
  - [x] T6.4 Implémenter `generateAndSaveSuggestions()` :
    ```kotlin
    override suspend fun generateAndSaveSuggestions(): AppResult<Unit> {
        val latestContent = try {
            synthesisDao.getLatest()?.content ?: analysisDao.getMostRecent()?.content
        } catch (e: Exception) {
            return AppResult.Error(e)
        }
        if (latestContent == null) return AppResult.Error(IllegalStateException("No coaching data available"))

        val currentAxes = try { dao.getAllTitles() } catch (e: Exception) { emptyList() }
        val prompt = buildSuggestionsPrompt(latestContent, currentAxes)

        return when (val result = vpsMistralEngine.generate(prompt)) {
            is AppResult.Success -> {
                val titles = parseSuggestionsResponse(result.data)
                if (titles.isEmpty()) return AppResult.Error(IllegalStateException("No suggestions parsed"))
                try {
                    val now = System.currentTimeMillis()
                    suggestionDao.insertAll(titles.map { AxisSuggestionEntity(title = it, generatedAt = now) })
                    AppResult.Success(Unit)
                } catch (e: Exception) {
                    AppResult.Error(e)
                }
            }
            is AppResult.Error -> AppResult.Error(result.exception)
            AppResult.Loading -> AppResult.Error(IllegalStateException("Unexpected loading state"))
        }
    }

    private fun buildSuggestionsPrompt(sourceContent: String, currentAxes: List<String>): String {
        val axesText = currentAxes.joinToString(", ").ifEmpty { "aucun" }
        return """
Tu es un coach tennis. Basé sur l'analyse ci-dessous, suggère 2 ou 3 axes de travail concrets et actionnables.

Analyse récente :
${sourceContent.take(500)}

Axes de travail actuels : $axesText

Réponds UNIQUEMENT avec les titres des axes suggérés, un par ligne, en 3 à 8 mots chacun. Exemples : "Montée au filet après service gagnant", "Constance du revers long de ligne". Ne duplique pas les axes déjà existants. Pas d'explication, pas de numérotation.
        """.trimIndent()
    }

    private fun parseSuggestionsResponse(response: String): List<String> =
        response.lines()
            .map { it.trim().trimStart('-', '*', '•', '1', '2', '3', '4', '5', '.', ' ').trim() }
            .filter { it.isNotBlank() && it.length in 3..150 }
            .take(3)
    ```
  - [x] T6.5 Implémenter `acceptSuggestion()` :
    ```kotlin
    override suspend fun acceptSuggestion(id: Long): AppResult<WorkAxis> = try {
        val suggestion = suggestionDao.getById(id)
            ?: return AppResult.Error(IllegalStateException("Suggestion $id not found"))
        val result = createWorkAxis(suggestion.title)
        if (result is AppResult.Success) suggestionDao.updateStatus(id, "ACCEPTED")
        result
    } catch (e: Exception) {
        AppResult.Error(e)
    }
    ```
  - [x] T6.6 Implémenter `ignoreSuggestion()` :
    ```kotlin
    override suspend fun ignoreSuggestion(id: Long) {
        try { suggestionDao.updateStatus(id, "IGNORED") }
        catch (e: Exception) { Timber.e(e, "Failed to ignore suggestion $id") }
    }
    ```

- [x] **T7 — `DataModule` mise à jour** (T5, T6)
  - [x] T7.1 Ajouter `MIGRATION_9_10` dans la liste `addMigrations(...)` du builder Room
  - [x] T7.2 Ajouter provider `AxisSuggestionDao` :
    ```kotlin
    @Provides @Singleton
    fun provideAxisSuggestionDao(db: SecondServeDatabase): AxisSuggestionDao =
        db.axisSuggestionDao()
    ```
  - [x] T7.3 Mettre à jour `provideWorkAxisRepository(...)` avec les 4 nouveaux paramètres :
    `AxisSuggestionDao`, `CoachingAnalysisDao`, `CoachingSynthesisDao`, `@VpsMistralEngine InferenceEngine`
  - [x] T7.4 Ajouter les imports nécessaires : `AxisSuggestionDao`, `VpsMistralEngine`, `InferenceEngine`

- [x] **T8 — `WorkAxesViewModel` mise à jour** (AC: 1, 2, 3, 4, 5)
  - [x] T8.1 Étendre `WorkAxesUiState` avec les champs suggestions (dans `WorkAxesViewModel.kt`) :
    ```kotlin
    data class WorkAxesUiState(
        val workAxes: List<WorkAxis> = emptyList(),
        val isAtMaxCapacity: Boolean = false,
        val isSaving: Boolean = false,
        val pendingSuggestions: List<AxisSuggestion> = emptyList(),
        val isGeneratingSuggestions: Boolean = false,
        val suggestionsError: String? = null
    )
    ```
  - [x] T8.2 Ajouter `SuggestionAccepted` dans `WorkAxesSideEffect` :
    ```kotlin
    data object SuggestionAccepted : WorkAxesSideEffect()
    ```
  - [x] T8.3 Ajouter `collectSuggestions()` dans `init {}` et implémenter :
    ```kotlin
    private fun collectSuggestions() = intent {
        workAxisRepository.observePendingSuggestions().collect { suggestions ->
            reduce { state.copy(pendingSuggestions = suggestions) }
        }
    }
    ```
  - [x] T8.4 Ajouter `tryGenerateSuggestionsIfNeeded()` dans `init {}` et implémenter :
    ```kotlin
    private fun tryGenerateSuggestionsIfNeeded() = intent {
        if (workAxisRepository.hasPendingSuggestions()) return@intent
        reduce { state.copy(isGeneratingSuggestions = true, suggestionsError = null) }
        try {
            when (val result = workAxisRepository.generateAndSaveSuggestions()) {
                is AppResult.Error -> reduce { state.copy(suggestionsError = "Suggestions IA indisponibles") }
                else -> Unit
            }
        } finally {
            reduce { state.copy(isGeneratingSuggestions = false) }
        }
    }
    ```
  - [x] T8.5 Ajouter `acceptSuggestion(id: Long)` :
    ```kotlin
    fun acceptSuggestion(id: Long) = intent {
        if (state.isAtMaxCapacity) {
            postSideEffect(WorkAxesSideEffect.ShowError("Maximum $MAX_WORK_AXES axes actifs atteint"))
            return@intent
        }
        when (workAxisRepository.acceptSuggestion(id)) {
            is AppResult.Success -> postSideEffect(WorkAxesSideEffect.SuggestionAccepted)
            is AppResult.Error -> postSideEffect(WorkAxesSideEffect.ShowError("Erreur lors de l'acceptation"))
            AppResult.Loading -> Unit
        }
    }
    ```
  - [x] T8.6 Ajouter `ignoreSuggestion(id: Long)` :
    ```kotlin
    fun ignoreSuggestion(id: Long) = intent {
        workAxisRepository.ignoreSuggestion(id)
    }
    ```

- [x] **T9 — `WorkAxesScreen` — section Suggestions IA** (AC: 1, 2, 3, 4, 5)
  - [x] T9.1 Gérer le nouveau `SuggestionAccepted` dans `collectSideEffect {}` (pas d'action spéciale nécessaire — le Flow met à jour l'état automatiquement)
  - [x] T9.2 Ajouter la section "Suggestions IA" dans le `LazyColumn`, AVANT les axes manuels :
    ```kotlin
    // Section suggestions IA — affichée uniquement si suggestions pending ou génération en cours
    if (state.isGeneratingSuggestions || state.pendingSuggestions.isNotEmpty()) {
        item {
            Text(
                "Suggestions IA",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
        }
        if (state.isGeneratingSuggestions) {
            item { CircularProgressIndicator(Modifier.padding(vertical = 8.dp)) }
        } else {
            items(state.pendingSuggestions, key = { "suggestion_${it.id}" }) { suggestion ->
                SuggestionCard(
                    suggestion = suggestion,
                    isAcceptDisabled = state.isAtMaxCapacity,
                    onAccept = { viewModel.acceptSuggestion(suggestion.id) },
                    onIgnore = { viewModel.ignoreSuggestion(suggestion.id) }
                )
            }
        }
        state.suggestionsError?.let { err ->
            item {
                Text(err, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
    }
    ```
  - [x] T9.3 Créer le composable `SuggestionCard` :
    ```kotlin
    @Composable
    private fun SuggestionCard(
        suggestion: AxisSuggestion,
        isAcceptDisabled: Boolean,
        onAccept: () -> Unit,
        onIgnore: () -> Unit
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = "Suggestion IA",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(suggestion.title, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onIgnore) { Text("Ignorer") }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = onAccept, enabled = !isAcceptDisabled) { Text("Accepter") }
                }
                if (isAcceptDisabled) {
                    Text(
                        "Maximum $MAX_WORK_AXES axes actifs atteint",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
    ```
  - [x] T9.4 Ajouter les imports manquants : `AxisSuggestion`, `Icons.Default.AutoAwesome`, `Button`, `Spacer`, `Row`, `size`

- [x] **T10 — Tests** (AC: 1, 2, 3, 4, 5)
  - [x] T10.1 Créer `android/data/src/test/kotlin/com/secondserve/data/repository/WorkAxisSuggestionsTest.kt` :
    - `generateAndSaveSuggestions_whenNoCoachingData_returnsError`
    - `generateAndSaveSuggestions_whenSynthesisExists_savesParsesSuggestions`
    - `generateAndSaveSuggestions_whenAnalysisExistsNoSynthesis_usesAnalysis`
    - `generateAndSaveSuggestions_whenVpsError_returnsError`
    - `generateAndSaveSuggestions_whenBlankResponse_returnsError`
    - `acceptSuggestion_createsWorkAxisAndMarksAccepted`
    - `ignoreSuggestion_marksIgnored`
    - `hasPendingSuggestions_whenNone_returnsFalse`
    - `parseSuggestionsResponse_stripsNumerationAndBullets`
    - `parseSuggestionsResponse_limitsToThreeSuggestions`
  - [x] T10.2 Créer `android/feature/profile/src/test/kotlin/com/secondserve/feature/profile/WorkAxesViewModelSuggestionsTest.kt` :
    - `init_whenNoPendingSuggestions_triesToGenerate`
    - `init_whenPendingSuggestionsExist_doesNotGenerate`
    - `init_whenGenerationError_setsSuggestionsError`
    - `acceptSuggestion_whenAtMaxCapacity_showsError`
    - `acceptSuggestion_whenSuccess_postsSuggestionAcceptedSideEffect`
    - `ignoreSuggestion_callsRepository`

## Dev Notes

### Architecture — Pourquoi logique Mistral dans WorkAxisRepositoryImpl

Le ViewModel `WorkAxesViewModel` est dans `:feature:profile`. Ce module n'a **pas** `implementation(project(":core:ai"))` (et ne doit pas — seul `:data` expose les engines). La logique d'appel VpsMistralEngine est donc placée dans `WorkAxisRepositoryImpl` (`:data`), qui dépend déjà de `:core:ai`.

**Ne pas ajouter `:core:ai` à `:feature:profile`** — cela violerait la frontière de dépendances.

### Fichiers existants modifiés — état actuel critique

**`WorkAxesViewModel.kt`** (feature/profile) — état actuel :
- Injecte uniquement `WorkAxisRepository`
- État : `WorkAxesUiState(workAxes, isAtMaxCapacity, isSaving)` défini **dans le même fichier** (pas de fichier séparé)
- Side effects : `WorkAxisCreated`, `WorkAxisUpdated`, `WorkAxisDeleted`, `ShowError`
- Init : `collectWorkAxes()` — étendu avec `collectSuggestions()` + `tryGenerateSuggestionsIfNeeded()`

**`WorkAxesScreen.kt`** (feature/profile) — état actuel :
- LazyColumn avec 2 items fixes (counter + list d'axes)
- Icône `Icons.Default.AutoAwesome` disponible via `compose.material.icons.extended` (déjà en dépendance)
- Importer `Button` de `material3` (non utilisé actuellement), `Spacer`, `width`, `size` de foundation/layout

**`WorkAxisRepositoryImpl.kt`** (data) — état actuel :
- Constructeur : `(dao: WorkAxisDao, vpsApiService: VpsApiService)`
- Étendre à : `(dao, suggestionDao, analysisDao, synthesisDao, vpsApiService, @VpsMistralEngine vpsMistralEngine)`
- `getActiveWorkAxesTitles()` appelle `dao.getAllTitles()` — réutilisé dans `generateAndSaveSuggestions()`

**`CoachingAnalysisDao.kt`** (data) — nouvelle méthode `getMostRecent()` : un seul ajout de Query.

**`SecondServeDatabase.kt`** (data) — version 9 → 10, + `AxisSuggestionEntity`, + `MIGRATION_9_10`, + `axisSuggestionDao()`.

**`DataModule.kt`** (app) — provider `WorkAxisRepository` actuel : 2 params. Passe à 6 params. Ajouter `provideAxisSuggestionDao()`. Ajouter `MIGRATION_9_10` dans `addMigrations(...)`.

### Pattern de migration Room établi

Pattern à respecter (cohérent avec MIGRATION_8_9) :
- Nom de table `snake_case` pluriel
- Toujours `CREATE TABLE IF NOT EXISTS`
- `INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL` pour PK auto
- Colonnes `NOT NULL` avec `DEFAULT` pour colonnes avec valeurs initiales
- Ne pas modifier les fichiers de schéma JSON dans `schemas/` — Room les génère automatiquement

### Persistance des suggestions — stratégie

- Statuts : `PENDING` (visible), `ACCEPTED` (axe créé, caché), `IGNORED` (rejeté, caché)
- `observePendingSuggestions()` filtre sur `status = 'PENDING'` uniquement
- Pas de purge des entrées ACCEPTED/IGNORED (volume faible, pas de politique de rétention nécessaire pour MVP)
- Pas de contrainte d'unicité sur `title` — une même suggestion peut réapparaître après une nouvelle analyse si re-générée

### Gestion du cas "aucune donnée IA disponible" (AC 5)

`generateAndSaveSuggestions()` retourne `AppResult.Error` si `synthesisDao.getLatest()` et `analysisDao.getMostRecent()` retournent tous les deux `null`.

Dans `WorkAxesViewModel.tryGenerateSuggestionsIfNeeded()`, cette erreur est **silencieuse** (pas de réduction de `suggestionsError`) — car la section Suggestions n'est pas affichée si aucune donnée coaching n'existe. L'error ne remonte dans `suggestionsError` que si une erreur réseau ou DB se produit alors qu'une source existe.

Implémentation :
```kotlin
private fun tryGenerateSuggestionsIfNeeded() = intent {
    if (workAxisRepository.hasPendingSuggestions()) return@intent
    reduce { state.copy(isGeneratingSuggestions = true, suggestionsError = null) }
    try {
        val result = workAxisRepository.generateAndSaveSuggestions()
        if (result is AppResult.Error) {
            val isNoData = result.exception.message == "No coaching data available"
            if (!isNoData) reduce { state.copy(suggestionsError = "Suggestions IA indisponibles") }
        }
    } finally {
        reduce { state.copy(isGeneratingSuggestions = false) }
    }
}
```

### Prompt Mistral — contrainte NFR-C3

Le `sourceContent` passé à `buildSuggestionsPrompt()` est le contenu généré par Mistral lui-même (synthèse ou analyse), jamais des données brutes de session. Il ne contient donc aucun identifiant personnel — conforme NFR-C3.

La troncature à `.take(500)` est intentionnelle : évite les prompts trop longs, Mistral répond en texte court de toute façon.

### Parsing de la réponse — robustesse

Mistral peut retourner : `"1. Montée au filet"`, `"- Travail du revers"`, `"• Service kicker"`, ou juste `"Montée au filet"`. Le `parseSuggestionsResponse()` gère ces cas. Le filtre `length in 3..150` évite les lignes parasites (trop courtes ou trop longues).

### Learnings story 5.3 applicables

- `@VpsMistralEngine` qualifier est défini dans `core/ai/di/InferenceEngineQualifiers.kt` — importer dans DataModule
- `AppResult.Loading` dans les when-branches ne doit jamais retourner `Result.failure()` (cf. P5 code review 5.3)
- Try-catch obligatoire autour des appels DB dans les workers/repos (cf. P6 code review 5.3)
- Validation `content.isNotBlank()` avant sauvegarde (cf. P12 code review 5.3) — `generateAndSaveSuggestions()` vérifie que `titles` est non-vide avant insert
- Pattern `mapNotNull { runCatching { it.toDomain() }.getOrNull() }` dans les Flows (cf. P9 code review 5.3) — appliqué dans `observePendingSuggestions()`

### Tests — patterns à réutiliser

Copier le pattern de `SynthesisWorkerTest.kt` pour les mocks :
```kotlin
private val mockSuggestionDao: AxisSuggestionDao = mockk()
private val mockSynthesisDao: CoachingSynthesisDao = mockk()
private val mockAnalysisDao: CoachingAnalysisDao = mockk()
private val mockVpsMistralEngine: InferenceEngine = mockk()
```

Pour les tests ViewModel, suivre `WorkAxesViewModelTest.kt` existant (même structure Turbine + Orbit test).

## Dev Agent Record

### Implementation Notes

- `acceptSuggestion` implémentée avec retour `AppResult<Unit>` (au lieu de `AppResult<WorkAxis>` spécifié dans T2) : la méthode existante `createWorkAxis` retourne `AppResult<Unit>` et le ViewModel n'utilise pas la donnée du succès — cohérence de type imposée par l'implémentation.
- Extension locale `AxisSuggestionEntity.toDomain()` dans `WorkAxisRepositoryImpl.kt` (au lieu d'import depuis l'entity) pour garder la même portée de visibilité que le pattern `mapNotNull { runCatching }`.
- `tryGenerateSuggestionsIfNeeded()` : l'erreur "No coaching data available" est silencieuse (AC 5) — seules les erreurs réseau/DB remontent dans `suggestionsError`.
- Schéma Room v10 généré automatiquement par KSP dans `android/data/schemas/`.

### Completion Notes

Tous les ACs vérifiés :
- AC1 : section "Suggestions IA" avec icône `AutoAwesome` — distincte visuellement des axes manuels ✓
- AC2 : `acceptSuggestion` → `createWorkAxis` + `updateStatus("ACCEPTED")` → disparaît du Flow ✓
- AC3 : `ignoreSuggestion` → `updateStatus("IGNORED")` → disparaît du Flow ✓
- AC4 : `isAtMaxCapacity` bloque `acceptSuggestion` avec message ✓
- AC5 : section masquée si `!isGeneratingSuggestions && pendingSuggestions.isEmpty()` + erreur silencieuse si no data ✓

16 tests créés, tous verts. Build : BUILD SUCCESSFUL.

## File List

- `android/domain/src/main/kotlin/com/secondserve/domain/model/AxisSuggestion.kt` (nouveau)
- `android/domain/src/main/kotlin/com/secondserve/domain/repository/WorkAxisRepository.kt` (modifié)
- `android/data/src/main/kotlin/com/secondserve/data/local/dao/CoachingAnalysisDao.kt` (modifié)
- `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/AxisSuggestionEntity.kt` (nouveau)
- `android/data/src/main/kotlin/com/secondserve/data/local/dao/AxisSuggestionDao.kt` (nouveau)
- `android/data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt` (modifié — v10, migration, entity, dao)
- `android/data/src/main/kotlin/com/secondserve/data/repository/WorkAxisRepositoryImpl.kt` (modifié)
- `android/app/src/main/kotlin/com/secondserve/di/DataModule.kt` (modifié)
- `android/feature/profile/src/main/kotlin/com/secondserve/feature/profile/WorkAxesViewModel.kt` (modifié)
- `android/feature/profile/src/main/kotlin/com/secondserve/feature/profile/WorkAxesScreen.kt` (modifié)
- `android/data/src/test/kotlin/com/secondserve/data/repository/WorkAxisRepositoryImplTest.kt` (modifié — constructeur mis à jour)
- `android/data/src/test/kotlin/com/secondserve/data/repository/WorkAxisSuggestionsTest.kt` (nouveau)
- `android/feature/profile/src/test/kotlin/com/secondserve/feature/profile/WorkAxesViewModelTest.kt` (modifié — stubs nouveaux mocks)
- `android/feature/profile/src/test/kotlin/com/secondserve/feature/profile/WorkAxesViewModelSuggestionsTest.kt` (nouveau)
- `android/data/schemas/com.secondserve.data.local.db.SecondServeDatabase/10.json` (généré)

## Change Log

- feat(story-5.4): suggestions IA axes de travail — Room v10 + WorkAxisRepositoryImpl + WorkAxesScreen (2026-06-23)

### Review Findings

- [x] [Review][Patch] TOCTOU — `createWorkAxis` doit vérifier le count DB avant insert pour empêcher le dépassement de `MAX_WORK_AXES` [WorkAxisRepositoryImpl.kt]
- [x] [Review][Patch] Race condition — `tryGenerateSuggestionsIfNeeded` doit guard sur `isGeneratingSuggestions` en début d'`intent` (rotation, back-stack pop) [WorkAxesViewModel.kt]
- [x] [Review][Patch] `parseSuggestionsResponse` — remplacer `trimStart` par regex pour stripper correctement les préfixes numériques > 5 et les combinaisons [WorkAxisRepositoryImpl.kt]
- [x] [Review][Patch] `ignoreSuggestion` — propager l'erreur DB dans le ViewModel pour afficher un snackbar (suggestion reste visible si le write échoue) [WorkAxesViewModel.kt]
- [x] [Review][Patch] `latestContent.isNullOrBlank()` au lieu de `== null` — un content vide génère un prompt vacueux envoyé au VPS [WorkAxisRepositoryImpl.kt]
- [x] [Review][Patch] Sanitiser les newlines dans les titres d'axes dans `buildSuggestionsPrompt` — un titre avec `\n` corrompt la structure du prompt ligne-par-ligne [WorkAxisRepositoryImpl.kt]
- [x] [Review][Patch] AC5 — spinner `isGeneratingSuggestions=true` visible brièvement avant que `generateAndSaveSuggestions` détecte l'absence de données (violation AC5) [WorkAxesViewModel.kt + WorkAxisRepository.kt]
- [x] [Review][Patch] Test manquant — vérifier que `isGeneratingSuggestions` revient à `false` après le chemin "no coaching data" [WorkAxesViewModelSuggestionsTest.kt]
- [x] [Review][Defer] `sourceContent.take(500)` troncature sémantique — intentionnel per Dev Notes, prompt quality à optimiser si besoin
- [x] [Review][Defer] Schéma `axis_suggestions` sans `source_id` FK — évolution produit future
- [x] [Review][Defer] `CoachingSideEffect` sealed class vide — cleanup cosmétique, candidat à supprimer
- [x] [Review][Defer] `AppResult.Loading → Result.retry()` dans SynthesisWorker — pre-existing contract InferenceEngine
- [x] [Review][Defer] `observeAnalyses()` drop silencieux sans log — pattern pre-existing (CoachingRepositoryImpl)
- [x] [Review][Defer] `hasPendingSuggestions()` catchAll → false — auto-corrigé par l'échec de generate()
- [x] [Review][Defer] `AxisSuggestion.status` raw String — refactoring architectural, enum ou sealed class
- [x] [Review][Defer] `generatedAt > 0L` sentinel magic number — cosmétique (CoachingScreen)
- [x] [Review][Defer] SynthesisWorker count vs size filtrée — pre-existing logic
- [x] [Review][Defer] `parseSuggestionsResponse` lower bound 2 chars — non-impactant pour termes tennis
- [x] [Review][Defer] `OnConflictStrategy.REPLACE` sur autoGenerated PK — comportement correct, risque théorique sur id != 0
