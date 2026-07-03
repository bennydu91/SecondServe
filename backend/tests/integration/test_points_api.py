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
