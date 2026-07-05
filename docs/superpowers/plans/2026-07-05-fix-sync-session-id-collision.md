# Fix Sync Session ID Collision Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop the backend from reusing the phone's local Room row ID as the server's primary key when syncing sessions, so a session pushed from the phone can never silently overwrite or get silently dropped in favor of an unrelated session created directly through the web app.

**Architecture:** Add a nullable `client_id` column to the backend `sessions` table. `SyncService` will look up "have I already synced this phone session before?" by `client_id` instead of by `id`. New rows let the database assign their own primary key (`id`) exactly like sessions created via the web `POST /api/v1/sessions` endpoint already do; only `client_id` is set to the value the phone provided. This fully decouples the two independent ID sequences (Room's local autoincrement vs. the backend's own autoincrement) that currently collide.

**Tech Stack:** FastAPI, SQLAlchemy (async, SQLite via aiosqlite), Alembic, pytest + pytest-asyncio + httpx `AsyncClient`.

## Global Constraints

- Backend tests run with `pytest` from `/root/SecondServe/backend` (venv already set up at `backend/.venv`).
- Every new/changed test must pass; do not modify existing test expectations except where explicitly instructed below.
- No changes to the Android app are required for this plan — the phone already sends `client_id` in every sync push payload (`android/data/.../worker/SyncWorker.kt`), this plan only changes how the backend interprets it. Do not touch Android code in this plan.
- Do not add authentication/authorization scoping (e.g. per-user filtering) — out of scope, the app is single-user and JWTs carry no subject claim; noticing this is not a reason to change it here.

---

## Background (read before starting)

Today, `backend/app/features/sync/service.py::SyncService._upsert_session` does:

```python
result = await self.db.execute(
    select(SessionModel).where(SessionModel.id == dto.client_id)
)
existing = result.scalar_one_or_none()
if existing is None:
    model = SessionModel(id=dto.client_id, ...)
```

`dto.client_id` is the **phone's local Room autoincrement ID** (`SessionEntity.id` in `android/data/.../local/db/entity/SessionEntity.kt`), which starts at 1 and increments independently of the backend's own `sessions.id` sequence (also autoincrement, starting at 1, used by `POST /api/v1/sessions` in `backend/app/features/sessions/repository.py::SessionRepository.create`).

Because both sequences start at 1 and increment independently, the first session created on the phone (`client_id=1`) will collide with the first session ever created directly via the web app (server `id=1`) the moment the phone syncs. The existing last-write-wins logic (`if dto.updated_at >= existing.updated_at`) then either overwrites the web session's fields with the phone session's fields, or silently no-ops if the web session happens to have a newer `updated_at` — in both cases the phone's session is never visible as its own distinct row, and the web-created session may be corrupted. This is the confirmed root cause of sessions "disappearing" between the phone and the web.

The fix: track the phone's local ID in a separate `client_id` column, and always let the backend's own primary key sequence assign `id`. Existing tests `test_sync_push_idempotence_double_send` and `test_sync_push_last_write_wins` in `backend/tests/integration/test_sync_api.py` encode the *intended* behavior for repeated pushes of the **same** phone session (same `client_id` twice → update in place, not duplicate) — that behavior must be preserved, just keyed off the new column instead of the primary key.

---

## File Structure

- Modify: `backend/app/features/sessions/models.py` — add `client_id` column to `SessionModel`.
- Create: `backend/alembic/versions/<hash>_add_client_id_to_sessions.py` — migration adding the column + index.
- Modify: `backend/app/features/sync/service.py` — `_upsert_session` and `_delete_session` look up by `client_id`, not `id`.
- Modify: `backend/tests/integration/test_sync_api.py` — add the regression test proving the collision is fixed.

---

### Task 1: Add `client_id` column to the backend session model + migration

**Files:**
- Modify: `backend/app/features/sessions/models.py`
- Create: `backend/alembic/versions/c1d2e3f4a5b6_add_client_id_to_sessions.py`
- Test: `backend/tests/integration/test_sync_api.py` (new test added in Task 2, after the model exists)

**Interfaces:**
- Produces: `SessionModel.client_id: int | None` — a nullable column, indexed, used by Task 2's lookup logic.

- [ ] **Step 1: Add the column to the SQLAlchemy model**

Edit `backend/app/features/sessions/models.py`, adding `client_id` right after `id`:

```python
from sqlalchemy import Column, Integer, String, Text, Index
from app.core.database import Base


class SessionModel(Base):
    __tablename__ = "sessions"

    id = Column(Integer, primary_key=True, autoincrement=True)
    client_id = Column(Integer, nullable=True)
    surface = Column(String, nullable=False)
    match_format = Column(String, nullable=False)
    third_set_rule = Column(String, nullable=False)
    opponent = Column(String, nullable=True)
    competition_type = Column(String, nullable=True)
    tournament = Column(String, nullable=True)
    status = Column(String, nullable=False, default="ACTIVE")
    session_type = Column(String, nullable=False, default="MATCH")
    result = Column(String, nullable=True)
    score_text = Column(String, nullable=True)
    score_seed_json = Column(Text, nullable=True)
    feeling_rating = Column(Integer, nullable=True)
    feeling_comment = Column(String, nullable=True)
    created_at = Column(Integer, nullable=False)
    updated_at = Column(Integer, nullable=False)
    scheduled_at = Column(Integer, nullable=True)
    first_serve_percent_self = Column(Integer, nullable=True)
    first_serve_percent_opponent = Column(Integer, nullable=True)
    winners_self = Column(Integer, nullable=True)
    winners_opponent = Column(Integer, nullable=True)

    __table_args__ = (
        Index("idx_sessions_client_id", "client_id"),
    )
```

**Why nullable and not unique:** sessions created directly via the web (`POST /api/v1/sessions`) never have a `client_id` (it stays `NULL`); only phone-synced sessions do. Not unique because SQLite allows multiple `NULL`s in a non-unique index anyway, and a real uniqueness constraint on `client_id` would incorrectly reject a second *phone* (or a reinstalled app with a fresh Room DB) that legitimately starts its own local IDs at 1 again — this plan intentionally keeps a single-writer assumption (documented in the Known Limitation note in Task 4) rather than solving multi-device `client_id` collisions, which is out of scope.

- [ ] **Step 2: Write the Alembic migration**

Create `backend/alembic/versions/c1d2e3f4a5b6_add_client_id_to_sessions.py`:

```python
"""add client_id column to sessions table

Revision ID: c1d2e3f4a5b6
Revises: b4c5d6e7f8a9
Create Date: 2026-07-05
"""
from alembic import op
import sqlalchemy as sa

revision = 'c1d2e3f4a5b6'
down_revision = 'b4c5d6e7f8a9'
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("sessions", sa.Column("client_id", sa.Integer(), nullable=True))
    op.create_index("idx_sessions_client_id", "sessions", ["client_id"])


def downgrade() -> None:
    op.drop_index("idx_sessions_client_id", table_name="sessions")
    op.drop_column("sessions", "client_id")
```

- [ ] **Step 3: Run the migration against the real backend DB**

```bash
cd /root/SecondServe/backend && source .venv/bin/activate && alembic upgrade head
```

Expected: output ends with `Running upgrade b4c5d6e7f8a9 -> c1d2e3f4a5b6, add client_id column to sessions table`, no errors.

- [ ] **Step 4: Commit**

```bash
git add backend/app/features/sessions/models.py backend/alembic/versions/c1d2e3f4a5b6_add_client_id_to_sessions.py
git commit -m "feat(backend): add client_id column to sessions table"
```

---

### Task 2: Write the failing regression test proving the collision

**Files:**
- Modify: `backend/tests/integration/test_sync_api.py`

**Interfaces:**
- Consumes: `client` fixture from `backend/tests/conftest.py`, `make_token()`/`auth()`/`session_dto()` helpers already defined at the top of `test_sync_api.py`.

- [ ] **Step 1: Add the test**

Append to `backend/tests/integration/test_sync_api.py`:

```python
@pytest.mark.asyncio
async def test_sync_push_does_not_collide_with_web_created_session(client):
    token = make_token()

    # Une session est créée directement via le web (comme le ferait un vrai utilisateur web),
    # elle obtient l'id=1 côté serveur en toute logique (première session de la base de test).
    web_response = await client.post(
        "/api/v1/sessions",
        json={
            "surface": "HARD",
            "match_format": "BEST_OF_3",
            "third_set_rule": "FULL_ADVANTAGE",
            "opponent": "Session créée depuis le web",
            "created_at": 1_000_000,
        },
        headers=auth(token),
    )
    assert web_response.status_code == 201
    web_session_id = web_response.json()["id"]

    # Le téléphone pousse SA session locale n°1 (Room autoincrement recommence à 1,
    # indépendamment du compteur serveur) — même client_id que l'id serveur ci-dessus.
    push_response = await client.post(
        "/api/v1/sync/push",
        json={"sessions": [session_dto(client_id=1, status="COMPLETED", result="VICTORY", updated_at=9_000_000)]},
        headers=auth(token),
    )
    assert push_response.status_code == 200
    assert push_response.json()["synced_sessions"] == 1

    # La session créée depuis le web ne doit PAS avoir été écrasée par le push du téléphone.
    web_session_after = await client.patch(
        f"/api/v1/sessions/{web_session_id}",
        json={},
        headers=auth(token),
    )
    assert web_session_after.status_code == 200
    assert web_session_after.json()["opponent"] == "Session créée depuis le web"
    assert web_session_after.json()["surface"] == "HARD"

    # La session du téléphone doit exister quelque part, distincte de celle du web.
    all_sessions = await client.get("/api/v1/sessions", headers=auth(token))
    assert all_sessions.status_code == 200
    items = all_sessions.json()["items"]
    assert len(items) == 2, "le push du téléphone doit créer une 2e session distincte, pas fusionner avec celle du web"
    phone_session = next(s for s in items if s["id"] != web_session_id)
    assert phone_session["status"] == "COMPLETED"
    assert phone_session["result"] == "VICTORY"
```

- [ ] **Step 2: Run it and confirm it fails against current code**

```bash
cd /root/SecondServe/backend && source .venv/bin/activate && python -m pytest tests/integration/test_sync_api.py::test_sync_push_does_not_collide_with_web_created_session -v
```

Expected: **FAIL**. With the current `_upsert_session`, the phone's push (`client_id=1`) matches `SessionModel.id == 1` (the web session) and overwrites it in place — so either the `len(items) == 2` assertion fails (only 1 session exists) or the `opponent`/`surface` assertions fail (the web session got overwritten).

- [ ] **Step 3: Commit the failing test on its own**

```bash
git add backend/tests/integration/test_sync_api.py
git commit -m "test(backend): add failing regression test for sync ID collision"
```

---

### Task 3: Fix `SyncService` to key off `client_id` instead of `id`

**Files:**
- Modify: `backend/app/features/sync/service.py`

**Interfaces:**
- Consumes: `SessionModel.client_id` (Task 1).

- [ ] **Step 1: Rewrite `_upsert_session` and `_delete_session`**

Replace the full contents of `backend/app/features/sync/service.py` with:

```python
import logging
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.features.sync.schemas import SyncPushRequest, SyncPushResponse
from app.features.sessions.models import SessionModel
from app.features.monitoring.events import emit_event

logger = logging.getLogger(__name__)


class SyncService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def push(self, request: SyncPushRequest) -> SyncPushResponse:
        synced = 0
        for session_dto in request.sessions:
            await self._upsert_session(session_dto)
            synced += 1
        for client_id in request.deleted_session_ids:
            await self._delete_session(client_id)
        await self.db.flush()
        logger.info("SyncService: %d sessions upserted, %d deleted", synced, len(request.deleted_session_ids))
        return SyncPushResponse(synced_sessions=synced)

    async def _get_by_client_id(self, client_id: int) -> SessionModel | None:
        result = await self.db.execute(
            select(SessionModel).where(SessionModel.client_id == client_id)
        )
        return result.scalar_one_or_none()

    async def _delete_session(self, client_id: int) -> None:
        existing = await self._get_by_client_id(client_id)
        if existing is not None:
            emit_event("match.ended", {"session_id": existing.id})
            await self.db.delete(existing)
            logger.info("SyncService: session client_id=%d (server id=%d) supprimée", client_id, existing.id)

    async def _upsert_session(self, dto) -> None:
        existing = await self._get_by_client_id(dto.client_id)
        if existing is None:
            model = SessionModel(
                client_id=dto.client_id,
                surface=dto.surface,
                match_format=dto.match_format,
                third_set_rule=dto.third_set_rule,
                opponent=dto.opponent,
                competition_type=dto.competition_type,
                tournament=dto.tournament,
                status=dto.status,
                session_type=dto.session_type,
                result=dto.result,
                score_text=dto.score_text,
                feeling_rating=dto.feeling_rating,
                feeling_comment=dto.feeling_comment,
                created_at=dto.created_at,
                updated_at=dto.updated_at,
                scheduled_at=dto.scheduled_at,
                first_serve_percent_self=dto.first_serve_percent_self,
                first_serve_percent_opponent=dto.first_serve_percent_opponent,
                winners_self=dto.winners_self,
                winners_opponent=dto.winners_opponent,
            )
            self.db.add(model)
        else:
            # last-write-wins sur updated_at (NFR-S4)
            if dto.updated_at >= existing.updated_at:
                existing.status = dto.status
                existing.result = dto.result
                existing.score_text = dto.score_text
                existing.feeling_rating = dto.feeling_rating
                existing.feeling_comment = dto.feeling_comment
                existing.updated_at = dto.updated_at
                existing.scheduled_at = dto.scheduled_at
                existing.first_serve_percent_self = dto.first_serve_percent_self
                existing.first_serve_percent_opponent = dto.first_serve_percent_opponent
                existing.winners_self = dto.winners_self
                existing.winners_opponent = dto.winners_opponent
```

Note the primary key `id` is never set explicitly on insert anymore — the database assigns it, exactly like `SessionRepository.create` already does for web-created sessions.

- [ ] **Step 2: Run the regression test from Task 2 and confirm it passes**

```bash
cd /root/SecondServe/backend && source .venv/bin/activate && python -m pytest tests/integration/test_sync_api.py::test_sync_push_does_not_collide_with_web_created_session -v
```

Expected: PASS.

- [ ] **Step 3: Run the full existing sync + sessions test suites to confirm no regressions**

```bash
cd /root/SecondServe/backend && source .venv/bin/activate && python -m pytest tests/integration/test_sync_api.py tests/integration/test_sessions_api.py tests/unit/test_session_service.py -v
```

Expected: ALL PASS, including the pre-existing `test_sync_push_idempotence_double_send` and `test_sync_push_last_write_wins` (same `client_id` pushed twice must still update the same row, not create a duplicate — this now works because the lookup is on `client_id`, which is unchanged across repeated pushes of the same phone session).

- [ ] **Step 4: Run the complete backend test suite**

```bash
cd /root/SecondServe/backend && source .venv/bin/activate && python -m pytest -v
```

Expected: ALL PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/app/features/sync/service.py
git commit -m "fix(backend): key sync upsert/delete on client_id, stop reusing it as primary key"
```

---

### Task 4: Deploy and document the known limitation

**Files:**
- Modify: `docs/design-system.md` — no change needed (not a UI change).
- No code changes in this task — operational + documentation only.

- [ ] **Step 1: Deploy the backend fix to the VPS**

This backend fix runs wherever the FastAPI app already runs (VPS, per [[project_vps]] memory). Restart the backend process so the new code + migration take effect:

```bash
cd /root/SecondServe/backend && source .venv/bin/activate && alembic upgrade head
```

Then restart whatever process manager runs the FastAPI app in production (check with `ss -tlnp` / `docker ps` / systemd unit — do not guess the restart command without confirming which one is actually used on this VPS).

- [ ] **Step 2: Note the known limitation for future work**

Add a short note to `backend/app/features/sync/service.py` module docstring (top of file, before imports):

```python
"""Session sync push/delete. Single-writer assumption: client_id is only
meaningful as long as exactly one device (the phone) pushes sessions. If a
second device ever pushes sessions independently (e.g. a tablet, or a
reinstalled app with a fresh local DB), its client_id sequence could again
collide with the phone's. Not handled here — see
docs/superpowers/plans/2026-07-05-fix-sync-session-id-collision.md."""
```

- [ ] **Step 3: Commit**

```bash
git add backend/app/features/sync/service.py
git commit -m "docs(backend): note single-writer client_id limitation"
```

- [ ] **Step 4: Manually inspect existing production data for prior corruption**

This bug has likely been live since the sync feature was first shipped, so some already-overwritten data may be unrecoverable (the last-write-wins overwrite has no undo log). Run this read-only query against the production DB to check for suspicious sessions worth a manual look (adjust the path if the production DB file lives elsewhere — confirm via `backend/app/core/config.py` / the deployed `.env`):

```bash
cd /root/SecondServe/backend && source .venv/bin/activate && python -c "
import asyncio
from sqlalchemy import select
from app.core.database import get_db_context  # adjust to however the prod session factory is exposed
from app.features.sessions.models import SessionModel

async def main():
    async with get_db_context() as db:
        result = await db.execute(select(SessionModel).order_by(SessionModel.id))
        for s in result.scalars().all():
            print(s.id, s.client_id, s.surface, s.opponent, s.status, s.created_at, s.updated_at)

asyncio.run(main())
"
```

Review the output with Benny: any two sessions whose `created_at`/`updated_at` look implausibly close together, or whose `opponent`/`surface` don't match what was actually played, are candidates for prior collisions. There is no automated way to reconstruct the original (overwritten) data — this step is for awareness, not repair.
