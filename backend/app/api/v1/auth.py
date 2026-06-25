# backend/app/api/v1/auth.py
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from app.core.security import JWTManager, verify_jwt
from app.core.config import settings
from app.core.google_auth import verify_google_id_token
import jwt

router = APIRouter()


class GoogleAuthRequest(BaseModel):
    google_id_token: str


class TokenResponse(BaseModel):
    token: str


@router.post("/init")
async def init_auth(request: GoogleAuthRequest) -> TokenResponse:
    try:
        payload = await verify_google_id_token(request.google_id_token, settings.google_client_id)
    except jwt.InvalidTokenError:
        raise HTTPException(status_code=401, detail="Invalid Google token")
    except Exception:
        raise HTTPException(status_code=503, detail="Authentication service unavailable")

    if not payload.get("email_verified") or payload.get("email") != settings.authorized_email:
        raise HTTPException(status_code=403, detail="Unauthorized")

    manager = JWTManager(settings.jwt_secret)
    token = manager.create_token()
    return TokenResponse(token=token)


@router.get("/verify")
async def verify_auth(token_payload: dict = Depends(verify_jwt)) -> dict:
    return {"message": "Token is valid"}
