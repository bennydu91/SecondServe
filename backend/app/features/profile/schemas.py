from pydantic import BaseModel, field_validator
from typing import Optional

FFT_VALID_SERIES = [
    "40", "30/5", "30/4", "30/3", "30/2", "30/1",
    "15/5", "15/4", "15/3", "15/2", "15/1",
    "4/6", "3/6", "2/6", "1/6"
]


class RankingRequest(BaseModel):
    series: str
    points: int

    @field_validator("series")
    @classmethod
    def validate_series(cls, v: str) -> str:
        if v not in FFT_VALID_SERIES:
            raise ValueError(f"Série FFT invalide : {v}. Valeurs acceptées : {FFT_VALID_SERIES}")
        return v

    @field_validator("points")
    @classmethod
    def validate_points(cls, v: int) -> int:
        if v <= 0:
            raise ValueError("Le nombre de points doit être un entier positif")
        return v


class RankingResponse(BaseModel):
    id: int
    series: str
    points: int
    recorded_at: int

    model_config = {"from_attributes": True}


class ProfileSummaryResponse(BaseModel):
    current_series: Optional[str] = None
    current_points: Optional[int] = None
    ranking_history: list[RankingResponse]

    model_config = {"from_attributes": True}
