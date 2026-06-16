from app.features.profile.repository import ProfileRepository
from app.features.profile.schemas import RankingRequest, RankingResponse, ProfileSummaryResponse


class ProfileService:
    def __init__(self, repository: ProfileRepository):
        self.repository = repository

    async def save_ranking(self, request: RankingRequest) -> RankingResponse:
        await self.repository.upsert_profile_ranking(request.series, request.points)
        entry = await self.repository.insert_ranking_history(request.series, request.points)
        return RankingResponse(id=entry.id, series=entry.series, points=entry.points, recorded_at=entry.recorded_at)

    async def get_profile_summary(self) -> ProfileSummaryResponse:
        profile = await self.repository.get_profile()
        history = await self.repository.get_ranking_history()
        return ProfileSummaryResponse(
            current_series=profile.current_series if profile else None,
            current_points=profile.current_points if profile else None,
            ranking_history=[
                RankingResponse(id=e.id, series=e.series, points=e.points, recorded_at=e.recorded_at)
                for e in history
            ]
        )
