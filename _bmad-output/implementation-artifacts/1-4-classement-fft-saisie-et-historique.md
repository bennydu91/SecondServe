---
baseline_commit: "bff7022"
status: "done"
---

# Story 1.4: Classement FFT — Saisie et historique

**Status:** done

## Story

**As a** player,
**I want** to record my official FFT ranking (series + points) and view my progression over time,
**So that** my AI coaching is calibrated to my actual competition level.

## Acceptance Criteria

1. **Given** je suis sur l'écran Profil, section "Classement FFT"
   **When** j'ouvre la section
   **Then** un formulaire accepte une série parmi les valeurs FFT valides : `40, 30/5, 30/4, 30/3, 30/2, 30/1, 15/5, 15/4, 15/3, 15/2, 15/1, 4/6, 3/6, 2/6, 1/6` et un nombre de points entier positif

2. **And** toute série hors de cette liste est rejetée avec un message d'erreur explicite

3. **When** je sauvegarde un classement valide
   **Then** il apparaît dans la timeline de progression (ordre chronologique, plus récent en premier)

4. **And** les entités `PlayerProfile` et `RankingHistory` sont créées en Room via la migration de cette story (tables `player_profiles` et `ranking_history`)

5. **And** la migration Alembic correspondante est appliquée sur le VPS

6. **And** le classement actuel (série) est visible sur le résumé de l'écran Profil

7. **And** la série courante est incluse dans le contexte envoyé aux moteurs IA (Gemini Nano et Mistral)

## Architecture Context

### Séquence d'implémentation (ARCH-13)

Cette story est la **quatrième** dans la chaîne :

```
ARCH-1 (done) → ARCH-2 (done) → ARCH-3 (done) → ARCH-4 (cette story) → ...
```

**Dépendances:**
- ✅ Story 1.1: Gradle multi-module + Hilt DI (`libs.versions.toml`, Room déjà dans `data/build.gradle.kts`)
- ✅ Story 1.2: Backend FastAPI + structure feature-based + Alembic configuré
- ✅ Story 1.3: JWT auth Android ↔ VPS — `VpsApiService`, `JwtInterceptor`, `AuthModule` opérationnels
- ❌ Story 1.5: PlayerProfile fields supplémentaires (play_style, surfaces, coach instructions) — **pas encore créés**
- ❌ Story 1.6: WorkAxis — **pas encore créés**

### État actuel du code — critique à connaître

**Android:**
- Room est déjà dans `data/build.gradle.kts` (libs.versions.toml: `room = "2.7.1"`) mais **aucun entity, DAO ni Database n'existe encore**
- `AppResult` en `:domain` n'a que `Success` et `Error` — **`Loading` manquant** (noté comme deferred depuis Story 1.1). À ajouter dans cette story car c'est la première avec des ViewModels
- `VpsApiService.kt` existe dans `:data/remote/api/` — à étendre avec les routes profil
- `AuthModule.kt` fournit `OkHttpClient` et `Retrofit` — `DataModule` doit réutiliser ces bindings

**Backend VPS:**
- `backend/app/features/profile/` existe avec des fichiers stubs vides (`models.py`, `schemas.py`, `service.py`, `repository.py`)
- `backend/app/api/v1/profile.py` contient uniquement `router = APIRouter()` vide avec un commentaire pointant vers Story 1.5
- Alembic initial migration `93050bf04cdd_initial.py` est vide (`pass`) — **cette story crée la première vraie migration**
- `backend/alembic/env.py` : `Base.metadata` est vide (pas d'imports de modèles) — à corriger par import explicite dans la migration

### Entités Room à créer (Version 1 du schema)

**Table `player_profiles`** (une seule ligne pour le joueur mono-utilisateur) :

| Colonne | Type Room | Remarques |
|---------|-----------|-----------|
| `id` | `Int` (PK) | Toujours = 1 (mono-utilisateur) |
| `current_series` | `String?` | Série FFT courante (nullable jusqu'à première saisie) |
| `current_points` | `Int?` | Points associés à la série courante |
| `updated_at` | `Long` | Epoch ms |

> ⚠️ Story 1.5 ajoutera à cette table : `play_style`, `preferred_surfaces`, `coach_instruction_1/2/3`. Story 1.4 crée une table minimaliste avec seulement les champs ranking — les autres champs seront ajoutés via migration Room en Story 1.5.

**Table `ranking_history`** (toutes les entrées de classement) :

| Colonne | Type Room | Remarques |
|---------|-----------|-----------|
| `id` | `Int` (PK, autoGenerate=true) |  |
| `series` | `String` | L'une des 16 valeurs FFT valides |
| `points` | `Int` | Entier positif |
| `recorded_at` | `Long` | Epoch ms (date d'enregistrement) |
| `updated_at` | `Long` | Epoch ms (pour delta sync futur) |

### Domaine FFT — valeurs de série valides

```kotlin
val FFT_VALID_SERIES = listOf(
    "40", "30/5", "30/4", "30/3", "30/2", "30/1",
    "15/5", "15/4", "15/3", "15/2", "15/1",
    "4/6", "3/6", "2/6", "1/6"
)
```

La validation côté Android se fait en UI (Dropdown Compose) — l'enum garantit qu'une valeur invalide ne peut pas être soumise. La validation côté VPS vérifie que la série reçue est dans la liste (défense en profondeur).

### Room Database (Version 1)

C'est la **première création** de `SecondServeDatabase`. Il faut :
1. Créer `SecondServeDatabase.kt` dans `:data/local/db/` avec `version = 1`
2. Pas de migration pour passer à la version 1 — c'est la version initiale
3. `fallbackToDestructiveMigration` non acceptable en production (architecture.md) — à NE PAS utiliser

```kotlin
@Database(
    entities = [PlayerProfileEntity::class, RankingHistoryEntity::class],
    version = 1,
    exportSchema = true
)
abstract class SecondServeDatabase : RoomDatabase() {
    abstract fun playerProfileDao(): PlayerProfileDao
    // RankingHistoryDao intégré dans PlayerProfileDao (pas de DAO séparé requis pour V1)
}
```

### Alembic VPS

La migration crée deux tables : `player_profiles` et `ranking_history`. Elle doit être générée manuellement (pas auto-généré, puisque le modèle SQLAlchemy est à créer) et pointée explicitement dans `alembic/env.py` via import du `Base` de `features/profile/models.py`.

```python
# alembic/env.py — ajouter dans la section target_metadata
from app.features.profile.models import Base as ProfileBase
target_metadata = ProfileBase.metadata
```

### Contexte IA — intégration du classement

La série courante doit être incluse dans `MatchContextProfile` (`:domain/model/`). Ce modèle est utilisé par `CoachingResolver` et `VpsMistralEngine`. Pour cette story, `MatchContextProfile` n'existe pas encore en tant que fichier — il sera créé ici (même si son utilisation complète n'interviendra que dans les Epics 2-3).

```kotlin
// domain/model/MatchContextProfile.kt (NEW)
data class MatchContextProfile(
    val fftSeries: String? = null,        // "15/2" etc. — null si non renseigné
    val playStyle: String? = null,         // null jusqu'à Story 1.5
    val activeWorkAxes: List<String> = emptyList() // null jusqu'à Story 1.6
)
```

Le payload Mistral inclura `fft_ranking` (le champ `fftSeries`) — voir NFR-S5 et D1.

---

## Technical Requirements

### Android — Couche domaine

**Nouveau fichier : `domain/src/main/kotlin/com/secondserve/domain/model/RankingEntry.kt`**

```kotlin
data class RankingEntry(
    val id: Int = 0,
    val series: String,
    val points: Int,
    val recordedAt: Long
)
```

**Nouveau fichier : `domain/src/main/kotlin/com/secondserve/domain/model/PlayerProfile.kt`**

```kotlin
data class PlayerProfile(
    val id: Int = 1,
    val currentSeries: String?,
    val currentPoints: Int?,
    val updatedAt: Long
)
```

**Nouveau fichier : `domain/src/main/kotlin/com/secondserve/domain/model/MatchContextProfile.kt`**

```kotlin
data class MatchContextProfile(
    val fftSeries: String? = null,
    val playStyle: String? = null,
    val activeWorkAxes: List<String> = emptyList()
)
```

**Mise à jour : `domain/src/main/kotlin/com/secondserve/domain/AppResult.kt`**

Ajouter la variante `Loading` (noté comme deferred depuis la review 1.1 — **obligatoire avant les ViewModels**) :

```kotlin
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val exception: Throwable) : AppResult<Nothing>()
    data object Loading : AppResult<Nothing>()
}
```

**Nouveau fichier : `domain/src/main/kotlin/com/secondserve/domain/repository/PlayerProfileRepository.kt`**

```kotlin
interface PlayerProfileRepository {
    suspend fun getProfile(): AppResult<PlayerProfile?>
    suspend fun saveRanking(series: String, points: Int): AppResult<Unit>
    fun getRankingHistory(): Flow<List<RankingEntry>>
    suspend fun buildMatchContextProfile(): MatchContextProfile
}
```

**Nouveau fichier : `domain/src/main/kotlin/com/secondserve/domain/constants/FftConstants.kt`**

```kotlin
object FftConstants {
    val VALID_SERIES = listOf(
        "40", "30/5", "30/4", "30/3", "30/2", "30/1",
        "15/5", "15/4", "15/3", "15/2", "15/1",
        "4/6", "3/6", "2/6", "1/6"
    )
}
```

### Android — Couche data (Room)

**Nouveau fichier : `data/src/main/kotlin/com/secondserve/data/local/db/entity/PlayerProfileEntity.kt`**

```kotlin
@Entity(tableName = "player_profiles")
data class PlayerProfileEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "current_series") val currentSeries: String?,
    @ColumnInfo(name = "current_points") val currentPoints: Int?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
```

**Nouveau fichier : `data/src/main/kotlin/com/secondserve/data/local/db/entity/RankingHistoryEntity.kt`**

```kotlin
@Entity(tableName = "ranking_history")
data class RankingHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "series") val series: String,
    @ColumnInfo(name = "points") val points: Int,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
```

**Nouveau fichier : `data/src/main/kotlin/com/secondserve/data/local/dao/PlayerProfileDao.kt`**

```kotlin
@Dao
interface PlayerProfileDao {
    @Query("SELECT * FROM player_profiles WHERE id = 1")
    suspend fun getProfile(): PlayerProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: PlayerProfileEntity)

    @Insert
    suspend fun insertRanking(ranking: RankingHistoryEntity)

    @Query("SELECT * FROM ranking_history ORDER BY recorded_at DESC")
    fun getRankingHistory(): Flow<List<RankingHistoryEntity>>
}
```

**Nouveau fichier : `data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt`**

```kotlin
@Database(
    entities = [PlayerProfileEntity::class, RankingHistoryEntity::class],
    version = 1,
    exportSchema = true
)
abstract class SecondServeDatabase : RoomDatabase() {
    abstract fun playerProfileDao(): PlayerProfileDao

    companion object {
        const val DB_NAME = "secondserve_db"
    }
}
```

**Nouveau fichier : `data/src/main/kotlin/com/secondserve/data/repository/PlayerProfileRepositoryImpl.kt`**

```kotlin
class PlayerProfileRepositoryImpl(
    private val dao: PlayerProfileDao,
    private val vpsApiService: VpsApiService
) : PlayerProfileRepository {

    override suspend fun getProfile(): AppResult<PlayerProfile?> = try {
        val entity = dao.getProfile()
        AppResult.Success(entity?.toDomain())
    } catch (e: Exception) {
        AppResult.Error(e)
    }

    override suspend fun saveRanking(series: String, points: Int): AppResult<Unit> = try {
        val now = System.currentTimeMillis()
        // Update profile current ranking
        val current = dao.getProfile()
        dao.upsertProfile(
            PlayerProfileEntity(
                id = 1,
                currentSeries = series,
                currentPoints = points,
                updatedAt = now
            )
        )
        // Insert ranking history entry
        dao.insertRanking(
            RankingHistoryEntity(
                series = series,
                points = points,
                recordedAt = now,
                updatedAt = now
            )
        )
        // Sync to VPS (fire-and-forget, errors logged but don't block local save)
        try {
            vpsApiService.saveRanking(RankingRequest(series = series, points = points))
        } catch (e: Exception) {
            Timber.w(e, "VPS ranking sync failed — local save succeeded")
        }
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(e)
    }

    override fun getRankingHistory(): Flow<List<RankingEntry>> =
        dao.getRankingHistory().map { entities -> entities.map { it.toDomain() } }

    override suspend fun buildMatchContextProfile(): MatchContextProfile {
        val profile = dao.getProfile()
        return MatchContextProfile(fftSeries = profile?.currentSeries)
    }
}
```

> ⚠️ **Mappers** : créer des fonctions d'extension `PlayerProfileEntity.toDomain()` et `RankingHistoryEntity.toDomain()` dans un fichier `data/local/db/entity/Mappers.kt` (ou directement dans les classes entity).

### Android — Feature Profile build.gradle.kts

**Vérifier/créer : `feature/profile/build.gradle.kts`**

Ce module n'a pas encore de `build.gradle.kts` avec les dépendances UI/Orbit. Il doit inclure :

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.secondserve.feature.profile"
    compileSdk = 35
    defaultConfig { minSdk = 35 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":core:ui"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.orbit.core)
    implementation(libs.orbit.viewmodel)
    implementation(libs.orbit.compose)

    implementation(libs.coroutines.android)
    implementation(libs.timber)
}
```

Et l'inclure dans `settings.gradle.kts` si ce n'est pas déjà fait :
```kotlin
include(":feature:profile")
```

### Android — Room exportSchema configuration

**Mettre à jour `data/build.gradle.kts`** pour exporter le schema Room (obligatoire pour les migrations futures) :

```kotlin
android {
    // ... existing config ...
    defaultConfig {
        // Ajouter :
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }
}
```

Ou équivalent KSP au niveau du bloc `ksp {}` dans `data/build.gradle.kts`. Le dossier `android/data/schemas/` sera créé automatiquement avec `version_1.json`.

### Android — Navigation

**Mettre à jour `app/src/main/kotlin/com/secondserve/navigation/AppNavGraph.kt`** pour inclure la route Profil :

```kotlin
// Dans AppNavGraph, ajouter la route "profile"
composable("profile") {
    ProfileScreen(
        onNavigateBack = { navController.popBackStack() }
    )
}
```

Vérifier également que `ProfileScreen` est accessible depuis l'UI principale (bouton ou onglet).

### Android — DI Hilt (DataModule)

**Nouveau fichier : `app/src/main/kotlin/com/secondserve/di/DataModule.kt`**

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideSecondServeDatabase(@ApplicationContext context: Context): SecondServeDatabase =
        Room.databaseBuilder(
            context,
            SecondServeDatabase::class.java,
            SecondServeDatabase.DB_NAME
        ).build()

    @Provides
    fun providePlayerProfileDao(db: SecondServeDatabase): PlayerProfileDao =
        db.playerProfileDao()

    @Provides
    @Singleton
    fun providePlayerProfileRepository(
        dao: PlayerProfileDao,
        vpsApiService: VpsApiService
    ): PlayerProfileRepository =
        PlayerProfileRepositoryImpl(dao, vpsApiService)
}
```

> `VpsApiService` est fourni par `AuthModule` — Hilt résout automatiquement la dépendance.

### Android — Feature Profile (UI)

**Nouveau fichier : `feature/profile/src/main/kotlin/com/secondserve/feature/profile/ProfileViewModel.kt`**

Pattern MVI léger (Orbit) pour cette feature :

```kotlin
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: PlayerProfileRepository
) : ViewModel(), ContainerHost<ProfileUiState, ProfileSideEffect> {

    override val container = container<ProfileUiState, ProfileSideEffect>(ProfileUiState())

    init {
        loadProfile()
    }

    fun loadProfile() = intent {
        reduce { state.copy(isLoading = true) }
        when (val result = profileRepository.getProfile()) {
            is AppResult.Success -> reduce {
                state.copy(isLoading = false, currentSeries = result.data?.currentSeries, currentPoints = result.data?.currentPoints)
            }
            is AppResult.Error -> reduce {
                state.copy(isLoading = false, error = "Impossible de charger le profil")
            }
            AppResult.Loading -> {}
        }
        profileRepository.getRankingHistory().collect { history ->
            reduce { state.copy(rankingHistory = history) }
        }
    }

    fun saveRanking(series: String, points: Int) = intent {
        if (series !in FftConstants.VALID_SERIES) {
            postSideEffect(ProfileSideEffect.ShowError("Série FFT invalide : $series"))
            return@intent
        }
        if (points <= 0) {
            postSideEffect(ProfileSideEffect.ShowError("Le nombre de points doit être positif"))
            return@intent
        }
        reduce { state.copy(isSaving = true) }
        when (val result = profileRepository.saveRanking(series, points)) {
            is AppResult.Success -> {
                reduce { state.copy(isSaving = false) }
                postSideEffect(ProfileSideEffect.RankingSaved)
            }
            is AppResult.Error -> {
                reduce { state.copy(isSaving = false) }
                postSideEffect(ProfileSideEffect.ShowError("Erreur lors de la sauvegarde"))
            }
            AppResult.Loading -> {}
        }
    }
}

data class ProfileUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val currentSeries: String? = null,
    val currentPoints: Int? = null,
    val rankingHistory: List<RankingEntry> = emptyList(),
    val error: String? = null
)

sealed class ProfileSideEffect {
    data object RankingSaved : ProfileSideEffect()
    data class ShowError(val message: String) : ProfileSideEffect()
}
```

**Nouveau fichier : `feature/profile/src/main/kotlin/com/secondserve/feature/profile/ProfileScreen.kt`**

Points clés UX :
- Afficher le classement actuel (série) en résumé en haut de l'écran Profil
- Section "Classement FFT" avec un `DropdownMenu` Compose pour la série (pas de saisie texte libre — seules les 16 valeurs valides sont proposées)
- Champ numérique pour les points (`KeyboardType.Number`)
- Timeline chronologique inversée pour l'historique (`LazyColumn`, plus récent en premier = ordre naturel de la query `ORDER BY recorded_at DESC`)

### VPS — Feature Profile

**Mettre à jour : `backend/app/features/profile/models.py`**

```python
from sqlalchemy import Column, Integer, String
from sqlalchemy.orm import DeclarativeBase

class Base(DeclarativeBase):
    pass

class PlayerProfile(Base):
    __tablename__ = "player_profiles"

    id = Column(Integer, primary_key=True, default=1)
    current_series = Column(String, nullable=True)
    current_points = Column(Integer, nullable=True)
    updated_at = Column(Integer, nullable=False)  # epoch ms

class RankingHistory(Base):
    __tablename__ = "ranking_history"

    id = Column(Integer, primary_key=True, autoincrement=True)
    series = Column(String, nullable=False)
    points = Column(Integer, nullable=False)
    recorded_at = Column(Integer, nullable=False)  # epoch ms
    updated_at = Column(Integer, nullable=False)   # epoch ms
```

**Mettre à jour : `backend/app/features/profile/schemas.py`**

```python
from pydantic import BaseModel, field_validator
from typing import Optional

FFT_VALID_SERIES = [
    "40", "30/5", "30/4", "30/3", "30/2", "30/1",
    "15/5", "15/4", "15/3", "15/2", "15/1",
    "4/6", "3/6", "2/6", "1/6"
]

class RankingRequest(BaseModel):
    series: str
    points: int

    @field_validator("series")
    @classmethod
    def validate_series(cls, v: str) -> str:
        if v not in FFT_VALID_SERIES:
            raise ValueError(f"Série FFT invalide : {v}. Valeurs acceptées : {FFT_VALID_SERIES}")
        return v

    @field_validator("points")
    @classmethod
    def validate_points(cls, v: int) -> int:
        if v <= 0:
            raise ValueError("Le nombre de points doit être un entier positif")
        return v

class RankingResponse(BaseModel):
    id: int
    series: str
    points: int
    recorded_at: int

    model_config = {"from_attributes": True}

class ProfileSummaryResponse(BaseModel):
    current_series: Optional[str]
    current_points: Optional[int]
    ranking_history: list[RankingResponse]

    model_config = {"from_attributes": True}
```

**Mettre à jour : `backend/app/features/profile/repository.py`**

```python
import time
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, desc
from app.features.profile.models import PlayerProfile, RankingHistory

class ProfileRepository:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_profile(self) -> PlayerProfile | None:
        result = await self.db.execute(select(PlayerProfile).where(PlayerProfile.id == 1))
        return result.scalar_one_or_none()

    async def upsert_profile_ranking(self, series: str, points: int) -> PlayerProfile:
        now = int(time.time() * 1000)
        profile = await self.get_profile()
        if profile:
            profile.current_series = series
            profile.current_points = points
            profile.updated_at = now
        else:
            profile = PlayerProfile(id=1, current_series=series, current_points=points, updated_at=now)
            self.db.add(profile)
        await self.db.flush()
        return profile

    async def insert_ranking_history(self, series: str, points: int) -> RankingHistory:
        now = int(time.time() * 1000)
        entry = RankingHistory(series=series, points=points, recorded_at=now, updated_at=now)
        self.db.add(entry)
        await self.db.flush()
        return entry

    async def get_ranking_history(self) -> list[RankingHistory]:
        result = await self.db.execute(
            select(RankingHistory).order_by(desc(RankingHistory.recorded_at))
        )
        return list(result.scalars().all())
```

**Mettre à jour : `backend/app/features/profile/service.py`**

```python
from app.features.profile.repository import ProfileRepository
from app.features.profile.schemas import RankingRequest, RankingResponse, ProfileSummaryResponse

class ProfileService:
    def __init__(self, repository: ProfileRepository):
        self.repository = repository

    async def save_ranking(self, request: RankingRequest) -> RankingResponse:
        await self.repository.upsert_profile_ranking(request.series, request.points)
        entry = await self.repository.insert_ranking_history(request.series, request.points)
        return RankingResponse(id=entry.id, series=entry.series, points=entry.points, recorded_at=entry.recorded_at)

    async def get_profile_summary(self) -> ProfileSummaryResponse:
        profile = await self.repository.get_profile()
        history = await self.repository.get_ranking_history()
        return ProfileSummaryResponse(
            current_series=profile.current_series if profile else None,
            current_points=profile.current_points if profile else None,
            ranking_history=[
                RankingResponse(id=e.id, series=e.series, points=e.points, recorded_at=e.recorded_at)
                for e in history
            ]
        )
```

**Mettre à jour : `backend/app/api/v1/profile.py`**

```python
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from app.core.database import get_db
from app.core.security import verify_jwt
from app.features.profile.repository import ProfileRepository
from app.features.profile.service import ProfileService
from app.features.profile.schemas import RankingRequest, RankingResponse, ProfileSummaryResponse

router = APIRouter()

async def get_profile_service(db: AsyncSession = Depends(get_db)) -> ProfileService:
    return ProfileService(ProfileRepository(db))

@router.get("", response_model=ProfileSummaryResponse)
async def get_profile(
    _token=Depends(verify_jwt),
    service: ProfileService = Depends(get_profile_service)
) -> ProfileSummaryResponse:
    return await service.get_profile_summary()

@router.post("/ranking", response_model=RankingResponse, status_code=201)
async def save_ranking(
    request: RankingRequest,
    _token=Depends(verify_jwt),
    service: ProfileService = Depends(get_profile_service)
) -> RankingResponse:
    return await service.save_ranking(request)
```

**Mettre à jour : `backend/app/api/v1/router.py`**

Inclure le router profile (en plus de auth) :

```python
from app.api.v1.profile import router as profile_router
api_router.include_router(profile_router, prefix="/profile", tags=["profile"])
```

### VPS — Migration Alembic

**Nouveau fichier : `backend/alembic/versions/<hash>_add_player_profile_and_ranking_history.py`**

```python
"""add player_profiles and ranking_history

Revision ID: <generated>
Revises: 93050bf04cdd
Create Date: <date>
"""
from alembic import op
import sqlalchemy as sa

revision = '<generated>'
down_revision = '93050bf04cdd'
branch_labels = None
depends_on = None

def upgrade() -> None:
    op.create_table(
        'player_profiles',
        sa.Column('id', sa.Integer(), primary_key=True),
        sa.Column('current_series', sa.String(), nullable=True),
        sa.Column('current_points', sa.Integer(), nullable=True),
        sa.Column('updated_at', sa.Integer(), nullable=False),
    )
    op.create_table(
        'ranking_history',
        sa.Column('id', sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column('series', sa.String(), nullable=False),
        sa.Column('points', sa.Integer(), nullable=False),
        sa.Column('recorded_at', sa.Integer(), nullable=False),
        sa.Column('updated_at', sa.Integer(), nullable=False),
    )

def downgrade() -> None:
    op.drop_table('ranking_history')
    op.drop_table('player_profiles')
```

**Mettre à jour `backend/alembic/env.py`** pour importer les modèles :

```python
# Ajouter en haut des imports :
from app.features.profile.models import Base as ProfileBase
target_metadata = ProfileBase.metadata
```

> ⚠️ **Alembic generate** : après avoir créé les modèles SQLAlchemy, générer la migration avec :
> ```bash
> cd backend && uv run alembic revision --autogenerate -m "add_player_profiles_and_ranking_history"
> uv run alembic upgrade head
> ```
> Vérifier le fichier généré dans `alembic/versions/` — la commande auto-génère le `up_revision` basé sur `93050bf04cdd`.

### Android — VpsApiService (extension)

Étendre `VpsApiService.kt` avec les routes profil :

```kotlin
// Ajouter dans VpsApiService.kt :
@GET("api/v1/profile")
suspend fun getProfile(): ProfileSummaryDto

@POST("api/v1/profile/ranking")
suspend fun saveRanking(@Body request: RankingRequest): RankingEntryDto
```

**Nouveau fichier : `data/src/main/kotlin/com/secondserve/data/remote/api/dto/ProfileDto.kt`**

```kotlin
data class RankingRequest(
    val series: String,
    val points: Int
)

data class RankingEntryDto(
    val id: Int,
    val series: String,
    val points: Int,
    @Json(name = "recorded_at") val recordedAt: Long
)

data class ProfileSummaryDto(
    @Json(name = "current_series") val currentSeries: String?,
    @Json(name = "current_points") val currentPoints: Int?,
    @Json(name = "ranking_history") val rankingHistory: List<RankingEntryDto>
)
```

---

## Development Context

### Learnings de la Story 1.3 (JWT Auth)

**Patterns établis :**
- `VpsApiService` (Retrofit) + `JwtInterceptor` opérationnels — réutiliser le même client HTTP
- `AuthModule.kt` fournit `OkHttpClient` et `Retrofit` — `DataModule` les consomme via Hilt
- Moshi est configuré (`MoshiConverterFactory`) — les DTOs doivent utiliser `@Json` pour les noms snake_case
- Tests VPS : `pytest` + `AsyncClient` via `conftest.py` avec SQLite en mémoire
- Tests Android : `JUnit 5` + `MockK` pour les unit tests

**Anti-patterns évités :**
- ❌ Ne pas hardcoder la série FFT en texte libre — utiliser un `DropdownMenu` avec les 16 valeurs fixes
- ❌ Ne pas utiliser `fallbackToDestructiveMigration` — Room version 1, pas de migration nécessaire mais le principe s'applique aux futures versions
- ❌ Ne pas utiliser `print()` côté VPS — `logger = logging.getLogger(__name__)`
- ❌ Ne pas utiliser `Log.d()` côté Android — `Timber.d()`

**TokenAuthenticator introduit en Story 1.3 :**
- `TokenAuthenticator` utilise `@AuthClient` OkHttpClient dédié (sans `TokenAuthenticator`) pour éviter les deadlocks — ne pas changer ce pattern

### Résolution du deferred : AppResult.Loading

Le deferred de la Story 1.1 note que `AppResult` manque de `Loading`. Cette story est la première avec des ViewModels Orbit — le variant `Loading` doit être ajouté maintenant. La modification de `AppResult.kt` est un changement breaking pour les compilations existantes, mais comme `AppResult` n'est utilisé nulle part encore (Story 1.3 utilisait `Result<T>` de Kotlin), l'impact est nul.

### Git — Patterns récents à suivre

```
edab44b — Merge pull request #6 (Story 1.3 done)
f206608 — fix(android): remove return in expression body
a40d54d — fix(android): resolve TokenAuthenticator deadlock and authReady blank screen
```

**Conventions commits :**
- `feat(1.4):` pour les nouvelles implémentations
- `fix(1.4):` pour les corrections
- Scope module si pertinent : `feat(android):`, `feat(backend):`

### File Structure — Fichiers à créer et modifier

**Android — NOUVEAUX fichiers :**

```
android/domain/src/main/kotlin/com/secondserve/domain/
├── model/
│   ├── PlayerProfile.kt                 — NEW
│   ├── RankingEntry.kt                  — NEW
│   └── MatchContextProfile.kt           — NEW
├── repository/
│   └── PlayerProfileRepository.kt       — NEW (interface)
└── constants/
    └── FftConstants.kt                  — NEW

android/data/src/main/kotlin/com/secondserve/data/
├── local/
│   ├── db/
│   │   ├── SecondServeDatabase.kt       — NEW
│   │   └── entity/
│   │       ├── PlayerProfileEntity.kt   — NEW
│   │       ├── RankingHistoryEntity.kt  — NEW
│   │       └── Mappers.kt              — NEW (extensions toDomain())
│   └── dao/
│       └── PlayerProfileDao.kt          — NEW
└── repository/
    └── PlayerProfileRepositoryImpl.kt   — NEW

android/app/src/main/kotlin/com/secondserve/
└── di/
    └── DataModule.kt                    — NEW

android/feature/profile/src/main/kotlin/com/secondserve/feature/profile/
├── ProfileScreen.kt                     — NEW
└── ProfileViewModel.kt                  — NEW
```

**Android — FICHIERS MODIFIÉS :**

```
android/domain/src/main/kotlin/com/secondserve/domain/AppResult.kt
  → Ajouter variante Loading

android/data/src/main/kotlin/com/secondserve/data/remote/api/VpsApiService.kt
  → Ajouter getProfile() et saveRanking()

android/data/src/main/kotlin/com/secondserve/data/remote/api/dto/ProfileDto.kt
  → NEW : RankingRequest, RankingEntryDto, ProfileSummaryDto
```

**Backend VPS — FICHIERS MODIFIÉS/COMPLÉTÉS :**

```
backend/app/features/profile/models.py    — FILL (était vide)
backend/app/features/profile/schemas.py   — FILL (était vide)
backend/app/features/profile/repository.py — FILL (était vide)
backend/app/features/profile/service.py   — FILL (était vide)
backend/app/api/v1/profile.py             — FILL (était stub vide)
backend/app/api/v1/router.py              — MODIFY: inclure profile router
backend/alembic/env.py                    — MODIFY: importer ProfileBase
backend/alembic/versions/<hash>_add_player_profile_and_ranking_history.py — NEW
```

---

## Testing Requirements

### Android — Tests unitaires (:data/test/ et :domain/test/)

**`PlayerProfileRepositoryImplTest.kt`**
- `saveRanking()` avec série valide → upsert profile + insert history
- `saveRanking()` avec VPS failure → succès local, log warning
- `getRankingHistory()` retourne Flow avec entités triées par date décroissante
- `buildMatchContextProfile()` retourne `MatchContextProfile` avec `fftSeries` correct

**`ProfileViewModelTest.kt`**
- `saveRanking("15/2", 100)` → `AppResult.Success` → `RankingSaved` side effect
- `saveRanking("invalide", 100)` → `ShowError` side effect sans appel repository
- `saveRanking("15/2", -1)` → `ShowError` sans appel repository

### VPS — Tests d'intégration (:backend/tests/integration/)

**Nouveau fichier : `backend/tests/integration/test_profile_api.py`**

```python
@pytest.mark.asyncio
async def test_save_ranking_valid(client):
    response = await client.post("/api/v1/profile/ranking",
        json={"series": "15/2", "points": 850},
        headers={"Authorization": f"Bearer {valid_token}"}
    )
    assert response.status_code == 201
    data = response.json()
    assert data["series"] == "15/2"
    assert data["points"] == 850

@pytest.mark.asyncio
async def test_save_ranking_invalid_series(client):
    response = await client.post("/api/v1/profile/ranking",
        json={"series": "invalide", "points": 100},
        headers={"Authorization": f"Bearer {valid_token}"}
    )
    assert response.status_code == 422  # Pydantic validation error

@pytest.mark.asyncio
async def test_get_profile_empty(client):
    response = await client.get("/api/v1/profile",
        headers={"Authorization": f"Bearer {valid_token}"}
    )
    assert response.status_code == 200
    data = response.json()
    assert data["current_series"] is None
    assert data["ranking_history"] == []

@pytest.mark.asyncio
async def test_get_profile_after_save(client):
    await client.post("/api/v1/profile/ranking",
        json={"series": "30/2", "points": 1200},
        headers={"Authorization": f"Bearer {valid_token}"}
    )
    response = await client.get("/api/v1/profile",
        headers={"Authorization": f"Bearer {valid_token}"}
    )
    assert response.json()["current_series"] == "30/2"
    assert len(response.json()["ranking_history"]) == 1

@pytest.mark.asyncio
async def test_save_ranking_without_token(client):
    response = await client.post("/api/v1/profile/ranking",
        json={"series": "15/2", "points": 850}
    )
    assert response.status_code == 401
```

**Test unitaire VPS : `backend/tests/unit/test_profile_service.py`**

```python
def test_valid_series_list():
    from app.features.profile.schemas import FFT_VALID_SERIES, RankingRequest
    assert len(FFT_VALID_SERIES) == 15
    # Valider toutes les séries de la liste
    for series in FFT_VALID_SERIES:
        req = RankingRequest(series=series, points=100)
        assert req.series == series

def test_invalid_series_rejected():
    from pydantic import ValidationError
    from app.features.profile.schemas import RankingRequest
    with pytest.raises(ValidationError):
        RankingRequest(series="40/5", points=100)  # n'existe pas en FFT
```

---

## Acceptance Criteria Checklist

- [ ] AC1: Formulaire Profil affiche les 16 séries FFT valides via DropdownMenu
- [ ] AC2: Série hors liste → message d'erreur explicite (validation Pydantic côté VPS + validation UI côté Android)
- [ ] AC3: Sauvegarde valide → apparaît dans la timeline (ordre décroissant)
- [ ] AC4: Tables `player_profiles` et `ranking_history` créées en Room (Database version 1)
- [ ] AC5: Migration Alembic appliquée sur VPS (tables identiques)
- [ ] AC6: Classement actuel (série) visible en résumé sur l'écran Profil
- [ ] AC7: Série courante incluse dans `MatchContextProfile.fftSeries`

## Tasks / Subtasks

### Domaine Android

- [x] **Task D-1** — Ajouter `AppResult.Loading` dans `AppResult.kt` (deferred de Story 1.1)
- [x] **Task D-2** — Créer `PlayerProfile.kt`, `RankingEntry.kt`, `MatchContextProfile.kt` dans `:domain/model/`
- [x] **Task D-3** — Créer `PlayerProfileRepository.kt` (interface) dans `:domain/repository/`
- [x] **Task D-4** — Créer `FftConstants.kt` dans `:domain/constants/`

### Room Android

- [x] **Task R-1** — Créer `PlayerProfileEntity.kt` et `RankingHistoryEntity.kt` dans `:data/local/db/entity/`
- [x] **Task R-2** — Créer `Mappers.kt` avec extensions `toDomain()` pour les deux entités
- [x] **Task R-3** — Créer `PlayerProfileDao.kt` avec les 4 méthodes (getProfile, upsertProfile, insertRanking, getRankingHistory)
- [x] **Task R-4** — Créer `SecondServeDatabase.kt` (version = 1, entities = [PlayerProfileEntity, RankingHistoryEntity])

### Data Layer Android

- [x] **Task DA-1** — Créer `ProfileDto.kt` dans `:data/remote/api/dto/` (RankingRequest, RankingEntryDto, ProfileSummaryDto)
- [x] **Task DA-2** — Étendre `VpsApiService.kt` avec `getProfile()` et `saveRanking()`
- [x] **Task DA-3** — Créer `PlayerProfileRepositoryImpl.kt`
- [x] **Task DA-4** — Créer `DataModule.kt` (Hilt module : `SecondServeDatabase`, `PlayerProfileDao`, `PlayerProfileRepository`)

### Feature Profile Android

- [x] **Task UI-0** — Créer/vérifier `feature/profile/build.gradle.kts` avec dépendances Compose + Orbit + Hilt
- [x] **Task UI-1** — Créer `ProfileViewModel.kt` (Orbit MVI, `saveRanking()`, `loadProfile()`)
- [x] **Task UI-2** — Créer `ProfileScreen.kt` (résumé classement actuel + section saisie + timeline historique)
  - [x] DropdownMenu avec les 16 séries FFT
  - [x] Champ numérique pour les points
  - [x] `LazyColumn` pour la timeline historique
- [x] **Task UI-3** — Mettre à jour `AppNavGraph.kt` pour inclure la route `"profile"` → `ProfileScreen`

### Backend VPS

- [x] **Task VPS-1** — Remplir `features/profile/models.py` (`PlayerProfile`, `RankingHistory`, SQLAlchemy)
- [x] **Task VPS-2** — Remplir `features/profile/schemas.py` (`RankingRequest` avec validators, `RankingResponse`, `ProfileSummaryResponse`)
- [x] **Task VPS-3** — Remplir `features/profile/repository.py` (`ProfileRepository`)
- [x] **Task VPS-4** — Remplir `features/profile/service.py` (`ProfileService`)
- [x] **Task VPS-5** — Remplir `api/v1/profile.py` (`GET /profile`, `POST /profile/ranking`, protégés par `verify_jwt`)
- [x] **Task VPS-6** — Mettre à jour `api/v1/router.py` pour inclure le profile router (déjà fait)
- [x] **Task VPS-7** — Mettre à jour `alembic/env.py` (import modèles profile pour Base.metadata)
- [x] **Task VPS-8** — Créer la migration Alembic (`player_profiles` + `ranking_history`)

### Tests

- [x] **Task T-1** — Tests unitaires Android : `PlayerProfileRepositoryImplTest.kt`, `ProfileViewModelTest.kt`
- [x] **Task T-2** — Tests VPS intégration : `tests/integration/test_profile_api.py`
- [x] **Task T-3** — Tests VPS unitaires : `tests/unit/test_profile_service.py`

---

## Risks & Mitigations

| Risque | Mitigation |
|--------|------------|
| Room version 1 sans `exportSchema=true` → perte de schéma de référence | Activer `exportSchema = true` dans `@Database`, stocker dans `schemas/` |
| `alembic/env.py` importe `ProfileBase` mais FastAPI ne charge pas le module au démarrage | Import explicite dans `env.py`, tester `alembic upgrade head` dans les tests CI |
| Moshi ne désérialise pas automatiquement les snake_case VPS | Utiliser `@Json(name = "recorded_at")` sur les champs DTOs |
| `ProfileScreen` en navigation — `AppNavGraph.kt` doit inclure la route Profile | Mettre à jour `AppNavGraph.kt` pour inclure `ProfileScreen` |
| DropdownMenu Compose exposé sans dismiss → UX cassé | Tester le lifecycle du DropdownMenu (open/close state) |

## Success Criteria

- ✅ Saisie de classement FFT valide → sauvegardé localement + visible en timeline
- ✅ Saisie série invalide → rejetée avec message clair (UI + VPS)
- ✅ `buildMatchContextProfile()` retourne `fftSeries` correctement renseigné
- ✅ Tables Room créées, Room sync fonctionne sans crash
- ✅ Migration Alembic `upgrade head` sans erreur
- ✅ Routes VPS `/api/v1/profile` et `/api/v1/profile/ranking` retournent 200/201 avec token valide, 401 sans token
- ✅ Tous les tests passent (unit + intégration)

## References

- [Source: epics.md § Story 1.4] — Acceptance criteria, user story, FR-14
- [Source: architecture.md § Data Architecture] — Room schema, tables, conventions snake_case
- [Source: architecture.md § Naming Patterns] — Conventions DB, REST, Kotlin, Python
- [Source: architecture.md § Implementation Patterns] — `sealed Result<T>`, Timber, logging
- [Source: architecture.md § Project Structure] — Arborescence fichiers Android et VPS
- [Source: architecture.md § API & Communication] — Format JSON, error schema, status codes
- [Source: _bmad-output/implementation-artifacts/1-3-jwt-authentication-android-vps.md] — VpsApiService, AuthModule, Moshi, patterns Hilt établis
- [Source: _bmad-output/implementation-artifacts/deferred-work.md] — `AppResult.Loading` manquant (deferred 1.1), `alembic/env.py` Base.metadata vide

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

- Utilisation du `Base` partagé de `app.core.database` pour les modèles SQLAlchemy (au lieu de créer un nouveau `DeclarativeBase` dans models.py)
- JWT déjà appliqué au niveau du router dans `router.py` — pas de double dépendance dans `profile.py`
- `feature/profile/build.gradle.kts` complété avec `:data`, timber, tests (junit5, mockk, turbine, coroutines-test)
- `ksp { arg("room.schemaLocation", ...) }` ajouté dans `data/build.gradle.kts` pour exportSchema
- `AppNavGraph.kt` mis à jour avec NavHost + composable "profile" (était vide)
- 22/22 tests backend passent (7 unitaires + 8 intégration profile + 7 auth/health existants), zéro régression

### File List

**Android — Nouveaux fichiers :**
- `android/domain/src/main/kotlin/com/secondserve/domain/model/PlayerProfile.kt`
- `android/domain/src/main/kotlin/com/secondserve/domain/model/RankingEntry.kt`
- `android/domain/src/main/kotlin/com/secondserve/domain/model/MatchContextProfile.kt`
- `android/domain/src/main/kotlin/com/secondserve/domain/repository/PlayerProfileRepository.kt`
- `android/domain/src/main/kotlin/com/secondserve/domain/constants/FftConstants.kt`
- `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/PlayerProfileEntity.kt`
- `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/RankingHistoryEntity.kt`
- `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/Mappers.kt`
- `android/data/src/main/kotlin/com/secondserve/data/local/dao/PlayerProfileDao.kt`
- `android/data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt`
- `android/data/src/main/kotlin/com/secondserve/data/remote/api/dto/ProfileDto.kt`
- `android/data/src/main/kotlin/com/secondserve/data/repository/PlayerProfileRepositoryImpl.kt`
- `android/app/src/main/kotlin/com/secondserve/di/DataModule.kt`
- `android/feature/profile/src/main/kotlin/com/secondserve/feature/profile/ProfileViewModel.kt`
- `android/feature/profile/src/main/kotlin/com/secondserve/feature/profile/ProfileScreen.kt`
- `android/data/src/test/kotlin/com/secondserve/data/repository/PlayerProfileRepositoryImplTest.kt`
- `android/feature/profile/src/test/kotlin/com/secondserve/feature/profile/ProfileViewModelTest.kt`

**Android — Fichiers modifiés :**
- `android/domain/src/main/kotlin/com/secondserve/domain/AppResult.kt` (ajout Loading)
- `android/data/src/main/kotlin/com/secondserve/data/remote/api/VpsApiService.kt` (ajout getProfile + saveRanking)
- `android/data/build.gradle.kts` (ksp schemaLocation + timber + turbine)
- `android/feature/profile/build.gradle.kts` (ajout :data, timber, tests)
- `android/app/src/main/kotlin/com/secondserve/navigation/AppNavGraph.kt` (NavHost + route profile)

**Backend VPS — Fichiers remplis/créés :**
- `backend/app/features/profile/models.py`
- `backend/app/features/profile/schemas.py`
- `backend/app/features/profile/repository.py`
- `backend/app/features/profile/service.py`
- `backend/app/api/v1/profile.py`
- `backend/alembic/env.py` (import profile models)
- `backend/alembic/versions/a1b2c3d4e5f6_add_player_profiles_and_ranking_history.py`
- `backend/tests/integration/test_profile_api.py`
- `backend/tests/unit/test_profile_service.py`

### Change Log

- 2026-06-16 : Implémentation complète de la Story 1.4 — classement FFT saisie et historique. Ajout Room DB v1, entités PlayerProfile/RankingHistory, DAO, Repository, DI Hilt, ProfileScreen Compose, ProfileViewModel Orbit MVI, backend FastAPI profile endpoints, migration Alembic, 22 tests passants.

### Review Findings

- [x] [Review][Patch] Moshi sans `KotlinJsonAdapterFactory` — toutes les API calls crashent au runtime [`android/app/.../di/AuthModule.kt:49`]
- [x] [Review][Patch] `saveRanking()` Room non atomique — `upsertProfile` et `insertRanking` pas dans une `@Transaction` [`android/data/.../repository/PlayerProfileRepositoryImpl.kt`]
- [x] [Review][Patch] `ProfileViewModel.loadProfile()` — `.collect {}` infini bloque l'intent Orbit ; si `loadProfile()` est rappelé, deux collecteurs parallèles s'activent [`android/feature/profile/.../ProfileViewModel.kt`]
- [x] [Review][Patch] `saveRanking()` succès ne met pas à jour `currentSeries`/`currentPoints` dans l'UI state — `RankingSummaryCard` reste obsolète jusqu'au redémarrage de l'écran (AC6) [`android/feature/profile/.../ProfileViewModel.kt`]
- [x] [Review][Patch] `SimpleDateFormat` instancié dans chaque item `LazyColumn` via `remember{}` — optimiser en passant le formatter au niveau de l'écran [`android/feature/profile/.../ProfileScreen.kt`]
- [x] [Review][Patch] `ProfileSummaryResponse` — `Optional[str]` et `Optional[int]` sans valeur par défaut `= None` en Pydantic v2 [`backend/app/features/profile/schemas.py`]
- [x] [Review][Patch] `menuAnchor()` sans argument déprécié en Material3 1.3+ [`android/feature/profile/.../ProfileScreen.kt`]
- [x] [Review][Defer] `runBlocking` dans `TokenAuthenticator` + race condition sur `reauthenticate()` — pré-existant Story 1.3 [`android/data/.../api/TokenAuthenticator.kt`] — deferred, pre-existing
- [x] [Review][Defer] `saveToken()` utilise `apply()` async — token peut être null à la relecture immédiate — pré-existant Story 1.3 [`android/data/.../security/JwtTokenStore.kt`] — deferred, pre-existing
- [x] [Review][Defer] Room schema JSON version 1 non committé — peut casser CI migration future [`android/data/schemas/`] — deferred, nécessite build Android pour générer
- [x] [Review][Defer] `navController.popBackStack()` sur seule destination — no-op silencieux — navigation provisoire Story 1.4 [`android/app/.../navigation/AppNavGraph.kt`] — deferred, pre-existing

