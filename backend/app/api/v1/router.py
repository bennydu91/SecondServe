import logging

from fastapi import APIRouter, Depends
from app.api.v1 import auth, sessions, profile, coaching, sync, notifications, work_axes, live_sharing
from app.core.security import verify_jwt

logger = logging.getLogger(__name__)

api_router = APIRouter()


@api_router.get("/health")
async def health():
    logger.debug("Health check called")
    return {"status": "ok"}


api_router.include_router(auth.router, prefix="/auth", tags=["auth"])
api_router.include_router(sessions.router, prefix="/sessions", tags=["sessions"], dependencies=[Depends(verify_jwt)])
api_router.include_router(profile.router, prefix="/profile", tags=["profile"], dependencies=[Depends(verify_jwt)])
api_router.include_router(coaching.router, prefix="/coaching", tags=["coaching"], dependencies=[Depends(verify_jwt)])
api_router.include_router(sync.router, prefix="/sync", tags=["sync"], dependencies=[Depends(verify_jwt)])
api_router.include_router(notifications.router, prefix="/notifications", tags=["notifications"], dependencies=[Depends(verify_jwt)])
api_router.include_router(work_axes.router, prefix="/work_axes", tags=["work_axes"], dependencies=[Depends(verify_jwt)])
api_router.include_router(live_sharing.router, prefix="/live", tags=["live_sharing"])
