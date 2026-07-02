import json as json_module

import pytest
from starlette.requests import Request as StarletteRequest

from app.api.v1.live_sharing import stream_snapshot
from app.features.live_sharing.repository import MatchShareRepository
from app.features.live_sharing.service import LiveSharingService
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
async def test_stream_first_event_is_current_snapshot(client, db_session):
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

    # httpx's ASGITransport (used by the `client` fixture) fully awaits the ASGI
    # app before returning any response — it does not support real incremental
    # streaming. Driving `GET /{token}/stream` through it would deadlock as soon
    # as the endpoint's loop calls `request.is_disconnected()` a second time
    # (no real socket ever signals disconnection). We instead call the route
    # handler directly — same production code (service, broadcaster, generator)
    # — and only pull the first chunk off the real StreamingResponse it returns.
    async def fake_receive():
        return {"type": "http.disconnect"}

    fake_request = StarletteRequest(
        {"type": "http", "method": "GET", "headers": [], "query_string": b"", "client": None, "path": f"/api/v1/live/{share_token}/stream"},
        receive=fake_receive,
    )
    service = LiveSharingService(MatchShareRepository(db_session))

    response = await stream_snapshot(token=share_token, request=fake_request, service=service)

    body_iterator = response.body_iterator
    try:
        first_chunk = await body_iterator.__anext__()
    finally:
        await body_iterator.aclose()

    assert first_chunk.startswith("data: ")
    payload = json_module.loads(first_chunk[len("data: "):].strip())
    assert payload["status"] == "LIVE"
    assert payload["current_set_games_a"] == 2
