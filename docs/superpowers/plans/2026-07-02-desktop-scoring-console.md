# Console de saisie point par point Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter au projet `web/` une console de saisie point par point (`/dashboard/console`), outil de rétro-saisie de matchs déjà joués (contexte tactique fin : ace, coup gagnant, faute provoquée...) doublé d'un secours en direct si l'app Android/montre plante, conforme à `design/README.md` écran 9.

**Architecture:** Backend FastAPI — nouvelle feature `features/points/` (table `points` déjà existante en base, jamais exploitée, + colonne `context` ajoutée) ; nouvelle colonne `score_seed_json` sur `sessions` ; nouvel endpoint de lookup `GET /live/shares/by-session/{id}`. Côté `web/`, un port TypeScript fidèle de `TennisScoreEngine.kt` (`web/lib/scoreEngine.ts`) rejoue les points persistés au chargement pour reconstruire l'état ; le JWT reste strictement côté serveur Next.js (jamais dans le JS navigateur, cf. plan du tableau de bord) — les composants client de la console appellent une fine couche de route handlers Next.js (`web/app/api/console/**`) qui lisent le cookie httpOnly et relaient vers le backend. Layout mobile-first : styles de base empilés en une colonne, une media query `min-width: 900px` élargit vers la grille CSS 3 colonnes du mockup desktop.

**Tech Stack:** FastAPI (backend), Next.js 16 App Router (Route Handlers pour le proxy authentifié), TypeScript, CSS Modules + variables `--ss-*` existantes, Vitest + React Testing Library.

## Global Constraints

- Ce plan couvre **uniquement** la console de saisie — le tableau de bord est un sous-projet déjà livré (`docs/superpowers/plans/2026-07-02-desktop-dashboard.md`), non retouché ici sauf le `Sidebar` (nouvel item de navigation).
- Convention JSON backend : `snake_case`. Conversion vers `camelCase` uniquement à la frontière (`web/lib/api.ts`), comme pour le reste de `web/`.
- **JWT jamais côté navigateur** : les composants `"use client"` de la console n'appellent jamais le backend directement ; ils passent par les route handlers `web/app/api/console/**`, qui lisent le cookie httpOnly `SESSION_COOKIE` côté serveur et attachent le JWT à l'appel backend.
- Next.js 16 : `cookies()` et `params` sont asynchrones (`await cookies()`, `await params`).
- Convention self/opponent : `Player.A` = l'utilisateur (self), `Player.B` = l'adversaire — confirmé par `MatchViewModel.kt` (`playerBName = opponentName`). Les 4 tags « Mon point » (`ACE`, `WINNER`, `FORCED_ERROR`, `UNFORCED_ERROR_OPPONENT`) impliquent `scorer=A` ; les 4 tags « Point adverse » (`ACE_OPPONENT`, `WINNER_OPPONENT`, `UNFORCED_ERROR_SELF`, `DOUBLE_FAULT`) impliquent `scorer=B`.
- Pas de bouton « Basculer » (décision validée dans le spec) — seul « Annuler le dernier point » existe.
- Persistance immédiate : un point n'est acquis côté UI qu'après confirmation serveur (pas de mise à jour optimiste).
- Finalisation automatique en fin de match via `POST /api/v1/sync/push` ; les champs `feeling_rating`/`feeling_comment`/`first_serve_percent_*`/`winners_*` sont envoyés à `null` dans ce DTO — **c'est sûr** car ces champs ne sont renseignés qu'a posteriori par un flux Android distinct avec un `updated_at` plus récent (LWW, `NFR-S4` déjà en place dans `SyncService._upsert_session`), donc jamais écrasés à tort.
- Live-share strictement opportuniste : ne pousser vers `/live/sessions/{id}/score` que si un lien existe déjà (`GET /live/shares/by-session/{id}` → 404 = ne rien pousser). Échec du push = best-effort, n'interrompt jamais la saisie.
- Mobile-first : breakpoint desktop = `min-width: 900px` (aucune convention de breakpoint n'existe encore dans `web/` — première feature responsive du projet, valeur choisie arbitrairement et documentée ici). En dessous de ce seuil : empilement vertical dans l'ordre ScoreCard+Annuler → grille 8 boutons+stats → déroulé point par point. Cibles tactiles ≥ 48px partout.
- **Pas de données fabriquées** (`docs/design-system.md` #7) : aucune valeur par défaut inventée pour un match — un score de départ non renseigné vaut 0-0 explicitement, jamais une estimation.
- Chaîne de migrations Alembic : la tête actuelle est `f1a2b3c4d5e6` (rien n'a `down_revision='f1a2b3c4d5e6'`). Les nouvelles migrations de ce plan chaînent à partir de là : `f1a2b3c4d5e6` → `a3b4c5d6e7f8` (Task 1) → `b4c5d6e7f8a9` (Task 2).

---

### Task 1: Backend — feature `points` (contexte tactique)

**Files:**
- Create: `backend/alembic/versions/a3b4c5d6e7f8_add_context_to_points.py`
- Create: `backend/app/features/points/__init__.py`
- Create: `backend/app/features/points/models.py`
- Create: `backend/app/features/points/schemas.py`
- Create: `backend/app/features/points/repository.py`
- Create: `backend/app/features/points/service.py`
- Create: `backend/app/api/v1/points.py`
- Modify: `backend/app/api/v1/router.py`
- Test: `backend/tests/unit/test_point_service.py`
- Test: `backend/tests/integration/test_points_api.py`

**Interfaces:**
- Consumes: `app.core.database.get_db`, `app.core.security.verify_jwt` (comme les autres features) ; table `sessions` existante pour la FK.
- Produces: `PointService.create_point(session_id, PointCreateRequest) -> PointResponse`, `PointService.list_points(session_id) -> PointsResponse`, `PointService.delete_last_point(session_id) -> None` ; routes `POST/GET /api/v1/sessions/{session_id}/points`, `DELETE /api/v1/sessions/{session_id}/points/last` (toutes JWT). `SCORER_BY_CONTEXT: dict[str, str]` — mapping fixe des 8 valeurs de `PointContext` vers `"A"`/`"B"`, consommé tel quel par les tâches suivantes comme référence des 8 valeurs valides.

- [ ] **Step 1: Écrire les tests unitaires du service (doivent échouer — module inexistant)**

Créer `backend/tests/unit/test_point_service.py` :

```python
import pytest
from unittest.mock import AsyncMock, MagicMock

from app.features.points.schemas import PointCreateRequest, PointResponse, PointsResponse
from app.features.points.service import PointService


def point_model(id=1, session_id=1, scorer="A", context="ACE", sequence_num=1, recorded_at=1_000_000):
    m = MagicMock()
    m.id = id
    m.session_id = session_id
    m.scorer = scorer
    m.context = context
    m.sequence_num = sequence_num
    m.recorded_at = recorded_at
    return m


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "context,expected_scorer",
    [
        ("ACE", "A"),
        ("WINNER", "A"),
        ("FORCED_ERROR", "A"),
        ("UNFORCED_ERROR_OPPONENT", "A"),
        ("ACE_OPPONENT", "B"),
        ("WINNER_OPPONENT", "B"),
        ("UNFORCED_ERROR_SELF", "B"),
        ("DOUBLE_FAULT", "B"),
    ],
)
async def test_create_point_derives_scorer_from_context(context, expected_scorer):
    model = point_model(scorer=expected_scorer, context=context)
    repo = MagicMock()
    repo.create = AsyncMock(return_value=model)
    service = PointService(repo)

    response = await service.create_point(7, PointCreateRequest(context=context))

    assert isinstance(response, PointResponse)
    repo.create.assert_called_once_with(7, expected_scorer, context)
    assert response.scorer == expected_scorer


@pytest.mark.asyncio
async def test_list_points_returns_all_from_repository():
    model_a = point_model(id=1, sequence_num=1)
    model_b = point_model(id=2, sequence_num=2)
    repo = MagicMock()
    repo.get_all_for_session = AsyncMock(return_value=[model_a, model_b])
    service = PointService(repo)

    result = await service.list_points(7)

    assert isinstance(result, PointsResponse)
    assert [item.id for item in result.items] == [1, 2]


@pytest.mark.asyncio
async def test_delete_last_point_calls_repository():
    repo = MagicMock()
    repo.delete_last = AsyncMock(return_value=True)
    service = PointService(repo)

    await service.delete_last_point(7)

    repo.delete_last.assert_called_once_with(7)
```

- [ ] **Step 2: Lancer les tests, vérifier l'échec**

Run: `cd backend && uv run pytest tests/unit/test_point_service.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'app.features.points'`.

- [ ] **Step 3: Créer le module `features/points`**

Créer `backend/app/features/points/__init__.py` (vide).

Créer `backend/app/features/points/models.py` :

```python
from sqlalchemy import Column, Integer, String, ForeignKey
from app.core.database import Base


class PointModel(Base):
    __tablename__ = "points"

    id = Column(Integer, primary_key=True, autoincrement=True)
    session_id = Column(Integer, ForeignKey("sessions.id", ondelete="CASCADE"), nullable=False, index=True)
    scorer = Column(String, nullable=False)
    context = Column(String, nullable=True)
    sequence_num = Column(Integer, nullable=False)
    recorded_at = Column(Integer, nullable=False)
```

Créer `backend/app/features/points/schemas.py` :

```python
from typing import Literal, Optional
from pydantic import BaseModel

PointContext = Literal[
    "ACE",
    "WINNER",
    "FORCED_ERROR",
    "UNFORCED_ERROR_OPPONENT",
    "ACE_OPPONENT",
    "WINNER_OPPONENT",
    "UNFORCED_ERROR_SELF",
    "DOUBLE_FAULT",
]


class PointCreateRequest(BaseModel):
    context: PointContext


class PointResponse(BaseModel):
    id: int
    session_id: int
    scorer: str
    context: Optional[str] = None
    sequence_num: int
    recorded_at: int

    model_config = {"from_attributes": True}


class PointsResponse(BaseModel):
    items: list[PointResponse]
```

Créer `backend/app/features/points/repository.py` :

```python
import time
from sqlalchemy import select, func
from sqlalchemy.ext.asyncio import AsyncSession
from app.features.points.models import PointModel


class PointRepository:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_all_for_session(self, session_id: int) -> list[PointModel]:
        result = await self.db.execute(
            select(PointModel)
            .where(PointModel.session_id == session_id)
            .order_by(PointModel.sequence_num.asc())
        )
        return list(result.scalars().all())

    async def create(self, session_id: int, scorer: str, context: str) -> PointModel:
        result = await self.db.execute(
            select(func.max(PointModel.sequence_num)).where(PointModel.session_id == session_id)
        )
        max_seq = result.scalar_one_or_none()
        point = PointModel(
            session_id=session_id,
            scorer=scorer,
            context=context,
            sequence_num=(max_seq or 0) + 1,
            recorded_at=int(time.time() * 1000),
        )
        self.db.add(point)
        await self.db.flush()
        return point

    async def delete_last(self, session_id: int) -> bool:
        result = await self.db.execute(
            select(PointModel)
            .where(PointModel.session_id == session_id)
            .order_by(PointModel.sequence_num.desc())
            .limit(1)
        )
        last = result.scalar_one_or_none()
        if last is None:
            return False
        await self.db.delete(last)
        await self.db.flush()
        return True
```

Créer `backend/app/features/points/service.py` :

```python
from app.features.points.repository import PointRepository
from app.features.points.schemas import PointCreateRequest, PointResponse, PointsResponse

SCORER_BY_CONTEXT = {
    "ACE": "A",
    "WINNER": "A",
    "FORCED_ERROR": "A",
    "UNFORCED_ERROR_OPPONENT": "A",
    "ACE_OPPONENT": "B",
    "WINNER_OPPONENT": "B",
    "UNFORCED_ERROR_SELF": "B",
    "DOUBLE_FAULT": "B",
}


class PointService:
    def __init__(self, repository: PointRepository):
        self.repository = repository

    async def create_point(self, session_id: int, request: PointCreateRequest) -> PointResponse:
        scorer = SCORER_BY_CONTEXT[request.context]
        point = await self.repository.create(session_id, scorer, request.context)
        return PointResponse.model_validate(point)

    async def list_points(self, session_id: int) -> PointsResponse:
        points = await self.repository.get_all_for_session(session_id)
        return PointsResponse(items=[PointResponse.model_validate(p) for p in points])

    async def delete_last_point(self, session_id: int) -> None:
        await self.repository.delete_last(session_id)
```

- [ ] **Step 4: Lancer les tests unitaires, vérifier le succès**

Run: `cd backend && uv run pytest tests/unit/test_point_service.py -v`
Expected: PASS (10 tests : 8 paramétrés + 2).

- [ ] **Step 5: Écrire les tests d'intégration (doivent échouer — route inexistante)**

Créer `backend/tests/integration/test_points_api.py` :

```python
import time
import jwt
import pytest
from app.core.config import settings


def make_token() -> str:
    payload = {"iat": int(time.time()), "exp": int(time.time()) + 3600}
    return jwt.encode(payload, settings.jwt_secret, algorithm="HS256")


def auth(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


async def create_session(client, token) -> int:
    response = await client.post(
        "/api/v1/sessions",
        json={
            "surface": "CLAY",
            "match_format": "BEST_OF_3",
            "third_set_rule": "FULL_ADVANTAGE",
            "created_at": 1_000_000,
        },
        headers=auth(token),
    )
    return response.json()["id"]


@pytest.mark.asyncio
async def test_create_point_requires_jwt(client):
    response = await client.post("/api/v1/sessions/1/points", json={"context": "ACE"})
    assert response.status_code in (401, 403)


@pytest.mark.asyncio
async def test_create_point_derives_scorer_self(client):
    token = make_token()
    session_id = await create_session(client, token)
    response = await client.post(
        f"/api/v1/sessions/{session_id}/points", json={"context": "ACE"}, headers=auth(token)
    )
    assert response.status_code == 201
    data = response.json()
    assert data["scorer"] == "A"
    assert data["context"] == "ACE"
    assert data["sequence_num"] == 1


@pytest.mark.asyncio
async def test_create_point_derives_scorer_opponent(client):
    token = make_token()
    session_id = await create_session(client, token)
    response = await client.post(
        f"/api/v1/sessions/{session_id}/points", json={"context": "DOUBLE_FAULT"}, headers=auth(token)
    )
    assert response.json()["scorer"] == "B"


@pytest.mark.asyncio
async def test_sequence_num_auto_increments_per_session(client):
    token = make_token()
    session_id = await create_session(client, token)
    first = await client.post(
        f"/api/v1/sessions/{session_id}/points", json={"context": "ACE"}, headers=auth(token)
    )
    second = await client.post(
        f"/api/v1/sessions/{session_id}/points", json={"context": "WINNER"}, headers=auth(token)
    )
    assert first.json()["sequence_num"] == 1
    assert second.json()["sequence_num"] == 2


@pytest.mark.asyncio
async def test_list_points_sorted_by_sequence_num(client):
    token = make_token()
    session_id = await create_session(client, token)
    await client.post(f"/api/v1/sessions/{session_id}/points", json={"context": "ACE"}, headers=auth(token))
    await client.post(f"/api/v1/sessions/{session_id}/points", json={"context": "WINNER"}, headers=auth(token))
    response = await client.get(f"/api/v1/sessions/{session_id}/points", headers=auth(token))
    assert response.status_code == 200
    items = response.json()["items"]
    assert [i["context"] for i in items] == ["ACE", "WINNER"]


@pytest.mark.asyncio
async def test_list_points_requires_jwt(client):
    response = await client.get("/api/v1/sessions/1/points")
    assert response.status_code in (401, 403)


@pytest.mark.asyncio
async def test_delete_last_point_removes_highest_sequence(client):
    token = make_token()
    session_id = await create_session(client, token)
    await client.post(f"/api/v1/sessions/{session_id}/points", json={"context": "ACE"}, headers=auth(token))
    await client.post(f"/api/v1/sessions/{session_id}/points", json={"context": "WINNER"}, headers=auth(token))
    delete_response = await client.delete(
        f"/api/v1/sessions/{session_id}/points/last", headers=auth(token)
    )
    assert delete_response.status_code == 204
    response = await client.get(f"/api/v1/sessions/{session_id}/points", headers=auth(token))
    items = response.json()["items"]
    assert len(items) == 1
    assert items[0]["context"] == "ACE"


@pytest.mark.asyncio
async def test_delete_last_point_noop_when_empty(client):
    token = make_token()
    session_id = await create_session(client, token)
    response = await client.delete(f"/api/v1/sessions/{session_id}/points/last", headers=auth(token))
    assert response.status_code == 204
```

- [ ] **Step 6: Lancer les tests d'intégration, vérifier l'échec**

Run: `cd backend && uv run pytest tests/integration/test_points_api.py -v`
Expected: FAIL — 404 Not Found (route inexistante).

- [ ] **Step 7: Créer le router et le brancher**

Créer `backend/app/api/v1/points.py` :

```python
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from app.core.database import get_db
from app.features.points.repository import PointRepository
from app.features.points.schemas import PointCreateRequest, PointResponse, PointsResponse
from app.features.points.service import PointService

router = APIRouter()


def get_point_service(db: AsyncSession = Depends(get_db)) -> PointService:
    return PointService(PointRepository(db))


@router.post("/{session_id}/points", response_model=PointResponse, status_code=201)
async def create_point(
    session_id: int,
    request: PointCreateRequest,
    service: PointService = Depends(get_point_service),
):
    return await service.create_point(session_id, request)


@router.get("/{session_id}/points", response_model=PointsResponse)
async def list_points(
    session_id: int,
    service: PointService = Depends(get_point_service),
):
    return await service.list_points(session_id)


@router.delete("/{session_id}/points/last", status_code=204)
async def delete_last_point(
    session_id: int,
    service: PointService = Depends(get_point_service),
):
    await service.delete_last_point(session_id)
```

Modifier `backend/app/api/v1/router.py` (ajouter l'import `points` et le montage, à la suite de `sessions`) :

```python
import logging

from fastapi import APIRouter, Depends
from app.api.v1 import auth, sessions, profile, coaching, sync, notifications, work_axes, live_sharing, points
from app.core.security import verify_jwt

logger = logging.getLogger(__name__)

api_router = APIRouter()


@api_router.get("/health")
async def health():
    logger.debug("Health check called")
    return {"status": "ok"}


api_router.include_router(auth.router, prefix="/auth", tags=["auth"])
api_router.include_router(sessions.router, prefix="/sessions", tags=["sessions"], dependencies=[Depends(verify_jwt)])
api_router.include_router(points.router, prefix="/sessions", tags=["points"], dependencies=[Depends(verify_jwt)])
api_router.include_router(profile.router, prefix="/profile", tags=["profile"], dependencies=[Depends(verify_jwt)])
api_router.include_router(coaching.router, prefix="/coaching", tags=["coaching"], dependencies=[Depends(verify_jwt)])
api_router.include_router(sync.router, prefix="/sync", tags=["sync"], dependencies=[Depends(verify_jwt)])
api_router.include_router(notifications.router, prefix="/notifications", tags=["notifications"], dependencies=[Depends(verify_jwt)])
api_router.include_router(work_axes.router, prefix="/work_axes", tags=["work_axes"], dependencies=[Depends(verify_jwt)])
api_router.include_router(live_sharing.router, prefix="/live", tags=["live_sharing"])
```

Créer la migration `backend/alembic/versions/a3b4c5d6e7f8_add_context_to_points.py` :

```python
"""add context to points

Revision ID: a3b4c5d6e7f8
Revises: f1a2b3c4d5e6
Create Date: 2026-07-03
"""
from alembic import op
import sqlalchemy as sa

revision = 'a3b4c5d6e7f8'
down_revision = 'f1a2b3c4d5e6'
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("points", sa.Column("context", sa.String(), nullable=True))


def downgrade() -> None:
    op.drop_column("points", "context")
```

- [ ] **Step 8: Lancer tous les tests, vérifier le succès**

Run: `cd backend && uv run pytest tests/unit/test_point_service.py tests/integration/test_points_api.py -v`
Expected: PASS (10 + 8 tests).

- [ ] **Step 9: Commit**

```bash
git add backend/app/features/points backend/app/api/v1/points.py backend/app/api/v1/router.py backend/alembic/versions/a3b4c5d6e7f8_add_context_to_points.py backend/tests/unit/test_point_service.py backend/tests/integration/test_points_api.py
git commit -m "feat(backend): ajouter la feature points (contexte tactique)"
```

---

### Task 2: Backend — `score_seed_json` sur les sessions

**Files:**
- Modify: `backend/app/features/sessions/models.py`
- Modify: `backend/app/features/sessions/schemas.py`
- Modify: `backend/app/features/sessions/repository.py`
- Modify: `backend/app/features/sessions/service.py`
- Modify: `backend/app/api/v1/sessions.py`
- Create: `backend/alembic/versions/b4c5d6e7f8a9_add_score_seed_json_to_sessions.py`
- Test: `backend/tests/unit/test_session_service.py`
- Test: `backend/tests/integration/test_sessions_api.py`

**Interfaces:**
- Consumes: `SessionRepository.get_by_id` (existe déjà).
- Produces: `SessionService.update_score_seed(session_id, ScoreSeedRequest) -> SessionResponse` (lève `SecondServeException("SESSION_NOT_FOUND", ..., 404)` si absent) ; route `PUT /api/v1/sessions/{session_id}/score-seed` ; `SessionResponse.score_seed_json: Optional[str]`.

- [ ] **Step 1: Étendre le fixture `session_model` et écrire les tests unitaires (doivent échouer)**

Modifier `backend/tests/unit/test_session_service.py` — ajouter le paramètre `score_seed_json` au fixture existant :

```python
def session_model(
    id=1,
    surface="CLAY",
    match_format="BEST_OF_3",
    third_set_rule="FULL_ADVANTAGE",
    opponent=None,
    competition_type=None,
    tournament=None,
    status="ACTIVE",
    session_type="MATCH",
    result=None,
    score_text=None,
    score_seed_json=None,
    created_at=1_000_000,
    updated_at=1_000_000
):
    m = MagicMock()
    m.id = id
    m.surface = surface
    m.match_format = match_format
    m.third_set_rule = third_set_rule
    m.opponent = opponent
    m.competition_type = competition_type
    m.tournament = tournament
    m.status = status
    m.session_type = session_type
    m.result = result
    m.score_text = score_text
    m.score_seed_json = score_seed_json
    m.created_at = created_at
    m.updated_at = updated_at
    return m
```

Ajouter à la fin du même fichier :

```python
import json
from app.features.sessions.schemas import ScoreSeedRequest
from app.shared.exceptions import SecondServeException


@pytest.mark.asyncio
async def test_update_score_seed_returns_session_response():
    model = session_model(id=5, score_seed_json='{"current_set_games_a": 3}')
    repo = MagicMock()
    repo.update_score_seed = AsyncMock(return_value=model)
    service = SessionService(repo)

    request = ScoreSeedRequest(current_set_games_a=3)
    response = await service.update_score_seed(5, request)

    assert response.id == 5
    assert response.score_seed_json == '{"current_set_games_a": 3}'
    repo.update_score_seed.assert_called_once_with(5, json.dumps(request.model_dump()))


@pytest.mark.asyncio
async def test_update_score_seed_raises_when_session_not_found():
    repo = MagicMock()
    repo.update_score_seed = AsyncMock(return_value=None)
    service = SessionService(repo)

    with pytest.raises(SecondServeException) as exc_info:
        await service.update_score_seed(999, ScoreSeedRequest())

    assert exc_info.value.error_code == "SESSION_NOT_FOUND"
    assert exc_info.value.status_code == 404
```

- [ ] **Step 2: Lancer les tests, vérifier l'échec**

Run: `cd backend && uv run pytest tests/unit/test_session_service.py -v`
Expected: FAIL — `ImportError: cannot import name 'ScoreSeedRequest'`.

- [ ] **Step 3: Ajouter la colonne au modèle**

Modifier `backend/app/features/sessions/models.py` — ajouter la colonne après `score_text` :

```python
from sqlalchemy import Column, Integer, String, Text
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
```

- [ ] **Step 4: Ajouter les schémas**

Modifier `backend/app/features/sessions/schemas.py` :

```python
from pydantic import BaseModel
from typing import Literal, Optional


class SessionCreateRequest(BaseModel):
    surface: Literal["CLAY", "GRASS", "HARD", "CARPET"]
    match_format: Literal["BEST_OF_1", "BEST_OF_3"]
    third_set_rule: Literal["FULL_ADVANTAGE", "SUPER_TIE_BREAK_10", "SHORT_DECISIVE_SET"]
    opponent: Optional[str] = None
    competition_type: Optional[str] = None
    tournament: Optional[str] = None
    created_at: int


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
    score_text: Optional[str] = None
    score_seed_json: Optional[str] = None
    created_at: int
    updated_at: int

    model_config = {"from_attributes": True}


class SessionsResponse(BaseModel):
    items: list[SessionResponse]
    total: int


class SetResultSchema(BaseModel):
    games_a: int
    games_b: int


class ScoreSeedRequest(BaseModel):
    completed_sets: list[SetResultSchema] = []
    current_set_games_a: int = 0
    current_set_games_b: int = 0
    current_game_points_a: Literal["ZERO", "FIFTEEN", "THIRTY", "FORTY", "ADVANTAGE"] = "ZERO"
    current_game_points_b: Literal["ZERO", "FIFTEEN", "THIRTY", "FORTY", "ADVANTAGE"] = "ZERO"
    tie_break_points_a: int = 0
    tie_break_points_b: int = 0
    is_tie_break: bool = False
    is_super_tie_break: bool = False
```

- [ ] **Step 5: Ajouter la méthode repository**

Modifier `backend/app/features/sessions/repository.py` — ajouter à la fin de la classe :

```python
    async def update_score_seed(self, session_id: int, score_seed_json: str) -> SessionModel | None:
        session = await self.get_by_id(session_id)
        if session is None:
            return None
        session.score_seed_json = score_seed_json
        await self.db.flush()
        return session
```

- [ ] **Step 6: Ajouter la méthode service**

Modifier `backend/app/features/sessions/service.py` :

```python
import json
from app.features.sessions.repository import SessionRepository
from app.features.sessions.schemas import (
    SessionCreateRequest,
    SessionResponse,
    SessionsResponse,
    ScoreSeedRequest,
)
from app.features.monitoring.events import emit_event
from app.shared.exceptions import SecondServeException


class SessionService:
    def __init__(self, repository: SessionRepository):
        self.repository = repository

    async def create_session(self, request: SessionCreateRequest) -> SessionResponse:
        session = await self.repository.create(request)
        response = SessionResponse.model_validate(session)
        emit_event("match.started", {"session_id": session.id, "status": session.status})
        return response

    async def list_sessions(self) -> SessionsResponse:
        sessions = await self.repository.get_all()
        items = [SessionResponse.model_validate(s) for s in sessions]
        return SessionsResponse(items=items, total=len(items))

    async def update_score_seed(self, session_id: int, request: ScoreSeedRequest) -> SessionResponse:
        session = await self.repository.update_score_seed(session_id, json.dumps(request.model_dump()))
        if session is None:
            raise SecondServeException(
                error_code="SESSION_NOT_FOUND", message="Session introuvable", status_code=404
            )
        return SessionResponse.model_validate(session)
```

- [ ] **Step 7: Lancer les tests unitaires, vérifier le succès**

Run: `cd backend && uv run pytest tests/unit/test_session_service.py -v`
Expected: PASS.

- [ ] **Step 8: Écrire les tests d'intégration (doivent échouer — route inexistante)**

Ajouter à `backend/tests/integration/test_sessions_api.py` :

```python
@pytest.mark.asyncio
async def test_update_score_seed(client):
    token = make_token()
    create_resp = await client.post(
        "/api/v1/sessions",
        json={"surface": "CLAY", "match_format": "BEST_OF_3", "third_set_rule": "FULL_ADVANTAGE", "created_at": 1_000_000},
        headers=auth(token),
    )
    session_id = create_resp.json()["id"]

    response = await client.put(
        f"/api/v1/sessions/{session_id}/score-seed",
        json={
            "completed_sets": [{"games_a": 6, "games_b": 4}],
            "current_set_games_a": 2,
            "current_set_games_b": 1,
            "current_game_points_a": "FORTY",
            "current_game_points_b": "THIRTY",
            "tie_break_points_a": 0,
            "tie_break_points_b": 0,
            "is_tie_break": False,
            "is_super_tie_break": False,
        },
        headers=auth(token),
    )
    assert response.status_code == 200
    assert response.json()["score_seed_json"] is not None


@pytest.mark.asyncio
async def test_update_score_seed_requires_jwt(client):
    response = await client.put("/api/v1/sessions/1/score-seed", json={})
    assert response.status_code in (401, 403)


@pytest.mark.asyncio
async def test_update_score_seed_404_when_session_missing(client):
    token = make_token()
    response = await client.put("/api/v1/sessions/999999/score-seed", json={}, headers=auth(token))
    assert response.status_code == 404
    assert response.json()["error_code"] == "SESSION_NOT_FOUND"
```

- [ ] **Step 9: Lancer les tests, vérifier l'échec**

Run: `cd backend && uv run pytest tests/integration/test_sessions_api.py -v`
Expected: FAIL — 404/405 (route inexistante).

- [ ] **Step 10: Ajouter la route et la migration**

Modifier `backend/app/api/v1/sessions.py` :

```python
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from app.core.database import get_db
from app.features.sessions.repository import SessionRepository
from app.features.sessions.schemas import (
    SessionCreateRequest,
    SessionResponse,
    SessionsResponse,
    ScoreSeedRequest,
)
from app.features.sessions.service import SessionService

router = APIRouter()


def get_session_service(db: AsyncSession = Depends(get_db)) -> SessionService:
    return SessionService(SessionRepository(db))


@router.get("", response_model=SessionsResponse)
async def list_sessions(service: SessionService = Depends(get_session_service)):
    return await service.list_sessions()


@router.post("", response_model=SessionResponse, status_code=201)
async def create_session(
    request: SessionCreateRequest,
    service: SessionService = Depends(get_session_service)
):
    return await service.create_session(request)


@router.put("/{session_id}/score-seed", response_model=SessionResponse)
async def update_score_seed(
    session_id: int,
    request: ScoreSeedRequest,
    service: SessionService = Depends(get_session_service),
):
    return await service.update_score_seed(session_id, request)
```

Créer la migration `backend/alembic/versions/b4c5d6e7f8a9_add_score_seed_json_to_sessions.py` :

```python
"""add score_seed_json to sessions

Revision ID: b4c5d6e7f8a9
Revises: a3b4c5d6e7f8
Create Date: 2026-07-03
"""
from alembic import op
import sqlalchemy as sa

revision = 'b4c5d6e7f8a9'
down_revision = 'a3b4c5d6e7f8'
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("sessions", sa.Column("score_seed_json", sa.Text(), nullable=True))


def downgrade() -> None:
    op.drop_column("sessions", "score_seed_json")
```

- [ ] **Step 11: Lancer tous les tests, vérifier le succès**

Run: `cd backend && uv run pytest tests/unit/test_session_service.py tests/integration/test_sessions_api.py -v`
Expected: PASS.

- [ ] **Step 12: Commit**

```bash
git add backend/app/features/sessions backend/app/api/v1/sessions.py backend/alembic/versions/b4c5d6e7f8a9_add_score_seed_json_to_sessions.py backend/tests/unit/test_session_service.py backend/tests/integration/test_sessions_api.py
git commit -m "feat(backend): ajouter score_seed_json et l'endpoint PUT /sessions/{id}/score-seed"
```

---

### Task 3: Backend — `GET /live/shares/by-session/{session_id}`

**Files:**
- Modify: `backend/app/features/live_sharing/service.py`
- Modify: `backend/app/api/v1/live_sharing.py`
- Test: `backend/tests/integration/test_live_sharing_api.py`

**Interfaces:**
- Consumes: `MatchShareRepository.get_by_session` (existe déjà).
- Produces: `LiveSharingService.get_share_by_session(session_id) -> CreateShareResponse` (lève `SecondServeException("SHARE_NOT_FOUND", ..., 404)` si aucun partage) ; route `GET /api/v1/live/shares/by-session/{session_id}` (JWT). Utilisé par la console pour décider du push opportuniste (404 = ne rien pousser).

- [ ] **Step 1: Écrire les tests d'intégration (doivent échouer — route inexistante)**

Ajouter à `backend/tests/integration/test_live_sharing_api.py` :

```python
@pytest.mark.asyncio
async def test_get_share_by_session_requires_jwt(client):
    response = await client.get("/api/v1/live/shares/by-session/1")
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_get_share_by_session_returns_existing_share(client):
    token = make_token()
    create_resp = await client.post(
        "/api/v1/live/shares", json={"session_id": 20}, headers=auth(token)
    )
    response = await client.get("/api/v1/live/shares/by-session/20", headers=auth(token))
    assert response.status_code == 200
    assert response.json()["token"] == create_resp.json()["token"]


@pytest.mark.asyncio
async def test_get_share_by_session_404_when_no_share(client):
    token = make_token()
    response = await client.get("/api/v1/live/shares/by-session/999", headers=auth(token))
    assert response.status_code == 404
    assert response.json()["error_code"] == "SHARE_NOT_FOUND"
```

- [ ] **Step 2: Lancer les tests, vérifier l'échec**

Run: `cd backend && uv run pytest tests/integration/test_live_sharing_api.py -v`
Expected: FAIL — 404 (route inexistante, différent du 404 attendu par le test car ce serait un 404 FastAPI générique de route manquante — vérifier que le nouveau test `test_get_share_by_session_returns_existing_share` échoue bien avant l'implémentation).

- [ ] **Step 3: Ajouter la méthode service**

Modifier `backend/app/features/live_sharing/service.py` — ajouter à la classe `LiveSharingService`, après `create_share` :

```python
    async def get_share_by_session(self, session_id: int) -> CreateShareResponse:
        share = await self.repository.get_by_session(session_id)
        if share is None:
            raise SecondServeException(
                error_code="SHARE_NOT_FOUND",
                message="Aucun lien de partage pour cette session",
                status_code=404,
            )
        return CreateShareResponse(
            token=share.token,
            url=f"{settings.public_web_base_url}/live/{share.token}",
        )
```

- [ ] **Step 4: Ajouter la route**

Modifier `backend/app/api/v1/live_sharing.py` — ajouter la route après `create_share`, avant `push_score` :

```python
@router.get(
    "/shares/by-session/{session_id}",
    response_model=CreateShareResponse,
    dependencies=[Depends(verify_jwt)],
)
async def get_share_by_session(
    session_id: int,
    service: LiveSharingService = Depends(get_live_sharing_service),
):
    return await service.get_share_by_session(session_id)
```

- [ ] **Step 5: Lancer les tests, vérifier le succès**

Run: `cd backend && uv run pytest tests/integration/test_live_sharing_api.py -v`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/app/features/live_sharing/service.py backend/app/api/v1/live_sharing.py backend/tests/integration/test_live_sharing_api.py
git commit -m "feat(backend): ajouter GET /live/shares/by-session/{id} (lookup sans création)"
```

---

### Task 4: Frontend — port TypeScript du moteur de scoring (`web/lib/scoreEngine.ts`)

**Files:**
- Create: `web/lib/scoreEngine.ts`
- Test: `web/lib/scoreEngine.test.ts`

**Interfaces:**
- Consumes: rien (module pur, aucune dépendance externe).
- Produces (consommé par les Tasks 5, 8) : types `Player`, `GamePoint`, `MatchFormatValue`, `ThirdSetRuleValue`, `SessionFormat`, `SetResult`, `MatchScore`, `EngineEvent` ; fonctions `emptyMatchScore(): MatchScore`, `isDeuce(score: MatchScore): boolean`, `formatScoreText(score: MatchScore): string`, `deriveMatchResult(score: MatchScore): "VICTORY" | "DEFEAT" | null` ; classe `TennisScoreEngine` (`constructor(format: SessionFormat, seed?: MatchScore)`, `currentScore: MatchScore` getter, `recordPoint(scorer: Player): EngineEvent`, `undo(): boolean`).

- [ ] **Step 1: Écrire le fichier de test complet (doit échouer — module inexistant)**

Créer `web/lib/scoreEngine.test.ts` :

```typescript
import { describe, expect, it } from "vitest";
import { TennisScoreEngine, formatScoreText, deriveMatchResult } from "./scoreEngine";
import type { SessionFormat, Player, EngineEvent } from "./scoreEngine";

const bestOf1Format: SessionFormat = { matchFormat: "BEST_OF_1", thirdSetRule: "FULL_ADVANTAGE" };
const bestOf3Format: SessionFormat = { matchFormat: "BEST_OF_3", thirdSetRule: "FULL_ADVANTAGE" };
const superTbFormat: SessionFormat = { matchFormat: "BEST_OF_3", thirdSetRule: "SUPER_TIE_BREAK_10" };
const shortSetFormat: SessionFormat = { matchFormat: "BEST_OF_3", thirdSetRule: "SHORT_DECISIVE_SET" };

function winPoints(engine: TennisScoreEngine, scorer: Player, count: number): void {
  for (let i = 0; i < count; i++) engine.recordPoint(scorer);
}

function winGame(engine: TennisScoreEngine, scorer: Player): EngineEvent {
  for (let i = 0; i < 3; i++) engine.recordPoint(scorer);
  return engine.recordPoint(scorer);
}

function winGames(engine: TennisScoreEngine, scorer: Player, count: number): void {
  for (let i = 0; i < count; i++) winGame(engine, scorer);
}

function winSet6_0(engine: TennisScoreEngine, scorer: Player): EngineEvent {
  let event: EngineEvent = { type: "POINT_SCORED", score: engine.currentScore };
  for (let i = 0; i < 6; i++) event = winGame(engine, scorer);
  return event;
}

// Atteint 6-6 dans le set courant en alternant les jeux (A: 1-6, B: 1-6)
function reachSixSixTieBreak(engine: TennisScoreEngine): void {
  for (let i = 0; i < 5; i++) {
    winGame(engine, "A");
    winGame(engine, "B");
  }
  winGame(engine, "A"); // 6-5
  winGame(engine, "B"); // 6-6 → tie-break
}

describe("RegularGameRules", () => {
  it("points progressed correctly from 0 to game", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    expect(engine.currentScore.currentGamePointsA).toBe("ZERO");
    engine.recordPoint("A");
    expect(engine.currentScore.currentGamePointsA).toBe("FIFTEEN");
    engine.recordPoint("A");
    expect(engine.currentScore.currentGamePointsA).toBe("THIRTY");
    engine.recordPoint("A");
    expect(engine.currentScore.currentGamePointsA).toBe("FORTY");
  });

  it("winning game at 40-0 returns GAME_WON event", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    winPoints(engine, "A", 3);
    const event = engine.recordPoint("A");
    expect(event.type).toBe("GAME_WON");
    if (event.type !== "GAME_WON") throw new Error("expected GAME_WON");
    expect(event.winner).toBe("A");
    expect(event.score.currentSetGamesA).toBe(1);
    expect(event.score.currentSetGamesB).toBe(0);
  });

  it("deuce and advantage cycle", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    winPoints(engine, "A", 3);
    winPoints(engine, "B", 3);
    expect(engine.currentScore.currentGamePointsA).toBe("FORTY");
    expect(engine.currentScore.currentGamePointsB).toBe("FORTY");
    engine.recordPoint("A");
    expect(engine.currentScore.currentGamePointsA).toBe("ADVANTAGE");
    expect(engine.currentScore.currentGamePointsB).toBe("FORTY");
    engine.recordPoint("B");
    expect(engine.currentScore.currentGamePointsA).toBe("FORTY");
    expect(engine.currentScore.currentGamePointsB).toBe("FORTY");
    engine.recordPoint("B");
    expect(engine.currentScore.currentGamePointsB).toBe("ADVANTAGE");
    const event = engine.recordPoint("B");
    expect(event.type).toBe("GAME_WON");
    if (event.type !== "GAME_WON") throw new Error("expected GAME_WON");
    expect(event.winner).toBe("B");
  });

  it("multiple deuce-advantage cycles in one game", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    winPoints(engine, "A", 3);
    winPoints(engine, "B", 3);
    engine.recordPoint("A");
    expect(engine.currentScore.currentGamePointsA).toBe("ADVANTAGE");
    engine.recordPoint("B");
    expect(engine.currentScore.currentGamePointsA).toBe("FORTY");
    expect(engine.currentScore.currentGamePointsB).toBe("FORTY");
    engine.recordPoint("B");
    expect(engine.currentScore.currentGamePointsB).toBe("ADVANTAGE");
    engine.recordPoint("A");
    expect(engine.currentScore.currentGamePointsA).toBe("FORTY");
    expect(engine.currentScore.currentGamePointsB).toBe("FORTY");
    engine.recordPoint("A");
    const event = engine.recordPoint("A");
    expect(event.type).toBe("GAME_WON");
    if (event.type !== "GAME_WON") throw new Error("expected GAME_WON");
    expect(event.winner).toBe("A");
  });
});

describe("ChangeoversDetection", () => {
  it("changeover when total games is odd", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    const event = winGame(engine, "A");
    expect(event.type).toBe("GAME_WON");
    if (event.type !== "GAME_WON") throw new Error("expected GAME_WON");
    expect(event.changeover).toBe(true);
  });

  it("no changeover when total games is even", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    winGame(engine, "A");
    const event = winGame(engine, "B");
    expect(event.type).toBe("GAME_WON");
    if (event.type !== "GAME_WON") throw new Error("expected GAME_WON");
    expect(event.changeover).toBe(false);
  });

  it("SetWon changeover false when set total is even (6-0)", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    const event = winSet6_0(engine, "A");
    expect(event.type).toBe("SET_WON");
    if (event.type !== "SET_WON") throw new Error("expected SET_WON");
    expect(event.changeover).toBe(false);
  });

  it("changeover always true after tie-break (13 total games)", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    reachSixSixTieBreak(engine);
    expect(engine.currentScore.isTieBreak).toBe(true);
    winPoints(engine, "A", 6);
    const event = engine.recordPoint("A"); // 7-0 → SET_WON
    expect(event.type).toBe("SET_WON");
    if (event.type !== "SET_WON") throw new Error("expected SET_WON");
    expect(event.changeover).toBe(true);
    const score = engine.currentScore;
    expect(score.isTieBreak).toBe(false);
    expect(score.completedSets).toHaveLength(1);
    expect(score.completedSets[0]).toEqual({ gamesA: 7, gamesB: 6 });
  });
});

describe("SetsAndGames", () => {
  it("win set 6-0", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    const event = winSet6_0(engine, "A");
    expect(event.type).toBe("SET_WON");
    if (event.type !== "SET_WON") throw new Error("expected SET_WON");
    expect(event.winner).toBe("A");
    expect(engine.currentScore.completedSets).toEqual([{ gamesA: 6, gamesB: 0 }]);
  });

  it("win set 6-3", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    winGames(engine, "A", 3);
    winGames(engine, "B", 3);
    winGames(engine, "A", 2);
    const event = winGame(engine, "A");
    expect(event.type).toBe("SET_WON");
    if (event.type !== "SET_WON") throw new Error("expected SET_WON");
    expect(event.winner).toBe("A");
    expect(engine.currentScore.completedSets[0]).toEqual({ gamesA: 6, gamesB: 3 });
  });

  it("win set 6-4", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    winGames(engine, "A", 4);
    winGames(engine, "B", 4);
    winGames(engine, "A", 1);
    const event = winGame(engine, "A");
    expect(event.type).toBe("SET_WON");
    if (event.type !== "SET_WON") throw new Error("expected SET_WON");
    expect(engine.currentScore.completedSets[0]).toEqual({ gamesA: 6, gamesB: 4 });
  });
});

describe("TieBreak", () => {
  it("tie-break triggered at 6-6", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    reachSixSixTieBreak(engine);
    expect(engine.currentScore.isTieBreak).toBe(true);
  });

  it("tie-break won at 7 with 2-point lead", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    reachSixSixTieBreak(engine);
    winPoints(engine, "A", 7);
    const score = engine.currentScore;
    expect(score.isTieBreak).toBe(false);
    expect(score.completedSets).toEqual([{ gamesA: 7, gamesB: 6 }]);
  });

  it("tie-break requires 2-point lead (7-6 not enough, 8-6 enough)", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    reachSixSixTieBreak(engine);
    winPoints(engine, "A", 6);
    winPoints(engine, "B", 6);
    winPoints(engine, "A", 1);
    expect(engine.currentScore.isTieBreak).toBe(true);
    winPoints(engine, "A", 1);
    expect(engine.currentScore.isTieBreak).toBe(false);
  });
});

describe("SuperTieBreak", () => {
  it("super tie-break triggered at 1-1 sets in SUPER_TIE_BREAK_10 format", () => {
    const engine = new TennisScoreEngine(superTbFormat);
    winSet6_0(engine, "A");
    winSet6_0(engine, "B");
    expect(engine.currentScore.isSuperTieBreak).toBe(true);
    expect(engine.currentScore.isTieBreak).toBe(false);
  });

  it("super tie-break won at 10 with 2-point lead", () => {
    const engine = new TennisScoreEngine(superTbFormat);
    winSet6_0(engine, "A");
    winSet6_0(engine, "B");
    winPoints(engine, "A", 10);
    const score = engine.currentScore;
    expect(score.isMatchOver).toBe(true);
    expect(score.matchWinner).toBe("A");
  });

  it("super tie-break requires 2-point lead (10-9 not enough)", () => {
    const engine = new TennisScoreEngine(superTbFormat);
    winSet6_0(engine, "A");
    winSet6_0(engine, "B");
    winPoints(engine, "A", 9);
    winPoints(engine, "B", 9);
    winPoints(engine, "A", 1);
    expect(engine.currentScore.isSuperTieBreak).toBe(true);
    expect(engine.currentScore.isMatchOver).toBe(false);
    winPoints(engine, "A", 1);
    expect(engine.currentScore.isMatchOver).toBe(true);
  });

  it("super tie-break result appears in completedSets", () => {
    const engine = new TennisScoreEngine(superTbFormat);
    winSet6_0(engine, "A");
    winSet6_0(engine, "B");
    winPoints(engine, "A", 10);
    const score = engine.currentScore;
    expect(score.isMatchOver).toBe(true);
    expect(score.completedSets).toHaveLength(3);
    expect(score.completedSets[2]).toEqual({ gamesA: 10, gamesB: 0 });
  });
});

describe("UndoTests", () => {
  it("undo restores previous state", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    engine.recordPoint("A");
    expect(engine.currentScore.currentGamePointsA).toBe("FIFTEEN");
    const result = engine.undo();
    expect(result).toBe(true);
    expect(engine.currentScore.currentGamePointsA).toBe("ZERO");
  });

  it("undo returns false when history empty", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    expect(engine.undo()).toBe(false);
  });

  it("undo works across game boundary", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    winPoints(engine, "A", 3);
    engine.recordPoint("A");
    expect(engine.currentScore.currentSetGamesA).toBe(1);
    engine.undo();
    expect(engine.currentScore.currentSetGamesA).toBe(0);
    expect(engine.currentScore.currentGamePointsA).toBe("FORTY");
  });

  it("undo after match over is possible", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    winSet6_0(engine, "A");
    expect(engine.currentScore.isMatchOver).toBe(true);
    engine.undo();
    expect(engine.currentScore.isMatchOver).toBe(false);
  });

  it("undo works across set boundary", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    winSet6_0(engine, "A");
    expect(engine.currentScore.completedSets).toHaveLength(1);
    engine.undo();
    expect(engine.currentScore.completedSets).toHaveLength(0);
    expect(engine.currentScore.currentSetGamesA).toBe(5);
    expect(engine.currentScore.currentGamePointsA).toBe("FORTY");
  });

  it("undo supports multiple levels", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    engine.recordPoint("A");
    engine.recordPoint("A");
    engine.recordPoint("B");
    engine.undo();
    expect(engine.currentScore.currentGamePointsB).toBe("ZERO");
    expect(engine.currentScore.currentGamePointsA).toBe("THIRTY");
    engine.undo();
    expect(engine.currentScore.currentGamePointsA).toBe("FIFTEEN");
    engine.undo();
    expect(engine.currentScore.currentGamePointsA).toBe("ZERO");
    expect(engine.undo()).toBe(false);
  });
});

describe("MatchFormats", () => {
  it("BEST_OF_1 match over after one set", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    const event = winSet6_0(engine, "A");
    expect(event.type).toBe("MATCH_OVER");
    if (event.type !== "MATCH_OVER") throw new Error("expected MATCH_OVER");
    expect(event.winner).toBe("A");
  });

  it("BEST_OF_3 requires 2 sets to win", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    winSet6_0(engine, "A");
    expect(engine.currentScore.isMatchOver).toBe(false);
    winSet6_0(engine, "A");
    expect(engine.currentScore.isMatchOver).toBe(true);
  });

  it("BEST_OF_3 can reach 3 sets", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    winSet6_0(engine, "A");
    winSet6_0(engine, "B");
    expect(engine.currentScore.isMatchOver).toBe(false);
    expect(engine.currentScore.completedSets).toHaveLength(2);
  });

  it("cannot record point after match over", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    winSet6_0(engine, "A");
    expect(() => engine.recordPoint("A")).toThrow("Cannot record point: match is over");
  });

  it("BEST_OF_3 FULL_ADVANTAGE win in 2 sets", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    winSet6_0(engine, "A");
    const event = winSet6_0(engine, "A");
    expect(event.type).toBe("MATCH_OVER");
    if (event.type !== "MATCH_OVER") throw new Error("expected MATCH_OVER");
    expect(event.winner).toBe("A");
    expect(engine.currentScore.completedSets).toHaveLength(2);
  });

  it("BEST_OF_3 Player B wins in 2 sets", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    winSet6_0(engine, "B");
    expect(engine.currentScore.isMatchOver).toBe(false);
    const event = winSet6_0(engine, "B");
    expect(event.type).toBe("MATCH_OVER");
    if (event.type !== "MATCH_OVER") throw new Error("expected MATCH_OVER");
    expect(event.winner).toBe("B");
    expect(engine.currentScore.completedSets).toHaveLength(2);
  });

  it("BEST_OF_3 FULL_ADVANTAGE win in 3 sets", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    winSet6_0(engine, "A");
    winSet6_0(engine, "B");
    const event = winSet6_0(engine, "A");
    expect(event.type).toBe("MATCH_OVER");
    if (event.type !== "MATCH_OVER") throw new Error("expected MATCH_OVER");
    expect(event.winner).toBe("A");
  });
});

describe("ShortDecisiveSet", () => {
  it("win at 4-0 in third set", () => {
    const engine = new TennisScoreEngine(shortSetFormat);
    winSet6_0(engine, "A");
    winSet6_0(engine, "B");
    winGames(engine, "A", 3);
    const event = winGame(engine, "A");
    expect(event.type).toBe("MATCH_OVER");
    if (event.type !== "MATCH_OVER") throw new Error("expected MATCH_OVER");
    expect(event.winner).toBe("A");
  });

  it("win at 4-2 in third set", () => {
    const engine = new TennisScoreEngine(shortSetFormat);
    winSet6_0(engine, "A");
    winSet6_0(engine, "B");
    winGames(engine, "A", 2);
    winGames(engine, "B", 2);
    winGames(engine, "A", 1);
    const event = winGame(engine, "A");
    expect(event.type).toBe("MATCH_OVER");
    if (event.type !== "MATCH_OVER") throw new Error("expected MATCH_OVER");
    expect(event.winner).toBe("A");
  });

  it("tie-break at 3-3", () => {
    const engine = new TennisScoreEngine(shortSetFormat);
    winSet6_0(engine, "A");
    winSet6_0(engine, "B");
    winGames(engine, "A", 3);
    winGames(engine, "B", 3);
    expect(engine.currentScore.isTieBreak).toBe(true);
  });

  it("tie-break at 3-3 then win match", () => {
    const engine = new TennisScoreEngine(shortSetFormat);
    winSet6_0(engine, "A");
    winSet6_0(engine, "B");
    winGames(engine, "A", 3);
    winGames(engine, "B", 3);
    expect(engine.currentScore.isTieBreak).toBe(true);
    winPoints(engine, "A", 7);
    expect(engine.currentScore.isMatchOver).toBe(true);
    expect(engine.currentScore.matchWinner).toBe("A");
  });

  it("Player B wins third set 4-0", () => {
    const engine = new TennisScoreEngine(shortSetFormat);
    winSet6_0(engine, "A");
    winSet6_0(engine, "B");
    winGames(engine, "B", 3);
    const event = winGame(engine, "B");
    expect(event.type).toBe("MATCH_OVER");
    if (event.type !== "MATCH_OVER") throw new Error("expected MATCH_OVER");
    expect(event.winner).toBe("B");
  });

  it("Player B wins third set 4-2", () => {
    const engine = new TennisScoreEngine(shortSetFormat);
    winSet6_0(engine, "A");
    winSet6_0(engine, "B");
    winGames(engine, "A", 2);
    winGames(engine, "B", 2);
    winGames(engine, "B", 1);
    const event = winGame(engine, "B");
    expect(event.type).toBe("MATCH_OVER");
    if (event.type !== "MATCH_OVER") throw new Error("expected MATCH_OVER");
    expect(event.winner).toBe("B");
  });
});

describe("IllegalStateChecks", () => {
  it("throws after match is over", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    winSet6_0(engine, "A");
    expect(() => engine.recordPoint("A")).toThrow();
  });

  it("throws with correct message", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    winSet6_0(engine, "A");
    expect(() => engine.recordPoint("B")).toThrow("Cannot record point: match is over");
  });
});

describe("PointLogTracking", () => {
  it("recordPoint appends scorer to currentSetPointLog", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    engine.recordPoint("A");
    engine.recordPoint("B");
    engine.recordPoint("A");
    expect(engine.currentScore.currentSetPointLog).toEqual(["A", "B", "A"]);
  });

  it("currentSetPointLog resets when the set is won", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    winSet6_0(engine, "A");
    expect(engine.currentScore.currentSetPointLog).toEqual([]);
  });

  it("currentSetPointLog is restored by undo", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    engine.recordPoint("A");
    engine.recordPoint("B");
    engine.undo();
    expect(engine.currentScore.currentSetPointLog).toEqual(["A"]);
  });
});

describe("formatScoreText", () => {
  it("joins completed sets with ' · '", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    winSet6_0(engine, "A");
    winGames(engine, "B", 3);
    winGames(engine, "A", 3);
    winGame(engine, "A"); // 6-3 → 2nd set won
    expect(formatScoreText(engine.currentScore)).toBe("6-0 · 6-3");
  });

  it("returns empty string when no set completed", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    expect(formatScoreText(engine.currentScore)).toBe("");
  });
});

describe("deriveMatchResult", () => {
  it("returns VICTORY when self (A) wins", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    winSet6_0(engine, "A");
    expect(deriveMatchResult(engine.currentScore)).toBe("VICTORY");
  });

  it("returns DEFEAT when opponent (B) wins", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    winSet6_0(engine, "B");
    expect(deriveMatchResult(engine.currentScore)).toBe("DEFEAT");
  });

  it("returns null while match is ongoing", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    expect(deriveMatchResult(engine.currentScore)).toBeNull();
  });
});
```

- [ ] **Step 2: Lancer les tests, vérifier l'échec**

Run: `cd web && npx vitest run lib/scoreEngine.test.ts`
Expected: FAIL — `Cannot find module './scoreEngine'`.

- [ ] **Step 3: Implémenter le moteur**

Créer `web/lib/scoreEngine.ts` :

```typescript
export type Player = "A" | "B";
export type GamePoint = "ZERO" | "FIFTEEN" | "THIRTY" | "FORTY" | "ADVANTAGE";
export type MatchFormatValue = "BEST_OF_1" | "BEST_OF_3";
export type ThirdSetRuleValue = "FULL_ADVANTAGE" | "SUPER_TIE_BREAK_10" | "SHORT_DECISIVE_SET";

export type SessionFormat = {
  matchFormat: MatchFormatValue;
  thirdSetRule: ThirdSetRuleValue;
};

export type SetResult = { gamesA: number; gamesB: number };

export type MatchScore = {
  completedSets: SetResult[];
  currentSetGamesA: number;
  currentSetGamesB: number;
  currentSetPointLog: Player[];
  currentGamePointsA: GamePoint;
  currentGamePointsB: GamePoint;
  tieBreakPointsA: number;
  tieBreakPointsB: number;
  isTieBreak: boolean;
  isSuperTieBreak: boolean;
  isMatchOver: boolean;
  matchWinner: Player | null;
};

export function emptyMatchScore(): MatchScore {
  return {
    completedSets: [],
    currentSetGamesA: 0,
    currentSetGamesB: 0,
    currentSetPointLog: [],
    currentGamePointsA: "ZERO",
    currentGamePointsB: "ZERO",
    tieBreakPointsA: 0,
    tieBreakPointsB: 0,
    isTieBreak: false,
    isSuperTieBreak: false,
    isMatchOver: false,
    matchWinner: null,
  };
}

export function isDeuce(score: MatchScore): boolean {
  return (
    !score.isTieBreak &&
    !score.isSuperTieBreak &&
    score.currentGamePointsA === "FORTY" &&
    score.currentGamePointsB === "FORTY"
  );
}

export function formatScoreText(score: MatchScore): string {
  return score.completedSets.map((s) => `${s.gamesA}-${s.gamesB}`).join(" · ");
}

export function deriveMatchResult(score: MatchScore): "VICTORY" | "DEFEAT" | null {
  if (score.matchWinner === "A") return "VICTORY";
  if (score.matchWinner === "B") return "DEFEAT";
  return null;
}

export type EngineEvent =
  | { type: "POINT_SCORED"; score: MatchScore }
  | { type: "GAME_WON"; score: MatchScore; winner: Player; changeover: boolean }
  | { type: "SET_WON"; score: MatchScore; winner: Player; changeover: boolean }
  | { type: "MATCH_OVER"; score: MatchScore; winner: Player };

const GAME_POINT_PROGRESSION: Record<GamePoint, GamePoint | null> = {
  ZERO: "FIFTEEN",
  FIFTEEN: "THIRTY",
  THIRTY: "FORTY",
  FORTY: null,
  ADVANTAGE: null,
};

export class TennisScoreEngine {
  readonly format: SessionFormat;
  private state: MatchScore;
  private history: MatchScore[] = [];

  constructor(format: SessionFormat, seed?: MatchScore) {
    this.format = format;
    this.state = seed ?? emptyMatchScore();
  }

  get currentScore(): MatchScore {
    return this.state;
  }

  recordPoint(scorer: Player): EngineEvent {
    if (this.state.isMatchOver) {
      throw new Error("Cannot record point: match is over");
    }
    this.history.push(this.state);
    this.state = { ...this.state, currentSetPointLog: [...this.state.currentSetPointLog, scorer] };
    if (this.state.isSuperTieBreak) return this.processSuperTieBreakPoint(scorer);
    if (this.state.isTieBreak) return this.processTieBreakPoint(scorer);
    return this.processRegularPoint(scorer);
  }

  undo(): boolean {
    const previous = this.history.pop();
    if (previous === undefined) return false;
    this.state = previous;
    return true;
  }

  private processRegularPoint(scorer: Player): EngineEvent {
    const pA = this.state.currentGamePointsA;
    const pB = this.state.currentGamePointsB;

    if (pA === "ADVANTAGE" || pB === "ADVANTAGE") {
      if ((scorer === "A" && pA === "ADVANTAGE") || (scorer === "B" && pB === "ADVANTAGE")) {
        return this.awardGame(scorer);
      }
      this.state = { ...this.state, currentGamePointsA: "FORTY", currentGamePointsB: "FORTY" };
      return { type: "POINT_SCORED", score: this.state };
    }

    if (isDeuce(this.state)) {
      this.state =
        scorer === "A"
          ? { ...this.state, currentGamePointsA: "ADVANTAGE" }
          : { ...this.state, currentGamePointsB: "ADVANTAGE" };
      return { type: "POINT_SCORED", score: this.state };
    }

    const currentPoints = scorer === "A" ? pA : pB;
    const nextPoints = GAME_POINT_PROGRESSION[currentPoints];
    if (nextPoints === null) {
      return this.awardGame(scorer);
    }

    this.state =
      scorer === "A"
        ? { ...this.state, currentGamePointsA: nextPoints }
        : { ...this.state, currentGamePointsB: nextPoints };
    return { type: "POINT_SCORED", score: this.state };
  }

  private processTieBreakPoint(scorer: Player): EngineEvent {
    const newA = this.state.tieBreakPointsA + (scorer === "A" ? 1 : 0);
    const newB = this.state.tieBreakPointsB + (scorer === "B" ? 1 : 0);
    this.state = { ...this.state, tieBreakPointsA: newA, tieBreakPointsB: newB };

    const winner: Player | null = newA >= 7 && newA - newB >= 2 ? "A" : newB >= 7 && newB - newA >= 2 ? "B" : null;
    return winner !== null ? this.awardTieBreakGame(winner) : { type: "POINT_SCORED", score: this.state };
  }

  private processSuperTieBreakPoint(scorer: Player): EngineEvent {
    const newA = this.state.tieBreakPointsA + (scorer === "A" ? 1 : 0);
    const newB = this.state.tieBreakPointsB + (scorer === "B" ? 1 : 0);
    this.state = { ...this.state, tieBreakPointsA: newA, tieBreakPointsB: newB };

    const winner: Player | null = newA >= 10 && newA - newB >= 2 ? "A" : newB >= 10 && newB - newA >= 2 ? "B" : null;
    if (winner !== null) {
      const superTbResult: SetResult = { gamesA: newA, gamesB: newB };
      this.state = {
        ...this.state,
        completedSets: [...this.state.completedSets, superTbResult],
        isMatchOver: true,
        matchWinner: winner,
        isSuperTieBreak: false,
      };
      return { type: "MATCH_OVER", score: this.state, winner };
    }
    return { type: "POINT_SCORED", score: this.state };
  }

  private awardGame(winner: Player): EngineEvent {
    const newGamesA = this.state.currentSetGamesA + (winner === "A" ? 1 : 0);
    const newGamesB = this.state.currentSetGamesB + (winner === "B" ? 1 : 0);
    const totalGames = newGamesA + newGamesB;
    const changeover = totalGames % 2 === 1;

    this.state = {
      ...this.state,
      currentSetGamesA: newGamesA,
      currentSetGamesB: newGamesB,
      currentGamePointsA: "ZERO",
      currentGamePointsB: "ZERO",
      isTieBreak: false,
      tieBreakPointsA: 0,
      tieBreakPointsB: 0,
    };

    if (newGamesA === 6 && newGamesB === 6) {
      return this.startTieBreak(changeover, winner);
    }

    return this.checkSetWon(winner, changeover);
  }

  private awardTieBreakGame(winner: Player): EngineEvent {
    const newGamesA = this.state.currentSetGamesA + (winner === "A" ? 1 : 0);
    const newGamesB = this.state.currentSetGamesB + (winner === "B" ? 1 : 0);

    this.state = {
      ...this.state,
      currentSetGamesA: newGamesA,
      currentSetGamesB: newGamesB,
      isTieBreak: false,
      tieBreakPointsA: 0,
      tieBreakPointsB: 0,
    };
    // Gagner le tie-break gagne toujours le set (7-6, ou 4-3 en SHORT_DECISIVE_SET)
    return this.awardSet(winner);
  }

  private startTieBreak(changeover: boolean, lastGameWinner: Player): EngineEvent {
    this.state = {
      ...this.state,
      isTieBreak: true,
      tieBreakPointsA: 0,
      tieBreakPointsB: 0,
      currentGamePointsA: "ZERO",
      currentGamePointsB: "ZERO",
    };
    return { type: "GAME_WON", score: this.state, winner: lastGameWinner, changeover };
  }

  private checkSetWon(winner: Player, gameChangeover: boolean): EngineEvent {
    const gA = this.state.currentSetGamesA;
    const gB = this.state.currentSetGamesB;

    if (this.format.thirdSetRule === "SHORT_DECISIVE_SET" && this.isFinalSet() && gA === 3 && gB === 3) {
      return this.startTieBreak(gameChangeover, winner);
    }

    const isShortDecisive = this.format.thirdSetRule === "SHORT_DECISIVE_SET" && this.isFinalSet();
    const setWinner: Player | null =
      gA >= 6 && gA - gB >= 2
        ? "A"
        : gB >= 6 && gB - gA >= 2
          ? "B"
          : isShortDecisive && gA >= 4 && gA - gB >= 2
            ? "A"
            : isShortDecisive && gB >= 4 && gB - gA >= 2
              ? "B"
              : null;

    if (setWinner === null) {
      return { type: "GAME_WON", score: this.state, winner, changeover: gameChangeover };
    }

    return this.awardSet(setWinner);
  }

  private awardSet(winner: Player): EngineEvent {
    const totalGamesInSet = this.state.currentSetGamesA + this.state.currentSetGamesB;
    const setChangeover = totalGamesInSet % 2 === 1;

    const completedSet: SetResult = { gamesA: this.state.currentSetGamesA, gamesB: this.state.currentSetGamesB };
    const newCompletedSets = [...this.state.completedSets, completedSet];

    const setsWonA = newCompletedSets.filter((s) => s.gamesA > s.gamesB).length;
    const setsWonB = newCompletedSets.filter((s) => s.gamesB > s.gamesA).length;
    const setsToWin = this.format.matchFormat === "BEST_OF_1" ? 1 : 2;

    this.state = {
      ...this.state,
      completedSets: newCompletedSets,
      currentSetGamesA: 0,
      currentSetGamesB: 0,
      currentSetPointLog: [],
      currentGamePointsA: "ZERO",
      currentGamePointsB: "ZERO",
      tieBreakPointsA: 0,
      tieBreakPointsB: 0,
      isTieBreak: false,
    };

    if (setsWonA >= setsToWin || setsWonB >= setsToWin) {
      this.state = { ...this.state, isMatchOver: true, matchWinner: winner };
      return { type: "MATCH_OVER", score: this.state, winner };
    }

    if (
      this.format.matchFormat === "BEST_OF_3" &&
      this.format.thirdSetRule === "SUPER_TIE_BREAK_10" &&
      setsWonA === 1 &&
      setsWonB === 1
    ) {
      this.state = { ...this.state, isSuperTieBreak: true, tieBreakPointsA: 0, tieBreakPointsB: 0 };
      return { type: "SET_WON", score: this.state, winner, changeover: setChangeover };
    }

    return { type: "SET_WON", score: this.state, winner, changeover: setChangeover };
  }

  private isFinalSet(): boolean {
    if (this.format.matchFormat === "BEST_OF_1") return true;
    const setsWonA = this.state.completedSets.filter((s) => s.gamesA > s.gamesB).length;
    const setsWonB = this.state.completedSets.filter((s) => s.gamesB > s.gamesA).length;
    return setsWonA === 1 && setsWonB === 1;
  }
}
```

- [ ] **Step 4: Lancer les tests, vérifier le succès**

Run: `cd web && npx vitest run lib/scoreEngine.test.ts`
Expected: PASS (38 tests).

- [ ] **Step 5: Commit**

```bash
git add web/lib/scoreEngine.ts web/lib/scoreEngine.test.ts
git commit -m "feat(web): porter TennisScoreEngine en TypeScript"
```

---

### Task 5: Frontend — client API + route handlers proxy (JWT côté serveur uniquement)

**Files:**
- Modify: `web/lib/types.ts`
- Modify: `web/lib/api.ts`
- Modify: `web/lib/api.test.ts`
- Modify: `web/lib/auth.ts`
- Create: `web/app/api/console/sessions/route.ts`
- Create: `web/app/api/console/sessions/[sessionId]/points/route.ts`
- Create: `web/app/api/console/sessions/[sessionId]/points/last/route.ts`
- Create: `web/app/api/console/sessions/[sessionId]/score-seed/route.ts`
- Create: `web/app/api/console/sessions/[sessionId]/share/route.ts`
- Create: `web/app/api/console/sessions/[sessionId]/live-score/route.ts`
- Create: `web/app/api/console/sessions/[sessionId]/finalize/route.ts`
- Test: `web/app/api/console/sessions/route.test.ts`
- Test: `web/app/api/console/sessions/[sessionId]/points/route.test.ts`

**Interfaces:**
- Consumes : `TennisScoreEngine`/`MatchScore`/`SetResult` types (Task 4, valeurs de `currentGamePointsA`/`B` réutilisées telles quelles côté wire) ; `SESSION_COOKIE` (`web/lib/auth.ts`).
- Produces (consommé par les Tasks 6-8) : côté serveur (`web/lib/api.ts`) — `createSession`, `getPoints`, `postPoint`, `deleteLastPoint`, `putScoreSeed`, `getShareForSession`, `pushLiveScore`, `finalizeSession` (signatures ci-dessous) ; côté client (même origine, sans JWT) — routes `POST /api/console/sessions`, `GET|POST /api/console/sessions/{id}/points`, `DELETE /api/console/sessions/{id}/points/last`, `PUT /api/console/sessions/{id}/score-seed`, `GET /api/console/sessions/{id}/share`, `POST /api/console/sessions/{id}/live-score`, `POST /api/console/sessions/{id}/finalize`. Types `PointContext`, `PointDto`, `ScoreSeed`, `LiveScoreUpdatePayload`, `FinalizeSessionInput` exportés par `web/lib/types.ts`/`web/lib/api.ts`. Pas de fonction de création de lien de partage : la console ne fait que consulter un lien déjà créé par ailleurs (montre/téléphone) — cf. Global Constraints, push strictement opportuniste.

- [ ] **Step 1: Étendre `web/lib/types.ts`**

Modifier `web/lib/types.ts` :

```typescript
export type SetResult = { gamesA: number; gamesB: number };

export type LiveSnapshot = {
  status: "WAITING" | "LIVE" | "ENDED";
  completedSets: SetResult[];
  currentSetGamesA: number;
  currentSetGamesB: number;
  currentSetPointLog: ("A" | "B")[];
  currentGamePointsA: string;
  currentGamePointsB: string;
  tieBreakPointsA: number;
  tieBreakPointsB: number;
  isTieBreak: boolean;
  isSuperTieBreak: boolean;
  matchWinner: "A" | "B" | null;
  playerAName: string | null;
  playerBName: string | null;
  surface: string | null;
  tournament: string | null;
  competitionType: string | null;
  startedAt: number | null;
};

export type SessionDto = {
  id: number;
  surface: string;
  matchFormat: string;
  thirdSetRule: string;
  opponent: string | null;
  competitionType: string | null;
  tournament: string | null;
  status: string;
  sessionType: "MATCH" | "TRAINING";
  result: string | null;
  scoreText: string | null;
  scoreSeedJson: string | null;
  createdAt: number;
  updatedAt: number;
};

export type PointContext =
  | "ACE"
  | "WINNER"
  | "FORCED_ERROR"
  | "UNFORCED_ERROR_OPPONENT"
  | "ACE_OPPONENT"
  | "WINNER_OPPONENT"
  | "UNFORCED_ERROR_SELF"
  | "DOUBLE_FAULT";

export type PointDto = {
  id: number;
  sessionId: number;
  scorer: "A" | "B";
  context: PointContext | null;
  sequenceNum: number;
  recordedAt: number;
};

export type ScoreSeed = {
  completedSets: SetResult[];
  currentSetGamesA: number;
  currentSetGamesB: number;
  currentGamePointsA: string;
  currentGamePointsB: string;
  tieBreakPointsA: number;
  tieBreakPointsB: number;
  isTieBreak: boolean;
  isSuperTieBreak: boolean;
};
```

- [ ] **Step 2: Écrire les tests unitaires pour les nouvelles fonctions `api.ts` (doivent échouer)**

Ajouter à `web/lib/api.test.ts` :

```typescript
import {
  createSession,
  getPoints,
  postPoint,
  deleteLastPoint,
  putScoreSeed,
  getShareForSession,
  pushLiveScore,
  finalizeSession,
} from "./api";

describe("createSession", () => {
  it("envoie les champs surface/format/date en snake_case et mappe la réponse", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 201,
      json: async () => ({ ...rawSession, id: 42 }),
    });
    vi.stubGlobal("fetch", fetchMock);

    const result = await createSession("jwt-token", {
      surface: "CLAY",
      matchFormat: "BEST_OF_3",
      thirdSetRule: "FULL_ADVANTAGE",
      createdAt: 5_000_000,
    });

    expect(result.id).toBe(42);
    const [, init] = fetchMock.mock.calls[0];
    const body = JSON.parse(init.body as string);
    expect(body).toEqual({
      surface: "CLAY",
      match_format: "BEST_OF_3",
      third_set_rule: "FULL_ADVANTAGE",
      opponent: null,
      competition_type: null,
      tournament: null,
      created_at: 5_000_000,
    });
  });
});

describe("points client", () => {
  it("getPoints mappe la liste snake_case en PointDto[]", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({
          items: [
            { id: 1, session_id: 7, scorer: "A", context: "ACE", sequence_num: 1, recorded_at: 1000 },
          ],
        }),
      })
    );
    const points = await getPoints("jwt-token", 7);
    expect(points).toEqual([
      { id: 1, sessionId: 7, scorer: "A", context: "ACE", sequenceNum: 1, recordedAt: 1000 },
    ]);
  });

  it("postPoint envoie { context } et mappe la réponse", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 201,
      json: async () => ({ id: 2, session_id: 7, scorer: "B", context: "DOUBLE_FAULT", sequence_num: 2, recorded_at: 2000 }),
    });
    vi.stubGlobal("fetch", fetchMock);
    const point = await postPoint("jwt-token", 7, "DOUBLE_FAULT");
    expect(point.scorer).toBe("B");
    const [, init] = fetchMock.mock.calls[0];
    expect(JSON.parse(init.body as string)).toEqual({ context: "DOUBLE_FAULT" });
  });

  it("deleteLastPoint appelle DELETE sans lever d'erreur sur 204", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true, status: 204 }));
    await expect(deleteLastPoint("jwt-token", 7)).resolves.toBeUndefined();
  });
});

describe("putScoreSeed", () => {
  it("envoie le seed en snake_case et mappe la SessionDto retournée", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ ...rawSession, score_seed_json: '{"a":1}' }),
    });
    vi.stubGlobal("fetch", fetchMock);

    const result = await putScoreSeed("jwt-token", 7, {
      completedSets: [{ gamesA: 6, gamesB: 4 }],
      currentSetGamesA: 2,
      currentSetGamesB: 1,
      currentGamePointsA: "FORTY",
      currentGamePointsB: "THIRTY",
      tieBreakPointsA: 0,
      tieBreakPointsB: 0,
      isTieBreak: false,
      isSuperTieBreak: false,
    });

    expect(result.scoreSeedJson).toBe('{"a":1}');
    const [, init] = fetchMock.mock.calls[0];
    const body = JSON.parse(init.body as string);
    expect(body.completed_sets).toEqual([{ games_a: 6, games_b: 4 }]);
    expect(body.current_set_games_a).toBe(2);
  });
});

describe("live share client", () => {
  it("getShareForSession retourne null sur 404", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 404 }));
    expect(await getShareForSession("jwt-token", 7)).toBeNull();
  });

  it("getShareForSession retourne { token, url } quand le partage existe", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => ({ token: "abc", url: "https://x/live/abc" }) })
    );
    expect(await getShareForSession("jwt-token", 7)).toEqual({ token: "abc", url: "https://x/live/abc" });
  });

  it("pushLiveScore convertit le payload camelCase en snake_case", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 204 });
    vi.stubGlobal("fetch", fetchMock);
    await pushLiveScore("jwt-token", 7, {
      completedSets: [],
      currentSetGamesA: 1,
      currentSetGamesB: 0,
      currentSetPointLog: ["A"],
      currentGamePointsA: "FIFTEEN",
      currentGamePointsB: "ZERO",
      tieBreakPointsA: 0,
      tieBreakPointsB: 0,
      isTieBreak: false,
      isSuperTieBreak: false,
      isMatchOver: false,
      matchWinner: null,
      playerAName: "Benjamin",
      playerBName: "Marceau",
      surface: "CLAY",
      tournament: null,
      competitionType: null,
      startedAt: 1000,
    });
    const [, init] = fetchMock.mock.calls[0];
    const body = JSON.parse(init.body as string);
    expect(body.current_set_point_log).toEqual(["A"]);
    expect(body.player_a_name).toBe("Benjamin");
  });
});

describe("finalizeSession", () => {
  it("enveloppe la session dans un SyncSessionDto avec les champs post-completion à null", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => ({ synced_sessions: 1 }) });
    vi.stubGlobal("fetch", fetchMock);

    await finalizeSession("jwt-token", {
      session: {
        id: 7,
        surface: "CLAY",
        matchFormat: "BEST_OF_3",
        thirdSetRule: "FULL_ADVANTAGE",
        opponent: "Marceau",
        competitionType: null,
        tournament: null,
        status: "ACTIVE",
        sessionType: "MATCH",
        result: null,
        scoreText: null,
        scoreSeedJson: null,
        createdAt: 1000,
        updatedAt: 1000,
      },
      status: "COMPLETED",
      result: "VICTORY",
      scoreText: "6-4 · 6-3",
      updatedAt: 9999,
    });

    const [, init] = fetchMock.mock.calls[0];
    const body = JSON.parse(init.body as string);
    expect(body.sessions[0]).toEqual({
      client_id: 7,
      surface: "CLAY",
      match_format: "BEST_OF_3",
      third_set_rule: "FULL_ADVANTAGE",
      opponent: "Marceau",
      competition_type: null,
      tournament: null,
      status: "COMPLETED",
      session_type: "MATCH",
      result: "VICTORY",
      feeling_rating: null,
      feeling_comment: null,
      created_at: 1000,
      updated_at: 9999,
      scheduled_at: null,
      score_text: "6-4 · 6-3",
      first_serve_percent_self: null,
      first_serve_percent_opponent: null,
      winners_self: null,
      winners_opponent: null,
    });
  });
});
```

- [ ] **Step 3: Lancer les tests, vérifier l'échec**

Run: `cd web && npx vitest run lib/api.test.ts`
Expected: FAIL — les nouvelles fonctions n'existent pas encore.

- [ ] **Step 4: Implémenter les fonctions dans `web/lib/api.ts`**

Modifier `web/lib/api.ts` — remplacer `RawSession`/`mapSession` et ajouter le reste à la fin du fichier :

```typescript
import type {
  LiveSnapshot,
  SessionDto,
  SetResult,
  PointDto,
  PointContext,
  ScoreSeed,
} from "./types";

export class ShareNotFoundError extends Error {}
export class ShareExpiredError extends Error {}
export class UnauthorizedError extends Error {}

// ... (RawSnapshot / mapSnapshot / getLiveSnapshot inchangés) ...

type RawSession = {
  id: number;
  surface: string;
  match_format: string;
  third_set_rule: string;
  opponent: string | null;
  competition_type: string | null;
  tournament: string | null;
  status: string;
  session_type: string;
  result: string | null;
  score_text: string | null;
  score_seed_json: string | null;
  created_at: number;
  updated_at: number;
};

function mapSession(raw: RawSession): SessionDto {
  return {
    id: raw.id,
    surface: raw.surface,
    matchFormat: raw.match_format,
    thirdSetRule: raw.third_set_rule,
    opponent: raw.opponent,
    competitionType: raw.competition_type,
    tournament: raw.tournament,
    status: raw.status,
    sessionType: raw.session_type === "TRAINING" ? "TRAINING" : "MATCH",
    result: raw.result,
    scoreText: raw.score_text,
    scoreSeedJson: raw.score_seed_json,
    createdAt: raw.created_at,
    updatedAt: raw.updated_at,
  };
}

export async function getSessions(token: string): Promise<SessionDto[]> {
  const response = await fetch(`${process.env.API_BASE_URL}/api/v1/sessions`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (response.status === 401) throw new UnauthorizedError();
  if (!response.ok) throw new Error(`Erreur inattendue (${response.status})`);
  const raw = (await response.json()) as { items: RawSession[]; total: number };
  return raw.items.map(mapSession);
}

export type CreateSessionInput = {
  surface: string;
  matchFormat: string;
  thirdSetRule: string;
  opponent?: string | null;
  competitionType?: string | null;
  tournament?: string | null;
  createdAt: number;
};

export async function createSession(token: string, input: CreateSessionInput): Promise<SessionDto> {
  const response = await fetch(`${process.env.API_BASE_URL}/api/v1/sessions`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      surface: input.surface,
      match_format: input.matchFormat,
      third_set_rule: input.thirdSetRule,
      opponent: input.opponent ?? null,
      competition_type: input.competitionType ?? null,
      tournament: input.tournament ?? null,
      created_at: input.createdAt,
    }),
    cache: "no-store",
  });
  if (response.status === 401) throw new UnauthorizedError();
  if (!response.ok) throw new Error(`Erreur inattendue (${response.status})`);
  const raw = (await response.json()) as RawSession;
  return mapSession(raw);
}

type RawPoint = {
  id: number;
  session_id: number;
  scorer: "A" | "B";
  context: string | null;
  sequence_num: number;
  recorded_at: number;
};

function mapPoint(raw: RawPoint): PointDto {
  return {
    id: raw.id,
    sessionId: raw.session_id,
    scorer: raw.scorer,
    context: raw.context as PointContext | null,
    sequenceNum: raw.sequence_num,
    recordedAt: raw.recorded_at,
  };
}

export async function getPoints(token: string, sessionId: number): Promise<PointDto[]> {
  const response = await fetch(`${process.env.API_BASE_URL}/api/v1/sessions/${sessionId}/points`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (response.status === 401) throw new UnauthorizedError();
  if (!response.ok) throw new Error(`Erreur inattendue (${response.status})`);
  const raw = (await response.json()) as { items: RawPoint[] };
  return raw.items.map(mapPoint);
}

export async function postPoint(token: string, sessionId: number, context: PointContext): Promise<PointDto> {
  const response = await fetch(`${process.env.API_BASE_URL}/api/v1/sessions/${sessionId}/points`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ context }),
    cache: "no-store",
  });
  if (response.status === 401) throw new UnauthorizedError();
  if (!response.ok) throw new Error(`Erreur inattendue (${response.status})`);
  const raw = (await response.json()) as RawPoint;
  return mapPoint(raw);
}

export async function deleteLastPoint(token: string, sessionId: number): Promise<void> {
  const response = await fetch(`${process.env.API_BASE_URL}/api/v1/sessions/${sessionId}/points/last`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (response.status === 401) throw new UnauthorizedError();
  if (!response.ok) throw new Error(`Erreur inattendue (${response.status})`);
}

export async function putScoreSeed(token: string, sessionId: number, seed: ScoreSeed): Promise<SessionDto> {
  const response = await fetch(`${process.env.API_BASE_URL}/api/v1/sessions/${sessionId}/score-seed`, {
    method: "PUT",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      completed_sets: seed.completedSets.map((s) => ({ games_a: s.gamesA, games_b: s.gamesB })),
      current_set_games_a: seed.currentSetGamesA,
      current_set_games_b: seed.currentSetGamesB,
      current_game_points_a: seed.currentGamePointsA,
      current_game_points_b: seed.currentGamePointsB,
      tie_break_points_a: seed.tieBreakPointsA,
      tie_break_points_b: seed.tieBreakPointsB,
      is_tie_break: seed.isTieBreak,
      is_super_tie_break: seed.isSuperTieBreak,
    }),
    cache: "no-store",
  });
  if (response.status === 401) throw new UnauthorizedError();
  if (!response.ok) throw new Error(`Erreur inattendue (${response.status})`);
  const raw = (await response.json()) as RawSession;
  return mapSession(raw);
}

export async function getShareForSession(
  token: string,
  sessionId: number
): Promise<{ token: string; url: string } | null> {
  const response = await fetch(`${process.env.API_BASE_URL}/api/v1/live/shares/by-session/${sessionId}`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (response.status === 404) return null;
  if (response.status === 401) throw new UnauthorizedError();
  if (!response.ok) throw new Error(`Erreur inattendue (${response.status})`);
  return (await response.json()) as { token: string; url: string };
}

export type LiveScoreUpdatePayload = {
  completedSets: SetResult[];
  currentSetGamesA: number;
  currentSetGamesB: number;
  currentSetPointLog: ("A" | "B")[];
  currentGamePointsA: string;
  currentGamePointsB: string;
  tieBreakPointsA: number;
  tieBreakPointsB: number;
  isTieBreak: boolean;
  isSuperTieBreak: boolean;
  isMatchOver: boolean;
  matchWinner: "A" | "B" | null;
  playerAName: string;
  playerBName: string;
  surface: string;
  tournament: string | null;
  competitionType: string | null;
  startedAt: number;
};

export async function pushLiveScore(token: string, sessionId: number, payload: LiveScoreUpdatePayload): Promise<void> {
  const response = await fetch(`${process.env.API_BASE_URL}/api/v1/live/sessions/${sessionId}/score`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      completed_sets: payload.completedSets.map((s) => ({ games_a: s.gamesA, games_b: s.gamesB })),
      current_set_games_a: payload.currentSetGamesA,
      current_set_games_b: payload.currentSetGamesB,
      current_set_point_log: payload.currentSetPointLog,
      current_game_points_a: payload.currentGamePointsA,
      current_game_points_b: payload.currentGamePointsB,
      tie_break_points_a: payload.tieBreakPointsA,
      tie_break_points_b: payload.tieBreakPointsB,
      is_tie_break: payload.isTieBreak,
      is_super_tie_break: payload.isSuperTieBreak,
      is_match_over: payload.isMatchOver,
      match_winner: payload.matchWinner,
      player_a_name: payload.playerAName,
      player_b_name: payload.playerBName,
      surface: payload.surface,
      tournament: payload.tournament,
      competition_type: payload.competitionType,
      started_at: payload.startedAt,
    }),
    cache: "no-store",
  });
  if (!response.ok) throw new Error(`Erreur inattendue (${response.status})`);
}

export type FinalizeSessionInput = {
  session: SessionDto;
  status: "COMPLETED" | "ACTIVE";
  result: "VICTORY" | "DEFEAT" | null;
  scoreText: string | null;
  updatedAt: number;
};

export async function finalizeSession(token: string, input: FinalizeSessionInput): Promise<void> {
  const response = await fetch(`${process.env.API_BASE_URL}/api/v1/sync/push`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      sessions: [
        {
          client_id: input.session.id,
          surface: input.session.surface,
          match_format: input.session.matchFormat,
          third_set_rule: input.session.thirdSetRule,
          opponent: input.session.opponent,
          competition_type: input.session.competitionType,
          tournament: input.session.tournament,
          status: input.status,
          session_type: input.session.sessionType,
          result: input.result,
          feeling_rating: null,
          feeling_comment: null,
          created_at: input.session.createdAt,
          updated_at: input.updatedAt,
          scheduled_at: null,
          score_text: input.scoreText,
          first_serve_percent_self: null,
          first_serve_percent_opponent: null,
          winners_self: null,
          winners_opponent: null,
        },
      ],
      deleted_session_ids: [],
    }),
    cache: "no-store",
  });
  if (!response.ok) throw new Error(`Erreur inattendue (${response.status})`);
}
```

Note : le corps du fichier entre `ShareNotFoundError`/`UnauthorizedError` et `RawSession` (le type `RawSnapshot`, `mapSnapshot`, `getLiveSnapshot`) reste inchangé — seuls `RawSession`/`mapSession`/`getSessions` sont remplacés (ajout de `score_seed_json`) et le reste est ajouté à la suite.

- [ ] **Step 5: Lancer les tests, vérifier le succès**

Run: `cd web && npx vitest run lib/api.test.ts`
Expected: PASS.

- [ ] **Step 6: Ajouter `getSessionToken` à `web/lib/auth.ts`**

Modifier `web/lib/auth.ts` :

```typescript
import { cookies } from "next/headers";

export const SESSION_COOKIE = "ss_session";
export const SESSION_MAX_AGE_SECONDS = 60 * 60 * 24 * 30; // 30 jours — aligné sur JWTManager.create_token() côté backend

export async function getSessionToken(): Promise<string | null> {
  const store = await cookies();
  return store.get(SESSION_COOKIE)?.value ?? null;
}
```

- [ ] **Step 7: Écrire les tests des route handlers (doivent échouer — fichiers inexistants)**

Créer `web/app/api/console/sessions/route.test.ts` :

```typescript
// @vitest-environment node
import { describe, expect, it, vi, afterEach } from "vitest";

vi.mock("next/headers", () => ({ cookies: vi.fn() }));
vi.mock("@/lib/api", () => ({ createSession: vi.fn(), UnauthorizedError: class UnauthorizedError extends Error {} }));

import { cookies } from "next/headers";
import { createSession } from "@/lib/api";
import { POST } from "./route";

afterEach(() => {
  vi.clearAllMocks();
});

function jsonRequest(body: unknown): Request {
  return new Request("http://localhost:3000/api/console/sessions", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

describe("POST /api/console/sessions", () => {
  it("retourne 401 si aucun cookie de session", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => undefined } as never);
    const response = await POST(jsonRequest({ surface: "CLAY" }));
    expect(response.status).toBe(401);
  });

  it("relaie vers createSession avec le token du cookie et retourne la session créée", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(createSession).mockResolvedValue({
      id: 5,
      surface: "CLAY",
      matchFormat: "BEST_OF_3",
      thirdSetRule: "FULL_ADVANTAGE",
      opponent: null,
      competitionType: null,
      tournament: null,
      status: "ACTIVE",
      sessionType: "MATCH",
      result: null,
      scoreText: null,
      scoreSeedJson: null,
      createdAt: 1000,
      updatedAt: 1000,
    });

    const response = await POST(
      jsonRequest({ surface: "CLAY", matchFormat: "BEST_OF_3", thirdSetRule: "FULL_ADVANTAGE", createdAt: 1000 })
    );

    expect(response.status).toBe(200);
    const data = await response.json();
    expect(data.id).toBe(5);
    expect(vi.mocked(createSession)).toHaveBeenCalledWith("jwt-abc", {
      surface: "CLAY",
      matchFormat: "BEST_OF_3",
      thirdSetRule: "FULL_ADVANTAGE",
      opponent: undefined,
      competitionType: undefined,
      tournament: undefined,
      createdAt: 1000,
    });
  });
});
```

Créer `web/app/api/console/sessions/[sessionId]/points/route.test.ts` :

```typescript
// @vitest-environment node
import { describe, expect, it, vi, afterEach } from "vitest";

vi.mock("next/headers", () => ({ cookies: vi.fn() }));
vi.mock("@/lib/api", () => ({
  getPoints: vi.fn(),
  postPoint: vi.fn(),
  UnauthorizedError: class UnauthorizedError extends Error {},
}));

import { cookies } from "next/headers";
import { getPoints, postPoint } from "@/lib/api";
import { GET, POST } from "./route";

afterEach(() => {
  vi.clearAllMocks();
});

function params(sessionId: string) {
  return { params: Promise.resolve({ sessionId }) };
}

describe("GET /api/console/sessions/[sessionId]/points", () => {
  it("retourne 401 sans cookie", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => undefined } as never);
    const response = await GET(new Request("http://localhost/x"), params("7"));
    expect(response.status).toBe(401);
  });

  it("relaie vers getPoints et retourne { items }", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(getPoints).mockResolvedValue([
      { id: 1, sessionId: 7, scorer: "A", context: "ACE", sequenceNum: 1, recordedAt: 1000 },
    ]);
    const response = await GET(new Request("http://localhost/x"), params("7"));
    expect(response.status).toBe(200);
    const data = await response.json();
    expect(data.items).toHaveLength(1);
    expect(vi.mocked(getPoints)).toHaveBeenCalledWith("jwt-abc", 7);
  });
});

describe("POST /api/console/sessions/[sessionId]/points", () => {
  it("relaie le context vers postPoint", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(postPoint).mockResolvedValue({
      id: 2,
      sessionId: 7,
      scorer: "B",
      context: "DOUBLE_FAULT",
      sequenceNum: 2,
      recordedAt: 2000,
    });
    const request = new Request("http://localhost/x", {
      method: "POST",
      body: JSON.stringify({ context: "DOUBLE_FAULT" }),
    });
    const response = await POST(request, params("7"));
    expect(response.status).toBe(200);
    expect(vi.mocked(postPoint)).toHaveBeenCalledWith("jwt-abc", 7, "DOUBLE_FAULT");
  });
});
```

- [ ] **Step 8: Lancer les tests, vérifier l'échec**

Run: `cd web && npx vitest run "app/api/console/sessions/route.test.ts" "app/api/console/sessions/[sessionId]/points/route.test.ts"`
Expected: FAIL — `Cannot find module './route'`.

- [ ] **Step 9: Implémenter les route handlers**

Créer `web/app/api/console/sessions/route.ts` :

```typescript
import { NextResponse } from "next/server";
import { getSessionToken } from "@/lib/auth";
import { createSession, UnauthorizedError } from "@/lib/api";

export async function POST(request: Request) {
  const token = await getSessionToken();
  if (!token) return NextResponse.json({ error: "unauthorized" }, { status: 401 });

  const body = await request.json();
  try {
    const session = await createSession(token, {
      surface: body.surface,
      matchFormat: body.matchFormat,
      thirdSetRule: body.thirdSetRule,
      opponent: body.opponent,
      competitionType: body.competitionType,
      tournament: body.tournament,
      createdAt: body.createdAt,
    });
    return NextResponse.json(session);
  } catch (error) {
    if (error instanceof UnauthorizedError) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
    throw error;
  }
}
```

Créer `web/app/api/console/sessions/[sessionId]/points/route.ts` :

```typescript
import { NextResponse } from "next/server";
import { getSessionToken } from "@/lib/auth";
import { getPoints, postPoint, UnauthorizedError } from "@/lib/api";
import type { PointContext } from "@/lib/types";

type RouteParams = { params: Promise<{ sessionId: string }> };

export async function GET(request: Request, { params }: RouteParams) {
  const token = await getSessionToken();
  if (!token) return NextResponse.json({ error: "unauthorized" }, { status: 401 });

  const { sessionId } = await params;
  try {
    const items = await getPoints(token, Number(sessionId));
    return NextResponse.json({ items });
  } catch (error) {
    if (error instanceof UnauthorizedError) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
    throw error;
  }
}

export async function POST(request: Request, { params }: RouteParams) {
  const token = await getSessionToken();
  if (!token) return NextResponse.json({ error: "unauthorized" }, { status: 401 });

  const { sessionId } = await params;
  const body = (await request.json()) as { context: PointContext };
  try {
    const point = await postPoint(token, Number(sessionId), body.context);
    return NextResponse.json(point);
  } catch (error) {
    if (error instanceof UnauthorizedError) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
    throw error;
  }
}
```

Créer `web/app/api/console/sessions/[sessionId]/points/last/route.ts` :

```typescript
import { NextResponse } from "next/server";
import { getSessionToken } from "@/lib/auth";
import { deleteLastPoint, UnauthorizedError } from "@/lib/api";

type RouteParams = { params: Promise<{ sessionId: string }> };

export async function DELETE(request: Request, { params }: RouteParams) {
  const token = await getSessionToken();
  if (!token) return NextResponse.json({ error: "unauthorized" }, { status: 401 });

  const { sessionId } = await params;
  try {
    await deleteLastPoint(token, Number(sessionId));
    return new NextResponse(null, { status: 204 });
  } catch (error) {
    if (error instanceof UnauthorizedError) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
    throw error;
  }
}
```

Créer `web/app/api/console/sessions/[sessionId]/score-seed/route.ts` :

```typescript
import { NextResponse } from "next/server";
import { getSessionToken } from "@/lib/auth";
import { putScoreSeed, UnauthorizedError } from "@/lib/api";
import type { ScoreSeed } from "@/lib/types";

type RouteParams = { params: Promise<{ sessionId: string }> };

export async function PUT(request: Request, { params }: RouteParams) {
  const token = await getSessionToken();
  if (!token) return NextResponse.json({ error: "unauthorized" }, { status: 401 });

  const { sessionId } = await params;
  const body = (await request.json()) as ScoreSeed;
  try {
    const session = await putScoreSeed(token, Number(sessionId), body);
    return NextResponse.json(session);
  } catch (error) {
    if (error instanceof UnauthorizedError) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
    throw error;
  }
}
```

Créer `web/app/api/console/sessions/[sessionId]/share/route.ts` :

```typescript
import { NextResponse } from "next/server";
import { getSessionToken } from "@/lib/auth";
import { getShareForSession, UnauthorizedError } from "@/lib/api";

type RouteParams = { params: Promise<{ sessionId: string }> };

export async function GET(request: Request, { params }: RouteParams) {
  const token = await getSessionToken();
  if (!token) return NextResponse.json({ error: "unauthorized" }, { status: 401 });

  const { sessionId } = await params;
  try {
    const share = await getShareForSession(token, Number(sessionId));
    return NextResponse.json(share);
  } catch (error) {
    if (error instanceof UnauthorizedError) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
    throw error;
  }
}
```

Créer `web/app/api/console/sessions/[sessionId]/live-score/route.ts` :

```typescript
import { NextResponse } from "next/server";
import { getSessionToken } from "@/lib/auth";
import { pushLiveScore } from "@/lib/api";
import type { LiveScoreUpdatePayload } from "@/lib/api";

type RouteParams = { params: Promise<{ sessionId: string }> };

export async function POST(request: Request, { params }: RouteParams) {
  const token = await getSessionToken();
  if (!token) return NextResponse.json({ error: "unauthorized" }, { status: 401 });

  const { sessionId } = await params;
  const body = (await request.json()) as LiveScoreUpdatePayload;
  await pushLiveScore(token, Number(sessionId), body);
  return new NextResponse(null, { status: 204 });
}
```

Créer `web/app/api/console/sessions/[sessionId]/finalize/route.ts` :

```typescript
import { NextResponse } from "next/server";
import { getSessionToken } from "@/lib/auth";
import { finalizeSession } from "@/lib/api";
import type { SessionDto } from "@/lib/types";

type RouteParams = { params: Promise<{ sessionId: string }> };
type FinalizeBody = {
  session: SessionDto;
  status: "COMPLETED" | "ACTIVE";
  result: "VICTORY" | "DEFEAT" | null;
  scoreText: string | null;
  updatedAt: number;
};

export async function POST(request: Request, { params }: RouteParams) {
  const token = await getSessionToken();
  if (!token) return NextResponse.json({ error: "unauthorized" }, { status: 401 });

  await params; // sessionId déjà présent dans body.session.id — conservé pour cohérence de route
  const body = (await request.json()) as FinalizeBody;
  await finalizeSession(token, body);
  return new NextResponse(null, { status: 204 });
}
```

- [ ] **Step 10: Lancer les tests, vérifier le succès**

Run: `cd web && npx vitest run "app/api/console/sessions/route.test.ts" "app/api/console/sessions/[sessionId]/points/route.test.ts"`
Expected: PASS.

- [ ] **Step 11: Lancer toute la suite web pour vérifier l'absence de régression**

Run: `cd web && npx vitest run`
Expected: PASS (tous les tests, y compris ceux modifiés à l'étape 2).

- [ ] **Step 12: Commit**

```bash
git add web/lib/types.ts web/lib/api.ts web/lib/api.test.ts web/lib/auth.ts web/app/api/console
git commit -m "feat(web): client API console + route handlers proxy authentifiés"
```

---

### Task 6: Frontend — navigation Sidebar + écran de sélection `/dashboard/console`

**Files:**
- Modify: `web/components/dashboard/Sidebar.tsx`
- Modify: `web/components/dashboard/Sidebar.module.css`
- Create: `web/components/dashboard/Sidebar.test.tsx`
- Create: `web/components/console/ConsoleSelectionView.tsx`
- Create: `web/components/console/ConsoleSelectionView.module.css`
- Create: `web/components/console/ConsoleSelectionView.test.tsx`
- Create: `web/components/console/NewMatchForm.tsx`
- Create: `web/components/console/NewMatchForm.module.css`
- Create: `web/components/console/NewMatchForm.test.tsx`
- Create: `web/app/dashboard/console/page.tsx`

**Interfaces:**
- Consumes : `SessionDto` (`web/lib/types.ts`), `getSessions`/`UnauthorizedError` (`web/lib/api.ts`, Task 5), `surfaceLabel` (`web/lib/surfaces.ts`), route `POST /api/console/sessions` (Task 5).
- Produces (consommé par Task 7) : `ConsoleSelectionView` accepte `activeSessions: SessionDto[]` en props ; le bouton « Reprendre » navigue vers `/dashboard/console/{id}` — Task 7 interceptera ce clic pour insérer le formulaire de score de départ.

- [ ] **Step 1: Écrire le test du Sidebar (doit échouer)**

Créer `web/components/dashboard/Sidebar.test.tsx` :

```typescript
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";

const usePathnameMock = vi.fn();
vi.mock("next/navigation", () => ({ usePathname: () => usePathnameMock() }));

import { Sidebar } from "./Sidebar";

describe("Sidebar", () => {
  it("met en avant Tableau de bord sur /dashboard", () => {
    usePathnameMock.mockReturnValue("/dashboard");
    render(<Sidebar />);
    expect(screen.getByRole("link", { name: /tableau de bord/i })).toHaveClass("navItemActive");
    expect(screen.getByRole("link", { name: /console de saisie/i })).not.toHaveClass("navItemActive");
  });

  it("met en avant Console de saisie sur /dashboard/console", () => {
    usePathnameMock.mockReturnValue("/dashboard/console");
    render(<Sidebar />);
    expect(screen.getByRole("link", { name: /console de saisie/i })).toHaveClass("navItemActive");
    expect(screen.getByRole("link", { name: /tableau de bord/i })).not.toHaveClass("navItemActive");
  });

  it("met en avant Console de saisie sur une sous-route /dashboard/console/{id}", () => {
    usePathnameMock.mockReturnValue("/dashboard/console/42");
    render(<Sidebar />);
    expect(screen.getByRole("link", { name: /console de saisie/i })).toHaveClass("navItemActive");
  });
});
```

- [ ] **Step 2: Lancer le test, vérifier l'échec**

Run: `cd web && npx vitest run components/dashboard/Sidebar.test.tsx`
Expected: FAIL — `Sidebar` ne rend pas encore de `<Link>` (rendu actuel = `<div>` statique unique).

- [ ] **Step 3: Convertir le Sidebar en composant client avec navigation multi-item**

Modifier `web/components/dashboard/Sidebar.tsx` :

```tsx
"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import styles from "./Sidebar.module.css";

const NAV_ITEMS = [
  { href: "/dashboard", label: "Tableau de bord" },
  { href: "/dashboard/console", label: "Console de saisie" },
];

export function Sidebar() {
  const pathname = usePathname();

  return (
    <aside className={styles.sidebar}>
      <div className={styles.logo}>SecondServe</div>
      <nav className={styles.nav}>
        {NAV_ITEMS.map((item) => {
          const isActive = item.href === "/dashboard" ? pathname === item.href : pathname.startsWith(item.href);
          return (
            <Link key={item.href} href={item.href} className={isActive ? styles.navItemActive : styles.navItem}>
              <span className={styles.dot} />
              {item.label}
            </Link>
          );
        })}
      </nav>
      <div className={styles.profile}>
        <span className={styles.profileName}>Benjamin</span>
        <form action="/logout" method="POST">
          <button type="submit" className={styles.logoutButton}>
            Déconnexion
          </button>
        </form>
      </div>
    </aside>
  );
}
```

Modifier `web/components/dashboard/Sidebar.module.css` — ajouter la classe `.navItem` (état inactif) après `.navItemActive` :

```css
.navItemActive {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--ss-text);
  color: var(--ss-bg);
  border-radius: 10px;
  padding: 10px 14px;
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
}

.navItem {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--ss-muted);
  border-radius: 10px;
  padding: 10px 14px;
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
}
```

(Le reste de `Sidebar.module.css` — `.sidebar`, `.logo`, `.nav`, `.dot`, `.profile`, `.profileName`, `.logoutButton` — est inchangé.)

- [ ] **Step 4: Lancer le test, vérifier le succès**

Run: `cd web && npx vitest run components/dashboard/Sidebar.test.tsx`
Expected: PASS.

- [ ] **Step 5: Écrire les tests de `NewMatchForm` (doivent échouer — composant inexistant)**

Créer `web/components/console/NewMatchForm.test.tsx` :

```typescript
import { describe, expect, it, vi, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";

const pushMock = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: pushMock }) }));

import { NewMatchForm } from "./NewMatchForm";

afterEach(() => {
  vi.unstubAllGlobals();
  pushMock.mockClear();
});

describe("NewMatchForm", () => {
  it("appelle onCancel au clic sur Annuler", () => {
    const onCancel = vi.fn();
    render(<NewMatchForm onCancel={onCancel} />);
    fireEvent.click(screen.getByRole("button", { name: /annuler/i }));
    expect(onCancel).toHaveBeenCalled();
  });

  it("soumet le formulaire et redirige vers la console de la session créée", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({ ok: true, json: async () => ({ id: 99 }) })
    );
    render(<NewMatchForm onCancel={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: /créer et commencer/i }));
    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/dashboard/console/99"));
  });

  it("affiche une erreur si la création échoue", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false }));
    render(<NewMatchForm onCancel={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: /créer et commencer/i }));
    await waitFor(() => expect(screen.getByText(/échec de la création/i)).toBeInTheDocument());
  });
});
```

- [ ] **Step 6: Lancer les tests, vérifier l'échec**

Run: `cd web && npx vitest run components/console/NewMatchForm.test.tsx`
Expected: FAIL — `Cannot find module './NewMatchForm'`.

- [ ] **Step 7: Implémenter `NewMatchForm`**

Créer `web/components/console/NewMatchForm.tsx` :

```tsx
"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import styles from "./NewMatchForm.module.css";

type Props = { onCancel: () => void };

function todayDateInputValue(): string {
  return new Date().toISOString().slice(0, 10);
}

export function NewMatchForm({ onCancel }: Props) {
  const router = useRouter();
  const [surface, setSurface] = useState("CLAY");
  const [matchFormat, setMatchFormat] = useState("BEST_OF_3");
  const [thirdSetRule, setThirdSetRule] = useState("FULL_ADVANTAGE");
  const [opponent, setOpponent] = useState("");
  const [date, setDate] = useState(todayDateInputValue());
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      const response = await fetch("/api/console/sessions", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          surface,
          matchFormat,
          thirdSetRule,
          opponent: opponent || null,
          createdAt: new Date(date).getTime(),
        }),
      });
      if (!response.ok) throw new Error("create_failed");
      const session = await response.json();
      router.push(`/dashboard/console/${session.id}`);
    } catch {
      setError("Échec de la création du match, réessayez.");
      setSubmitting(false);
    }
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit}>
      <label className={styles.field}>
        Surface
        <select value={surface} onChange={(e) => setSurface(e.target.value)}>
          <option value="CLAY">Terre battue</option>
          <option value="HARD">Dur</option>
          <option value="GRASS">Gazon</option>
          <option value="CARPET">Indoor</option>
        </select>
      </label>

      <label className={styles.field}>
        Format
        <select value={matchFormat} onChange={(e) => setMatchFormat(e.target.value)}>
          <option value="BEST_OF_1">1 set</option>
          <option value="BEST_OF_3">3 sets</option>
        </select>
      </label>

      <label className={styles.field}>
        Règle du 3e set
        <select value={thirdSetRule} onChange={(e) => setThirdSetRule(e.target.value)}>
          <option value="FULL_ADVANTAGE">Set complet</option>
          <option value="SUPER_TIE_BREAK_10">Super tie-break à 10</option>
          <option value="SHORT_DECISIVE_SET">Jeu décisif court</option>
        </select>
      </label>

      <label className={styles.field}>
        Adversaire
        <input type="text" value={opponent} onChange={(e) => setOpponent(e.target.value)} />
      </label>

      <label className={styles.field}>
        Date du match
        <input type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
      </label>

      {error && <p className={styles.error}>{error}</p>}

      <div className={styles.actions}>
        <button type="button" className={styles.cancelButton} onClick={onCancel} disabled={submitting}>
          Annuler
        </button>
        <button type="submit" className={styles.submitButton} disabled={submitting}>
          {submitting ? "Création..." : "Créer et commencer la saisie"}
        </button>
      </div>
    </form>
  );
}
```

Créer `web/components/console/NewMatchForm.module.css` :

```css
.form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: var(--ss-surface);
  border: 1px solid var(--ss-border);
  border-radius: 12px;
  padding: 20px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--ss-muted);
}

.field select,
.field input {
  min-height: 48px;
  border-radius: 8px;
  border: 1px solid var(--ss-border);
  background: var(--ss-bg);
  color: var(--ss-text);
  padding: 0 12px;
  font-size: 14px;
}

.error {
  color: var(--ss-hot);
  font-size: 13px;
  margin: 0;
}

.actions {
  display: flex;
  gap: 12px;
}

.cancelButton,
.submitButton {
  flex: 1;
  min-height: 48px;
  border-radius: 10px;
  font-weight: 700;
  font-size: 14px;
  cursor: pointer;
}

.cancelButton {
  background: transparent;
  border: 1px solid var(--ss-border);
  color: var(--ss-muted);
}

.submitButton {
  border: none;
  background: var(--ss-lime);
  color: var(--ss-lime-text);
}
```

- [ ] **Step 8: Lancer les tests, vérifier le succès**

Run: `cd web && npx vitest run components/console/NewMatchForm.test.tsx`
Expected: PASS.

- [ ] **Step 9: Écrire les tests de `ConsoleSelectionView` (doivent échouer — composant inexistant)**

Créer `web/components/console/ConsoleSelectionView.test.tsx` :

```typescript
import { describe, expect, it, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import type { SessionDto } from "@/lib/types";

const pushMock = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: pushMock }) }));

import { ConsoleSelectionView } from "./ConsoleSelectionView";

function session(overrides: Partial<SessionDto> = {}): SessionDto {
  return {
    id: 1,
    surface: "CLAY",
    matchFormat: "BEST_OF_3",
    thirdSetRule: "FULL_ADVANTAGE",
    opponent: "Marceau",
    competitionType: null,
    tournament: null,
    status: "ACTIVE",
    sessionType: "MATCH",
    result: null,
    scoreText: null,
    scoreSeedJson: null,
    createdAt: 1000,
    updatedAt: 1000,
    ...overrides,
  };
}

describe("ConsoleSelectionView", () => {
  it("affiche un état vide sobre sans session active", () => {
    render(<ConsoleSelectionView activeSessions={[]} />);
    expect(screen.getByText(/aucune session active/i)).toBeInTheDocument();
  });

  it("liste les sessions actives et navigue vers la console au clic sur Reprendre", () => {
    render(<ConsoleSelectionView activeSessions={[session({ id: 7 })]} />);
    expect(screen.getByText(/marceau/i)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /reprendre/i }));
    expect(pushMock).toHaveBeenCalledWith("/dashboard/console/7");
  });

  it("affiche le formulaire Nouveau match au clic sur le bouton", () => {
    render(<ConsoleSelectionView activeSessions={[]} />);
    fireEvent.click(screen.getByRole("button", { name: /^nouveau match$/i }));
    expect(screen.getByText(/date du match/i)).toBeInTheDocument();
  });
});
```

- [ ] **Step 10: Lancer les tests, vérifier l'échec**

Run: `cd web && npx vitest run components/console/ConsoleSelectionView.test.tsx`
Expected: FAIL — `Cannot find module './ConsoleSelectionView'`.

- [ ] **Step 11: Implémenter `ConsoleSelectionView`**

Créer `web/components/console/ConsoleSelectionView.tsx` :

```tsx
"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import type { SessionDto } from "@/lib/types";
import { surfaceLabel } from "@/lib/surfaces";
import { NewMatchForm } from "./NewMatchForm";
import styles from "./ConsoleSelectionView.module.css";

type Props = { activeSessions: SessionDto[] };

export function ConsoleSelectionView({ activeSessions }: Props) {
  const router = useRouter();
  const [showNewMatchForm, setShowNewMatchForm] = useState(false);

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>Console de saisie</h1>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Reprendre une session active</h2>
        {activeSessions.length === 0 ? (
          <p className={styles.empty}>Aucune session active à reprendre.</p>
        ) : (
          <ul className={styles.list}>
            {activeSessions.map((session) => (
              <li key={session.id} className={styles.listItem}>
                <span className={styles.listItemLabel}>
                  {session.opponent ?? "Adversaire"} · {surfaceLabel(session.surface)}
                </span>
                <button
                  type="button"
                  className={styles.resumeButton}
                  onClick={() => router.push(`/dashboard/console/${session.id}`)}
                >
                  Reprendre
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className={styles.section}>
        {showNewMatchForm ? (
          <NewMatchForm onCancel={() => setShowNewMatchForm(false)} />
        ) : (
          <button type="button" className={styles.newMatchButton} onClick={() => setShowNewMatchForm(true)}>
            Nouveau match
          </button>
        )}
      </section>
    </div>
  );
}
```

Créer `web/components/console/ConsoleSelectionView.module.css` :

```css
.container {
  padding: 24px 16px;
  max-width: 640px;
}

.title {
  font-family: var(--font-barlow), sans-serif;
  font-weight: 800;
  font-size: 24px;
  margin: 0 0 24px;
  color: var(--ss-text);
}

.section {
  margin-bottom: 32px;
}

.sectionTitle {
  font-size: 14px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 1px;
  color: var(--ss-muted);
  margin: 0 0 12px;
}

.empty {
  color: var(--ss-faint);
  font-size: 14px;
}

.list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.listItem {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: var(--ss-surface);
  border: 1px solid var(--ss-border);
  border-radius: 12px;
  padding: 12px 16px;
}

.listItemLabel {
  font-size: 14px;
  font-weight: 600;
  color: var(--ss-text);
}

.resumeButton,
.newMatchButton {
  min-height: 48px;
  padding: 0 20px;
  border-radius: 10px;
  border: none;
  background: var(--ss-text);
  color: var(--ss-bg);
  font-weight: 700;
  font-size: 14px;
  cursor: pointer;
}

.newMatchButton {
  width: 100%;
}
```

- [ ] **Step 12: Lancer les tests, vérifier le succès**

Run: `cd web && npx vitest run components/console/ConsoleSelectionView.test.tsx`
Expected: PASS.

- [ ] **Step 13: Créer la page serveur `/dashboard/console`**

Créer `web/app/dashboard/console/page.tsx` :

```tsx
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { SESSION_COOKIE } from "@/lib/auth";
import { getSessions, UnauthorizedError } from "@/lib/api";
import { ConsoleSelectionView } from "@/components/console/ConsoleSelectionView";

export default async function ConsolePage() {
  const store = await cookies();
  const token = store.get(SESSION_COOKIE)?.value;
  if (!token) redirect("/login");

  let sessions;
  try {
    sessions = await getSessions(token);
  } catch (error) {
    if (error instanceof UnauthorizedError) redirect("/login");
    throw error;
  }

  const activeSessions = sessions.filter((s) => s.status === "ACTIVE");

  return <ConsoleSelectionView activeSessions={activeSessions} />;
}
```

- [ ] **Step 14: Lancer toute la suite web, vérifier l'absence de régression**

Run: `cd web && npx vitest run`
Expected: PASS.

- [ ] **Step 15: Commit**

```bash
git add web/components/dashboard/Sidebar.tsx web/components/dashboard/Sidebar.module.css web/components/dashboard/Sidebar.test.tsx web/components/console web/app/dashboard/console/page.tsx
git commit -m "feat(web): nav Sidebar + écran de sélection de la console de saisie"
```

---

### Task 7: Frontend — formulaire « score de départ » (reprise en mode secours)

**Files:**
- Create: `web/components/console/ScoreSeedForm.tsx`
- Create: `web/components/console/ScoreSeedForm.module.css`
- Create: `web/components/console/ScoreSeedForm.test.tsx`
- Modify: `web/components/console/ConsoleSelectionView.tsx`
- Modify: `web/components/console/ConsoleSelectionView.module.css`
- Modify: `web/components/console/ConsoleSelectionView.test.tsx`

**Interfaces:**
- Consumes : route `PUT /api/console/sessions/{id}/score-seed` (Task 5).
- Produces : `ScoreSeedForm` props `{ sessionId: number; onCancel: () => void; onSeeded: () => void }` — `onSeeded` déclenche la navigation vers `/dashboard/console/{id}` côté appelant (Task 8 consommera cette même page).

- [ ] **Step 1: Écrire les tests de `ScoreSeedForm` (doivent échouer — composant inexistant)**

Créer `web/components/console/ScoreSeedForm.test.tsx` :

```typescript
import { describe, expect, it, vi, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { ScoreSeedForm } from "./ScoreSeedForm";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("ScoreSeedForm", () => {
  it("appelle onCancel au clic sur Annuler", () => {
    const onCancel = vi.fn();
    render(<ScoreSeedForm sessionId={7} onCancel={onCancel} onSeeded={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: /annuler/i }));
    expect(onCancel).toHaveBeenCalled();
  });

  it("envoie le score de départ parsé et appelle onSeeded", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true });
    vi.stubGlobal("fetch", fetchMock);
    const onSeeded = vi.fn();
    render(<ScoreSeedForm sessionId={7} onCancel={vi.fn()} onSeeded={onSeeded} />);

    fireEvent.change(screen.getByPlaceholderText("6-4, 3-6"), { target: { value: "6-4" } });
    fireEvent.click(screen.getByRole("button", { name: /valider et reprendre/i }));

    await waitFor(() => expect(onSeeded).toHaveBeenCalled());
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/console/sessions/7/score-seed");
    const body = JSON.parse(init.body as string);
    expect(body.completedSets).toEqual([{ gamesA: 6, gamesB: 4 }]);
  });

  it("affiche une erreur si l'enregistrement échoue", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false }));
    render(<ScoreSeedForm sessionId={7} onCancel={vi.fn()} onSeeded={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: /valider et reprendre/i }));
    await waitFor(() => expect(screen.getByText(/échec de l'enregistrement/i)).toBeInTheDocument());
  });
});
```

- [ ] **Step 2: Lancer les tests, vérifier l'échec**

Run: `cd web && npx vitest run components/console/ScoreSeedForm.test.tsx`
Expected: FAIL — `Cannot find module './ScoreSeedForm'`.

- [ ] **Step 3: Implémenter `ScoreSeedForm`**

Créer `web/components/console/ScoreSeedForm.tsx` :

```tsx
"use client";

import { useState, type FormEvent } from "react";
import styles from "./ScoreSeedForm.module.css";

type Props = {
  sessionId: number;
  onCancel: () => void;
  onSeeded: () => void;
};

const GAME_POINT_OPTIONS = ["ZERO", "FIFTEEN", "THIRTY", "FORTY", "ADVANTAGE"];

function parseCompletedSets(text: string): { gamesA: number; gamesB: number }[] {
  return text
    .split(",")
    .map((part) => part.trim())
    .filter((part) => part.length > 0)
    .map((part) => {
      const [a, b] = part.split("-").map((n) => Number(n.trim()));
      return { gamesA: a || 0, gamesB: b || 0 };
    });
}

export function ScoreSeedForm({ sessionId, onCancel, onSeeded }: Props) {
  const [completedSetsText, setCompletedSetsText] = useState("");
  const [currentSetGamesA, setCurrentSetGamesA] = useState(0);
  const [currentSetGamesB, setCurrentSetGamesB] = useState(0);
  const [currentGamePointsA, setCurrentGamePointsA] = useState("ZERO");
  const [currentGamePointsB, setCurrentGamePointsB] = useState("ZERO");
  const [isTieBreak, setIsTieBreak] = useState(false);
  const [tieBreakPointsA, setTieBreakPointsA] = useState(0);
  const [tieBreakPointsB, setTieBreakPointsB] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      const response = await fetch(`/api/console/sessions/${sessionId}/score-seed`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          completedSets: parseCompletedSets(completedSetsText),
          currentSetGamesA,
          currentSetGamesB,
          currentGamePointsA,
          currentGamePointsB,
          tieBreakPointsA,
          tieBreakPointsB,
          isTieBreak,
          isSuperTieBreak: false,
        }),
      });
      if (!response.ok) throw new Error("seed_failed");
      onSeeded();
    } catch {
      setError("Échec de l'enregistrement du score de départ, réessayez.");
      setSubmitting(false);
    }
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit}>
      <p className={styles.hint}>
        Sets déjà terminés (ex. « 6-4, 3-6 »), score du set en cours, et point du jeu en cours si la reprise se
        fait en plein jeu.
      </p>

      <label className={styles.field}>
        Sets terminés
        <input
          type="text"
          value={completedSetsText}
          onChange={(e) => setCompletedSetsText(e.target.value)}
          placeholder="6-4, 3-6"
        />
      </label>

      <div className={styles.row}>
        <label className={styles.field}>
          Jeux (moi)
          <input
            type="number"
            min={0}
            value={currentSetGamesA}
            onChange={(e) => setCurrentSetGamesA(Number(e.target.value))}
          />
        </label>
        <label className={styles.field}>
          Jeux (adversaire)
          <input
            type="number"
            min={0}
            value={currentSetGamesB}
            onChange={(e) => setCurrentSetGamesB(Number(e.target.value))}
          />
        </label>
      </div>

      <label className={styles.field}>
        <input type="checkbox" checked={isTieBreak} onChange={(e) => setIsTieBreak(e.target.checked)} />
        Le set en cours est un tie-break
      </label>

      {isTieBreak ? (
        <div className={styles.row}>
          <label className={styles.field}>
            Points tie-break (moi)
            <input
              type="number"
              min={0}
              value={tieBreakPointsA}
              onChange={(e) => setTieBreakPointsA(Number(e.target.value))}
            />
          </label>
          <label className={styles.field}>
            Points tie-break (adversaire)
            <input
              type="number"
              min={0}
              value={tieBreakPointsB}
              onChange={(e) => setTieBreakPointsB(Number(e.target.value))}
            />
          </label>
        </div>
      ) : (
        <div className={styles.row}>
          <label className={styles.field}>
            Point du jeu (moi)
            <select value={currentGamePointsA} onChange={(e) => setCurrentGamePointsA(e.target.value)}>
              {GAME_POINT_OPTIONS.map((opt) => (
                <option key={opt} value={opt}>
                  {opt}
                </option>
              ))}
            </select>
          </label>
          <label className={styles.field}>
            Point du jeu (adversaire)
            <select value={currentGamePointsB} onChange={(e) => setCurrentGamePointsB(e.target.value)}>
              {GAME_POINT_OPTIONS.map((opt) => (
                <option key={opt} value={opt}>
                  {opt}
                </option>
              ))}
            </select>
          </label>
        </div>
      )}

      {error && <p className={styles.error}>{error}</p>}

      <div className={styles.actions}>
        <button type="button" className={styles.cancelButton} onClick={onCancel} disabled={submitting}>
          Annuler
        </button>
        <button type="submit" className={styles.submitButton} disabled={submitting}>
          {submitting ? "Enregistrement..." : "Valider et reprendre"}
        </button>
      </div>
    </form>
  );
}
```

Créer `web/components/console/ScoreSeedForm.module.css` :

```css
.form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: var(--ss-surface);
  border: 1px solid var(--ss-border);
  border-radius: 12px;
  padding: 20px;
}

.hint {
  font-size: 13px;
  color: var(--ss-muted);
  margin: 0;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--ss-muted);
  flex: 1;
}

.field input,
.field select {
  min-height: 48px;
  border-radius: 8px;
  border: 1px solid var(--ss-border);
  background: var(--ss-bg);
  color: var(--ss-text);
  padding: 0 12px;
  font-size: 14px;
}

.row {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.error {
  color: var(--ss-hot);
  font-size: 13px;
  margin: 0;
}

.actions {
  display: flex;
  gap: 12px;
}

.cancelButton,
.submitButton {
  flex: 1;
  min-height: 48px;
  border-radius: 10px;
  font-weight: 700;
  font-size: 14px;
  cursor: pointer;
}

.cancelButton {
  background: transparent;
  border: 1px solid var(--ss-border);
  color: var(--ss-muted);
}

.submitButton {
  border: none;
  background: var(--ss-lime);
  color: var(--ss-lime-text);
}

@media (min-width: 900px) {
  .row {
    flex-direction: row;
  }
}
```

- [ ] **Step 4: Lancer les tests, vérifier le succès**

Run: `cd web && npx vitest run components/console/ScoreSeedForm.test.tsx`
Expected: PASS.

- [ ] **Step 5: Mettre à jour le test de `ConsoleSelectionView`** (le clic sur « Reprendre » ouvre désormais le formulaire au lieu de naviguer directement)

Modifier `web/components/console/ConsoleSelectionView.test.tsx` — remplacer le test `"liste les sessions actives et navigue vers la console au clic sur Reprendre"` par :

```typescript
import { describe, expect, it, vi, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import type { SessionDto } from "@/lib/types";

const pushMock = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: pushMock }) }));

import { ConsoleSelectionView } from "./ConsoleSelectionView";

function session(overrides: Partial<SessionDto> = {}): SessionDto {
  return {
    id: 1,
    surface: "CLAY",
    matchFormat: "BEST_OF_3",
    thirdSetRule: "FULL_ADVANTAGE",
    opponent: "Marceau",
    competitionType: null,
    tournament: null,
    status: "ACTIVE",
    sessionType: "MATCH",
    result: null,
    scoreText: null,
    scoreSeedJson: null,
    createdAt: 1000,
    updatedAt: 1000,
    ...overrides,
  };
}

afterEach(() => {
  vi.unstubAllGlobals();
  pushMock.mockClear();
});

describe("ConsoleSelectionView", () => {
  it("affiche un état vide sobre sans session active", () => {
    render(<ConsoleSelectionView activeSessions={[]} />);
    expect(screen.getByText(/aucune session active/i)).toBeInTheDocument();
  });

  it("liste les sessions actives et ouvre le formulaire de score de départ au clic sur Reprendre", () => {
    render(<ConsoleSelectionView activeSessions={[session({ id: 7 })]} />);
    expect(screen.getByText(/marceau/i)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /reprendre/i }));
    expect(screen.getByText(/sets déjà terminés/i)).toBeInTheDocument();
  });

  it("navigue vers la console une fois le score de départ enregistré", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true }));
    render(<ConsoleSelectionView activeSessions={[session({ id: 7 })]} />);
    fireEvent.click(screen.getByRole("button", { name: /reprendre/i }));
    fireEvent.click(screen.getByRole("button", { name: /valider et reprendre/i }));
    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/dashboard/console/7"));
  });

  it("affiche le formulaire Nouveau match au clic sur le bouton", () => {
    render(<ConsoleSelectionView activeSessions={[]} />);
    fireEvent.click(screen.getByRole("button", { name: /^nouveau match$/i }));
    expect(screen.getByText(/date du match/i)).toBeInTheDocument();
  });
});
```

- [ ] **Step 6: Lancer les tests, vérifier l'échec**

Run: `cd web && npx vitest run components/console/ConsoleSelectionView.test.tsx`
Expected: FAIL — le clic sur « Reprendre » navigue encore directement (comportement de Task 6).

- [ ] **Step 7: Brancher `ScoreSeedForm` dans `ConsoleSelectionView`**

Modifier `web/components/console/ConsoleSelectionView.tsx` :

```tsx
"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import type { SessionDto } from "@/lib/types";
import { surfaceLabel } from "@/lib/surfaces";
import { NewMatchForm } from "./NewMatchForm";
import { ScoreSeedForm } from "./ScoreSeedForm";
import styles from "./ConsoleSelectionView.module.css";

type Props = { activeSessions: SessionDto[] };

export function ConsoleSelectionView({ activeSessions }: Props) {
  const router = useRouter();
  const [showNewMatchForm, setShowNewMatchForm] = useState(false);
  const [resumingSessionId, setResumingSessionId] = useState<number | null>(null);

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>Console de saisie</h1>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Reprendre une session active</h2>
        {activeSessions.length === 0 ? (
          <p className={styles.empty}>Aucune session active à reprendre.</p>
        ) : (
          <ul className={styles.list}>
            {activeSessions.map((session) => (
              <li key={session.id} className={styles.listItem}>
                <span className={styles.listItemLabel}>
                  {session.opponent ?? "Adversaire"} · {surfaceLabel(session.surface)}
                </span>
                <button
                  type="button"
                  className={styles.resumeButton}
                  onClick={() => setResumingSessionId(session.id)}
                >
                  Reprendre
                </button>
              </li>
            ))}
          </ul>
        )}
        {resumingSessionId !== null && (
          <div className={styles.seedFormWrapper}>
            <ScoreSeedForm
              sessionId={resumingSessionId}
              onCancel={() => setResumingSessionId(null)}
              onSeeded={() => router.push(`/dashboard/console/${resumingSessionId}`)}
            />
          </div>
        )}
      </section>

      <section className={styles.section}>
        {showNewMatchForm ? (
          <NewMatchForm onCancel={() => setShowNewMatchForm(false)} />
        ) : (
          <button type="button" className={styles.newMatchButton} onClick={() => setShowNewMatchForm(true)}>
            Nouveau match
          </button>
        )}
      </section>
    </div>
  );
}
```

Modifier `web/components/console/ConsoleSelectionView.module.css` — ajouter à la fin :

```css
.seedFormWrapper {
  margin-top: 16px;
}
```

- [ ] **Step 8: Lancer les tests, vérifier le succès**

Run: `cd web && npx vitest run components/console/ConsoleSelectionView.test.tsx`
Expected: PASS.

- [ ] **Step 9: Lancer toute la suite web, vérifier l'absence de régression**

Run: `cd web && npx vitest run`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add web/components/console
git commit -m "feat(web): formulaire de score de départ pour la reprise en mode secours"
```

---

### Task 8: Frontend — assemblage de la console de saisie (`/dashboard/console/[sessionId]`)

**Files:**
- Modify: `web/lib/api.ts`
- Modify: `web/lib/api.test.ts`
- Create: `web/components/console/ScoreCard.tsx`
- Create: `web/components/console/ScoreCard.module.css`
- Create: `web/components/console/ScoreCard.test.tsx`
- Create: `web/components/console/PointButtonGrid.tsx`
- Create: `web/components/console/PointButtonGrid.module.css`
- Create: `web/components/console/PointButtonGrid.test.tsx`
- Create: `web/components/console/PointStatsTiles.tsx`
- Create: `web/components/console/PointStatsTiles.module.css`
- Create: `web/components/console/PointStatsTiles.test.tsx`
- Create: `web/components/console/PointTrail.tsx`
- Create: `web/components/console/PointTrail.module.css`
- Create: `web/components/console/PointTrail.test.tsx`
- Create: `web/components/console/ConsoleScreen.tsx`
- Create: `web/components/console/ConsoleScreen.module.css`
- Create: `web/components/console/ConsoleScreen.test.tsx`
- Create: `web/app/dashboard/console/[sessionId]/page.tsx`

**Interfaces:**
- Consumes : `TennisScoreEngine`/`MatchScore`/`Player`/`SessionFormat`/`formatScoreText`/`deriveMatchResult` (Task 4) ; `getSessions`/`getPoints`/`UnauthorizedError` (Task 5, appelées côté serveur dans `page.tsx`) ; routes proxy `/api/console/sessions/{id}/points`, `/points/last`, `/share`, `/live-score`, `/finalize` (Task 5, appelées côté client sans JWT) ; `SessionDto`/`PointDto`/`PointContext` (`web/lib/types.ts`).
- Produces : `ScoreCard({ score, selfName, opponentName })`, `PointButtonGrid({ onSelect, disabled })`, `PointStatsTiles({ points })`, `PointTrail({ points })`, `ConsoleScreen({ session, initialPoints })` — assemble l'écran final conforme au README #9.

- [ ] **Step 1: Ajouter `parseScoreSeed` à `web/lib/api.ts` (test d'abord, doit échouer)**

Ajouter à `web/lib/api.test.ts` :

```typescript
import { parseScoreSeed } from "./api";

describe("parseScoreSeed", () => {
  it("retourne null quand scoreSeedJson est null", () => {
    expect(parseScoreSeed(null)).toBeNull();
  });

  it("parse le JSON snake_case du backend vers un MatchScore camelCase", () => {
    const json = JSON.stringify({
      completed_sets: [{ games_a: 6, games_b: 4 }],
      current_set_games_a: 2,
      current_set_games_b: 1,
      current_game_points_a: "FORTY",
      current_game_points_b: "THIRTY",
      tie_break_points_a: 0,
      tie_break_points_b: 0,
      is_tie_break: false,
      is_super_tie_break: false,
    });

    const score = parseScoreSeed(json);

    expect(score).toEqual({
      completedSets: [{ gamesA: 6, gamesB: 4 }],
      currentSetGamesA: 2,
      currentSetGamesB: 1,
      currentSetPointLog: [],
      currentGamePointsA: "FORTY",
      currentGamePointsB: "THIRTY",
      tieBreakPointsA: 0,
      tieBreakPointsB: 0,
      isTieBreak: false,
      isSuperTieBreak: false,
      isMatchOver: false,
      matchWinner: null,
    });
  });
});
```

Run: `cd web && npx vitest run lib/api.test.ts`
Expected: FAIL — `parseScoreSeed` n'existe pas.

- [ ] **Step 2: Implémenter `parseScoreSeed`**

Modifier `web/lib/api.ts` — ajouter en haut du fichier l'import et à la fin la fonction :

```typescript
import type { MatchScore } from "./scoreEngine";
import { emptyMatchScore } from "./scoreEngine";

// ... (reste des imports/fonctions existants inchangé) ...

type RawScoreSeed = {
  completed_sets: { games_a: number; games_b: number }[];
  current_set_games_a: number;
  current_set_games_b: number;
  current_game_points_a: string;
  current_game_points_b: string;
  tie_break_points_a: number;
  tie_break_points_b: number;
  is_tie_break: boolean;
  is_super_tie_break: boolean;
};

export function parseScoreSeed(scoreSeedJson: string | null): MatchScore | null {
  if (scoreSeedJson === null) return null;
  const raw = JSON.parse(scoreSeedJson) as RawScoreSeed;
  return {
    ...emptyMatchScore(),
    completedSets: raw.completed_sets.map((s) => ({ gamesA: s.games_a, gamesB: s.games_b })),
    currentSetGamesA: raw.current_set_games_a,
    currentSetGamesB: raw.current_set_games_b,
    currentGamePointsA: raw.current_game_points_a as MatchScore["currentGamePointsA"],
    currentGamePointsB: raw.current_game_points_b as MatchScore["currentGamePointsB"],
    tieBreakPointsA: raw.tie_break_points_a,
    tieBreakPointsB: raw.tie_break_points_b,
    isTieBreak: raw.is_tie_break,
    isSuperTieBreak: raw.is_super_tie_break,
  };
}
```

Run: `cd web && npx vitest run lib/api.test.ts`
Expected: PASS.

- [ ] **Step 3: Écrire le test de `ScoreCard` (doit échouer)**

Créer `web/components/console/ScoreCard.test.tsx` :

```typescript
import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { emptyMatchScore } from "@/lib/scoreEngine";
import { ScoreCard } from "./ScoreCard";

describe("ScoreCard", () => {
  it("affiche les deux noms de joueur", () => {
    render(<ScoreCard score={emptyMatchScore()} selfName="Benjamin" opponentName="Marceau" />);
    expect(screen.getByText("Benjamin")).toBeInTheDocument();
    expect(screen.getByText("Marceau")).toBeInTheDocument();
  });

  it("affiche les points au format 15/30/40/AD", () => {
    const score = { ...emptyMatchScore(), currentGamePointsA: "FORTY" as const };
    render(<ScoreCard score={score} selfName="Benjamin" opponentName="Marceau" />);
    expect(screen.getByText("40")).toBeInTheDocument();
  });

  it("affiche le score de tie-break au lieu des points classiques", () => {
    const score = { ...emptyMatchScore(), isTieBreak: true, tieBreakPointsA: 5 };
    render(<ScoreCard score={score} selfName="Benjamin" opponentName="Marceau" />);
    expect(screen.getByText("5")).toBeInTheDocument();
  });

  it("affiche la bannière de fin de match avec le nom du vainqueur", () => {
    const score = { ...emptyMatchScore(), isMatchOver: true, matchWinner: "A" as const };
    render(<ScoreCard score={score} selfName="Benjamin" opponentName="Marceau" />);
    expect(screen.getByText(/match terminé/i)).toHaveTextContent("Benjamin");
  });
});
```

- [ ] **Step 4: Lancer le test, vérifier l'échec**

Run: `cd web && npx vitest run components/console/ScoreCard.test.tsx`
Expected: FAIL — `Cannot find module './ScoreCard'`.

- [ ] **Step 5: Implémenter `ScoreCard`**

Créer `web/components/console/ScoreCard.tsx` :

```tsx
import type { MatchScore } from "@/lib/scoreEngine";
import styles from "./ScoreCard.module.css";

type Props = {
  score: MatchScore;
  selfName: string;
  opponentName: string;
};

const POINT_LABELS: Record<string, string> = { ZERO: "0", FIFTEEN: "15", THIRTY: "30", FORTY: "40", ADVANTAGE: "AD" };

function pointLabel(points: string, isTieBreak: boolean, isSuperTieBreak: boolean, tieBreakPoints: number): string {
  if (isTieBreak || isSuperTieBreak) return String(tieBreakPoints);
  return POINT_LABELS[points] ?? "0";
}

export function ScoreCard({ score, selfName, opponentName }: Props) {
  const setsWonA = score.completedSets.filter((s) => s.gamesA > s.gamesB).length;
  const setsWonB = score.completedSets.filter((s) => s.gamesB > s.gamesA).length;
  const leadingIsA = score.currentSetGamesA > score.currentSetGamesB || setsWonA >= setsWonB;

  const rows = [
    {
      name: selfName,
      leading: leadingIsA,
      sets: score.completedSets.map((s) => s.gamesA),
      games: score.currentSetGamesA,
      points: pointLabel(score.currentGamePointsA, score.isTieBreak, score.isSuperTieBreak, score.tieBreakPointsA),
    },
    {
      name: opponentName,
      leading: !leadingIsA,
      sets: score.completedSets.map((s) => s.gamesB),
      games: score.currentSetGamesB,
      points: pointLabel(score.currentGamePointsB, score.isTieBreak, score.isSuperTieBreak, score.tieBreakPointsB),
    },
  ];

  return (
    <div className={styles.card}>
      <div className={styles.headerRow}>
        <span className={styles.headerName}>JOUEUR</span>
        <span className={styles.headerSets}>SETS</span>
        <span className={styles.headerGames}>JEUX</span>
        <span className={styles.headerPoints}>POINTS</span>
      </div>
      {rows.map((row, index) => (
        <div key={index}>
          {index > 0 && <div className={styles.divider} />}
          <div className={`${styles.playerRow} ${row.leading ? styles.playerRowLeading : ""}`}>
            <span className={styles.playerName}>{row.name}</span>
            <span className={styles.sets}>
              {row.sets.length === 0
                ? "—"
                : row.sets.map((g, i) => (
                    <span key={i} className={styles.setBox}>
                      {g}
                    </span>
                  ))}
            </span>
            <span className={styles.games}>{row.games}</span>
            <span className={styles.points}>{row.points}</span>
          </div>
        </div>
      ))}
      {score.isMatchOver && (
        <div className={styles.matchOverBanner}>
          Match terminé — {score.matchWinner === "A" ? selfName : opponentName} gagne
        </div>
      )}
    </div>
  );
}
```

Créer `web/components/console/ScoreCard.module.css` :

```css
.card {
  width: 100%;
  background: var(--ss-surface);
  border: 1px solid var(--ss-border);
  border-radius: 16px;
  overflow: hidden;
}

.headerRow {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  font-family: var(--font-barlow), sans-serif;
  font-weight: 700;
  font-size: 11px;
  letter-spacing: 1.5px;
  color: var(--ss-faint);
  border-bottom: 1px solid var(--ss-border);
}

.headerName {
  flex: 1;
}

.headerSets,
.headerGames,
.headerPoints {
  width: 64px;
  text-align: center;
}

.playerRow {
  display: flex;
  align-items: center;
  padding: 16px;
}

.playerRowLeading {
  background: rgba(200, 255, 61, 0.14);
}

.playerName {
  flex: 1;
  font-weight: 600;
  font-size: 16px;
  color: var(--ss-text);
}

.sets {
  width: 64px;
  display: flex;
  justify-content: center;
  gap: 4px;
  color: var(--ss-faint);
  font-family: var(--font-barlow), sans-serif;
  font-weight: 800;
  font-size: 20px;
  font-feature-settings: "tnum";
}

.games {
  width: 64px;
  text-align: center;
  font-family: var(--font-barlow), sans-serif;
  font-weight: 800;
  font-size: 36px;
  color: var(--ss-text);
  font-feature-settings: "tnum";
}

.points {
  width: 64px;
  text-align: center;
  font-family: var(--font-barlow), sans-serif;
  font-weight: 800;
  font-size: 26px;
  color: var(--ss-data);
  font-feature-settings: "tnum";
}

.divider {
  height: 1px;
  background: var(--ss-border);
}

.matchOverBanner {
  padding: 12px 16px;
  text-align: center;
  font-weight: 700;
  font-size: 13px;
  color: var(--ss-lime-text);
  background: var(--ss-lime);
}
```

- [ ] **Step 6: Lancer le test, vérifier le succès**

Run: `cd web && npx vitest run components/console/ScoreCard.test.tsx`
Expected: PASS.

- [ ] **Step 7: Écrire le test de `PointButtonGrid` (doit échouer)**

Créer `web/components/console/PointButtonGrid.test.tsx` :

```typescript
import { describe, expect, it, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { PointButtonGrid } from "./PointButtonGrid";

describe("PointButtonGrid", () => {
  it("affiche les 4 boutons Mon point et les 4 boutons Point adverse", () => {
    render(<PointButtonGrid onSelect={vi.fn()} disabled={false} />);
    for (const label of [
      "Ace",
      "Coup gagnant",
      "Faute provoquée",
      "Faute adverse",
      "Ace adverse",
      "Coup gagnant adverse",
      "Ma faute",
      "Double faute",
    ]) {
      expect(screen.getByRole("button", { name: label })).toBeInTheDocument();
    }
  });

  it("appelle onSelect avec le context correspondant", () => {
    const onSelect = vi.fn();
    render(<PointButtonGrid onSelect={onSelect} disabled={false} />);
    fireEvent.click(screen.getByRole("button", { name: "Ace" }));
    expect(onSelect).toHaveBeenCalledWith("ACE");
    fireEvent.click(screen.getByRole("button", { name: "Double faute" }));
    expect(onSelect).toHaveBeenCalledWith("DOUBLE_FAULT");
  });

  it("désactive tous les boutons quand disabled=true", () => {
    render(<PointButtonGrid onSelect={vi.fn()} disabled={true} />);
    expect(screen.getByRole("button", { name: "Ace" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Double faute" })).toBeDisabled();
  });
});
```

- [ ] **Step 8: Lancer le test, vérifier l'échec**

Run: `cd web && npx vitest run components/console/PointButtonGrid.test.tsx`
Expected: FAIL — `Cannot find module './PointButtonGrid'`.

- [ ] **Step 9: Implémenter `PointButtonGrid`**

Créer `web/components/console/PointButtonGrid.tsx` :

```tsx
"use client";

import type { PointContext } from "@/lib/types";
import styles from "./PointButtonGrid.module.css";

type ButtonSpec = { context: PointContext; label: string };

const SELF_BUTTONS: ButtonSpec[] = [
  { context: "ACE", label: "Ace" },
  { context: "WINNER", label: "Coup gagnant" },
  { context: "FORCED_ERROR", label: "Faute provoquée" },
  { context: "UNFORCED_ERROR_OPPONENT", label: "Faute adverse" },
];

const OPPONENT_BUTTONS: ButtonSpec[] = [
  { context: "ACE_OPPONENT", label: "Ace adverse" },
  { context: "WINNER_OPPONENT", label: "Coup gagnant adverse" },
  { context: "UNFORCED_ERROR_SELF", label: "Ma faute" },
  { context: "DOUBLE_FAULT", label: "Double faute" },
];

type Props = {
  onSelect: (context: PointContext) => void;
  disabled: boolean;
};

export function PointButtonGrid({ onSelect, disabled }: Props) {
  return (
    <div className={styles.grid}>
      <div className={styles.group}>
        <h3 className={styles.groupTitle}>Mon point</h3>
        <div className={styles.buttons}>
          {SELF_BUTTONS.map((btn) => (
            <button
              key={btn.context}
              type="button"
              className={styles.selfButton}
              onClick={() => onSelect(btn.context)}
              disabled={disabled}
            >
              {btn.label}
            </button>
          ))}
        </div>
      </div>
      <div className={styles.group}>
        <h3 className={styles.groupTitle}>Point adverse</h3>
        <div className={styles.buttons}>
          {OPPONENT_BUTTONS.map((btn) => (
            <button
              key={btn.context}
              type="button"
              className={styles.opponentButton}
              onClick={() => onSelect(btn.context)}
              disabled={disabled}
            >
              {btn.label}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
```

Créer `web/components/console/PointButtonGrid.module.css` :

```css
.grid {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.groupTitle {
  font-size: 13px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 1px;
  color: var(--ss-muted);
  margin: 0 0 12px;
}

.buttons {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
}

.selfButton,
.opponentButton {
  min-height: 48px;
  border-radius: 10px;
  font-weight: 700;
  font-size: 14px;
  cursor: pointer;
  padding: 0 16px;
}

.selfButton:disabled,
.opponentButton:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.selfButton {
  border: none;
  background: var(--ss-lime);
  color: var(--ss-lime-text);
}

.opponentButton {
  border: 1px solid var(--ss-border);
  background: var(--ss-surface);
  color: var(--ss-text);
}

@media (min-width: 900px) {
  .buttons {
    grid-template-columns: 1fr 1fr;
  }
}
```

- [ ] **Step 10: Lancer le test, vérifier le succès**

Run: `cd web && npx vitest run components/console/PointButtonGrid.test.tsx`
Expected: PASS.

- [ ] **Step 11: Écrire le test de `PointStatsTiles` (doit échouer)**

Créer `web/components/console/PointStatsTiles.test.tsx` :

```typescript
import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import type { PointDto } from "@/lib/types";
import { PointStatsTiles } from "./PointStatsTiles";

function point(context: PointDto["context"]): PointDto {
  return { id: 1, sessionId: 1, scorer: "A", context, sequenceNum: 1, recordedAt: 1000 };
}

describe("PointStatsTiles", () => {
  it("compte les aces, coups gagnants, fautes directes et doubles fautes", () => {
    render(<PointStatsTiles points={[point("ACE"), point("ACE"), point("WINNER"), point("DOUBLE_FAULT")]} />);
    expect(screen.getAllByText(/^\d+$/).map((el) => el.textContent)).toEqual(["2", "1", "0", "1"]);
  });
});
```

- [ ] **Step 12: Lancer le test, vérifier l'échec**

Run: `cd web && npx vitest run components/console/PointStatsTiles.test.tsx`
Expected: FAIL — `Cannot find module './PointStatsTiles'`.

- [ ] **Step 13: Implémenter `PointStatsTiles`**

Créer `web/components/console/PointStatsTiles.tsx` :

```tsx
import type { PointDto } from "@/lib/types";
import styles from "./PointStatsTiles.module.css";

type Props = { points: PointDto[] };

function count(points: PointDto[], context: string): number {
  return points.filter((p) => p.context === context).length;
}

export function PointStatsTiles({ points }: Props) {
  const tiles = [
    { label: "Aces", value: count(points, "ACE") },
    { label: "Coups gagnants", value: count(points, "WINNER") },
    { label: "Fautes directes", value: count(points, "UNFORCED_ERROR_SELF") },
    { label: "Doubles fautes", value: count(points, "DOUBLE_FAULT") },
  ];

  return (
    <div className={styles.tiles}>
      {tiles.map((tile) => (
        <div key={tile.label} className={styles.tile}>
          <span className={styles.tileValue}>{tile.value}</span>
          <span className={styles.tileLabel}>{tile.label}</span>
        </div>
      ))}
    </div>
  );
}
```

Créer `web/components/console/PointStatsTiles.module.css` :

```css
.tiles {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  margin-top: 24px;
}

.tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  background: var(--ss-surface);
  border: 1px solid var(--ss-border);
  border-radius: 12px;
  padding: 12px;
}

.tileValue {
  font-family: var(--font-barlow), sans-serif;
  font-weight: 800;
  font-size: 24px;
  color: var(--ss-text);
}

.tileLabel {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--ss-muted);
}

@media (min-width: 900px) {
  .tiles {
    grid-template-columns: repeat(4, 1fr);
  }
}
```

- [ ] **Step 14: Lancer le test, vérifier le succès**

Run: `cd web && npx vitest run components/console/PointStatsTiles.test.tsx`
Expected: PASS.

- [ ] **Step 15: Écrire le test de `PointTrail` (doit échouer)**

Créer `web/components/console/PointTrail.test.tsx` :

```typescript
import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import type { PointDto } from "@/lib/types";
import { PointTrail } from "./PointTrail";

describe("PointTrail", () => {
  it("affiche un état vide sans point", () => {
    render(<PointTrail points={[]} />);
    expect(screen.getByText(/aucun point saisi/i)).toBeInTheDocument();
  });

  it("affiche les points du plus récent au plus ancien", () => {
    const points: PointDto[] = [
      { id: 1, sessionId: 1, scorer: "A", context: "ACE", sequenceNum: 1, recordedAt: 1000 },
      { id: 2, sessionId: 1, scorer: "B", context: "DOUBLE_FAULT", sequenceNum: 2, recordedAt: 2000 },
    ];
    render(<PointTrail points={points} />);
    const items = screen.getAllByRole("listitem");
    expect(items[0]).toHaveTextContent("Double faute");
    expect(items[1]).toHaveTextContent("Ace");
  });
});
```

- [ ] **Step 16: Lancer le test, vérifier l'échec, puis implémenter**

Run: `cd web && npx vitest run components/console/PointTrail.test.tsx`
Expected: FAIL — `Cannot find module './PointTrail'`.

Créer `web/components/console/PointTrail.tsx` :

```tsx
import type { PointDto } from "@/lib/types";
import styles from "./PointTrail.module.css";

type Props = { points: PointDto[] };

const CONTEXT_LABELS: Record<string, string> = {
  ACE: "Ace",
  WINNER: "Coup gagnant",
  FORCED_ERROR: "Faute provoquée",
  UNFORCED_ERROR_OPPONENT: "Faute adverse",
  ACE_OPPONENT: "Ace adverse",
  WINNER_OPPONENT: "Coup gagnant adverse",
  UNFORCED_ERROR_SELF: "Ma faute",
  DOUBLE_FAULT: "Double faute",
};

export function PointTrail({ points }: Props) {
  if (points.length === 0) {
    return <p className={styles.empty}>Aucun point saisi pour l'instant.</p>;
  }

  return (
    <ul className={styles.list}>
      {[...points].reverse().map((point) => (
        <li key={point.id} className={styles.item}>
          <span className={`${styles.scorerBadge} ${point.scorer === "A" ? styles.scorerA : styles.scorerB}`}>
            {point.scorer}
          </span>
          <span className={styles.label}>{point.context ? CONTEXT_LABELS[point.context] : "Point"}</span>
        </li>
      ))}
    </ul>
  );
}
```

Créer `web/components/console/PointTrail.module.css` :

```css
.empty {
  color: var(--ss-faint);
  font-size: 13px;
}

.list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 400px;
  overflow-y: auto;
}

.item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--ss-surface);
  border: 1px solid var(--ss-border);
  font-size: 13px;
}

.scorerBadge {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  font-size: 11px;
  flex-shrink: 0;
}

.scorerA {
  background: var(--ss-lime);
  color: var(--ss-lime-text);
}

.scorerB {
  background: var(--ss-border);
  color: var(--ss-muted);
}

.label {
  color: var(--ss-text);
  font-weight: 600;
}
```

Run: `cd web && npx vitest run components/console/PointTrail.test.tsx`
Expected: PASS.

- [ ] **Step 17: Écrire le test de `ConsoleScreen` (doit échouer)**

Créer `web/components/console/ConsoleScreen.test.tsx` :

```typescript
import { describe, expect, it, vi, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import type { SessionDto, PointDto } from "@/lib/types";
import { ConsoleScreen } from "./ConsoleScreen";

function session(overrides: Partial<SessionDto> = {}): SessionDto {
  return {
    id: 7,
    surface: "CLAY",
    matchFormat: "BEST_OF_1",
    thirdSetRule: "FULL_ADVANTAGE",
    opponent: "Marceau",
    competitionType: null,
    tournament: null,
    status: "ACTIVE",
    sessionType: "MATCH",
    result: null,
    scoreText: null,
    scoreSeedJson: null,
    createdAt: 1000,
    updatedAt: 1000,
    ...overrides,
  };
}

function jsonResponse(body: unknown, ok = true) {
  return Promise.resolve({ ok, json: async () => body });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("ConsoleScreen", () => {
  it("reconstruit l'état à partir des points initiaux (rechargement de page)", () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(null)));
    const initialPoints: PointDto[] = [
      { id: 1, sessionId: 7, scorer: "A", context: "ACE", sequenceNum: 1, recordedAt: 1000 },
    ];
    render(<ConsoleScreen session={session()} initialPoints={initialPoints} />);
    expect(screen.getByText("15")).toBeInTheDocument();
  });

  it("un clic sur un bouton de point poste le point et met à jour le score", async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url.endsWith("/share")) return jsonResponse(null);
      if (url.endsWith("/points") && init?.method === "POST") {
        return jsonResponse({ id: 1, sessionId: 7, scorer: "A", context: "ACE", sequenceNum: 1, recordedAt: 1000 });
      }
      return jsonResponse(null);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<ConsoleScreen session={session()} initialPoints={[]} />);
    fireEvent.click(screen.getByRole("button", { name: "Ace" }));

    await waitFor(() => expect(screen.getByText("15")).toBeInTheDocument());
  });

  it("Annuler retire le dernier point et restaure le score précédent", async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url.endsWith("/share")) return jsonResponse(null);
      if (url.endsWith("/points/last") && init?.method === "DELETE") return jsonResponse(null, true);
      return jsonResponse(null);
    });
    vi.stubGlobal("fetch", fetchMock);

    const initialPoints: PointDto[] = [
      { id: 1, sessionId: 7, scorer: "A", context: "ACE", sequenceNum: 1, recordedAt: 1000 },
    ];
    render(<ConsoleScreen session={session()} initialPoints={initialPoints} />);
    expect(screen.getByText("15")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /annuler le dernier point/i }));
    await waitFor(() => expect(screen.getByText("0")).toBeInTheDocument());
  });

  it("affiche une erreur si l'enregistrement du point échoue", async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url.endsWith("/share")) return jsonResponse(null);
      if (url.endsWith("/points") && init?.method === "POST") return jsonResponse(null, false);
      return jsonResponse(null);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<ConsoleScreen session={session()} initialPoints={[]} />);
    fireEvent.click(screen.getByRole("button", { name: "Ace" }));
    await waitFor(() => expect(screen.getByText(/échec de l'enregistrement/i)).toBeInTheDocument());
  });

  it("désactive les boutons de point et affiche la bannière une fois le match terminé", async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url.endsWith("/share")) return jsonResponse(null);
      if (url.endsWith("/points") && init?.method === "POST") {
        return jsonResponse({ id: 1, sessionId: 7, scorer: "A", context: "ACE", sequenceNum: 1, recordedAt: 1000 });
      }
      return jsonResponse(null, true);
    });
    vi.stubGlobal("fetch", fetchMock);

    // BEST_OF_1, déjà à 5-0 jeux et 40-0 : le prochain point gagne le match.
    const seed = JSON.stringify({
      completed_sets: [],
      current_set_games_a: 5,
      current_set_games_b: 0,
      current_game_points_a: "FORTY",
      current_game_points_b: "ZERO",
      tie_break_points_a: 0,
      tie_break_points_b: 0,
      is_tie_break: false,
      is_super_tie_break: false,
    });
    render(<ConsoleScreen session={session({ scoreSeedJson: seed })} initialPoints={[]} />);
    fireEvent.click(screen.getByRole("button", { name: "Ace" }));

    await waitFor(() => expect(screen.getByText(/match terminé/i)).toBeInTheDocument());
    expect(screen.getByRole("button", { name: "Ace" })).toBeDisabled();
  });
});
```

- [ ] **Step 18: Lancer le test, vérifier l'échec**

Run: `cd web && npx vitest run components/console/ConsoleScreen.test.tsx`
Expected: FAIL — `Cannot find module './ConsoleScreen'`.

- [ ] **Step 19: Implémenter `ConsoleScreen`**

Créer `web/components/console/ConsoleScreen.tsx` :

```tsx
"use client";

import { useEffect, useRef, useState } from "react";
import type { SessionDto, PointDto, PointContext } from "@/lib/types";
import { parseScoreSeed } from "@/lib/api";
import { TennisScoreEngine, formatScoreText, deriveMatchResult } from "@/lib/scoreEngine";
import type { MatchScore, Player, SessionFormat } from "@/lib/scoreEngine";
import { ScoreCard } from "./ScoreCard";
import { PointButtonGrid } from "./PointButtonGrid";
import { PointStatsTiles } from "./PointStatsTiles";
import { PointTrail } from "./PointTrail";
import styles from "./ConsoleScreen.module.css";

type Props = {
  session: SessionDto;
  initialPoints: PointDto[];
};

const SELF_NAME = "Benjamin";

function buildInitialEngine(session: SessionDto, initialPoints: PointDto[]): TennisScoreEngine {
  const format: SessionFormat = {
    matchFormat: session.matchFormat as SessionFormat["matchFormat"],
    thirdSetRule: session.thirdSetRule as SessionFormat["thirdSetRule"],
  };
  const seed = parseScoreSeed(session.scoreSeedJson);
  const engine = new TennisScoreEngine(format, seed ?? undefined);
  for (const point of initialPoints) {
    engine.recordPoint(point.scorer as Player);
  }
  return engine;
}

export function ConsoleScreen({ session, initialPoints }: Props) {
  const engineRef = useRef<TennisScoreEngine | null>(null);
  if (engineRef.current === null) {
    engineRef.current = buildInitialEngine(session, initialPoints);
  }
  const engine = engineRef.current;

  const [score, setScore] = useState<MatchScore>(engine.currentScore);
  const [points, setPoints] = useState<PointDto[]>(initialPoints);
  const [shareToken, setShareToken] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const opponentName = session.opponent ?? "Adversaire";

  useEffect(() => {
    let cancelled = false;
    fetch(`/api/console/sessions/${session.id}/share`)
      .then((res) => (res.ok ? res.json() : null))
      .then((share: { token: string } | null) => {
        if (!cancelled) setShareToken(share?.token ?? null);
      })
      .catch(() => {
        if (!cancelled) setShareToken(null);
      });
    return () => {
      cancelled = true;
    };
  }, [session.id]);

  async function pushLiveScoreIfShared(nextScore: MatchScore) {
    if (shareToken === null) return;
    try {
      await fetch(`/api/console/sessions/${session.id}/live-score`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          completedSets: nextScore.completedSets,
          currentSetGamesA: nextScore.currentSetGamesA,
          currentSetGamesB: nextScore.currentSetGamesB,
          currentSetPointLog: nextScore.currentSetPointLog,
          currentGamePointsA: nextScore.currentGamePointsA,
          currentGamePointsB: nextScore.currentGamePointsB,
          tieBreakPointsA: nextScore.tieBreakPointsA,
          tieBreakPointsB: nextScore.tieBreakPointsB,
          isTieBreak: nextScore.isTieBreak,
          isSuperTieBreak: nextScore.isSuperTieBreak,
          isMatchOver: nextScore.isMatchOver,
          matchWinner: nextScore.matchWinner,
          playerAName: SELF_NAME,
          playerBName: opponentName,
          surface: session.surface,
          tournament: session.tournament,
          competitionType: session.competitionType,
          startedAt: session.createdAt,
        }),
      });
    } catch {
      // best-effort : le live-share ne doit jamais bloquer la saisie
    }
  }

  async function finalize(nextScore: MatchScore, status: "COMPLETED" | "ACTIVE") {
    await fetch(`/api/console/sessions/${session.id}/finalize`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        session,
        status,
        result: status === "COMPLETED" ? deriveMatchResult(nextScore) : null,
        scoreText: status === "COMPLETED" ? formatScoreText(nextScore) : null,
        updatedAt: Date.now(),
      }),
    });
  }

  async function handlePointClick(context: PointContext) {
    if (pending) return;
    setPending(true);
    setError(null);
    try {
      const response = await fetch(`/api/console/sessions/${session.id}/points`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ context }),
      });
      if (!response.ok) throw new Error("point_failed");
      const point: PointDto = await response.json();

      engine.recordPoint(point.scorer as Player);
      const nextScore = engine.currentScore;
      setScore(nextScore);
      setPoints((prev) => [...prev, point]);

      await pushLiveScoreIfShared(nextScore);
      if (nextScore.isMatchOver) {
        await finalize(nextScore, "COMPLETED");
      }
    } catch {
      setError("Échec de l'enregistrement du point, réessayez.");
    } finally {
      setPending(false);
    }
  }

  async function handleUndo() {
    if (pending || points.length === 0) return;
    setPending(true);
    setError(null);
    try {
      const response = await fetch(`/api/console/sessions/${session.id}/points/last`, { method: "DELETE" });
      if (!response.ok) throw new Error("undo_failed");

      const wasMatchOver = engine.currentScore.isMatchOver;
      engine.undo();
      const nextScore = engine.currentScore;
      setScore(nextScore);
      setPoints((prev) => prev.slice(0, -1));

      await pushLiveScoreIfShared(nextScore);
      if (wasMatchOver && !nextScore.isMatchOver) {
        await finalize(nextScore, "ACTIVE");
      }
    } catch {
      setError("Échec de l'annulation, réessayez.");
    } finally {
      setPending(false);
    }
  }

  return (
    <div className={styles.grid}>
      <div className={styles.leftColumn}>
        <ScoreCard score={score} selfName={SELF_NAME} opponentName={opponentName} />
        <button
          type="button"
          className={styles.undoButton}
          onClick={handleUndo}
          disabled={pending || points.length === 0}
        >
          Annuler le dernier point
        </button>
        {error && <p className={styles.error}>{error}</p>}
      </div>

      <div className={styles.centerColumn}>
        <PointButtonGrid onSelect={handlePointClick} disabled={pending || score.isMatchOver} />
        <PointStatsTiles points={points} />
      </div>

      <div className={styles.rightColumn}>
        <h3 className={styles.trailTitle}>Déroulé</h3>
        <PointTrail points={points} />
      </div>
    </div>
  );
}
```

Créer `web/components/console/ConsoleScreen.module.css` :

```css
.grid {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: 24px 16px;
}

.leftColumn {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.centerColumn {
  display: flex;
  flex-direction: column;
}

.rightColumn {
  display: flex;
  flex-direction: column;
}

.undoButton {
  min-height: 48px;
  border-radius: 10px;
  border: 1px solid var(--ss-border);
  background: var(--ss-surface);
  color: var(--ss-text);
  font-weight: 700;
  font-size: 14px;
  cursor: pointer;
}

.undoButton:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.error {
  color: var(--ss-hot);
  font-size: 13px;
  margin: 0;
}

.trailTitle {
  font-size: 13px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 1px;
  color: var(--ss-muted);
  margin: 0 0 12px;
}

@media (min-width: 900px) {
  .grid {
    display: grid;
    grid-template-columns: 340px 1fr 288px;
    align-items: start;
    gap: 24px;
  }
}
```

- [ ] **Step 20: Lancer le test, vérifier le succès**

Run: `cd web && npx vitest run components/console/ConsoleScreen.test.tsx`
Expected: PASS.

- [ ] **Step 21: Créer la page serveur `/dashboard/console/[sessionId]`**

Créer `web/app/dashboard/console/[sessionId]/page.tsx` :

```tsx
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { SESSION_COOKIE } from "@/lib/auth";
import { getSessions, getPoints, UnauthorizedError } from "@/lib/api";
import { ConsoleScreen } from "@/components/console/ConsoleScreen";

type Props = { params: Promise<{ sessionId: string }> };

export default async function ConsoleSessionPage({ params }: Props) {
  const { sessionId } = await params;
  const store = await cookies();
  const token = store.get(SESSION_COOKIE)?.value;
  if (!token) redirect("/login");

  let sessions;
  try {
    sessions = await getSessions(token);
  } catch (error) {
    if (error instanceof UnauthorizedError) redirect("/login");
    throw error;
  }

  const session = sessions.find((s) => s.id === Number(sessionId));
  if (!session || session.status !== "ACTIVE") {
    redirect("/dashboard/console");
  }

  const points = await getPoints(token, session.id);

  return <ConsoleScreen session={session} initialPoints={points} />;
}
```

- [ ] **Step 22: Lancer toute la suite web, vérifier l'absence de régression**

Run: `cd web && npx vitest run`
Expected: PASS (toute la suite, backend et frontend confondus avec l'étape suivante).

- [ ] **Step 23: Lancer la suite backend complète également**

Run: `cd backend && uv run pytest`
Expected: PASS.

- [ ] **Step 24: Vérification manuelle responsive (mobile-first)**

Lancer `cd web && npm run dev`, ouvrir `http://localhost:3000/dashboard/console` puis une session dans un viewport ≤ 480px (DevTools mobile) et un viewport ≥ 900px. Vérifier : écran de sélection et formulaires utilisables en une colonne sur mobile ; console empilée dans l'ordre ScoreCard+Annuler → grille+stats → déroulé sur mobile ; grille 3 colonnes (340px/1fr/288px) à partir de 900px ; cibles tactiles ≥ 48px partout.

- [ ] **Step 25: Commit**

```bash
git add web/lib/api.ts web/lib/api.test.ts web/components/console web/app/dashboard/console
git commit -m "feat(web): assembler la console de saisie point par point (mobile-first)"
```

---

## Self-Review (effectué par le rédacteur du plan)

- **Couverture du spec** : les 8 tags de contexte, l'absence de bouton Basculer, la persistance immédiate (attente de confirmation serveur avant mise à jour locale), la finalisation automatique + réactivation via undo, le live-share opportuniste (lookup avant push), le champ date modifiable, le formulaire de score de départ, et le mobile-first (breakpoint 900px, ordre d'empilement) sont chacun couverts par une tâche et un test explicite ci-dessus.
- **Aucun placeholder** : chaque étape contient le code complet (pas de "TODO"/"à compléter") ; les seules données non testées explicitement (ex. vérification manuelle responsive, Step 24 de la Task 8) sont documentées comme telles dans le spec lui-même (« pas de test automatisé pertinent pour du CSS »).
- **Cohérence des types** : `PointContext` (8 valeurs) est défini une seule fois côté backend (`features/points/schemas.py`) et répliqué à l'identique côté frontend (`web/lib/types.ts`) — vérifié champ par champ. `MatchScore`/`SessionFormat`/`Player` (Task 4) sont réutilisés sans renommage dans `parseScoreSeed` (Task 8, dans `api.ts`) et dans `ConsoleScreen`. `ScoreSeed` (camelCase, Task 5) et `RawScoreSeed`/`ScoreSeedRequest` (snake_case, Task 2/Task 8) restent bien deux representations distinctes de la même donnée, jamais confondues dans une même fonction.

