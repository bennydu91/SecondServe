import time

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.features.notifications.models import PendingNotificationModel
from app.features.notifications.schemas import PendingNotificationResponse

router = APIRouter()


@router.get("/pending", response_model=PendingNotificationResponse)
async def get_pending_notification(
    session_id: int = Query(...),
    db: AsyncSession = Depends(get_db),
) -> PendingNotificationResponse:
    now_ms = int(time.time() * 1000)
    result = await db.execute(
        select(PendingNotificationModel).where(
            PendingNotificationModel.session_id == session_id,
            PendingNotificationModel.expires_at >= now_ms,
        )
    )
    pending = result.scalar_one_or_none()
    if pending is None:
        raise HTTPException(
            status_code=404,
            detail="Aucune notification pré-match disponible pour cette session",
        )
    return PendingNotificationResponse(content=pending.content)
