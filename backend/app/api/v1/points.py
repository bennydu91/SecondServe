from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from app.core.database import get_db
from app.features.points.repository import PointRepository
from app.features.points.schemas import PointCreateRequest, PointResponse, PointsResponse
from app.features.points.service import PointService

router = APIRouter()


def get_point_service(db: AsyncSession = Depends(get_db)) -> PointService:
    return PointService(PointRepository(db))


@router.post("/{session_id}/points", response_model=PointResponse, status_code=201)
async def create_point(
    session_id: int,
    request: PointCreateRequest,
    service: PointService = Depends(get_point_service),
):
    return await service.create_point(session_id, request)


@router.get("/{session_id}/points", response_model=PointsResponse)
async def list_points(
    session_id: int,
    service: PointService = Depends(get_point_service),
):
    return await service.list_points(session_id)


@router.delete("/{session_id}/points/last", status_code=204)
async def delete_last_point(
    session_id: int,
    service: PointService = Depends(get_point_service),
):
    await service.delete_last_point(session_id)
