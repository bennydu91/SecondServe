# Add Android Session Pull Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make sessions created on the web (or on any other device) actually appear on the phone. Today the phone only ever reads its own local Room database — it has no code path that fetches sessions from the backend — so anything created through the web app is permanently invisible on the phone.

**Architecture:** Add a `GET /api/v1/sessions` client call to the Android app, and a new `SessionRepository.pullFromServer()` that reconciles the backend's session list into the local Room database. Sessions the phone itself already pushed are recognized via the `client_id` field (added in [[2026-07-05-fix-sync-session-id-collision]]) and matched directly against the local row with that same Room `id` — no new bookkeeping needed for those. Sessions with no `client_id` (created on the web, or on any device other than this one) are "foreign": they get a new local Room row, tracked via a new `server_id` column so a later pull recognizes them again instead of duplicating them. The pull is triggered whenever the History screen is opened.

**Tech Stack:** Kotlin, Room, Retrofit + Moshi, Hilt, Orbit MVI, JUnit 5 + MockK + Turbine (Android); FastAPI + Pydantic (backend, one schema field).

## Global Constraints

- This plan depends on [[2026-07-05-fix-sync-session-id-collision]] being implemented and deployed first — it relies on the backend's `sessions.client_id` column existing and being populated correctly by `SyncService`. Do not start this plan until that one is deployed to the VPS.
- Android tests run via Gradle on the VPS (per [[build_deploy_workflow]] memory): `./gradlew :data:testDebugUnitTest :feature:history:testDebugUnitTest` etc. — run tests from the VPS, never attempt `installDebug`/`installStaging` from the VPS (no devices attached there).
- Backend tests run with `pytest` from `/root/SecondServe/backend`.
- Do not change the push path (`SyncWorker`, `SessionRepositoryImpl.createSession`/`closeSession`/etc.) — this plan only adds a read path, it does not touch the existing write path.
- Do not implement a periodic background pull (WorkManager) in this plan — triggering on History screen open is sufficient for the reported bug and keeps this plan's blast radius small. Note it as a possible follow-up, don't build it.

---

## File Structure

- Modify: `backend/app/features/sessions/schemas.py` — expose `client_id` on `SessionResponse`.
- Modify: `backend/tests/integration/test_sessions_api.py` — cover the new field.
- Modify: `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/SessionEntity.kt` — add `serverId` column.
- Modify: `android/data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt` — bump version, add `MIGRATION_13_14`.
- Modify: `android/app/src/main/kotlin/com/secondserve/di/DataModule.kt` — register the new migration.
- Modify: `android/data/src/main/kotlin/com/secondserve/data/local/dao/SessionDao.kt` — add `getByServerId`.
- Create: `android/data/src/main/kotlin/com/secondserve/data/remote/api/dto/SessionDto.kt` — remote DTOs for the sessions list response.
- Modify: `android/data/src/main/kotlin/com/secondserve/data/remote/api/VpsApiService.kt` — add `listSessions()`.
- Modify: `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/Mappers.kt` — add `RemoteSessionDto.toForeignEntity()` / `.applyTo()`.
- Modify: `android/domain/src/main/kotlin/com/secondserve/domain/repository/SessionRepository.kt` — add `pullFromServer()`.
- Modify: `android/data/src/main/kotlin/com/secondserve/data/repository/SessionRepositoryImpl.kt` — implement `pullFromServer()`.
- Modify: `android/data/src/test/kotlin/com/secondserve/data/repository/SessionRepositoryImplTest.kt` — update constructor call, add pull tests.
- Modify: `android/feature/history/src/main/kotlin/com/secondserve/feature/history/HistoryViewModel.kt` — trigger the pull.
- Modify: `android/feature/history/src/test/kotlin/com/secondserve/feature/history/HistoryViewModelTest.kt` — cover the trigger.

---

### Task 1: Expose `client_id` on the backend `SessionResponse`

**Files:**
- Modify: `backend/app/features/sessions/schemas.py`
- Modify: `backend/tests/integration/test_sessions_api.py`

**Interfaces:**
- Produces: `SessionResponse.client_id: int | None` — the field Android's pull logic (Task 4) uses to tell "mine" from "foreign".

- [ ] **Step 1: Write the failing test**

Append to `backend/tests/integration/test_sessions_api.py`:

```python
@pytest.mark.asyncio
async def test_list_sessions_exposes_client_id_for_phone_synced_sessions(client):
    token = make_token()

    # Session créée directement via le web : pas de client_id.
    await client.post(
        "/api/v1/sessions",
        json={
            "surface": "CLAY",
            "match_format": "BEST_OF_3",
            "third_set_rule": "FULL_ADVANTAGE",
            "created_at": 1_000_000,
        },
        headers=auth(token),
    )

    # Session poussée depuis le téléphone : porte un client_id.
    await client.post(
        "/api/v1/sync/push",
        json={
            "sessions": [{
                "client_id": 7,
                "surface": "HARD",
                "match_format": "BEST_OF_3",
                "third_set_rule": "FULL_ADVANTAGE",
                "opponent": None,
                "competition_type": None,
                "tournament": None,
                "status": "COMPLETED",
                "session_type": "MATCH",
                "result": "VICTORY",
                "feeling_rating": None,
                "feeling_comment": None,
                "created_at": 1_000_000,
                "updated_at": 2_000_000,
            }]
        },
        headers=auth(token),
    )

    response = await client.get("/api/v1/sessions", headers=auth(token))
    assert response.status_code == 200
    items = response.json()["items"]
    assert len(items) == 2
    web_item = next(i for i in items if i["surface"] == "CLAY")
    phone_item = next(i for i in items if i["surface"] == "HARD")
    assert web_item["client_id"] is None
    assert phone_item["client_id"] == 7
```

- [ ] **Step 2: Run it and confirm it fails**

```bash
cd /root/SecondServe/backend && source .venv/bin/activate && python -m pytest tests/integration/test_sessions_api.py::test_list_sessions_exposes_client_id_for_phone_synced_sessions -v
```

Expected: FAIL with `KeyError: 'client_id'` (field absent from the response JSON).

- [ ] **Step 3: Add the field to the schema**

Edit `backend/app/features/sessions/schemas.py`, in `SessionResponse` (add right after `id`):

```python
class SessionResponse(BaseModel):
    id: int
    client_id: Optional[int] = None
    surface: str
    match_format: str
    third_set_rule: str
    opponent: Optional[str] = None
    competition_type: Optional[str] = None
    tournament: Optional[str] = None
    status: str
    session_type: str
    result: Optional[str] = None
    score_text: Optional[str] = None
    score_seed_json: Optional[str] = None
    created_at: int
    updated_at: int

    model_config = {"from_attributes": True}
```

No change needed in `SessionService` — `SessionResponse.model_validate(session)` (`from_attributes=True`) reads `session.client_id` straight off the ORM model, which already has that column from [[2026-07-05-fix-sync-session-id-collision]].

- [ ] **Step 4: Run the test again and confirm it passes**

```bash
cd /root/SecondServe/backend && source .venv/bin/activate && python -m pytest tests/integration/test_sessions_api.py -v
```

Expected: ALL PASS.

- [ ] **Step 5: Run the full backend suite**

```bash
cd /root/SecondServe/backend && source .venv/bin/activate && python -m pytest -v
```

Expected: ALL PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/app/features/sessions/schemas.py backend/tests/integration/test_sessions_api.py
git commit -m "feat(backend): expose client_id on session list/detail responses"
```

- [ ] **Step 7: Deploy to the VPS**

Restart the FastAPI process on the VPS so it picks up the new field (same process you use for normal backend deploys — confirm via `ss -tlnp`/whatever process manager is configured before restarting).

---

### Task 2: Add `server_id` column to the Android `sessions` table

**Files:**
- Modify: `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/SessionEntity.kt`
- Modify: `android/data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt`
- Modify: `android/app/src/main/kotlin/com/secondserve/di/DataModule.kt`
- Modify: `android/data/src/main/kotlin/com/secondserve/data/local/dao/SessionDao.kt`

**Interfaces:**
- Produces: `SessionEntity.serverId: Long?`, `SessionDao.getByServerId(serverId: Long): SessionEntity?` — used by Task 4's reconciliation logic.

- [ ] **Step 1: Add the column to `SessionEntity`**

Edit `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/SessionEntity.kt`, adding after `id`:

```kotlin
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "server_id") val serverId: Long? = null,
    @ColumnInfo(name = "surface") val surface: String,
    // ... rest of the fields unchanged
```

(Only the new `serverId` line is added; every other field and the class body stay exactly as they are today.)

- [ ] **Step 2: Bump the Room database version and add the migration**

Edit `android/data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt`:

Change:
```kotlin
    version = 13,
```
to:
```kotlin
    version = 14,
```

Add, after `MIGRATION_12_13`, still inside `companion object`:

```kotlin
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sessions ADD COLUMN server_id INTEGER")
            }
        }
```

- [ ] **Step 3: Register the migration**

Edit `android/app/src/main/kotlin/com/secondserve/di/DataModule.kt`, in `provideSecondServeDatabase`, add `SecondServeDatabase.MIGRATION_13_14` at the end of the `.addMigrations(...)` list (after `MIGRATION_12_13`).

- [ ] **Step 4: Add the DAO query**

Edit `android/data/src/main/kotlin/com/secondserve/data/local/dao/SessionDao.kt`, adding this method inside the `SessionDao` interface:

```kotlin
    @Query("SELECT * FROM sessions WHERE server_id = :serverId")
    suspend fun getByServerId(serverId: Long): SessionEntity?
```

- [ ] **Step 5: Compile to confirm the schema change and migration are consistent**

```bash
cd /root/SecondServe/android && ./gradlew :data:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add android/data/src/main/kotlin/com/secondserve/data/local/db/entity/SessionEntity.kt android/data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt android/app/src/main/kotlin/com/secondserve/di/DataModule.kt android/data/src/main/kotlin/com/secondserve/data/local/dao/SessionDao.kt
git commit -m "feat(android): add server_id column to sessions table for pull reconciliation"
```

---

### Task 3: Add the remote DTOs and `listSessions()` API call

**Files:**
- Create: `android/data/src/main/kotlin/com/secondserve/data/remote/api/dto/SessionDto.kt`
- Modify: `android/data/src/main/kotlin/com/secondserve/data/remote/api/VpsApiService.kt`

**Interfaces:**
- Produces: `RemoteSessionsResponse`, `RemoteSessionDto` (with `clientId: Long?`), `VpsApiService.listSessions(): RemoteSessionsResponse` — consumed by Task 4.

- [ ] **Step 1: Create the DTO file**

Create `android/data/src/main/kotlin/com/secondserve/data/remote/api/dto/SessionDto.kt`:

```kotlin
package com.secondserve.data.remote.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RemoteSessionsResponse(
    val items: List<RemoteSessionDto>,
    val total: Int
)

@JsonClass(generateAdapter = true)
data class RemoteSessionDto(
    val id: Long,
    @Json(name = "client_id") val clientId: Long?,
    val surface: String,
    @Json(name = "match_format") val matchFormat: String,
    @Json(name = "third_set_rule") val thirdSetRule: String,
    val opponent: String?,
    @Json(name = "competition_type") val competitionType: String?,
    val tournament: String?,
    val status: String,
    @Json(name = "session_type") val sessionType: String,
    val result: String?,
    @Json(name = "score_text") val scoreText: String?,
    @Json(name = "created_at") val createdAt: Long,
    @Json(name = "updated_at") val updatedAt: Long
)
```

- [ ] **Step 2: Add the endpoint to `VpsApiService`**

Edit `android/data/src/main/kotlin/com/secondserve/data/remote/api/VpsApiService.kt`:

Add the import:
```kotlin
import com.secondserve.data.remote.api.dto.RemoteSessionsResponse
```

Add the method (near `syncPush`):
```kotlin
    @GET("api/v1/sessions")
    suspend fun listSessions(): RemoteSessionsResponse
```

- [ ] **Step 3: Compile**

```bash
cd /root/SecondServe/android && ./gradlew :data:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add android/data/src/main/kotlin/com/secondserve/data/remote/api/dto/SessionDto.kt android/data/src/main/kotlin/com/secondserve/data/remote/api/VpsApiService.kt
git commit -m "feat(android): add listSessions() API call"
```

---

### Task 4: Implement `SessionRepository.pullFromServer()`

**Files:**
- Modify: `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/Mappers.kt`
- Modify: `android/domain/src/main/kotlin/com/secondserve/domain/repository/SessionRepository.kt`
- Modify: `android/data/src/main/kotlin/com/secondserve/data/repository/SessionRepositoryImpl.kt`
- Test: `android/data/src/test/kotlin/com/secondserve/data/repository/SessionRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `VpsApiService.listSessions()` (Task 3), `SessionDao.getByServerId` (Task 2), `SessionEntity.serverId` (Task 2).
- Produces: `SessionRepository.pullFromServer(): AppResult<Unit>` — consumed by Task 5.

- [ ] **Step 1: Add the mapper functions**

Edit `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/Mappers.kt`, add the import:

```kotlin
import com.secondserve.data.remote.api.dto.RemoteSessionDto
```

Add at the end of the file:

```kotlin
fun RemoteSessionDto.toForeignEntity(): SessionEntity = SessionEntity(
    id = 0L,
    serverId = id,
    surface = surface,
    matchFormat = matchFormat,
    thirdSetRule = thirdSetRule,
    opponent = opponent,
    competitionType = competitionType,
    tournament = tournament,
    status = status,
    sessionType = sessionType,
    result = result,
    scoreText = scoreText,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun RemoteSessionDto.applyTo(entity: SessionEntity): SessionEntity = entity.copy(
    surface = surface,
    matchFormat = matchFormat,
    thirdSetRule = thirdSetRule,
    opponent = opponent,
    competitionType = competitionType,
    tournament = tournament,
    status = status,
    sessionType = sessionType,
    result = result,
    scoreText = scoreText,
    updatedAt = updatedAt
)
```

- [ ] **Step 2: Add `pullFromServer` to the domain interface**

Edit `android/domain/src/main/kotlin/com/secondserve/domain/repository/SessionRepository.kt`, add:

```kotlin
    suspend fun pullFromServer(): AppResult<Unit>
```

(as a new method in the `SessionRepository` interface, alongside the existing ones).

- [ ] **Step 3: Write the failing tests**

Add to `android/data/src/test/kotlin/com/secondserve/data/repository/SessionRepositoryImplTest.kt`.

First, update `setup()` to inject a `vpsApiService` mock and pass it to the constructor:

```kotlin
    private lateinit var dao: SessionDao
    private lateinit var syncQueueDao: SyncQueueDao
    private lateinit var database: SecondServeDatabase
    private lateinit var notificationScheduler: NotificationScheduler
    private lateinit var vpsApiService: VpsApiService
    private lateinit var repository: SessionRepositoryImpl

    @BeforeEach
    fun setup() {
        dao = mockk()
        syncQueueDao = mockk(relaxed = true)
        database = mockk()
        notificationScheduler = mockk(relaxed = true)
        vpsApiService = mockk()
        mockkStatic("androidx.room.RoomDatabaseKt__RoomDatabase_androidKt")
        coEvery { database.withTransaction<Any?>(any()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            secondArg<suspend () -> Any?>().invoke()
        }
        repository = SessionRepositoryImpl(dao, syncQueueDao, database, notificationScheduler, vpsApiService)
    }
```

Add the import at the top of the file:
```kotlin
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.data.remote.api.dto.RemoteSessionDto
import com.secondserve.data.remote.api.dto.RemoteSessionsResponse
```

Then add these tests at the end of the class, before the final closing brace:

```kotlin
    private fun aRemoteDto(
        id: Long = 100L,
        clientId: Long? = null,
        surface: String = "CLAY",
        status: String = "COMPLETED",
        updatedAt: Long = 1_000_000L
    ) = RemoteSessionDto(
        id = id,
        clientId = clientId,
        surface = surface,
        matchFormat = "BEST_OF_3",
        thirdSetRule = "FULL_ADVANTAGE",
        opponent = null,
        competitionType = null,
        tournament = null,
        status = status,
        sessionType = "MATCH",
        result = null,
        scoreText = null,
        createdAt = 1_000_000L,
        updatedAt = updatedAt
    )

    @Test
    fun `pullFromServer inserts a new local row for a foreign session not seen before`() = runTest {
        coEvery { vpsApiService.listSessions() } returns RemoteSessionsResponse(
            items = listOf(aRemoteDto(id = 100L, clientId = null)),
            total = 1
        )
        coEvery { dao.getByServerId(100L) } returns null
        coEvery { dao.insert(any()) } returns 55L

        val result = repository.pullFromServer()

        assertIs<AppResult.Success<Unit>>(result)
        coVerify { dao.insert(match { it.serverId == 100L && it.id == 0L }) }
    }

    @Test
    fun `pullFromServer updates existing foreign row instead of duplicating it`() = runTest {
        val existing = anEntity(id = 55L).copy(serverId = 100L, updatedAt = 1_000L)
        coEvery { vpsApiService.listSessions() } returns RemoteSessionsResponse(
            items = listOf(aRemoteDto(id = 100L, clientId = null, status = "COMPLETED", updatedAt = 2_000L)),
            total = 1
        )
        coEvery { dao.getByServerId(100L) } returns existing
        coEvery { dao.update(any()) } returns Unit

        val result = repository.pullFromServer()

        assertIs<AppResult.Success<Unit>>(result)
        coVerify(exactly = 0) { dao.insert(any()) }
        coVerify { dao.update(match { it.id == 55L && it.status == "COMPLETED" }) }
    }

    @Test
    fun `pullFromServer skips foreign row update when local copy is already newer`() = runTest {
        val existing = anEntity(id = 55L).copy(serverId = 100L, status = "ACTIVE", updatedAt = 5_000L)
        coEvery { vpsApiService.listSessions() } returns RemoteSessionsResponse(
            items = listOf(aRemoteDto(id = 100L, clientId = null, status = "COMPLETED", updatedAt = 2_000L)),
            total = 1
        )
        coEvery { dao.getByServerId(100L) } returns existing

        val result = repository.pullFromServer()

        assertIs<AppResult.Success<Unit>>(result)
        coVerify(exactly = 0) { dao.update(any()) }
    }

    @Test
    fun `pullFromServer reconciles own session by matching client_id to local id`() = runTest {
        val own = anEntity(id = 7L, status = "ACTIVE", updatedAt = 1_000L)
        coEvery { vpsApiService.listSessions() } returns RemoteSessionsResponse(
            items = listOf(aRemoteDto(id = 999L, clientId = 7L, status = "COMPLETED", updatedAt = 2_000L)),
            total = 1
        )
        coEvery { dao.getById(7L) } returns own
        coEvery { dao.update(any()) } returns Unit

        val result = repository.pullFromServer()

        assertIs<AppResult.Success<Unit>>(result)
        coVerify(exactly = 0) { dao.getByServerId(any()) }
        coVerify(exactly = 0) { dao.insert(any()) }
        coVerify { dao.update(match { it.id == 7L && it.status == "COMPLETED" }) }
    }

    @Test
    fun `pullFromServer returns AppResult Error when the API call fails`() = runTest {
        coEvery { vpsApiService.listSessions() } throws RuntimeException("network error")

        val result = repository.pullFromServer()

        assertIs<AppResult.Error>(result)
    }
```

- [ ] **Step 4: Run the tests and confirm they fail**

```bash
cd /root/SecondServe/android && ./gradlew :data:testDebugUnitTest --tests "com.secondserve.data.repository.SessionRepositoryImplTest"
```

Expected: FAIL to compile (constructor signature mismatch — `SessionRepositoryImpl` doesn't yet take a `VpsApiService` param, `pullFromServer` doesn't exist yet).

- [ ] **Step 5: Implement `pullFromServer` in `SessionRepositoryImpl`**

Edit `android/data/src/main/kotlin/com/secondserve/data/repository/SessionRepositoryImpl.kt`.

Add imports:
```kotlin
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.data.local.db.entity.applyTo
import com.secondserve.data.local.db.entity.toForeignEntity
```

Change the constructor:
```kotlin
class SessionRepositoryImpl @Inject constructor(
    private val dao: SessionDao,
    private val syncQueueDao: SyncQueueDao,
    private val database: SecondServeDatabase,
    private val notificationScheduler: NotificationScheduler,
    private val vpsApiService: VpsApiService
) : SessionRepository {
```

Add the method (anywhere in the class body, e.g. right after `getAllSessions`):

```kotlin
    override suspend fun pullFromServer(): AppResult<Unit> = try {
        val response = vpsApiService.listSessions()
        for (remote in response.items) {
            if (remote.clientId != null) {
                val local = dao.getById(remote.clientId)
                if (local != null && remote.updatedAt > local.updatedAt) {
                    dao.update(remote.applyTo(local))
                }
            } else {
                val existing = dao.getByServerId(remote.id)
                if (existing == null) {
                    dao.insert(remote.toForeignEntity())
                } else if (remote.updatedAt > existing.updatedAt) {
                    dao.update(remote.applyTo(existing))
                }
            }
        }
        Timber.d("SessionRepository: pull terminé, %d sessions distantes examinées", response.items.size)
        AppResult.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "SessionRepository: pullFromServer failed")
        AppResult.Error(e)
    }
```

- [ ] **Step 6: Run the tests and confirm they pass**

```bash
cd /root/SecondServe/android && ./gradlew :data:testDebugUnitTest --tests "com.secondserve.data.repository.SessionRepositoryImplTest"
```

Expected: ALL PASS.

- [ ] **Step 7: Run the full `:data` and `:domain` unit test suites to confirm no regressions**

```bash
cd /root/SecondServe/android && ./gradlew :data:testDebugUnitTest :domain:test
```

Expected: ALL PASS.

- [ ] **Step 8: Commit**

```bash
git add android/data/src/main/kotlin/com/secondserve/data/local/db/entity/Mappers.kt android/domain/src/main/kotlin/com/secondserve/domain/repository/SessionRepository.kt android/data/src/main/kotlin/com/secondserve/data/repository/SessionRepositoryImpl.kt android/data/src/test/kotlin/com/secondserve/data/repository/SessionRepositoryImplTest.kt
git commit -m "feat(android): implement SessionRepository.pullFromServer with client_id/server_id reconciliation"
```

---

### Task 5: Trigger the pull when the History screen opens

**Files:**
- Modify: `android/feature/history/src/main/kotlin/com/secondserve/feature/history/HistoryViewModel.kt`
- Test: `android/feature/history/src/test/kotlin/com/secondserve/feature/history/HistoryViewModelTest.kt`

**Interfaces:**
- Consumes: `SessionRepository.pullFromServer()` (Task 4).

- [ ] **Step 1: Write the failing test**

Add to `android/feature/history/src/test/kotlin/com/secondserve/feature/history/HistoryViewModelTest.kt`, add import:

```kotlin
import com.secondserve.domain.AppResult
import io.mockk.coEvery
import io.mockk.coVerify
```

Add this test at the end of the class:

```kotlin
    @Test
    fun `init triggers a pull from the server`() = runTest {
        val sessionsFlow = MutableStateFlow(emptyList<Session>())
        every { sessionRepository.getAllSessions() } returns sessionsFlow
        coEvery { sessionRepository.pullFromServer() } returns AppResult.Success(Unit)

        viewModel = HistoryViewModel(sessionRepository)
        viewModel.container.stateFlow.first { it is HistoryUiState.Content }

        coVerify(exactly = 1) { sessionRepository.pullFromServer() }
    }
```

- [ ] **Step 2: Run it and confirm it fails**

```bash
cd /root/SecondServe/android && ./gradlew :feature:history:testDebugUnitTest --tests "com.secondserve.feature.history.HistoryViewModelTest"
```

Expected: FAIL — `pullFromServer()` is never called, or the mock throws `MockKException` for an unstubbed call if `sessionRepository` isn't relaxed (verify by running; if it errors instead of cleanly failing the `coVerify`, that's still a fail signal confirming the trigger doesn't exist yet).

- [ ] **Step 3: Add the trigger**

Edit `android/feature/history/src/main/kotlin/com/secondserve/feature/history/HistoryViewModel.kt`:

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
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel(), ContainerHost<HistoryUiState, HistorySideEffect> {

    override val container = container<HistoryUiState, HistorySideEffect>(HistoryUiState.Loading)

    init {
        viewModelScope.launch {
            sessionRepository.getAllSessions()
                .catch { e -> intent { reduce { HistoryUiState.Error(e.message ?: "Erreur de chargement") } } }
                .collect { sessions ->
                    intent { reduce { HistoryUiState.Content(sessions) } }
                }
        }
        viewModelScope.launch {
            val result = sessionRepository.pullFromServer()
            if (result is com.secondserve.domain.AppResult.Error) {
                Timber.w(result.exception, "HistoryViewModel: pull depuis le serveur a échoué, affichage des données locales uniquement")
            }
        }
    }

    fun onSessionClicked(sessionId: Long) = intent {
        postSideEffect(HistorySideEffect.NavigateToDetail(sessionId))
    }
}
```

Note the pull runs in its own `launch` block, independent of the `getAllSessions()` collection: if `pullFromServer()` fails, the screen still shows whatever is already in Room (via the untouched `getAllSessions()` Flow) instead of surfacing an error state for what is a best-effort refresh.

- [ ] **Step 4: Run the test and confirm it passes**

```bash
cd /root/SecondServe/android && ./gradlew :feature:history:testDebugUnitTest --tests "com.secondserve.feature.history.HistoryViewModelTest"
```

Expected: ALL PASS (including the pre-existing tests in this file — `pullFromServer()` needs to be stubbed with `coEvery` in the other pre-existing tests too if MockK's default strict mock complains about an unstubbed call; if any pre-existing test fails with `MockKException`, add `coEvery { sessionRepository.pullFromServer() } returns AppResult.Success(Unit)` to that test's setup).

- [ ] **Step 5: Run the full history feature test suite**

```bash
cd /root/SecondServe/android && ./gradlew :feature:history:testDebugUnitTest
```

Expected: ALL PASS.

- [ ] **Step 6: Commit**

```bash
git add android/feature/history/src/main/kotlin/com/secondserve/feature/history/HistoryViewModel.kt android/feature/history/src/test/kotlin/com/secondserve/feature/history/HistoryViewModelTest.kt
git commit -m "feat(android): pull sessions from the server when the History screen opens"
```

---

### Task 6: Full build, deploy, and manual verification

**Files:** none (build/deploy/manual verification only).

- [ ] **Step 1: Run the full Android unit test suite**

```bash
cd /root/SecondServe/android && ./gradlew test
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 2: Build and deploy to the phone**

From Benny's local machine (not the VPS — see [[build_deploy_workflow]]):

```bash
node scripts/deploy-ui/server.js
```

or the CLI form:
```bash
./scripts/deploy-devices.sh --phone-only
```

- [ ] **Step 3: Manual end-to-end verification**

1. On the web app, create a new session (any match).
2. Open the History screen on the phone.
3. Confirm the web-created session now appears in the phone's history list.
4. On the phone, play and close out a match.
5. Reload the web app's session list (or the dashboard, whichever reads `GET /api/v1/sessions`).
6. Confirm the phone-created session appears there too, as its own distinct entry — not merged into another session.

This exercises both directions fixed by this plan and [[2026-07-05-fix-sync-session-id-collision]] together.

- [ ] **Step 4: Known limitations to mention to Benny**

- No periodic background pull — a session created on the web only appears on the phone the next time the History screen is opened (not immediately/live). Acceptable for now; note as a possible follow-up if it becomes annoying in practice.
- Foreign (web-created) sessions pulled onto the phone will have `feelingRating`, `feelingComment`, `scheduledAt`, and the fine match stats (`firstServePercentSelf/Opponent`, `winnersSelf/Opponent`) as `null`, because `SessionResponse` doesn't currently expose those fields. Not a regression (the web app doesn't set those fields for its own sessions today either), but worth knowing if those fields matter for web-created sessions later.
