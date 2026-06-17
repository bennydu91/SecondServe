from pydantic import BaseModel
from typing import Optional


class SessionCreateRequest(BaseModel):
    surface: str
    match_format: str
    third_set_rule: str
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
