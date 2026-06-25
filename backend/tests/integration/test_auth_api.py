# backend/tests/integration/test_auth_api.py
import pytest
import jwt
from unittest.mock import patch, AsyncMock
from app.core.config import settings


VALID_GOOGLE_PAYLOAD = {
    "email": "ben.finot@gmail.com",
    "email_verified": True,
    "sub": "1234567890",
    "iss": "https://accounts.google.com",
    "aud": settings.google_client_id,
}

UNAUTHORIZED_GOOGLE_PAYLOAD = {
    "email": "hacker@gmail.com",
    "email_verified": True,
    "sub": "9999999999",
}


@pytest.mark.asyncio
async def test_init_auth_with_valid_google_token_returns_jwt(client):
    with patch(
        "app.api.v1.auth.verify_google_id_token",
        new=AsyncMock(return_value=VALID_GOOGLE_PAYLOAD),
    ):
        response = await client.post(
            "/api/v1/auth/init",
            json={"google_id_token": "fake.google.token"},
        )
    assert response.status_code == 200
    data = response.json()
    assert "token" in data
    decoded = jwt.decode(data["token"], settings.jwt_secret, algorithms=["HS256"])
    assert "exp" in decoded
    assert "iat" in decoded


@pytest.mark.asyncio
async def test_init_auth_with_invalid_google_token_returns_401(client):
    with patch(
        "app.api.v1.auth.verify_google_id_token",
        new=AsyncMock(side_effect=jwt.InvalidTokenError("bad signature")),
    ):
        response = await client.post(
            "/api/v1/auth/init",
            json={"google_id_token": "invalid.token.here"},
        )
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_init_auth_when_google_service_unavailable_returns_503(client):
    with patch(
        "app.api.v1.auth.verify_google_id_token",
        new=AsyncMock(side_effect=Exception("timeout")),
    ):
        response = await client.post(
            "/api/v1/auth/init",
            json={"google_id_token": "some.token"},
        )
    assert response.status_code == 503


@pytest.mark.asyncio
async def test_init_auth_with_unauthorized_email_returns_403(client):
    with patch(
        "app.api.v1.auth.verify_google_id_token",
        new=AsyncMock(return_value=UNAUTHORIZED_GOOGLE_PAYLOAD),
    ):
        response = await client.post(
            "/api/v1/auth/init",
            json={"google_id_token": "fake.token.for.hacker"},
        )
    assert response.status_code == 403


@pytest.mark.asyncio
async def test_init_auth_missing_body_returns_422(client):
    response = await client.post("/api/v1/auth/init")
    assert response.status_code == 422


@pytest.mark.asyncio
async def test_init_auth_missing_token_field_returns_422(client):
    response = await client.post("/api/v1/auth/init", json={})
    assert response.status_code == 422


@pytest.mark.asyncio
async def test_missing_auth_header_returns_401(client):
    response = await client.get("/api/v1/auth/verify")
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_invalid_token_returns_401(client):
    response = await client.get(
        "/api/v1/auth/verify",
        headers={"Authorization": "Bearer invalid.token.here"},
    )
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_valid_token_returns_200(client):
    with patch(
        "app.api.v1.auth.verify_google_id_token",
        new=AsyncMock(return_value=VALID_GOOGLE_PAYLOAD),
    ):
        init_response = await client.post(
            "/api/v1/auth/init",
            json={"google_id_token": "fake.google.token"},
        )
    token = init_response.json()["token"]

    response = await client.get(
        "/api/v1/auth/verify",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 200
    assert response.json()["message"] == "Token is valid"
