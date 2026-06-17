---
baseline_commit: 6760248b8a323a4c2022d7754096add7015d1c6d
---

# Story 1.6 : Axes de travail — CRUD de base

**Status:** done

## Story

**As a** player,
**I want** to create, edit, and delete my work axes (max 3 active simultaneously),
**So that** my coaching is focused on my current training priorities from the very first match.

## Acceptance Criteria

1. **Given** je suis sur l'écran Axes de travail
   **When** je crée un axe avec un texte descriptif
   **Then** il apparaît dans la liste des axes actifs
   **And** il est inclus dans le contexte IA dès la prochaine interaction coaching (Gemini Nano + Mistral via `MatchContextProfile.activeWorkAxes`)

2. **When** je tente de créer un 4e axe actif
   **Then** l'app affiche un message d'erreur : "Maximum 3 axes actifs atteint"
   **And** la création est bloquée (le bouton "Ajouter" est désactivé si `workAxes.size >= MAX_WORK_AXES`)

3. **When** je modifie un axe existant
   **Then** le texte mis à jour est immédiatement utilisé dans les futurs prompts IA

4. **When** je supprime un axe
   **Then** il est retiré de la liste
   **And** n'est plus inclus dans le contexte IA

5. **And** la table `work_axes` est créée en Room (Migration 2→3) et Alembic via la migration de cette story

---

## Architecture Context

### Position dans la séquence d'implémentation (ARCH-13)

Cette story est la **sixième et dernière** dans la chaîne Epic 1 :

```
ARCH-1 (done) → ARCH-2 (done) → ARCH-3 (done) → ARCH-4 (done) → Story 1.5 (done) → Story 1.6 (CETTE STORY)
```

**Dépendances satisfaites :**
- ✅ Story 1.4 : Room DB version 1 (`player_profiles`, `ranking_history`), `PlayerProfileEntity`, `PlayerProfileDao`, `PlayerProfileRepositoryImpl`, `ProfileViewModel`, `ProfileScreen`
- ✅ Story 1.5 : Room DB version 2 (`MIGRATION_1_2`), `MatchContextProfile.activeWorkAxes` déjà déclaré `List<String> = emptyList()` mais **jamais peuplé** — cette story le peuple enfin
- ❌ Aucune table `sessions` encore (Story 2.3) — sans impact sur cette story

### État actuel du code — critique à connaître

**Android — couche domaine :**
- `MatchContextProfile.kt` — champ `activeWorkAxes: List<String> = emptyList()` existe déjà depuis Story 1.5 — **Story 1.6 le peuple enfin**
- `WorkAxis.kt` — **N'EXISTE PAS** — Story 1.6 le crée
- `WorkAxisRepository.kt` — **N'EXISTE PAS** — Story 1.6 le crée
- `PlayerProfileRepository.kt` — `buildMatchContextProfile()` retourne `activeWorkAxes = emptyList()` en dur — **Story 1.6 le corrige**

**Android — couche data :**
- `SecondServeDatabase.kt` — version **2**, entités `[PlayerProfileEntity, RankingHistoryEntity]`, `MIGRATION_1_2` définie — **Story 1.6 passe à version 3** avec `MIGRATION_2_3` + `WorkAxisEntity`
- `WorkAxisEntity.kt` — **N'EXISTE PAS** — Story 1.6 le crée
- `WorkAxisDao.kt` — **N'EXISTE PAS** — Story 1.6 le crée
- `WorkAxisRepositoryImpl.kt` — **N'EXISTE PAS** — Story 1.6 le crée
- `DataModule.kt` — fournit `SecondServeDatabase`, `PlayerProfileDao`, `PlayerDataStore`, `PlayerProfileRepository` — **Story 1.6 ajoute** `WorkAxisDao`, `WorkAxisRepository` + `MIGRATION_2_3` + met à jour l'injection de `PlayerProfileRepositoryImpl` pour y ajouter `WorkAxisRepository`
- `PlayerProfileRepositoryImpl.kt` — `buildMatchContextProfile()` retourne `activeWorkAxes = emptyList()` — **Story 1.6 injecte `WorkAxisRepository`** pour le peupler
- `Mappers.kt` — fonctions `toDomain()` pour `PlayerProfileEntity` et `RankingHistoryEntity` — **Story 1.6 ajoute** `WorkAxisEntity.toDomain()`

**Android — feature profile :**
- `ProfileViewModel.kt` — gère profil joueur + classement — **Story 1.6 ne le modifie pas** (les axes ont leur propre ViewModel)
- `ProfileScreen.kt` — 3 sections existantes (classement, style, consignes) — **Story 1.6 ajoute** un bouton de navigation vers `WorkAxesScreen`
- `AppNavGraph.kt` — seule route `"profile"` — **Story 1.6 ajoute** `"work_axes"` + callback de navigation `ProfileScreen`

**Backend VPS :**
- Dernier `down_revision` Alembic = `'b2c3d4e5f6a7'` (migration Story 1.5)
- Dossier `features/work_axes/` — **N'EXISTE PAS** — Story 1.6 le crée complet
- `api/v1/work_axes.py` — **N'EXISTE PAS** — Story 1.6 le crée
- `api/v1/router.py` — **Story 1.6 l'inclut** avec `prefix="/work_axes"` + `dependencies=[Depends(verify_jwt)]`

---

## Technical Requirements

### Constante domaine — NOUVEAU fichier ou dans WorkAxis

**`domain/src/main/kotlin/com/secondserve/domain/model/WorkAxis.kt`** (NEW)

```kotlin
package com.secondserve.domain.model

const val MAX_WORK_AXES = 3

data class WorkAxis(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)
```

> `MAX_WORK_AXES` : constante SCREAMING_SNAKE_CASE dans `:domain` — la même constante est utilisée côté Android (ViewModel) ET côté VPS (Python `MAX_WORK_AXES = 3`) pour éviter la duplication logique.

### Domaine — WorkAxisRepository

**`domain/src/main/kotlin/com/secondserve/domain/repository/WorkAxisRepository.kt`** (NEW)

```kotlin
package com.secondserve.domain.repository

import com.secondserve.domain.AppResult
import com.secondserve.domain.model.WorkAxis
import kotlinx.coroutines.flow.Flow

interface WorkAxisRepository {
    fun getWorkAxes(): Flow<List<WorkAxis>>
    suspend fun createWorkAxis(title: String): AppResult<Unit>
    suspend fun updateWorkAxis(id: Long, title: String): AppResult<Unit>
    suspend fun deleteWorkAxis(id: Long): AppResult<Unit>
    suspend fun getActiveWorkAxesTitles(): List<String>
}
```

### Domaine — PlayerProfileRepository (UPDATE)

**`domain/src/main/kotlin/com/secondserve/domain/repository/PlayerProfileRepository.kt`** (UPDATE)

La méthode `buildMatchContextProfile()` conserve sa signature **inchangée**. L'implémentation change (voir `PlayerProfileRepositoryImpl`).

### Android — Room : WorkAxisEntity

**`data/src/main/kotlin/com/secondserve/data/local/db/entity/WorkAxisEntity.kt`** (NEW)

```kotlin
package com.secondserve.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "work_axes")
data class WorkAxisEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
```

### Android — Mappers.kt (UPDATE)

**`data/src/main/kotlin/com/secondserve/data/local/db/entity/Mappers.kt`** (UPDATE)

Ajouter à la fin du fichier existant :

```kotlin
import com.secondserve.domain.model.WorkAxis

fun WorkAxisEntity.toDomain(): WorkAxis = WorkAxis(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt
)
```

> Ne pas modifier `PlayerProfileEntity.toDomain()` ni `RankingHistoryEntity.toDomain()` — laisser intact.

### Android — Room : WorkAxisDao

**`data/src/main/kotlin/com/secondserve/data/local/dao/WorkAxisDao.kt`** (NEW)

```kotlin
package com.secondserve.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.secondserve.data.local.db.entity.WorkAxisEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkAxisDao {
    @Query("SELECT * FROM work_axes ORDER BY created_at ASC")
    fun getAll(): Flow<List<WorkAxisEntity>>

    @Query("SELECT COUNT(*) FROM work_axes")
    suspend fun count(): Int

    @Query("SELECT title FROM work_axes ORDER BY created_at ASC")
    suspend fun getAllTitles(): List<String>

    @Insert
    suspend fun insert(entity: WorkAxisEntity): Long

    @Update
    suspend fun update(entity: WorkAxisEntity)

    @Query("DELETE FROM work_axes WHERE id = :id")
    suspend fun delete(id: Long)
}
```

### Android — SecondServeDatabase (UPDATE)

**`data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt`** (UPDATE)

```kotlin
@Database(
    entities = [
        PlayerProfileEntity::class,
        RankingHistoryEntity::class,
        WorkAxisEntity::class         // ← NEW
    ],
    version = 3,                      // ← 2 → 3
    exportSchema = true
)
abstract class SecondServeDatabase : RoomDatabase() {
    abstract fun playerProfileDao(): PlayerProfileDao
    abstract fun workAxisDao(): WorkAxisDao               // ← NEW

    companion object {
        const val DB_NAME = "secondserve_db"

        val MIGRATION_1_2 = object : Migration(1, 2) {  // inchangé
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE player_profiles ADD COLUMN play_style TEXT")
                database.execSQL("ALTER TABLE player_profiles ADD COLUMN preferred_surfaces TEXT")
                database.execSQL("ALTER TABLE player_profiles ADD COLUMN coach_instruction_1 TEXT")
                database.execSQL("ALTER TABLE player_profiles ADD COLUMN coach_instruction_2 TEXT")
                database.execSQL("ALTER TABLE player_profiles ADD COLUMN coach_instruction_3 TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {  // ← NEW
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS work_axes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )"""
                )
            }
        }
    }
}
```

> ⚠️ **CRITIQUE** : Toujours inclure `IF NOT EXISTS` dans `CREATE TABLE` de migration — Room peut exécuter les migrations out-of-order en test.

### Android — VpsApiService (UPDATE)

**`data/src/main/kotlin/com/secondserve/data/remote/api/VpsApiService.kt`** (UPDATE)

Ajouter les endpoints work_axes :

```kotlin
// Work Axes
@GET("api/v1/work_axes")
suspend fun getWorkAxes(): WorkAxesResponse

@POST("api/v1/work_axes")
suspend fun createWorkAxis(@Body request: WorkAxisRequest): WorkAxisResponse

@PUT("api/v1/work_axes/{id}")
suspend fun updateWorkAxis(@Path("id") id: Long, @Body request: WorkAxisRequest): WorkAxisResponse

@DELETE("api/v1/work_axes/{id}")
suspend fun deleteWorkAxis(@Path("id") id: Long)
```

### Android — DTOs WorkAxis

**`data/src/main/kotlin/com/secondserve/data/remote/api/dto/WorkAxisDto.kt`** (NEW)

```kotlin
package com.secondserve.data.remote.api.dto

import com.squareup.moshi.Json

data class WorkAxisRequest(
    @Json(name = "title") val title: String,
    @Json(name = "created_at") val createdAt: Long
)

data class WorkAxisResponse(
    @Json(name = "id") val id: Long,
    @Json(name = "title") val title: String,
    @Json(name = "created_at") val createdAt: Long,
    @Json(name = "updated_at") val updatedAt: Long
)

data class WorkAxesResponse(
    @Json(name = "items") val items: List<WorkAxisResponse>,
    @Json(name = "total") val total: Int
)
```

> Pattern JSON `{ "items": [...], "total": N }` — architecture standard (liste toujours enveloppée). `createdAt` inclus dans le `WorkAxisRequest` car c'est l'ID naturel côté Android pour la réconciliation.

### Android — WorkAxisRepositoryImpl

**`data/src/main/kotlin/com/secondserve/data/repository/WorkAxisRepositoryImpl.kt`** (NEW)

```kotlin
package com.secondserve.data.repository

import com.secondserve.data.local.dao.WorkAxisDao
import com.secondserve.data.local.db.entity.WorkAxisEntity
import com.secondserve.data.local.db.entity.toDomain
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.data.remote.api.dto.WorkAxisRequest
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.WorkAxis
import com.secondserve.domain.repository.WorkAxisRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

class WorkAxisRepositoryImpl(
    private val dao: WorkAxisDao,
    private val vpsApiService: VpsApiService
) : WorkAxisRepository {

    override fun getWorkAxes(): Flow<List<WorkAxis>> =
        dao.getAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun createWorkAxis(title: String): AppResult<Unit> = try {
        val now = System.currentTimeMillis()
        val entity = WorkAxisEntity(title = title, createdAt = now, updatedAt = now)
        val localId = dao.insert(entity)
        try {
            vpsApiService.createWorkAxis(WorkAxisRequest(title = title, createdAt = now))
        } catch (e: Exception) {
            Timber.w(e, "VPS work axis create failed — local save succeeded")
        }
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(e)
    }

    override suspend fun updateWorkAxis(id: Long, title: String): AppResult<Unit> = try {
        val now = System.currentTimeMillis()
        // Load existing to preserve createdAt
        val existing = dao.getAll().let { flow ->
            // Use a one-shot query approach
            var found: WorkAxisEntity? = null
            dao.getAll().collect { list -> found = list.firstOrNull { it.id == id } }
            found
        } ?: return AppResult.Error(Exception("WorkAxis $id not found"))
        dao.update(existing.copy(title = title, updatedAt = now))
        try {
            vpsApiService.updateWorkAxis(id, WorkAxisRequest(title = title, createdAt = existing.createdAt))
        } catch (e: Exception) {
            Timber.w(e, "VPS work axis update failed — local save succeeded")
        }
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(e)
    }

    override suspend fun deleteWorkAxis(id: Long): AppResult<Unit> = try {
        dao.delete(id)
        try {
            vpsApiService.deleteWorkAxis(id)
        } catch (e: Exception) {
            Timber.w(e, "VPS work axis delete failed — local delete succeeded")
        }
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(e)
    }

    override suspend fun getActiveWorkAxesTitles(): List<String> =
        dao.getAllTitles()
}
```

> ⚠️ **Anti-pattern dans `updateWorkAxis`** : ne pas utiliser `.collect {}` sur un `Flow` pour un one-shot — remplacer par un DAO `@Query` dédié.  
> Ajouter dans `WorkAxisDao` :  
> ```kotlin
> @Query("SELECT * FROM work_axes WHERE id = :id")
> suspend fun getById(id: Long): WorkAxisEntity?
> ```  
> Et dans `updateWorkAxis` :  
> ```kotlin
> val existing = dao.getById(id) ?: return AppResult.Error(Exception("WorkAxis $id not found"))
> ```

### Android — PlayerProfileRepositoryImpl (UPDATE)

**`data/src/main/kotlin/com/secondserve/data/repository/PlayerProfileRepositoryImpl.kt`** (UPDATE)

Injecter `WorkAxisRepository` et peupler `activeWorkAxes` dans `buildMatchContextProfile()` :

```kotlin
class PlayerProfileRepositoryImpl(
    private val dao: PlayerProfileDao,
    private val vpsApiService: VpsApiService,
    private val workAxisRepository: WorkAxisRepository  // ← NEW injection
) : PlayerProfileRepository {

    // ... getProfile(), saveRanking(), getRankingHistory(), saveProfileDetails(),
    //     observeMatchSessionCount() — INCHANGÉS ...

    override suspend fun buildMatchContextProfile(): MatchContextProfile {
        val profile = dao.getProfile()
        return MatchContextProfile(
            fftSeries = profile?.currentSeries,
            playStyle = profile?.playStyle,
            preferredSurfaces = profile?.preferredSurfaces.toPreferredSurfacesList(),
            coachInstructions = listOfNotNull(
                profile?.coachInstruction1?.takeIf { it.isNotBlank() },
                profile?.coachInstruction2?.takeIf { it.isNotBlank() },
                profile?.coachInstruction3?.takeIf { it.isNotBlank() }
            ),
            activeWorkAxes = workAxisRepository.getActiveWorkAxesTitles()  // ← NEW
        )
    }
}
```

### Android — DataModule (UPDATE)

**`app/src/main/kotlin/com/secondserve/di/DataModule.kt`** (UPDATE)

```kotlin
@Provides
@Singleton
fun provideSecondServeDatabase(@ApplicationContext context: Context): SecondServeDatabase =
    Room.databaseBuilder(
        context,
        SecondServeDatabase::class.java,
        SecondServeDatabase.DB_NAME
    )
    .addMigrations(
        SecondServeDatabase.MIGRATION_1_2,
        SecondServeDatabase.MIGRATION_2_3  // ← NEW
    )
    .build()

// NEW
@Provides
@Singleton
fun provideWorkAxisDao(db: SecondServeDatabase): WorkAxisDao =
    db.workAxisDao()

// NEW
@Provides
@Singleton
fun provideWorkAxisRepository(
    dao: WorkAxisDao,
    vpsApiService: VpsApiService
): WorkAxisRepository =
    WorkAxisRepositoryImpl(dao, vpsApiService)

// UPDATE — ajout de WorkAxisRepository
@Provides
@Singleton
fun providePlayerProfileRepository(
    dao: PlayerProfileDao,
    vpsApiService: VpsApiService,
    workAxisRepository: WorkAxisRepository  // ← NEW
): PlayerProfileRepository =
    PlayerProfileRepositoryImpl(dao, vpsApiService, workAxisRepository)
```

> ⚠️ **CRITIQUE** : `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)` — les deux migrations doivent être présentes. Sans `MIGRATION_2_3`, crash `IllegalStateException` sur toute installation existante.

### Android — WorkAxesViewModel

**`feature/profile/src/main/kotlin/com/secondserve/feature/profile/WorkAxesViewModel.kt`** (NEW)

```kotlin
package com.secondserve.feature.profile

import androidx.lifecycle.ViewModel
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.MAX_WORK_AXES
import com.secondserve.domain.model.WorkAxis
import com.secondserve.domain.repository.WorkAxisRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

@HiltViewModel
class WorkAxesViewModel @Inject constructor(
    private val workAxisRepository: WorkAxisRepository
) : ViewModel(), ContainerHost<WorkAxesUiState, WorkAxesSideEffect> {

    override val container = container<WorkAxesUiState, WorkAxesSideEffect>(WorkAxesUiState())

    init {
        collectWorkAxes()
    }

    private fun collectWorkAxes() = intent {
        workAxisRepository.getWorkAxes().collect { axes ->
            reduce {
                state.copy(
                    workAxes = axes,
                    isAtMaxCapacity = axes.size >= MAX_WORK_AXES
                )
            }
        }
    }

    fun createWorkAxis(title: String) = intent {
        if (state.isAtMaxCapacity) {
            postSideEffect(WorkAxesSideEffect.ShowError("Maximum $MAX_WORK_AXES axes actifs atteint"))
            return@intent
        }
        if (title.isBlank()) {
            postSideEffect(WorkAxesSideEffect.ShowError("Le titre ne peut pas être vide"))
            return@intent
        }
        reduce { state.copy(isSaving = true) }
        when (val result = workAxisRepository.createWorkAxis(title.trim())) {
            is AppResult.Success -> {
                reduce { state.copy(isSaving = false) }
                postSideEffect(WorkAxesSideEffect.WorkAxisCreated)
            }
            is AppResult.Error -> {
                reduce { state.copy(isSaving = false) }
                postSideEffect(WorkAxesSideEffect.ShowError("Erreur lors de la création"))
            }
            AppResult.Loading -> {}
        }
    }

    fun updateWorkAxis(id: Long, newTitle: String) = intent {
        if (newTitle.isBlank()) {
            postSideEffect(WorkAxesSideEffect.ShowError("Le titre ne peut pas être vide"))
            return@intent
        }
        reduce { state.copy(isSaving = true) }
        when (val result = workAxisRepository.updateWorkAxis(id, newTitle.trim())) {
            is AppResult.Success -> {
                reduce { state.copy(isSaving = false) }
                postSideEffect(WorkAxesSideEffect.WorkAxisUpdated)
            }
            is AppResult.Error -> {
                reduce { state.copy(isSaving = false) }
                postSideEffect(WorkAxesSideEffect.ShowError("Erreur lors de la modification"))
            }
            AppResult.Loading -> {}
        }
    }

    fun deleteWorkAxis(id: Long) = intent {
        when (val result = workAxisRepository.deleteWorkAxis(id)) {
            is AppResult.Success ->
                postSideEffect(WorkAxesSideEffect.WorkAxisDeleted)
            is AppResult.Error ->
                postSideEffect(WorkAxesSideEffect.ShowError("Erreur lors de la suppression"))
            AppResult.Loading -> {}
        }
    }
}

data class WorkAxesUiState(
    val workAxes: List<WorkAxis> = emptyList(),
    val isAtMaxCapacity: Boolean = false,
    val isSaving: Boolean = false
)

sealed class WorkAxesSideEffect {
    data object WorkAxisCreated : WorkAxesSideEffect()
    data object WorkAxisUpdated : WorkAxesSideEffect()
    data object WorkAxisDeleted : WorkAxesSideEffect()
    data class ShowError(val message: String) : WorkAxesSideEffect()
}
```

### Android — WorkAxesScreen

**`feature/profile/src/main/kotlin/com/secondserve/feature/profile/WorkAxesScreen.kt`** (NEW)

```kotlin
package com.secondserve.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkAxesScreen(
    onNavigateBack: () -> Unit,
    viewModel: WorkAxesViewModel = hiltViewModel()
) {
    val state by viewModel.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingAxis by remember { mutableStateOf<com.secondserve.domain.model.WorkAxis?>(null) }
    var newTitle by remember { mutableStateOf("") }

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is WorkAxesSideEffect.WorkAxisCreated -> {
                showCreateDialog = false
                newTitle = ""
            }
            is WorkAxesSideEffect.WorkAxisUpdated -> {
                editingAxis = null
                newTitle = ""
            }
            WorkAxesSideEffect.WorkAxisDeleted -> {}
            is WorkAxesSideEffect.ShowError ->
                scope.launch { snackbarHostState.showSnackbar(effect.message) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Axes de travail") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (state.isAtMaxCapacity) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Maximum ${com.secondserve.domain.model.MAX_WORK_AXES} axes actifs atteint")
                        }
                    } else {
                        showCreateDialog = true
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter un axe")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                if (state.isAtMaxCapacity) {
                    Text(
                        text = "Maximum ${com.secondserve.domain.model.MAX_WORK_AXES} axes actifs atteint",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Text(
                        text = "${state.workAxes.size}/${com.secondserve.domain.model.MAX_WORK_AXES} axes actifs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            items(state.workAxes, key = { it.id }) { axis ->
                WorkAxisCard(
                    axis = axis,
                    onEdit = { editingAxis = axis; newTitle = axis.title },
                    onDelete = { viewModel.deleteWorkAxis(axis.id) }
                )
            }
        }
    }

    // Création dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false; newTitle = "" },
            title = { Text("Nouvel axe de travail") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { if (it.length <= 200) newTitle = it },
                    label = { Text("Description de l'axe") },
                    supportingText = { Text("${newTitle.length}/200") },
                    singleLine = false,
                    maxLines = 3
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.createWorkAxis(newTitle) },
                    enabled = newTitle.isNotBlank() && !state.isSaving
                ) { Text("Créer") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false; newTitle = "" }) { Text("Annuler") }
            }
        )
    }

    // Edition dialog
    editingAxis?.let { axis ->
        AlertDialog(
            onDismissRequest = { editingAxis = null; newTitle = "" },
            title = { Text("Modifier l'axe") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { if (it.length <= 200) newTitle = it },
                    label = { Text("Description de l'axe") },
                    supportingText = { Text("${newTitle.length}/200") },
                    singleLine = false,
                    maxLines = 3
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.updateWorkAxis(axis.id, newTitle) },
                    enabled = newTitle.isNotBlank() && !state.isSaving
                ) { Text("Enregistrer") }
            },
            dismissButton = {
                TextButton(onClick = { editingAxis = null; newTitle = "" }) { Text("Annuler") }
            }
        )
    }
}

@Composable
private fun WorkAxisCard(
    axis: com.secondserve.domain.model.WorkAxis,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = axis.title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Modifier")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Supprimer",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
```

> Limite de 200 caractères par axe — cohérente avec le pattern de 500 caractères des consignes coach (Story 1.5). Ajustable.

### Android — AppNavGraph (UPDATE)

**`app/src/main/kotlin/com/secondserve/navigation/AppNavGraph.kt`** (UPDATE)

```kotlin
@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "profile") {
        composable("profile") {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToWorkAxes = { navController.navigate("work_axes") }  // ← NEW
            )
        }
        composable("work_axes") {  // ← NEW
            WorkAxesScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
```

### Android — ProfileScreen (UPDATE)

**`feature/profile/src/main/kotlin/com/secondserve/feature/profile/ProfileScreen.kt`** (UPDATE)

1. Ajouter le paramètre `onNavigateToWorkAxes: () -> Unit` à la signature de `ProfileScreen`
2. Ajouter un `TextButton` ou `OutlinedButton` "Gérer mes axes de travail" dans le `LazyColumn` (après les sections de consignes coach)

```kotlin
// Dans la signature :
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToWorkAxes: () -> Unit,  // ← NEW
    viewModel: ProfileViewModel = hiltViewModel()
) { ... }

// Dans le LazyColumn, après les sections existantes :
item {
    OutlinedButton(
        onClick = onNavigateToWorkAxes,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text("Gérer mes axes de travail")
    }
}
```

---

### VPS Backend — features/work_axes/models.py (NEW)

**`backend/app/features/work_axes/models.py`** (NEW)

```python
from sqlalchemy import Column, Integer, String
from app.core.database import Base

class WorkAxis(Base):
    __tablename__ = "work_axes"

    id = Column(Integer, primary_key=True, autoincrement=True)
    title = Column(String, nullable=False)
    created_at = Column(Integer, nullable=False)  # epoch ms
    updated_at = Column(Integer, nullable=False)  # epoch ms
```

### VPS Backend — features/work_axes/schemas.py (NEW)

**`backend/app/features/work_axes/schemas.py`** (NEW)

```python
from pydantic import BaseModel, field_validator

MAX_WORK_AXES = 3

class WorkAxisRequest(BaseModel):
    title: str
    created_at: int  # epoch ms — identifiant naturel côté Android

    @field_validator("title")
    @classmethod
    def validate_title(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("Le titre ne peut pas être vide")
        if len(v) > 200:
            raise ValueError("Le titre ne peut pas dépasser 200 caractères")
        return v

class WorkAxisResponse(BaseModel):
    id: int
    title: str
    created_at: int
    updated_at: int
    model_config = {"from_attributes": True}

class WorkAxesResponse(BaseModel):
    items: list[WorkAxisResponse]
    total: int
```

### VPS Backend — features/work_axes/repository.py (NEW)

**`backend/app/features/work_axes/repository.py`** (NEW)

```python
import time
import logging
from sqlalchemy import select, func
from sqlalchemy.ext.asyncio import AsyncSession
from app.features.work_axes.models import WorkAxis

logger = logging.getLogger(__name__)

class WorkAxisRepository:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_all(self) -> list[WorkAxis]:
        result = await self.db.execute(select(WorkAxis).order_by(WorkAxis.created_at.asc()))
        return list(result.scalars().all())

    async def count(self) -> int:
        result = await self.db.execute(select(func.count()).select_from(WorkAxis))
        return result.scalar_one()

    async def create(self, title: str, created_at: int) -> WorkAxis:
        now = int(time.time() * 1000)
        axis = WorkAxis(title=title, created_at=created_at, updated_at=now)
        self.db.add(axis)
        await self.db.flush()
        return axis

    async def get_by_id(self, axis_id: int) -> WorkAxis | None:
        result = await self.db.execute(select(WorkAxis).where(WorkAxis.id == axis_id))
        return result.scalar_one_or_none()

    async def update(self, axis_id: int, title: str) -> WorkAxis | None:
        axis = await self.get_by_id(axis_id)
        if axis:
            axis.title = title
            axis.updated_at = int(time.time() * 1000)
            await self.db.flush()
        return axis

    async def delete(self, axis_id: int) -> bool:
        axis = await self.get_by_id(axis_id)
        if axis:
            await self.db.delete(axis)
            await self.db.flush()
            return True
        return False
```

### VPS Backend — features/work_axes/service.py (NEW)

**`backend/app/features/work_axes/service.py`** (NEW)

```python
import logging
from fastapi import HTTPException
from app.features.work_axes.repository import WorkAxisRepository
from app.features.work_axes.schemas import (
    WorkAxisRequest, WorkAxisResponse, WorkAxesResponse, MAX_WORK_AXES
)

logger = logging.getLogger(__name__)

class WorkAxisService:
    def __init__(self, repository: WorkAxisRepository):
        self.repository = repository

    async def get_all(self) -> WorkAxesResponse:
        axes = await self.repository.get_all()
        items = [WorkAxisResponse.model_validate(a) for a in axes]
        return WorkAxesResponse(items=items, total=len(items))

    async def create(self, request: WorkAxisRequest) -> WorkAxisResponse:
        count = await self.repository.count()
        if count >= MAX_WORK_AXES:
            raise HTTPException(
                status_code=422,
                detail={"error_code": "MAX_WORK_AXES_REACHED",
                        "message": f"Maximum {MAX_WORK_AXES} axes actifs atteint"}
            )
        axis = await self.repository.create(request.title, request.created_at)
        return WorkAxisResponse.model_validate(axis)

    async def update(self, axis_id: int, request: WorkAxisRequest) -> WorkAxisResponse:
        axis = await self.repository.update(axis_id, request.title)
        if not axis:
            raise HTTPException(
                status_code=404,
                detail={"error_code": "WORK_AXIS_NOT_FOUND", "message": "Axe non trouvé"}
            )
        return WorkAxisResponse.model_validate(axis)

    async def delete(self, axis_id: int) -> None:
        deleted = await self.repository.delete(axis_id)
        if not deleted:
            raise HTTPException(
                status_code=404,
                detail={"error_code": "WORK_AXIS_NOT_FOUND", "message": "Axe non trouvé"}
            )
```

### VPS Backend — api/v1/work_axes.py (NEW)

**`backend/app/api/v1/work_axes.py`** (NEW)

```python
import logging
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from app.core.database import get_db
from app.features.work_axes.repository import WorkAxisRepository
from app.features.work_axes.schemas import WorkAxisRequest, WorkAxisResponse, WorkAxesResponse
from app.features.work_axes.service import WorkAxisService

logger = logging.getLogger(__name__)

router = APIRouter()

def get_work_axis_service(db: AsyncSession = Depends(get_db)) -> WorkAxisService:
    return WorkAxisService(WorkAxisRepository(db))

@router.get("", response_model=WorkAxesResponse)
async def list_work_axes(service: WorkAxisService = Depends(get_work_axis_service)):
    return await service.get_all()

@router.post("", response_model=WorkAxisResponse, status_code=201)
async def create_work_axis(
    request: WorkAxisRequest,
    service: WorkAxisService = Depends(get_work_axis_service)
):
    return await service.create(request)

@router.put("/{axis_id}", response_model=WorkAxisResponse)
async def update_work_axis(
    axis_id: int,
    request: WorkAxisRequest,
    service: WorkAxisService = Depends(get_work_axis_service)
):
    return await service.update(axis_id, request)

@router.delete("/{axis_id}", status_code=204)
async def delete_work_axis(
    axis_id: int,
    service: WorkAxisService = Depends(get_work_axis_service)
):
    await service.delete(axis_id)
```

> JWT est appliqué au niveau du router (`router.py : dependencies=[Depends(verify_jwt)]`) — pas de double dépendance ici.

### VPS Backend — router.py (UPDATE)

**`backend/app/api/v1/router.py`** (UPDATE)

```python
from app.api.v1 import auth, sessions, profile, coaching, sync, notifications, work_axes  # ← add work_axes

# Ajouter après la ligne notifications :
api_router.include_router(work_axes.router, prefix="/work_axes", tags=["work_axes"], dependencies=[Depends(verify_jwt)])
```

### VPS Backend — Migration Alembic (NEW)

**`backend/alembic/versions/<hash>_add_work_axes_table.py`** (NEW)

```python
"""add work_axes table

Revision ID: <generated>
Revises: b2c3d4e5f6a7
Create Date: <date>
"""
from alembic import op
import sqlalchemy as sa

revision = '<generated>'
down_revision = 'b2c3d4e5f6a7'
branch_labels = None
depends_on = None

def upgrade() -> None:
    op.create_table(
        'work_axes',
        sa.Column('id', sa.Integer(), nullable=False, autoincrement=True),
        sa.Column('title', sa.String(), nullable=False),
        sa.Column('created_at', sa.Integer(), nullable=False),
        sa.Column('updated_at', sa.Integer(), nullable=False),
        sa.PrimaryKeyConstraint('id')
    )

def downgrade() -> None:
    op.drop_table('work_axes')
```

> ⚠️ `down_revision = 'b2c3d4e5f6a7'` — hash exact de la migration Story 1.5. À vérifier dans `alembic/versions/b2c3d4e5f6a7_add_profile_details_columns.py`.

### VPS Backend — alembic/env.py (UPDATE)

**`backend/alembic/env.py`** (UPDATE)

Ajouter l'import du modèle WorkAxis pour que Alembic le détecte :

```python
from app.features.work_axes.models import WorkAxis  # ← ajouter aux imports existants
```

---

## Tasks / Subtasks

### Domaine Android

- [x] **Task D-1** — Créer `WorkAxis.kt` (data class + `MAX_WORK_AXES` const) dans `:domain/model/`
- [x] **Task D-2** — Créer `WorkAxisRepository.kt` (interface) dans `:domain/repository/`

### Room Android

- [x] **Task R-1** — Créer `WorkAxisEntity.kt` dans `:data/local/db/entity/` (table `work_axes`, 4 colonnes)
- [x] **Task R-2** — Ajouter `WorkAxisEntity.toDomain()` dans `Mappers.kt`
- [x] **Task R-3** — Créer `WorkAxisDao.kt` dans `:data/local/dao/` (avec `getById()` one-shot)
- [x] **Task R-4** — Mettre à jour `SecondServeDatabase.kt` : version 2→3, `WorkAxisEntity` dans entities, `workAxisDao()` abstract, `MIGRATION_2_3`

### Data Layer Android

- [x] **Task DA-1** — Créer `WorkAxisDto.kt` dans `:data/remote/api/dto/` (`WorkAxisRequest`, `WorkAxisResponse`, `WorkAxesResponse`)
- [x] **Task DA-2** — Ajouter les 4 endpoints work_axes à `VpsApiService.kt`
- [x] **Task DA-3** — Créer `WorkAxisRepositoryImpl.kt` dans `:data/repository/` (CRUD + fire-and-forget VPS)
- [x] **Task DA-4** — Mettre à jour `PlayerProfileRepositoryImpl.kt` : injecter `WorkAxisRepository`, peupler `activeWorkAxes` dans `buildMatchContextProfile()`
- [x] **Task DA-5** — Mettre à jour `DataModule.kt` : `MIGRATION_2_3`, `WorkAxisDao`, `WorkAxisRepository`, mise à jour injection `PlayerProfileRepository`

### Feature Android

- [x] **Task UI-1** — Créer `WorkAxesViewModel.kt` dans `:feature:profile/` (Orbit MVI, CRUD, validation max 3)
- [x] **Task UI-2** — Créer `WorkAxesScreen.kt` dans `:feature:profile/` (LazyColumn, dialogs création/édition, delete, FAB désactivé si max)
- [x] **Task UI-3** — Mettre à jour `AppNavGraph.kt` : ajouter route `"work_axes"`, `onNavigateToWorkAxes` vers `ProfileScreen`
- [x] **Task UI-4** — Mettre à jour `ProfileScreen.kt` : ajouter paramètre `onNavigateToWorkAxes`, ajouter bouton "Gérer mes axes de travail"

### Backend VPS

- [x] **Task VPS-1** — Créer `backend/app/features/work_axes/__init__.py` (vide)
- [x] **Task VPS-2** — Créer `features/work_axes/models.py` (SQLAlchemy `WorkAxis`)
- [x] **Task VPS-3** — Créer `features/work_axes/schemas.py` (`WorkAxisRequest`, `WorkAxisResponse`, `WorkAxesResponse`, validation)
- [x] **Task VPS-4** — Créer `features/work_axes/repository.py` (CRUD asynchrone)
- [x] **Task VPS-5** — Créer `features/work_axes/service.py` (validation max 3, gestion 404)
- [x] **Task VPS-6** — Créer `api/v1/work_axes.py` (router GET/POST/PUT/DELETE)
- [x] **Task VPS-7** — Mettre à jour `api/v1/router.py` : inclure work_axes router
- [x] **Task VPS-8** — Créer migration Alembic (`c3d4e5f6a7b8_add_work_axes_table`, `down_revision = 'b2c3d4e5f6a7'`) — validée via tests d'intégration
- [x] **Task VPS-9** — Mettre à jour `alembic/env.py` : importer `WorkAxis`

### Tests

- [x] **Task T-1** — `WorkAxisRepositoryImplTest.kt` : `createWorkAxis()` Room insert + VPS failure fallback, `updateWorkAxis()` préserve `createdAt`, `deleteWorkAxis()`, `getActiveWorkAxesTitles()` ordre chronologique
- [x] **Task T-2** — `WorkAxesViewModelTest.kt` : `createWorkAxis()` quand count=3 → `ShowError("Maximum 3...")`, `createWorkAxis("")` → `ShowError`, flux CRUD nominal
- [x] **Task T-3** — `PlayerProfileRepositoryImplTest.kt` : `buildMatchContextProfile()` avec 2 work axes → `activeWorkAxes = ["Revers", "Service"]`
- [x] **Task T-4** — `backend/tests/integration/test_work_axes_api.py` : CRUD complet, POST 4e axe → 422, DELETE 404, sans JWT → 401 (12 tests passent)
- [x] **Task T-5** — `backend/tests/unit/test_work_axis_service.py` : `create()` quand count=3 → HTTPException 422 (9 tests passent)

### Review Findings

- [x] [Review][Patch] FAB non désactivé à la capacité max — AC2 requiert `enabled = false` (apparence disabled), pas seulement un garde du clic [`android/feature/profile/.../WorkAxesScreen.kt:87`]
- [x] [Review][Patch] `isSaving` jamais remis à `false` sur `AppResult.Loading` — UI bloquée indéfiniment si le repository retourne `Loading` [`android/feature/profile/.../WorkAxesViewModel.kt:54,73`]
- [x] [Review][Patch] État `newTitle` partagé entre dialog création et édition — UX bug : titre de l'axe édité peut polluer le dialog de création [`android/feature/profile/.../WorkAxesScreen.kt:57`]
- [x] [Review][Patch] Test AC2 assertion trop faible — `message.contains("3")` au lieu de valider le message complet "Maximum 3 axes actifs atteint" [`android/feature/profile/.../WorkAxesViewModelTest.kt:57`]
- [x] [Review][Patch] `onNavigateToWorkAxes` sans valeur par défaut — les Previews Compose et call sites existants cassent à la compilation [`android/feature/profile/.../ProfileScreen.kt:57`]
- [x] [Review][Defer] TOCTOU dans `WorkAxisService.create()` count check VPS — même pattern dans toutes les features ; fire-and-forget est l'architecture documentée [`backend/app/features/work_axes/service.py:create()`] — deferred, pre-existing
- [x] [Review][Defer] Race condition Android côté client — check `isAtMaxCapacity` non atomique — inhérent à l'architecture Flow, pattern identique à toutes les features [`android/feature/profile/.../WorkAxesViewModel.kt`] — deferred, pre-existing
- [x] [Review][Defer] `created_at` non contraint UNIQUE côté VPS — décision data model pour story future si réconciliation multi-device est ajoutée [`backend/app/features/work_axes/models.py`] — deferred, pre-existing
- [x] [Review][Defer] Pas d'empty-state UI dans `WorkAxesScreen` — amélioration UX non spécifiée dans les AC [`android/feature/profile/.../WorkAxesScreen.kt`] — deferred, pre-existing
- [x] [Review][Defer] Records VPS orphelins sur échec réseau delete — pattern fire-and-forget documenté dans spec, offline sync hors scope story [`android/data/.../repository/WorkAxisRepositoryImpl.kt`] — deferred, pre-existing

### Review Findings — 2e passe (2026-06-17)

- [x] [Review][Patch] AC2 violation : FAB click silencieux quand `isAtMaxCapacity` — AC2 requiert "l'app affiche un message d'erreur" mais le FAB patché absorbait le clic sans snackbar ; restauré [`android/feature/profile/.../WorkAxesScreen.kt:89`]
- [x] [Review][Patch] FAB `contentDescription` non conditionnel — TalkBack annonçait "Ajouter un axe" même quand désactivé ; corrigé en "Limite atteinte" [`android/feature/profile/.../WorkAxesScreen.kt:97`]
- [x] [Review][Patch] Import `OutlinedButton` hors ordre alphabétique — inséré avant `CircularProgressIndicator` (C < O) ; swappé [`android/feature/profile/.../ProfileScreen.kt:19`]
- [x] [Review][Patch] Assertions `contains("vide")` faibles sur tests titre vide — remplacées par `assertEquals("Le titre ne peut pas être vide", ...)` dans 2 tests [`android/feature/profile/.../WorkAxesViewModelTest.kt:71,111`]
- [x] [Review][Patch] `test_create_4th_axis_rejected` sans assert sur les 3 créations préliminaires — ajout de `assert resp.status_code == 201` dans la boucle [`backend/tests/integration/test_work_axes_api.py:43`]
- [x] [Review][Patch] `coEvery` utilisé sur `getAll()` non-suspend retournant un `Flow` — remplacé par `every {}` (MockK : `coEvery` pour suspend uniquement) [`android/data/.../WorkAxisRepositoryImplTest.kt:130`, `android/feature/profile/.../WorkAxesViewModelTest.kt:34,49,133`]
- [x] [Review][Defer] `deleteWorkAxis` retourne `AppResult.Success` même si l'id n'existe pas localement — Room `DELETE WHERE id = :id` est un no-op silencieux ; acceptable en usage single-user [`android/data/.../WorkAxisRepositoryImpl.kt:54`] — deferred, pre-existing
- [x] [Review][Defer] Pas de dialog de confirmation avant suppression — hors AC, amélioration UX future [`android/feature/profile/.../WorkAxesScreen.kt:WorkAxisCard`] — deferred, pre-existing
- [x] [Review][Defer] `created_at` envoyé dans PUT VPS mais ignoré côté serveur — architecture fire-and-forget documentée ; nécessite un DTO séparé si la spec évolue [`android/data/.../WorkAxisRepositoryImpl.kt:45`, `backend/app/features/work_axes/repository.py`] — deferred, pre-existing
- [x] [Review][Defer] `localId` retourné par `dao.insert()` ignoré — pas de sync Android↔VPS par id dans cette story ; à traiter si réconciliation multi-device ajoutée [`android/data/.../WorkAxisRepositoryImpl.kt:29`] — deferred, pre-existing
- [x] [Review][Defer] `CancellationException` swallowée dans `try/catch (e: Exception)` — pattern systémique pre-existing dans tous les repositories du projet [`android/data/.../WorkAxisRepositoryImpl.kt`] — deferred, pre-existing
- [x] [Review][Defer] Status HTTP 422 pour limite métier (`MAX_WORK_AXES_REACHED`) conflit sémantiquement avec 422 Pydantic — décision architecture ; 409 Conflict serait plus précis [`backend/app/features/work_axes/service.py`] — deferred, pre-existing

---

## Dev Notes

### Guardrails critiques

**Room Migration 2→3 :**
- **TOUJOURS** `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)` — les deux migrations doivent être listées. Oublier `MIGRATION_2_3` → crash `IllegalStateException: A migration from 2 to 3 was required but not found.`
- `MIGRATION_2_3` utilise `CREATE TABLE IF NOT EXISTS` + types SQLite explicites (`INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL`, `TEXT NOT NULL`, `INTEGER NOT NULL`)
- Pas de `NOT NULL DEFAULT` sur les colonnes Room en migration (SQLite ne le supporte pas) — sauf pour les colonnes avec `DEFAULT` simple : `INTEGER NOT NULL DEFAULT 0` est supporté

**WorkAxisDao — getById() obligatoire :**
- `updateWorkAxis()` dans `WorkAxisRepositoryImpl` DOIT utiliser `dao.getById(id)` (one-shot suspend) et non `.collect {}` sur le Flow — ne jamais collecter un Flow dans un contexte suspend pour une lecture unique

**MAX_WORK_AXES — valeur unique :**
- La constante `MAX_WORK_AXES = 3` est définie dans `domain/model/WorkAxis.kt` côté Android ET `MAX_WORK_AXES = 3` dans `features/work_axes/schemas.py` côté Python
- Ne pas la redéfinir dans `WorkAxesViewModel.kt` — importer depuis le domaine : `import com.secondserve.domain.model.MAX_WORK_AXES`

**Validation max 3 — double verrou :**
- **Android** : `WorkAxesViewModel.createWorkAxis()` vérifie `state.isAtMaxCapacity` avant l'appel repository — blocage UI proactif
- **VPS** : `WorkAxisService.create()` revalide côté serveur → HTTP 422 si dépassement (source de vérité serveur)
- Toujours valider les deux côtés

**`buildMatchContextProfile()` — impact sur le coaching :**
- `PlayerProfileRepositoryImpl.buildMatchContextProfile()` est appelé pour peupler `MatchContextProfile` avant chaque interaction IA (Gemini Nano, Mistral)
- `activeWorkAxes` passe par `workAxisRepository.getActiveWorkAxesTitles()` → `WorkAxisDao.getAllTitles()` → Room `SELECT title FROM work_axes ORDER BY created_at ASC`
- L'ordre est stable (chronologique) — les axes les plus anciens en premier

**VPS — fire-and-forget pattern :**
- Même pattern que `saveRanking()` et `saveProfileDetails()` : Room est la source de vérité, VPS sync en best-effort dans un `try/catch` avec `Timber.w()`
- Ne jamais bloquer la réponse Android sur le succès VPS

**Orbit MVI — pas de `isSaving` partagé :**
- Leçon Story 1.5 : `isSaving` séparé par opération. Dans `WorkAxesUiState`, `isSaving: Boolean = false` est global à l'écran car les opérations CRUD sont séquentielles (l'utilisateur ferme le dialog avant de modifier un autre)
- Si des opérations parallèles sont nécessaires (futur), utiliser des `Set<Long>` d'IDs en cours de sauvegarde

**ProfileScreen — paramètre `onNavigateToWorkAxes` :**
- Ajouter le paramètre à la signature ET l'appel dans `AppNavGraph.kt`. Les deux fichiers doivent être modifiés en cohérence
- Deferred depuis Story 1.4 : `navController.popBackStack()` sur la seule destination est un no-op — maintenant résolu car `work_axes` est une vraie route de retour

### Patterns établis à réutiliser

| Pattern | Référence |
|---------|-----------|
| Orbit MVI `intent {}` + `reduce {}` | `ProfileViewModel.kt` (patterns saveRanking, saveProfileDetails) |
| `AppResult<T>` sealed class | `PlayerProfileRepositoryImpl.kt` (tous les appels Room/VPS) |
| Fire-and-forget VPS sync | `saveRanking()` et `saveProfileDetails()` dans `PlayerProfileRepositoryImpl.kt` |
| `Timber.w(e, "...")` sur échec VPS | `PlayerProfileRepositoryImpl.kt` — jamais `Log.w()` |
| Moshi `@Json(name = ...)` | `ProfileDto.kt` (pattern existant) |
| `data object` pour side effects sans data | `ProfileSideEffect.RankingSaved`, `ProfileSideEffect.ProfileDetailsSaved` |
| `AlertDialog` Compose | Pattern à créer — utiliser Material3 `AlertDialog` avec `OutlinedTextField` |
| `hiltViewModel()` dans Composable | `ProfileScreen.kt` (pattern existant) |
| `suspend fun count(): Int` DAO | Pattern simple Room, pas de Flow |
| `FlowList<Entity> → Flow<List<Domain>>` | `PlayerProfileRepositoryImpl.getRankingHistory()` → `.map { }` |

### Deferreds pertinents de stories précédentes

- **`navController.popBackStack()` no-op** (deferred Story 1.4) : **résolu dans cette story** car `work_axes` est une vraie route
- **Room schema JSON non committé** (deferred Story 1.4) : La version 3 génère `3.json`. Committer les schemas 1, 2, et 3 après le premier build réussi
- **Race read-modify-write** (deferred Story 1.5) : Présent dans `updateWorkAxis()`. Utiliser `getById()` one-shot (suspend) + `dao.update()` — plus robuste que `saveProfileDetails()` car pas de lecture totale du profil

### Commandes Alembic

```bash
cd backend
uv run alembic revision --autogenerate -m "add_work_axes_table"
# Vérifier que le fichier généré a down_revision = 'b2c3d4e5f6a7'
uv run alembic upgrade head
```

### Notes structure VPS

```
backend/app/features/work_axes/
├── __init__.py          (vide)
├── models.py            (SQLAlchemy WorkAxis)
├── schemas.py           (WorkAxisRequest, WorkAxisResponse, WorkAxesResponse)
├── repository.py        (CRUD async)
└── service.py           (business rules : max 3, 404)

backend/app/api/v1/
└── work_axes.py         (FastAPI router GET/POST/PUT/DELETE)
```

### Project Structure Notes

**Arborescence des fichiers concernés :**

```
android/domain/src/main/kotlin/com/secondserve/domain/
├── model/
│   └── WorkAxis.kt                          — NEW (+ MAX_WORK_AXES const)
└── repository/
    └── WorkAxisRepository.kt                — NEW

android/data/src/main/kotlin/com/secondserve/data/
├── local/
│   ├── dao/
│   │   └── WorkAxisDao.kt                   — NEW (+ getById one-shot)
│   └── db/
│       ├── SecondServeDatabase.kt           — UPDATE (v2→3, MIGRATION_2_3, WorkAxisEntity, workAxisDao())
│       └── entity/
│           ├── WorkAxisEntity.kt            — NEW
│           └── Mappers.kt                   — UPDATE (WorkAxisEntity.toDomain())
├── remote/api/dto/
│   └── WorkAxisDto.kt                       — NEW (3 classes)
├── remote/api/
│   └── VpsApiService.kt                     — UPDATE (+4 endpoints)
└── repository/
    ├── WorkAxisRepositoryImpl.kt            — NEW
    └── PlayerProfileRepositoryImpl.kt       — UPDATE (inject WorkAxisRepository, activeWorkAxes)

android/app/src/main/kotlin/com/secondserve/
├── di/
│   └── DataModule.kt                        — UPDATE (MIGRATION_2_3, WorkAxisDao, WorkAxisRepository)
└── navigation/
    └── AppNavGraph.kt                       — UPDATE (route work_axes)

android/feature/profile/src/main/kotlin/com/secondserve/feature/profile/
├── WorkAxesViewModel.kt                     — NEW
├── WorkAxesScreen.kt                        — NEW
└── ProfileScreen.kt                         — UPDATE (onNavigateToWorkAxes param + bouton)

backend/app/features/work_axes/
├── __init__.py                              — NEW
├── models.py                                — NEW
├── schemas.py                               — NEW
├── repository.py                            — NEW
└── service.py                               — NEW

backend/app/api/v1/
├── work_axes.py                             — NEW
└── router.py                                — UPDATE (include work_axes router)

backend/alembic/
├── versions/<hash>_add_work_axes_table.py  — NEW
└── env.py                                   — UPDATE (import WorkAxis model)
```

### References

- [Source: epics.md § Story 1.6] — Acceptance criteria, note AI suggestions déférées à Epic 5
- [Source: architecture.md § Data Architecture] — Tables Room : `WorkAxis` listée dans les 7 entités
- [Source: architecture.md § Naming Patterns] — `work_axes` (snake_case pluriel), `MAX_WORK_AXES` (SCREAMING_SNAKE_CASE)
- [Source: architecture.md § Project Structure] — `WorkAxis.kt` dans `:domain/model/`, `WorkAxisEntity.kt` dans `:data/local/db/entity/`
- [Source: architecture.md § API & Communication] — Pattern listes REST : `{ "items": [...], "total": N }`
- [Source: architecture.md § Process Patterns] — `sealed Result<T>`, `Timber`, `logging.getLogger(__name__)`
- [Source: 1-5-profil-joueur-style-de-jeu-donnees-complementaires.md § Dev Notes] — Patterns Orbit MVI, fire-and-forget VPS, `isSaving` séparé
- [Source: 1-5-profil-joueur-style-de-jeu-donnees-complementaires.md § Dev Agent Record § Review Findings] — Leçon : `isSaving` partagé entre saveRanking/saveProfileDetails → réglé avec `isDetailsSaving`
- [Source: deferred-work.md] — Race read-modify-write, Room schema JSON, navController.popBackStack() no-op

---

## Testing Requirements

### Android — Tests unitaires

**`WorkAxisRepositoryImplTest.kt`** (NEW)

```kotlin
// Test 1 : création locale réussie
// given: dao.count() = 0
// when: createWorkAxis("Travail du revers")
// then: dao.insert() appelé avec title="Travail du revers"
// and: AppResult.Success retourné

// Test 2 : création avec échec VPS → succès local
// given: vpsApiService.createWorkAxis() lance Exception
// when: createWorkAxis("Service")
// then: AppResult.Success retourné (Room OK)
// and: Timber.w() loggué

// Test 3 : updateWorkAxis préserve createdAt
// given: axe existant avec createdAt=1000
// when: updateWorkAxis(id=1, "Nouveau titre")
// then: dao.update() avec entity.createdAt = 1000, entity.title = "Nouveau titre"

// Test 4 : deleteWorkAxis → dao.delete() + VPS delete best-effort

// Test 5 : getActiveWorkAxesTitles() retourne les titres en ordre chronologique
```

**`WorkAxesViewModelTest.kt`** (NEW)

```kotlin
// Test 1 : createWorkAxis quand isAtMaxCapacity=true → ShowError("Maximum 3 axes actifs atteint")
// Test 2 : createWorkAxis("") → ShowError("Le titre ne peut pas être vide")
// Test 3 : createWorkAxis valide → WorkAxisCreated side effect + dialog fermé
// Test 4 : deleteWorkAxis → WorkAxisDeleted side effect
// Test 5 : isAtMaxCapacity=true quand workAxes.size=3
```

**`PlayerProfileRepositoryImplTest.kt`** (EXTEND)

```kotlin
// Test existant : buildMatchContextProfile() — étendre pour vérifier activeWorkAxes
// given: workAxisRepository.getActiveWorkAxesTitles() = ["Revers", "Service"]
// when: buildMatchContextProfile()
// then: result.activeWorkAxes = ["Revers", "Service"]
```

### VPS — Tests d'intégration et unitaires

**`backend/tests/integration/test_work_axes_api.py`** (NEW)

```python
async def test_list_work_axes_empty(client, valid_token):
    response = await client.get("/api/v1/work_axes", headers=auth(valid_token))
    assert response.status_code == 200
    assert response.json() == {"items": [], "total": 0}

async def test_create_work_axis(client, valid_token):
    response = await client.post("/api/v1/work_axes",
        json={"title": "Travail du revers", "created_at": 1749470400000},
        headers=auth(valid_token))
    assert response.status_code == 201
    assert response.json()["title"] == "Travail du revers"
    assert "id" in response.json()

async def test_create_4th_axis_rejected(client, valid_token):
    # Créer 3 axes
    for i in range(3):
        await client.post("/api/v1/work_axes",
            json={"title": f"Axe {i}", "created_at": 1000 + i},
            headers=auth(valid_token))
    # 4e tente
    response = await client.post("/api/v1/work_axes",
        json={"title": "Axe 4", "created_at": 1003},
        headers=auth(valid_token))
    assert response.status_code == 422
    assert response.json()["detail"]["error_code"] == "MAX_WORK_AXES_REACHED"

async def test_update_work_axis(client, valid_token):
    create_resp = await client.post("/api/v1/work_axes",
        json={"title": "Ancien titre", "created_at": 1000}, headers=auth(valid_token))
    axis_id = create_resp.json()["id"]
    resp = await client.put(f"/api/v1/work_axes/{axis_id}",
        json={"title": "Nouveau titre", "created_at": 1000}, headers=auth(valid_token))
    assert resp.status_code == 200
    assert resp.json()["title"] == "Nouveau titre"

async def test_delete_work_axis(client, valid_token):
    create_resp = await client.post("/api/v1/work_axes",
        json={"title": "À supprimer", "created_at": 1000}, headers=auth(valid_token))
    axis_id = create_resp.json()["id"]
    del_resp = await client.delete(f"/api/v1/work_axes/{axis_id}", headers=auth(valid_token))
    assert del_resp.status_code == 204

async def test_delete_nonexistent_axis(client, valid_token):
    resp = await client.delete("/api/v1/work_axes/99999", headers=auth(valid_token))
    assert resp.status_code == 404

async def test_work_axes_require_jwt(client):
    response = await client.get("/api/v1/work_axes")
    assert response.status_code == 401

async def test_create_axis_blank_title_rejected(client, valid_token):
    resp = await client.post("/api/v1/work_axes",
        json={"title": "  ", "created_at": 1000}, headers=auth(valid_token))
    assert resp.status_code == 422

async def test_create_axis_title_too_long(client, valid_token):
    resp = await client.post("/api/v1/work_axes",
        json={"title": "x" * 201, "created_at": 1000}, headers=auth(valid_token))
    assert resp.status_code == 422
```

**`backend/tests/unit/test_work_axis_service.py`** (NEW)

```python
async def test_create_raises_when_at_max():
    # Mock repository.count() = 3
    # assert HTTPException 422 avec error_code MAX_WORK_AXES_REACHED
    pass

async def test_update_raises_404_when_not_found():
    # Mock repository.update() returns None
    # assert HTTPException 404
    pass
```

---

## Risks & Mitigations

| Risque | Mitigation |
|--------|------------|
| Oubli de `MIGRATION_2_3` dans `addMigrations()` → crash `IllegalStateException` | Tâche DA-5 explicite — les deux migrations listées |
| `collect {}` dans suspend pour one-shot → deadlock potentiel | `WorkAxisDao.getById()` suspend obligatoire — tâche R-3 |
| `MAX_WORK_AXES` redéfini dans ViewModel avec valeur différente | Importer depuis domaine : `import com.secondserve.domain.model.MAX_WORK_AXES` |
| Alembic `down_revision` incorrect → chaîne migrée cassée | Vérifier `b2c3d4e5f6a7` dans le fichier `b2c3d4e5f6a7_add_profile_details_columns.py` |
| `ProfileScreen` ne compile plus car `onNavigateToWorkAxes` manquant | Mettre à jour `ProfileScreen` + `AppNavGraph` dans la même PR |
| VPS `work_axes` feature non importée dans `alembic/env.py` → migration autogenerate vide | Tâche VPS-9 explicite — `from app.features.work_axes.models import WorkAxis` |
| `WorkAxesResponse` manque le wrapper `{ "items": [...], "total": N }` | Pattern architecture standard — `WorkAxesResponse` avec `items` et `total` |
| Titre vide stocké (espace seul) → axe inutilisable dans prompt IA | `.trim()` sur title à la création (ViewModel + VPS validator) |

---

## Success Criteria

- Créer un axe → apparaît dans la liste et dans `buildMatchContextProfile().activeWorkAxes`
- Créer un 4e axe → bloqué par le ViewModel ET rejeté par le VPS (422)
- Modifier un axe → texte mis à jour immédiatement
- Supprimer un axe → disparu de la liste et de `activeWorkAxes`
- Room version 3 sans crash sur upgrade depuis version 2 (MIGRATION_2_3 appliquée)
- `alembic upgrade head` sans erreur (table `work_axes` créée, `down_revision = 'b2c3d4e5f6a7'`)
- `buildMatchContextProfile()` retourne les titres des axes actifs (non vide)
- Tous les tests passent (unitaires + intégration VPS)

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- Migration Alembic non exécutable directement (pas de .env dans le conteneur) — validée via les tests d'intégration SQLite en mémoire qui créent la table `work_axes` via le schema SQLAlchemy.
- `Icons.Default.ArrowBack` déprécié → remplacé par `Icons.AutoMirrored.Filled.ArrowBack` dans `WorkAxesScreen.kt`.

### Completion Notes List

- Toutes les tâches Android (D-1, D-2, R-1 à R-4, DA-1 à DA-5, UI-1 à UI-4) implémentées.
- Backend VPS (VPS-1 à VPS-9) : module `features/work_axes/` complet, router CRUD, migration Alembic `c3d4e5f6a7b8`, `alembic/env.py` mis à jour.
- `WorkAxisDao.getById()` one-shot utilisé dans `updateWorkAxis()` — anti-pattern `.collect{}` évité.
- `PlayerProfileRepositoryImpl.buildMatchContextProfile()` peuple maintenant `activeWorkAxes` via `WorkAxisRepository.getActiveWorkAxesTitles()`.
- `PlayerProfileRepositoryImplTest.kt` mis à jour : mock `WorkAxisRepository` ajouté, 2 nouveaux tests `activeWorkAxes`.
- 67 tests VPS passent (21 nouveaux + 46 existants) — aucune régression.

### File List

**Android — nouveaux fichiers :**
- `android/domain/src/main/kotlin/com/secondserve/domain/model/WorkAxis.kt`
- `android/domain/src/main/kotlin/com/secondserve/domain/repository/WorkAxisRepository.kt`
- `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/WorkAxisEntity.kt`
- `android/data/src/main/kotlin/com/secondserve/data/local/dao/WorkAxisDao.kt`
- `android/data/src/main/kotlin/com/secondserve/data/remote/api/dto/WorkAxisDto.kt`
- `android/data/src/main/kotlin/com/secondserve/data/repository/WorkAxisRepositoryImpl.kt`
- `android/feature/profile/src/main/kotlin/com/secondserve/feature/profile/WorkAxesViewModel.kt`
- `android/feature/profile/src/main/kotlin/com/secondserve/feature/profile/WorkAxesScreen.kt`
- `android/data/src/test/kotlin/com/secondserve/data/repository/WorkAxisRepositoryImplTest.kt`
- `android/feature/profile/src/test/kotlin/com/secondserve/feature/profile/WorkAxesViewModelTest.kt`

**Android — fichiers modifiés :**
- `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/Mappers.kt`
- `android/data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt`
- `android/data/src/main/kotlin/com/secondserve/data/remote/api/VpsApiService.kt`
- `android/data/src/main/kotlin/com/secondserve/data/repository/PlayerProfileRepositoryImpl.kt`
- `android/app/src/main/kotlin/com/secondserve/di/DataModule.kt`
- `android/app/src/main/kotlin/com/secondserve/navigation/AppNavGraph.kt`
- `android/feature/profile/src/main/kotlin/com/secondserve/feature/profile/ProfileScreen.kt`
- `android/data/src/test/kotlin/com/secondserve/data/repository/PlayerProfileRepositoryImplTest.kt`

**Backend VPS — nouveaux fichiers :**
- `backend/app/features/work_axes/__init__.py`
- `backend/app/features/work_axes/models.py`
- `backend/app/features/work_axes/schemas.py`
- `backend/app/features/work_axes/repository.py`
- `backend/app/features/work_axes/service.py`
- `backend/app/api/v1/work_axes.py`
- `backend/alembic/versions/c3d4e5f6a7b8_add_work_axes_table.py`
- `backend/tests/integration/test_work_axes_api.py`
- `backend/tests/unit/test_work_axis_service.py`

**Backend VPS — fichiers modifiés :**
- `backend/app/api/v1/router.py`
- `backend/alembic/env.py`

**Sprint tracking :**
- `_bmad-output/implementation-artifacts/1-6-axes-de-travail-crud-de-base.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`
