import logging
import time
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, desc
from app.features.profile.models import PlayerProfile, RankingHistory

logger = logging.getLogger(__name__)


class ProfileRepository:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_profile(self) -> PlayerProfile | None:
        result = await self.db.execute(select(PlayerProfile).where(PlayerProfile.id == 1))
        return result.scalar_one_or_none()

    async def upsert_profile_ranking(self, series: str, points: int) -> PlayerProfile:
        now = int(time.time() * 1000)
        profile = await self.get_profile()
        if profile:
            profile.current_series = series
            profile.current_points = points
            profile.updated_at = now
        else:
            profile = PlayerProfile(id=1, current_series=series, current_points=points, updated_at=now)
            self.db.add(profile)
        await self.db.flush()
        return profile

    async def insert_ranking_history(self, series: str, points: int) -> RankingHistory:
        now = int(time.time() * 1000)
        entry = RankingHistory(series=series, points=points, recorded_at=now, updated_at=now)
        self.db.add(entry)
        await self.db.flush()
        return entry

    async def get_ranking_history(self) -> list[RankingHistory]:
        result = await self.db.execute(
            select(RankingHistory).order_by(desc(RankingHistory.recorded_at))
        )
        return list(result.scalars().all())

    async def update_profile_details(
        self,
        display_name: str | None,
        club: str | None,
        play_style: str | None,
        preferred_surfaces: str | None,
        coach_instruction_1: str | None,
        coach_instruction_2: str | None,
        coach_instruction_3: str | None
    ) -> PlayerProfile:
        now = int(time.time() * 1000)
        profile = await self.get_profile()
        if profile:
            profile.display_name = display_name
            profile.club = club
            profile.play_style = play_style
            profile.preferred_surfaces = preferred_surfaces
            profile.coach_instruction_1 = coach_instruction_1
            profile.coach_instruction_2 = coach_instruction_2
            profile.coach_instruction_3 = coach_instruction_3
            profile.updated_at = now
        else:
            profile = PlayerProfile(
                id=1, display_name=display_name, club=club,
                play_style=play_style, preferred_surfaces=preferred_surfaces,
                coach_instruction_1=coach_instruction_1, coach_instruction_2=coach_instruction_2,
                coach_instruction_3=coach_instruction_3, updated_at=now
            )
            self.db.add(profile)
        await self.db.flush()
        return profile
