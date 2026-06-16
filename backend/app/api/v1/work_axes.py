import logging
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from app.core.database import get_db
from app.features.work_axes.repository import WorkAxisRepository
from app.features.work_axes.schemas import WorkAxisRequest, WorkAxisResponse, WorkAxesResponse
from app.features.work_axes.service import WorkAxisService

logger = logging.getLogger(__name__)

router = APIRouter()


def get_work_axis_service(db: AsyncSession = Depends(get_db)) -> WorkAxisService:
    return WorkAxisService(WorkAxisRepository(db))


@router.get("", response_model=WorkAxesResponse)
async def list_work_axes(service: WorkAxisService = Depends(get_work_axis_service)):
    return await service.get_all()


@router.post("", response_model=WorkAxisResponse, status_code=201)
async def create_work_axis(
    request: WorkAxisRequest,
    service: WorkAxisService = Depends(get_work_axis_service)
):
    return await service.create(request)


@router.put("/{axis_id}", response_model=WorkAxisResponse)
async def update_work_axis(
    axis_id: int,
    request: WorkAxisRequest,
    service: WorkAxisService = Depends(get_work_axis_service)
):
    return await service.update(axis_id, request)


@router.delete("/{axis_id}", status_code=204)
async def delete_work_axis(
    axis_id: int,
    service: WorkAxisService = Depends(get_work_axis_service)
):
    await service.delete(axis_id)
