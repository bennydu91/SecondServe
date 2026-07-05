# Web : responsive, historique des matchs, saisie rétroactive — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rendre la partie web (`web/`) utilisable sur mobile/tablette, permettre d'éditer ou supprimer un match déjà enregistré, et permettre de saisir directement le score final d'un match déjà joué (sans passer par la saisie point par point en direct).

**Architecture:** Un unique nouvel endpoint backend `PATCH /api/v1/sessions/{id}` (+ `DELETE /api/v1/sessions/{id}`) sert de fondation à la fois pour l'édition d'un match existant et pour la finalisation d'un match saisi rétroactivement (créé `ACTIVE` via le `POST` existant, puis basculé `COMPLETED` via ce même `PATCH`). Le responsive est traité en CSS Modules pur (breakpoint unique `900px`, cohérent avec les media queries déjà présentes côté console), sans changement de structure React sauf pour la navigation (nouveau composant `MobileTabBar`).

**Tech Stack:** Backend FastAPI + SQLAlchemy async (Python, `uv run pytest`). Frontend Next.js 16 (App Router) + React 19 + CSS Modules + Vitest/Testing Library (`yarn test`).

## Global Constraints

- Backend : app mono-utilisateur, aucune vérification d'ownership sur les sessions (JWT suffit, déjà appliqué au niveau du router).
- Édition/suppression limitées aux sessions `session_type == "MATCH"` côté UI (pas les séances `TRAINING`).
- Breakpoint responsive unique : `@media (max-width: 899px)` = mobile, au-delà = desktop (aligné sur les media queries `min-width: 900px` déjà présentes dans `ConsoleScreen.module.css`, `ScoreSeedForm.module.css`, `PointButtonGrid.module.css`, `PointStatsTiles.module.css`).
- Pour un match saisi rétroactivement, `updated_at` doit être fixé à la même valeur que `created_at` (durée nulle) pour ne pas fausser `computePlayTime` dans `web/lib/stats.ts`.
- Aucune migration Alembic nécessaire pour ce plan (aucun changement de modèle de données).
- Tests backend : `cd /root/SecondServe/backend && uv run pytest tests/ -q`. Tests frontend : `cd /root/SecondServe/web && yarn test`.
- Convention de nommage des événements de monitoring (`emit_event`) : `match.started` (création, existe déjà), `match.ended` (suppression, déjà utilisé par `sync/service.py` — à réutiliser tel quel pour le nouvel endpoint `DELETE`), `match.updated` (nouveau, pour le `PATCH`).

---

## Task 1: Backend — `PATCH /api/v1/sessions/{id}`

**Files:**
- Modify: `backend/app/features/sessions/schemas.py:1-53`
- Modify: `backend/app/features/sessions/repository.py:1-47`
- Modify: `backend/app/features/sessions/service.py:1-34`
- Modify: `backend/app/api/v1/sessions.py:1-39`
- Test: `backend/tests/unit/test_session_service.py`
- Test: `backend/tests/integration/test_sessions_api.py`

**Interfaces:**
- Produces: `SessionUpdateRequest` (pydantic model, tous champs optionnels) dans `app.features.sessions.schemas`; `SessionRepository.update(session_id: int, request: SessionUpdateRequest) -> SessionModel | None`; `SessionService.update_session(session_id: int, request: SessionUpdateRequest) -> SessionResponse` (lève `SecondServeException(error_code="SESSION_NOT_FOUND", status_code=404)` si absent) ; route `PATCH /api/v1/sessions/{session_id}`.

- [ ] **Step 1: Écrire le test unitaire du service (échoue)**

Ouvrir `backend/tests/unit/test_session_service.py`. Modifier la ligne 4 (import) pour ajouter `SessionUpdateRequest` :

```python
from app.features.sessions.schemas import SessionCreateRequest, SessionResponse, SessionsResponse, SessionUpdateRequest
```

Ajouter à la fin du fichier (après la dernière fonction, ligne 179) :

```python


@pytest.mark.asyncio
async def test_update_session_returns_updated_session_response():
    model = session_model(id=7, opponent="Nadal", surface="HARD")
    repo = MagicMock()
    repo.update = AsyncMock(return_value=model)
    service = SessionService(repo)

    request = SessionUpdateRequest(opponent="Nadal", surface="HARD")
    response = await service.update_session(7, request)

    assert isinstance(response, SessionResponse)
    assert response.id == 7
    assert response.opponent == "Nadal"
    assert response.surface == "HARD"
    repo.update.assert_called_once_with(7, request)


@pytest.mark.asyncio
async def test_update_session_raises_when_session_not_found():
    repo = MagicMock()
    repo.update = AsyncMock(return_value=None)
    service = SessionService(repo)

    with pytest.raises(SecondServeException) as exc_info:
        await service.update_session(999, SessionUpdateRequest())

    assert exc_info.value.error_code == "SESSION_NOT_FOUND"
    assert exc_info.value.status_code == 404
```

- [ ] **Step 2: Vérifier que le test échoue**

Run: `cd /root/SecondServe/backend && uv run pytest tests/unit/test_session_service.py -q`
Expected: FAIL — `ImportError: cannot import name 'SessionUpdateRequest'` (le schéma n'existe pas encore).

- [ ] **Step 3: Ajouter le schéma `SessionUpdateRequest`**

Dans `backend/app/features/sessions/schemas.py`, ajouter après `SessionCreateRequest` (après la ligne 12, avant `class SessionResponse`) :

```python
class SessionUpdateRequest(BaseModel):
    surface: Optional[Literal["CLAY", "GRASS", "HARD", "CARPET"]] = None
    match_format: Optional[Literal["BEST_OF_1", "BEST_OF_3"]] = None
    third_set_rule: Optional[Literal["FULL_ADVANTAGE", "SUPER_TIE_BREAK_10", "SHORT_DECISIVE_SET"]] = None
    opponent: Optional[str] = None
    competition_type: Optional[str] = None
    tournament: Optional[str] = None
    status: Optional[Literal["ACTIVE", "COMPLETED"]] = None
    result: Optional[Literal["VICTORY", "DEFEAT"]] = None
    score_text: Optional[str] = None
    created_at: Optional[int] = None
    updated_at: Optional[int] = None
```

- [ ] **Step 4: Ajouter `SessionRepository.update`**

Dans `backend/app/features/sessions/repository.py`, modifier l'import ligne 4 :

```python
from app.features.sessions.schemas import SessionCreateRequest, SessionUpdateRequest
```

Ajouter la méthode à la fin de la classe (après `update_score_seed`, fin de fichier ligne 47) :

```python

    async def update(self, session_id: int, request: SessionUpdateRequest) -> SessionModel | None:
        session = await self.get_by_id(session_id)
        if session is None:
            return None
        for field, value in request.model_dump(exclude_unset=True).items():
            setattr(session, field, value)
        await self.db.flush()
        return session
```

- [ ] **Step 5: Ajouter `SessionService.update_session`**

Dans `backend/app/features/sessions/service.py`, modifier l'import ligne 3-8 :

```python
from app.features.sessions.schemas import (
    SessionCreateRequest,
    SessionResponse,
    SessionsResponse,
    ScoreSeedRequest,
    SessionUpdateRequest,
)
```

Ajouter la méthode à la fin de la classe (après `update_score_seed`, fin de fichier ligne 34) :

```python

    async def update_session(self, session_id: int, request: SessionUpdateRequest) -> SessionResponse:
        session = await self.repository.update(session_id, request)
        if session is None:
            raise SecondServeException(
                error_code="SESSION_NOT_FOUND", message="Session introuvable", status_code=404
            )
        emit_event("match.updated", {"session_id": session.id})
        return SessionResponse.model_validate(session)
```

- [ ] **Step 6: Vérifier que le test unitaire passe**

Run: `cd /root/SecondServe/backend && uv run pytest tests/unit/test_session_service.py -q`
Expected: PASS (9 tests passed).

- [ ] **Step 7: Écrire les tests d'intégration de la route (échouent)**

Ajouter à la fin de `backend/tests/integration/test_sessions_api.py` :

```python


@pytest.mark.asyncio
async def test_update_session_partial_fields(client):
    token = make_token()
    create_resp = await client.post(
        "/api/v1/sessions",
        json={
            "surface": "CLAY",
            "match_format": "BEST_OF_3",
            "third_set_rule": "FULL_ADVANTAGE",
            "opponent": "Dupont",
            "created_at": 1_000_000,
        },
        headers=auth(token),
    )
    session_id = create_resp.json()["id"]

    response = await client.patch(
        f"/api/v1/sessions/{session_id}",
        json={"opponent": "Martin", "surface": "HARD"},
        headers=auth(token),
    )
    assert response.status_code == 200
    data = response.json()
    assert data["opponent"] == "Martin"
    assert data["surface"] == "HARD"
    assert data["match_format"] == "BEST_OF_3"


@pytest.mark.asyncio
async def test_update_session_completes_match_with_score(client):
    token = make_token()
    create_resp = await client.post(
        "/api/v1/sessions",
        json={"surface": "CLAY", "match_format": "BEST_OF_3", "third_set_rule": "FULL_ADVANTAGE", "created_at": 1_000_000},
        headers=auth(token),
    )
    session_id = create_resp.json()["id"]

    response = await client.patch(
        f"/api/v1/sessions/{session_id}",
        json={"status": "COMPLETED", "result": "VICTORY", "score_text": "6-4 · 6-3", "updated_at": 1_000_000},
        headers=auth(token),
    )
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "COMPLETED"
    assert data["result"] == "VICTORY"
    assert data["score_text"] == "6-4 · 6-3"
    assert data["updated_at"] == 1_000_000


@pytest.mark.asyncio
async def test_update_session_requires_jwt(client):
    response = await client.patch("/api/v1/sessions/1", json={"opponent": "Martin"})
    assert response.status_code in (401, 403)


@pytest.mark.asyncio
async def test_update_session_404_when_missing(client):
    token = make_token()
    response = await client.patch("/api/v1/sessions/999999", json={"opponent": "Martin"}, headers=auth(token))
    assert response.status_code == 404
    assert response.json()["error_code"] == "SESSION_NOT_FOUND"
```

- [ ] **Step 8: Vérifier que les tests échouent**

Run: `cd /root/SecondServe/backend && uv run pytest tests/integration/test_sessions_api.py -q`
Expected: FAIL — `405 Method Not Allowed` (la route `PATCH` n'existe pas encore).

- [ ] **Step 9: Ajouter la route `PATCH /{session_id}`**

Dans `backend/app/api/v1/sessions.py`, modifier l'import lignes 5-10 :

```python
from app.features.sessions.schemas import (
    SessionCreateRequest,
    SessionResponse,
    SessionsResponse,
    ScoreSeedRequest,
    SessionUpdateRequest,
)
```

Ajouter la route à la fin du fichier (après `update_score_seed`, fin de fichier ligne 39) :

```python


@router.patch("/{session_id}", response_model=SessionResponse)
async def update_session(
    session_id: int,
    request: SessionUpdateRequest,
    service: SessionService = Depends(get_session_service),
):
    return await service.update_session(session_id, request)
```

- [ ] **Step 10: Vérifier que tous les tests backend passent**

Run: `cd /root/SecondServe/backend && uv run pytest tests/ -q`
Expected: PASS, tous les tests verts.

- [ ] **Step 11: Commit**

```bash
git add backend/app/features/sessions/schemas.py backend/app/features/sessions/repository.py backend/app/features/sessions/service.py backend/app/api/v1/sessions.py backend/tests/unit/test_session_service.py backend/tests/integration/test_sessions_api.py
git commit -m "feat(backend): ajouter PATCH /api/v1/sessions/{id} pour l'édition partielle d'un match"
```

---

## Task 2: Backend — `DELETE /api/v1/sessions/{id}`

**Files:**
- Modify: `backend/app/features/sessions/repository.py`
- Modify: `backend/app/features/sessions/service.py`
- Modify: `backend/app/api/v1/sessions.py`
- Test: `backend/tests/unit/test_session_service.py`
- Test: `backend/tests/integration/test_sessions_api.py`

**Interfaces:**
- Consumes: rien de nouveau (utilise `SessionRepository.get_by_id`, déjà existant).
- Produces: `SessionRepository.delete(session_id: int) -> bool`; `SessionService.delete_session(session_id: int) -> None` (lève `SecondServeException(error_code="SESSION_NOT_FOUND", status_code=404)` si absent) ; route `DELETE /api/v1/sessions/{session_id}` (204).

- [ ] **Step 1: Écrire le test unitaire du service (échoue)**

Ajouter à la fin de `backend/tests/unit/test_session_service.py` :

```python


@pytest.mark.asyncio
async def test_delete_session_calls_repository():
    repo = MagicMock()
    repo.delete = AsyncMock(return_value=True)
    service = SessionService(repo)

    await service.delete_session(7)

    repo.delete.assert_called_once_with(7)


@pytest.mark.asyncio
async def test_delete_session_raises_when_not_found():
    repo = MagicMock()
    repo.delete = AsyncMock(return_value=False)
    service = SessionService(repo)

    with pytest.raises(SecondServeException) as exc_info:
        await service.delete_session(999)

    assert exc_info.value.error_code == "SESSION_NOT_FOUND"
    assert exc_info.value.status_code == 404
```

- [ ] **Step 2: Vérifier que le test échoue**

Run: `cd /root/SecondServe/backend && uv run pytest tests/unit/test_session_service.py -q`
Expected: FAIL — `AttributeError: 'SessionService' object has no attribute 'delete_session'`.

- [ ] **Step 3: Ajouter `SessionRepository.delete`**

Dans `backend/app/features/sessions/repository.py`, ajouter à la fin de la classe :

```python

    async def delete(self, session_id: int) -> bool:
        session = await self.get_by_id(session_id)
        if session is None:
            return False
        await self.db.delete(session)
        await self.db.flush()
        return True
```

- [ ] **Step 4: Ajouter `SessionService.delete_session`**

Dans `backend/app/features/sessions/service.py`, ajouter à la fin de la classe :

```python

    async def delete_session(self, session_id: int) -> None:
        deleted = await self.repository.delete(session_id)
        if not deleted:
            raise SecondServeException(
                error_code="SESSION_NOT_FOUND", message="Session introuvable", status_code=404
            )
        emit_event("match.ended", {"session_id": session_id})
```

- [ ] **Step 5: Vérifier que le test unitaire passe**

Run: `cd /root/SecondServe/backend && uv run pytest tests/unit/test_session_service.py -q`
Expected: PASS (11 tests passed).

- [ ] **Step 6: Écrire les tests d'intégration de la route (échouent)**

Ajouter à la fin de `backend/tests/integration/test_sessions_api.py` :

```python


@pytest.mark.asyncio
async def test_delete_session(client):
    token = make_token()
    create_resp = await client.post(
        "/api/v1/sessions",
        json={"surface": "CLAY", "match_format": "BEST_OF_3", "third_set_rule": "FULL_ADVANTAGE", "created_at": 1_000_000},
        headers=auth(token),
    )
    session_id = create_resp.json()["id"]

    response = await client.delete(f"/api/v1/sessions/{session_id}", headers=auth(token))
    assert response.status_code == 204

    list_response = await client.get("/api/v1/sessions", headers=auth(token))
    assert list_response.json()["total"] == 0


@pytest.mark.asyncio
async def test_delete_session_requires_jwt(client):
    response = await client.delete("/api/v1/sessions/1")
    assert response.status_code in (401, 403)


@pytest.mark.asyncio
async def test_delete_session_404_when_missing(client):
    token = make_token()
    response = await client.delete("/api/v1/sessions/999999", headers=auth(token))
    assert response.status_code == 404
    assert response.json()["error_code"] == "SESSION_NOT_FOUND"
```

- [ ] **Step 7: Vérifier que les tests échouent**

Run: `cd /root/SecondServe/backend && uv run pytest tests/integration/test_sessions_api.py -q`
Expected: FAIL — `405 Method Not Allowed`.

- [ ] **Step 8: Ajouter la route `DELETE /{session_id}`**

Dans `backend/app/api/v1/sessions.py`, ajouter à la fin du fichier :

```python


@router.delete("/{session_id}", status_code=204)
async def delete_session(
    session_id: int,
    service: SessionService = Depends(get_session_service),
):
    await service.delete_session(session_id)
```

- [ ] **Step 9: Vérifier que tous les tests backend passent**

Run: `cd /root/SecondServe/backend && uv run pytest tests/ -q`
Expected: PASS, tous les tests verts.

- [ ] **Step 10: Commit**

```bash
git add backend/app/features/sessions/repository.py backend/app/features/sessions/service.py backend/app/api/v1/sessions.py backend/tests/unit/test_session_service.py backend/tests/integration/test_sessions_api.py
git commit -m "feat(backend): ajouter DELETE /api/v1/sessions/{id} pour la suppression d'un match"
```

---

## Task 3: Frontend — client API `updateSession` / `deleteSession`

**Files:**
- Modify: `web/lib/api.ts`
- Test: `web/lib/api.test.ts`

**Interfaces:**
- Consumes: `SessionDto`, `mapSession`, `RawSession`, `UnauthorizedError` (déjà définis dans `web/lib/api.ts`).
- Produces: `export type UpdateSessionInput = Partial<{ surface: string; matchFormat: string; thirdSetRule: string; opponent: string | null; competitionType: string | null; tournament: string | null; status: "ACTIVE" | "COMPLETED"; result: "VICTORY" | "DEFEAT" | null; scoreText: string | null; createdAt: number; updatedAt: number }>` ; `export async function updateSession(token: string, sessionId: number, input: UpdateSessionInput): Promise<SessionDto>` ; `export async function deleteSession(token: string, sessionId: number): Promise<void>`.

- [ ] **Step 1: Écrire les tests (échouent)**

Ajouter à la fin de `web/lib/api.test.ts` :

```typescript

describe("updateSession", () => {
  it("envoie uniquement les champs fournis en snake_case et mappe la SessionDto retournée", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        id: 7,
        surface: "HARD",
        match_format: "BEST_OF_3",
        third_set_rule: "FULL_ADVANTAGE",
        opponent: "Martin",
        competition_type: null,
        tournament: null,
        status: "COMPLETED",
        session_type: "MATCH",
        result: "VICTORY",
        score_text: "6-4 · 6-3",
        score_seed_json: null,
        created_at: 1000,
        updated_at: 1000,
      }),
    });
    vi.stubGlobal("fetch", fetchMock);

    const result = await updateSession("jwt-token", 7, { opponent: "Martin", surface: "HARD" });

    expect(result.opponent).toBe("Martin");
    expect(result.surface).toBe("HARD");
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toContain("/sessions/7");
    expect(init.method).toBe("PATCH");
    expect(JSON.parse(init.body as string)).toEqual({ opponent: "Martin", surface: "HARD" });
  });

  it("lève UnauthorizedError sur 401", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 401 }));
    await expect(updateSession("jwt-token", 7, { opponent: "Martin" })).rejects.toThrow(UnauthorizedError);
  });
});

describe("deleteSession", () => {
  it("appelle DELETE sans lever d'erreur sur 204", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 204 });
    vi.stubGlobal("fetch", fetchMock);

    await deleteSession("jwt-token", 7);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toContain("/sessions/7");
    expect(init.method).toBe("DELETE");
  });

  it("lève UnauthorizedError sur 401", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 401 }));
    await expect(deleteSession("jwt-token", 7)).rejects.toThrow(UnauthorizedError);
  });
});
```

Ajouter `updateSession, deleteSession,` à l'import de `web/lib/api.test.ts` en haut du fichier (import depuis `./api`).

- [ ] **Step 2: Vérifier que les tests échouent**

Run: `cd /root/SecondServe/web && yarn test lib/api.test.ts`
Expected: FAIL — `updateSession is not a function`.

- [ ] **Step 3: Implémenter `updateSession` et `deleteSession`**

Ajouter à la fin de `web/lib/api.ts` :

```typescript

export type UpdateSessionInput = Partial<{
  surface: string;
  matchFormat: string;
  thirdSetRule: string;
  opponent: string | null;
  competitionType: string | null;
  tournament: string | null;
  status: "ACTIVE" | "COMPLETED";
  result: "VICTORY" | "DEFEAT" | null;
  scoreText: string | null;
  createdAt: number;
  updatedAt: number;
}>;

const UPDATE_SESSION_FIELD_MAP: Record<keyof UpdateSessionInput, string> = {
  surface: "surface",
  matchFormat: "match_format",
  thirdSetRule: "third_set_rule",
  opponent: "opponent",
  competitionType: "competition_type",
  tournament: "tournament",
  status: "status",
  result: "result",
  scoreText: "score_text",
  createdAt: "created_at",
  updatedAt: "updated_at",
};

function toUpdateSessionPatch(input: UpdateSessionInput): Record<string, unknown> {
  const patch: Record<string, unknown> = {};
  for (const key of Object.keys(input) as (keyof UpdateSessionInput)[]) {
    const value = input[key];
    if (value !== undefined) patch[UPDATE_SESSION_FIELD_MAP[key]] = value;
  }
  return patch;
}

export async function updateSession(token: string, sessionId: number, input: UpdateSessionInput): Promise<SessionDto> {
  const response = await fetch(`${process.env.API_BASE_URL}/api/v1/sessions/${sessionId}`, {
    method: "PATCH",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(toUpdateSessionPatch(input)),
    cache: "no-store",
  });
  if (response.status === 401) throw new UnauthorizedError();
  if (!response.ok) throw new Error(`Erreur inattendue (${response.status})`);
  const raw = (await response.json()) as RawSession;
  return mapSession(raw);
}

export async function deleteSession(token: string, sessionId: number): Promise<void> {
  const response = await fetch(`${process.env.API_BASE_URL}/api/v1/sessions/${sessionId}`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (response.status === 401) throw new UnauthorizedError();
  if (!response.ok) throw new Error(`Erreur inattendue (${response.status})`);
}
```

- [ ] **Step 4: Vérifier que les tests passent**

Run: `cd /root/SecondServe/web && yarn test lib/api.test.ts`
Expected: PASS, tous les tests verts.

- [ ] **Step 5: Commit**

```bash
git add web/lib/api.ts web/lib/api.test.ts
git commit -m "feat(web): ajouter updateSession/deleteSession au client API"
```

---

## Task 4: Frontend — route proxy `PATCH`/`DELETE` de session

**Files:**
- Create: `web/app/api/console/sessions/[sessionId]/route.ts`
- Test: `web/app/api/console/sessions/[sessionId]/route.test.ts`

**Interfaces:**
- Consumes: `updateSession`, `deleteSession`, `UnauthorizedError`, `UpdateSessionInput` (Task 3, `@/lib/api`) ; `getSessionToken` (`@/lib/auth`).
- Produces: `PATCH` et `DELETE` handlers Next.js sur `/api/console/sessions/[sessionId]`, appelés par le frontend via `fetch(\`/api/console/sessions/${id}\`, { method: "PATCH" | "DELETE" })`.

- [ ] **Step 1: Écrire le test (échoue)**

Créer `web/app/api/console/sessions/[sessionId]/route.test.ts` :

```typescript
// @vitest-environment node
import { describe, expect, it, vi, afterEach } from "vitest";

vi.mock("next/headers", () => ({ cookies: vi.fn() }));
vi.mock("@/lib/api", () => ({
  updateSession: vi.fn(),
  deleteSession: vi.fn(),
  UnauthorizedError: class UnauthorizedError extends Error {},
}));

import { cookies } from "next/headers";
import { updateSession, deleteSession } from "@/lib/api";
import { PATCH, DELETE } from "./route";

afterEach(() => {
  vi.clearAllMocks();
});

function params(sessionId: string) {
  return { params: Promise.resolve({ sessionId }) };
}

function jsonRequest(method: string, body: unknown): Request {
  return new Request("http://localhost/x", {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

describe("PATCH /api/console/sessions/[sessionId]", () => {
  it("retourne 401 sans cookie", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => undefined } as never);
    const response = await PATCH(jsonRequest("PATCH", {}), params("7"));
    expect(response.status).toBe(401);
  });

  it("relaie vers updateSession et retourne la session mise à jour", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(updateSession).mockResolvedValue({
      id: 7,
      surface: "HARD",
      matchFormat: "BEST_OF_3",
      thirdSetRule: "FULL_ADVANTAGE",
      opponent: "Martin",
      competitionType: null,
      tournament: null,
      status: "COMPLETED",
      sessionType: "MATCH",
      result: "VICTORY",
      scoreText: "6-4 · 6-3",
      scoreSeedJson: null,
      createdAt: 1000,
      updatedAt: 1000,
    });

    const patch = { opponent: "Martin", status: "COMPLETED" as const };
    const response = await PATCH(jsonRequest("PATCH", patch), params("7"));
    expect(response.status).toBe(200);
    const data = await response.json();
    expect(data.opponent).toBe("Martin");
    expect(vi.mocked(updateSession)).toHaveBeenCalledWith("jwt-abc", 7, patch);
  });

  it("retourne 401 si updateSession lève UnauthorizedError", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    const { UnauthorizedError } = await import("@/lib/api");
    vi.mocked(updateSession).mockRejectedValue(new UnauthorizedError());

    const response = await PATCH(jsonRequest("PATCH", {}), params("7"));
    expect(response.status).toBe(401);
  });
});

describe("DELETE /api/console/sessions/[sessionId]", () => {
  it("retourne 401 sans cookie", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => undefined } as never);
    const response = await DELETE(jsonRequest("DELETE", {}), params("7"));
    expect(response.status).toBe(401);
  });

  it("relaie vers deleteSession et retourne 204", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(deleteSession).mockResolvedValue(undefined);

    const response = await DELETE(jsonRequest("DELETE", {}), params("7"));
    expect(response.status).toBe(204);
    expect(vi.mocked(deleteSession)).toHaveBeenCalledWith("jwt-abc", 7);
  });

  it("retourne 401 si deleteSession lève UnauthorizedError", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    const { UnauthorizedError } = await import("@/lib/api");
    vi.mocked(deleteSession).mockRejectedValue(new UnauthorizedError());

    const response = await DELETE(jsonRequest("DELETE", {}), params("7"));
    expect(response.status).toBe(401);
  });
});
```

- [ ] **Step 2: Vérifier que le test échoue**

Run: `cd /root/SecondServe/web && yarn test app/api/console/sessions/\[sessionId\]/route.test.ts`
Expected: FAIL — le fichier `route.ts` correspondant n'existe pas.

- [ ] **Step 3: Créer la route proxy**

Créer `web/app/api/console/sessions/[sessionId]/route.ts` :

```typescript
import { NextResponse } from "next/server";
import { getSessionToken } from "@/lib/auth";
import { updateSession, deleteSession, UnauthorizedError } from "@/lib/api";
import type { UpdateSessionInput } from "@/lib/api";

type RouteParams = { params: Promise<{ sessionId: string }> };

export async function PATCH(request: Request, { params }: RouteParams) {
  const token = await getSessionToken();
  if (!token) return NextResponse.json({ error: "unauthorized" }, { status: 401 });

  const { sessionId } = await params;
  const body = (await request.json()) as UpdateSessionInput;
  try {
    const session = await updateSession(token, Number(sessionId), body);
    return NextResponse.json(session);
  } catch (error) {
    if (error instanceof UnauthorizedError) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
    throw error;
  }
}

export async function DELETE(request: Request, { params }: RouteParams) {
  const token = await getSessionToken();
  if (!token) return NextResponse.json({ error: "unauthorized" }, { status: 401 });

  const { sessionId } = await params;
  try {
    await deleteSession(token, Number(sessionId));
    return new NextResponse(null, { status: 204 });
  } catch (error) {
    if (error instanceof UnauthorizedError) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
    throw error;
  }
}
```

- [ ] **Step 4: Vérifier que les tests passent**

Run: `cd /root/SecondServe/web && yarn test app/api/console/sessions/\[sessionId\]/route.test.ts`
Expected: PASS, tous les tests verts.

- [ ] **Step 5: Commit**

```bash
git add web/app/api/console/sessions/\[sessionId\]/route.ts web/app/api/console/sessions/\[sessionId\]/route.test.ts
git commit -m "feat(web): route proxy PATCH/DELETE pour /api/console/sessions/[sessionId]"
```

---

## Task 5: Frontend — `computeSetsOutcome` dans `scoreEngine.ts`

**Files:**
- Modify: `web/lib/scoreEngine.ts:54-56`
- Test: `web/lib/scoreEngine.test.ts`

**Interfaces:**
- Consumes: `SetResult` (déjà défini, `{ gamesA: number; gamesB: number }`).
- Produces: `export function formatSetsText(sets: SetResult[]): string` ; `export function computeSetsOutcome(sets: SetResult[]): { result: "VICTORY" | "DEFEAT" | null; scoreText: string }`.

- [ ] **Step 1: Écrire les tests (échouent)**

Ajouter à la fin de `web/lib/scoreEngine.test.ts` :

```typescript

describe("formatSetsText", () => {
  it("joins sets with ' · '", () => {
    expect(formatSetsText([{ gamesA: 6, gamesB: 4 }, { gamesA: 6, gamesB: 3 }])).toBe("6-4 · 6-3");
  });

  it("returns empty string for empty array", () => {
    expect(formatSetsText([])).toBe("");
  });
});

describe("computeSetsOutcome", () => {
  it("returns VICTORY when self wins more sets (2-0)", () => {
    const outcome = computeSetsOutcome([{ gamesA: 6, gamesB: 4 }, { gamesA: 6, gamesB: 3 }]);
    expect(outcome.result).toBe("VICTORY");
    expect(outcome.scoreText).toBe("6-4 · 6-3");
  });

  it("returns VICTORY when self wins in 3 sets (2-1)", () => {
    const outcome = computeSetsOutcome([
      { gamesA: 6, gamesB: 4 },
      { gamesA: 3, gamesB: 6 },
      { gamesA: 10, gamesB: 7 },
    ]);
    expect(outcome.result).toBe("VICTORY");
  });

  it("returns DEFEAT when opponent wins more sets", () => {
    const outcome = computeSetsOutcome([{ gamesA: 4, gamesB: 6 }, { gamesA: 3, gamesB: 6 }]);
    expect(outcome.result).toBe("DEFEAT");
  });

  it("returns DEFEAT for a single lost set (BEST_OF_1)", () => {
    const outcome = computeSetsOutcome([{ gamesA: 4, gamesB: 6 }]);
    expect(outcome.result).toBe("DEFEAT");
  });

  it("returns null when sets are equal (aucun set)", () => {
    expect(computeSetsOutcome([]).result).toBeNull();
  });
});
```

Modifier l'import en haut du fichier (ligne 2) pour ajouter les nouvelles fonctions :

```typescript
import { TennisScoreEngine, formatScoreText, deriveMatchResult, formatSetsText, computeSetsOutcome } from "./scoreEngine";
```

- [ ] **Step 2: Vérifier que les tests échouent**

Run: `cd /root/SecondServe/web && yarn test lib/scoreEngine.test.ts`
Expected: FAIL — `formatSetsText is not a function`.

- [ ] **Step 3: Implémenter, en réutilisant la logique dans `formatScoreText`**

Dans `web/lib/scoreEngine.ts`, remplacer les lignes 54-56 :

```typescript
export function formatScoreText(score: MatchScore): string {
  return score.completedSets.map((s) => `${s.gamesA}-${s.gamesB}`).join(" · ");
}
```

par :

```typescript
export function formatSetsText(sets: SetResult[]): string {
  return sets.map((s) => `${s.gamesA}-${s.gamesB}`).join(" · ");
}

export function formatScoreText(score: MatchScore): string {
  return formatSetsText(score.completedSets);
}

export function computeSetsOutcome(sets: SetResult[]): { result: "VICTORY" | "DEFEAT" | null; scoreText: string } {
  const setsWonA = sets.filter((s) => s.gamesA > s.gamesB).length;
  const setsWonB = sets.filter((s) => s.gamesB > s.gamesA).length;
  const result: "VICTORY" | "DEFEAT" | null = setsWonA === setsWonB ? null : setsWonA > setsWonB ? "VICTORY" : "DEFEAT";
  return { result, scoreText: formatSetsText(sets) };
}
```

- [ ] **Step 4: Vérifier que tous les tests de `scoreEngine.test.ts` passent (y compris les tests existants de `formatScoreText`)**

Run: `cd /root/SecondServe/web && yarn test lib/scoreEngine.test.ts`
Expected: PASS, tous les tests verts (le comportement de `formatScoreText` est inchangé).

- [ ] **Step 5: Commit**

```bash
git add web/lib/scoreEngine.ts web/lib/scoreEngine.test.ts
git commit -m "feat(web): ajouter computeSetsOutcome pour dériver résultat/score depuis une liste de sets"
```

---

## Task 6: Frontend — composant partagé `SetScoreInputs`

**Files:**
- Create: `web/components/console/SetScoreInputs.tsx`
- Create: `web/components/console/SetScoreInputs.module.css`
- Test: `web/components/console/SetScoreInputs.test.tsx`

**Interfaces:**
- Consumes: `SetResult` (`@/lib/types`).
- Produces: `export type SetScoreEntry = { self: string; opponent: string }` ; `export function parseSetEntries(entries: SetScoreEntry[]): SetResult[]` ; `export function SetScoreInputs(props: { sets: SetScoreEntry[]; onChange: (sets: SetScoreEntry[]) => void; maxSets: number }): JSX.Element`. Utilisé par les Tasks 11 (`NewMatchForm`) et 13 (`MatchEditForm`).

- [ ] **Step 1: Écrire les tests (échouent)**

Créer `web/components/console/SetScoreInputs.test.tsx` :

```tsx
import { describe, expect, it, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { SetScoreInputs, parseSetEntries } from "./SetScoreInputs";
import type { SetScoreEntry } from "./SetScoreInputs";

describe("parseSetEntries", () => {
  it("ignore les sets incomplets ou vides", () => {
    const entries: SetScoreEntry[] = [
      { self: "6", opponent: "4" },
      { self: "", opponent: "" },
      { self: "3", opponent: "6" },
    ];
    expect(parseSetEntries(entries)).toEqual([
      { gamesA: 6, gamesB: 4 },
      { gamesA: 3, gamesB: 6 },
    ]);
  });

  it("retourne un tableau vide si aucun set n'est rempli", () => {
    expect(parseSetEntries([{ self: "", opponent: "" }])).toEqual([]);
  });

  it("ignore les valeurs négatives", () => {
    expect(parseSetEntries([{ self: "-1", opponent: "4" }])).toEqual([]);
  });
});

describe("SetScoreInputs", () => {
  it("affiche un champ par set fourni", () => {
    render(
      <SetScoreInputs
        sets={[{ self: "6", opponent: "4" }, { self: "", opponent: "" }]}
        onChange={vi.fn()}
        maxSets={3}
      />
    );
    expect(screen.getByLabelText("Set 1 - jeux moi")).toHaveValue(6);
    expect(screen.getByLabelText("Set 2 - jeux adversaire")).toHaveValue(null);
  });

  it("appelle onChange avec la valeur modifiée au changement d'un champ", () => {
    const onChange = vi.fn();
    render(<SetScoreInputs sets={[{ self: "", opponent: "" }]} onChange={onChange} maxSets={3} />);
    fireEvent.change(screen.getByLabelText("Set 1 - jeux moi"), { target: { value: "6" } });
    expect(onChange).toHaveBeenCalledWith([{ self: "6", opponent: "" }]);
  });

  it("ajoute un set au clic sur le bouton d'ajout, dans la limite de maxSets", () => {
    const onChange = vi.fn();
    render(
      <SetScoreInputs sets={[{ self: "6", opponent: "4" }, { self: "3", opponent: "6" }]} onChange={onChange} maxSets={3} />
    );
    fireEvent.click(screen.getByRole("button", { name: /ajouter un set/i }));
    expect(onChange).toHaveBeenCalledWith([
      { self: "6", opponent: "4" },
      { self: "3", opponent: "6" },
      { self: "", opponent: "" },
    ]);
  });

  it("n'affiche pas le bouton d'ajout quand maxSets est atteint", () => {
    render(
      <SetScoreInputs
        sets={[{ self: "6", opponent: "4" }, { self: "3", opponent: "6" }]}
        onChange={vi.fn()}
        maxSets={2}
      />
    );
    expect(screen.queryByRole("button", { name: /ajouter un set/i })).not.toBeInTheDocument();
  });

  it("supprime un set au clic sur son bouton de suppression", () => {
    const onChange = vi.fn();
    render(
      <SetScoreInputs sets={[{ self: "6", opponent: "4" }, { self: "3", opponent: "6" }]} onChange={onChange} maxSets={3} />
    );
    fireEvent.click(screen.getByRole("button", { name: /supprimer le set 2/i }));
    expect(onChange).toHaveBeenCalledWith([{ self: "6", opponent: "4" }]);
  });

  it("n'affiche pas de bouton de suppression s'il ne reste qu'un set", () => {
    render(<SetScoreInputs sets={[{ self: "6", opponent: "4" }]} onChange={vi.fn()} maxSets={3} />);
    expect(screen.queryByRole("button", { name: /supprimer le set 1/i })).not.toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Vérifier que les tests échouent**

Run: `cd /root/SecondServe/web && yarn test components/console/SetScoreInputs.test.tsx`
Expected: FAIL — le fichier `SetScoreInputs.tsx` n'existe pas.

- [ ] **Step 3: Créer `SetScoreInputs.tsx`**

Créer `web/components/console/SetScoreInputs.tsx` :

```tsx
import type { SetResult } from "@/lib/types";
import styles from "./SetScoreInputs.module.css";

export type SetScoreEntry = { self: string; opponent: string };

type Props = {
  sets: SetScoreEntry[];
  onChange: (sets: SetScoreEntry[]) => void;
  maxSets: number;
};

export function parseSetEntries(entries: SetScoreEntry[]): SetResult[] {
  return entries
    .filter((e) => e.self.trim() !== "" && e.opponent.trim() !== "")
    .map((e) => ({ gamesA: Number(e.self), gamesB: Number(e.opponent) }))
    .filter((s) => Number.isFinite(s.gamesA) && Number.isFinite(s.gamesB) && s.gamesA >= 0 && s.gamesB >= 0);
}

export function SetScoreInputs({ sets, onChange, maxSets }: Props) {
  function updateSet(index: number, field: "self" | "opponent", value: string) {
    onChange(sets.map((s, i) => (i === index ? { ...s, [field]: value } : s)));
  }

  function addSet() {
    if (sets.length >= maxSets) return;
    onChange([...sets, { self: "", opponent: "" }]);
  }

  function removeSet(index: number) {
    onChange(sets.filter((_, i) => i !== index));
  }

  return (
    <div className={styles.container}>
      {sets.map((set, index) => (
        <div key={index} className={styles.setRow}>
          <span className={styles.setLabel}>Set {index + 1}</span>
          <input
            type="number"
            min={0}
            value={set.self}
            onChange={(e) => updateSet(index, "self", e.target.value)}
            aria-label={`Set ${index + 1} - jeux moi`}
            className={styles.setInput}
          />
          <span className={styles.dash}>-</span>
          <input
            type="number"
            min={0}
            value={set.opponent}
            onChange={(e) => updateSet(index, "opponent", e.target.value)}
            aria-label={`Set ${index + 1} - jeux adversaire`}
            className={styles.setInput}
          />
          {sets.length > 1 && (
            <button
              type="button"
              className={styles.removeButton}
              onClick={() => removeSet(index)}
              aria-label={`Supprimer le set ${index + 1}`}
            >
              ×
            </button>
          )}
        </div>
      ))}
      {sets.length < maxSets && (
        <button type="button" className={styles.addButton} onClick={addSet}>
          + Ajouter un set
        </button>
      )}
    </div>
  );
}
```

Créer `web/components/console/SetScoreInputs.module.css` :

```css
.container {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.setRow {
  display: flex;
  align-items: center;
  gap: 8px;
}

.setLabel {
  width: 48px;
  font-size: 12px;
  font-weight: 600;
  color: var(--ss-muted);
}

.setInput {
  width: 56px;
  min-height: 44px;
  border-radius: 8px;
  border: 1px solid var(--ss-border);
  background: var(--ss-bg);
  color: var(--ss-text);
  padding: 0 8px;
  font-size: 14px;
  text-align: center;
}

.dash {
  color: var(--ss-muted);
}

.removeButton {
  min-width: 32px;
  min-height: 32px;
  border-radius: 8px;
  border: 1px solid var(--ss-border);
  background: transparent;
  color: var(--ss-muted);
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
}

.addButton {
  align-self: flex-start;
  min-height: 40px;
  padding: 0 14px;
  border-radius: 8px;
  border: 1px dashed var(--ss-border);
  background: transparent;
  color: var(--ss-muted);
  font-weight: 600;
  font-size: 13px;
  cursor: pointer;
}
```

- [ ] **Step 4: Vérifier que les tests passent**

Run: `cd /root/SecondServe/web && yarn test components/console/SetScoreInputs.test.tsx`
Expected: PASS, tous les tests verts.

- [ ] **Step 5: Commit**

```bash
git add web/components/console/SetScoreInputs.tsx web/components/console/SetScoreInputs.module.css web/components/console/SetScoreInputs.test.tsx
git commit -m "feat(web): composant partagé SetScoreInputs pour la saisie de scores par set"
```

---

## Task 7: Frontend — navigation responsive (`navItems` partagés + `MobileTabBar` + sidebar masquée sous 900px)

**Files:**
- Create: `web/components/dashboard/navItems.ts`
- Create: `web/components/dashboard/navItems.test.ts`
- Create: `web/components/dashboard/MobileTabBar.tsx`
- Create: `web/components/dashboard/MobileTabBar.module.css`
- Create: `web/components/dashboard/MobileTabBar.test.tsx`
- Modify: `web/components/dashboard/Sidebar.tsx`
- Modify: `web/components/dashboard/Sidebar.module.css`
- Modify: `web/app/dashboard/layout.tsx`
- Modify: `web/app/dashboard/layout.module.css`

**Interfaces:**
- Produces: `export type NavItem = { href: string; label: string }` ; `export const NAV_ITEMS: NavItem[]` ; `export function isNavItemActive(pathname: string, href: string): boolean` (dans `navItems.ts`, consommé par `Sidebar.tsx` et `MobileTabBar.tsx`, et étendu par la Task 12 pour ajouter l'entrée « Historique »).

- [ ] **Step 1: Écrire le test de `navItems.ts` (échoue)**

Créer `web/components/dashboard/navItems.test.ts` :

```typescript
import { describe, expect, it } from "vitest";
import { NAV_ITEMS, isNavItemActive } from "./navItems";

describe("NAV_ITEMS", () => {
  it("contient le tableau de bord et la console de saisie", () => {
    expect(NAV_ITEMS.map((item) => item.href)).toEqual(["/dashboard", "/dashboard/console"]);
  });
});

describe("isNavItemActive", () => {
  it("est actif sur /dashboard uniquement pour une correspondance exacte", () => {
    expect(isNavItemActive("/dashboard", "/dashboard")).toBe(true);
    expect(isNavItemActive("/dashboard/console", "/dashboard")).toBe(false);
  });

  it("est actif sur une sous-route pour les autres items", () => {
    expect(isNavItemActive("/dashboard/console/42", "/dashboard/console")).toBe(true);
    expect(isNavItemActive("/dashboard", "/dashboard/console")).toBe(false);
  });
});
```

- [ ] **Step 2: Vérifier que le test échoue**

Run: `cd /root/SecondServe/web && yarn test components/dashboard/navItems.test.ts`
Expected: FAIL — le fichier `navItems.ts` n'existe pas.

- [ ] **Step 3: Créer `navItems.ts`**

Créer `web/components/dashboard/navItems.ts` :

```typescript
export type NavItem = { href: string; label: string };

export const NAV_ITEMS: NavItem[] = [
  { href: "/dashboard", label: "Tableau de bord" },
  { href: "/dashboard/console", label: "Console de saisie" },
];

export function isNavItemActive(pathname: string, href: string): boolean {
  return href === "/dashboard" ? pathname === href : pathname.startsWith(href);
}
```

- [ ] **Step 4: Vérifier que le test passe**

Run: `cd /root/SecondServe/web && yarn test components/dashboard/navItems.test.ts`
Expected: PASS.

- [ ] **Step 5: Refactoriser `Sidebar.tsx` pour utiliser `navItems.ts`**

Remplacer tout le contenu de `web/components/dashboard/Sidebar.tsx` par :

```tsx
"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { NAV_ITEMS, isNavItemActive } from "./navItems";
import styles from "./Sidebar.module.css";

export function Sidebar() {
  const pathname = usePathname();

  return (
    <aside className={styles.sidebar}>
      <div className={styles.logo}>SecondServe</div>
      <nav className={styles.nav}>
        {NAV_ITEMS.map((item) => (
          <Link
            key={item.href}
            href={item.href}
            className={isNavItemActive(pathname, item.href) ? styles.navItemActive : styles.navItem}
          >
            <span className={styles.dot} />
            {item.label}
          </Link>
        ))}
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

- [ ] **Step 6: Vérifier que `Sidebar.test.tsx` (existant, inchangé) passe toujours**

Run: `cd /root/SecondServe/web && yarn test components/dashboard/Sidebar.test.tsx`
Expected: PASS, les 3 tests existants passent sans modification (comportement identique, refactorisation interne uniquement).

- [ ] **Step 7: Masquer la sidebar sous 900px**

Ajouter à la fin de `web/components/dashboard/Sidebar.module.css` :

```css

@media (max-width: 899px) {
  .sidebar {
    display: none;
  }
}
```

- [ ] **Step 8: Écrire le test de `MobileTabBar` (échoue)**

Créer `web/components/dashboard/MobileTabBar.test.tsx` :

```tsx
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";

const usePathnameMock = vi.fn();
vi.mock("next/navigation", () => ({ usePathname: () => usePathnameMock() }));

import { MobileTabBar } from "./MobileTabBar";

describe("MobileTabBar", () => {
  it("met en avant Tableau de bord sur /dashboard", () => {
    usePathnameMock.mockReturnValue("/dashboard");
    render(<MobileTabBar />);
    expect(screen.getByRole("link", { name: /tableau de bord/i })).toHaveClass("tabActive");
    expect(screen.getByRole("link", { name: /console de saisie/i })).not.toHaveClass("tabActive");
  });

  it("met en avant Console de saisie sur /dashboard/console", () => {
    usePathnameMock.mockReturnValue("/dashboard/console");
    render(<MobileTabBar />);
    expect(screen.getByRole("link", { name: /console de saisie/i })).toHaveClass("tabActive");
  });
});
```

- [ ] **Step 9: Vérifier que le test échoue**

Run: `cd /root/SecondServe/web && yarn test components/dashboard/MobileTabBar.test.tsx`
Expected: FAIL — le fichier `MobileTabBar.tsx` n'existe pas.

- [ ] **Step 10: Créer `MobileTabBar.tsx` et son CSS**

Créer `web/components/dashboard/MobileTabBar.tsx` :

```tsx
"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { NAV_ITEMS, isNavItemActive } from "./navItems";
import styles from "./MobileTabBar.module.css";

export function MobileTabBar() {
  const pathname = usePathname();

  return (
    <nav className={styles.tabBar}>
      {NAV_ITEMS.map((item) => (
        <Link
          key={item.href}
          href={item.href}
          className={isNavItemActive(pathname, item.href) ? styles.tabActive : styles.tab}
        >
          {item.label}
        </Link>
      ))}
    </nav>
  );
}
```

Créer `web/components/dashboard/MobileTabBar.module.css` :

```css
.tabBar {
  display: none;
}

@media (max-width: 899px) {
  .tabBar {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    display: flex;
    justify-content: space-around;
    background: var(--ss-surface);
    border-top: 1px solid var(--ss-border);
    padding: 8px 4px calc(8px + env(safe-area-inset-bottom));
    z-index: 10;
  }
}

.tab,
.tabActive {
  flex: 1;
  text-align: center;
  font-size: 11px;
  font-weight: 600;
  text-decoration: none;
  padding: 8px 4px;
  border-radius: 10px;
}

.tab {
  color: var(--ss-muted);
}

.tabActive {
  color: var(--ss-text);
  background: var(--ss-bg);
}
```

- [ ] **Step 11: Vérifier que le test passe**

Run: `cd /root/SecondServe/web && yarn test components/dashboard/MobileTabBar.test.tsx`
Expected: PASS.

- [ ] **Step 12: Intégrer `MobileTabBar` dans le layout du dashboard**

Remplacer le contenu de `web/app/dashboard/layout.tsx` :

```tsx
import { Sidebar } from "@/components/dashboard/Sidebar";
import { MobileTabBar } from "@/components/dashboard/MobileTabBar";
import styles from "./layout.module.css";

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className={styles.shell}>
      <Sidebar />
      <main className={styles.content}>{children}</main>
      <MobileTabBar />
    </div>
  );
}
```

Ajouter à la fin de `web/app/dashboard/layout.module.css` :

```css

@media (max-width: 899px) {
  .content {
    padding-bottom: 88px;
  }
}
```

- [ ] **Step 13: Vérifier que l'ensemble des tests du module dashboard passe**

Run: `cd /root/SecondServe/web && yarn test components/dashboard`
Expected: PASS, tous les tests verts.

- [ ] **Step 14: Commit**

```bash
git add web/components/dashboard/navItems.ts web/components/dashboard/navItems.test.ts web/components/dashboard/MobileTabBar.tsx web/components/dashboard/MobileTabBar.module.css web/components/dashboard/MobileTabBar.test.tsx web/components/dashboard/Sidebar.tsx web/components/dashboard/Sidebar.module.css web/app/dashboard/layout.tsx web/app/dashboard/layout.module.css
git commit -m "feat(web): navigation mobile en bottom tab bar sous 900px"
```

---

## Task 8: Frontend — grilles responsive du tableau de bord + score sticky de la console

**Files:**
- Modify: `web/components/dashboard/DashboardView.module.css`
- Modify: `web/components/console/ConsoleScreen.module.css`

**Interfaces:** Aucune (CSS uniquement, aucun changement de comportement testable au-delà du rendu visuel).

- [ ] **Step 1: Ajouter le repli en une colonne des grilles du dashboard sous 900px**

Ajouter à la fin de `web/components/dashboard/DashboardView.module.css` :

```css

@media (max-width: 899px) {
  .kpiGrid {
    grid-template-columns: 1fr;
  }

  .middleGrid {
    grid-template-columns: 1fr;
  }
}
```

- [ ] **Step 2: Rendre le score de la console sticky en haut sous 900px**

Ajouter à la fin de `web/components/console/ConsoleScreen.module.css` :

```css

@media (max-width: 899px) {
  .leftColumn {
    position: sticky;
    top: 0;
    z-index: 5;
    background: var(--ss-bg);
    padding-bottom: 8px;
  }
}
```

- [ ] **Step 3: Vérifier que les suites de tests concernées passent toujours (CSS pur, aucun changement de rendu attendu par les tests)**

Run: `cd /root/SecondServe/web && yarn test components/dashboard/DashboardView.test.tsx components/console/ConsoleScreen.test.tsx`
Expected: PASS, aucun test cassé.

- [ ] **Step 4: Commit**

```bash
git add web/components/dashboard/DashboardView.module.css web/components/console/ConsoleScreen.module.css
git commit -m "style(web): grilles dashboard en 1 colonne et score sticky en console sous 900px"
```

---

## Task 9: Frontend — `RecentMatchesTable` en cartes empilées sous 900px

**Files:**
- Modify: `web/components/dashboard/RecentMatchesTable.module.css`

**Interfaces:** Aucune (CSS uniquement — le repli en carte se fait via `display: grid` + `grid-template-areas` sur les classes déjà présentes dans le JSX, sans toucher `RecentMatchesTable.tsx`).

- [ ] **Step 1: Ajouter la mise en page carte sous 900px**

Ajouter à la fin de `web/components/dashboard/RecentMatchesTable.module.css` :

```css

@media (max-width: 899px) {
  .columnHeader {
    display: none;
  }

  .row {
    display: grid;
    grid-template-columns: 1fr auto;
    grid-template-areas:
      "date result"
      "opponent opponent"
      "surface score";
    row-gap: 6px;
    padding: 12px 16px;
  }

  .dateCol {
    grid-area: date;
    width: auto;
  }

  .opponentCol {
    grid-area: opponent;
  }

  .surfaceCol {
    grid-area: surface;
    width: auto;
  }

  .scoreCol {
    grid-area: score;
    width: auto;
    text-align: right;
  }

  .resultCol {
    grid-area: result;
    width: auto;
    text-align: right;
  }
}
```

- [ ] **Step 2: Vérifier que `RecentMatchesTable.test.tsx` (existant, inchangé) passe toujours**

Run: `cd /root/SecondServe/web && yarn test components/dashboard/RecentMatchesTable.test.tsx`
Expected: PASS, les 4 tests existants passent (le test ne vérifie que le contenu texte/rôles, pas la mise en page CSS).

- [ ] **Step 3: Commit**

```bash
git add web/components/dashboard/RecentMatchesTable.module.css
git commit -m "style(web): RecentMatchesTable en cartes empilées sous 900px"
```

---

## Task 10: Frontend — corrections responsive de la page live publique

**Files:**
- Modify: `web/components/ScoreTable.tsx:46-52`
- Modify: `web/components/ScoreTable.module.css`
- Modify: `web/components/LiveScoreBoard.module.css`
- Test: `web/components/ScoreTable.test.tsx`

**Interfaces:** Aucune nouvelle interface — ajustement CSS + un `<span>` d'encapsulation pour permettre la troncature du nom de joueur.

**Contexte** : `ScoreTable` utilise des colonnes à largeur fixe (`sets`: 44px, `games`: 62px, `points`: 58px) plus un avatar de 38px et un padding de 26px de chaque côté (`playerRow`). Sur un écran de 320-375px de large, un nom de joueur long peut déborder horizontalement. `LiveScoreBoard.module.css` applique par ailleurs un padding fixe de 40px sur `.page`, ce qui réduit d'autant la largeur disponible sur mobile. La page `login` a été vérifiée et ne présente pas de risque de débordement réel (pas de largeur fixe, contenu court) — aucun changement n'y est nécessaire.

- [ ] **Step 1: Écrire le test vérifiant la troncature du nom (échoue)**

Ouvrir `web/components/ScoreTable.test.tsx` (utilise déjà un helper `buildSnapshot(overrides)` en tête de fichier), puis ajouter un test qui vérifie que le nom du joueur est bien rendu dans un élément dédié (nécessaire pour cibler la troncature CSS sans affecter l'avatar) :

```tsx

it("rend le nom du joueur dans un span dédié pour permettre la troncature CSS", () => {
  render(<ScoreTable snapshot={buildSnapshot({ playerAName: "Jean-Baptiste-Alexandre" })} />);
  const nameNode = screen.getByText("Jean-Baptiste-Alexandre");
  expect(nameNode.tagName).toBe("SPAN");
  expect(nameNode.className).toContain("playerNameText");
});
```

- [ ] **Step 2: Vérifier que le test échoue**

Run: `cd /root/SecondServe/web && yarn test components/ScoreTable.test.tsx`
Expected: FAIL — le nom est actuellement un texte brut, pas un `<span>` avec la classe `playerNameText`.

- [ ] **Step 3: Encapsuler le nom du joueur dans `ScoreTable.tsx`**

Dans `web/components/ScoreTable.tsx`, remplacer les lignes 47-52 :

```tsx
            <span className={styles.playerName}>
              <span className={`${styles.avatar} ${row.leading ? styles.avatarLeading : styles.avatarTrailing}`}>
                {row.name.charAt(0).toUpperCase()}
              </span>
              {row.name}
            </span>
```

par :

```tsx
            <span className={styles.playerName}>
              <span className={`${styles.avatar} ${row.leading ? styles.avatarLeading : styles.avatarTrailing}`}>
                {row.name.charAt(0).toUpperCase()}
              </span>
              <span className={styles.playerNameText}>{row.name}</span>
            </span>
```

- [ ] **Step 4: Vérifier que le test passe**

Run: `cd /root/SecondServe/web && yarn test components/ScoreTable.test.tsx`
Expected: PASS.

- [ ] **Step 5: Ajouter les media queries de repli mobile**

Ajouter à la fin de `web/components/ScoreTable.module.css` :

```css

.playerNameText {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 599px) {
  .headerRow,
  .playerRow {
    padding-left: 14px;
    padding-right: 14px;
  }

  .playerName {
    font-size: 15px;
    gap: 8px;
    overflow: hidden;
  }

  .avatar {
    width: 32px;
    height: 32px;
    font-size: 14px;
    flex-shrink: 0;
  }

  .sets {
    width: 32px;
    font-size: 20px;
  }

  .games {
    width: 46px;
    font-size: 34px;
  }

  .points {
    width: 40px;
    font-size: 24px;
  }
}
```

Ajouter à la fin de `web/components/LiveScoreBoard.module.css` :

```css

@media (max-width: 599px) {
  .page {
    padding: 20px 12px;
  }
}
```

- [ ] **Step 6: Vérifier que tous les tests liés à la page live passent**

Run: `cd /root/SecondServe/web && yarn test components/ScoreTable.test.tsx components/LiveScoreBoard.test.tsx`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add web/components/ScoreTable.tsx web/components/ScoreTable.module.css web/components/ScoreTable.test.tsx web/components/LiveScoreBoard.module.css
git commit -m "fix(web): éviter le débordement horizontal de ScoreTable/LiveScoreBoard sur petit écran"
```

---

## Task 11: Frontend — saisie rétroactive dans `NewMatchForm`

**Files:**
- Modify: `web/components/console/NewMatchForm.tsx`
- Modify: `web/components/console/NewMatchForm.module.css`
- Modify: `web/components/console/NewMatchForm.test.tsx`

**Interfaces:**
- Consumes: `SetScoreInputs`, `SetScoreEntry`, `parseSetEntries` (Task 6, `./SetScoreInputs`) ; `computeSetsOutcome` (Task 5, `@/lib/scoreEngine`) ; route proxy `PATCH /api/console/sessions/{id}` (Task 4).
- Produces: comportement inchangé en mode `LIVE` ; en mode `PAST`, crée la session puis la finalise directement, redirige vers `/dashboard`.

- [ ] **Step 1: Écrire les tests du nouveau mode (échouent)**

Ajouter à la fin de `web/components/console/NewMatchForm.test.tsx` :

```tsx

it("affiche le mode « Match en cours » par défaut, sans les champs de score", () => {
  render(<NewMatchForm onCancel={vi.fn()} />);
  expect(screen.queryByLabelText("Set 1 - jeux moi")).not.toBeInTheDocument();
});

it("affiche les champs de score en mode « Match déjà joué »", () => {
  render(<NewMatchForm onCancel={vi.fn()} />);
  fireEvent.click(screen.getByRole("button", { name: /match déjà joué/i }));
  expect(screen.getByLabelText("Set 1 - jeux moi")).toBeInTheDocument();
  expect(screen.getByLabelText("Set 2 - jeux moi")).toBeInTheDocument();
});

it("en mode PAST, crée la session puis la finalise avec le score des sets, et redirige vers /dashboard", async () => {
  const fetchMock = vi.fn().mockImplementation((url: string) => {
    if (String(url).endsWith("/api/console/sessions")) {
      return Promise.resolve({ ok: true, json: async () => ({ id: 42 }) });
    }
    return Promise.resolve({ ok: true, json: async () => ({ id: 42 }) });
  });
  vi.stubGlobal("fetch", fetchMock);

  render(<NewMatchForm onCancel={vi.fn()} />);
  fireEvent.click(screen.getByRole("button", { name: /match déjà joué/i }));
  fireEvent.change(screen.getByLabelText("Set 1 - jeux moi"), { target: { value: "6" } });
  fireEvent.change(screen.getByLabelText("Set 1 - jeux adversaire"), { target: { value: "4" } });
  fireEvent.change(screen.getByLabelText("Set 2 - jeux moi"), { target: { value: "6" } });
  fireEvent.change(screen.getByLabelText("Set 2 - jeux adversaire"), { target: { value: "3" } });

  fireEvent.click(screen.getByRole("button", { name: /enregistrer le match/i }));
  await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/dashboard"));

  const postCall = fetchMock.mock.calls.find(([url]) => String(url).endsWith("/api/console/sessions"));
  const patchCall = fetchMock.mock.calls.find(([url]) => String(url).endsWith("/api/console/sessions/42"));
  expect(postCall).toBeDefined();
  expect(patchCall).toBeDefined();
  const postBody = JSON.parse(postCall![1].body as string);
  const [, init] = patchCall!;
  expect(init.method).toBe("PATCH");
  const body = JSON.parse(init.body as string);
  expect(body).toMatchObject({ status: "COMPLETED", result: "VICTORY", scoreText: "6-4 · 6-3" });
  expect(body.updatedAt).toBe(postBody.createdAt);
});

it("affiche une erreur si la finalisation du match déjà joué échoue", async () => {
  const fetchMock = vi.fn().mockImplementation((url: string) => {
    if (String(url).endsWith("/api/console/sessions")) {
      return Promise.resolve({ ok: true, json: async () => ({ id: 42 }) });
    }
    return Promise.resolve({ ok: false });
  });
  vi.stubGlobal("fetch", fetchMock);

  render(<NewMatchForm onCancel={vi.fn()} />);
  fireEvent.click(screen.getByRole("button", { name: /match déjà joué/i }));
  fireEvent.change(screen.getByLabelText("Set 1 - jeux moi"), { target: { value: "6" } });
  fireEvent.change(screen.getByLabelText("Set 1 - jeux adversaire"), { target: { value: "4" } });

  fireEvent.click(screen.getByRole("button", { name: /enregistrer le match/i }));
  await waitFor(() => expect(screen.getByText(/échec de l'enregistrement du match/i)).toBeInTheDocument());
});
```

- [ ] **Step 2: Vérifier que les tests échouent**

Run: `cd /root/SecondServe/web && yarn test components/console/NewMatchForm.test.tsx`
Expected: FAIL — le bouton « Match déjà joué » et les champs de score n'existent pas encore.

- [ ] **Step 3: Implémenter le toggle et la soumission en mode `PAST`**

Remplacer tout le contenu de `web/components/console/NewMatchForm.tsx` :

```tsx
"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { SetScoreInputs, parseSetEntries } from "./SetScoreInputs";
import type { SetScoreEntry } from "./SetScoreInputs";
import { computeSetsOutcome } from "@/lib/scoreEngine";
import styles from "./NewMatchForm.module.css";

type Props = { onCancel: () => void };
type Mode = "LIVE" | "PAST";

function todayDateInputValue(): string {
  return new Date().toISOString().slice(0, 10);
}

function emptySets(): SetScoreEntry[] {
  return [{ self: "", opponent: "" }, { self: "", opponent: "" }];
}

export function NewMatchForm({ onCancel }: Props) {
  const router = useRouter();
  const [mode, setMode] = useState<Mode>("LIVE");
  const [surface, setSurface] = useState("CLAY");
  const [matchFormat, setMatchFormat] = useState("BEST_OF_3");
  const [thirdSetRule, setThirdSetRule] = useState("FULL_ADVANTAGE");
  const [opponent, setOpponent] = useState("");
  const [date, setDate] = useState(todayDateInputValue());
  const [sets, setSets] = useState<SetScoreEntry[]>(emptySets());
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const maxSets = matchFormat === "BEST_OF_3" ? 3 : 1;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    setError(null);
    const createdAt = new Date(date).getTime();
    try {
      const response = await fetch("/api/console/sessions", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          surface,
          matchFormat,
          thirdSetRule,
          opponent: opponent || null,
          createdAt,
        }),
      });
      if (!response.ok) throw new Error("create_failed");
      const session = await response.json();

      if (mode === "LIVE") {
        router.push(`/dashboard/console/${session.id}`);
        return;
      }

      const parsedSets = parseSetEntries(sets).slice(0, maxSets);
      const { result, scoreText } = computeSetsOutcome(parsedSets);
      const patchResponse = await fetch(`/api/console/sessions/${session.id}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ status: "COMPLETED", result, scoreText, updatedAt: createdAt }),
      });
      if (!patchResponse.ok) throw new Error("finalize_failed");
      router.push("/dashboard");
    } catch {
      setError(
        mode === "LIVE"
          ? "Échec de la création du match, réessayez."
          : "Échec de l'enregistrement du match, réessayez."
      );
      setSubmitting(false);
    }
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit}>
      <div className={styles.modeToggle}>
        <button
          type="button"
          className={mode === "LIVE" ? styles.modeButtonActive : styles.modeButton}
          onClick={() => setMode("LIVE")}
        >
          Match en cours
        </button>
        <button
          type="button"
          className={mode === "PAST" ? styles.modeButtonActive : styles.modeButton}
          onClick={() => setMode("PAST")}
        >
          Match déjà joué
        </button>
      </div>

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

      {mode === "PAST" && (
        <div className={styles.field}>
          Score final
          <SetScoreInputs sets={sets} onChange={setSets} maxSets={maxSets} />
        </div>
      )}

      {error && <p className={styles.error}>{error}</p>}

      <div className={styles.actions}>
        <button type="button" className={styles.cancelButton} onClick={onCancel} disabled={submitting}>
          Annuler
        </button>
        <button type="submit" className={styles.submitButton} disabled={submitting}>
          {submitting
            ? mode === "LIVE"
              ? "Création..."
              : "Enregistrement..."
            : mode === "LIVE"
              ? "Créer et commencer la saisie"
              : "Enregistrer le match"}
        </button>
      </div>
    </form>
  );
}
```

- [ ] **Step 4: Ajouter le style du toggle**

Ajouter à la fin de `web/components/console/NewMatchForm.module.css` :

```css

.modeToggle {
  display: flex;
  gap: 8px;
}

.modeButton,
.modeButtonActive {
  flex: 1;
  min-height: 44px;
  border-radius: 8px;
  font-weight: 700;
  font-size: 13px;
  cursor: pointer;
}

.modeButton {
  background: transparent;
  border: 1px solid var(--ss-border);
  color: var(--ss-muted);
}

.modeButtonActive {
  background: var(--ss-text);
  border: 1px solid var(--ss-text);
  color: var(--ss-bg);
}
```

- [ ] **Step 5: Vérifier que tous les tests de `NewMatchForm` passent (y compris les tests existants du mode LIVE)**

Run: `cd /root/SecondServe/web && yarn test components/console/NewMatchForm.test.tsx`
Expected: PASS, tous les tests verts.

- [ ] **Step 6: Vérifier l'ensemble de la suite console pour non-régression**

Run: `cd /root/SecondServe/web && yarn test components/console`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add web/components/console/NewMatchForm.tsx web/components/console/NewMatchForm.module.css web/components/console/NewMatchForm.test.tsx
git commit -m "feat(web): mode de saisie rétroactive (match déjà joué) dans NewMatchForm"
```

---

## Task 12: Frontend — page Historique (liste + pagination + entrée de navigation)

**Files:**
- Create: `web/components/history/HistoryView.tsx`
- Create: `web/components/history/HistoryView.module.css`
- Create: `web/components/history/HistoryView.test.tsx`
- Create: `web/app/dashboard/history/page.tsx`
- Create: `web/app/dashboard/history/page.test.tsx`
- Modify: `web/components/dashboard/navItems.ts`
- Modify: `web/components/dashboard/navItems.test.ts`

**Interfaces:**
- Consumes: `getSessions`, `UnauthorizedError` (`@/lib/api`) ; `SessionDto` (`@/lib/types`) ; `surfaceLabel` (`@/lib/surfaces`) ; `SESSION_COOKIE` (`@/lib/auth`).
- Produces: `export function HistoryView(props: { matches: SessionDto[] }): JSX.Element` (pagination client 20/page, expose un slot `Modifier`/`Supprimer` par ligne branché dans les Tasks 13 et 14) ; route `/dashboard/history`.

**Note d'implémentation** : ce plan livre `HistoryView` avec les actions **Modifier**/**Supprimer** déjà visibles dans le DOM (boutons), mais leur comportement (ouverture du formulaire d'édition, confirmation de suppression) est branché dans les Tasks 13 et 14. Dans cette tâche, les boutons appellent des callbacks `onEdit`/`onDelete` passés en props, que les tâches suivantes viendront fournir depuis un composant parent — voir Task 13 Step 3 pour le câblage final.

- [ ] **Step 1: Ajouter l'entrée « Historique » à `navItems.ts`**

Dans `web/components/dashboard/navItems.ts`, remplacer :

```typescript
export const NAV_ITEMS: NavItem[] = [
  { href: "/dashboard", label: "Tableau de bord" },
  { href: "/dashboard/console", label: "Console de saisie" },
];
```

par :

```typescript
export const NAV_ITEMS: NavItem[] = [
  { href: "/dashboard", label: "Tableau de bord" },
  { href: "/dashboard/console", label: "Console de saisie" },
  { href: "/dashboard/history", label: "Historique" },
];
```

Dans `web/components/dashboard/navItems.test.ts`, mettre à jour le test existant :

```typescript
  it("contient le tableau de bord, la console de saisie et l'historique", () => {
    expect(NAV_ITEMS.map((item) => item.href)).toEqual(["/dashboard", "/dashboard/console", "/dashboard/history"]);
  });
```

(remplace le test `"contient le tableau de bord et la console de saisie"` de la Task 7.)

- [ ] **Step 2: Vérifier la mise à jour**

Run: `cd /root/SecondServe/web && yarn test components/dashboard/navItems.test.ts components/dashboard/Sidebar.test.tsx components/dashboard/MobileTabBar.test.tsx`
Expected: PASS (les tests de `Sidebar`/`MobileTabBar` de la Task 7 ne vérifient que 2 des items, ils restent valides avec un 3e item ajouté).

- [ ] **Step 3: Écrire les tests de `HistoryView` (échouent)**

Créer `web/components/history/HistoryView.test.tsx` :

```tsx
import { describe, expect, it, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { HistoryView } from "./HistoryView";
import type { SessionDto } from "@/lib/types";

function buildMatch(overrides: Partial<SessionDto> = {}): SessionDto {
  return {
    id: 1,
    surface: "CLAY",
    matchFormat: "BEST_OF_3",
    thirdSetRule: "FULL_ADVANTAGE",
    opponent: "Rafael",
    competitionType: null,
    tournament: null,
    status: "COMPLETED",
    sessionType: "MATCH",
    result: "VICTORY",
    scoreText: "6-4 · 6-3",
    scoreSeedJson: null,
    createdAt: Date.UTC(2026, 0, 15),
    updatedAt: Date.UTC(2026, 0, 15),
    ...overrides,
  };
}

describe("HistoryView", () => {
  it("affiche un message quand il n'y a aucun match", () => {
    render(<HistoryView matches={[]} />);
    expect(screen.getByText("Pas encore de match")).toBeInTheDocument();
  });

  it("affiche chaque match avec ses actions Modifier/Supprimer", () => {
    render(<HistoryView matches={[buildMatch()]} />);
    expect(screen.getByText("Rafael")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /modifier/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /supprimer/i })).toBeInTheDocument();
  });

  it("pagine à 20 matchs par page", () => {
    const matches = Array.from({ length: 25 }, (_, i) =>
      buildMatch({ id: i + 1, opponent: `Joueur ${i + 1}`, createdAt: Date.UTC(2026, 0, 1) - i * 1000 })
    );
    render(<HistoryView matches={matches} />);
    expect(screen.getByText("Joueur 1")).toBeInTheDocument();
    expect(screen.queryByText("Joueur 21")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /page suivante/i }));
    expect(screen.getByText("Joueur 21")).toBeInTheDocument();
    expect(screen.queryByText("Joueur 1")).not.toBeInTheDocument();
  });

  it("ne montre pas de bouton page suivante s'il y a moins de 20 matchs", () => {
    render(<HistoryView matches={[buildMatch()]} />);
    expect(screen.queryByRole("button", { name: /page suivante/i })).not.toBeInTheDocument();
  });
});
```

- [ ] **Step 4: Vérifier que les tests échouent**

Run: `cd /root/SecondServe/web && yarn test components/history/HistoryView.test.tsx`
Expected: FAIL — le fichier `HistoryView.tsx` n'existe pas.

- [ ] **Step 5: Créer `HistoryView.tsx`**

Créer `web/components/history/HistoryView.tsx` :

```tsx
"use client";

import { useState } from "react";
import type { SessionDto } from "@/lib/types";
import { surfaceLabel } from "@/lib/surfaces";
import styles from "./HistoryView.module.css";

type Props = { matches: SessionDto[] };

const PAGE_SIZE = 20;

function formatDate(timestampMs: number): string {
  return new Date(timestampMs).toLocaleDateString("fr-FR", { day: "numeric", month: "short", year: "numeric" });
}

export function HistoryView({ matches }: Props) {
  const [page, setPage] = useState(0);
  const totalPages = Math.ceil(matches.length / PAGE_SIZE);
  const pageMatches = matches.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  return (
    <div className={styles.card}>
      <div className={styles.header}>Historique des matchs</div>
      {matches.length === 0 ? (
        <div className={styles.empty}>Pas encore de match</div>
      ) : (
        <>
          <div className={styles.list}>
            {pageMatches.map((match) => (
              <div key={match.id} className={styles.row}>
                <span className={styles.dateCol}>{formatDate(match.createdAt)}</span>
                <span className={styles.opponentCol}>{match.opponent ?? "Adversaire"}</span>
                <span className={styles.surfaceCol}>
                  <span className={styles.surfaceChip}>{surfaceLabel(match.surface)}</span>
                </span>
                <span className={styles.scoreCol}>{match.scoreText ?? "—"}</span>
                <span className={styles.resultCol}>
                  {match.result === "VICTORY" && <span className={`${styles.resultBadge} ${styles.resultVictory}`}>VICTOIRE</span>}
                  {match.result === "DEFEAT" && <span className={`${styles.resultBadge} ${styles.resultDefeat}`}>DÉFAITE</span>}
                </span>
                <span className={styles.actionsCol}>
                  <button type="button" className={styles.editButton}>
                    Modifier
                  </button>
                  <button type="button" className={styles.deleteButton}>
                    Supprimer
                  </button>
                </span>
              </div>
            ))}
          </div>
          {totalPages > 1 && (
            <div className={styles.pagination}>
              <button
                type="button"
                className={styles.pageButton}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
              >
                Page précédente
              </button>
              <span className={styles.pageIndicator}>
                Page {page + 1} / {totalPages}
              </span>
              {page < totalPages - 1 && (
                <button type="button" className={styles.pageButton} onClick={() => setPage((p) => p + 1)}>
                  Page suivante
                </button>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
}
```

Créer `web/components/history/HistoryView.module.css` (repris de `RecentMatchesTable.module.css`, colonne `actionsCol` ajoutée, sans conteneur à hauteur fixe puisque la pagination remplace le scroll interne) :

```css
.card {
  background: var(--ss-surface);
  border: 1px solid var(--ss-border);
  border-radius: 14px;
  overflow: hidden;
}

.header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--ss-border);
  font-size: 14px;
  font-weight: 600;
  color: var(--ss-text);
}

.list {
  display: flex;
  flex-direction: column;
}

.row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 20px;
  border-top: 1px solid var(--ss-bg);
  font-size: 14px;
}

.dateCol {
  width: 100px;
  color: var(--ss-muted);
}

.opponentCol {
  flex: 1;
  font-weight: 500;
  color: var(--ss-text);
}

.surfaceCol {
  width: 110px;
}

.surfaceChip {
  font-size: 12px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 100px;
}

.scoreCol {
  width: 120px;
  font-family: var(--font-barlow), sans-serif;
  font-weight: 800;
  font-size: 15px;
  color: var(--ss-text);
}

.resultCol {
  width: 80px;
}

.resultBadge {
  font-family: var(--font-barlow), sans-serif;
  font-weight: 800;
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 7px;
}

.resultVictory {
  background: rgba(31, 111, 229, 0.1);
  color: var(--ss-data);
}

.resultDefeat {
  background: rgba(230, 57, 88, 0.1);
  color: var(--ss-hot);
}

.actionsCol {
  display: flex;
  gap: 8px;
  width: 180px;
  justify-content: flex-end;
}

.editButton,
.deleteButton {
  min-height: 36px;
  padding: 0 12px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.editButton {
  background: transparent;
  border: 1px solid var(--ss-border);
  color: var(--ss-text);
}

.deleteButton {
  background: transparent;
  border: 1px solid var(--ss-hot);
  color: var(--ss-hot);
}

.empty {
  padding: 32px 20px;
  text-align: center;
  color: var(--ss-muted);
  font-size: 14px;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 16px 20px;
  border-top: 1px solid var(--ss-border);
}

.pageButton {
  min-height: 40px;
  padding: 0 16px;
  border-radius: 8px;
  border: 1px solid var(--ss-border);
  background: transparent;
  color: var(--ss-text);
  font-weight: 600;
  font-size: 13px;
  cursor: pointer;
}

.pageButton:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.pageIndicator {
  font-size: 13px;
  color: var(--ss-muted);
}

@media (max-width: 899px) {
  .row {
    display: grid;
    grid-template-columns: 1fr auto;
    grid-template-areas:
      "date result"
      "opponent opponent"
      "surface score"
      "actions actions";
    row-gap: 6px;
    padding: 12px 16px;
  }

  .dateCol {
    grid-area: date;
    width: auto;
  }

  .opponentCol {
    grid-area: opponent;
  }

  .surfaceCol {
    grid-area: surface;
    width: auto;
  }

  .scoreCol {
    grid-area: score;
    width: auto;
    text-align: right;
  }

  .resultCol {
    grid-area: result;
    width: auto;
    text-align: right;
  }

  .actionsCol {
    grid-area: actions;
    width: auto;
    justify-content: flex-start;
  }
}
```

- [ ] **Step 6: Vérifier que les tests passent**

Run: `cd /root/SecondServe/web && yarn test components/history/HistoryView.test.tsx`
Expected: PASS.

- [ ] **Step 7: Écrire le test de la page serveur (échoue)**

Créer `web/app/dashboard/history/page.test.tsx`, en reprenant exactement le pattern de `web/app/dashboard/page.test.tsx` existant (mock `next/headers` + `@/lib/api`, et usage de `isRedirectError` pour vérifier une redirection réelle plutôt que de mocker `next/navigation`) :

```tsx
import { describe, expect, it, vi, afterEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { isRedirectError } from "next/dist/client/components/redirect-error";

vi.mock("next/headers", () => ({ cookies: vi.fn() }));
vi.mock("@/lib/api", () => ({ getSessions: vi.fn(), UnauthorizedError: class UnauthorizedError extends Error {} }));

import { cookies } from "next/headers";
import { getSessions, UnauthorizedError } from "@/lib/api";
import HistoryPage from "./page";
import type { SessionDto } from "@/lib/types";

afterEach(() => {
  vi.clearAllMocks();
});

function buildSession(overrides: Partial<SessionDto> = {}): SessionDto {
  return {
    id: 1,
    surface: "CLAY",
    matchFormat: "BEST_OF_3",
    thirdSetRule: "FULL_ADVANTAGE",
    opponent: "Novak",
    competitionType: null,
    tournament: null,
    status: "COMPLETED",
    sessionType: "MATCH",
    result: "VICTORY",
    scoreText: "6-4 · 6-3",
    scoreSeedJson: null,
    createdAt: Date.UTC(2026, 0, 15),
    updatedAt: Date.UTC(2026, 0, 15),
    ...overrides,
  };
}

describe("HistoryPage", () => {
  it("redirige vers /login si aucun cookie de session", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => undefined } as never);

    try {
      await HistoryPage();
      expect.unreachable("HistoryPage aurait dû rediriger");
    } catch (error) {
      expect(isRedirectError(error)).toBe(true);
      expect((error as { digest: string }).digest).toContain("/login");
    }
  });

  it("redirige vers /login si getSessions lève UnauthorizedError", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(getSessions).mockRejectedValue(new UnauthorizedError());

    try {
      await HistoryPage();
      expect.unreachable("HistoryPage aurait dû rediriger");
    } catch (error) {
      expect(isRedirectError(error)).toBe(true);
      expect((error as { digest: string }).digest).toContain("/login");
    }
  });

  it("relance les erreurs qui ne sont pas des UnauthorizedError", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(getSessions).mockRejectedValue(new Error("boom"));

    await expect(HistoryPage()).rejects.toThrow("boom");
  });

  it("ne passe que les sessions de type MATCH, triées par date décroissante", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(getSessions).mockResolvedValue([
      buildSession({ id: 1, opponent: "Ancien", createdAt: 1_000 }),
      buildSession({ id: 2, opponent: "Entraînement", sessionType: "TRAINING", createdAt: 2_000 }),
      buildSession({ id: 3, opponent: "Récent", createdAt: 3_000 }),
    ]);

    const element = await HistoryPage();
    render(element);

    expect(screen.getByText("Récent")).toBeInTheDocument();
    expect(screen.getByText("Ancien")).toBeInTheDocument();
    expect(screen.queryByText("Entraînement")).not.toBeInTheDocument();
  });
});
```

- [ ] **Step 8: Vérifier que le test échoue**

Run: `cd /root/SecondServe/web && yarn test app/dashboard/history/page.test.tsx`
Expected: FAIL — le fichier `page.tsx` n'existe pas.

- [ ] **Step 9: Créer `app/dashboard/history/page.tsx`**

Créer `web/app/dashboard/history/page.tsx` (même pattern que `web/app/dashboard/page.tsx`) :

```tsx
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { SESSION_COOKIE } from "@/lib/auth";
import { getSessions, UnauthorizedError } from "@/lib/api";
import { HistoryView } from "@/components/history/HistoryView";

export default async function HistoryPage() {
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

  const matches = sessions
    .filter((s) => s.sessionType === "MATCH")
    .sort((a, b) => b.createdAt - a.createdAt);

  return <HistoryView matches={matches} />;
}
```

- [ ] **Step 10: Vérifier que les tests passent**

Run: `cd /root/SecondServe/web && yarn test app/dashboard/history/page.test.tsx`
Expected: PASS.

- [ ] **Step 11: Commit**

```bash
git add web/components/history/HistoryView.tsx web/components/history/HistoryView.module.css web/components/history/HistoryView.test.tsx web/app/dashboard/history/page.tsx web/app/dashboard/history/page.test.tsx web/components/dashboard/navItems.ts web/components/dashboard/navItems.test.ts
git commit -m "feat(web): page Historique des matchs avec pagination et navigation associée"
```

---

## Task 13: Frontend — édition d'un match dans l'Historique (`MatchEditForm`)

**Files:**
- Create: `web/components/history/MatchEditForm.tsx`
- Create: `web/components/history/MatchEditForm.module.css`
- Create: `web/components/history/MatchEditForm.test.tsx`
- Modify: `web/components/history/HistoryView.tsx`
- Modify: `web/components/history/HistoryView.test.tsx`

**Interfaces:**
- Consumes: `SetScoreInputs`, `SetScoreEntry`, `parseSetEntries` (Task 6) ; `computeSetsOutcome` (Task 5) ; route proxy `PATCH /api/console/sessions/{id}` (Task 4) ; `SessionDto` (`@/lib/types`).
- Produces: `export function MatchEditForm(props: { match: SessionDto; onCancel: () => void; onSaved: () => void }): JSX.Element`.

- [ ] **Step 1: Écrire les tests de `MatchEditForm` (échouent)**

Créer `web/components/history/MatchEditForm.test.tsx` :

```tsx
import { describe, expect, it, vi, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MatchEditForm } from "./MatchEditForm";
import type { SessionDto } from "@/lib/types";

afterEach(() => {
  vi.unstubAllGlobals();
});

function buildMatch(overrides: Partial<SessionDto> = {}): SessionDto {
  return {
    id: 5,
    surface: "CLAY",
    matchFormat: "BEST_OF_3",
    thirdSetRule: "FULL_ADVANTAGE",
    opponent: "Rafael",
    competitionType: null,
    tournament: null,
    status: "COMPLETED",
    sessionType: "MATCH",
    result: "DEFEAT",
    scoreText: "4-6 · 3-6",
    scoreSeedJson: null,
    createdAt: Date.UTC(2026, 0, 15),
    updatedAt: Date.UTC(2026, 0, 15),
    ...overrides,
  };
}

describe("MatchEditForm", () => {
  it("pré-remplit l'adversaire et la surface depuis le match fourni", () => {
    render(<MatchEditForm match={buildMatch()} onCancel={vi.fn()} onSaved={vi.fn()} />);
    expect(screen.getByLabelText("Adversaire")).toHaveValue("Rafael");
    expect(screen.getByLabelText("Surface")).toHaveValue("CLAY");
  });

  it("pré-remplit les sets depuis scoreText", () => {
    render(<MatchEditForm match={buildMatch()} onCancel={vi.fn()} onSaved={vi.fn()} />);
    expect(screen.getByLabelText("Set 1 - jeux moi")).toHaveValue(4);
    expect(screen.getByLabelText("Set 1 - jeux adversaire")).toHaveValue(6);
    expect(screen.getByLabelText("Set 2 - jeux moi")).toHaveValue(3);
    expect(screen.getByLabelText("Set 2 - jeux adversaire")).toHaveValue(6);
  });

  it("appelle onCancel au clic sur Annuler", () => {
    const onCancel = vi.fn();
    render(<MatchEditForm match={buildMatch()} onCancel={onCancel} onSaved={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: /annuler/i }));
    expect(onCancel).toHaveBeenCalled();
  });

  it("soumet le PATCH avec les champs modifiés et le score recalculé, puis appelle onSaved", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ id: 5 }) });
    vi.stubGlobal("fetch", fetchMock);
    const onSaved = vi.fn();

    render(<MatchEditForm match={buildMatch()} onCancel={vi.fn()} onSaved={onSaved} />);
    fireEvent.change(screen.getByLabelText("Adversaire"), { target: { value: "Novak" } });
    fireEvent.change(screen.getByLabelText("Set 1 - jeux moi"), { target: { value: "6" } });
    fireEvent.change(screen.getByLabelText("Set 1 - jeux adversaire"), { target: { value: "4" } });

    fireEvent.click(screen.getByRole("button", { name: /enregistrer/i }));
    await waitFor(() => expect(onSaved).toHaveBeenCalled());

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/console/sessions/5");
    expect(init.method).toBe("PATCH");
    const body = JSON.parse(init.body as string);
    expect(body.opponent).toBe("Novak");
    expect(body.result).toBe("VICTORY");
    expect(body.scoreText).toBe("6-4 · 3-6");
  });

  it("affiche une erreur si le PATCH échoue", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false }));
    render(<MatchEditForm match={buildMatch()} onCancel={vi.fn()} onSaved={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: /enregistrer/i }));
    await waitFor(() => expect(screen.getByText(/échec de la mise à jour/i)).toBeInTheDocument());
  });
});
```

- [ ] **Step 2: Vérifier que les tests échouent**

Run: `cd /root/SecondServe/web && yarn test components/history/MatchEditForm.test.tsx`
Expected: FAIL — le fichier `MatchEditForm.tsx` n'existe pas.

- [ ] **Step 3: Créer `MatchEditForm.tsx`**

Créer `web/components/history/MatchEditForm.tsx` :

```tsx
"use client";

import { useState, type FormEvent } from "react";
import type { SessionDto, SetResult } from "@/lib/types";
import { SetScoreInputs, parseSetEntries } from "@/components/console/SetScoreInputs";
import type { SetScoreEntry } from "@/components/console/SetScoreInputs";
import { computeSetsOutcome } from "@/lib/scoreEngine";
import styles from "./MatchEditForm.module.css";

type Props = {
  match: SessionDto;
  onCancel: () => void;
  onSaved: () => void;
};

function parseScoreTextToSets(scoreText: string | null): SetScoreEntry[] {
  if (!scoreText) return [{ self: "", opponent: "" }, { self: "", opponent: "" }];
  const parts = scoreText.split(" · ").map((part) => {
    const [self, opponent] = part.split("-");
    return { self: self ?? "", opponent: opponent ?? "" };
  });
  return parts.length > 0 ? parts : [{ self: "", opponent: "" }, { self: "", opponent: "" }];
}

export function MatchEditForm({ match, onCancel, onSaved }: Props) {
  const [surface, setSurface] = useState(match.surface);
  const [matchFormat, setMatchFormat] = useState(match.matchFormat);
  const [opponent, setOpponent] = useState(match.opponent ?? "");
  const [sets, setSets] = useState<SetScoreEntry[]>(parseScoreTextToSets(match.scoreText));
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const maxSets = matchFormat === "BEST_OF_3" ? 3 : 1;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      const parsedSets: SetResult[] = parseSetEntries(sets).slice(0, maxSets);
      const { result, scoreText } = computeSetsOutcome(parsedSets);
      const response = await fetch(`/api/console/sessions/${match.id}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          surface,
          matchFormat,
          opponent: opponent || null,
          result,
          scoreText,
        }),
      });
      if (!response.ok) throw new Error("update_failed");
      onSaved();
    } catch {
      setError("Échec de la mise à jour du match, réessayez.");
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
        Adversaire
        <input type="text" value={opponent} onChange={(e) => setOpponent(e.target.value)} />
      </label>

      <div className={styles.field}>
        Score final
        <SetScoreInputs sets={sets} onChange={setSets} maxSets={maxSets} />
      </div>

      {error && <p className={styles.error}>{error}</p>}

      <div className={styles.actions}>
        <button type="button" className={styles.cancelButton} onClick={onCancel} disabled={submitting}>
          Annuler
        </button>
        <button type="submit" className={styles.submitButton} disabled={submitting}>
          {submitting ? "Enregistrement..." : "Enregistrer"}
        </button>
      </div>
    </form>
  );
}
```

Créer `web/components/history/MatchEditForm.module.css` (repris de `NewMatchForm.module.css`) :

```css
.form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: var(--ss-surface-elevated);
  border: 1px solid var(--ss-border);
  border-radius: 12px;
  padding: 20px;
  margin-top: 8px;
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
.field input[type="text"] {
  min-height: 44px;
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
  min-height: 44px;
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

- [ ] **Step 4: Vérifier que les tests passent**

Run: `cd /root/SecondServe/web && yarn test components/history/MatchEditForm.test.tsx`
Expected: PASS.

- [ ] **Step 5: Câbler `MatchEditForm` dans `HistoryView`**

Remplacer tout le contenu de `web/components/history/HistoryView.tsx` par :

```tsx
"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import type { SessionDto } from "@/lib/types";
import { surfaceLabel } from "@/lib/surfaces";
import { MatchEditForm } from "./MatchEditForm";
import styles from "./HistoryView.module.css";

type Props = { matches: SessionDto[] };

const PAGE_SIZE = 20;

function formatDate(timestampMs: number): string {
  return new Date(timestampMs).toLocaleDateString("fr-FR", { day: "numeric", month: "short", year: "numeric" });
}

export function HistoryView({ matches }: Props) {
  const router = useRouter();
  const [page, setPage] = useState(0);
  const [editingId, setEditingId] = useState<number | null>(null);
  const totalPages = Math.ceil(matches.length / PAGE_SIZE);
  const pageMatches = matches.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  return (
    <div className={styles.card}>
      <div className={styles.header}>Historique des matchs</div>
      {matches.length === 0 ? (
        <div className={styles.empty}>Pas encore de match</div>
      ) : (
        <>
          <div className={styles.list}>
            {pageMatches.map((match) => (
              <div key={match.id}>
                <div className={styles.row}>
                  <span className={styles.dateCol}>{formatDate(match.createdAt)}</span>
                  <span className={styles.opponentCol}>{match.opponent ?? "Adversaire"}</span>
                  <span className={styles.surfaceCol}>
                    <span className={styles.surfaceChip}>{surfaceLabel(match.surface)}</span>
                  </span>
                  <span className={styles.scoreCol}>{match.scoreText ?? "—"}</span>
                  <span className={styles.resultCol}>
                    {match.result === "VICTORY" && <span className={`${styles.resultBadge} ${styles.resultVictory}`}>VICTOIRE</span>}
                    {match.result === "DEFEAT" && <span className={`${styles.resultBadge} ${styles.resultDefeat}`}>DÉFAITE</span>}
                  </span>
                  <span className={styles.actionsCol}>
                    <button
                      type="button"
                      className={styles.editButton}
                      onClick={() => setEditingId(editingId === match.id ? null : match.id)}
                    >
                      Modifier
                    </button>
                    <button type="button" className={styles.deleteButton}>
                      Supprimer
                    </button>
                  </span>
                </div>
                {editingId === match.id && (
                  <MatchEditForm
                    match={match}
                    onCancel={() => setEditingId(null)}
                    onSaved={() => {
                      setEditingId(null);
                      router.refresh();
                    }}
                  />
                )}
              </div>
            ))}
          </div>
          {totalPages > 1 && (
            <div className={styles.pagination}>
              <button
                type="button"
                className={styles.pageButton}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
              >
                Page précédente
              </button>
              <span className={styles.pageIndicator}>
                Page {page + 1} / {totalPages}
              </span>
              {page < totalPages - 1 && (
                <button type="button" className={styles.pageButton} onClick={() => setPage((p) => p + 1)}>
                  Page suivante
                </button>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
}
```

- [ ] **Step 6: Mettre à jour `HistoryView.test.tsx` pour le mock de `next/navigation`**

En tête de `web/components/history/HistoryView.test.tsx`, ajouter avant les imports du composant :

```tsx
import { vi } from "vitest";
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh: vi.fn() }) }));
```

Ajouter un test vérifiant l'ouverture du formulaire d'édition :

```tsx

it("ouvre le formulaire d'édition au clic sur Modifier", () => {
  render(<HistoryView matches={[buildMatch()]} />);
  fireEvent.click(screen.getByRole("button", { name: /modifier/i }));
  expect(screen.getByLabelText("Adversaire")).toHaveValue("Rafael");
});
```

(Importer `fireEvent` depuis `@testing-library/react` si ce n'est pas déjà fait.)

- [ ] **Step 7: Vérifier que tous les tests d'Historique passent**

Run: `cd /root/SecondServe/web && yarn test components/history`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add web/components/history/MatchEditForm.tsx web/components/history/MatchEditForm.module.css web/components/history/MatchEditForm.test.tsx web/components/history/HistoryView.tsx web/components/history/HistoryView.test.tsx
git commit -m "feat(web): édition d'un match depuis l'Historique via MatchEditForm"
```

---

## Task 14: Frontend — suppression d'un match depuis l'Historique

**Files:**
- Create: `web/components/history/DeleteMatchButton.tsx`
- Create: `web/components/history/DeleteMatchButton.module.css`
- Create: `web/components/history/DeleteMatchButton.test.tsx`
- Modify: `web/components/history/HistoryView.tsx`
- Modify: `web/components/history/HistoryView.test.tsx`

**Interfaces:**
- Consumes: route proxy `DELETE /api/console/sessions/{id}` (Task 4).
- Produces: `export function DeleteMatchButton(props: { sessionId: number; onDeleted: () => void }): JSX.Element` (confirmation inline en deux étapes, pas de modal — cohérent avec le pattern déjà utilisé par `ConsoleSelectionView` pour basculer entre affichage et formulaire).

- [ ] **Step 1: Écrire les tests de `DeleteMatchButton` (échouent)**

Créer `web/components/history/DeleteMatchButton.test.tsx` :

```tsx
import { describe, expect, it, vi, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { DeleteMatchButton } from "./DeleteMatchButton";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("DeleteMatchButton", () => {
  it("affiche d'abord juste le bouton Supprimer, sans confirmation", () => {
    render(<DeleteMatchButton sessionId={5} onDeleted={vi.fn()} />);
    expect(screen.getByRole("button", { name: "Supprimer" })).toBeInTheDocument();
    expect(screen.queryByText(/confirmer la suppression/i)).not.toBeInTheDocument();
  });

  it("affiche la confirmation au premier clic, sans appeler l'API", () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    render(<DeleteMatchButton sessionId={5} onDeleted={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: "Supprimer" }));
    expect(screen.getByText(/confirmer la suppression/i)).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("annule la confirmation au clic sur Annuler", () => {
    render(<DeleteMatchButton sessionId={5} onDeleted={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: "Supprimer" }));
    fireEvent.click(screen.getByRole("button", { name: /annuler/i }));
    expect(screen.queryByText(/confirmer la suppression/i)).not.toBeInTheDocument();
  });

  it("appelle DELETE et onDeleted au clic sur Confirmer", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true });
    vi.stubGlobal("fetch", fetchMock);
    const onDeleted = vi.fn();
    render(<DeleteMatchButton sessionId={5} onDeleted={onDeleted} />);

    fireEvent.click(screen.getByRole("button", { name: "Supprimer" }));
    fireEvent.click(screen.getByRole("button", { name: /confirmer/i }));

    await waitFor(() => expect(onDeleted).toHaveBeenCalled());
    expect(fetchMock).toHaveBeenCalledWith("/api/console/sessions/5", { method: "DELETE" });
  });

  it("affiche une erreur si la suppression échoue", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false }));
    render(<DeleteMatchButton sessionId={5} onDeleted={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: "Supprimer" }));
    fireEvent.click(screen.getByRole("button", { name: /confirmer/i }));
    await waitFor(() => expect(screen.getByText(/échec de la suppression/i)).toBeInTheDocument());
  });
});
```

- [ ] **Step 2: Vérifier que les tests échouent**

Run: `cd /root/SecondServe/web && yarn test components/history/DeleteMatchButton.test.tsx`
Expected: FAIL — le fichier `DeleteMatchButton.tsx` n'existe pas.

- [ ] **Step 3: Créer `DeleteMatchButton.tsx`**

Créer `web/components/history/DeleteMatchButton.tsx` :

```tsx
"use client";

import { useState } from "react";
import styles from "./DeleteMatchButton.module.css";

type Props = {
  sessionId: number;
  onDeleted: () => void;
};

export function DeleteMatchButton({ sessionId, onDeleted }: Props) {
  const [confirming, setConfirming] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleConfirm() {
    if (pending) return;
    setPending(true);
    setError(null);
    try {
      const response = await fetch(`/api/console/sessions/${sessionId}`, { method: "DELETE" });
      if (!response.ok) throw new Error("delete_failed");
      onDeleted();
    } catch {
      setError("Échec de la suppression, réessayez.");
      setPending(false);
    }
  }

  if (!confirming) {
    return (
      <button type="button" className={styles.deleteButton} onClick={() => setConfirming(true)}>
        Supprimer
      </button>
    );
  }

  return (
    <span className={styles.confirmGroup}>
      <span className={styles.confirmText}>Confirmer la suppression ?</span>
      <button type="button" className={styles.confirmButton} onClick={handleConfirm} disabled={pending}>
        {pending ? "Suppression..." : "Confirmer"}
      </button>
      <button type="button" className={styles.cancelButton} onClick={() => setConfirming(false)} disabled={pending}>
        Annuler
      </button>
      {error && <span className={styles.error}>{error}</span>}
    </span>
  );
}
```

Créer `web/components/history/DeleteMatchButton.module.css` :

```css
.deleteButton {
  min-height: 36px;
  padding: 0 12px;
  border-radius: 8px;
  border: 1px solid var(--ss-hot);
  background: transparent;
  color: var(--ss-hot);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.confirmGroup {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.confirmText {
  font-size: 12px;
  color: var(--ss-hot);
  font-weight: 600;
}

.confirmButton,
.cancelButton {
  min-height: 32px;
  padding: 0 10px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.confirmButton {
  border: none;
  background: var(--ss-hot);
  color: #ffffff;
}

.cancelButton {
  border: 1px solid var(--ss-border);
  background: transparent;
  color: var(--ss-muted);
}

.error {
  width: 100%;
  font-size: 11px;
  color: var(--ss-hot);
}
```

- [ ] **Step 4: Vérifier que les tests passent**

Run: `cd /root/SecondServe/web && yarn test components/history/DeleteMatchButton.test.tsx`
Expected: PASS.

- [ ] **Step 5: Câbler `DeleteMatchButton` dans `HistoryView`**

Dans `web/components/history/HistoryView.tsx`, ajouter l'import :

```tsx
import { DeleteMatchButton } from "./DeleteMatchButton";
```

Remplacer :

```tsx
                    <button type="button" className={styles.deleteButton}>
                      Supprimer
                    </button>
```

par :

```tsx
                    <DeleteMatchButton sessionId={match.id} onDeleted={() => router.refresh()} />
```

- [ ] **Step 6: Mettre à jour les tests de `HistoryView` impactés**

Dans `web/components/history/HistoryView.test.tsx`, le test `"affiche chaque match avec ses actions Modifier/Supprimer"` reste valide (le bouton « Supprimer » est maintenant rendu par `DeleteMatchButton`, mais son libellé accessible ne change pas). Ajouter un test d'intégration bout-en-bout de la suppression :

```tsx

it("supprime le match et rafraîchit la liste au clic sur Confirmer", async () => {
  const fetchMock = vi.fn().mockResolvedValue({ ok: true });
  vi.stubGlobal("fetch", fetchMock);
  render(<HistoryView matches={[buildMatch()]} />);

  fireEvent.click(screen.getByRole("button", { name: "Supprimer" }));
  fireEvent.click(screen.getByRole("button", { name: /confirmer/i }));

  await waitFor(() => expect(fetchMock).toHaveBeenCalledWith("/api/console/sessions/1", { method: "DELETE" }));
  vi.unstubAllGlobals();
});
```

- [ ] **Step 7: Vérifier que toute la suite Historique passe**

Run: `cd /root/SecondServe/web && yarn test components/history`
Expected: PASS.

- [ ] **Step 8: Vérifier l'ensemble de la suite frontend pour non-régression finale**

Run: `cd /root/SecondServe/web && yarn test`
Expected: PASS, tous les tests verts.

- [ ] **Step 9: Vérifier le typecheck et le lint**

Run: `cd /root/SecondServe/web && yarn typecheck && yarn lint`
Expected: aucune erreur.

- [ ] **Step 10: Commit**

```bash
git add web/components/history/DeleteMatchButton.tsx web/components/history/DeleteMatchButton.module.css web/components/history/DeleteMatchButton.test.tsx web/components/history/HistoryView.tsx web/components/history/HistoryView.test.tsx
git commit -m "feat(web): suppression d'un match depuis l'Historique avec confirmation inline"
```

---

## Vérification finale

- [ ] **Backend** : `cd /root/SecondServe/backend && uv run pytest tests/ -q` → tous les tests passent.
- [ ] **Frontend** : `cd /root/SecondServe/web && yarn test && yarn typecheck && yarn lint` → tout est vert.
- [ ] **Vérification manuelle** (pas d'automatisation de rendu visuel disponible) : ouvrir `yarn dev`, tester sur les largeurs 360px (mobile), 800px (tablette), 1280px (desktop) — dashboard, console, historique, login, page live publique.
