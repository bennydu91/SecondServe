from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from app.core.database import get_db
from app.features.profile.repository import ProfileRepository
from app.features.profile.service import ProfileService
from app.features.profile.schemas import (
    RankingRequest, RankingResponse, ProfileSummaryResponse,
    ProfileDetailsRequest, ProfileDetailsResponse
)

router = APIRouter()


async def get_profile_service(db: AsyncSession = Depends(get_db)) -> ProfileService:
    return ProfileService(ProfileRepository(db))


@router.get("", response_model=ProfileSummaryResponse)
async def get_profile(
    service: ProfileService = Depends(get_profile_service)
) -> ProfileSummaryResponse:
    return await service.get_profile_summary()


@router.post("/ranking", response_model=RankingResponse, status_code=201)
async def save_ranking(
    request: RankingRequest,
    service: ProfileService = Depends(get_profile_service)
) -> RankingResponse:
    return await service.save_ranking(request)


@router.put("/details", response_model=ProfileDetailsResponse)
async def update_profile_details(
    request: ProfileDetailsRequest,
    service: ProfileService = Depends(get_profile_service)
) -> ProfileDetailsResponse:
    return await service.update_profile_details(request)
