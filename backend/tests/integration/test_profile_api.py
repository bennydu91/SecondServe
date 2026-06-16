import pytest
import jwt
from app.core.config import settings


def make_token() -> str:
    import time
    payload = {"iat": int(time.time()), "exp": int(time.time()) + 3600}
    return jwt.encode(payload, settings.jwt_secret, algorithm="HS256")


@pytest.mark.asyncio
async def test_save_ranking_valid(client):
    token = make_token()
    response = await client.post(
        "/api/v1/profile/ranking",
        json={"series": "15/2", "points": 850},
        headers={"Authorization": f"Bearer {token}"}
    )
    assert response.status_code == 201
    data = response.json()
    assert data["series"] == "15/2"
    assert data["points"] == 850
    assert "id" in data
    assert "recorded_at" in data


@pytest.mark.asyncio
async def test_save_ranking_invalid_series(client):
    token = make_token()
    response = await client.post(
        "/api/v1/profile/ranking",
        json={"series": "invalide", "points": 100},
        headers={"Authorization": f"Bearer {token}"}
    )
    assert response.status_code == 422


@pytest.mark.asyncio
async def test_save_ranking_zero_points(client):
    token = make_token()
    response = await client.post(
        "/api/v1/profile/ranking",
        json={"series": "15/2", "points": 0},
        headers={"Authorization": f"Bearer {token}"}
    )
    assert response.status_code == 422


@pytest.mark.asyncio
async def test_get_profile_empty(client):
    token = make_token()
    response = await client.get(
        "/api/v1/profile",
        headers={"Authorization": f"Bearer {token}"}
    )
    assert response.status_code == 200
    data = response.json()
    assert data["current_series"] is None
    assert data["ranking_history"] == []


@pytest.mark.asyncio
async def test_get_profile_after_save(client):
    token = make_token()
    await client.post(
        "/api/v1/profile/ranking",
        json={"series": "30/2", "points": 1200},
        headers={"Authorization": f"Bearer {token}"}
    )
    response = await client.get(
        "/api/v1/profile",
        headers={"Authorization": f"Bearer {token}"}
    )
    assert response.status_code == 200
    data = response.json()
    assert data["current_series"] == "30/2"
    assert len(data["ranking_history"]) == 1
    assert data["ranking_history"][0]["series"] == "30/2"


@pytest.mark.asyncio
async def test_save_ranking_without_token(client):
    response = await client.post(
        "/api/v1/profile/ranking",
        json={"series": "15/2", "points": 850}
    )
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_get_profile_without_token(client):
    response = await client.get("/api/v1/profile")
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_ranking_history_ordered_by_date_desc(client):
    token = make_token()
    await client.post(
        "/api/v1/profile/ranking",
        json={"series": "40", "points": 500},
        headers={"Authorization": f"Bearer {token}"}
    )
    await client.post(
        "/api/v1/profile/ranking",
        json={"series": "15/2", "points": 850},
        headers={"Authorization": f"Bearer {token}"}
    )
    response = await client.get(
        "/api/v1/profile",
        headers={"Authorization": f"Bearer {token}"}
    )
    history = response.json()["ranking_history"]
    assert len(history) == 2
    assert history[0]["series"] == "15/2"
    assert history[1]["series"] == "40"
