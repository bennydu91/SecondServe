import pytest
from unittest.mock import patch, AsyncMock, MagicMock
import jwt
from app.core.google_auth import verify_google_id_token


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
