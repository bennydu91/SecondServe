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


class SessionUpdateRequest(BaseModel):
    surface: Optional[Literal["CLAY", "GRASS", "HARD", "CARPET"]] = None
    match_format: Optional[Literal["BEST_OF_1", "BEST_OF_3"]] = None
    third_set_rule: Optional[Literal["FULL_ADVANTAGE", "SUPER_TIE_BREAK_10", "SHORT_DECISIVE_SET"]] = None
    opponent: Optional[str] = None
    competition_type: Optional[str] = None
    tournament: Optional[str] = None
    status: Optional[Literal["ACTIVE", "COMPLETED"]] = None
    result: Optional[Literal["VICTORY", "DEFEAT"]] = None
    score_text: Optional[str] = None
    created_at: Optional[int] = None
    updated_at: Optional[int] = None


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
    score_text: Optional[str] = None
    score_seed_json: Optional[str] = None
    created_at: int
    updated_at: int

    model_config = {"from_attributes": True}


class SessionsResponse(BaseModel):
    items: list[SessionResponse]
    total: int


class SetResultSchema(BaseModel):
    games_a: int
    games_b: int


class ScoreSeedRequest(BaseModel):
    completed_sets: list[SetResultSchema] = []
    current_set_games_a: int = 0
    current_set_games_b: int = 0
    current_game_points_a: Literal["ZERO", "FIFTEEN", "THIRTY", "FORTY", "ADVANTAGE"] = "ZERO"
    current_game_points_b: Literal["ZERO", "FIFTEEN", "THIRTY", "FORTY", "ADVANTAGE"] = "ZERO"
    tie_break_points_a: int = 0
    tie_break_points_b: int = 0
    is_tie_break: bool = False
    is_super_tie_break: bool = False
