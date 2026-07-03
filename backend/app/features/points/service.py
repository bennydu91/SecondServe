from app.features.points.repository import PointRepository
from app.features.points.schemas import PointCreateRequest, PointResponse, PointsResponse

SCORER_BY_CONTEXT = {
    "ACE": "A",
    "WINNER": "A",
    "FORCED_ERROR": "A",
    "UNFORCED_ERROR_OPPONENT": "A",
    "ACE_OPPONENT": "B",
    "WINNER_OPPONENT": "B",
    "UNFORCED_ERROR_SELF": "B",
    "DOUBLE_FAULT": "B",
}


class PointService:
    def __init__(self, repository: PointRepository):
        self.repository = repository

    async def create_point(self, session_id: int, request: PointCreateRequest) -> PointResponse:
        scorer = SCORER_BY_CONTEXT[request.context]
        point = await self.repository.create(session_id, scorer, request.context)
        return PointResponse.model_validate(point)

    async def list_points(self, session_id: int) -> PointsResponse:
        points = await self.repository.get_all_for_session(session_id)
        return PointsResponse(items=[PointResponse.model_validate(p) for p in points])

    async def delete_last_point(self, session_id: int) -> None:
        await self.repository.delete_last(session_id)
