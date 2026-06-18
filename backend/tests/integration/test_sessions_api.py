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
