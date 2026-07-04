import pytest
import jwt
import time
from app.core.config import settings


def make_token() -> str:
    payload = {"iat": int(time.time()), "exp": int(time.time()) + 3600}
    return jwt.encode(payload, settings.jwt_secret, algorithm="HS256")


def auth(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


@pytest.mark.asyncio
async def test_create_session_minimal(client):
    token = make_token()
    response = await client.post(
        "/api/v1/sessions",
        json={
            "surface": "CLAY",
            "match_format": "BEST_OF_3",
            "third_set_rule": "FULL_ADVANTAGE",
            "created_at": 1_000_000
        },
        headers=auth(token)
    )
    assert response.status_code == 201
    data = response.json()
    assert data["surface"] == "CLAY"
    assert data["match_format"] == "BEST_OF_3"
    assert data["third_set_rule"] == "FULL_ADVANTAGE"
    assert data["status"] == "ACTIVE"
    assert data["session_type"] == "MATCH"
    assert data["result"] is None
    assert "id" in data
    assert data["id"] > 0


@pytest.mark.asyncio
async def test_create_session_with_optional_fields(client):
    token = make_token()
    response = await client.post(
        "/api/v1/sessions",
        json={
            "surface": "HARD",
            "match_format": "BEST_OF_1",
            "third_set_rule": "FULL_ADVANTAGE",
            "opponent": "Dupont",
            "competition_type": "Ligue",
            "tournament": "Open 06",
            "created_at": 2_000_000
        },
        headers=auth(token)
    )
    assert response.status_code == 201
    data = response.json()
    assert data["opponent"] == "Dupont"
    assert data["competition_type"] == "Ligue"
    assert data["tournament"] == "Open 06"
    assert data["surface"] == "HARD"
    assert data["match_format"] == "BEST_OF_1"


@pytest.mark.asyncio
async def test_create_session_requires_jwt(client):
    response = await client.post(
        "/api/v1/sessions",
        json={
            "surface": "CLAY",
            "match_format": "BEST_OF_3",
            "third_set_rule": "FULL_ADVANTAGE",
            "created_at": 1_000_000
        }
    )
    assert response.status_code in (401, 403)


@pytest.mark.asyncio
async def test_create_session_super_tie_break(client):
    token = make_token()
    response = await client.post(
        "/api/v1/sessions",
        json={
            "surface": "GRASS",
            "match_format": "BEST_OF_3",
            "third_set_rule": "SUPER_TIE_BREAK_10",
            "created_at": 3_000_000
        },
        headers=auth(token)
    )
    assert response.status_code == 201
    assert response.json()["third_set_rule"] == "SUPER_TIE_BREAK_10"


@pytest.mark.asyncio
async def test_create_session_sets_updated_at(client):
    token = make_token()
    response = await client.post(
        "/api/v1/sessions",
        json={
            "surface": "CARPET",
            "match_format": "BEST_OF_1",
            "third_set_rule": "FULL_ADVANTAGE",
            "created_at": 1_000_000
        },
        headers=auth(token)
    )
    assert response.status_code == 201
    data = response.json()
    assert "updated_at" in data
    assert data["updated_at"] == 1_000_000


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
