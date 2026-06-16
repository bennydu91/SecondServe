from pydantic import BaseModel, Field, field_validator
from typing import Optional

FFT_VALID_SERIES = [
    "40", "30/5", "30/4", "30/3", "30/2", "30/1",
    "15/5", "15/4", "15/3", "15/2", "15/1",
    "4/6", "3/6", "2/6", "1/6"
]

PLAY_STYLE_VALUES = ["DEFENSIVE", "OFFENSIVE", "COUNTERPUNCHER", "ALL_COURT"]
VALID_SURFACES = {"CLAY", "GRASS", "HARD", "CARPET"}


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


class ProfileDetailsRequest(BaseModel):
    play_style: Optional[str] = None
    preferred_surfaces: Optional[str] = None
    coach_instruction_1: Optional[str] = Field(None, max_length=500)
    coach_instruction_2: Optional[str] = Field(None, max_length=500)
    coach_instruction_3: Optional[str] = Field(None, max_length=500)

    @field_validator("play_style")
    @classmethod
    def validate_play_style(cls, v: str | None) -> str | None:
        if v is not None and v not in PLAY_STYLE_VALUES:
            raise ValueError(f"Style invalide : {v}. Valeurs acceptées : {PLAY_STYLE_VALUES}")
        return v

    @field_validator("preferred_surfaces")
    @classmethod
    def validate_preferred_surfaces(cls, v: str | None) -> str | None:
        if v is None:
            return v
        surfaces = list(dict.fromkeys(s.strip() for s in v.split(",") if s.strip()))
        if not surfaces:
            return None
        invalid = [s for s in surfaces if s not in VALID_SURFACES]
        if invalid:
            raise ValueError(
                f"Surface(s) invalide(s) : {invalid}. Valeurs acceptées : {sorted(VALID_SURFACES)}"
            )
        return ",".join(surfaces)


class ProfileDetailsResponse(BaseModel):
    updated_at: int
    model_config = {"from_attributes": True}


class ProfileSummaryResponse(BaseModel):
    current_series: Optional[str] = None
    current_points: Optional[int] = None
    ranking_history: list[RankingResponse] = []
    play_style: Optional[str] = None
    preferred_surfaces: Optional[str] = None
    coach_instruction_1: Optional[str] = None
    coach_instruction_2: Optional[str] = None
    coach_instruction_3: Optional[str] = None

    model_config = {"from_attributes": True}
