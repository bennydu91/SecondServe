import logging

from fastapi import APIRouter
from app.api.v1 import auth, sessions, profile, coaching, sync, notifications

logger = logging.getLogger(__name__)

api_router = APIRouter()


@api_router.get("/health")
async def health():
    logger.debug("Health check called")
    return {"status": "ok"}


api_router.include_router(auth.router, prefix="/auth", tags=["auth"])
api_router.include_router(sessions.router, prefix="/sessions", tags=["sessions"])
api_router.include_router(profile.router, prefix="/profile", tags=["profile"])
api_router.include_router(coaching.router, prefix="/coaching", tags=["coaching"])
api_router.include_router(sync.router, prefix="/sync", tags=["sync"])
api_router.include_router(notifications.router, prefix="/notifications", tags=["notifications"])
