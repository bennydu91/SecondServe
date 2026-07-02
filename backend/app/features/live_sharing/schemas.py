from typing import Literal, Optional
from pydantic import BaseModel


class SetResultSchema(BaseModel):
    games_a: int
    games_b: int


class CreateShareRequest(BaseModel):
    session_id: int


class CreateShareResponse(BaseModel):
    token: str
    url: str


class LiveScoreUpdateRequest(BaseModel):
    completed_sets: list[SetResultSchema] = []
    current_set_games_a: int
    current_set_games_b: int
    current_set_point_log: list[Literal["A", "B"]] = []
    current_game_points_a: str
    current_game_points_b: str
    tie_break_points_a: int
    tie_break_points_b: int
    is_tie_break: bool
    is_super_tie_break: bool
    is_match_over: bool
    match_winner: Optional[Literal["A", "B"]] = None
    player_a_name: str
    player_b_name: str
    surface: str
    tournament: Optional[str] = None
    competition_type: Optional[str] = None
    started_at: int


class LiveSnapshotResponse(BaseModel):
    status: Literal["WAITING", "LIVE", "ENDED"]
    completed_sets: list[SetResultSchema] = []
    current_set_games_a: int = 0
    current_set_games_b: int = 0
    current_set_point_log: list[Literal["A", "B"]] = []
    current_game_points_a: str = "ZERO"
    current_game_points_b: str = "ZERO"
    tie_break_points_a: int = 0
    tie_break_points_b: int = 0
    is_tie_break: bool = False
    is_super_tie_break: bool = False
    match_winner: Optional[Literal["A", "B"]] = None
    player_a_name: Optional[str] = None
    player_b_name: Optional[str] = None
    surface: Optional[str] = None
    tournament: Optional[str] = None
    competition_type: Optional[str] = None
    started_at: Optional[int] = None
