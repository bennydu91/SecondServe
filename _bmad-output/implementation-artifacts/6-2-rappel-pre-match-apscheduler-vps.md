---
baseline_commit: be128e2
---

# Story 6.2 : Rappel pré-match & APScheduler VPS

Status: review

## Story

As a player,
I want a preparation reminder before a planned match, with AI-generated coaching content,
So that I arrive mentally prepared with relevant tactical focus points.

## Acceptance Criteria

1. **Given** je crée une Session avec une date/heure future (match planifié)
   **When** la session est sauvegardée
   **Then** un `OneTimeWorkRequest` WorkManager est créé avec un délai calculé pour se déclencher 2h avant le match
   **And** si le réseau est disponible au déclenchement, le VPS APScheduler génère le contenu coaching pré-match via Mistral et le stocke
   **And** `PreMatchReminderWorker` récupère le contenu généré via `GET /api/v1/notifications/pending?session_id=<id>`
   **And** si le réseau est indisponible, un contenu de rappel générique est utilisé en fallback
   **And** la notification contient : adversaire (si renseigné), surface, au moins 1 référence spécifique au profil (Axe de travail, classement, historique récent)
   **And** si le match planifié est supprimé, le `WorkRequest` correspondant est annulé

## Tasks / Subtasks

---

### BLOC A — Domaine : nouveaux statuts + champ `scheduledAt`

- [x] **T1 — Étendre `SessionStatus` + `Session` domain model** (AC: 1)
  - [x] T1.1 Dans `android/domain/src/main/kotlin/com/secondserve/domain/model/Session.kt` :
    ```kotlin
    enum class SessionStatus { ACTIVE, COMPLETED, INTERRUPTED, PLANNED, CANCELLED }
    // ...
    data class Session(
        // ...champs existants...
        val scheduledAt: Long? = null  // epoch ms, null = match immédiat
    )
    ```
  - [x] T1.2 Dans `android/domain/src/main/kotlin/com/secondserve/domain/notification/NotificationScheduler.kt`, ajouter :
    ```kotlin
    interface NotificationScheduler {
        fun scheduleDaily()
        fun scheduleEvery2Days()
        fun scheduleWeekly()
        fun cancel()
        fun schedulePreMatchReminder(sessionId: Long, triggerAtMs: Long)
        fun cancelPreMatchReminder(sessionId: Long)
    }
    ```
  - [x] T1.3 Dans `android/domain/src/main/kotlin/com/secondserve/domain/repository/SessionRepository.kt`, ajouter :
    ```kotlin
    suspend fun deleteSession(sessionId: Long): AppResult<Unit>
    ```

---

### BLOC B — Data layer : Room migration + mappers + DAO

- [x] **T2 — Room migration 10→11 : ajout de `scheduled_at`** (AC: 1)
  - [x] T2.1 Dans `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/SessionEntity.kt`, ajouter :
    ```kotlin
    @ColumnInfo(name = "scheduled_at") val scheduledAt: Long? = null
    ```
  - [x] T2.2 Dans `android/data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt` :
    - Changer `version = 10` → `version = 11`
    - Ajouter dans le `companion object` :
    ```kotlin
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE sessions ADD COLUMN scheduled_at INTEGER")
        }
    }
    ```
    - Ajouter `MIGRATION_10_11` dans le builder Room (chercher l'appel `addMigrations(...)` dans `DataModule.kt` ou `DatabaseModule.kt`)

- [x] **T3 — Mettre à jour les mappers** (AC: 1)
  - [x] T3.1 Dans `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/Mappers.kt` :
    - `SessionEntity.toDomain()` : ajouter `scheduledAt = scheduledAt`
    - `Session.toEntity()` : ajouter `scheduledAt = scheduledAt`
    - `Session.toSyncDto()` : ajouter `@Json(name = "scheduled_at") val scheduledAt: Long?` dans `SyncSessionDto`

- [x] **T4 — Mettre à jour `SyncSessionDto`** (AC: 1)
  - [x] T4.1 Dans `android/data/src/main/kotlin/com/secondserve/data/remote/api/dto/SyncDto.kt`, ajouter dans `SyncSessionDto` :
    ```kotlin
    @Json(name = "scheduled_at") val scheduledAt: Long? = null
    ```

- [x] **T5 — `SessionDao` : ajout `deleteById`** (AC: 1)
  - [x] T5.1 Dans `android/data/src/main/kotlin/com/secondserve/data/local/dao/SessionDao.kt`, ajouter :
    ```kotlin
    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM sessions WHERE scheduled_at IS NOT NULL AND status = 'PLANNED' ORDER BY scheduled_at ASC")
    suspend fun getPlannedSessions(): List<SessionEntity>
    ```

---

### BLOC C — Data layer : workers + scheduler + repo

- [x] **T6 — Étendre `NotificationSchedulerImpl`** (AC: 1)
  - [x] T6.1 Dans `android/data/src/main/kotlin/com/secondserve/data/worker/NotificationSchedulerImpl.kt`, ajouter les imports nécessaires et les implémentations :
    ```kotlin
    import androidx.work.Data
    import androidx.work.ExistingWorkPolicy
    import androidx.work.OneTimeWorkRequestBuilder
    import androidx.work.workDataOf

    override fun schedulePreMatchReminder(sessionId: Long, triggerAtMs: Long) {
        val delayMs = triggerAtMs - System.currentTimeMillis()
        if (delayMs <= 0L) return
        val data = workDataOf(
            PreMatchReminderWorker.KEY_SESSION_ID to sessionId
        )
        val request = OneTimeWorkRequestBuilder<PreMatchReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "pre_match_reminder_$sessionId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    override fun cancelPreMatchReminder(sessionId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("pre_match_reminder_$sessionId")
    }
    ```

- [x] **T7 — Nouveau `PreMatchReminderWorker`** (AC: 1)
  - [x] T7.1 Créer `android/data/src/main/kotlin/com/secondserve/data/worker/PreMatchReminderWorker.kt` :
    ```kotlin
    package com.secondserve.data.worker

    import android.Manifest
    import android.content.Context
    import android.content.pm.PackageManager
    import androidx.core.app.ActivityCompat
    import androidx.core.app.NotificationCompat
    import androidx.core.app.NotificationManagerCompat
    import androidx.hilt.work.HiltWorker
    import androidx.work.CoroutineWorker
    import androidx.work.WorkerParameters
    import com.secondserve.data.local.PlayerDataStore
    import com.secondserve.data.local.dao.PlayerProfileDao
    import com.secondserve.data.local.dao.WorkAxisDao
    import com.secondserve.data.remote.api.VpsApiService
    import dagger.assisted.Assisted
    import dagger.assisted.AssistedInject
    import timber.log.Timber

    @HiltWorker
    class PreMatchReminderWorker @AssistedInject constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val vpsApiService: VpsApiService,
        private val playerProfileDao: PlayerProfileDao,
        private val workAxisDao: WorkAxisDao,
        private val playerDataStore: PlayerDataStore
    ) : CoroutineWorker(context, params) {

        override suspend fun doWork(): Result {
            val sessionId = inputData.getLong(KEY_SESSION_ID, -1L)
            if (sessionId == -1L) return Result.success()

            val content = fetchVpsContentOrFallback(sessionId)
            if (content.isNullOrBlank()) return Result.success()

            postNotification(content)
            return Result.success()
        }

        private suspend fun fetchVpsContentOrFallback(sessionId: Long): String? {
            return try {
                val response = vpsApiService.getPendingNotification(sessionId)
                response.content.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                Timber.d("PreMatchReminderWorker: VPS unavailable — using fallback")
                buildFallback()
            }
        }

        private suspend fun buildFallback(): String? {
            val surface = try { playerProfileDao.getProfile()?.preferredSurfaces } catch (e: Exception) { null }
            val axis = try {
                workAxisDao.getAllTitles().firstOrNull { it.isNotBlank() }
            } catch (e: Exception) { null }
            return when {
                axis != null && surface != null ->
                    "Rappel pré-match : concentre-toi sur « $axis » (surface : $surface)."
                axis != null ->
                    "Rappel pré-match : concentre-toi sur « $axis »."
                surface != null ->
                    "Rappel pré-match : match sur $surface — sois prêt !"
                else -> "Rappel : tu as un match bientôt. Reste focus !"
            }
        }

        private fun postNotification(content: String) {
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Rappel pré-match")
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            val granted = ActivityCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                try {
                    NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
                } catch (e: SecurityException) {
                    Timber.d("PreMatchReminderWorker: SecurityException on notify: %s", e.message)
                }
            }
        }

        companion object {
            const val KEY_SESSION_ID = "session_id"
            const val CHANNEL_ID = "coaching_notifications"  // canal créé en 6.1
            const val NOTIFICATION_ID = 1002  // 1001 = daily tip (NotificationWorker)
        }
    }
    ```

- [x] **T8 — `VpsApiService` : ajout endpoint `getPendingNotification`** (AC: 1)
  - [x] T8.1 Dans `android/data/src/main/kotlin/com/secondserve/data/remote/api/VpsApiService.kt`, ajouter :
    ```kotlin
    @GET("api/v1/notifications/pending")
    suspend fun getPendingNotification(@Query("session_id") sessionId: Long): PendingNotificationResponse
    ```
  - [x] T8.2 Créer `android/data/src/main/kotlin/com/secondserve/data/remote/api/dto/NotificationDto.kt` :
    ```kotlin
    package com.secondserve.data.remote.api.dto

    import com.squareup.moshi.JsonClass

    @JsonClass(generateAdapter = true)
    data class PendingNotificationResponse(val content: String)
    ```
    Ajouter l'import dans `VpsApiService.kt` : `import com.secondserve.data.remote.api.dto.PendingNotificationResponse`

- [x] **T9 — `SessionRepositoryImpl` : supprimer session + annuler worker** (AC: 1)
  - [x] T9.1 Dans `android/data/src/main/kotlin/com/secondserve/data/repository/SessionRepositoryImpl.kt` :
    - Ajouter `private val notificationScheduler: NotificationScheduler` au constructeur `@Inject` :
      ```kotlin
      @Singleton
      class SessionRepositoryImpl @Inject constructor(
          private val dao: SessionDao,
          private val syncQueueDao: SyncQueueDao,
          private val database: SecondServeDatabase,
          private val notificationScheduler: NotificationScheduler
      ) : SessionRepository {
      ```
    - Ajouter l'import : `import com.secondserve.domain.notification.NotificationScheduler`
    - Implémenter `deleteSession()` :
      ```kotlin
      override suspend fun deleteSession(sessionId: Long): AppResult<Unit> = try {
          dao.deleteById(sessionId)
          notificationScheduler.cancelPreMatchReminder(sessionId)
          Timber.d("SessionRepository: session %d supprimée + reminder annulé", sessionId)
          AppResult.Success(Unit)
      } catch (e: Exception) {
          Timber.e(e, "SessionRepository: deleteSession failed for id=%d", sessionId)
          AppResult.Error(e)
      }
      ```
    - Modifier `createSession()` pour ajouter la session planifiée à la SyncQueue :
      ```kotlin
      override suspend fun createSession(session: Session): AppResult<Session> = try {
          val id = dao.insert(session.toEntity())
          if (session.scheduledAt != null) {
              // Sync immédiat : le VPS APScheduler doit connaître scheduled_at
              val now = System.currentTimeMillis()
              syncQueueDao.insert(SyncQueueEntity(
                  entityType = SyncQueueEntity.ENTITY_TYPE_SESSION,
                  entityId = id,
                  operation = SyncQueueEntity.OPERATION_UPSERT,
                  createdAt = now
              ))
          }
          Timber.d("SessionRepository: session créée id=%d (planned=%b)", id, session.scheduledAt != null)
          AppResult.Success(session.copy(id = id))
      } catch (e: Exception) {
          Timber.e(e, "SessionRepository: createSession failed")
          AppResult.Error(e)
      }
      ```

---

### BLOC D — Feature : NewMatchScreen + planification

- [x] **T10 — Étendre `NewMatchViewModel` pour sessions planifiées** (AC: 1)
  - [x] T10.1 Dans `android/feature/match/src/main/kotlin/com/secondserve/feature/match/NewMatchViewModel.kt` :
    - Injecter `NotificationScheduler` au constructeur :
      ```kotlin
      @HiltViewModel
      class NewMatchViewModel @Inject constructor(
          private val sessionRepository: SessionRepository,
          private val notificationScheduler: NotificationScheduler
      ) : ViewModel(), ContainerHost<NewMatchUiState, NewMatchSideEffect> {
      ```
    - Ajouter dans `NewMatchUiState` :
      ```kotlin
      data class NewMatchUiState(
          // ...champs existants...
          val isScheduled: Boolean = false,
          val scheduledAt: Long? = null,
      ) {
          val canStartMatch: Boolean get() =
              selectedSurface != null && selectedMatchFormat != null &&
              (selectedMatchFormat == MatchFormat.BEST_OF_1 || selectedThirdSetRule != null) &&
              (if (isScheduled) scheduledAt != null && scheduledAt > System.currentTimeMillis() else true)
      }
      ```
    - Ajouter les handlers dans le ViewModel :
      ```kotlin
      fun onScheduledToggled(enabled: Boolean) = intent {
          reduce { state.copy(isScheduled = enabled, scheduledAt = if (!enabled) null else state.scheduledAt) }
      }

      fun onScheduledAtChanged(epochMs: Long) = intent {
          reduce { state.copy(scheduledAt = epochMs) }
      }
      ```
    - Modifier `startMatch()` :
      ```kotlin
      fun startMatch() = intent {
          val surface = state.selectedSurface ?: return@intent
          val matchFormat = state.selectedMatchFormat ?: return@intent
          val thirdSetRule = if (matchFormat == MatchFormat.BEST_OF_3)
              state.selectedThirdSetRule ?: ThirdSetRule.FULL_ADVANTAGE
          else ThirdSetRule.FULL_ADVANTAGE

          reduce { state.copy(isLoading = true) }

          val now = System.currentTimeMillis()
          val isPlanned = state.isScheduled && state.scheduledAt != null && state.scheduledAt > now
          val session = Session(
              surface = surface,
              format = SessionFormat(matchFormat = matchFormat, thirdSetRule = thirdSetRule),
              opponent = state.opponent.takeIf { it.isNotBlank() },
              competitionType = state.competitionType.takeIf { it.isNotBlank() },
              tournament = state.tournament.takeIf { it.isNotBlank() },
              status = if (isPlanned) SessionStatus.PLANNED else SessionStatus.ACTIVE,
              scheduledAt = if (isPlanned) state.scheduledAt else null,
              createdAt = now,
              updatedAt = now
          )

          when (val result = sessionRepository.createSession(session)) {
              is AppResult.Success -> {
                  reduce { state.copy(isLoading = false) }
                  val createdSession = result.data
                  if (isPlanned && createdSession.scheduledAt != null) {
                      val triggerMs = createdSession.scheduledAt - 2 * 60 * 60 * 1000L
                      notificationScheduler.schedulePreMatchReminder(createdSession.id, triggerMs)
                  }
                  if (isPlanned) {
                      postSideEffect(NewMatchSideEffect.SessionPlanned(createdSession.id))
                  } else {
                      postSideEffect(NewMatchSideEffect.SessionStarted(createdSession.id))
                  }
              }
              is AppResult.Error -> {
                  reduce { state.copy(isLoading = false) }
                  postSideEffect(NewMatchSideEffect.ShowError("Impossible de créer la session"))
              }
              AppResult.Loading -> {}
          }
      }
      ```
    - Ajouter dans `sealed class NewMatchSideEffect` :
      ```kotlin
      data class SessionPlanned(val sessionId: Long) : NewMatchSideEffect()
      ```

- [x] **T11 — Étendre `NewMatchScreen` avec DateTimePicker** (AC: 1)
  - [x] T11.1 Dans `android/feature/match/src/main/kotlin/com/secondserve/feature/match/NewMatchScreen.kt` :
    - Ajouter les imports `DatePickerDialog`, `TimePickerDialog`, `Switch`, `Calendar`
    - Collecter le `NewMatchSideEffect.SessionPlanned` et appeler `onNavigateBack()` (ou une callback `onSessionPlanned`)
    - Ajouter la section "Planifier pour plus tard" en bas des champs optionnels :
      ```kotlin
      // Toggle planification
      Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth()
      ) {
          Text(
              text = "Planifier pour plus tard",
              modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.bodyLarge
          )
          Switch(
              checked = state.isScheduled,
              onCheckedChange = { viewModel.onScheduledToggled(it) }
          )
      }

      if (state.isScheduled) {
          // Afficher la date sélectionnée ou le bouton de sélection
          val context = LocalContext.current
          val calendar = remember { Calendar.getInstance() }

          if (state.scheduledAt != null) {
              val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE) }
              Text(
                  text = "Match planifié : ${fmt.format(Date(state.scheduledAt))}",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.primary
              )
          }

          TextButton(
              onClick = {
                  DatePickerDialog(
                      context,
                      { _, year, month, day ->
                          calendar.set(year, month, day)
                          TimePickerDialog(
                              context,
                              { _, hour, minute ->
                                  calendar.set(Calendar.HOUR_OF_DAY, hour)
                                  calendar.set(Calendar.MINUTE, minute)
                                  calendar.set(Calendar.SECOND, 0)
                                  val selected = calendar.timeInMillis
                                  if (selected > System.currentTimeMillis()) {
                                      viewModel.onScheduledAtChanged(selected)
                                  }
                              },
                              calendar.get(Calendar.HOUR_OF_DAY),
                              calendar.get(Calendar.MINUTE),
                              true
                          ).show()
                      },
                      calendar.get(Calendar.YEAR),
                      calendar.get(Calendar.MONTH),
                      calendar.get(Calendar.DAY_OF_MONTH)
                  ).apply {
                      datePicker.minDate = System.currentTimeMillis() + 60_000L
                  }.show()
              }
          ) {
              Text(if (state.scheduledAt != null) "Changer la date/heure" else "Choisir la date/heure")
          }
      }
      ```
    - Modifier le bouton "Démarrer le match" :
      ```kotlin
      Button(
          onClick = viewModel::startMatch,
          enabled = state.canStartMatch,
          modifier = Modifier.fillMaxWidth()
      ) {
          Text(if (state.isScheduled) "Planifier le match" else "Démarrer le match")
      }
      ```
    - Gérer `NewMatchSideEffect.SessionPlanned` dans `collectSideEffect` → naviguer vers historique ou afficher confirmation

- [x] **T12 — Supprimer une session planifiée depuis `SessionDetailScreen`** (AC: 1)
  - [x] T12.1 Dans `android/feature/history/src/main/kotlin/com/secondserve/feature/history/SessionDetailViewModel.kt` :
    - Injecter `NotificationScheduler` (pour appel explicite si besoin) — non, la suppression passe par `SessionRepositoryImpl.deleteSession()` qui appelle `cancelPreMatchReminder()` en interne.
    - Ajouter :
      ```kotlin
      fun deleteSession() = intent {
          val s = (state as? SessionDetailUiState.Content) ?: return@intent
          when (sessionRepository.deleteSession(s.session.id)) {
              is AppResult.Success -> postSideEffect(SessionDetailSideEffect.SessionDeleted)
              is AppResult.Error -> { /* log */ }
              AppResult.Loading -> {}
          }
      }
      ```
    - Modifier la signature de `container` pour autoriser les side effects : changer `ContainerHost<SessionDetailUiState, Nothing>` → `ContainerHost<SessionDetailUiState, SessionDetailSideEffect>` et ajouter :
      ```kotlin
      sealed class SessionDetailSideEffect {
          object SessionDeleted : SessionDetailSideEffect()
      }
      ```
  - [x] T12.2 Dans `android/feature/history/src/main/kotlin/com/secondserve/feature/history/SessionDetailScreen.kt` :
    - Collecter le side effect `SessionDeleted` → appeler `onNavigateBack()`
    - Si `session.status == SessionStatus.PLANNED` : afficher un bouton "Annuler ce match planifié" (OutlinedButton rouge) qui appelle `viewModel.deleteSession()`
    - Ajouter confirmation `AlertDialog` avant la suppression

---

### BLOC E — VPS : APScheduler + endpoint

- [x] **T13 — Ajouter APScheduler à `pyproject.toml`** (AC: 1)
  - [x] T13.1 Dans `backend/pyproject.toml`, ajouter dans `dependencies` :
    ```toml
    "apscheduler>=3.10.0",
    ```
    Puis exécuter `uv sync` sur le VPS pour installer la dépendance.

- [x] **T14 — Alembic migration VPS** (AC: 1)
  - [x] T14.1 Créer `backend/alembic/versions/f6a7b8c9d0e1_add_scheduled_at_and_pending_notifications.py` :
    ```python
    """add scheduled_at to sessions and pending_notifications table

    Revision ID: f6a7b8c9d0e1
    Revises: e5f6a7b8c9d0
    Create Date: 2026-06-23
    """
    from alembic import op
    import sqlalchemy as sa

    revision = 'f6a7b8c9d0e1'
    down_revision = 'e5f6a7b8c9d0'
    branch_labels = None
    depends_on = None


    def upgrade() -> None:
        op.add_column("sessions", sa.Column("scheduled_at", sa.Integer(), nullable=True))

        op.create_table(
            "pending_notifications",
            sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
            sa.Column(
                "session_id",
                sa.Integer(),
                sa.ForeignKey("sessions.id", ondelete="CASCADE"),
                nullable=False,
                unique=True
            ),
            sa.Column("content", sa.String(), nullable=False),
            sa.Column("generated_at", sa.Integer(), nullable=False),
            sa.Column("expires_at", sa.Integer(), nullable=False),
        )
        op.create_index("idx_pending_notif_session", "pending_notifications", ["session_id"])


    def downgrade() -> None:
        op.drop_index("idx_pending_notif_session")
        op.drop_table("pending_notifications")
        op.drop_column("sessions", "scheduled_at")
    ```

- [x] **T15 — Mettre à jour le modèle VPS `SessionModel`** (AC: 1)
  - [x] T15.1 Dans `backend/app/features/sessions/models.py`, ajouter :
    ```python
    scheduled_at = Column(Integer, nullable=True)
    ```
  - [x] T15.2 Dans `backend/app/features/sync/schemas.py`, ajouter dans `SyncSessionDto` :
    ```python
    scheduled_at: Optional[int] = None
    ```
  - [x] T15.3 Dans `backend/app/features/sync/service.py`, ajouter `scheduled_at=dto.scheduled_at` dans le bloc `if existing is None:` du `_upsert_session()`.

- [x] **T16 — Modèle et schémas `PendingNotification`** (AC: 1)
  - [x] T16.1 Créer `backend/app/features/notifications/models.py` :
    ```python
    from sqlalchemy import Column, Integer, String, ForeignKey
    from app.core.database import Base


    class PendingNotificationModel(Base):
        __tablename__ = "pending_notifications"

        id = Column(Integer, primary_key=True, autoincrement=True)
        session_id = Column(Integer, ForeignKey("sessions.id", ondelete="CASCADE"), nullable=False, unique=True)
        content = Column(String, nullable=False)
        generated_at = Column(Integer, nullable=False)
        expires_at = Column(Integer, nullable=False)
    ```
  - [x] T16.2 Créer `backend/app/features/notifications/schemas.py` :
    ```python
    from pydantic import BaseModel
    from typing import Optional


    class PendingNotificationResponse(BaseModel):
        content: str


    class NotFoundResponse(BaseModel):
        detail: str
    ```

- [x] **T17 — Service de génération de contenu pré-match** (AC: 1)
  - [x] T17.1 Créer `backend/app/features/notifications/service.py` :
    ```python
    import logging
    import time
    from sqlalchemy import select
    from sqlalchemy.ext.asyncio import AsyncSession

    from app.features.notifications.models import PendingNotificationModel
    from app.features.sessions.models import SessionModel
    from app.features.coaching import mistral_client

    logger = logging.getLogger(__name__)

    LOOKAHEAD_MIN_SEC = 90 * 60       # 1h30 avant le match
    LOOKAHEAD_MAX_SEC = 4 * 60 * 60   # 4h avant le match
    CONTENT_TTL_SEC = 3 * 60 * 60     # contenu valide 3h


    async def generate_pending_for_upcoming(db: AsyncSession, api_key: str) -> int:
        """
        APScheduler job : génère le contenu coaching pré-match pour les sessions
        planifiées dans la fenêtre [now+1h30, now+4h].
        """
        now_ms = int(time.time() * 1000)
        window_min = now_ms + LOOKAHEAD_MIN_SEC * 1000
        window_max = now_ms + LOOKAHEAD_MAX_SEC * 1000

        result = await db.execute(
            select(SessionModel).where(
                SessionModel.scheduled_at.isnot(None),
                SessionModel.scheduled_at >= window_min,
                SessionModel.scheduled_at <= window_max,
                SessionModel.status == "PLANNED",
            )
        )
        sessions = result.scalars().all()

        generated = 0
        for session in sessions:
            existing = await db.execute(
                select(PendingNotificationModel).where(
                    PendingNotificationModel.session_id == session.id
                )
            )
            if existing.scalar_one_or_none() is not None:
                continue  # déjà généré

            try:
                prompt = _build_prompt(session)
                content = await mistral_client.generate(prompt, api_key)
                pending = PendingNotificationModel(
                    session_id=session.id,
                    content=content,
                    generated_at=now_ms,
                    expires_at=now_ms + CONTENT_TTL_SEC * 1000,
                )
                db.add(pending)
                generated += 1
                logger.info("APScheduler: contenu pré-match généré pour session_id=%d", session.id)
            except Exception as exc:
                logger.error("APScheduler: erreur génération session %d: %s", session.id, exc)

        await db.flush()
        return generated


    def _build_prompt(session: SessionModel) -> str:
        parts = [
            f"Surface : {session.surface}",
            f"Format : {session.match_format}",
        ]
        if session.opponent:
            parts.append(f"Adversaire : {session.opponent}")
        context = ", ".join(parts)
        return (
            f"En tant que coach tennis IA, génère un conseil de préparation mentale et tactique "
            f"avant le match ({context}). "
            f"Sois concis (2-3 phrases), actionnable, et personnalisé selon le contexte fourni. "
            f"Pas de formule de politesse."
        )
    ```

- [x] **T18 — Scheduler APScheduler intégré à FastAPI** (AC: 1)
  - [x] T18.1 Créer `backend/app/features/notifications/scheduler.py` :
    ```python
    import logging
    from apscheduler.schedulers.asyncio import AsyncIOScheduler
    from app.core.database import AsyncSessionLocal
    from app.core.config import settings
    from app.features.notifications.service import generate_pending_for_upcoming

    logger = logging.getLogger(__name__)

    _scheduler: AsyncIOScheduler | None = None


    async def _run_job() -> None:
        async with AsyncSessionLocal() as db:
            try:
                count = await generate_pending_for_upcoming(db, settings.mistral_api_key)
                await db.commit()
                if count:
                    logger.info("APScheduler job: %d notifications pré-match générées", count)
            except Exception as exc:
                await db.rollback()
                logger.error("APScheduler job: erreur: %s", exc)


    def start_scheduler() -> None:
        global _scheduler
        _scheduler = AsyncIOScheduler()
        _scheduler.add_job(_run_job, "interval", minutes=30, id="pre_match_reminder")
        _scheduler.start()
        logger.info("APScheduler démarré (intervalle 30 min)")


    def stop_scheduler() -> None:
        global _scheduler
        if _scheduler and _scheduler.running:
            _scheduler.shutdown(wait=False)
            logger.info("APScheduler arrêté")
    ```
  - [x] T18.2 Dans `backend/app/main.py`, ajouter les import et hooks `startup`/`shutdown` :
    ```python
    from app.features.notifications.scheduler import start_scheduler, stop_scheduler

    @app.on_event("startup")
    async def startup_event() -> None:
        from app.core.security import JWTManager
        JWTManager(settings.jwt_secret)
        start_scheduler()

    @app.on_event("shutdown")
    async def shutdown_event() -> None:
        stop_scheduler()
    ```
    Note : remplacer l'ancien `startup_validation` par `startup_event` (ou fusionner les deux fonctions). FastAPI n'autorise pas deux fonctions `@app.on_event("startup")`.

  - [x] T18.3 Vérifier que `async_session_factory` est exposé dans `backend/app/core/database.py`. Si absent, l'ajouter (pattern : `async_session_factory = async_sessionmaker(engine, expire_on_commit=False)`).

- [x] **T19 — Endpoint `GET /api/v1/notifications/pending`** (AC: 1)
  - [x] T19.1 Dans `backend/app/api/v1/notifications.py` :
    ```python
    from fastapi import APIRouter, Depends, HTTPException, Query
    from sqlalchemy import select
    from sqlalchemy.ext.asyncio import AsyncSession

    from app.core.database import get_db
    from app.core.security import verify_jwt
    from app.features.notifications.models import PendingNotificationModel
    from app.features.notifications.schemas import PendingNotificationResponse

    router = APIRouter()


    @router.get("/pending", response_model=PendingNotificationResponse)
    async def get_pending_notification(
        session_id: int = Query(..., description="ID de la session"),
        db: AsyncSession = Depends(get_db),
        _: str = Depends(verify_jwt),
    ) -> PendingNotificationResponse:
        result = await db.execute(
            select(PendingNotificationModel).where(
                PendingNotificationModel.session_id == session_id
            )
        )
        pending = result.scalar_one_or_none()
        if pending is None:
            raise HTTPException(status_code=404, detail="Aucune notification pré-match générée pour cette session")
        return PendingNotificationResponse(content=pending.content)
    ```

- [x] **T20 — Enregistrer `PendingNotificationModel` dans Alembic / imports**
  - [x] T20.1 S'assurer que `PendingNotificationModel` est importé dans `backend/alembic/env.py` (ou dans le fichier `Base` target) pour que les migrations la détectent. Chercher le pattern existant d'import dans `alembic/env.py`.

---

### BLOC F — Tests

- [x] **T21 — Tests `PreMatchReminderWorker`** (AC: 1)
  - [x] T21.1 Créer `android/data/src/test/kotlin/com/secondserve/data/worker/PreMatchReminderWorkerTest.kt`
    - Pattern identique à `NotificationWorkerTest.kt` (MockK + JUnit 5 + `runTest`)
    - Mock : `VpsApiService`, `PlayerProfileDao`, `WorkAxisDao`, `PlayerDataStore`
    - **4 cas de test :**
      1. `doWork_whenVpsSuccess_postsNotificationWithContent` — `vpsApiService.getPendingNotification` retourne `PendingNotificationResponse("conseil")` → contenu = "conseil"
      2. `doWork_whenVpsThrows_usesFallbackWithAxis` — `vpsApiService` throw `IOException`, axes = listOf("Revers croisé") → fallback contient "Revers croisé"
      3. `doWork_whenVpsThrowsAndNoData_usesGenericFallback` — VPS throw, profile=null, axes=empty → message générique non null
      4. `doWork_whenSessionIdMissing_returnsSuccessImmediately` — `inputData.getLong(KEY_SESSION_ID, -1L) == -1L` → `Result.success()` sans appel VPS

- [x] **T22 — Test VPS `GET /notifications/pending`** (AC: 1)
  - [x] T22.1 Créer `backend/tests/unit/test_notifications.py` :
    ```python
    import pytest
    from unittest.mock import AsyncMock, patch
    from fastapi.testclient import TestClient
    from app.main import app

    # Tests basiques : 200 avec contenu / 404 si absent
    # Pattern identique aux tests existants (ex: tests/unit/test_coaching.py)
    ```
    - Vérifier pattern de test existant avant d'écrire (ex: `tests/unit/test_coaching.py`)
    - **2 cas :**
      1. `test_get_pending_returns_content` — insérer une `PendingNotificationModel` en DB → GET retourne le contenu
      2. `test_get_pending_returns_404_when_absent` — GET sans entrée DB → HTTP 404

---

## Dev Notes

### Architecture — Fil conducteur de la feature

```
Utilisateur (NewMatchScreen)
  → NewMatchViewModel.startMatch() [PLANNED status]
    → SessionRepositoryImpl.createSession()  ← sync queue si scheduled_at
      → SyncWorker  →  VPS: sessions.scheduled_at
    → NotificationSchedulerImpl.schedulePreMatchReminder(sessionId, scheduled_at - 2h)
      → OneTimeWorkRequest<PreMatchReminderWorker> (unique: "pre_match_reminder_$sessionId")

VPS (toutes les 30 min):
  APScheduler._run_job()
    → generate_pending_for_upcoming()
      → scan sessions WHERE scheduled_at BETWEEN now+90m AND now+4h AND status='PLANNED'
      → mistral_client.generate(prompt)
      → INSERT INTO pending_notifications

Android (2h avant le match):
  PreMatchReminderWorker.doWork()
    → GET /api/v1/notifications/pending?session_id=<id>
    → si 200: postNotification(content)
    → si 404 / réseau: buildFallback() + postNotification(fallback)

Suppression:
  SessionDetailViewModel.deleteSession()
    → SessionRepositoryImpl.deleteSession(sessionId)
      → SessionDao.deleteById(id)
      → NotificationSchedulerImpl.cancelPreMatchReminder(sessionId)
```

### CRITIQUE — Room version 11 obligatoire

La table `sessions` passe de la version 10 à 11 avec l'ajout de la colonne `scheduled_at INTEGER` (nullable). **Sans migration, Room crashe au démarrage.** Vérifier que le builder Room dans `DataModule.kt` (ou le fichier qui configure `Room.databaseBuilder(...)`) inclut `addMigrations(..., MIGRATION_10_11)`.

### CRITIQUE — `AsyncSessionLocal` sur VPS (pas `Depends(get_db)`)

APScheduler appelle des fonctions async hors du contexte d'une requête FastAPI. Il ne peut pas utiliser `Depends(get_db)`. Il faut utiliser `AsyncSessionLocal` (déjà défini dans `backend/app/core/database.py` : `AsyncSessionLocal = async_sessionmaker(engine, expire_on_commit=False)`). Import : `from app.core.database import AsyncSessionLocal`.

### CRITIQUE — Ne pas créer deux `@app.on_event("startup")`

`main.py` a déjà un `startup_validation`. **Fusionner** en une seule fonction `startup_event` qui fait les deux (validation JWT + démarrage APScheduler). Deux handlers `startup` distincts compilent mais le second écrase silencieusement le premier en FastAPI.

### Dépendance modules Android — INVARIANT

- `PreMatchReminderWorker` est dans `:data/worker/` → peut injecter `VpsApiService`, `PlayerProfileDao`, `WorkAxisDao`, `PlayerDataStore` directement
- `NewMatchViewModel` est dans `:feature:match` → injecte `NotificationScheduler` (interface domain) **uniquement** — ne jamais importer depuis `:data` dans un ViewModel feature
- `SessionRepositoryImpl` est dans `:data/repository/` → peut injecter `NotificationScheduler` directement (interface dans `:domain`, implémentation dans `:data`, pas de cycle)

### `@HiltWorker` + `@AssistedInject` — OBLIGATOIRE

`PreMatchReminderWorker` **doit** utiliser `@HiltWorker` + `@AssistedInject` exactement comme `NotificationWorker` et `SyncWorker`. Sans ça, Hilt ne peut pas injecter les dépendances. Le pattern est :
```kotlin
@HiltWorker
class PreMatchReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    // ... dépendances normales
) : CoroutineWorker(context, params)
```

### `ExistingWorkPolicy.REPLACE` pour OneTimeWorkRequest

Utiliser `ExistingWorkPolicy.REPLACE` pour `enqueueUniqueWork()` dans `schedulePreMatchReminder()`. Si l'utilisateur modifie la date du match (non prévu dans cette story, mais possible via une correction), le worker est remplacé proprement.

### Canal `coaching_notifications` — existant depuis 6.1

Ne PAS recréer le canal. Il a été créé dans `SecondServeApp.onCreate()` en 6.1. `PreMatchReminderWorker.CHANNEL_ID = "coaching_notifications"` doit être identique à `NotificationWorker.CHANNEL_ID`.

### Conflit `NOTIFICATION_ID`

- `NotificationWorker.NOTIFICATION_ID = 1001` (conseil du jour)
- `PreMatchReminderWorker.NOTIFICATION_ID = 1002` (rappel pré-match)
- Ces IDs distincts permettent aux deux notifications de coexister sans s'écraser

### `DatePickerDialog` + `TimePickerDialog` — Android legacy OK

`DatePickerDialog` et `TimePickerDialog` sont des API Android legacy (non Compose), mais elles sont stables et recommandées dans les guides officiels Android pour les projets Material3. L'alternative `DatePickerDialog` de Material3 Compose est en beta — à éviter. Utiliser `LocalContext.current` pour le context.

### `minDate` sur `DatePickerDialog`

Ajouter `datePicker.minDate = System.currentTimeMillis() + 60_000L` avant d'appeler `.show()` pour empêcher la sélection d'une date/heure dans le passé.

### VPS — APScheduler `AsyncIOScheduler`

Utiliser `AsyncIOScheduler` (et non `BackgroundScheduler`) car FastAPI tourne sur un event loop asyncio. `BackgroundScheduler` créerait un thread séparé incompatible avec les sessions SQLAlchemy async.

### VPS — Fenêtre de génération [1h30 → 4h avant le match]

Le worker Android se déclenche 2h avant le match. APScheduler tourne toutes les 30 min. Avec une fenêtre de détection de 1h30 à 4h, on garantit qu'APScheduler passe au moins une fois avant que le worker se déclenche (cas défavorable : APScheduler passe à T-2h05, génère, worker se déclenche à T-2h et trouve le contenu ✓).

### VPS — Alembic `down_revision`

`down_revision` de la nouvelle migration doit pointer sur `'e5f6a7b8c9d0'` (la migration précédente). Vérifier la dernière entrée dans `alembic/versions/`.

### Pattern existant `toSyncDto()` — champ manquant `score_text`

Note : `Session.toSyncDto()` dans `Mappers.kt` n'inclut pas `scoreText` (pré-existant, non lié à cette story). Ne pas corriger dans cette story sauf si ça bloque la compilation.

### Orbit MVI dans `SessionDetailViewModel`

`SessionDetailViewModel` utilise actuellement `ContainerHost<SessionDetailUiState, Nothing>`. Pour ajouter le side effect `SessionDeleted`, changer `Nothing` → `SessionDetailSideEffect`. Définir `sealed class SessionDetailSideEffect` dans le même fichier ou dans un fichier séparé.

### Pas de `score_text` dans `SyncSessionDto` côté VPS

`SyncSessionDto` côté VPS n'a pas `score_text` non plus (pré-existant). Dans cette story, on ajoute `scheduled_at` uniquement. Ne pas corriger les omissions pré-existantes.

### Learnings story 6.1 à réappliquer

- `@VpsMistralEngine` qualifier : import depuis `com.secondserve.core.ai.di.VpsMistralEngine` — `PreMatchReminderWorker` n'en a PAS besoin (il utilise `VpsApiService`, pas `InferenceEngine`)
- `try-catch` obligatoire autour de chaque appel DAO dans un worker
- `@SuppressLint("MissingPermission")` → à éviter : utiliser `checkSelfPermission()` + try-catch `SecurityException`
- `cancelUniqueWork("pre_match_reminder_$sessionId")` est un no-op si le work n'existe pas — sûr à appeler sans condition

### Migration Room — pattern `addMigrations`

Chercher `Room.databaseBuilder` dans le projet Android (probablement dans `DataModule.kt`). Ajouter `MIGRATION_10_11` à la liste existante. Le pattern est :
```kotlin
Room.databaseBuilder(context, SecondServeDatabase::class.java, SecondServeDatabase.DB_NAME)
    .addMigrations(
        SecondServeDatabase.MIGRATION_1_2,
        // ... migrations existantes ...
        SecondServeDatabase.MIGRATION_10_11  // NOUVEAU
    )
    .build()
```

## Dev Agent Record

### Completion Notes

_À remplir par le dev agent._

### Debug Log

_À remplir si des blocages sont rencontrés._

## File List

### Android — Fichiers modifiés
- `android/domain/src/main/kotlin/com/secondserve/domain/model/Session.kt`
- `android/domain/src/main/kotlin/com/secondserve/domain/notification/NotificationScheduler.kt`
- `android/domain/src/main/kotlin/com/secondserve/domain/repository/SessionRepository.kt`
- `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/SessionEntity.kt`
- `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/Mappers.kt`
- `android/data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt`
- `android/data/src/main/kotlin/com/secondserve/data/local/dao/SessionDao.kt`
- `android/data/src/main/kotlin/com/secondserve/data/remote/api/VpsApiService.kt`
- `android/data/src/main/kotlin/com/secondserve/data/remote/api/dto/SyncDto.kt`
- `android/data/src/main/kotlin/com/secondserve/data/worker/NotificationSchedulerImpl.kt`
- `android/data/src/main/kotlin/com/secondserve/data/repository/SessionRepositoryImpl.kt`
- `android/feature/match/src/main/kotlin/com/secondserve/feature/match/NewMatchViewModel.kt`
- `android/feature/match/src/main/kotlin/com/secondserve/feature/match/NewMatchScreen.kt`
- `android/feature/history/src/main/kotlin/com/secondserve/feature/history/SessionDetailViewModel.kt`
- `android/feature/history/src/main/kotlin/com/secondserve/feature/history/SessionDetailScreen.kt`

### Android — Fichiers nouveaux
- `android/data/src/main/kotlin/com/secondserve/data/worker/PreMatchReminderWorker.kt`
- `android/data/src/main/kotlin/com/secondserve/data/remote/api/dto/NotificationDto.kt`
- `android/data/src/test/kotlin/com/secondserve/data/worker/PreMatchReminderWorkerTest.kt`

### VPS — Fichiers modifiés
- `backend/pyproject.toml`
- `backend/app/features/sessions/models.py`
- `backend/app/features/sync/schemas.py`
- `backend/app/features/sync/service.py`
- `backend/app/main.py`
- `backend/app/api/v1/notifications.py`

### VPS — Fichiers nouveaux
- `backend/alembic/versions/f6a7b8c9d0e1_add_scheduled_at_and_pending_notifications.py`
- `backend/app/features/notifications/models.py`
- `backend/app/features/notifications/schemas.py`
- `backend/app/features/notifications/service.py`
- `backend/app/features/notifications/scheduler.py`
- `backend/tests/unit/test_notifications.py`

## Change Log
