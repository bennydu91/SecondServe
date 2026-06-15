import pytest
import jwt
from app.core.config import settings


@pytest.mark.asyncio
async def test_init_auth_returns_token(client):
    """Test that POST /api/v1/auth/init returns a valid JWT token"""
    response = await client.post("/api/v1/auth/init")
    assert response.status_code == 200
    data = response.json()
    assert "token" in data
    assert isinstance(data["token"], str)


@pytest.mark.asyncio
async def test_init_auth_token_is_valid_jwt(client):
    """Test that the returned token is a valid JWT that can be decoded"""
    response = await client.post("/api/v1/auth/init")
    assert response.status_code == 200
    data = response.json()
    token = data["token"]

    decoded = jwt.decode(token, settings.jwt_secret, algorithms=["HS256"])
    assert "exp" in decoded
    assert "iat" in decoded


@pytest.mark.asyncio
async def test_init_auth_multiple_calls_return_different_tokens(client):
    """Test that multiple calls to /init return different tokens (due to iat timestamp)"""
    response1 = await client.post("/api/v1/auth/init")
    response2 = await client.post("/api/v1/auth/init")

    token1 = response1.json()["token"]
    token2 = response2.json()["token"]

    # Tokens should be different (due to iat timestamp)
    assert token1 != token2

    # Both should be valid
    decoded1 = jwt.decode(token1, settings.jwt_secret, algorithms=["HS256"])
    decoded2 = jwt.decode(token2, settings.jwt_secret, algorithms=["HS256"])
    assert "exp" in decoded1
    assert "exp" in decoded2


@pytest.mark.asyncio
async def test_missing_auth_header_returns_401(client):
    """Test that accessing a protected route without token returns 401"""
    response = await client.get("/api/v1/auth/verify")
    assert response.status_code == 401
    data = response.json()
    assert "detail" in data


@pytest.mark.asyncio
async def test_invalid_token_returns_401(client):
    """Test that accessing a protected route with invalid token returns 401"""
    response = await client.get(
        "/api/v1/auth/verify",
        headers={"Authorization": "Bearer invalid.token.here"}
    )
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_valid_token_returns_200(client):
    """Test that accessing a protected route with valid token returns 200"""
    # First, get a valid token
    init_response = await client.post("/api/v1/auth/init")
    token = init_response.json()["token"]

    # Use the token to access the protected route
    response = await client.get(
        "/api/v1/auth/verify",
        headers={"Authorization": f"Bearer {token}"}
    )
    assert response.status_code == 200
    data = response.json()
    assert data["message"] == "Token is valid"
    assert "payload" in data
