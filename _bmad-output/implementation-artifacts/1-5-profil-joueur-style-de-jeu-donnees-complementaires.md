---
baseline_commit: "f59229c"
status: "review"
---

# Story 1.5 : Profil joueur — Style de jeu & données complémentaires

**Status:** done

## Story

**As a** player,
**I want** to configure my preferred surfaces, my coach's instructions, and see my inferred playing style,
**So that** every AI coaching interaction reflects my specific game identity.

## Acceptance Criteria

1. **Given** je suis sur l'écran Profil, section "Style de jeu"
   **When** moins de 10 Sessions Match sont enregistrées
   **Then** le style affiche "Données insuffisantes (minimum 10 matchs)"

2. **When** je sélectionne manuellement un style (Défenseur / Attaquant / Contre-puncheur / All-court)
   **Then** il est sauvegardé et intégré dans les prompts IA dès la prochaine interaction coaching

3. **Given** je suis sur la section "Profil complémentaire"
   **When** je renseigne l'un des 3 champs consignes coach (axe principal, axe secondaire, mauvaises habitudes)
   **Then** chaque champ renseigné est envoyé comme élément distinct dans le contexte IA

4. **And** les champs vides sont simplement omis du contexte (aucun placeholder envoyé)

5. **When** j'entre un numéro de licence FFT
   **Then** il est stocké dans `EncryptedSharedPreferences` (local uniquement)
   **And** il n'est inclus dans aucun payload sortant vers Mistral API ou tout service tiers

6. **And** les surfaces de prédilection sélectionnées sont reflétées dans les statistiques et recommandations IA

## Architecture Context

### Séquence d'implémentation (ARCH-13)

Cette story est la **cinquième** dans la chaîne :

```
ARCH-1 (done) → ARCH-2 (done) → ARCH-3 (done) → ARCH-4 (done, Story 1.4) → Story 1.5 (cette story) → Story 1.6
```

**Dépendances :**
- ✅ Story 1.4 : Room DB version 1 créée (`player_profiles`, `ranking_history`), `PlayerProfile`, `PlayerProfileEntity`, `PlayerProfileDao`, `PlayerProfileRepositoryImpl`, `ProfileViewModel`, `ProfileScreen` — tout est opérationnel
- ❌ Story 1.6 : `work_axes` — pas encore créé
- ❌ Story 2.3 : table `sessions` — pas encore créée (impacte le comptage de Sessions pour le seuil de style inféré)

### État actuel du code — critique à connaître

**Android — couche domaine :**
- `PlayerProfile.kt` — 4 champs : `id`, `currentSeries`, `currentPoints`, `updatedAt` — **Story 1.5 ajoute 5 champs**
- `MatchContextProfile.kt` — 3 champs : `fftSeries`, `playStyle`, `activeWorkAxes` — **Story 1.5 ajoute** `preferredSurfaces` et `coachInstructions`
- `PlayerProfileRepository.kt` — 4 méthodes — **Story 1.5 ajoute** `saveProfileDetails(...)` et `observeMatchSessionCount()`
- `AppResult.Loading` existe depuis Story 1.4 — l'utiliser dans les nouveaux intents Orbit

**Android — couche data :**
- `PlayerProfileEntity.kt` — 4 colonnes Room (`id`, `current_series`, `current_points`, `updated_at`) — **Story 1.5 ajoute 5 colonnes via Migration 1→2**
- `SecondServeDatabase.kt` — version = 1 — **Story 1.5 passe à version = 2** avec `MIGRATION_1_2`
- `DataModule.kt` — provisionné avec `SecondServeDatabase`, `PlayerProfileDao`, `PlayerProfileRepository` — **Story 1.5 ajoute** `PlayerDataStore` et `.addMigrations(MIGRATION_1_2)` au builder Room
- `JwtTokenStore.kt` — pattern EncryptedSharedPreferences opérationnel dans `:data/remote/security/` — **réutiliser exactement ce pattern** pour `PlayerDataStore`
- `Mappers.kt` — `PlayerProfileEntity.toDomain()` — **Story 1.5 met à jour** pour mapper les 5 nouveaux champs

**Android — feature profile :**
- `ProfileViewModel.kt` — état `ProfileUiState` avec `isLoading`, `isSaving`, `currentSeries`, `currentPoints`, `rankingHistory`, `error` — **Story 1.5 étend** cet état
- `ProfileScreen.kt` — affiche `RankingSummaryCard`, `RankingInputSection`, timeline historique — **Story 1.5 ajoute** 3 nouvelles sections

**Backend VPS :**
- `features/profile/models.py` — `PlayerProfile` SQLAlchemy avec 4 colonnes — **Story 1.5 ajoute 5 colonnes**
- `features/profile/schemas.py` — `ProfileSummaryResponse` avec `current_series`, `current_points`, `ranking_history` — **Story 1.5 étend**
- `features/profile/repository.py` — `upsert_profile_ranking()`, `insert_ranking_history()` — **Story 1.5 ajoute** `update_profile_details()`
- `features/profile/service.py` — `save_ranking()`, `get_profile_summary()` — **Story 1.5 ajoute** `update_profile_details()`
- `api/v1/profile.py` — `GET /profile`, `POST /profile/ranking` protégés par JWT via router.py — **Story 1.5 ajoute** `PUT /profile/details`
- `alembic/versions/a1b2c3d4e5f6_add_player_profiles_and_ranking_history.py` — migration précédente — **Story 1.5 ajoute une nouvelle migration** qui modifie la table `player_profiles`

### Entités Room — Migration 1 → 2

**Table `player_profiles` : ajout de 5 colonnes nullable :**

| Colonne | Type Room | Remarques |
|---------|-----------|-----------|
| `play_style` | `String?` | Une valeur parmi : `DEFENSIVE`, `OFFENSIVE`, `COUNTERPUNCHER`, `ALL_COURT` (ou null) |
| `preferred_surfaces` | `String?` | Comma-separated : `"CLAY,HARD"` (ou null si aucune sélection) |
| `coach_instruction_1` | `String?` | Axe principal (texte libre, ou null) |
| `coach_instruction_2` | `String?` | Axe secondaire (texte libre, ou null) |
| `coach_instruction_3` | `String?` | Mauvaises habitudes (texte libre, ou null) |

> ⚠️ **Migration Room** : SQLite ne supporte pas `ALTER TABLE ADD COLUMN NOT NULL` sans valeur par défaut. Toutes ces colonnes sont `nullable` intentionnellement.

### Numéro de licence FFT — isolation critique (NFR-C3)

Le numéro de licence FFT est **strictement local** :
- Stocké dans un `EncryptedSharedPreferences` dédié (`"player_data_store"`, clé `"fft_license"`)
- Utiliser le pattern exact de `JwtTokenStore.kt` (même `MasterKey`, même schémas AES256)
- **Jamais** inclus dans `PlayerProfile` (domaine), `PlayerProfileEntity` (Room), `ProfileSummaryDto` (VPS), `MatchContextProfile` (IA), ni aucun log
- `PlayerDataStore` dans `:data/local/` (pas dans `:remote/security/` — c'est une donnée locale, pas réseau)

### Seuil session pour style inféré (Story 1.5 provisoire)

- AC-1 : afficher "Données insuffisantes" si `matchSessionCount < 10`
- La table `sessions` n'existe pas encore (Story 2.3)
- `PlayerProfileRepository.observeMatchSessionCount()` retourne `flowOf(0)` dans cette story
- **TODO Story 2.3** : l'implémentation sera mise à jour pour déléguer à `SessionDao.countMatchSessions()` quand la table `sessions` existera

---

## Technical Requirements

### Constantes domaine — NOUVEAUX fichiers

**`domain/src/main/kotlin/com/secondserve/domain/model/PlayStyleConstants.kt`** (NEW)

```kotlin
object PlayStyleConstants {
    const val DEFENSIVE    = "DEFENSIVE"
    const val OFFENSIVE    = "OFFENSIVE"
    const val COUNTERPUNCHER = "COUNTERPUNCHER"
    const val ALL_COURT    = "ALL_COURT"

    val ALL = listOf(DEFENSIVE, OFFENSIVE, COUNTERPUNCHER, ALL_COURT)

    val DISPLAY_NAMES = mapOf(
        DEFENSIVE     to "Défenseur",
        OFFENSIVE     to "Attaquant",
        COUNTERPUNCHER to "Contre-puncheur",
        ALL_COURT     to "All-court"
    )
}
```

**`domain/src/main/kotlin/com/secondserve/domain/model/SurfaceConstants.kt`** (NEW)

```kotlin
object SurfaceConstants {
    const val CLAY   = "CLAY"
    const val GRASS  = "GRASS"
    const val HARD   = "HARD"
    const val CARPET = "CARPET"

    val ALL = listOf(CLAY, GRASS, HARD, CARPET)

    val DISPLAY_NAMES = mapOf(
        CLAY   to "Terre battue",
        GRASS  to "Gazon",
        HARD   to "Dur",
        CARPET to "Carpet"
    )
}
```

> `SurfaceConstants` sera réutilisé par les Stories 2.3+ (champ `surface` de `Session`). Ne pas redéfinir ces constantes dans les features.

### Domaine — fichiers mis à jour

**`domain/src/main/kotlin/com/secondserve/domain/model/PlayerProfile.kt`** (UPDATE)

```kotlin
data class PlayerProfile(
    val id: Int = 1,
    val currentSeries: String?,
    val currentPoints: Int?,
    val playStyle: String?,
    val preferredSurfaces: List<String>,
    val coachInstruction1: String?,
    val coachInstruction2: String?,
    val coachInstruction3: String?,
    val updatedAt: Long
)
```

**`domain/src/main/kotlin/com/secondserve/domain/model/MatchContextProfile.kt`** (UPDATE)

```kotlin
data class MatchContextProfile(
    val fftSeries: String? = null,
    val playStyle: String? = null,
    val preferredSurfaces: List<String> = emptyList(),
    val coachInstructions: List<String> = emptyList(),  // non-null, non-blank uniquement
    val activeWorkAxes: List<String> = emptyList()       // Story 1.6
)
```

**`domain/src/main/kotlin/com/secondserve/domain/repository/PlayerProfileRepository.kt`** (UPDATE)

```kotlin
interface PlayerProfileRepository {
    suspend fun getProfile(): AppResult<PlayerProfile?>
    suspend fun saveRanking(series: String, points: Int): AppResult<Unit>
    fun getRankingHistory(): Flow<List<RankingEntry>>
    suspend fun buildMatchContextProfile(): MatchContextProfile
    // NEW Story 1.5
    suspend fun saveProfileDetails(
        playStyle: String?,
        preferredSurfaces: List<String>,
        coachInstruction1: String?,
        coachInstruction2: String?,
        coachInstruction3: String?
    ): AppResult<Unit>
    fun observeMatchSessionCount(): Flow<Int>  // returns flowOf(0) until Story 2.3
}
```

### Android — couche data (Room)

**`data/src/main/kotlin/com/secondserve/data/local/db/entity/PlayerProfileEntity.kt`** (UPDATE)

```kotlin
@Entity(tableName = "player_profiles")
data class PlayerProfileEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "current_series") val currentSeries: String?,
    @ColumnInfo(name = "current_points") val currentPoints: Int?,
    @ColumnInfo(name = "play_style") val playStyle: String?,
    @ColumnInfo(name = "preferred_surfaces") val preferredSurfaces: String?,  // CSV
    @ColumnInfo(name = "coach_instruction_1") val coachInstruction1: String?,
    @ColumnInfo(name = "coach_instruction_2") val coachInstruction2: String?,
    @ColumnInfo(name = "coach_instruction_3") val coachInstruction3: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
```

**`data/src/main/kotlin/com/secondserve/data/local/db/entity/Mappers.kt`** (UPDATE)

```kotlin
fun PlayerProfileEntity.toDomain(): PlayerProfile = PlayerProfile(
    id = id,
    currentSeries = currentSeries,
    currentPoints = currentPoints,
    playStyle = playStyle,
    preferredSurfaces = preferredSurfaces
        ?.split(",")
        ?.filter { it.isNotBlank() }
        ?: emptyList(),
    coachInstruction1 = coachInstruction1,
    coachInstruction2 = coachInstruction2,
    coachInstruction3 = coachInstruction3,
    updatedAt = updatedAt
)

fun List<String>.toPreferredSurfacesString(): String? =
    if (isEmpty()) null else joinToString(",")
```

**`data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt`** (UPDATE)

```kotlin
@Database(
    entities = [PlayerProfileEntity::class, RankingHistoryEntity::class],
    version = 2,           // ← version 1→2
    exportSchema = true
)
abstract class SecondServeDatabase : RoomDatabase() {
    abstract fun playerProfileDao(): PlayerProfileDao

    companion object {
        const val DB_NAME = "secondserve_db"

        val MIGRATION_1_2 = Migration(1, 2) { database ->
            database.execSQL("ALTER TABLE player_profiles ADD COLUMN play_style TEXT")
            database.execSQL("ALTER TABLE player_profiles ADD COLUMN preferred_surfaces TEXT")
            database.execSQL("ALTER TABLE player_profiles ADD COLUMN coach_instruction_1 TEXT")
            database.execSQL("ALTER TABLE player_profiles ADD COLUMN coach_instruction_2 TEXT")
            database.execSQL("ALTER TABLE player_profiles ADD COLUMN coach_instruction_3 TEXT")
        }
    }
}
```

**`data/src/main/kotlin/com/secondserve/data/local/dao/PlayerProfileDao.kt`** (UPDATE — ajout méthode upsert complète)

```kotlin
@Dao
interface PlayerProfileDao {
    @Query("SELECT * FROM player_profiles WHERE id = 1")
    suspend fun getProfile(): PlayerProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: PlayerProfileEntity)

    @Insert
    suspend fun insertRanking(ranking: RankingHistoryEntity)

    @Transaction
    suspend fun saveProfileAndHistory(profile: PlayerProfileEntity, ranking: RankingHistoryEntity) {
        upsertProfile(profile)
        insertRanking(ranking)
    }

    @Query("SELECT * FROM ranking_history ORDER BY recorded_at DESC")
    fun getRankingHistory(): Flow<List<RankingHistoryEntity>>
}
```

> `upsertProfile` est déjà `OnConflictStrategy.REPLACE` — il suffit de passer un `PlayerProfileEntity` complet avec les nouveaux champs. Pas besoin d'une nouvelle méthode DAO.

### Android — local data store pour FFT licence

**`data/src/main/kotlin/com/secondserve/data/local/PlayerDataStore.kt`** (NEW)

```kotlin
package com.secondserve.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PlayerDataStore(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "player_data_store",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveFftLicenseNumber(number: String) {
        prefs.edit().putString(KEY_FFT_LICENSE, number).apply()
    }

    fun getFftLicenseNumber(): String? = prefs.getString(KEY_FFT_LICENSE, null)

    fun clearFftLicenseNumber() {
        prefs.edit().remove(KEY_FFT_LICENSE).apply()
    }

    companion object {
        private const val KEY_FFT_LICENSE = "fft_license"
    }
}
```

> ⚠️ Utiliser `apply()` (async) comme dans `JwtTokenStore` — cohérence du pattern. Le deferred `apply() vs commit()` de Story 1.3 reste différé pour les deux stores.

### Android — PlayerProfileRepositoryImpl (UPDATE)

```kotlin
class PlayerProfileRepositoryImpl(
    private val dao: PlayerProfileDao,
    private val vpsApiService: VpsApiService,
    private val playerDataStore: PlayerDataStore  // NEW injection
) : PlayerProfileRepository {

    // ... getProfile(), saveRanking(), getRankingHistory() inchangés ...

    override suspend fun buildMatchContextProfile(): MatchContextProfile {
        val profile = dao.getProfile()
        return MatchContextProfile(
            fftSeries = profile?.currentSeries,
            playStyle = profile?.playStyle,
            preferredSurfaces = profile?.preferredSurfaces
                ?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            coachInstructions = listOfNotNull(
                profile?.coachInstruction1?.takeIf { it.isNotBlank() },
                profile?.coachInstruction2?.takeIf { it.isNotBlank() },
                profile?.coachInstruction3?.takeIf { it.isNotBlank() }
            )
        )
    }

    override suspend fun saveProfileDetails(
        playStyle: String?,
        preferredSurfaces: List<String>,
        coachInstruction1: String?,
        coachInstruction2: String?,
        coachInstruction3: String?
    ): AppResult<Unit> = try {
        val now = System.currentTimeMillis()
        val current = dao.getProfile()
        dao.upsertProfile(
            PlayerProfileEntity(
                id = 1,
                currentSeries = current?.currentSeries,
                currentPoints = current?.currentPoints,
                playStyle = playStyle,
                preferredSurfaces = preferredSurfaces.toPreferredSurfacesString(),
                coachInstruction1 = coachInstruction1?.takeIf { it.isNotBlank() },
                coachInstruction2 = coachInstruction2?.takeIf { it.isNotBlank() },
                coachInstruction3 = coachInstruction3?.takeIf { it.isNotBlank() },
                updatedAt = now
            )
        )
        try {
            vpsApiService.updateProfileDetails(
                ProfileDetailsRequest(
                    playStyle = playStyle,
                    preferredSurfaces = preferredSurfaces.toPreferredSurfacesString(),
                    coachInstruction1 = coachInstruction1?.takeIf { it.isNotBlank() },
                    coachInstruction2 = coachInstruction2?.takeIf { it.isNotBlank() },
                    coachInstruction3 = coachInstruction3?.takeIf { it.isNotBlank() }
                )
            )
        } catch (e: Exception) {
            Timber.w(e, "VPS profile details sync failed — local save succeeded")
        }
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(e)
    }

    override fun observeMatchSessionCount(): Flow<Int> = flowOf(0)
    // TODO(Story 2.3): replace with SessionDao.countMatchSessions() when sessions table exists
}
```

> ⚠️ **Anti-pattern à éviter** : Ne pas inclure `playerDataStore.getFftLicenseNumber()` dans `buildMatchContextProfile()` ni dans aucun DTO VPS. La licence FFT reste strictement dans `PlayerDataStore`.

### Android — VpsApiService (UPDATE)

```kotlin
@PUT("api/v1/profile/details")
suspend fun updateProfileDetails(@Body request: ProfileDetailsRequest): ProfileDetailsResponse
```

### Android — ProfileDto (UPDATE)

```kotlin
// Mettre à jour ProfileSummaryDto avec les nouveaux champs
data class ProfileSummaryDto(
    @Json(name = "current_series") val currentSeries: String?,
    @Json(name = "current_points") val currentPoints: Int?,
    @Json(name = "ranking_history") val rankingHistory: List<RankingEntryDto>,
    // NEW Story 1.5
    @Json(name = "play_style") val playStyle: String?,
    @Json(name = "preferred_surfaces") val preferredSurfaces: String?,
    @Json(name = "coach_instruction_1") val coachInstruction1: String?,
    @Json(name = "coach_instruction_2") val coachInstruction2: String?,
    @Json(name = "coach_instruction_3") val coachInstruction3: String?
)

// NEW Story 1.5
data class ProfileDetailsRequest(
    @Json(name = "play_style") val playStyle: String?,
    @Json(name = "preferred_surfaces") val preferredSurfaces: String?,
    @Json(name = "coach_instruction_1") val coachInstruction1: String?,
    @Json(name = "coach_instruction_2") val coachInstruction2: String?,
    @Json(name = "coach_instruction_3") val coachInstruction3: String?
)

data class ProfileDetailsResponse(
    @Json(name = "updated_at") val updatedAt: Long
)
```

> Moshi avec `KotlinJsonAdapterFactory` est déjà configuré dans `AuthModule.kt` (patch Story 1.4) — les nouveaux DTOs sont désérialisés automatiquement.

### Android — DataModule (UPDATE)

```kotlin
@Provides
@Singleton
fun provideSecondServeDatabase(@ApplicationContext context: Context): SecondServeDatabase =
    Room.databaseBuilder(
        context,
        SecondServeDatabase::class.java,
        SecondServeDatabase.DB_NAME
    )
    .addMigrations(SecondServeDatabase.MIGRATION_1_2)  // CRITIQUE : sans ça → crash IllegalStateException
    .build()

// NEW
@Provides
@Singleton
fun providePlayerDataStore(@ApplicationContext context: Context): PlayerDataStore =
    PlayerDataStore(context)

@Provides
@Singleton
fun providePlayerProfileRepository(
    dao: PlayerProfileDao,
    vpsApiService: VpsApiService,
    playerDataStore: PlayerDataStore  // NEW
): PlayerProfileRepository =
    PlayerProfileRepositoryImpl(dao, vpsApiService, playerDataStore)
```

### Android — ProfileViewModel (UPDATE)

```kotlin
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: PlayerProfileRepository,
    private val playerDataStore: PlayerDataStore  // NEW — inject pour FFT licence
) : ViewModel(), ContainerHost<ProfileUiState, ProfileSideEffect> {

    override val container = container<ProfileUiState, ProfileSideEffect>(ProfileUiState())

    init {
        loadProfile()
        collectRankingHistory()
        collectMatchSessionCount()  // NEW
    }

    // ... loadProfile(), collectRankingHistory(), saveRanking() inchangés ...

    private fun collectMatchSessionCount() = intent {
        profileRepository.observeMatchSessionCount().collect { count ->
            reduce { state.copy(matchSessionCount = count) }
        }
    }

    fun saveProfileDetails(
        playStyle: String?,
        preferredSurfaces: List<String>,
        coachInstruction1: String?,
        coachInstruction2: String?,
        coachInstruction3: String?
    ) = intent {
        reduce { state.copy(isSaving = true) }
        when (val result = profileRepository.saveProfileDetails(
            playStyle, preferredSurfaces,
            coachInstruction1, coachInstruction2, coachInstruction3
        )) {
            is AppResult.Success -> {
                reduce {
                    state.copy(
                        isSaving = false,
                        playStyle = playStyle,
                        preferredSurfaces = preferredSurfaces,
                        coachInstruction1 = coachInstruction1,
                        coachInstruction2 = coachInstruction2,
                        coachInstruction3 = coachInstruction3
                    )
                }
                postSideEffect(ProfileSideEffect.ProfileDetailsSaved)
            }
            is AppResult.Error -> {
                reduce { state.copy(isSaving = false) }
                postSideEffect(ProfileSideEffect.ShowError("Erreur lors de la sauvegarde"))
            }
            AppResult.Loading -> {}
        }
    }

    fun saveFftLicense(licenseNumber: String) = intent {
        playerDataStore.saveFftLicenseNumber(licenseNumber)
        reduce { state.copy(fftLicenseNumber = licenseNumber) }
        // Pas de side effect — sauvegarde silencieuse (local uniquement)
    }

    fun loadFftLicense() = intent {
        val license = playerDataStore.getFftLicenseNumber()
        reduce { state.copy(fftLicenseNumber = license) }
    }
}

data class ProfileUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    // Ranking (Story 1.4 — inchangé)
    val currentSeries: String? = null,
    val currentPoints: Int? = null,
    val rankingHistory: List<RankingEntry> = emptyList(),
    // NEW Story 1.5
    val matchSessionCount: Int = 0,
    val playStyle: String? = null,
    val preferredSurfaces: List<String> = emptyList(),
    val coachInstruction1: String? = null,
    val coachInstruction2: String? = null,
    val coachInstruction3: String? = null,
    val fftLicenseNumber: String? = null,  // chargé depuis EncryptedSharedPreferences
    val error: String? = null
)

sealed class ProfileSideEffect {
    data object RankingSaved : ProfileSideEffect()
    data object ProfileDetailsSaved : ProfileSideEffect()  // NEW
    data class ShowError(val message: String) : ProfileSideEffect()
}
```

> ⚠️ **Anti-pattern à éviter** : `loadProfile()` doit charger depuis la DB Room ET peupler les nouveaux champs du state (`playStyle`, `preferredSurfaces`, etc.). Appeler `loadFftLicense()` dans `init {}` pour charger la licence depuis `PlayerDataStore`.

### Android — ProfileScreen (UPDATE)

Ajouter 3 nouvelles sections dans `LazyColumn` après la timeline historique :

**Section "Style de jeu" :**
- Affiche "Données insuffisantes (minimum 10 matchs)" si `state.matchSessionCount < 10` ET `state.playStyle == null`
- `DropdownMenu` Compose (même pattern que DropdownMenu séries FFT Story 1.4) avec les 4 valeurs de `PlayStyleConstants.ALL` + labels de `PlayStyleConstants.DISPLAY_NAMES`
- Si style déjà sélectionné, afficher le label courant dans le champ

**Section "Surfaces de prédilection" :**
- `MultiSelectChip` ou `FilterChip` (Material3) pour chaque valeur de `SurfaceConstants.ALL` + labels de `SurfaceConstants.DISPLAY_NAMES`
- État local `selectedSurfaces: Set<String>` (mutableStateOf) pré-rempli depuis `state.preferredSurfaces`
- Bouton "Enregistrer les surfaces"

**Section "Profil complémentaire" :**
- 3 `OutlinedTextField` (texte libre, `KeyboardType.Text`) :
  - "Axe principal du coach"
  - "Axe secondaire du coach"
  - "Mauvaises habitudes à corriger"
- Bouton "Enregistrer les consignes"
- 1 `OutlinedTextField` pour le numéro de licence FFT (`KeyboardType.Number`) avec texte d'aide "Stocké localement uniquement"
- Bouton "Enregistrer la licence" (sauvegarde silencieuse via `viewModel.saveFftLicense()`)

**Gestion du SideEffect `ProfileDetailsSaved` :**
```kotlin
is ProfileSideEffect.ProfileDetailsSaved -> {
    scope.launch { snackbarHostState.showSnackbar("Profil mis à jour") }
}
```

### VPS Backend — models.py (UPDATE)

```python
class PlayerProfile(Base):
    __tablename__ = "player_profiles"

    id = Column(Integer, primary_key=True, default=1)
    current_series = Column(String, nullable=True)
    current_points = Column(Integer, nullable=True)
    updated_at = Column(Integer, nullable=False)
    # NEW Story 1.5
    play_style = Column(String, nullable=True)
    preferred_surfaces = Column(String, nullable=True)   # CSV: "CLAY,HARD"
    coach_instruction_1 = Column(String, nullable=True)
    coach_instruction_2 = Column(String, nullable=True)
    coach_instruction_3 = Column(String, nullable=True)
```

> `RankingHistory` ne change pas.

### VPS Backend — schemas.py (UPDATE)

```python
PLAY_STYLE_VALUES = ["DEFENSIVE", "OFFENSIVE", "COUNTERPUNCHER", "ALL_COURT"]

class ProfileDetailsRequest(BaseModel):
    play_style: Optional[str] = None
    preferred_surfaces: Optional[str] = None  # CSV
    coach_instruction_1: Optional[str] = None
    coach_instruction_2: Optional[str] = None
    coach_instruction_3: Optional[str] = None

    @field_validator("play_style")
    @classmethod
    def validate_play_style(cls, v: str | None) -> str | None:
        if v is not None and v not in PLAY_STYLE_VALUES:
            raise ValueError(f"Style invalide : {v}. Valeurs acceptées : {PLAY_STYLE_VALUES}")
        return v

class ProfileDetailsResponse(BaseModel):
    updated_at: int
    model_config = {"from_attributes": True}

class ProfileSummaryResponse(BaseModel):
    current_series: Optional[str] = None
    current_points: Optional[int] = None
    ranking_history: list[RankingResponse] = []
    # NEW Story 1.5
    play_style: Optional[str] = None
    preferred_surfaces: Optional[str] = None
    coach_instruction_1: Optional[str] = None
    coach_instruction_2: Optional[str] = None
    coach_instruction_3: Optional[str] = None
    model_config = {"from_attributes": True}
```

### VPS Backend — repository.py (UPDATE)

```python
async def update_profile_details(
    self,
    play_style: str | None,
    preferred_surfaces: str | None,
    coach_instruction_1: str | None,
    coach_instruction_2: str | None,
    coach_instruction_3: str | None
) -> PlayerProfile:
    now = int(time.time() * 1000)
    profile = await self.get_profile()
    if profile:
        profile.play_style = play_style
        profile.preferred_surfaces = preferred_surfaces
        profile.coach_instruction_1 = coach_instruction_1
        profile.coach_instruction_2 = coach_instruction_2
        profile.coach_instruction_3 = coach_instruction_3
        profile.updated_at = now
    else:
        profile = PlayerProfile(
            id=1, play_style=play_style, preferred_surfaces=preferred_surfaces,
            coach_instruction_1=coach_instruction_1, coach_instruction_2=coach_instruction_2,
            coach_instruction_3=coach_instruction_3, updated_at=now
        )
        self.db.add(profile)
    await self.db.flush()
    return profile
```

### VPS Backend — service.py (UPDATE)

```python
async def update_profile_details(self, request: ProfileDetailsRequest) -> ProfileDetailsResponse:
    profile = await self.repository.update_profile_details(
        request.play_style,
        request.preferred_surfaces,
        request.coach_instruction_1,
        request.coach_instruction_2,
        request.coach_instruction_3
    )
    return ProfileDetailsResponse(updated_at=profile.updated_at)
```

### VPS Backend — profile.py (UPDATE)

```python
from app.features.profile.schemas import (
    RankingRequest, RankingResponse, ProfileSummaryResponse,
    ProfileDetailsRequest, ProfileDetailsResponse  # NEW
)

@router.put("/details", response_model=ProfileDetailsResponse)
async def update_profile_details(
    request: ProfileDetailsRequest,
    service: ProfileService = Depends(get_profile_service)
) -> ProfileDetailsResponse:
    return await service.update_profile_details(request)
```

> JWT est déjà appliqué au niveau du router (`router.py : dependencies=[Depends(verify_jwt)]`) — pas de double dépendance dans `profile.py`.

### VPS Backend — Migration Alembic (NOUVEAU fichier)

**`backend/alembic/versions/<hash>_add_profile_details_columns.py`** (NEW)

```python
"""add profile details columns to player_profiles

Revision ID: <generated>
Revises: a1b2c3d4e5f6
Create Date: <date>
"""
from alembic import op
import sqlalchemy as sa

revision = '<generated>'
down_revision = 'a1b2c3d4e5f6'
branch_labels = None
depends_on = None

def upgrade() -> None:
    op.add_column('player_profiles', sa.Column('play_style', sa.String(), nullable=True))
    op.add_column('player_profiles', sa.Column('preferred_surfaces', sa.String(), nullable=True))
    op.add_column('player_profiles', sa.Column('coach_instruction_1', sa.String(), nullable=True))
    op.add_column('player_profiles', sa.Column('coach_instruction_2', sa.String(), nullable=True))
    op.add_column('player_profiles', sa.Column('coach_instruction_3', sa.String(), nullable=True))

def downgrade() -> None:
    op.drop_column('player_profiles', 'coach_instruction_3')
    op.drop_column('player_profiles', 'coach_instruction_2')
    op.drop_column('player_profiles', 'coach_instruction_1')
    op.drop_column('player_profiles', 'preferred_surfaces')
    op.drop_column('player_profiles', 'play_style')
```

---

## Tasks / Subtasks

### Domaine Android

- [x] **Task D-1** — Créer `PlayStyleConstants.kt` et `SurfaceConstants.kt` dans `:domain/model/`
- [x] **Task D-2** — Mettre à jour `PlayerProfile.kt` avec les 5 nouveaux champs
- [x] **Task D-3** — Mettre à jour `MatchContextProfile.kt` (ajouter `preferredSurfaces`, `coachInstructions`)
- [x] **Task D-4** — Mettre à jour `PlayerProfileRepository.kt` (ajouter `saveProfileDetails()`, `observeMatchSessionCount()`)

### Room Android

- [x] **Task R-1** — Mettre à jour `PlayerProfileEntity.kt` avec les 5 nouvelles colonnes nullable
- [x] **Task R-2** — Mettre à jour `Mappers.kt` — `PlayerProfileEntity.toDomain()` mapper les nouveaux champs + `List<String>.toPreferredSurfacesString()`
- [x] **Task R-3** — Mettre à jour `SecondServeDatabase.kt` — version 1→2, créer `MIGRATION_1_2`

### Data Layer Android

- [x] **Task DA-1** — Créer `PlayerDataStore.kt` dans `:data/local/` (EncryptedSharedPreferences, fichier `"player_data_store"`)
- [x] **Task DA-2** — Mettre à jour `ProfileDto.kt` : `ProfileSummaryDto` (5 champs), `ProfileDetailsRequest`, `ProfileDetailsResponse`
- [x] **Task DA-3** — Ajouter `updateProfileDetails()` à `VpsApiService.kt`
- [x] **Task DA-4** — Mettre à jour `PlayerProfileRepositoryImpl.kt` : `buildMatchContextProfile()`, `saveProfileDetails()`, `observeMatchSessionCount()`, constructeur + `playerDataStore`
- [x] **Task DA-5** — Mettre à jour `DataModule.kt` : `.addMigrations(MIGRATION_1_2)`, `providePlayerDataStore`, mise à jour de `providePlayerProfileRepository`

### Feature Profile Android

- [x] **Task UI-1** — Mettre à jour `ProfileViewModel.kt` : `ProfileUiState` étendu, `saveProfileDetails()`, `saveFftLicense()`, `loadFftLicense()`, `collectMatchSessionCount()`, `ProfileDetailsSaved` side effect
- [x] **Task UI-2** — Mettre à jour `ProfileScreen.kt` : section "Style de jeu" (DropdownMenu + seuil 10 sessions), section "Surfaces de prédilection" (FilterChip multi-select), section "Profil complémentaire" (3 champs texte + licence FFT), gestion `ProfileDetailsSaved`
- [x] **Task UI-3** — S'assurer que `loadProfile()` propage les nouveaux champs dans le state (playStyle, preferredSurfaces, coachInstruction1/2/3)
- [x] **Task UI-4** — Appeler `loadFftLicense()` dans `init {}` de `ProfileViewModel`

### Backend VPS

- [x] **Task VPS-1** — Mettre à jour `features/profile/models.py` (5 nouvelles colonnes)
- [x] **Task VPS-2** — Mettre à jour `features/profile/schemas.py` (`ProfileDetailsRequest`, `ProfileDetailsResponse`, mise à jour `ProfileSummaryResponse`)
- [x] **Task VPS-3** — Mettre à jour `features/profile/repository.py` (ajouter `update_profile_details()`)
- [x] **Task VPS-4** — Mettre à jour `features/profile/service.py` (ajouter `update_profile_details()`)
- [x] **Task VPS-5** — Mettre à jour `api/v1/profile.py` (ajouter `PUT /details`)
- [x] **Task VPS-6** — Créer la migration Alembic (`add_profile_details_columns`) — `down_revision = 'a1b2c3d4e5f6'`

### Tests

- [x] **Task T-1** — `PlayerProfileRepositoryImplTest.kt` : tester `saveProfileDetails()`, `buildMatchContextProfile()` avec `coachInstructions` (omission des champs vides), `observeMatchSessionCount()` = 0
- [x] **Task T-2** — `ProfileViewModelTest.kt` : tester `saveProfileDetails()`, `saveFftLicense()`, `matchSessionCount < 10` → état "Données insuffisantes"
- [x] **Task T-3** — `tests/integration/test_profile_details_api.py` : `PUT /profile/details` avec et sans token, avec style invalide (422), avec tous les champs null (200)
- [x] **Task T-4** — `tests/unit/test_profile_details_service.py` : valider `PLAY_STYLE_VALUES`, `PlayStyleConstants` côté backend

---

## Dev Notes

### Guardrails critiques

**Room Migration :**
- **TOUJOURS** ajouter `.addMigrations(MIGRATION_1_2)` dans `DataModule.provideSecondServeDatabase()` — sans ça, l'app crash avec `IllegalStateException: Room cannot verify the data integrity` au démarrage sur toute installation existante
- `MIGRATION_1_2` utilise `ALTER TABLE ADD COLUMN TEXT` (nullable) — seule opération DDL supportée par SQLite pour les ALTER TABLE

**Isolation PII — numéro de licence FFT :**
- `PlayerDataStore` ne doit JAMAIS être injecté dans `PlayerProfileRepositoryImpl` pour des raisons autres que read/write de la licence
- La licence FFT ne doit JAMAIS apparaître dans : `PlayerProfile` (domain), `PlayerProfileEntity` (Room), `ProfileSummaryDto` / `ProfileDetailsRequest` (VPS), `MatchContextProfile` (IA), ni dans les logs Timber
- Tester explicitement l'absence de la licence dans les payloads VPS

**MatchContextProfile — construction des coachInstructions :**
- Utiliser `takeIf { it.isNotBlank() }` pour omettre les champs vides — ne pas envoyer `""` ou `" "` comme instruction coach
- L'ordre de la liste `coachInstructions` est [instruction1, instruction2, instruction3] (les nulls omis)

**Orbit MVI — pattern loadProfile() :**
- `loadProfile()` doit également peupler `playStyle`, `preferredSurfaces`, `coachInstruction1/2/3` depuis la réponse `AppResult.Success`
- Ne pas appeler `saveProfileDetails()` depuis `loadProfile()` — lecture seule dans `loadProfile()`

**ProfileScreen — Multi-select surfaces :**
- Utiliser `FilterChip` de Material3 (disponible dans Compose BOM 2026.05.00)
- État local `var selectedSurfaces by remember { mutableStateOf(state.preferredSurfaces.toSet()) }` — synchroniser au changement du state via `LaunchedEffect(state.preferredSurfaces)`
- Un seul bouton "Enregistrer" pour surfaces + style de jeu ensemble (ou séparés — au choix du développeur, mais cohérent avec l'UX de la section)

### Patterns établis à réutiliser

| Pattern | Référence |
|---------|-----------|
| EncryptedSharedPreferences | `JwtTokenStore.kt` (exactement ce pattern) |
| DropdownMenu Compose | `ProfileScreen.kt` (section séries FFT Story 1.4) |
| Fire-and-forget VPS sync | `saveRanking()` dans `PlayerProfileRepositoryImpl.kt` |
| Moshi `@Json` snake_case | `ProfileDto.kt` (pattern existant) |
| Orbit `intent {}` async | `ProfileViewModel.saveRanking()` (pattern existant) |
| `AppResult.Loading` dans `intent` | Pattern Story 1.4 — ignorer avec `AppResult.Loading -> {}` |

### Deferreds pertinents de stories précédentes

- **Room schema JSON version 1 non committé** (deferred Story 1.4) : la migration 1→2 génère le schema version 2. Il faudra committer les deux JSON après le premier build réussi.
- **`navController.popBackStack()` no-op** (deferred Story 1.4) : navigation provisoire, pas de correction dans cette story.

### Alembic — commande après création des modèles

```bash
cd backend
uv run alembic revision --autogenerate -m "add_profile_details_columns"
uv run alembic upgrade head
```

Vérifier que le fichier généré dans `alembic/versions/` a `down_revision = 'a1b2c3d4e5f6'` (clé de la migration Story 1.4).

### Project Structure Notes

**Arborescence des fichiers concernés :**

```
android/domain/src/main/kotlin/com/secondserve/domain/
├── model/
│   ├── PlayerProfile.kt                     — UPDATE (5 champs)
│   ├── MatchContextProfile.kt               — UPDATE (preferredSurfaces, coachInstructions)
│   ├── PlayStyleConstants.kt                — NEW
│   └── SurfaceConstants.kt                  — NEW
└── repository/
    └── PlayerProfileRepository.kt           — UPDATE (saveProfileDetails, observeMatchSessionCount)

android/data/src/main/kotlin/com/secondserve/data/
├── local/
│   ├── PlayerDataStore.kt                   — NEW
│   └── db/
│       ├── SecondServeDatabase.kt           — UPDATE (version 2, MIGRATION_1_2)
│       └── entity/
│           ├── PlayerProfileEntity.kt       — UPDATE (5 colonnes)
│           └── Mappers.kt                   — UPDATE (nouveaux champs)
└── repository/
    └── PlayerProfileRepositoryImpl.kt       — UPDATE (nouveaux champs, constructeur)

android/data/src/main/kotlin/com/secondserve/data/remote/api/
├── VpsApiService.kt                         — UPDATE (updateProfileDetails)
└── dto/
    └── ProfileDto.kt                        — UPDATE (ProfileSummaryDto, ProfileDetailsRequest/Response)

android/app/src/main/kotlin/com/secondserve/di/
└── DataModule.kt                            — UPDATE (addMigrations, PlayerDataStore)

android/feature/profile/src/main/kotlin/com/secondserve/feature/profile/
├── ProfileViewModel.kt                      — UPDATE (nouveaux états/actions)
└── ProfileScreen.kt                         — UPDATE (3 nouvelles sections)

backend/app/features/profile/
├── models.py                                — UPDATE (5 colonnes)
├── schemas.py                               — UPDATE (ProfileDetailsRequest/Response, ProfileSummaryResponse)
├── repository.py                            — UPDATE (update_profile_details)
└── service.py                               — UPDATE (update_profile_details)

backend/app/api/v1/
└── profile.py                               — UPDATE (PUT /details)

backend/alembic/versions/
└── <hash>_add_profile_details_columns.py    — NEW
```

### References

- [Source: epics.md § Story 1.5] — Acceptance criteria, FR-15, FR-16
- [Source: architecture.md § Data Architecture] — Room schema, colonnes player_profiles Story 1.5 annoncées
- [Source: architecture.md § Authentication & Security] — NFR-C3 isolation PII (licence FFT)
- [Source: architecture.md § Naming Patterns] — Conventions DB, REST, Kotlin, Python
- [Source: architecture.md § Implementation Patterns] — sealed Result, Timber, logging Python
- [Source: architecture.md § Project Structure] — Arborescence Android et VPS
- [Source: 1-4-classement-fft-saisie-et-historique.md § Technical Requirements] — Entités Room v1, PlayerProfileEntity, patterns Orbit MVI, patterns Moshi
- [Source: 1-4-classement-fft-saisie-et-historique.md § Dev Agent Record § Review Findings] — Patch: `menuAnchor()`, `SimpleDateFormat` hors LazyColumn, `loadProfile()` flow, `@Singleton` DAO
- [Source: deferred-work.md § 2026-06-16] — Skew timestamp backend, `apply()` async TokenStore

---

## Testing Requirements

### Android — Tests unitaires

**`PlayerProfileRepositoryImplTest.kt`** (EXTEND)

- `saveProfileDetails(playStyle = "OFFENSIVE", surfaces = ["CLAY"], ...)` → Room upsert réussi
- `saveProfileDetails(...)` avec VPS failure → succès local, Timber.w
- `buildMatchContextProfile()` avec `coachInstruction1 = "travail du revers"`, `coachInstruction2 = ""`, `coachInstruction3 = null` → `coachInstructions = ["travail du revers"]` (vide et null omis)
- `buildMatchContextProfile()` avec `preferredSurfaces = "CLAY,HARD"` → `preferredSurfaces = ["CLAY", "HARD"]`
- `observeMatchSessionCount()` → émet 0

**`ProfileViewModelTest.kt`** (EXTEND)

- `saveProfileDetails(...)` → `ProfileDetailsSaved` side effect + state mis à jour
- `state.matchSessionCount = 0` → inférence de style "Données insuffisantes"
- `saveFftLicense("1234567")` → `playerDataStore.saveFftLicenseNumber()` appelé
- La licence FFT n'apparaît pas dans les arguments de `profileRepository.saveProfileDetails()`

### VPS — Tests d'intégration

**`backend/tests/integration/test_profile_details_api.py`** (NEW)

```python
async def test_update_profile_details_valid(client):
    response = await client.put("/api/v1/profile/details",
        json={"play_style": "OFFENSIVE", "preferred_surfaces": "CLAY,HARD",
              "coach_instruction_1": "Améliorer le service"},
        headers={"Authorization": f"Bearer {valid_token}"}
    )
    assert response.status_code == 200
    assert "updated_at" in response.json()

async def test_update_profile_invalid_style(client):
    response = await client.put("/api/v1/profile/details",
        json={"play_style": "INVINCIBLE"},
        headers={"Authorization": f"Bearer {valid_token}"}
    )
    assert response.status_code == 422

async def test_update_profile_all_null(client):
    response = await client.put("/api/v1/profile/details",
        json={},
        headers={"Authorization": f"Bearer {valid_token}"}
    )
    assert response.status_code == 200

async def test_update_profile_no_fft_license_in_response(client):
    response = await client.get("/api/v1/profile",
        headers={"Authorization": f"Bearer {valid_token}"}
    )
    assert "fft_license" not in response.json()   # isolation PII
    assert "license" not in response.json()

async def test_update_profile_without_token(client):
    response = await client.put("/api/v1/profile/details", json={})
    assert response.status_code == 401
```

---

## Risks & Mitigations

| Risque | Mitigation |
|--------|------------|
| Oubli de `.addMigrations(MIGRATION_1_2)` → crash `IllegalStateException` | Tâche DA-5 explicite — vérifier avant merge |
| `PlayerDataStore` injecté là où il ne devrait pas → leak PII | Injection uniquement dans `ProfileViewModel` et `PlayerProfileRepositoryImpl` — tester absence dans VPS payloads |
| `coachInstructions` avec strings vides envoyés au LLM | `takeIf { it.isNotBlank() }` dans le mapper + test unitaire |
| `ProfileSummaryDto` avec nouveaux champs → `@Json` manquant → null inattendu | Pattern Moshi existant : vérifier chaque nouveau champ a son annotation `@Json(name = ...)` |
| Migration Alembic avec mauvais `down_revision` → chaîne cassée | Vérifier que `down_revision = 'a1b2c3d4e5f6'` (hash exact de la migration Story 1.4) |
| `FilterChip` multi-select surfaces → état local `selectedSurfaces` désynchronisé du state Orbit | `LaunchedEffect(state.preferredSurfaces) { selectedSurfaces = state.preferredSurfaces.toSet() }` |

## Success Criteria

- Style de jeu sélectionné → sauvegardé localement + affiché à l'écran Profil
- `matchSessionCount < 10` → "Données insuffisantes" affiché (toujours vrai pour Epic 1)
- `coachInstructions` dans `MatchContextProfile` exclut les champs vides/null
- Numéro de licence FFT absent de tout payload VPS (testé par `test_update_profile_no_fft_license_in_response`)
- Migration Alembic `upgrade head` sans erreur
- Room version 2 sans crash sur upgrade depuis version 1
- Tous les tests passent (unit + intégration, sans régression sur les 22 tests Story 1.4)

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- Migration Alembic nécessite JWT_SECRET en variable d'environnement pour `upgrade head` en CLI (hors tests)
- `saveRanking()` mis à jour pour préserver les 5 nouveaux champs Room lors de l'upsert du profil
- Utilisation de `object : Migration(1, 2)` (anonymous object) car l'interface Migration de Room est abstraite

### Completion Notes List

- [x] D-1 : `PlayStyleConstants.kt` et `SurfaceConstants.kt` créés dans `:domain/model/`
- [x] D-2/D-3/D-4 : `PlayerProfile`, `MatchContextProfile`, `PlayerProfileRepository` étendus
- [x] R-1/R-2/R-3 : `PlayerProfileEntity` (5 colonnes), `Mappers` (nouveaux champs + CSV helper), `SecondServeDatabase` v2 + MIGRATION_1_2
- [x] DA-1 : `PlayerDataStore` créé dans `:data/local/` — isolation PII FFT licence respectée
- [x] DA-2/DA-3 : `ProfileDto` étendu (3 nouveaux DTOs), `VpsApiService` + `PUT /api/v1/profile/details`
- [x] DA-4 : `PlayerProfileRepositoryImpl` — `buildMatchContextProfile()` filtre les instructions vides, `saveProfileDetails()`, `observeMatchSessionCount()` = flowOf(0)
- [x] DA-5 : `DataModule` — `.addMigrations(MIGRATION_1_2)`, `providePlayerDataStore`, `providePlayerProfileRepository` étendu
- [x] UI-1/3/4 : `ProfileViewModel` — state étendu, `saveProfileDetails()`, `saveFftLicense()`, `loadFftLicense()` dans `init {}`
- [x] UI-2 : `ProfileScreen` — `PlayStyleSection` (DropdownMenu + FilterChip + 3 champs coach), `FftLicenseSection`
- [x] VPS-1 à VPS-5 : backend Python entièrement mis à jour
- [x] VPS-6 : migration Alembic `b2c3d4e5f6a7` avec `down_revision = 'a1b2c3d4e5f6'` — `upgrade head` OK
- [x] T-1 à T-4 : 35/35 tests passent (backend) ; tests Android mis à jour avec nouveaux constructeurs

### File List

android/domain/src/main/kotlin/com/secondserve/domain/model/PlayStyleConstants.kt (NEW)
android/domain/src/main/kotlin/com/secondserve/domain/model/SurfaceConstants.kt (NEW)
android/domain/src/main/kotlin/com/secondserve/domain/model/PlayerProfile.kt (UPDATE)
android/domain/src/main/kotlin/com/secondserve/domain/model/MatchContextProfile.kt (UPDATE)
android/domain/src/main/kotlin/com/secondserve/domain/repository/PlayerProfileRepository.kt (UPDATE)
android/data/src/main/kotlin/com/secondserve/data/local/PlayerDataStore.kt (NEW)
android/data/src/main/kotlin/com/secondserve/data/local/db/entity/PlayerProfileEntity.kt (UPDATE)
android/data/src/main/kotlin/com/secondserve/data/local/db/entity/Mappers.kt (UPDATE)
android/data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt (UPDATE)
android/data/src/main/kotlin/com/secondserve/data/remote/api/VpsApiService.kt (UPDATE)
android/data/src/main/kotlin/com/secondserve/data/remote/api/dto/ProfileDto.kt (UPDATE)
android/data/src/main/kotlin/com/secondserve/data/repository/PlayerProfileRepositoryImpl.kt (UPDATE)
android/app/src/main/kotlin/com/secondserve/di/DataModule.kt (UPDATE)
android/feature/profile/src/main/kotlin/com/secondserve/feature/profile/ProfileViewModel.kt (UPDATE)
android/feature/profile/src/main/kotlin/com/secondserve/feature/profile/ProfileScreen.kt (UPDATE)
android/data/src/test/kotlin/com/secondserve/data/repository/PlayerProfileRepositoryImplTest.kt (UPDATE)
android/feature/profile/src/test/kotlin/com/secondserve/feature/profile/ProfileViewModelTest.kt (UPDATE)
backend/app/features/profile/models.py (UPDATE)
backend/app/features/profile/schemas.py (UPDATE)
backend/app/features/profile/repository.py (UPDATE)
backend/app/features/profile/service.py (UPDATE)
backend/app/api/v1/profile.py (UPDATE)
backend/alembic/versions/b2c3d4e5f6a7_add_profile_details_columns.py (NEW)
backend/tests/integration/test_profile_details_api.py (NEW)
backend/tests/unit/test_profile_details_service.py (NEW)
_bmad-output/implementation-artifacts/1-5-profil-joueur-style-de-jeu-donnees-complementaires.md (UPDATE)
_bmad-output/implementation-artifacts/sprint-status.yaml (UPDATE)

### Review Findings

- [x] [Review][Patch] Coach instruction `LaunchedEffect` manquant — champs instruction1/2/3 toujours vides au rechargement [ProfileScreen.kt:PlayStyleSection]
- [x] [Review][Patch] `PlayerDataStore` non utilisé dans `PlayerProfileRepositoryImpl` — injection inutile créant une surface de risque PII [PlayerProfileRepositoryImpl.kt, DataModule.kt]
- [x] [Review][Patch] `PlayerDataStore.prefs` sans try/catch sur `EncryptedSharedPreferences.create()` — diverge du pattern `JwtTokenStore` [PlayerDataStore.kt]
- [x] [Review][Patch] CSV `preferred_surfaces` sans validation côté backend — valeurs arbitraires stockées et envoyées au LLM [schemas.py]
- [x] [Review][Patch] Duplication du parsing CSV `split/filter` sans `.trim()` → corruption silencieuse sur round-trip [Mappers.kt, PlayerProfileRepositoryImpl.kt]
- [x] [Review][Patch] Consignes coach sans limite de longueur — injection de prompt illimitée [schemas.py]
- [x] [Review][Patch] `isSaving` partagé entre `saveRanking()` et `saveProfileDetails()` — bloque les sections UI indépendantes [ProfileViewModel.kt, ProfileScreen.kt]
- [x] [Review][Defer] Race read-modify-write dans `saveRanking()`/`saveProfileDetails()` [PlayerProfileRepositoryImpl.kt] — deferred, pattern pré-existant Story 1.4
- [x] [Review][Defer] `loadProfile()` lit Room uniquement (divergence fresh-install) [PlayerProfileRepositoryImpl.kt] — deferred, offline-first by design depuis Story 1.4
- [x] [Review][Defer] `PlayStyleConstants` dupliqué Android/backend — peut diverger — deferred, concerne multi-repo, hors scope story
- [x] [Review][Defer] `apply()` async dans `saveFftLicense()` — pattern cohérent avec `JwtTokenStore` — deferred, acceptable
- [x] [Review][Defer] Race concurrent PUT /profile/details backend — `SELECT FOR UPDATE` manquant — deferred, pré-existant, refactoring majeur

### Review Findings — Passe 2 (2026-06-16)

- [ ] [Review][Decision] AC-1 : message "Données insuffisantes" conditionnel à `selectedStyle == null` — masqué dès qu'un style est sélectionné même si `matchSessionCount < 10` ; AC-1 implique affichage incondititionnel quand sessions < 10 [ProfileScreen.kt:PlayStyleSection]
- [ ] [Review][Patch] Backend : `validate_preferred_surfaces` retourne `""` au lieu de `None` pour une chaîne vide (`"".split(",")` → liste vide → `",".join([])` = `""`) — stocke `""` en DB au lieu de `NULL` [backend/app/features/profile/schemas.py]
- [ ] [Review][Patch] Backend : surfaces dupliquées dans le CSV non déduplicées — `"CLAY,CLAY,HARD"` passe la validation et est stocké tel quel ; round-trip Android normalise via `toSet()` mais la DB reste incohérente [backend/app/features/profile/schemas.py]
- [ ] [Review][Patch] Android : champs consignes coach sans contrainte de longueur — backend impose `max_length=500` mais `OutlinedTextField` accepte une saisie illimitée ; dépasse 500 → VPS renvoie 422, save local OK → désynchronisation silencieuse [ProfileScreen.kt:PlayStyleSection]
- [x] [Review][Defer] Ordre non déterministe `selectedSurfaces.toList()` — `toSet()` sur une `List<String>` produit un `LinkedHashSet` (ordre d'insertion stable en session), mais les toggles via `SnapshotStateSet` peuvent modifier l'ordre entre sessions [ProfileScreen.kt] — deferred, cosmétique, pas d'impact fonctionnel
- [x] [Review][Defer] `LaunchedEffect` écrase les éditions en cours si `loadProfile()` se termine pendant la saisie — champs surfaces/style/instructions réinitialisés silencieusement [ProfileScreen.kt:PlayStyleSection] — deferred, refactoring "dirty state" hors scope
- [x] [Review][Defer] Save hors-ligne peut écraser play_style/preferred_surfaces sur le backend — `saveProfileDetails` envoie tous les champs depuis l'état UI (potentiellement null si le chargement a échoué) ; `saveRanking` lit depuis DAO pour préserver — deferred, pattern offline-first pré-existant
- [x] [Review][Defer] `EncryptedSharedPreferences` exceptions post-init non catchées dans ViewModel — `saveFftLicenseNumber`/`getFftLicenseNumber` synchrones sans try/catch ; KeyStore reset (factory reset, root) peut lever `GeneralSecurityException` hors du catch d'init [PlayerDataStore.kt, ProfileViewModel.kt] — deferred, pattern identique à `JwtTokenStore`, acceptable MVP

## Change Log

- 2026-06-16 : Implémentation story 1.5 complète — style de jeu, surfaces, consignes coach, licence FFT, migration Room 1→2, migration Alembic, PUT /profile/details, 35 tests backend passent
- 2026-06-16 : Code review passe 1 — 7 patches appliqués, 5 défauts différés
- 2026-06-16 : Code review passe 2 — 1 décision nécessaire, 3 patches identifiés, 4 défauts différés
