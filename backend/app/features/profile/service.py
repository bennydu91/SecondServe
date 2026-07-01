from app.features.profile.repository import ProfileRepository
from app.features.profile.schemas import (
    RankingRequest, RankingResponse, ProfileSummaryResponse,
    ProfileDetailsRequest, ProfileDetailsResponse
)


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
            display_name=profile.display_name if profile else None,
            club=profile.club if profile else None,
            current_series=profile.current_series if profile else None,
            current_points=profile.current_points if profile else None,
            ranking_history=[
                RankingResponse(id=e.id, series=e.series, points=e.points, recorded_at=e.recorded_at)
                for e in history
            ],
            play_style=profile.play_style if profile else None,
            preferred_surfaces=profile.preferred_surfaces if profile else None,
            coach_instruction_1=profile.coach_instruction_1 if profile else None,
            coach_instruction_2=profile.coach_instruction_2 if profile else None,
            coach_instruction_3=profile.coach_instruction_3 if profile else None
        )

    async def update_profile_details(self, request: ProfileDetailsRequest) -> ProfileDetailsResponse:
        profile = await self.repository.update_profile_details(
            request.display_name,
            request.club,
            request.play_style,
            request.preferred_surfaces,
            request.coach_instruction_1,
            request.coach_instruction_2,
            request.coach_instruction_3
        )
        return ProfileDetailsResponse(updated_at=profile.updated_at)
