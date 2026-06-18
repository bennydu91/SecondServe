from pydantic import BaseModel
from typing import Literal, Optional


class SessionCreateRequest(BaseModel):
    surface: Literal["CLAY", "GRASS", "HARD", "CARPET"]
    match_format: Literal["BEST_OF_1", "BEST_OF_3"]
    third_set_rule: Literal["FULL_ADVANTAGE", "SUPER_TIE_BREAK_10", "SHORT_DECISIVE_SET"]
    opponent: Optional[str] = None
    competition_type: Optional[str] = None
    tournament: Optional[str] = None
    created_at: int


class SessionResponse(BaseModel):
    id: int
    surface: str
    match_format: str
    third_set_rule: str
    opponent: Optional[str] = None
    competition_type: Optional[str] = None
    tournament: Optional[str] = None
    status: str
    session_type: str
    result: Optional[str] = None
    created_at: int
    updated_at: int

    model_config = {"from_attributes": True}


class SessionsResponse(BaseModel):
    items: list[SessionResponse]
    total: int
