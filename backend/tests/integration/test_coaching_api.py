import pytest
import jwt
import time
from unittest.mock import AsyncMock, patch
from app.core.config import settings
from app.shared.exceptions import SecondServeException


def make_token() -> str:
    payload = {"iat": int(time.time()), "exp": int(time.time()) + 3600}
    return jwt.encode(payload, settings.jwt_secret, algorithm="HS256")


def auth(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


@pytest.mark.asyncio
async def test_analyze_success(client):
    token = make_token()
    with patch.object(settings, "mistral_api_key", "test-api-key"), \
         patch(
            "app.features.coaching.service.mistral_client.generate",
            new=AsyncMock(return_value="Conseil Mistral"),
         ):
        response = await client.post(
            "/api/v1/coaching/analyze",
            json={"prompt": "Tu es coach tennis. Conseil court."},
            headers=auth(token),
        )
    assert response.status_code == 200
    data = response.json()
    assert data["content"] == "Conseil Mistral"


@pytest.mark.asyncio
async def test_analyze_mistral_unavailable_returns_503(client):
    token = make_token()
    with patch.object(settings, "mistral_api_key", "test-api-key"), \
         patch(
            "app.features.coaching.service.mistral_client.generate",
            new=AsyncMock(
                side_effect=SecondServeException("MISTRAL_UNAVAILABLE", "Mistral timeout after retry", 503)
            ),
         ):
        response = await client.post(
            "/api/v1/coaching/analyze",
            json={"prompt": "prompt"},
            headers=auth(token),
        )
    assert response.status_code == 503
    data = response.json()
    assert data["error_code"] == "MISTRAL_UNAVAILABLE"


@pytest.mark.asyncio
async def test_analyze_missing_api_key_returns_503(client):
    token = make_token()
    with patch.object(settings, "mistral_api_key", ""):
        response = await client.post(
            "/api/v1/coaching/analyze",
            json={"prompt": "prompt"},
            headers=auth(token),
        )
    assert response.status_code == 503
    data = response.json()
    assert data["error_code"] == "MISTRAL_NOT_CONFIGURED"


@pytest.mark.asyncio
async def test_analyze_requires_auth(client):
    response = await client.post(
        "/api/v1/coaching/analyze",
        json={"prompt": "prompt"},
    )
    assert response.status_code == 401
