import base64
import json
import httpx
import jwt
from jwt.algorithms import RSAAlgorithm

GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs"
GOOGLE_ISSUERS = ["https://accounts.google.com", "accounts.google.com"]


def _decode_header(id_token: str) -> dict:
    """Decode only the header segment without validating the full token."""
    try:
        header_segment = id_token.split(".")[0]
        # Add padding if necessary
        padding = 4 - len(header_segment) % 4
        if padding != 4:
            header_segment += "=" * padding
        header_bytes = base64.urlsafe_b64decode(header_segment)
        return json.loads(header_bytes)
    except Exception as exc:
        raise jwt.InvalidTokenError(f"Invalid token header: {exc}") from exc


async def verify_google_id_token(id_token: str, client_id: str) -> dict:
    header = _decode_header(id_token)
    kid = header.get("kid")

    async with httpx.AsyncClient() as client:
        response = await client.get(GOOGLE_JWKS_URL)
        response.raise_for_status()
        jwks = response.json()

    signing_key = None
    for jwk_data in jwks["keys"]:
        if jwk_data.get("kid") == kid:
            signing_key = RSAAlgorithm.from_jwk(jwk_data)
            break

    if signing_key is None:
        raise jwt.InvalidTokenError("No matching signing key")

    return jwt.decode(
        id_token,
        signing_key,
        algorithms=["RS256"],
        audience=client_id,
        issuer=GOOGLE_ISSUERS,
    )
