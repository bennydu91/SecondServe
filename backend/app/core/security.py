from datetime import datetime, timedelta, timezone
import jwt
from fastapi import HTTPException, Request
from app.core.config import settings


class JWTManager:
    ALGORITHM = "HS256"

    def __init__(self, secret: str):
        if len(secret) < 32:
            raise ValueError("JWT_SECRET must be at least 32 characters")
        self.secret = secret

    def create_token(self, expires_delta: timedelta | None = None) -> str:
        if expires_delta is None:
            expires_delta = timedelta(days=30)

        now = datetime.now(timezone.utc)
        payload = {
            "exp": int((now + expires_delta).timestamp()),
            "iat": int(now.timestamp()),
        }
        return jwt.encode(payload, self.secret, algorithm=self.ALGORITHM)

    def verify_token(self, token: str) -> dict:
        try:
            return jwt.decode(token, self.secret, algorithms=[self.ALGORITHM])
        except jwt.ExpiredSignatureError:
            raise HTTPException(status_code=401, detail="Token expired")
        except jwt.InvalidTokenError:
            raise HTTPException(status_code=401, detail="Invalid token")
        except Exception:
            raise HTTPException(status_code=500, detail="Token verification error")


async def verify_jwt(request: Request) -> dict:
    """Dependency for protected routes"""
    auth_header = request.headers.get("Authorization")
    if not auth_header or not auth_header.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing Authorization header")

    token = auth_header.split(" ", 1)[1]
    if not token:
        raise HTTPException(status_code=401, detail="Missing token")

    manager = JWTManager(settings.jwt_secret)
    return manager.verify_token(token)
