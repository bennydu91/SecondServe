import pytest
import jwt
from app.core.config import settings


def make_token() -> str:
    import time
    payload = {"iat": int(time.time()), "exp": int(time.time()) + 3600}
    return jwt.encode(payload, settings.jwt_secret, algorithm="HS256")


def auth(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


@pytest.mark.asyncio
async def test_list_work_axes_empty(client):
    token = make_token()
    response = await client.get("/api/v1/work_axes", headers=auth(token))
    assert response.status_code == 200
    assert response.json() == {"items": [], "total": 0}


@pytest.mark.asyncio
async def test_create_work_axis(client):
    token = make_token()
    response = await client.post(
        "/api/v1/work_axes",
        json={"title": "Travail du revers", "created_at": 1749470400000},
        headers=auth(token)
    )
    assert response.status_code == 201
    data = response.json()
    assert data["title"] == "Travail du revers"
    assert "id" in data
    assert "updated_at" in data


@pytest.mark.asyncio
async def test_create_4th_axis_rejected(client):
    token = make_token()
    for i in range(3):
        resp = await client.post(
            "/api/v1/work_axes",
            json={"title": f"Axe {i}", "created_at": 1000 + i},
            headers=auth(token)
        )
        assert resp.status_code == 201
    response = await client.post(
        "/api/v1/work_axes",
        json={"title": "Axe 4", "created_at": 1003},
        headers=auth(token)
    )
    assert response.status_code == 422
    assert response.json()["detail"]["error_code"] == "MAX_WORK_AXES_REACHED"


@pytest.mark.asyncio
async def test_update_work_axis(client):
    token = make_token()
    create_resp = await client.post(
        "/api/v1/work_axes",
        json={"title": "Ancien titre", "created_at": 1000},
        headers=auth(token)
    )
    axis_id = create_resp.json()["id"]
    resp = await client.put(
        f"/api/v1/work_axes/{axis_id}",
        json={"title": "Nouveau titre", "created_at": 1000},
        headers=auth(token)
    )
    assert resp.status_code == 200
    assert resp.json()["title"] == "Nouveau titre"


@pytest.mark.asyncio
async def test_delete_work_axis(client):
    token = make_token()
    create_resp = await client.post(
        "/api/v1/work_axes",
        json={"title": "À supprimer", "created_at": 1000},
        headers=auth(token)
    )
    axis_id = create_resp.json()["id"]
    del_resp = await client.delete(f"/api/v1/work_axes/{axis_id}", headers=auth(token))
    assert del_resp.status_code == 204


@pytest.mark.asyncio
async def test_delete_nonexistent_axis(client):
    token = make_token()
    resp = await client.delete("/api/v1/work_axes/99999", headers=auth(token))
    assert resp.status_code == 404
    assert resp.json()["detail"]["error_code"] == "WORK_AXIS_NOT_FOUND"


@pytest.mark.asyncio
async def test_update_nonexistent_axis(client):
    token = make_token()
    resp = await client.put(
        "/api/v1/work_axes/99999",
        json={"title": "Titre", "created_at": 1000},
        headers=auth(token)
    )
    assert resp.status_code == 404


@pytest.mark.asyncio
async def test_work_axes_require_jwt(client):
    response = await client.get("/api/v1/work_axes")
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_work_axes_create_requires_jwt(client):
    response = await client.post(
        "/api/v1/work_axes",
        json={"title": "Test", "created_at": 1000}
    )
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_create_axis_blank_title_rejected(client):
    token = make_token()
    resp = await client.post(
        "/api/v1/work_axes",
        json={"title": "  ", "created_at": 1000},
        headers=auth(token)
    )
    assert resp.status_code == 422


@pytest.mark.asyncio
async def test_create_axis_title_too_long(client):
    token = make_token()
    resp = await client.post(
        "/api/v1/work_axes",
        json={"title": "x" * 201, "created_at": 1000},
        headers=auth(token)
    )
    assert resp.status_code == 422


@pytest.mark.asyncio
async def test_list_work_axes_returns_all_after_create(client):
    token = make_token()
    await client.post(
        "/api/v1/work_axes",
        json={"title": "Revers", "created_at": 1000},
        headers=auth(token)
    )
    await client.post(
        "/api/v1/work_axes",
        json={"title": "Service", "created_at": 2000},
        headers=auth(token)
    )
    resp = await client.get("/api/v1/work_axes", headers=auth(token))
    assert resp.status_code == 200
    data = resp.json()
    assert data["total"] == 2
    titles = [item["title"] for item in data["items"]]
    assert "Revers" in titles
    assert "Service" in titles
