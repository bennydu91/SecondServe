from fastapi import APIRouter
from app.core.config import settings
from app.features.coaching import service
from app.features.coaching.schemas import AnalyzeRequest, AnalyzeResponse

router = APIRouter()


@router.post("/analyze", response_model=AnalyzeResponse)
async def analyze(request: AnalyzeRequest) -> AnalyzeResponse:
    content = await service.analyze(request.prompt, settings.mistral_api_key)
    return AnalyzeResponse(content=content)
