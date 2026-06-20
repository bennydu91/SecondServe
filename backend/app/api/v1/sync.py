from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import verify_jwt
from app.features.sync.schemas import SyncPushRequest, SyncPushResponse
from app.features.sync.service import SyncService

router = APIRouter()


@router.post("/push", response_model=SyncPushResponse)
async def sync_push(
    request: SyncPushRequest,
    db: AsyncSession = Depends(get_db),
    _: str = Depends(verify_jwt),
) -> SyncPushResponse:
    service = SyncService(db)
    return await service.push(request)
