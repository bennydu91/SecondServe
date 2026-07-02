# Tableau de bord desktop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter au projet `web/` un tableau de bord desktop authentifié (`/dashboard`) fidèle au mockup `design/SecondServe Web.dc.html` — KPI, win rate par mois, répartition par surface, derniers matchs — avec authentification Google via proxy serveur Next.js et thème clair/sombre avec détection système.

**Architecture:** Extension du backend FastAPI existant (un endpoint `GET /sessions`, déjà préparé par le schéma `SessionsResponse` inutilisé). Côté `web/`, le JWT SecondServe est géré entièrement côté serveur Next.js (cookie httpOnly posé par une route handler qui échange le Google ID token contre le JWT) ; un middleware protège `/dashboard` ; la page est un composant serveur qui calcule les agrégats (win rate, séquence, par surface, temps de jeu) à partir de la liste complète de sessions — logique portée depuis `StatsComputer.kt` (Android) pour rester cohérente entre plateformes. Le thème clair/sombre utilise des variables CSS commutées par un attribut `data-theme`, initialisé par un script inline exécuté avant hydratation.

**Tech Stack:** FastAPI (backend, inchangé), Next.js 16 (App Router, TypeScript), CSS Modules + variables CSS, Vitest + React Testing Library, Google Identity Services (JS).

## Global Constraints

- Ce plan ne couvre **que** le tableau de bord — pas la console de saisie point par point (sous-projet séparé, cf. spec).
- Convention JSON backend : `snake_case`. Conversion vers `camelCase` uniquement à la frontière (`web/lib/api.ts`).
- **Pas de données fabriquées** (règle non négociable, `docs/design-system.md` #7) : un mois/une surface sans donnée suffisante affiche un état vide plutôt qu'un pourcentage inventé.
- Auth : le JWT ne touche jamais le JS navigateur — cookie httpOnly posé côté serveur Next.js. Aucun changement CORS backend nécessaire pour cette feature (les appels vers `/sessions` sont serveur→serveur).
- Table « Derniers matchs » : hauteur fixe (~8 lignes), scroll interne, pas de lien « Tout voir ».
- Dark mode inclus dès cette itération : détection système + switch persistant (`localStorage`), palette DARK reprise telle quelle de `design/README.md`.
- Palette et tokens : valeurs exactes extraites de `design/README.md` (sections "Couleurs — LIGHT" et "Couleurs — DARK").
- Résultats de session valides : `"VICTORY"`, `"DEFEAT"` comptent dans les stats ; `"DRAW"`, `"ABANDONED"`, `null` sont ignorés des calculs de win rate mais un match avec `status != COMPLETED` et un résultat `VICTORY`/`DEFEAT` compte quand même dans le calcul de la **séquence active** (comportement identique à `StatsComputer.kt`, à ne pas "corriger").
- Next.js 16 : `cookies()` et `params` sont asynchrones (`await cookies()`), cohérent avec le reste du projet `web/`.

---

### Task 1: Backend — exposer `GET /sessions`

**Files:**
- Modify: `backend/app/features/sessions/service.py`
- Modify: `backend/app/api/v1/sessions.py`
- Test: `backend/tests/unit/test_session_service.py`
- Test: `backend/tests/integration/test_sessions_api.py`

**Interfaces:**
- Produces: `SessionService.list_sessions() -> SessionsResponse` ; route `GET /api/v1/sessions` (protégée JWT, comme le reste de la feature).
- Consumes: `SessionRepository.get_all()` (existe déjà, trié par `created_at desc`), `SessionsResponse`/`SessionResponse` (existent déjà dans `schemas.py`).

- [ ] **Step 1: Étendre le fixture de test avec `score_text`**

`score_text` existe déjà sur `SessionModel` (colonne alimentée par la sync Android) mais n'est pas encore dans `SessionResponse` ni dans le fixture de test — nécessaire dès maintenant pour que la table « Derniers matchs » du dashboard (Task 6) affiche un vrai score plutôt qu'une donnée fabriquée.

Modifier la fonction `session_model(...)` existante dans `backend/tests/unit/test_session_service.py` — ajouter le paramètre et l'assignation :

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
    m.created_at = created_at
    m.updated_at = updated_at
    return m
```

- [ ] **Step 2: Écrire le test unitaire du service (doit échouer — méthode inexistante)**

Ajouter à `backend/tests/unit/test_session_service.py` :

```python
from app.features.sessions.schemas import SessionsResponse


@pytest.mark.asyncio
async def test_list_sessions_returns_all_from_repository():
    model_a = session_model(id=1, created_at=2_000_000, score_text="6-4 · 6-3")
    model_b = session_model(id=2, created_at=1_000_000)
    repo = MagicMock()
    repo.get_all = AsyncMock(return_value=[model_a, model_b])
    service = SessionService(repo)

    result = await service.list_sessions()

    assert isinstance(result, SessionsResponse)
    assert result.total == 2
    assert [item.id for item in result.items] == [1, 2]
    assert result.items[0].score_text == "6-4 · 6-3"


@pytest.mark.asyncio
async def test_list_sessions_empty():
    repo = MagicMock()
    repo.get_all = AsyncMock(return_value=[])
    service = SessionService(repo)

    result = await service.list_sessions()

    assert result.total == 0
    assert result.items == []
```

Run: `cd backend && uv run pytest tests/unit/test_session_service.py -v`
Expected: FAIL — `AttributeError: 'SessionService' object has no attribute 'list_sessions'`.

- [ ] **Step 3: Ajouter `score_text` au schéma `SessionResponse`**

Modifier `backend/app/features/sessions/schemas.py` :

```python
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
    created_at: int
    updated_at: int

    model_config = {"from_attributes": True}
```

- [ ] **Step 4: Implémenter `SessionService.list_sessions`**

Modifier `backend/app/features/sessions/service.py` :

```python
from app.features.sessions.repository import SessionRepository
from app.features.sessions.schemas import SessionCreateRequest, SessionResponse, SessionsResponse
from app.features.monitoring.events import emit_event


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
```

- [ ] **Step 5: Lancer le test unitaire et vérifier qu'il passe**

Run: `cd backend && uv run pytest tests/unit/test_session_service.py -v`
Expected: tous les tests PASS (existants + les 2 nouveaux).

- [ ] **Step 6: Ajouter la route et écrire les tests d'intégration (doivent échouer — route inexistante)**

Ajouter à `backend/tests/integration/test_sessions_api.py` :

```python
@pytest.mark.asyncio
async def test_list_sessions_requires_jwt(client):
    response = await client.get("/api/v1/sessions")
    assert response.status_code in (401, 403)


@pytest.mark.asyncio
async def test_list_sessions_empty(client):
    token = make_token()
    response = await client.get("/api/v1/sessions", headers=auth(token))
    assert response.status_code == 200
    assert response.json() == {"items": [], "total": 0}


@pytest.mark.asyncio
async def test_list_sessions_sorted_by_created_at_desc(client):
    token = make_token()
    await client.post(
        "/api/v1/sessions",
        json={"surface": "CLAY", "match_format": "BEST_OF_3", "third_set_rule": "FULL_ADVANTAGE", "created_at": 1_000_000},
        headers=auth(token)
    )
    await client.post(
        "/api/v1/sessions",
        json={"surface": "HARD", "match_format": "BEST_OF_1", "third_set_rule": "FULL_ADVANTAGE", "created_at": 2_000_000},
        headers=auth(token)
    )
    response = await client.get("/api/v1/sessions", headers=auth(token))
    assert response.status_code == 200
    data = response.json()
    assert data["total"] == 2
    assert data["items"][0]["surface"] == "HARD"
    assert data["items"][1]["surface"] == "CLAY"


@pytest.mark.asyncio
async def test_list_sessions_includes_score_text_field(client):
    token = make_token()
    await client.post(
        "/api/v1/sessions",
        json={"surface": "CLAY", "match_format": "BEST_OF_3", "third_set_rule": "FULL_ADVANTAGE", "created_at": 1_000_000},
        headers=auth(token)
    )
    response = await client.get("/api/v1/sessions", headers=auth(token))
    assert "score_text" in response.json()["items"][0]
    assert response.json()["items"][0]["score_text"] is None
```

Run: `cd backend && uv run pytest tests/integration/test_sessions_api.py -v`
Expected: FAIL — `404 Not Found` sur les 4 nouveaux tests (route inexistante).

- [ ] **Step 7: Implémenter la route**

Modifier `backend/app/api/v1/sessions.py` :

```python
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from app.core.database import get_db
from app.features.sessions.repository import SessionRepository
from app.features.sessions.schemas import SessionCreateRequest, SessionResponse, SessionsResponse
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
```

- [ ] **Step 8: Lancer les tests d'intégration et vérifier qu'ils passent**

Run: `cd backend && uv run pytest tests/integration/test_sessions_api.py -v`
Expected: tous les tests PASS (existants + les 4 nouveaux).

- [ ] **Step 9: Lancer toute la suite backend pour vérifier l'absence de régression**

Run: `cd backend && uv run pytest -v`
Expected: tous les tests PASS.

- [ ] **Step 10: Commit**

```bash
git add backend/app/features/sessions/service.py backend/app/api/v1/sessions.py backend/app/features/sessions/schemas.py backend/tests/unit/test_session_service.py backend/tests/integration/test_sessions_api.py
git commit -m "feat(backend): exposer GET /sessions (avec score_text) pour le tableau de bord desktop"
```

---

### Task 2: Web — types et client API pour les sessions

**Files:**
- Modify: `web/lib/types.ts`
- Modify: `web/lib/api.ts`
- Modify: `web/lib/api.test.ts`

**Interfaces:**
- Produces: type `SessionDto` (camelCase) ; `getSessions(token: string): Promise<SessionDto[]>` (lève `UnauthorizedError` sur 401) ; classe `UnauthorizedError`.
- Consumes: réponse JSON `GET /api/v1/sessions` (Task 1) : `{ items: RawSession[], total: number }`.

- [ ] **Step 1: Ajouter le type `SessionDto`**

Ajouter à `web/lib/types.ts` :

```typescript
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
  createdAt: number;
  updatedAt: number;
};
```

- [ ] **Step 2: Écrire les tests du client API (doivent échouer — fonctions inexistantes)**

Ajouter à `web/lib/api.test.ts` :

```typescript
import { getSessions, UnauthorizedError } from "./api";

const rawSession = {
  id: 1,
  surface: "CLAY",
  match_format: "BEST_OF_3",
  third_set_rule: "FULL_ADVANTAGE",
  opponent: "Marceau",
  competition_type: "CLUB",
  tournament: "Tournoi du club",
  status: "COMPLETED",
  session_type: "MATCH",
  result: "VICTORY",
  score_text: "6-4 · 6-3",
  created_at: 1000,
  updated_at: 2000,
};

describe("getSessions", () => {
  it("mappe la liste snake_case du backend vers des SessionDto camelCase", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({ items: [rawSession], total: 1 }),
      })
    );
    const sessions = await getSessions("jwt-token");
    expect(sessions).toEqual([
      {
        id: 1,
        surface: "CLAY",
        matchFormat: "BEST_OF_3",
        thirdSetRule: "FULL_ADVANTAGE",
        opponent: "Marceau",
        competitionType: "CLUB",
        tournament: "Tournoi du club",
        status: "COMPLETED",
        sessionType: "MATCH",
        result: "VICTORY",
        scoreText: "6-4 · 6-3",
        createdAt: 1000,
        updatedAt: 2000,
      },
    ]);
  });

  it("envoie le JWT en Authorization Bearer", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => ({ items: [], total: 0 }) });
    vi.stubGlobal("fetch", fetchMock);
    await getSessions("jwt-token");
    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers.Authorization).toBe("Bearer jwt-token");
  });

  it("lève UnauthorizedError sur 401", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 401, json: async () => ({}) }));
    await expect(getSessions("expired-token")).rejects.toThrow(UnauthorizedError);
  });
});
```

- [ ] **Step 3: Lancer les tests (doivent échouer)**

Run: `cd web && npm run test -- lib/api.test.ts`
Expected: FAIL — `getSessions`/`UnauthorizedError` non exportés.

- [ ] **Step 4: Implémenter `getSessions`**

Ajouter à `web/lib/api.ts` :

```typescript
import type { LiveSnapshot, SessionDto, SetResult } from "./types";

export class UnauthorizedError extends Error {}

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
```

(L'import existant `import type { LiveSnapshot, SetResult } from "./types";` en haut du fichier devient la ligne ci-dessus, augmentée de `SessionDto`.)

- [ ] **Step 5: Lancer les tests et vérifier qu'ils passent**

Run: `cd web && npm run test -- lib/api.test.ts`
Expected: tous les tests PASS (existants live-share + les 3 nouveaux).

- [ ] **Step 6: Vérifier le build**

Run: `cd web && npm run build`
Expected: build réussi sans erreur TypeScript.

- [ ] **Step 7: Commit**

```bash
git add web/lib/types.ts web/lib/api.ts web/lib/api.test.ts
git commit -m "feat(web): client API GET /sessions pour le tableau de bord"
```

---

### Task 3: Web — module de calcul des statistiques (port de `StatsComputer.kt`)

**Files:**
- Create: `web/lib/stats.ts`
- Test: `web/lib/stats.test.ts`

**Interfaces:**
- Produces: `computeStats(sessions: SessionDto[]): AggregatedStats`, `computeMonthlyWinRate(sessions: SessionDto[], now?: Date): MonthlyWinRate[]`, `computeWinRateTrend(monthly: MonthlyWinRate[]): number | null`, `computePlayTime(sessions: SessionDto[]): PlayTime`. Types `AggregatedStats`, `SurfaceWinRate`, `ActiveStreak`, `MonthlyWinRate`, `PlayTime`.
- Consumes: `SessionDto` (Task 2).

- [ ] **Step 1: Écrire les tests portés depuis `StatsComputerTest.kt` (doivent échouer — module inexistant)**

`web/lib/stats.test.ts` :

```typescript
import { describe, expect, it } from "vitest";
import {
  computeStats,
  computeMonthlyWinRate,
  computeWinRateTrend,
  computePlayTime,
} from "./stats";
import type { SessionDto } from "./types";

function fakeSession(overrides: Partial<SessionDto> & { id: number }): SessionDto {
  return {
    surface: "CLAY",
    matchFormat: "BEST_OF_3",
    thirdSetRule: "FULL_ADVANTAGE",
    opponent: null,
    competitionType: null,
    tournament: null,
    status: "COMPLETED",
    sessionType: "MATCH",
    result: "VICTORY",
    createdAt: Date.now(),
    updatedAt: Date.now(),
    ...overrides,
  };
}

describe("computeStats", () => {
  it("aucune session : compteurs à zéro, win rate et séquence nuls", () => {
    const stats = computeStats([]);
    expect(stats.totalMatchSessions).toBe(0);
    expect(stats.totalTrainingSessions).toBe(0);
    expect(stats.completedMatchSessions).toBe(0);
    expect(stats.winRateGlobal).toBeNull();
    expect(stats.activeStreak).toBeNull();
    expect(stats.winRateBySurface).toEqual([]);
  });

  it("3 victoires sur terre battue : win rate 100%, surface affichée, séquence de 3 victoires", () => {
    const sessions = [
      fakeSession({ id: 1, result: "VICTORY", surface: "CLAY", createdAt: 3000 }),
      fakeSession({ id: 2, result: "VICTORY", surface: "CLAY", createdAt: 2000 }),
      fakeSession({ id: 3, result: "VICTORY", surface: "CLAY", createdAt: 1000 }),
    ];
    const stats = computeStats(sessions);
    expect(stats.winRateGlobal).toBe(1);
    expect(stats.winRateBySurface).toHaveLength(1);
    expect(stats.winRateBySurface[0].winRatePercent).toBe(1);
    expect(stats.activeStreak).toEqual({ result: "VICTORY", count: 3 });
  });

  it("2 matchs sur une surface : win rate par surface nul (données insuffisantes, seuil 3)", () => {
    const sessions = [
      fakeSession({ id: 1, result: "VICTORY", surface: "HARD" }),
      fakeSession({ id: 2, result: "DEFEAT", surface: "HARD" }),
    ];
    const stats = computeStats(sessions);
    expect(stats.winRateBySurface).toHaveLength(1);
    expect(stats.winRateBySurface[0].winRatePercent).toBeNull();
  });

  it("la séquence se rompt : 1 défaite après 2 victoires -> séquence Defeats(1)", () => {
    const sessions = [
      fakeSession({ id: 1, result: "DEFEAT", createdAt: 3000 }),
      fakeSession({ id: 2, result: "VICTORY", createdAt: 2000 }),
      fakeSession({ id: 3, result: "VICTORY", createdAt: 1000 }),
    ];
    const stats = computeStats(sessions);
    expect(stats.activeStreak).toEqual({ result: "DEFEAT", count: 1 });
  });

  it("les entraînements comptent dans totalTrainingSessions mais pas dans le win rate", () => {
    const sessions = [
      fakeSession({ id: 1, sessionType: "TRAINING", result: null }),
      fakeSession({ id: 2, result: "VICTORY" }),
    ];
    const stats = computeStats(sessions);
    expect(stats.totalTrainingSessions).toBe(1);
    expect(stats.totalMatchSessions).toBe(1);
    expect(stats.completedMatchSessions).toBe(1);
  });

  it("DRAW et ABANDONED ne comptent pas dans le win rate", () => {
    const sessions = [
      fakeSession({ id: 1, result: "DRAW" }),
      fakeSession({ id: 2, result: "ABANDONED" }),
      fakeSession({ id: 3, result: "VICTORY" }),
    ];
    const stats = computeStats(sessions);
    expect(stats.completedMatchSessions).toBe(1);
    expect(stats.winRateGlobal).toBe(1);
  });

  it("win rate 50% avec 2 victoires et 2 défaites sur la même surface", () => {
    const sessions = [
      fakeSession({ id: 1, result: "VICTORY", surface: "HARD", createdAt: 4000 }),
      fakeSession({ id: 2, result: "DEFEAT", surface: "HARD", createdAt: 3000 }),
      fakeSession({ id: 3, result: "VICTORY", surface: "HARD", createdAt: 2000 }),
      fakeSession({ id: 4, result: "DEFEAT", surface: "HARD", createdAt: 1000 }),
    ];
    const stats = computeStats(sessions);
    expect(stats.winRateGlobal).toBe(0.5);
    expect(stats.winRateBySurface[0].winRatePercent).toBe(0.5);
  });

  it("une session INTERRUPTED n'est pas comptée comme terminée", () => {
    const sessions = [fakeSession({ id: 1, status: "INTERRUPTED", result: "VICTORY" })];
    const stats = computeStats(sessions);
    expect(stats.completedMatchSessions).toBe(0);
    expect(stats.winRateGlobal).toBeNull();
  });

  it("les surfaces sont triées par nombre de matchs décroissant", () => {
    const sessions = [
      fakeSession({ id: 1, result: "VICTORY", surface: "CLAY", createdAt: 5000 }),
      fakeSession({ id: 2, result: "VICTORY", surface: "CLAY", createdAt: 4000 }),
      fakeSession({ id: 3, result: "VICTORY", surface: "CLAY", createdAt: 3000 }),
      fakeSession({ id: 4, result: "VICTORY", surface: "HARD", createdAt: 2000 }),
      fakeSession({ id: 5, result: "DEFEAT", surface: "HARD", createdAt: 1000 }),
    ];
    const stats = computeStats(sessions);
    expect(stats.winRateBySurface.map((s) => s.surface)).toEqual(["CLAY", "HARD"]);
  });

  it("une session INTERRUPTED avec résultat DEFEAT rompt quand même une séquence de victoires", () => {
    const sessions = [
      fakeSession({ id: 1, status: "INTERRUPTED", result: "DEFEAT", createdAt: 3000 }),
      fakeSession({ id: 2, result: "VICTORY", createdAt: 2000 }),
      fakeSession({ id: 3, result: "VICTORY", createdAt: 1000 }),
    ];
    const stats = computeStats(sessions);
    expect(stats.activeStreak).toEqual({ result: "DEFEAT", count: 1 });
    expect(stats.completedMatchSessions).toBe(2);
    expect(stats.winRateGlobal).toBe(1);
  });
});

describe("computePlayTime", () => {
  it("additionne la durée des sessions terminées (matchs + entraînements), ignore les autres", () => {
    const oneHourMs = 60 * 60 * 1000;
    const sessions = [
      fakeSession({ id: 1, status: "COMPLETED", createdAt: 0, updatedAt: oneHourMs }),
      fakeSession({ id: 2, sessionType: "TRAINING", status: "COMPLETED", createdAt: 0, updatedAt: 2 * oneHourMs }),
      fakeSession({ id: 3, status: "ACTIVE", createdAt: 0, updatedAt: oneHourMs }),
    ];
    const playTime = computePlayTime(sessions);
    expect(playTime.hours).toBe(3);
    expect(playTime.sessionCount).toBe(2);
  });

  it("aucune session terminée : zéro heure, zéro session", () => {
    const playTime = computePlayTime([]);
    expect(playTime.hours).toBe(0);
    expect(playTime.sessionCount).toBe(0);
  });
});

describe("computeMonthlyWinRate", () => {
  it("retourne 5 mois se terminant au mois courant, le dernier marqué isCurrentMonth", () => {
    const now = new Date(2026, 5, 15); // 15 juin 2026
    const months = computeMonthlyWinRate([], now);
    expect(months).toHaveLength(5);
    expect(months.map((m) => m.monthLabel)).toEqual(["Fév", "Mar", "Avr", "Mai", "Juin"]);
    expect(months[4].isCurrentMonth).toBe(true);
    expect(months[0].isCurrentMonth).toBe(false);
  });

  it("un mois sans match terminé a un winRatePercent nul (pas de donnée fabriquée)", () => {
    const now = new Date(2026, 5, 15);
    const months = computeMonthlyWinRate([], now);
    expect(months.every((m) => m.winRatePercent === null)).toBe(true);
  });

  it("calcule le win rate du mois courant à partir des matchs de ce mois", () => {
    const now = new Date(2026, 5, 15);
    const sessions = [
      fakeSession({ id: 1, result: "VICTORY", createdAt: new Date(2026, 5, 1).getTime() }),
      fakeSession({ id: 2, result: "DEFEAT", createdAt: new Date(2026, 5, 10).getTime() }),
    ];
    const months = computeMonthlyWinRate(sessions, now);
    expect(months[4].winRatePercent).toBe(0.5);
  });
});

describe("computeWinRateTrend", () => {
  it("nul si moins de 2 mois disponibles", () => {
    expect(computeWinRateTrend([{ monthLabel: "Juin", winRatePercent: 1, isCurrentMonth: true }])).toBeNull();
  });

  it("nul si le mois courant ou le précédent n'a pas de donnée", () => {
    const months = [
      { monthLabel: "Mai", winRatePercent: null, isCurrentMonth: false },
      { monthLabel: "Juin", winRatePercent: 0.8, isCurrentMonth: true },
    ];
    expect(computeWinRateTrend(months)).toBeNull();
  });

  it("calcule la différence entre le mois courant et le précédent", () => {
    const months = [
      { monthLabel: "Mai", winRatePercent: 0.5, isCurrentMonth: false },
      { monthLabel: "Juin", winRatePercent: 0.8, isCurrentMonth: true },
    ];
    expect(computeWinRateTrend(months)).toBeCloseTo(0.3);
  });
});
```

- [ ] **Step 2: Lancer les tests (doivent échouer — module inexistant)**

Run: `cd web && npm run test -- lib/stats.test.ts`
Expected: FAIL — `Cannot find module './stats'`.

- [ ] **Step 3: Implémenter `web/lib/stats.ts`**

```typescript
import type { SessionDto } from "./types";

export type SurfaceWinRate = {
  surface: string;
  matchCount: number;
  victories: number;
  winRatePercent: number | null;
};

export type ActiveStreak = { result: "VICTORY" | "DEFEAT"; count: number };

export type AggregatedStats = {
  totalMatchSessions: number;
  totalTrainingSessions: number;
  completedMatchSessions: number;
  victories: number;
  defeats: number;
  winRateGlobal: number | null;
  winRateBySurface: SurfaceWinRate[];
  activeStreak: ActiveStreak | null;
};

export type PlayTime = { hours: number; sessionCount: number };

export type MonthlyWinRate = { monthLabel: string; winRatePercent: number | null; isCurrentMonth: boolean };

const SCORED_RESULTS = new Set(["VICTORY", "DEFEAT"]);
const MIN_MATCHES_FOR_SURFACE_RATE = 3;
const MONTH_LABELS = ["Jan", "Fév", "Mar", "Avr", "Mai", "Juin", "Juil", "Août", "Sep", "Oct", "Nov", "Déc"];
const MONTHLY_CHART_SPAN = 5;

function isScored(session: SessionDto): boolean {
  return session.result !== null && SCORED_RESULTS.has(session.result);
}

export function computeStats(sessions: SessionDto[]): AggregatedStats {
  const allMatch = sessions.filter((s) => s.sessionType === "MATCH");
  const allTraining = sessions.filter((s) => s.sessionType === "TRAINING");

  const scored = allMatch.filter((s) => s.status === "COMPLETED" && isScored(s));
  const victories = scored.filter((s) => s.result === "VICTORY").length;
  const defeats = scored.filter((s) => s.result === "DEFEAT").length;
  const winRateGlobal = scored.length === 0 ? null : victories / scored.length;

  const bySurfaceMap = new Map<string, SessionDto[]>();
  for (const session of scored) {
    const key = session.surface || "INCONNUE";
    const list = bySurfaceMap.get(key) ?? [];
    list.push(session);
    bySurfaceMap.set(key, list);
  }
  const winRateBySurface: SurfaceWinRate[] = Array.from(bySurfaceMap.entries())
    .map(([surface, list]) => {
      const surfaceVictories = list.filter((s) => s.result === "VICTORY").length;
      return {
        surface,
        matchCount: list.length,
        victories: surfaceVictories,
        winRatePercent: list.length >= MIN_MATCHES_FOR_SURFACE_RATE ? surfaceVictories / list.length : null,
      };
    })
    .sort((a, b) => b.matchCount - a.matchCount);

  const allWithResult = allMatch
    .filter(isScored)
    .sort((a, b) => b.createdAt - a.createdAt || b.id - a.id);
  const activeStreak = computeStreak(allWithResult);

  return {
    totalMatchSessions: allMatch.length,
    totalTrainingSessions: allTraining.length,
    completedMatchSessions: scored.length,
    victories,
    defeats,
    winRateGlobal,
    winRateBySurface,
    activeStreak,
  };
}

function computeStreak(sortedSessions: SessionDto[]): ActiveStreak | null {
  if (sortedSessions.length === 0) return null;
  const firstResult = sortedSessions[0].result;
  if (firstResult !== "VICTORY" && firstResult !== "DEFEAT") return null;
  let count = 0;
  for (const session of sortedSessions) {
    if (session.result !== firstResult) break;
    count += 1;
  }
  return { result: firstResult, count };
}

export function computePlayTime(sessions: SessionDto[]): PlayTime {
  const completed = sessions.filter((s) => s.status === "COMPLETED");
  const totalMs = completed.reduce((sum, s) => sum + Math.max(0, s.updatedAt - s.createdAt), 0);
  return { hours: totalMs / (1000 * 60 * 60), sessionCount: completed.length };
}

export function computeMonthlyWinRate(sessions: SessionDto[], now: Date = new Date()): MonthlyWinRate[] {
  const scored = sessions.filter((s) => s.sessionType === "MATCH" && s.status === "COMPLETED" && isScored(s));
  const months: MonthlyWinRate[] = [];
  for (let offset = MONTHLY_CHART_SPAN - 1; offset >= 0; offset--) {
    const monthDate = new Date(now.getFullYear(), now.getMonth() - offset, 1);
    const matches = scored.filter((s) => {
      const d = new Date(s.createdAt);
      return d.getFullYear() === monthDate.getFullYear() && d.getMonth() === monthDate.getMonth();
    });
    const victories = matches.filter((s) => s.result === "VICTORY").length;
    months.push({
      monthLabel: MONTH_LABELS[monthDate.getMonth()],
      winRatePercent: matches.length === 0 ? null : victories / matches.length,
      isCurrentMonth: offset === 0,
    });
  }
  return months;
}

export function computeWinRateTrend(monthly: MonthlyWinRate[]): number | null {
  if (monthly.length < 2) return null;
  const current = monthly[monthly.length - 1];
  const previous = monthly[monthly.length - 2];
  if (current.winRatePercent === null || previous.winRatePercent === null) return null;
  return current.winRatePercent - previous.winRatePercent;
}
```

- [ ] **Step 4: Lancer les tests et vérifier qu'ils passent**

Run: `cd web && npm run test -- lib/stats.test.ts`
Expected: tous les tests PASS (16 tests).

- [ ] **Step 5: Vérifier le build**

Run: `cd web && npm run build`
Expected: build réussi.

- [ ] **Step 6: Commit**

```bash
git add web/lib/stats.ts web/lib/stats.test.ts
git commit -m "feat(web): porter le calcul de stats (win rate, séquence, par surface) depuis Android"
```

---

### Task 4: Web — authentification (cookie serveur, callback Google, middleware, logout)

**Files:**
- Create: `web/lib/auth.ts`
- Create: `web/app/api/auth/callback/route.ts`
- Test: `web/app/api/auth/callback/route.test.ts`
- Create: `web/middleware.ts`
- Test: `web/middleware.test.ts`
- Create: `web/app/logout/route.ts`
- Create: `web/app/login/page.tsx`
- Create: `web/app/login/page.module.css`

**Interfaces:**
- Produces: `SESSION_COOKIE`, `SESSION_MAX_AGE_SECONDS` (constantes partagées) ; route `POST /api/auth/callback` (pose le cookie) ; `middleware` protégeant `/dashboard/*` ; route `POST /logout` (supprime le cookie) ; page `/login`.
- Consumes: `POST {API_BASE_URL}/api/v1/auth/init` (backend existant, prend `{ google_id_token }`, retourne `{ token }`).

- [ ] **Step 1: Déclarer les constantes partagées**

`web/lib/auth.ts` :

```typescript
export const SESSION_COOKIE = "ss_session";
export const SESSION_MAX_AGE_SECONDS = 60 * 60 * 24 * 30; // 30 jours — aligné sur JWTManager.create_token() côté backend
```

- [ ] **Step 2: Écrire le test de la route de callback (doit échouer — route inexistante)**

`web/app/api/auth/callback/route.test.ts` :

```typescript
// @vitest-environment node
import { describe, expect, it, vi, afterEach } from "vitest";
import { POST } from "./route";
import { SESSION_COOKIE } from "@/lib/auth";

afterEach(() => {
  vi.unstubAllGlobals();
});

function jsonRequest(body: unknown): Request {
  return new Request("http://localhost:3000/api/auth/callback", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

describe("POST /api/auth/callback", () => {
  it("retourne 400 si le credential est absent", async () => {
    const response = await POST(jsonRequest({}));
    expect(response.status).toBe(400);
  });

  it("retourne 401 sans poser de cookie si le backend rejette le token", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 403 }));
    const response = await POST(jsonRequest({ credential: "bad-token" }));
    expect(response.status).toBe(401);
    expect(response.cookies.get(SESSION_COOKIE)).toBeUndefined();
  });

  it("pose un cookie httpOnly avec le JWT retourné par le backend", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => ({ token: "jwt-abc" }) })
    );
    const response = await POST(jsonRequest({ credential: "good-token" }));
    expect(response.status).toBe(200);
    const cookie = response.cookies.get(SESSION_COOKIE);
    expect(cookie?.value).toBe("jwt-abc");
    expect(cookie?.httpOnly).toBe(true);
  });

  it("transmet le credential au backend sous google_id_token", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => ({ token: "jwt-abc" }) });
    vi.stubGlobal("fetch", fetchMock);
    await POST(jsonRequest({ credential: "good-token" }));
    const [, init] = fetchMock.mock.calls[0];
    expect(JSON.parse(init.body as string)).toEqual({ google_id_token: "good-token" });
  });
});
```

- [ ] **Step 3: Lancer le test (doit échouer — route inexistante)**

Run: `cd web && npm run test -- app/api/auth/callback/route.test.ts`
Expected: FAIL — `Cannot find module './route'`.

- [ ] **Step 4: Implémenter la route de callback**

`web/app/api/auth/callback/route.ts` :

```typescript
import { NextResponse } from "next/server";
import { SESSION_COOKIE, SESSION_MAX_AGE_SECONDS } from "@/lib/auth";

export async function POST(request: Request) {
  const body = (await request.json()) as { credential?: string };
  if (!body.credential) {
    return NextResponse.json({ error: "missing_credential" }, { status: 400 });
  }

  const backendResponse = await fetch(`${process.env.API_BASE_URL}/api/v1/auth/init`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ google_id_token: body.credential }),
  });

  if (!backendResponse.ok) {
    return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  }

  const { token } = (await backendResponse.json()) as { token: string };
  const response = NextResponse.json({ ok: true });
  response.cookies.set(SESSION_COOKIE, token, {
    httpOnly: true,
    secure: true,
    sameSite: "lax",
    maxAge: SESSION_MAX_AGE_SECONDS,
    path: "/",
  });
  return response;
}
```

- [ ] **Step 5: Lancer le test et vérifier qu'il passe**

Run: `cd web && npm run test -- app/api/auth/callback/route.test.ts`
Expected: 4 tests PASS.

- [ ] **Step 6: Écrire le test du middleware (doit échouer — middleware inexistant)**

`web/middleware.test.ts` :

```typescript
// @vitest-environment node
import { describe, expect, it } from "vitest";
import { NextRequest } from "next/server";
import { middleware } from "./middleware";
import { SESSION_COOKIE } from "@/lib/auth";

describe("middleware", () => {
  it("redirige vers /login si le cookie de session est absent", () => {
    const request = new NextRequest(new URL("http://localhost:3000/dashboard"));
    const response = middleware(request);
    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toContain("/login");
  });

  it("laisse passer si le cookie de session est présent", () => {
    const request = new NextRequest(new URL("http://localhost:3000/dashboard"), {
      headers: { cookie: `${SESSION_COOKIE}=some-jwt` },
    });
    const response = middleware(request);
    expect(response.status).toBe(200);
  });
});
```

- [ ] **Step 7: Lancer le test (doit échouer)**

Run: `cd web && npm run test -- middleware.test.ts`
Expected: FAIL — `Cannot find module './middleware'`.

- [ ] **Step 8: Implémenter le middleware**

`web/middleware.ts` :

```typescript
import { NextRequest, NextResponse } from "next/server";
import { SESSION_COOKIE } from "@/lib/auth";

export function middleware(request: NextRequest) {
  const token = request.cookies.get(SESSION_COOKIE)?.value;
  if (!token) {
    return NextResponse.redirect(new URL("/login", request.url));
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/dashboard/:path*"],
};
```

- [ ] **Step 9: Lancer le test et vérifier qu'il passe**

Run: `cd web && npm run test -- middleware.test.ts`
Expected: 2 tests PASS.

- [ ] **Step 10: Implémenter la route de logout (pas de TDD — une ligne de logique)**

`web/app/logout/route.ts` :

```typescript
import { NextResponse } from "next/server";
import { SESSION_COOKIE } from "@/lib/auth";

export async function POST(request: Request) {
  const response = NextResponse.redirect(new URL("/login", request.url));
  response.cookies.delete(SESSION_COOKIE);
  return response;
}
```

- [ ] **Step 11: Implémenter la page de login (assemblage direct — nécessite le script Google réel, pas testable unitairement)**

`web/app/login/page.module.css` :

```css
.page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f4f4f1;
}

.card {
  background: #ffffff;
  border: 1px solid #e4e5e2;
  border-radius: 22px;
  padding: 40px 48px;
  text-align: center;
  box-shadow: 0 20px 50px -30px rgba(20, 22, 26, 0.3);
}

.title {
  font-family: var(--font-barlow), sans-serif;
  font-weight: 800;
  font-size: 28px;
  margin: 0 0 4px;
}

.subtitle {
  color: #6a6f78;
  font-size: 14px;
  margin: 0 0 28px;
}
```

`web/app/login/page.tsx` :

```typescript
"use client";

import Script from "next/script";
import { useCallback, useRef } from "react";
import { useRouter } from "next/navigation";
import styles from "./page.module.css";

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (config: {
            client_id: string;
            callback: (response: { credential: string }) => void;
          }) => void;
          renderButton: (parent: HTMLElement, options: Record<string, unknown>) => void;
        };
      };
    };
  }
}

export default function LoginPage() {
  const router = useRouter();
  const buttonRef = useRef<HTMLDivElement>(null);

  const handleCredential = useCallback(
    async (response: { credential: string }) => {
      const res = await fetch("/api/auth/callback", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ credential: response.credential }),
      });
      if (res.ok) router.push("/dashboard");
    },
    [router]
  );

  const handleScriptLoad = useCallback(() => {
    if (!window.google || !buttonRef.current) return;
    window.google.accounts.id.initialize({
      client_id: process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID ?? "",
      callback: handleCredential,
    });
    window.google.accounts.id.renderButton(buttonRef.current, { theme: "outline", size: "large" });
  }, [handleCredential]);

  return (
    <div className={styles.page}>
      <Script src="https://accounts.google.com/gsi/client" strategy="afterInteractive" onLoad={handleScriptLoad} />
      <div className={styles.card}>
        <h1 className={styles.title}>SecondServe</h1>
        <p className={styles.subtitle}>Tableau de bord</p>
        <div ref={buttonRef} />
      </div>
    </div>
  );
}
```

- [ ] **Step 12: Vérifier le build**

Run: `cd web && npm run build`
Expected: build réussi sans erreur TypeScript (le middleware et les routes apparaissent dans le résumé de build).

- [ ] **Step 13: Commit**

```bash
git add web/lib/auth.ts web/app/api/auth/callback web/middleware.ts web/middleware.test.ts web/app/logout web/app/login
git commit -m "feat(web): authentification Google via proxy serveur (cookie httpOnly + middleware)"
```

---

### Task 5: Web — thème clair/sombre avec détection système

**Files:**
- Create: `web/lib/theme.ts`
- Test: `web/lib/theme.test.ts`
- Modify: `web/app/globals.css`
- Modify: `web/app/layout.tsx`
- Create: `web/components/dashboard/ThemeToggle.tsx`
- Create: `web/components/dashboard/ThemeToggle.module.css`
- Test: `web/components/dashboard/ThemeToggle.test.tsx`

**Interfaces:**
- Produces: `THEME_INIT_SCRIPT` (chaîne JS exécutée avant hydratation) ; variables CSS `--ss-*` (root = clair, `[data-theme="dark"]` = sombre) ; composant `<ThemeToggle />`.

- [ ] **Step 1: Écrire le test du script d'initialisation (doit échouer — module inexistant)**

`web/lib/theme.test.ts` :

```typescript
import { describe, expect, it, vi, afterEach } from "vitest";
import { THEME_INIT_SCRIPT } from "./theme";

function runInitScript(storedTheme: string | null, prefersDark: boolean) {
  document.documentElement.removeAttribute("data-theme");
  const store: Record<string, string> = {};
  if (storedTheme) store["ss-theme"] = storedTheme;
  vi.stubGlobal("localStorage", {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => {
      store[key] = value;
    },
  });
  vi.stubGlobal("matchMedia", (_query: string) => ({ matches: prefersDark }));
  // eslint-disable-next-line no-new-func
  new Function(THEME_INIT_SCRIPT)();
}

afterEach(() => {
  vi.unstubAllGlobals();
  document.documentElement.removeAttribute("data-theme");
});

describe("THEME_INIT_SCRIPT", () => {
  it("privilégie la préférence explicite stockée sur la préférence système", () => {
    runInitScript("dark", false);
    expect(document.documentElement.getAttribute("data-theme")).toBe("dark");
  });

  it("retombe sur la préférence système si rien n'est stocké", () => {
    runInitScript(null, true);
    expect(document.documentElement.getAttribute("data-theme")).toBe("dark");
    runInitScript(null, false);
    expect(document.documentElement.getAttribute("data-theme")).toBe("light");
  });

  it("ignore une valeur stockée invalide et retombe sur le système", () => {
    runInitScript("blue", true);
    expect(document.documentElement.getAttribute("data-theme")).toBe("dark");
  });
});
```

- [ ] **Step 2: Lancer le test (doit échouer)**

Run: `cd web && npm run test -- lib/theme.test.ts`
Expected: FAIL — `Cannot find module './theme'`.

- [ ] **Step 3: Implémenter le script d'init**

`web/lib/theme.ts` :

```typescript
export const THEME_INIT_SCRIPT = `(function(){try{var t=localStorage.getItem('ss-theme');if(t!=='light'&&t!=='dark'){t=window.matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light';}document.documentElement.setAttribute('data-theme',t);}catch(e){}})();`;
```

- [ ] **Step 4: Lancer le test et vérifier qu'il passe**

Run: `cd web && npm run test -- lib/theme.test.ts`
Expected: 3 tests PASS.

- [ ] **Step 5: Ajouter les variables CSS clair/sombre**

Modifier `web/app/globals.css` — ajouter avant les `@keyframes` existants :

```css
:root {
  --ss-bg: #f4f4f1;
  --ss-surface: #ffffff;
  --ss-surface-elevated: #fbfbf9;
  --ss-border: #e4e5e2;
  --ss-text: #14161a;
  --ss-muted: #6a6f78;
  --ss-faint: #9aa0a8;
  --ss-lime: #c8ff3d;
  --ss-lime-text: #14161a;
  --ss-hot: #e63958;
  --ss-data: #1f6fe5;
  --ss-surface-clay: #c85a2c;
  --ss-surface-hard: #2c6fd8;
  --ss-surface-grass: #3e9e66;
  --ss-surface-indoor: #8a5fd6;
}

[data-theme="dark"] {
  --ss-bg: #0c0d0f;
  --ss-surface: #16181c;
  --ss-surface-elevated: #1b1e23;
  --ss-border: #24272d;
  --ss-text: #f2f3f0;
  --ss-muted: #8a8f98;
  --ss-faint: #6b7079;
  --ss-lime: #c8ff3d;
  --ss-lime-text: #0c0d0f;
  --ss-hot: #ff5c7a;
  --ss-data: #4ea8ff;
  --ss-surface-clay: #e0703f;
  --ss-surface-hard: #3e8ef0;
  --ss-surface-grass: #4fb477;
  --ss-surface-indoor: #a97cf0;
}
```

(Le reste de `globals.css` — reset `*`, `html, body`, `@keyframes ssPulse` — ne change pas ; ces variables ne sont consommées que par les nouveaux composants du tableau de bord, pas par la page publique `/live/[token]` existante.)

- [ ] **Step 6: Câbler le script d'init dans le layout racine**

Modifier `web/app/layout.tsx` :

```typescript
import type { Metadata } from "next";
import { barlowSemiCondensed, spaceGrotesk } from "@/lib/fonts";
import { THEME_INIT_SCRIPT } from "@/lib/theme";
import "./globals.css";

export const metadata: Metadata = {
  title: "SecondServe — Suivi live",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="fr" className={`${barlowSemiCondensed.variable} ${spaceGrotesk.variable}`} suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: THEME_INIT_SCRIPT }} />
      </head>
      <body>{children}</body>
    </html>
  );
}
```

- [ ] **Step 7: Écrire le test du composant `ThemeToggle` (doit échouer — composant inexistant)**

`web/components/dashboard/ThemeToggle.test.tsx` :

```typescript
import { describe, expect, it, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { ThemeToggle } from "./ThemeToggle";

beforeEach(() => {
  document.documentElement.setAttribute("data-theme", "light");
  localStorage.clear();
});

describe("ThemeToggle", () => {
  it("bascule l'attribut data-theme et persiste le choix au clic", () => {
    render(<ThemeToggle />);
    const button = screen.getByRole("button", { name: /changer de thème/i });
    fireEvent.click(button);
    expect(document.documentElement.getAttribute("data-theme")).toBe("dark");
    expect(localStorage.getItem("ss-theme")).toBe("dark");

    fireEvent.click(button);
    expect(document.documentElement.getAttribute("data-theme")).toBe("light");
    expect(localStorage.getItem("ss-theme")).toBe("light");
  });
});
```

- [ ] **Step 8: Lancer le test (doit échouer)**

Run: `cd web && npm run test -- components/dashboard/ThemeToggle.test.tsx`
Expected: FAIL — `Cannot find module './ThemeToggle'`.

- [ ] **Step 9: Implémenter `ThemeToggle`**

`web/components/dashboard/ThemeToggle.module.css` :

```css
.toggle {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  border: 1px solid var(--ss-border);
  background: var(--ss-surface);
  color: var(--ss-text);
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
```

`web/components/dashboard/ThemeToggle.tsx` :

```typescript
"use client";

import { useEffect, useState } from "react";
import styles from "./ThemeToggle.module.css";

type Theme = "light" | "dark";

export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme | null>(null);

  useEffect(() => {
    const current = document.documentElement.getAttribute("data-theme");
    setTheme(current === "dark" ? "dark" : "light");
  }, []);

  function toggle() {
    const next: Theme = theme === "dark" ? "light" : "dark";
    document.documentElement.setAttribute("data-theme", next);
    localStorage.setItem("ss-theme", next);
    setTheme(next);
  }

  if (theme === null) return null;

  return (
    <button onClick={toggle} className={styles.toggle} aria-label="Changer de thème">
      {theme === "dark" ? "☀️" : "🌙"}
    </button>
  );
}
```

- [ ] **Step 10: Lancer le test et vérifier qu'il passe**

Run: `cd web && npm run test -- components/dashboard/ThemeToggle.test.tsx`
Expected: 1 test PASS.

- [ ] **Step 11: Lancer toute la suite de tests web**

Run: `cd web && npm run test`
Expected: tous les tests PASS (Task 2 à 5).

- [ ] **Step 12: Vérifier le build**

Run: `cd web && npm run build`
Expected: build réussi.

- [ ] **Step 13: Commit**

```bash
git add web/lib/theme.ts web/lib/theme.test.ts web/app/globals.css web/app/layout.tsx web/components/dashboard/ThemeToggle.tsx web/components/dashboard/ThemeToggle.module.css web/components/dashboard/ThemeToggle.test.tsx
git commit -m "feat(web): thème clair/sombre avec détection système et switch persistant"
```

---

### Task 6: Web — écran du tableau de bord

**Files:**
- Create: `web/lib/surfaces.ts`
- Create: `web/components/dashboard/Sidebar.tsx`
- Create: `web/components/dashboard/Sidebar.module.css`
- Create: `web/components/dashboard/KpiCard.tsx`
- Create: `web/components/dashboard/KpiCard.module.css`
- Create: `web/components/dashboard/MonthlyWinRateChart.tsx`
- Create: `web/components/dashboard/MonthlyWinRateChart.module.css`
- Create: `web/components/dashboard/SurfaceBreakdown.tsx`
- Create: `web/components/dashboard/SurfaceBreakdown.module.css`
- Create: `web/components/dashboard/RecentMatchesTable.tsx`
- Create: `web/components/dashboard/RecentMatchesTable.module.css`
- Create: `web/components/dashboard/DashboardView.tsx`
- Create: `web/components/dashboard/DashboardView.module.css`
- Create: `web/app/dashboard/layout.tsx`
- Create: `web/app/dashboard/layout.module.css`
- Create: `web/app/dashboard/page.tsx`
- Create: `web/app/dashboard/error.tsx`

**Interfaces:**
- Consumes: `getSessions`, `UnauthorizedError` (Task 2), `computeStats`, `computeMonthlyWinRate`, `computeWinRateTrend`, `computePlayTime` (Task 3), `SESSION_COOKIE` (Task 4), `ThemeToggle` (Task 5).
- Produces: route `/dashboard` complète.

Ces composants sont de l'assemblage visuel fidèle au mockup (comme `ScoreTable`/`LiveScoreBoard` dans le plan de la page publique) — pas de TDD, la logique testée (Task 3) est déjà couverte séparément.

- [ ] **Step 1: Labels et couleurs de surface**

`web/lib/surfaces.ts` :

```typescript
export const SURFACE_LABELS: Record<string, string> = {
  CLAY: "Terre battue",
  HARD: "Dur",
  GRASS: "Gazon",
  CARPET: "Indoor",
};

export const SURFACE_COLOR_VARS: Record<string, string> = {
  CLAY: "--ss-surface-clay",
  HARD: "--ss-surface-hard",
  GRASS: "--ss-surface-grass",
  CARPET: "--ss-surface-indoor",
};

export function surfaceLabel(surface: string): string {
  return SURFACE_LABELS[surface] ?? surface;
}

export function surfaceColorVar(surface: string): string {
  return SURFACE_COLOR_VARS[surface] ?? "--ss-faint";
}
```

- [ ] **Step 2: Sidebar**

`web/components/dashboard/Sidebar.module.css` :

```css
.sidebar {
  width: 224px;
  flex-shrink: 0;
  background: var(--ss-surface);
  border-right: 1px solid var(--ss-border);
  display: flex;
  flex-direction: column;
  padding: 24px 16px;
  min-height: 100vh;
}

.logo {
  font-family: var(--font-barlow), sans-serif;
  font-weight: 800;
  font-size: 18px;
  margin-bottom: 32px;
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

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
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--ss-lime);
}

.profile {
  margin-top: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 16px;
  border-top: 1px solid var(--ss-border);
}

.profileName {
  font-size: 14px;
  font-weight: 600;
  color: var(--ss-text);
}

.logoutButton {
  border: none;
  background: none;
  color: var(--ss-muted);
  font-size: 12px;
  cursor: pointer;
  padding: 0;
}
```

`web/components/dashboard/Sidebar.tsx` :

```typescript
import styles from "./Sidebar.module.css";

export function Sidebar() {
  return (
    <aside className={styles.sidebar}>
      <div className={styles.logo}>SecondServe</div>
      <nav className={styles.nav}>
        <div className={styles.navItemActive}>
          <span className={styles.dot} />
          Tableau de bord
        </div>
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

- [ ] **Step 3: KpiCard**

`web/components/dashboard/KpiCard.module.css` :

```css
.card {
  background: var(--ss-surface);
  border: 1px solid var(--ss-border);
  border-radius: 14px;
  padding: 18px;
}

.label {
  font-size: 12px;
  color: var(--ss-muted);
  font-weight: 500;
  margin-bottom: 8px;
}

.value {
  font-family: var(--font-barlow), sans-serif;
  font-weight: 800;
  font-size: 40px;
  line-height: 0.9;
  color: var(--ss-text);
  font-feature-settings: "tnum";
}

.valueUnit {
  font-size: 22px;
}

.subtext {
  font-size: 12px;
  color: var(--ss-muted);
  margin-top: 6px;
}

.subtextPositive {
  color: var(--ss-surface-grass);
  font-weight: 600;
}
```

`web/components/dashboard/KpiCard.tsx` :

```typescript
import styles from "./KpiCard.module.css";

type Props = {
  label: string;
  value: string;
  unit?: string;
  subtext?: string;
  subtextPositive?: boolean;
};

export function KpiCard({ label, value, unit, subtext, subtextPositive }: Props) {
  return (
    <div className={styles.card}>
      <div className={styles.label}>{label}</div>
      <div className={styles.value}>
        {value}
        {unit && <span className={styles.valueUnit}>{unit}</span>}
      </div>
      {subtext && (
        <div className={`${styles.subtext} ${subtextPositive ? styles.subtextPositive : ""}`}>{subtext}</div>
      )}
    </div>
  );
}
```

- [ ] **Step 4: MonthlyWinRateChart**

`web/components/dashboard/MonthlyWinRateChart.module.css` :

```css
.card {
  background: var(--ss-surface);
  border: 1px solid var(--ss-border);
  border-radius: 14px;
  padding: 20px;
}

.title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ss-text);
  margin-bottom: 20px;
}

.chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  height: 130px;
  gap: 12px;
}

.column {
  flex: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.bar {
  width: 100%;
  background: var(--ss-border);
  border-radius: 6px;
  min-height: 4px;
}

.barCurrent {
  background: var(--ss-lime);
}

.monthLabel {
  font-size: 11px;
  color: var(--ss-faint);
}

.monthLabelCurrent {
  color: var(--ss-text);
  font-weight: 600;
}
```

`web/components/dashboard/MonthlyWinRateChart.tsx` :

```typescript
import type { MonthlyWinRate } from "@/lib/stats";
import styles from "./MonthlyWinRateChart.module.css";

type Props = { months: MonthlyWinRate[] };

export function MonthlyWinRateChart({ months }: Props) {
  return (
    <div className={styles.card}>
      <div className={styles.title}>Win rate par mois</div>
      <div className={styles.chart}>
        {months.map((month) => (
          <div key={month.monthLabel} className={styles.column}>
            <div
              className={`${styles.bar} ${month.isCurrentMonth ? styles.barCurrent : ""}`}
              style={{ height: `${Math.round((month.winRatePercent ?? 0) * 100)}%` }}
            />
            <span className={`${styles.monthLabel} ${month.isCurrentMonth ? styles.monthLabelCurrent : ""}`}>
              {month.monthLabel}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
```

- [ ] **Step 5: SurfaceBreakdown**

`web/components/dashboard/SurfaceBreakdown.module.css` :

```css
.card {
  background: var(--ss-surface);
  border: 1px solid var(--ss-border);
  border-radius: 14px;
  padding: 20px;
}

.title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ss-text);
  margin-bottom: 18px;
}

.row {
  margin-bottom: 14px;
}

.row:last-child {
  margin-bottom: 0;
}

.rowHeader {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
}

.surfaceName {
  font-size: 13px;
  font-weight: 500;
  color: var(--ss-text);
}

.surfacePercent {
  font-size: 12px;
  color: var(--ss-muted);
}

.track {
  height: 8px;
  border-radius: 100px;
  background: var(--ss-bg);
  overflow: hidden;
}

.fill {
  height: 100%;
  border-radius: 100px;
}
```

`web/components/dashboard/SurfaceBreakdown.tsx` :

```typescript
import type { SurfaceWinRate } from "@/lib/stats";
import { surfaceLabel, surfaceColorVar } from "@/lib/surfaces";
import styles from "./SurfaceBreakdown.module.css";

type Props = { bySurface: SurfaceWinRate[] };

export function SurfaceBreakdown({ bySurface }: Props) {
  return (
    <div className={styles.card}>
      <div className={styles.title}>Par surface</div>
      {bySurface.map((entry) => {
        const percent = entry.winRatePercent === null ? null : Math.round(entry.winRatePercent * 100);
        return (
          <div key={entry.surface} className={styles.row}>
            <div className={styles.rowHeader}>
              <span className={styles.surfaceName}>{surfaceLabel(entry.surface)}</span>
              <span className={styles.surfacePercent}>{percent === null ? "—" : `${percent}%`}</span>
            </div>
            <div className={styles.track}>
              <div
                className={styles.fill}
                style={{
                  width: `${percent ?? 0}%`,
                  background: `var(${surfaceColorVar(entry.surface)})`,
                }}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
}
```

- [ ] **Step 6: RecentMatchesTable (hauteur fixe, scroll interne)**

`web/components/dashboard/RecentMatchesTable.module.css` :

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

.columnHeader {
  display: flex;
  align-items: center;
  padding: 10px 20px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--ss-faint);
  text-transform: uppercase;
  background: var(--ss-surface-elevated);
}

/* ~8 lignes visibles (44px header + 8 * 52px) ; scroll interne au-delà */
.scrollArea {
  max-height: 416px;
  overflow-y: auto;
}

.row {
  display: flex;
  align-items: center;
  padding: 14px 20px;
  border-top: 1px solid var(--ss-bg);
  font-size: 14px;
}

.dateCol {
  width: 90px;
  color: var(--ss-muted);
}

.opponentCol {
  flex: 1;
  font-weight: 500;
  color: var(--ss-text);
}

.surfaceCol {
  width: 120px;
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
  font-size: 17px;
  color: var(--ss-text);
}

.resultCol {
  width: 90px;
  text-align: right;
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

.empty {
  padding: 32px 20px;
  text-align: center;
  color: var(--ss-muted);
  font-size: 14px;
}
```

`web/components/dashboard/RecentMatchesTable.tsx` :

```typescript
import type { SessionDto } from "@/lib/types";
import { surfaceLabel } from "@/lib/surfaces";
import styles from "./RecentMatchesTable.module.css";

type Props = { matches: SessionDto[] };

function formatDate(timestampMs: number): string {
  return new Date(timestampMs).toLocaleDateString("fr-FR", { day: "numeric", month: "short" });
}

export function RecentMatchesTable({ matches }: Props) {
  return (
    <div className={styles.card}>
      <div className={styles.header}>Derniers matchs</div>
      <div className={styles.columnHeader}>
        <span style={{ width: 90 }}>Date</span>
        <span style={{ flex: 1 }}>Adversaire</span>
        <span style={{ width: 120 }}>Surface</span>
        <span style={{ width: 120 }}>Score</span>
        <span style={{ width: 90, textAlign: "right" }}>Résultat</span>
      </div>
      {matches.length === 0 ? (
        <div className={styles.empty}>Pas encore de match</div>
      ) : (
        <div className={styles.scrollArea}>
          {matches.map((match) => (
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
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
```

La colonne Score affiche `match.scoreText` (champ `score_text`, déjà exposé par le backend et mappé côté client depuis Task 1/2) — jamais de donnée fabriquée : `—` si le champ est `null` (match sans score enregistré).

- [ ] **Step 7: DashboardView (assemblage)**

`web/components/dashboard/DashboardView.module.css` :

```css
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.title {
  font-family: var(--font-barlow), sans-serif;
  font-weight: 800;
  font-size: 24px;
  color: var(--ss-text);
}

.kpiGrid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 20px;
}

.middleGrid {
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: 14px;
  margin-bottom: 20px;
}
```

`web/components/dashboard/DashboardView.tsx` :

```typescript
import type { AggregatedStats, MonthlyWinRate, PlayTime } from "@/lib/stats";
import { computeWinRateTrend } from "@/lib/stats";
import type { SessionDto } from "@/lib/types";
import { KpiCard } from "./KpiCard";
import { MonthlyWinRateChart } from "./MonthlyWinRateChart";
import { SurfaceBreakdown } from "./SurfaceBreakdown";
import { RecentMatchesTable } from "./RecentMatchesTable";
import { ThemeToggle } from "./ThemeToggle";
import styles from "./DashboardView.module.css";

type Props = {
  stats: AggregatedStats;
  monthlyWinRate: MonthlyWinRate[];
  playTime: PlayTime;
  recentMatches: SessionDto[];
};

function formatPercent(value: number | null): string {
  return value === null ? "—" : `${Math.round(value * 100)}`;
}

export function DashboardView({ stats, monthlyWinRate, playTime, recentMatches }: Props) {
  const trend = computeWinRateTrend(monthlyWinRate);
  const streakLabel =
    stats.activeStreak === null
      ? null
      : `${stats.activeStreak.count} ${stats.activeStreak.result === "VICTORY" ? "V" : "D"}`;

  return (
    <div>
      <div className={styles.header}>
        <h1 className={styles.title}>Tableau de bord</h1>
        <ThemeToggle />
      </div>

      <div className={styles.kpiGrid}>
        <KpiCard
          label="Win rate global"
          value={formatPercent(stats.winRateGlobal)}
          unit="%"
          subtext={trend === null ? undefined : `${trend >= 0 ? "↑" : "↓"} ${Math.abs(Math.round(trend * 100))}% vs mois dernier`}
          subtextPositive={trend !== null && trend >= 0}
        />
        <KpiCard
          label="Victoires · Défaites"
          value={`${stats.victories}·${stats.defeats}`}
          subtext={`${stats.completedMatchSessions} matchs terminés`}
        />
        <KpiCard
          label="Séquence active"
          value={streakLabel ?? "—"}
          subtext={streakLabel ? "en cours" : undefined}
        />
        <KpiCard
          label="Temps de jeu"
          value={String(Math.round(playTime.hours))}
          unit="h"
          subtext={`${playTime.sessionCount} sessions`}
        />
      </div>

      <div className={styles.middleGrid}>
        <MonthlyWinRateChart months={monthlyWinRate} />
        <SurfaceBreakdown bySurface={stats.winRateBySurface} />
      </div>

      <RecentMatchesTable matches={recentMatches} />
    </div>
  );
}
```

- [ ] **Step 8: Layout, page et état d'erreur du segment `/dashboard`**

`web/app/dashboard/layout.module.css` :

```css
.shell {
  display: flex;
  min-height: 100vh;
  background: var(--ss-bg);
}

.content {
  flex: 1;
  padding: 24px 32px;
}
```

`web/app/dashboard/layout.tsx` :

```typescript
import { Sidebar } from "@/components/dashboard/Sidebar";
import styles from "./layout.module.css";

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className={styles.shell}>
      <Sidebar />
      <main className={styles.content}>{children}</main>
    </div>
  );
}
```

`web/app/dashboard/page.tsx` :

```typescript
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { SESSION_COOKIE } from "@/lib/auth";
import { getSessions, UnauthorizedError } from "@/lib/api";
import { computeStats, computeMonthlyWinRate, computePlayTime } from "@/lib/stats";
import { DashboardView } from "@/components/dashboard/DashboardView";

const RECENT_MATCHES_LIMIT = 30;

export default async function DashboardPage() {
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

  const stats = computeStats(sessions);
  const monthlyWinRate = computeMonthlyWinRate(sessions);
  const playTime = computePlayTime(sessions);
  const recentMatches = sessions
    .filter((s) => s.sessionType === "MATCH" && s.status === "COMPLETED")
    .slice(0, RECENT_MATCHES_LIMIT);

  return (
    <DashboardView
      stats={stats}
      monthlyWinRate={monthlyWinRate}
      playTime={playTime}
      recentMatches={recentMatches}
    />
  );
}
```

`web/app/dashboard/error.tsx` :

```typescript
"use client";

export default function DashboardError({ reset }: { error: Error; reset: () => void }) {
  return (
    <div style={{ minHeight: "100vh", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 16 }}>
      <h1 style={{ fontFamily: "var(--font-barlow), sans-serif", fontWeight: 800 }}>Impossible de charger le tableau de bord</h1>
      <p style={{ color: "#6A6F78" }}>Le serveur SecondServe est peut-être indisponible.</p>
      <button onClick={() => reset()}>Réessayer</button>
    </div>
  );
}
```

- [ ] **Step 9: Lancer toute la suite de tests web**

Run: `cd web && npm run test`
Expected: tous les tests PASS (aucune régression sur Task 2-5).

- [ ] **Step 10: Vérifier le build**

Run: `cd web && npm run build`
Expected: build réussi, route `/dashboard` listée.

- [ ] **Step 11: Commit**

```bash
git add web/lib/surfaces.ts web/components/dashboard web/app/dashboard
git commit -m "feat(web): écran du tableau de bord desktop (KPI, win rate par mois, par surface, derniers matchs)"
```

---

### Task 7: Redirection racine, variables d'environnement et documentation de déploiement

**Files:**
- Modify: `web/app/page.tsx`
- Delete: `web/app/page.module.css`
- Create: `web/.env.example`
- Modify: `web/DEPLOY.md`

**Interfaces:**
- Produces: `/` redirige vers `/dashboard` (qui redirige lui-même vers `/login` si non authentifié) ; documentation complète des nouvelles variables d'environnement et de l'étape manuelle Google Cloud Console.

- [ ] **Step 1: Remplacer la page racine par une redirection**

Modifier `web/app/page.tsx` :

```typescript
import { redirect } from "next/navigation";

export default function RootPage() {
  redirect("/dashboard");
}
```

Run: `rm web/app/page.module.css`

- [ ] **Step 2: Documenter les variables d'environnement du projet web**

`web/.env.example` :

```
# URL du backend FastAPI, utilisée uniquement côté serveur Next.js
# (page publique /live/[token], callback d'auth, tableau de bord)
API_BASE_URL=http://localhost:8000

# URL publique du backend, exposée au navigateur pour la connexion SSE de /live/[token]
NEXT_PUBLIC_API_BASE_URL=http://localhost:8000

# Web Client ID Google (le même que GOOGLE_CLIENT_ID côté backend) — utilisé par
# Google Identity Services JS sur /login pour afficher le bouton de connexion.
NEXT_PUBLIC_GOOGLE_CLIENT_ID=your-web-client-id.apps.googleusercontent.com
```

- [ ] **Step 3: Mettre à jour `DEPLOY.md`**

Modifier `web/DEPLOY.md` — remplacer le contenu de la section « 2. Configurer les variables d'environnement sur le VPS » :

```markdown
### 2. Configurer les variables d'environnement sur le VPS

`/opt/secondserve-web/.env.production.local` :

```
PORT=3000
API_BASE_URL=http://127.0.0.1:8000
NEXT_PUBLIC_API_BASE_URL=https://api.<ton-domaine>
NEXT_PUBLIC_GOOGLE_CLIENT_ID=<Web Client ID Google — le même que GOOGLE_CLIENT_ID côté backend>
```

> `API_BASE_URL` (sans `NEXT_PUBLIC_`) est utilisé côté serveur (page publique, callback d'auth `/api/auth/callback`, tableau de bord `/dashboard`) — appel direct en local sur le VPS, pas via Cloudflare. `NEXT_PUBLIC_API_BASE_URL` est exposé au navigateur pour la connexion SSE de la page publique. `NEXT_PUBLIC_GOOGLE_CLIENT_ID` est exposé au navigateur pour afficher le bouton Google Identity Services sur `/login`.

### 2bis. Étape manuelle : autoriser le domaine desktop dans Google Cloud Console

Le Web Client ID Google existant (créé pour l'auth Android, cf. `docs/superpowers/plans/2026-06-25-google-signin-auth.md`) doit aussi autoriser le domaine du tableau de bord comme origine JavaScript :

1. https://console.cloud.google.com → APIs & Services → Credentials
2. Ouvrir le **Web Client ID** existant (celui utilisé pour `GOOGLE_CLIENT_ID` côté backend)
3. Dans **Authorized JavaScript origins**, ajouter `https://<ton-domaine>`
4. Enregistrer (peut prendre quelques minutes pour se propager)
```

- [ ] **Step 4: Vérifier le build final**

Run: `cd web && npm run build`
Expected: build réussi, route `/` en redirection statique vers `/dashboard`.

- [ ] **Step 5: Commit**

```bash
git add web/app/page.tsx web/.env.example web/DEPLOY.md
git rm web/app/page.module.css
git commit -m "feat(web): rediriger / vers /dashboard et documenter le déploiement du tableau de bord"
```

- [ ] **Step 6: Test manuel de bout en bout**

Avec le backend lancé localement et au moins une session créée en base (`POST /api/v1/sessions`), et le web en local :

```bash
cd backend && uv run uvicorn app.main:app --port 8000 &
cd web && API_BASE_URL=http://localhost:8000 NEXT_PUBLIC_GOOGLE_CLIENT_ID=<ton-client-id-de-dev> npm run dev -- --port 3100
```

1. Ouvrir `http://localhost:3100` → doit rediriger vers `http://localhost:3100/login` (pas de cookie).
2. Se connecter avec le compte Google `ben.finot@gmail.com` → redirection vers `/dashboard`.
3. Vérifier : les 4 cartes KPI, le graphe win rate par mois, la répartition par surface et la table des derniers matchs s'affichent avec les vraies données du backend.
4. Basculer le switch de thème → l'interface passe en sombre immédiatement ; recharger la page → le thème sombre est conservé.
5. Cliquer « Déconnexion » → redirection vers `/login` ; retourner sur `/dashboard` directement par l'URL → redirection immédiate vers `/login` (middleware).

Expected: les 5 vérifications passent sans erreur console.
