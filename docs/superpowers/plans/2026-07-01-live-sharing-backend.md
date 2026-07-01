# Partage de lien live — Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter au backend FastAPI existant une feature `live_sharing` qui génère un lien public non-devinable par match, reçoit les mises à jour de score en direct depuis l'app Android, et les diffuse en temps réel aux spectateurs via Server-Sent Events (SSE).

**Architecture:** Nouvelle feature backend suivant le pattern existant (`models.py` / `schemas.py` / `repository.py` / `service.py` + router `api/v1`). Un registre en mémoire (process unique uvicorn) diffuse les mises à jour aux abonnés SSE ; `score_snapshot` en base est la source de vérité pour le premier chargement et la résilience au redémarrage. Deux endpoints protégés par JWT (créer le lien, pousser le score) et deux endpoints publics (lire l'état courant, s'abonner au flux SSE).

**Tech Stack:** FastAPI, SQLAlchemy (async, aiosqlite), Alembic, Pydantic, APScheduler, pytest + pytest-asyncio + httpx (tests d'intégration existants).

## Global Constraints

- Convention JSON existante : tous les champs échangés avec l'app Android sont en `snake_case` (voir `backend/app/features/sync/schemas.py` et son pendant Kotlin `SyncDto.kt`).
- Un seul process uvicorn (pas de multi-worker) — le registre de diffusion SSE est en mémoire.
- Token de lien : `secrets.token_urlsafe(16)`, non-devinable.
- Rétention après fin de match : **48h** (`expires_at = now + 48h` quand `is_match_over` passe à `true`).
- Pas de scoping utilisateur (app mono-utilisateur, cohérent avec le reste du backend — voir `_bmad-output/implementation-artifacts/deferred-work.md`).
- Tests : suivre le pattern d'intégration existant (`backend/tests/integration/test_work_axes_api.py`), pas de tests unitaires isolés séparés dans ce projet.
- Head Alembic actuel : `d0e1f2a3b4c5` (`add_fine_match_stats_to_sessions`).

---

### Task 1: Couche domaine — modèle, migration, schémas, repository, broadcaster, service

**Files:**
- Create: `backend/app/features/live_sharing/__init__.py`
- Create: `backend/app/features/live_sharing/models.py`
- Create: `backend/app/features/live_sharing/schemas.py`
- Create: `backend/app/features/live_sharing/broadcast.py`
- Create: `backend/app/features/live_sharing/repository.py`
- Create: `backend/app/features/live_sharing/service.py`
- Create: `backend/alembic/versions/f1a2b3c4d5e6_add_match_shares_table.py`
- Test: `backend/tests/integration/test_live_sharing_service.py`

**Interfaces:**
- Produces: `MatchShareModel` (colonnes `id`, `token`, `session_id`, `created_at`, `expires_at`, `score_snapshot`) ; `MatchShareRepository(db).create/get_by_session/get_by_token/update_snapshot/delete_expired` ; `LiveSharingService(repository).create_share/push_score/get_snapshot` ; `broadcaster` (instance module-level de `ShareBroadcaster`) avec `subscribe(token)/unsubscribe(token, queue)/publish(token, snapshot)`.
- Consumes: `app.core.database.Base`, `app.core.config.settings`, `app.shared.exceptions.SecondServeException` (existants).

- [ ] **Step 1: Créer le package et le modèle SQLAlchemy**

`backend/app/features/live_sharing/__init__.py` (fichier vide).

`backend/app/features/live_sharing/models.py` :

```python
from sqlalchemy import Column, Integer, String, Text
from app.core.database import Base


class MatchShareModel(Base):
    __tablename__ = "match_shares"

    id = Column(Integer, primary_key=True, autoincrement=True)
    token = Column(String, nullable=False, unique=True, index=True)
    session_id = Column(Integer, nullable=False, unique=True, index=True)
    created_at = Column(Integer, nullable=False)  # epoch ms
    expires_at = Column(Integer, nullable=True)  # epoch ms, null tant que le match est en cours
    score_snapshot = Column(Text, nullable=True)  # JSON — dernier état connu (score + contexte)
```

- [ ] **Step 2: Créer la migration Alembic**

`backend/alembic/versions/f1a2b3c4d5e6_add_match_shares_table.py` :

```python
"""add match_shares table

Revision ID: f1a2b3c4d5e6
Revises: d0e1f2a3b4c5
Create Date: 2026-07-01
"""
from alembic import op
import sqlalchemy as sa

revision = 'f1a2b3c4d5e6'
down_revision = 'd0e1f2a3b4c5'
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        'match_shares',
        sa.Column('id', sa.Integer(), nullable=False, autoincrement=True),
        sa.Column('token', sa.String(), nullable=False),
        sa.Column('session_id', sa.Integer(), nullable=False),
        sa.Column('created_at', sa.Integer(), nullable=False),
        sa.Column('expires_at', sa.Integer(), nullable=True),
        sa.Column('score_snapshot', sa.Text(), nullable=True),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index('idx_match_shares_token', 'match_shares', ['token'], unique=True)
    op.create_index('idx_match_shares_session_id', 'match_shares', ['session_id'], unique=True)


def downgrade() -> None:
    op.drop_index('idx_match_shares_session_id', table_name='match_shares')
    op.drop_index('idx_match_shares_token', table_name='match_shares')
    op.drop_table('match_shares')
```

Vérifier que la migration s'applique et se révoque proprement sur une base locale :

Run: `cd backend && uv run alembic upgrade head && uv run alembic downgrade -1 && uv run alembic upgrade head`
Expected: les trois commandes s'exécutent sans erreur.

- [ ] **Step 3: Écrire les schémas Pydantic**

`current_set_point_log` correspond au « déroulé du set » du mockup public : une entrée par **point** remporté dans le set en cours (pas par jeu), reset à chaque set terminé. Cette donnée est produite par `TennisScoreEngine`/`MatchScore.currentSetPointLog` côté Android (voir plan Android, Task 1) — à ne pas confondre avec `MatchViewModel.state.currentSetGameLog`, qui est un log par **jeu** utilisé uniquement pour la barre de momentum affichée sur le téléphone et n'est pas transmis au backend.

`backend/app/features/live_sharing/schemas.py` :

```python
from typing import Literal, Optional
from pydantic import BaseModel


class SetResultSchema(BaseModel):
    games_a: int
    games_b: int


class CreateShareRequest(BaseModel):
    session_id: int


class CreateShareResponse(BaseModel):
    token: str
    url: str


class LiveScoreUpdateRequest(BaseModel):
    completed_sets: list[SetResultSchema] = []
    current_set_games_a: int
    current_set_games_b: int
    current_set_point_log: list[Literal["A", "B"]] = []
    current_game_points_a: str
    current_game_points_b: str
    tie_break_points_a: int
    tie_break_points_b: int
    is_tie_break: bool
    is_super_tie_break: bool
    is_match_over: bool
    match_winner: Optional[Literal["A", "B"]] = None
    player_a_name: str
    player_b_name: str
    surface: str
    tournament: Optional[str] = None
    competition_type: Optional[str] = None
    started_at: int


class LiveSnapshotResponse(BaseModel):
    status: Literal["WAITING", "LIVE", "ENDED"]
    completed_sets: list[SetResultSchema] = []
    current_set_games_a: int = 0
    current_set_games_b: int = 0
    current_set_point_log: list[Literal["A", "B"]] = []
    current_game_points_a: str = "ZERO"
    current_game_points_b: str = "ZERO"
    tie_break_points_a: int = 0
    tie_break_points_b: int = 0
    is_tie_break: bool = False
    is_super_tie_break: bool = False
    match_winner: Optional[Literal["A", "B"]] = None
    player_a_name: Optional[str] = None
    player_b_name: Optional[str] = None
    surface: Optional[str] = None
    tournament: Optional[str] = None
    competition_type: Optional[str] = None
    started_at: Optional[int] = None
```

- [ ] **Step 4: Écrire le broadcaster en mémoire**

`backend/app/features/live_sharing/broadcast.py` :

```python
import asyncio


class ShareBroadcaster:
    """Registre en mémoire (process unique) : diffuse chaque mise à jour de score
    à tous les abonnés SSE d'un token donné."""

    def __init__(self) -> None:
        self._subscribers: dict[str, list[asyncio.Queue]] = {}

    def subscribe(self, token: str) -> asyncio.Queue:
        queue: asyncio.Queue = asyncio.Queue()
        self._subscribers.setdefault(token, []).append(queue)
        return queue

    def unsubscribe(self, token: str, queue: asyncio.Queue) -> None:
        queues = self._subscribers.get(token)
        if not queues:
            return
        if queue in queues:
            queues.remove(queue)
        if not queues:
            self._subscribers.pop(token, None)

    def publish(self, token: str, snapshot: dict) -> None:
        for queue in self._subscribers.get(token, []):
            queue.put_nowait(snapshot)


broadcaster = ShareBroadcaster()
```

- [ ] **Step 5: Écrire le repository**

`backend/app/features/live_sharing/repository.py` :

```python
import secrets
import time
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.features.live_sharing.models import MatchShareModel


class MatchShareRepository:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_by_session(self, session_id: int) -> MatchShareModel | None:
        result = await self.db.execute(
            select(MatchShareModel).where(MatchShareModel.session_id == session_id)
        )
        return result.scalar_one_or_none()

    async def get_by_token(self, token: str) -> MatchShareModel | None:
        result = await self.db.execute(
            select(MatchShareModel).where(MatchShareModel.token == token)
        )
        return result.scalar_one_or_none()

    async def create(self, session_id: int) -> MatchShareModel:
        share = MatchShareModel(
            token=secrets.token_urlsafe(16),
            session_id=session_id,
            created_at=int(time.time() * 1000),
            expires_at=None,
            score_snapshot=None,
        )
        self.db.add(share)
        await self.db.flush()
        return share

    async def update_snapshot(
        self, share: MatchShareModel, snapshot_json: str, expires_at: int | None
    ) -> None:
        share.score_snapshot = snapshot_json
        share.expires_at = expires_at
        await self.db.flush()

    async def delete_expired(self, now_ms: int) -> int:
        result = await self.db.execute(
            select(MatchShareModel).where(
                MatchShareModel.expires_at.is_not(None),
                MatchShareModel.expires_at < now_ms,
            )
        )
        expired = list(result.scalars().all())
        for share in expired:
            await self.db.delete(share)
        await self.db.flush()
        return len(expired)
```

- [ ] **Step 6: Écrire le service**

`backend/app/features/live_sharing/service.py` :

```python
import json
import time
from app.core.config import settings
from app.features.live_sharing.broadcast import broadcaster
from app.features.live_sharing.repository import MatchShareRepository
from app.features.live_sharing.schemas import (
    CreateShareRequest,
    CreateShareResponse,
    LiveScoreUpdateRequest,
    LiveSnapshotResponse,
)
from app.shared.exceptions import SecondServeException

SHARE_RETENTION_MS_AFTER_MATCH = 48 * 60 * 60 * 1000


class LiveSharingService:
    def __init__(self, repository: MatchShareRepository):
        self.repository = repository

    async def create_share(self, request: CreateShareRequest) -> CreateShareResponse:
        share = await self.repository.get_by_session(request.session_id)
        if share is None:
            share = await self.repository.create(request.session_id)
        return CreateShareResponse(
            token=share.token,
            url=f"{settings.public_web_base_url}/live/{share.token}",
        )

    async def push_score(self, session_id: int, request: LiveScoreUpdateRequest) -> None:
        share = await self.repository.get_by_session(session_id)
        if share is None:
            return  # match non partagé : ignoré silencieusement
        snapshot = request.model_dump()
        expires_at = (
            int(time.time() * 1000) + SHARE_RETENTION_MS_AFTER_MATCH
            if request.is_match_over
            else None
        )
        await self.repository.update_snapshot(share, json.dumps(snapshot), expires_at)
        broadcaster.publish(share.token, snapshot)

    async def get_snapshot(self, token: str) -> LiveSnapshotResponse:
        share = await self.repository.get_by_token(token)
        if share is None:
            raise SecondServeException(
                error_code="SHARE_NOT_FOUND", message="Lien introuvable", status_code=404
            )
        now_ms = int(time.time() * 1000)
        if share.expires_at is not None and share.expires_at < now_ms:
            raise SecondServeException(
                error_code="SHARE_EXPIRED",
                message="Ce lien n'est plus disponible",
                status_code=410,
            )
        if share.score_snapshot is None:
            return LiveSnapshotResponse(status="WAITING")
        data = json.loads(share.score_snapshot)
        is_match_over = data.pop("is_match_over", False)
        status = "ENDED" if is_match_over else "LIVE"
        return LiveSnapshotResponse(status=status, **data)
```

- [ ] **Step 7: Ajouter `public_web_base_url` à la config**

Modifier `backend/app/core/config.py` — ajouter le champ dans `Settings` (après `google_client_id`, avant `monitor_db_url`) :

```python
    public_web_base_url: str = "http://localhost:3000"
```

- [ ] **Step 8: Écrire le test de la couche service**

`backend/tests/integration/test_live_sharing_service.py` :

```python
import time
import pytest
from app.features.live_sharing.repository import MatchShareRepository
from app.features.live_sharing.schemas import CreateShareRequest, LiveScoreUpdateRequest
from app.features.live_sharing.service import LiveSharingService
from app.shared.exceptions import SecondServeException


def make_score_update(is_match_over: bool = False) -> LiveScoreUpdateRequest:
    return LiveScoreUpdateRequest(
        completed_sets=[],
        current_set_games_a=1,
        current_set_games_b=0,
        current_set_point_log=["A"],
        current_game_points_a="THIRTY",
        current_game_points_b="FIFTEEN",
        tie_break_points_a=0,
        tie_break_points_b=0,
        is_tie_break=False,
        is_super_tie_break=False,
        is_match_over=is_match_over,
        match_winner="A" if is_match_over else None,
        player_a_name="Benjamin",
        player_b_name="Marceau",
        surface="CLAY",
        tournament="Tournoi du club",
        competition_type="CLUB",
        started_at=1000,
    )


@pytest.mark.asyncio
async def test_create_share_is_idempotent(db_session):
    service = LiveSharingService(MatchShareRepository(db_session))
    first = await service.create_share(CreateShareRequest(session_id=1))
    second = await service.create_share(CreateShareRequest(session_id=1))
    assert first.token == second.token
    assert first.url.endswith(f"/live/{first.token}")


@pytest.mark.asyncio
async def test_get_snapshot_unknown_token_raises_404(db_session):
    service = LiveSharingService(MatchShareRepository(db_session))
    with pytest.raises(SecondServeException) as exc_info:
        await service.get_snapshot("unknown-token")
    assert exc_info.value.status_code == 404


@pytest.mark.asyncio
async def test_get_snapshot_before_first_push_is_waiting(db_session):
    service = LiveSharingService(MatchShareRepository(db_session))
    share = await service.create_share(CreateShareRequest(session_id=2))
    snapshot = await service.get_snapshot(share.token)
    assert snapshot.status == "WAITING"


@pytest.mark.asyncio
async def test_push_score_then_get_snapshot_is_live(db_session):
    service = LiveSharingService(MatchShareRepository(db_session))
    share = await service.create_share(CreateShareRequest(session_id=3))
    await service.push_score(3, make_score_update(is_match_over=False))
    snapshot = await service.get_snapshot(share.token)
    assert snapshot.status == "LIVE"
    assert snapshot.current_set_games_a == 1
    assert snapshot.player_a_name == "Benjamin"


@pytest.mark.asyncio
async def test_push_score_match_over_sets_expiry_and_ended_status(db_session):
    service = LiveSharingService(MatchShareRepository(db_session))
    share = await service.create_share(CreateShareRequest(session_id=4))
    await service.push_score(4, make_score_update(is_match_over=True))
    snapshot = await service.get_snapshot(share.token)
    assert snapshot.status == "ENDED"
    assert snapshot.match_winner == "A"


@pytest.mark.asyncio
async def test_push_score_for_unshared_session_is_noop(db_session):
    service = LiveSharingService(MatchShareRepository(db_session))
    await service.push_score(999, make_score_update())  # ne doit pas lever d'exception
    repo = MatchShareRepository(db_session)
    assert await repo.get_by_session(999) is None


@pytest.mark.asyncio
async def test_get_snapshot_expired_raises_410(db_session):
    repo = MatchShareRepository(db_session)
    service = LiveSharingService(repo)
    share = await service.create_share(CreateShareRequest(session_id=5))
    await service.push_score(5, make_score_update(is_match_over=True))
    stored = await repo.get_by_token(share.token)
    stored.expires_at = int(time.time() * 1000) - 1000  # forcer l'expiration
    await db_session.flush()
    with pytest.raises(SecondServeException) as exc_info:
        await service.get_snapshot(share.token)
    assert exc_info.value.status_code == 410
```

- [ ] **Step 9: Lancer les tests et vérifier qu'ils passent**

Run: `cd backend && uv run pytest tests/integration/test_live_sharing_service.py -v`
Expected: 7 tests PASS.

- [ ] **Step 10: Commit**

```bash
git add backend/app/features/live_sharing backend/alembic/versions/f1a2b3c4d5e6_add_match_shares_table.py backend/app/core/config.py backend/tests/integration/test_live_sharing_service.py
git commit -m "feat(backend): ajouter la couche domaine live_sharing (modèle, service, repository, broadcaster)"
```

---

### Task 2: Couche HTTP — endpoints REST + SSE

**Files:**
- Create: `backend/app/api/v1/live_sharing.py`
- Modify: `backend/app/api/v1/router.py`
- Test: `backend/tests/integration/test_live_sharing_api.py`

**Interfaces:**
- Consumes: `LiveSharingService`, `MatchShareRepository`, schémas de Task 1 ; `app.core.security.verify_jwt`, `app.core.database.get_db` (existants).
- Produces: routes `POST /api/v1/live/shares`, `POST /api/v1/live/sessions/{session_id}/score`, `GET /api/v1/live/{token}`, `GET /api/v1/live/{token}/stream`.

- [ ] **Step 1: Écrire le router**

`backend/app/api/v1/live_sharing.py` :

```python
import asyncio
import json
import logging
from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse
from sqlalchemy.ext.asyncio import AsyncSession
from app.core.database import get_db
from app.core.security import verify_jwt
from app.features.live_sharing.broadcast import broadcaster
from app.features.live_sharing.repository import MatchShareRepository
from app.features.live_sharing.schemas import (
    CreateShareRequest,
    CreateShareResponse,
    LiveScoreUpdateRequest,
    LiveSnapshotResponse,
)
from app.features.live_sharing.service import LiveSharingService

logger = logging.getLogger(__name__)

router = APIRouter()


def get_live_sharing_service(db: AsyncSession = Depends(get_db)) -> LiveSharingService:
    return LiveSharingService(MatchShareRepository(db))


# NOTE ordre des routes : "/shares" et "/sessions/{id}/score" sont des routes POST distinctes
# des routes publiques GET "/{token}" et "/{token}/stream" — pas de collision possible car
# les méthodes HTTP diffèrent, mais on les déclare avant par lisibilité.

@router.post("/shares", response_model=CreateShareResponse, dependencies=[Depends(verify_jwt)])
async def create_share(
    request: CreateShareRequest,
    service: LiveSharingService = Depends(get_live_sharing_service),
):
    return await service.create_share(request)


@router.post(
    "/sessions/{session_id}/score",
    status_code=204,
    dependencies=[Depends(verify_jwt)],
)
async def push_score(
    session_id: int,
    request: LiveScoreUpdateRequest,
    service: LiveSharingService = Depends(get_live_sharing_service),
):
    await service.push_score(session_id, request)


@router.get("/{token}", response_model=LiveSnapshotResponse)
async def get_snapshot(
    token: str,
    service: LiveSharingService = Depends(get_live_sharing_service),
):
    return await service.get_snapshot(token)


@router.get("/{token}/stream")
async def stream_snapshot(
    token: str,
    request: Request,
    service: LiveSharingService = Depends(get_live_sharing_service),
):
    initial = await service.get_snapshot(token)  # lève 404/410 avant tout abonnement
    queue = broadcaster.subscribe(token)

    async def event_generator():
        try:
            yield f"data: {initial.model_dump_json()}\n\n"
            while True:
                if await request.is_disconnected():
                    break
                try:
                    snapshot = await asyncio.wait_for(queue.get(), timeout=15)
                    yield f"data: {json.dumps(snapshot)}\n\n"
                except asyncio.TimeoutError:
                    yield ": keep-alive\n\n"
        finally:
            broadcaster.unsubscribe(token, queue)

    return StreamingResponse(event_generator(), media_type="text/event-stream")
```

- [ ] **Step 2: Câbler le router dans `api/v1/router.py`**

Modifier `backend/app/api/v1/router.py` :

```python
import logging

from fastapi import APIRouter, Depends
from app.api.v1 import auth, sessions, profile, coaching, sync, notifications, work_axes, live_sharing
from app.core.security import verify_jwt

logger = logging.getLogger(__name__)

api_router = APIRouter()


@api_router.get("/health")
async def health():
    logger.debug("Health check called")
    return {"status": "ok"}


api_router.include_router(auth.router, prefix="/auth", tags=["auth"])
api_router.include_router(sessions.router, prefix="/sessions", tags=["sessions"], dependencies=[Depends(verify_jwt)])
api_router.include_router(profile.router, prefix="/profile", tags=["profile"], dependencies=[Depends(verify_jwt)])
api_router.include_router(coaching.router, prefix="/coaching", tags=["coaching"], dependencies=[Depends(verify_jwt)])
api_router.include_router(sync.router, prefix="/sync", tags=["sync"], dependencies=[Depends(verify_jwt)])
api_router.include_router(notifications.router, prefix="/notifications", tags=["notifications"], dependencies=[Depends(verify_jwt)])
api_router.include_router(work_axes.router, prefix="/work_axes", tags=["work_axes"], dependencies=[Depends(verify_jwt)])
api_router.include_router(live_sharing.router, prefix="/live", tags=["live_sharing"])
```

Noter que `live_sharing.router` est inclus **sans** `dependencies=[Depends(verify_jwt)]` au niveau du routeur (contrairement aux autres features) : l'authentification est appliquée route par route à l'intérieur de `live_sharing.py`, puisque deux des quatre routes doivent rester publiques.

- [ ] **Step 3: Écrire les tests d'intégration HTTP**

`backend/tests/integration/test_live_sharing_api.py` :

```python
import pytest
from tests.integration.test_work_axes_api import make_token, auth


def score_payload(is_match_over: bool = False) -> dict:
    return {
        "completed_sets": [],
        "current_set_games_a": 2,
        "current_set_games_b": 1,
        "current_set_point_log": ["A", "B", "A"],
        "current_game_points_a": "FORTY",
        "current_game_points_b": "THIRTY",
        "tie_break_points_a": 0,
        "tie_break_points_b": 0,
        "is_tie_break": False,
        "is_super_tie_break": False,
        "is_match_over": is_match_over,
        "match_winner": "A" if is_match_over else None,
        "player_a_name": "Benjamin",
        "player_b_name": "Marceau",
        "surface": "CLAY",
        "tournament": "Tournoi du club",
        "competition_type": "CLUB",
        "started_at": 1000,
    }


@pytest.mark.asyncio
async def test_create_share_requires_jwt(client):
    response = await client.post("/api/v1/live/shares", json={"session_id": 1})
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_create_share_returns_token_and_url(client):
    token = make_token()
    response = await client.post(
        "/api/v1/live/shares", json={"session_id": 10}, headers=auth(token)
    )
    assert response.status_code == 200
    data = response.json()
    assert "token" in data
    assert data["url"].endswith(f"/live/{data['token']}")


@pytest.mark.asyncio
async def test_create_share_idempotent(client):
    token = make_token()
    first = await client.post(
        "/api/v1/live/shares", json={"session_id": 11}, headers=auth(token)
    )
    second = await client.post(
        "/api/v1/live/shares", json={"session_id": 11}, headers=auth(token)
    )
    assert first.json()["token"] == second.json()["token"]


@pytest.mark.asyncio
async def test_get_snapshot_unknown_token_returns_404(client):
    response = await client.get("/api/v1/live/does-not-exist")
    assert response.status_code == 404
    assert response.json()["error_code"] == "SHARE_NOT_FOUND"


@pytest.mark.asyncio
async def test_push_score_requires_jwt(client):
    response = await client.post(
        "/api/v1/live/sessions/12/score", json=score_payload()
    )
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_full_flow_create_push_read(client):
    token = make_token()
    create_resp = await client.post(
        "/api/v1/live/shares", json={"session_id": 13}, headers=auth(token)
    )
    share_token = create_resp.json()["token"]

    waiting = await client.get(f"/api/v1/live/{share_token}")
    assert waiting.json()["status"] == "WAITING"

    push_resp = await client.post(
        "/api/v1/live/sessions/13/score",
        json=score_payload(is_match_over=False),
        headers=auth(token),
    )
    assert push_resp.status_code == 204

    live = await client.get(f"/api/v1/live/{share_token}")
    live_data = live.json()
    assert live_data["status"] == "LIVE"
    assert live_data["current_set_games_a"] == 2
    assert live_data["current_set_point_log"] == ["A", "B", "A"]
    assert live_data["player_a_name"] == "Benjamin"

    end_resp = await client.post(
        "/api/v1/live/sessions/13/score",
        json=score_payload(is_match_over=True),
        headers=auth(token),
    )
    assert end_resp.status_code == 204

    ended = await client.get(f"/api/v1/live/{share_token}")
    assert ended.json()["status"] == "ENDED"
    assert ended.json()["match_winner"] == "A"


@pytest.mark.asyncio
async def test_stream_first_event_is_current_snapshot(client):
    token = make_token()
    create_resp = await client.post(
        "/api/v1/live/shares", json={"session_id": 14}, headers=auth(token)
    )
    share_token = create_resp.json()["token"]
    await client.post(
        "/api/v1/live/sessions/14/score",
        json=score_payload(),
        headers=auth(token),
    )

    async with client.stream("GET", f"/api/v1/live/{share_token}/stream") as response:
        assert response.status_code == 200
        async for line in response.aiter_lines():
            if line.startswith("data: "):
                import json as json_module
                payload = json_module.loads(line[len("data: "):])
                assert payload["status"] == "LIVE"
                assert payload["current_set_games_a"] == 2
                break
```

- [ ] **Step 4: Lancer les tests et vérifier qu'ils passent**

Run: `cd backend && uv run pytest tests/integration/test_live_sharing_api.py -v`
Expected: 7 tests PASS.

- [ ] **Step 5: Lancer toute la suite pour vérifier l'absence de régression**

Run: `cd backend && uv run pytest -v`
Expected: tous les tests PASS (existants + nouveaux).

- [ ] **Step 6: Commit**

```bash
git add backend/app/api/v1/live_sharing.py backend/app/api/v1/router.py backend/tests/integration/test_live_sharing_api.py
git commit -m "feat(backend): exposer les endpoints REST/SSE de live_sharing"
```

---

### Task 3: Nettoyage planifié des liens expirés

**Files:**
- Modify: `backend/app/features/notifications/scheduler.py`

**Interfaces:**
- Consumes: `MatchShareRepository.delete_expired` (Task 1), `AsyncSessionLocal` (existant).

- [ ] **Step 1: Ajouter le job de purge**

Modifier `backend/app/features/notifications/scheduler.py` :

```python
import logging
import time
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from app.core.database import AsyncSessionLocal
from app.core.config import settings
from app.features.notifications.service import generate_pending_for_upcoming
from app.features.monitoring.database import MonitoringSessionLocal
from app.features.monitoring import service as monitoring_svc
from app.features.live_sharing.repository import MatchShareRepository

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


async def _run_purge_job() -> None:
    async with MonitoringSessionLocal() as db:
        try:
            await monitoring_svc.purge_old_records(db, days=30)
            logger.info("APScheduler: purge monitor.db — entrées > 30 jours supprimées")
        except Exception as exc:
            logger.error("APScheduler: erreur purge monitoring: %s", exc)


async def _run_live_share_cleanup_job() -> None:
    async with AsyncSessionLocal() as db:
        try:
            repository = MatchShareRepository(db)
            deleted = await repository.delete_expired(int(time.time() * 1000))
            await db.commit()
            if deleted:
                logger.info("APScheduler: %d liens de match expirés supprimés", deleted)
        except Exception as exc:
            await db.rollback()
            logger.error("APScheduler: erreur purge match_shares: %s", exc)


def start_scheduler() -> None:
    global _scheduler
    _scheduler = AsyncIOScheduler()
    _scheduler.add_job(_run_job, "interval", minutes=30, id="pre_match_reminder")
    _scheduler.add_job(_run_purge_job, "cron", hour=3, minute=0, id="monitoring_purge")
    _scheduler.add_job(_run_live_share_cleanup_job, "cron", hour=4, minute=0, id="live_share_cleanup")
    _scheduler.start()
    logger.info("APScheduler démarré (intervalle 30 min)")


def stop_scheduler() -> None:
    global _scheduler
    if _scheduler and _scheduler.running:
        _scheduler.shutdown(wait=False)
        logger.info("APScheduler arrêté")
```

- [ ] **Step 2: Écrire le test du repository de purge**

Le job planifié (`_run_live_share_cleanup_job`) ouvre sa propre session DB via `AsyncSessionLocal`, distincte de la fixture `db_session` des tests — le tester directement demanderait de mocker la connexion à la base de production, ce qui n'apporte rien. On teste donc la méthode `delete_expired` du repository qu'il appelle, ce qui couvre la seule logique métier réelle du job.

Ajouter à `backend/tests/integration/test_live_sharing_service.py` (à la suite des tests existants) :

```python
@pytest.mark.asyncio
async def test_repository_delete_expired_removes_only_past_shares(db_session):
    repo = MatchShareRepository(db_session)
    now = int(time.time() * 1000)

    expired = await repo.create(100)
    await repo.update_snapshot(expired, "{}", expires_at=now - 1000)

    active = await repo.create(101)
    await repo.update_snapshot(active, "{}", expires_at=now + 1_000_000)

    still_live = await repo.create(102)  # expires_at=None : match en cours

    deleted_count = await repo.delete_expired(now)

    assert deleted_count == 1
    assert await repo.get_by_session(100) is None
    assert await repo.get_by_session(101) is not None
    assert await repo.get_by_session(102) is not None
```

(Retirer le test `test_cleanup_job_deletes_expired_shares` ci-dessus — ne pas l'ajouter au fichier, seul `test_repository_delete_expired_removes_only_past_shares` est conservé.)

- [ ] **Step 3: Lancer les tests et vérifier qu'ils passent**

Run: `cd backend && uv run pytest tests/integration/test_live_sharing_service.py -v`
Expected: 8 tests PASS (les 7 de Task 1 + le nouveau test du repository).

- [ ] **Step 4: Commit**

```bash
git add backend/app/features/notifications/scheduler.py backend/tests/integration/test_live_sharing_service.py
git commit -m "feat(backend): purger quotidiennement les liens de match expirés"
```

---

### Task 4: CORS pour la page publique Next.js + documentation de déploiement

**Files:**
- Modify: `backend/app/core/config.py`
- Modify: `backend/app/main.py`
- Modify: `backend/.env.example`
- Modify: `backend/DEPLOY.md`

**Interfaces:**
- Produces: réglage `settings.web_cors_origin`, middleware CORS FastAPI actif sur les endpoints publics `GET /api/v1/live/*`.

- [ ] **Step 1: Ajouter le réglage CORS**

Modifier `backend/app/core/config.py` — ajouter après `public_web_base_url` :

```python
    web_cors_origin: str = "http://localhost:3000"
```

- [ ] **Step 2: Activer le middleware CORS**

Modifier `backend/app/main.py` — ajouter l'import et le middleware après la création de `app` :

```python
from fastapi.middleware.cors import CORSMiddleware
```

Après `app.add_middleware(RequestLoggingMiddleware)`, ajouter :

```python
app.add_middleware(
    CORSMiddleware,
    allow_origins=[settings.web_cors_origin],
    allow_methods=["GET"],
    allow_headers=["*"],
)
```

- [ ] **Step 3: Vérifier manuellement l'en-tête CORS**

Run:
```bash
cd backend && uv run uvicorn app.main:app --port 8001 &
sleep 1
curl -sI -H "Origin: http://localhost:3000" http://localhost:8001/api/v1/live/does-not-exist | grep -i access-control-allow-origin
kill %1
```
Expected: la ligne `access-control-allow-origin: http://localhost:3000` est présente.

- [ ] **Step 4: Documenter les nouvelles variables d'environnement**

Modifier `backend/.env.example` — ajouter à la fin :

```
# Partage de lien live (feature live_sharing)
PUBLIC_WEB_BASE_URL=https://secondserve.app
WEB_CORS_ORIGIN=https://secondserve.app
```

Modifier `backend/DEPLOY.md` — dans le tableau de la section « Configurer les variables d'environnement », ajouter deux lignes :

```
| `PUBLIC_WEB_BASE_URL` | URL publique de l'app Next.js (page de suivi live), ex. `https://secondserve.app` |
| `WEB_CORS_ORIGIN` | Même valeur que `PUBLIC_WEB_BASE_URL` — origine autorisée en CORS pour les endpoints publics `/api/v1/live/*` |
```

- [ ] **Step 5: Commit**

```bash
git add backend/app/core/config.py backend/app/main.py backend/.env.example backend/DEPLOY.md
git commit -m "feat(backend): activer CORS pour la page publique et documenter le déploiement"
```
