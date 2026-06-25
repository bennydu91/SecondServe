import json
import time
import pytest
from unittest.mock import patch, AsyncMock, MagicMock
import jwt
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives import serialization
from jwt.algorithms import RSAAlgorithm
from app.core.google_auth import verify_google_id_token


@pytest.mark.asyncio
async def test_verify_valid_google_token_returns_payload():
    # Générer une paire RSA locale
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    public_key = private_key.public_key()

    # Construire un JWKS à partir de la clé publique
    public_jwk = json.loads(RSAAlgorithm.to_jwk(public_key))
    public_jwk["kid"] = "test-key-id"
    public_jwk["alg"] = "RS256"
    public_jwk["use"] = "sig"
    mock_jwks = {"keys": [public_jwk]}

    # Signer un token valide
    now = int(time.time())
    payload = {
        "iss": "https://accounts.google.com",
        "aud": "test-client-id",
        "email": "ben.finot@gmail.com",
        "email_verified": True,
        "sub": "1234567890",
        "iat": now,
        "exp": now + 3600,
    }
    id_token = jwt.encode(payload, private_key, algorithm="RS256", headers={"kid": "test-key-id"})

    # Mocker le fetch JWKS
    mock_response = MagicMock()
    mock_response.json.return_value = mock_jwks
    mock_response.raise_for_status = MagicMock()
    mock_client = AsyncMock()
    mock_client.get.return_value = mock_response

    with patch("app.core.google_auth.httpx.AsyncClient") as mock_cls:
        mock_cls.return_value.__aenter__ = AsyncMock(return_value=mock_client)
        mock_cls.return_value.__aexit__ = AsyncMock(return_value=False)
        result = await verify_google_id_token(id_token, "test-client-id")

    assert result["email"] == "ben.finot@gmail.com"
    assert result["email_verified"] is True


@pytest.mark.asyncio
async def test_verify_rejects_wrong_audience():
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    public_key = private_key.public_key()
    public_jwk = json.loads(RSAAlgorithm.to_jwk(public_key))
    public_jwk["kid"] = "test-key-id"
    mock_jwks = {"keys": [public_jwk]}

    now = int(time.time())
    payload = {
        "iss": "https://accounts.google.com",
        "aud": "wrong-client-id",  # Mauvais audience
        "email": "ben.finot@gmail.com",
        "email_verified": True,
        "sub": "1234567890",
        "iat": now,
        "exp": now + 3600,
    }
    id_token = jwt.encode(payload, private_key, algorithm="RS256", headers={"kid": "test-key-id"})

    mock_response = MagicMock()
    mock_response.json.return_value = mock_jwks
    mock_response.raise_for_status = MagicMock()
    mock_client = AsyncMock()
    mock_client.get.return_value = mock_response

    with patch("app.core.google_auth.httpx.AsyncClient") as mock_cls:
        mock_cls.return_value.__aenter__ = AsyncMock(return_value=mock_client)
        mock_cls.return_value.__aexit__ = AsyncMock(return_value=False)
        with pytest.raises(jwt.InvalidTokenError):
            await verify_google_id_token(id_token, "test-client-id")


@pytest.mark.asyncio
async def test_verify_raises_invalid_token_when_no_matching_kid():
    mock_response = MagicMock()
    mock_response.json.return_value = {"keys": []}
    mock_response.raise_for_status = MagicMock()

    mock_client = AsyncMock()
    mock_client.get.return_value = mock_response

    with patch("app.core.google_auth.httpx.AsyncClient") as mock_cls:
        mock_cls.return_value.__aenter__ = AsyncMock(return_value=mock_client)
        mock_cls.return_value.__aexit__ = AsyncMock(return_value=False)

        with pytest.raises(jwt.InvalidTokenError, match="No matching signing key"):
            # Token with kid "unknown" — aucune clé correspondante dans le JWKS vide
            import base64, json as _json
            header = base64.urlsafe_b64encode(
                _json.dumps({"alg": "RS256", "kid": "unknown"}).encode()
            ).rstrip(b"=").decode()
            fake_token = f"{header}.payload.signature"
            await verify_google_id_token(fake_token, "test-client-id")
