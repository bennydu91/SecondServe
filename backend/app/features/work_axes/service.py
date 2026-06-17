import logging
from fastapi import HTTPException
from app.features.work_axes.repository import WorkAxisRepository
from app.features.work_axes.schemas import (
    WorkAxisRequest, WorkAxisResponse, WorkAxesResponse, MAX_WORK_AXES
)

logger = logging.getLogger(__name__)


class WorkAxisService:
    def __init__(self, repository: WorkAxisRepository):
        self.repository = repository

    async def get_all(self) -> WorkAxesResponse:
        axes = await self.repository.get_all()
        items = [WorkAxisResponse.model_validate(a) for a in axes]
        return WorkAxesResponse(items=items, total=len(items))

    async def create(self, request: WorkAxisRequest) -> WorkAxisResponse:
        count = await self.repository.count()
        if count >= MAX_WORK_AXES:
            raise HTTPException(
                status_code=422,
                detail={
                    "error_code": "MAX_WORK_AXES_REACHED",
                    "message": f"Maximum {MAX_WORK_AXES} axes actifs atteint"
                }
            )
        axis = await self.repository.create(request.title, request.created_at)
        return WorkAxisResponse.model_validate(axis)

    async def update(self, axis_id: int, request: WorkAxisRequest) -> WorkAxisResponse:
        axis = await self.repository.update(axis_id, request.title)
        if not axis:
            raise HTTPException(
                status_code=404,
                detail={"error_code": "WORK_AXIS_NOT_FOUND", "message": "Axe non trouvé"}
            )
        return WorkAxisResponse.model_validate(axis)

    async def delete(self, axis_id: int) -> None:
        deleted = await self.repository.delete(axis_id)
        if not deleted:
            raise HTTPException(
                status_code=404,
                detail={"error_code": "WORK_AXIS_NOT_FOUND", "message": "Axe non trouvé"}
            )
