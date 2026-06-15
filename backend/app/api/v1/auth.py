from fastapi import APIRouter, Depends
from pydantic import BaseModel
from app.core.security import JWTManager, verify_jwt
from app.core.config import settings

router = APIRouter()


class TokenResponse(BaseModel):
    token: str


@router.post("/init")
async def init_auth() -> TokenResponse:
    """
    Initialize JWT authentication.
    Called once per client on first launch.
    Returns a signed JWT token.
    """
    manager = JWTManager(settings.jwt_secret)
    token = manager.create_token()
    return TokenResponse(token=token)


@router.get("/verify")
async def verify_auth(token_payload: dict = Depends(verify_jwt)) -> dict:
    """
    Verify JWT token. Protected route for testing JWT validation.
    """
    return {"message": "Token is valid", "payload": token_payload}
