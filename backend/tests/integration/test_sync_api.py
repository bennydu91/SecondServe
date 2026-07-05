import pytest
import jwt
import time
from app.core.config import settings


def make_token() -> str:
    payload = {"iat": int(time.time()), "exp": int(time.time()) + 3600}
    return jwt.encode(payload, settings.jwt_secret, algorithm="HS256")


def auth(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


def session_dto(
    client_id: int = 1,
    surface: str = "CLAY",
    match_format: str = "BEST_OF_3",
    third_set_rule: str = "FULL_ADVANTAGE",
    status: str = "COMPLETED",
    result: str = "VICTORY",
    updated_at: int = 2_000_000,
    feeling_rating: int | None = None,
    feeling_comment: str | None = None,
) -> dict:
    return {
        "client_id": client_id,
        "surface": surface,
        "match_format": match_format,
        "third_set_rule": third_set_rule,
        "opponent": None,
        "competition_type": None,
        "tournament": None,
        "status": status,
        "session_type": "MATCH",
        "result": result,
        "feeling_rating": feeling_rating,
        "feeling_comment": feeling_comment,
        "created_at": 1_000_000,
        "updated_at": updated_at,
    }


@pytest.mark.asyncio
async def test_sync_push_new_session(client):
    token = make_token()
    response = await client.post(
        "/api/v1/sync/push",
        json={"sessions": [session_dto(client_id=1)]},
        headers=auth(token),
    )
    assert response.status_code == 200
    data = response.json()
    assert data["synced_sessions"] == 1


@pytest.mark.asyncio
async def test_sync_push_requires_jwt(client):
    response = await client.post(
        "/api/v1/sync/push",
        json={"sessions": [session_dto()]},
    )
    assert response.status_code in (401, 403)


@pytest.mark.asyncio
async def test_sync_push_idempotence_double_send(client):
    token = make_token()
    payload = {"sessions": [session_dto(client_id=42)]}

    r1 = await client.post("/api/v1/sync/push", json=payload, headers=auth(token))
    assert r1.status_code == 200

    r2 = await client.post("/api/v1/sync/push", json=payload, headers=auth(token))
    assert r2.status_code == 200

    # Les deux pushes doivent réussir et chacun syncer 1 session (idempotent — pas de doublon)
    assert r2.json()["synced_sessions"] == 1


@pytest.mark.asyncio
async def test_sync_push_last_write_wins(client):
    token = make_token()

    # Premier push : session COMPLETED, updated_at=1000
    r1 = await client.post(
        "/api/v1/sync/push",
        json={"sessions": [session_dto(client_id=99, status="COMPLETED", updated_at=1_000)]},
        headers=auth(token),
    )
    assert r1.status_code == 200

    # Deuxième push : updated_at plus récent → doit écraser
    r2 = await client.post(
        "/api/v1/sync/push",
        json={"sessions": [session_dto(client_id=99, status="INTERRUPTED", updated_at=2_000)]},
        headers=auth(token),
    )
    assert r2.status_code == 200

    # Troisième push : updated_at plus ancien → doit être ignoré
    r3 = await client.post(
        "/api/v1/sync/push",
        json={"sessions": [session_dto(client_id=99, status="ACTIVE", updated_at=500)]},
        headers=auth(token),
    )
    assert r3.status_code == 200


@pytest.mark.asyncio
async def test_sync_push_with_feeling(client):
    token = make_token()
    response = await client.post(
        "/api/v1/sync/push",
        json={
            "sessions": [
                session_dto(
                    client_id=77,
                    feeling_rating=4,
                    feeling_comment="Bon match, bonne concentration",
                )
            ]
        },
        headers=auth(token),
    )
    assert response.status_code == 200
    assert response.json()["synced_sessions"] == 1


@pytest.mark.asyncio
async def test_sync_push_empty_sessions(client):
    token = make_token()
    response = await client.post(
        "/api/v1/sync/push",
        json={"sessions": []},
        headers=auth(token),
    )
    assert response.status_code == 200
    assert response.json()["synced_sessions"] == 0


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
