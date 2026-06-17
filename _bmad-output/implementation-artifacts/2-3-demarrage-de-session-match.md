# Story 2.3 : Démarrage de session match

Status: ready-for-dev

## Story

**As a** player,  
**I want** to start a Match session by configuring its surface and format,  
**So that** the session is tracked and my coaching is contextualized from the first point.

## Acceptance Criteria

1. **Given** je suis sur l'écran d'accueil  
   **When** je tape "Nouveau match"  
   **Then** un formulaire s'affiche avec : surface (obligatoire : Terre battue / Gazon / Dur / Carpet), format sets (obligatoire : 1 set / 3 sets), règle 3e set (obligatoire si 3 sets : avantage complet / super tie-break à 10 pts / set décisif raccourci), adversaire (optionnel, texte libre), type de compétition (optionnel), tournoi (optionnel)

2. **When** je soumets avec surface + format uniquement (sans les champs optionnels)  
   **Then** la session est créée et persistée en Room — table `sessions` créée via la migration de cette story  
   **And** aucune connexion réseau n'est requise pour créer et démarrer une session

3. **And** la session est accessible dans l'historique même si elle est interrompue sans clôture formelle

4. **And** le format choisi (MatchFormat + ThirdSetRule) est stocké avec la session et conditionne la logique de score pour toute sa durée (Story 2.4 lira ce format depuis `SessionRepository`)

5. **And** la migration Alembic correspondante (`sessions`) est appliquée sur le VPS

## Tasks / Subtasks

### Domain
- [ ] **Task D-1** — Créer `android/domain/src/main/kotlin/com/secondserve/domain/model/Session.kt` : `Session` data class + `SessionStatus` enum (`ACTIVE`, `COMPLETED`, `INTERRUPTED`) + `SessionType` enum (`MATCH`, `TRAINING`)
- [ ] **Task D-2** — Créer `android/domain/src/main/kotlin/com/secondserve/domain/repository/SessionRepository.kt` : interface avec `suspend fun createSession(session: Session): AppResult<Session>`, `fun getAllSessions(): Flow<List<Session>>`, `suspend fun getSessionById(id: Long): Session?`

### Data Layer
- [ ] **Task DB-1** — Créer `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/SessionEntity.kt` : `@Entity(tableName = "sessions", indices = [...])` (voir spec schema ci-dessous)
- [ ] **Task DB-2** — Mettre à jour `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/Mappers.kt` : ajouter `fun SessionEntity.toDomain()` et `fun Session.toEntity()` (imports `SessionFormat`, `MatchFormat`, `ThirdSetRule`, `SessionStatus`, `SessionType`)
- [ ] **Task DB-3** — Créer `android/data/src/main/kotlin/com/secondserve/data/local/dao/SessionDao.kt` : `@Dao` interface
- [ ] **Task DB-4** — Mettre à jour `android/data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt` : ajouter `SessionEntity::class` dans `@Database(entities=[...])`, bumper version `3 → 4`, ajouter `MIGRATION_3_4` dans `companion object`
- [ ] **Task DB-5** — Créer `android/data/src/main/kotlin/com/secondserve/data/repository/SessionRepositoryImpl.kt` : `@Singleton` `@Inject constructor(private val dao: SessionDao)`
- [ ] **Task DB-6** — Créer `android/data/src/main/kotlin/com/secondserve/data/di/SessionModule.kt` : abstract class `@Binds @Singleton` (pattern ScoreModule.kt)
- [ ] **Task DB-7** — Mettre à jour `android/app/src/main/kotlin/com/secondserve/di/DataModule.kt` : ajouter `provideSessionDao()`, ajouter `SecondServeDatabase.MIGRATION_3_4` dans `.addMigrations(...)`

### Feature Layer
- [ ] **Task F-1** — Créer `android/feature/match/src/main/kotlin/com/secondserve/feature/match/NewMatchViewModel.kt` : `@HiltViewModel`, Orbit MVI, `NewMatchUiState`, `NewMatchSideEffect` (voir spec ci-dessous)
- [ ] **Task F-2** — Créer `android/feature/match/src/main/kotlin/com/secondserve/feature/match/NewMatchScreen.kt` : formulaire Compose (voir spec UI ci-dessous)

### Navigation
- [ ] **Task N-1** — Créer `android/app/src/main/kotlin/com/secondserve/HomeScreen.kt` : écran minimal avec bouton "Nouveau match" + accès au profil
- [ ] **Task N-2** — Mettre à jour `android/app/src/main/kotlin/com/secondserve/navigation/AppNavGraph.kt` : `startDestination = "home"`, ajouter routes `home` et `new_match`

### Backend VPS
- [ ] **Task VPS-1** — Créer `backend/alembic/versions/d4e5f6a7b8c9_add_sessions_table.py` : migration Alembic (voir spec ci-dessous)
- [ ] **Task VPS-2** — Créer `backend/app/features/sessions/models.py` : SQLAlchemy model `SessionModel`
- [ ] **Task VPS-3** — Créer `backend/app/features/sessions/schemas.py` : Pydantic v2 `SessionCreateRequest`, `SessionResponse`, `SessionsResponse`
- [ ] **Task VPS-4** — Créer `backend/app/features/sessions/repository.py` : `SessionRepository` async
- [ ] **Task VPS-5** — Créer `backend/app/features/sessions/service.py` : `SessionService`
- [ ] **Task VPS-6** — Mettre à jour `backend/app/api/v1/sessions.py` : implémenter `POST /sessions` (remplace le commentaire placeholder)

### Tests
- [ ] **Task T-1** — Créer `android/data/src/test/kotlin/com/secondserve/data/repository/SessionRepositoryImplTest.kt` : JVM tests
- [ ] **Task T-2** — Valider localement avec `./gradlew :data:test` et `./gradlew :feature:match:assembleDebug` (Android SDK absent de l'env distant)

---

## Dev Notes

### Guardrails critiques

#### ⚠️ DB version : actuellement 3 — cette story crée MIGRATION_3_4

`SecondServeDatabase.kt` est à version **3**. Migrations existantes :
- `MIGRATION_1_2` — profil player_profiles (colonnes play_style, preferred_surfaces, etc.)
- `MIGRATION_2_3` — table `work_axes`

Cette story ajoute `MIGRATION_3_4` pour `sessions`. **NE PAS modifier les migrations existantes.**

La méthode `.addMigrations(...)` dans `DataModule.kt` doit devenir :
```kotlin
.addMigrations(
    SecondServeDatabase.MIGRATION_1_2,
    SecondServeDatabase.MIGRATION_2_3,
    SecondServeDatabase.MIGRATION_3_4  // ← AJOUTER
)
```

#### ⚠️ AppResult.Error — UN SEUL argument Throwable

```kotlin
// AppResult.kt — signature réelle :
data class Error(val exception: Throwable) : AppResult<Nothing>()
// PAS data class Error(val exception: Throwable, val message: String)
```
Toujours `AppResult.Error(e)` — jamais `AppResult.Error(e, "message")`.

#### ⚠️ SurfaceConstants et SessionFormat EXISTENT — ne pas réinventer

`SurfaceConstants` est dans `domain/model/SurfaceConstants.kt` :
```kotlin
object SurfaceConstants {
    const val CLAY = "CLAY"; const val GRASS = "GRASS"
    const val HARD = "HARD"; const val CARPET = "CARPET"
    val ALL = listOf(CLAY, GRASS, HARD, CARPET)
    val DISPLAY_NAMES = mapOf(CLAY to "Terre battue", GRASS to "Gazon", HARD to "Dur", CARPET to "Carpet")
}
```

`MatchFormat`, `ThirdSetRule`, `SessionFormat` sont dans `domain/model/SessionFormat.kt` :
```kotlin
enum class MatchFormat { BEST_OF_1, BEST_OF_3 }
enum class ThirdSetRule { FULL_ADVANTAGE, SUPER_TIE_BREAK_10, SHORT_DECISIVE_SET }
data class SessionFormat(val matchFormat: MatchFormat, val thirdSetRule: ThirdSetRule = ThirdSetRule.FULL_ADVANTAGE)
```
Utiliser ces types directement dans `Session.kt` et `NewMatchViewModel.kt`. Ne pas recréer ces enums.

#### ⚠️ Pattern DI : @Inject constructor + @Binds (ScoreModule pattern)

Suivre `ScoreModule.kt` (`data/di/ScoreModule.kt`) :
```kotlin
// data/di/SessionModule.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {
    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository
}
```
`SessionRepositoryImpl` doit avoir `@Inject constructor` + `@Singleton` pour que `@Binds` fonctionne.

**NE PAS** créer un `@Provides fun provideSessionRepository(...)` dans `DataModule.kt` — ce serait un doublon conflit avec le `@Binds`.

`DataModule.kt` n'a besoin que de `provideSessionDao(db: SecondServeDatabase): SessionDao`.

#### ⚠️ Mappers co-localisés dans Mappers.kt

Les extensions `toDomain()` et `toEntity()` doivent être ajoutées dans `data/local/db/entity/Mappers.kt` (fichier existant), pas dans `SessionEntity.kt`. Le fichier contient déjà les mappers pour `PlayerProfileEntity`, `RankingHistoryEntity`, `WorkAxisEntity`.

#### ⚠️ Timber — JAMAIS Log.*

```kotlin
Timber.d("SessionRepo: session créée id=%d", id)   // ✅
Timber.e(e, "SessionRepo: createSession failed")     // ✅
Log.d("TAG", message)                                 // ❌ interdit
```

---

### Spécifications techniques détaillées

#### `Session.kt` (NEW — `:domain/model/`)

```kotlin
package com.secondserve.domain.model

enum class SessionStatus { ACTIVE, COMPLETED, INTERRUPTED }
enum class SessionType { MATCH, TRAINING }

data class Session(
    val id: Long = 0L,
    val surface: String,              // SurfaceConstants.CLAY / GRASS / HARD / CARPET
    val format: SessionFormat,
    val opponent: String? = null,
    val competitionType: String? = null,
    val tournament: String? = null,
    val status: SessionStatus = SessionStatus.ACTIVE,
    val sessionType: SessionType = SessionType.MATCH,
    val result: String? = null,       // "VICTORY" / "DEFEAT" / "DRAW" — set à Story 2.6
    val createdAt: Long,              // epoch ms
    val updatedAt: Long               // epoch ms
)
```

#### `SessionEntity.kt` (NEW — `:data/local/db/entity/`)

```kotlin
package com.secondserve.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sessions",
    indices = [Index(value = ["surface"], name = "idx_sessions_surface")]
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "surface") val surface: String,
    @ColumnInfo(name = "match_format") val matchFormat: String,    // MatchFormat.name
    @ColumnInfo(name = "third_set_rule") val thirdSetRule: String, // ThirdSetRule.name
    @ColumnInfo(name = "opponent") val opponent: String? = null,
    @ColumnInfo(name = "competition_type") val competitionType: String? = null,
    @ColumnInfo(name = "tournament") val tournament: String? = null,
    @ColumnInfo(name = "status") val status: String = "ACTIVE",       // SessionStatus.name
    @ColumnInfo(name = "session_type") val sessionType: String = "MATCH", // SessionType.name
    @ColumnInfo(name = "result") val result: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
```

Mappers à ajouter dans **`Mappers.kt`** (fichier existant) :

```kotlin
fun SessionEntity.toDomain(): Session = Session(
    id = id,
    surface = surface,
    format = SessionFormat(
        matchFormat = MatchFormat.valueOf(matchFormat),
        thirdSetRule = ThirdSetRule.valueOf(thirdSetRule)
    ),
    opponent = opponent,
    competitionType = competitionType,
    tournament = tournament,
    status = SessionStatus.valueOf(status),
    sessionType = SessionType.valueOf(sessionType),
    result = result,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Session.toEntity(): SessionEntity = SessionEntity(
    id = id,
    surface = surface,
    matchFormat = format.matchFormat.name,
    thirdSetRule = format.thirdSetRule.name,
    opponent = opponent,
    competitionType = competitionType,
    tournament = tournament,
    status = status.name,
    sessionType = sessionType.name,
    result = result,
    createdAt = createdAt,
    updatedAt = updatedAt
)
```

#### MIGRATION_3_4 (`:data/local/db/SecondServeDatabase.kt`)

```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                surface TEXT NOT NULL,
                match_format TEXT NOT NULL,
                third_set_rule TEXT NOT NULL,
                opponent TEXT,
                competition_type TEXT,
                tournament TEXT,
                status TEXT NOT NULL DEFAULT 'ACTIVE',
                session_type TEXT NOT NULL DEFAULT 'MATCH',
                result TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent())
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_sessions_surface ON sessions (surface)"
        )
    }
}
```

Et dans l'annotation `@Database` :
```kotlin
@Database(
    entities = [
        PlayerProfileEntity::class,
        RankingHistoryEntity::class,
        WorkAxisEntity::class,
        SessionEntity::class   // ← AJOUTER
    ],
    version = 4,               // ← 3 → 4
    exportSchema = true
)
```

#### `SessionDao.kt` (NEW — `:data/local/dao/`)

```kotlin
package com.secondserve.data.local.dao

import androidx.room.*
import com.secondserve.data.local.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity): Long

    @Query("SELECT * FROM sessions ORDER BY created_at DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): SessionEntity?

    @Update
    suspend fun update(session: SessionEntity)
}
```

#### `SessionRepositoryImpl.kt` (NEW — `:data/repository/`)

```kotlin
package com.secondserve.data.repository

import com.secondserve.data.local.dao.SessionDao
import com.secondserve.data.local.db.entity.toDomain
import com.secondserve.data.local.db.entity.toEntity
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.Session
import com.secondserve.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val dao: SessionDao
) : SessionRepository {

    override suspend fun createSession(session: Session): AppResult<Session> = try {
        val id = dao.insert(session.toEntity())
        Timber.d("SessionRepository: session créée id=%d", id)
        AppResult.Success(session.copy(id = id))
    } catch (e: Exception) {
        Timber.e(e, "SessionRepository: createSession failed")
        AppResult.Error(e)
    }

    override fun getAllSessions(): Flow<List<Session>> =
        dao.getAllSessions().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getSessionById(id: Long): Session? =
        dao.getById(id)?.toDomain()
}
```

#### `NewMatchViewModel.kt` (NEW — `:feature:match/`)

Pattern exact de `ProfileViewModel.kt` (Orbit MVI) :

```kotlin
package com.secondserve.feature.match

import androidx.lifecycle.ViewModel
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.*
import com.secondserve.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

@HiltViewModel
class NewMatchViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel(), ContainerHost<NewMatchUiState, NewMatchSideEffect> {

    override val container = container<NewMatchUiState, NewMatchSideEffect>(NewMatchUiState())

    fun onSurfaceSelected(surface: String) = intent {
        reduce { state.copy(selectedSurface = surface) }
    }

    fun onMatchFormatSelected(format: MatchFormat) = intent {
        reduce {
            state.copy(
                selectedMatchFormat = format,
                selectedThirdSetRule = if (format == MatchFormat.BEST_OF_1) null
                                       else state.selectedThirdSetRule ?: ThirdSetRule.FULL_ADVANTAGE
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

    fun startMatch() = intent {
        val surface = state.selectedSurface ?: return@intent
        val matchFormat = state.selectedMatchFormat ?: return@intent
        val thirdSetRule = if (matchFormat == MatchFormat.BEST_OF_3)
            state.selectedThirdSetRule ?: ThirdSetRule.FULL_ADVANTAGE
        else ThirdSetRule.FULL_ADVANTAGE

        reduce { state.copy(isLoading = true, error = null) }

        val now = System.currentTimeMillis()
        val session = Session(
            surface = surface,
            format = SessionFormat(matchFormat = matchFormat, thirdSetRule = thirdSetRule),
            opponent = state.opponent.takeIf { it.isNotBlank() },
            competitionType = state.competitionType.takeIf { it.isNotBlank() },
            tournament = state.tournament.takeIf { it.isNotBlank() },
            createdAt = now,
            updatedAt = now
        )

        when (val result = sessionRepository.createSession(session)) {
            is AppResult.Success -> {
                reduce { state.copy(isLoading = false) }
                postSideEffect(NewMatchSideEffect.SessionStarted(result.data.id))
            }
            is AppResult.Error -> {
                reduce { state.copy(isLoading = false, error = "Impossible de créer la session") }
                postSideEffect(NewMatchSideEffect.ShowError("Impossible de créer la session"))
            }
            AppResult.Loading -> {}
        }
    }
}

data class NewMatchUiState(
    val selectedSurface: String? = null,
    val selectedMatchFormat: MatchFormat? = null,
    val selectedThirdSetRule: ThirdSetRule? = null,
    val opponent: String = "",
    val competitionType: String = "",
    val tournament: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val canStartMatch: Boolean get() =
        selectedSurface != null && selectedMatchFormat != null &&
        (selectedMatchFormat == MatchFormat.BEST_OF_1 || selectedThirdSetRule != null)
}

sealed class NewMatchSideEffect {
    data class SessionStarted(val sessionId: Long) : NewMatchSideEffect()
    data class ShowError(val message: String) : NewMatchSideEffect()
}
```

#### `NewMatchScreen.kt` — Points UI critiques

- Surfaces : chips/boutons sélectionnables via `SurfaceConstants.ALL` + `SurfaceConstants.DISPLAY_NAMES`
- Format : boutons radio "1 set" (`BEST_OF_1`) / "3 sets" (`BEST_OF_3`)
- Règle 3e set : visible **uniquement si** `BEST_OF_3` sélectionné ; 3 options : "Avantage complet" / "Super tie-break à 10" / "Set décisif raccourci"
- Champs optionnels : `OutlinedTextField` pour adversaire, type compétition, tournoi
- Bouton "Démarrer le match" : désactivé si `canStartMatch == false` (état vient de `NewMatchUiState`)
- Collecter les side effects : `SessionStarted(sessionId)` → naviguer vers l'écran match (Story 2.4 — pour l'instant, naviguer back ou afficher un toast)
- Pattern de collecte side effects : voir `ProfileScreen.kt` pour référence

#### Backend VPS — `d4e5f6a7b8c9_add_sessions_table.py`

```python
"""add sessions table

Revision ID: d4e5f6a7b8c9
Revises: c3d4e5f6a7b8
Create Date: 2026-06-17
"""
from alembic import op
import sqlalchemy as sa

revision = 'd4e5f6a7b8c9'
down_revision = 'c3d4e5f6a7b8'
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        'sessions',
        sa.Column('id', sa.Integer(), nullable=False, autoincrement=True),
        sa.Column('surface', sa.String(), nullable=False),
        sa.Column('match_format', sa.String(), nullable=False),
        sa.Column('third_set_rule', sa.String(), nullable=False),
        sa.Column('opponent', sa.String(), nullable=True),
        sa.Column('competition_type', sa.String(), nullable=True),
        sa.Column('tournament', sa.String(), nullable=True),
        sa.Column('status', sa.String(), nullable=False, server_default='ACTIVE'),
        sa.Column('session_type', sa.String(), nullable=False, server_default='MATCH'),
        sa.Column('result', sa.String(), nullable=True),
        sa.Column('created_at', sa.Integer(), nullable=False),
        sa.Column('updated_at', sa.Integer(), nullable=False),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index('idx_sessions_surface', 'sessions', ['surface'])


def downgrade() -> None:
    op.drop_index('idx_sessions_surface', table_name='sessions')
    op.drop_table('sessions')
```

#### Backend VPS — patterns service/repository

Suivre exactement le pattern `work_axes/` (voir `features/work_axes/service.py` et `api/v1/work_axes.py`).

`sessions/models.py` — SQLAlchemy :
```python
from sqlalchemy import Column, Integer, String
from app.core.database import Base

class SessionModel(Base):
    __tablename__ = "sessions"
    id = Column(Integer, primary_key=True, autoincrement=True)
    surface = Column(String, nullable=False)
    match_format = Column(String, nullable=False)
    third_set_rule = Column(String, nullable=False)
    opponent = Column(String, nullable=True)
    competition_type = Column(String, nullable=True)
    tournament = Column(String, nullable=True)
    status = Column(String, nullable=False, default="ACTIVE")
    session_type = Column(String, nullable=False, default="MATCH")
    result = Column(String, nullable=True)
    created_at = Column(Integer, nullable=False)
    updated_at = Column(Integer, nullable=False)
```

`sessions/schemas.py` — Pydantic v2 :
```python
from pydantic import BaseModel
from typing import Optional

class SessionCreateRequest(BaseModel):
    surface: str
    match_format: str
    third_set_rule: str
    opponent: Optional[str] = None
    competition_type: Optional[str] = None
    tournament: Optional[str] = None
    created_at: int  # epoch ms

class SessionResponse(BaseModel):
    id: int
    surface: str
    match_format: str
    third_set_rule: str
    opponent: Optional[str] = None
    competition_type: Optional[str] = None
    tournament: Optional[str] = None
    status: str
    session_type: str
    result: Optional[str] = None
    created_at: int
    updated_at: int
    model_config = {"from_attributes": True}

class SessionsResponse(BaseModel):
    items: list[SessionResponse]
    total: int
```

`api/v1/sessions.py` — remplacer le commentaire placeholder par :
```python
import logging
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from app.core.database import get_db
from app.features.sessions.repository import SessionRepository
from app.features.sessions.schemas import SessionCreateRequest, SessionResponse
from app.features.sessions.service import SessionService

logger = logging.getLogger(__name__)
router = APIRouter()

def get_session_service(db: AsyncSession = Depends(get_db)) -> SessionService:
    return SessionService(SessionRepository(db))

@router.post("", response_model=SessionResponse, status_code=201)
async def create_session(
    request: SessionCreateRequest,
    service: SessionService = Depends(get_session_service)
):
    return await service.create_session(request)
```

**Router déjà enregistré** dans `api/v1/router.py` — NE PAS re-déclarer le `include_router`.

---

### Project Structure Notes

**Fichiers à créer (NEW) :**
```
android/domain/src/main/kotlin/com/secondserve/domain/
├── model/Session.kt                        ← NEW
└── repository/SessionRepository.kt         ← NEW

android/data/src/main/kotlin/com/secondserve/data/
├── local/db/entity/SessionEntity.kt        ← NEW
├── local/dao/SessionDao.kt                 ← NEW
├── repository/SessionRepositoryImpl.kt     ← NEW
└── di/SessionModule.kt                     ← NEW (pattern ScoreModule.kt)

android/feature/match/src/main/kotlin/com/secondserve/feature/match/
├── NewMatchViewModel.kt                    ← NEW
└── NewMatchScreen.kt                       ← NEW

android/app/src/main/kotlin/com/secondserve/
└── HomeScreen.kt                           ← NEW (minimal)

backend/alembic/versions/d4e5f6a7b8c9_add_sessions_table.py  ← NEW
backend/app/features/sessions/
├── models.py     ← NEW (était vide)
├── schemas.py    ← NEW (était vide)
├── repository.py ← NEW (était vide)
└── service.py    ← NEW (était vide)
```

**Fichiers à modifier (UPDATE) :**
```
android/data/src/main/kotlin/com/secondserve/data/local/db/entity/Mappers.kt
  → ajouter SessionEntity.toDomain() et Session.toEntity()

android/data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt
  → version 3→4, SessionEntity, MIGRATION_3_4

android/app/src/main/kotlin/com/secondserve/di/DataModule.kt
  → provideSessionDao(), MIGRATION_3_4 dans addMigrations()

android/app/src/main/kotlin/com/secondserve/navigation/AppNavGraph.kt
  → startDestination "home", routes "home" et "new_match"

backend/app/api/v1/sessions.py
  → remplacer placeholder par implémentation POST /sessions
```

**Alignement architecture.md :**
- `sessions` table avec `idx_sessions_surface` → NFR-P3 (chargement historique ≤1s) ✅
- Epoch ms pour `created_at` / `updated_at` ✅
- Tables pluriel snake_case ✅
- `fallbackToDestructiveMigration` ABSENT — migrations explicites seulement ✅
- Session créée sans réseau — NFR-OFF1 compliance ✅

**Conflit de nommage à vérifier :** `data/local/dao/` contient déjà `PlayerProfileDao.kt` et `WorkAxisDao.kt`. `SessionDao.kt` s'ajoute au même package `com.secondserve.data.local.dao`. Pas de conflit de nommage.

**`feature/match/build.gradle.kts` — aucun changement requis.** Le module dépend déjà de `:domain` (où vit `SessionRepository`). Hilt wire l'implémentation depuis `:data` via `SessionModule.kt` à l'échelle du `SingletonComponent`.

### References

- [Source: epics.md § Story 2.3] — Acceptance criteria complets, FR-1 (démarrage session)
- [Source: epics.md § ARCH-4] — "Room database schema — tables Session, index idx_sessions_surface. Migrations Room, pas de fallbackToDestructiveMigration"
- [Source: architecture.md § Data Architecture] — Epoch ms, migrations explicites, Room + KSP
- [Source: architecture.md § Naming Patterns] — Tables: snake_case pluriel; colonnes: snake_case; index: `idx_{table}_{colonne}`
- [Source: architecture.md § Process Patterns] — AppResult<T>, Timber, jamais Log.*
- [Source: architecture.md § Project Structure] — SessionRepositoryImpl dans data/repository/, SessionModule dans data/di/
- [Source: architecture.md § Communication Patterns] — Orbit MVI, ContainerHost, UiState data class, SideEffect sealed class
- [Source: 2-2-datalayer-bridge-watch-phone.md § Debug Log] — "AppResult.Error prend un seul Throwable — pas de message String"
- [Source: 2-2-datalayer-bridge-watch-phone.md § Completion Notes DI-1] — "binding co-localisé dans :data (ScoreModule.kt), NE PAS ajouter un second binding dans DataModule.kt"
- [Source: android/data/local/db/SecondServeDatabase.kt] — version actuelle: 3, dernière migration MIGRATION_2_3 (work_axes)
- [Source: android/domain/model/SessionFormat.kt] — MatchFormat, ThirdSetRule, SessionFormat déjà définis
- [Source: android/domain/model/SurfaceConstants.kt] — SurfaceConstants avec DISPLAY_NAMES
- [Source: android/data/di/ScoreModule.kt] — Pattern @Binds @Singleton à réutiliser
- [Source: android/feature/profile/ProfileViewModel.kt] — Pattern Orbit MVI (ContainerHost, intent, reduce, postSideEffect)
- [Source: android/data/local/db/entity/Mappers.kt] — Ajouter les mappers Session/SessionEntity ICI (pas dans SessionEntity.kt)
- [Source: android/app/di/DataModule.kt] — Structure à étendre (provideSessionDao + MIGRATION_3_4)
- [Source: backend/alembic/versions/c3d4e5f6a7b8_add_work_axes_table.py] — Pattern migration Alembic à reproduire
- [Source: backend/app/features/work_axes/service.py] — Pattern service FastAPI à reproduire
- [Source: backend/app/api/v1/sessions.py] — Placeholder existant à remplacer
- [Source: backend/app/api/v1/router.py:19] — `sessions.router` déjà enregistré avec JWT — NE PAS re-déclarer

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6 (Claude Code remote session)

### Debug Log References

### Completion Notes List

### File List
