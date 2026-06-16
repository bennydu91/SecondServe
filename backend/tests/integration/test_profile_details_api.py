import pytest
import jwt
from app.core.config import settings


def make_token() -> str:
    import time
    payload = {"iat": int(time.time()), "exp": int(time.time()) + 3600}
    return jwt.encode(payload, settings.jwt_secret, algorithm="HS256")


@pytest.mark.asyncio
async def test_update_profile_details_valid(client):
    token = make_token()
    response = await client.put(
        "/api/v1/profile/details",
        json={
            "play_style": "OFFENSIVE",
            "preferred_surfaces": "CLAY,HARD",
            "coach_instruction_1": "Améliorer le service"
        },
        headers={"Authorization": f"Bearer {token}"}
    )
    assert response.status_code == 200
    assert "updated_at" in response.json()


@pytest.mark.asyncio
async def test_update_profile_invalid_style(client):
    token = make_token()
    response = await client.put(
        "/api/v1/profile/details",
        json={"play_style": "INVINCIBLE"},
        headers={"Authorization": f"Bearer {token}"}
    )
    assert response.status_code == 422


@pytest.mark.asyncio
async def test_update_profile_all_null(client):
    token = make_token()
    response = await client.put(
        "/api/v1/profile/details",
        json={},
        headers={"Authorization": f"Bearer {token}"}
    )
    assert response.status_code == 200
    assert "updated_at" in response.json()


@pytest.mark.asyncio
async def test_update_profile_no_fft_license_in_response(client):
    token = make_token()
    response = await client.get(
        "/api/v1/profile",
        headers={"Authorization": f"Bearer {token}"}
    )
    data = response.json()
    assert "fft_license" not in data
    assert "license" not in data
    assert "fft_license_number" not in data


@pytest.mark.asyncio
async def test_update_profile_without_token(client):
    response = await client.put("/api/v1/profile/details", json={})
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_update_profile_details_persisted_in_get(client):
    token = make_token()
    await client.put(
        "/api/v1/profile/details",
        json={"play_style": "DEFENSIVE", "preferred_surfaces": "CLAY"},
        headers={"Authorization": f"Bearer {token}"}
    )
    response = await client.get(
        "/api/v1/profile",
        headers={"Authorization": f"Bearer {token}"}
    )
    data = response.json()
    assert data["play_style"] == "DEFENSIVE"
    assert data["preferred_surfaces"] == "CLAY"


@pytest.mark.asyncio
async def test_update_profile_valid_styles(client):
    token = make_token()
    for style in ["DEFENSIVE", "OFFENSIVE", "COUNTERPUNCHER", "ALL_COURT"]:
        response = await client.put(
            "/api/v1/profile/details",
            json={"play_style": style},
            headers={"Authorization": f"Bearer {token}"}
        )
        assert response.status_code == 200, f"Style {style} devrait être valide"
