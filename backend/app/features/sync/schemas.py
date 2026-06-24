from pydantic import BaseModel
from typing import Optional


class SyncSessionDto(BaseModel):
    client_id: int
    surface: str
    match_format: str
    third_set_rule: str
    opponent: Optional[str] = None
    competition_type: Optional[str] = None
    tournament: Optional[str] = None
    status: str
    session_type: str
    result: Optional[str] = None
    feeling_rating: Optional[int] = None
    feeling_comment: Optional[str] = None
    created_at: int
    updated_at: int
    scheduled_at: Optional[int] = None


class SyncPushRequest(BaseModel):
    sessions: list[SyncSessionDto]
    deleted_session_ids: list[int] = []


class SyncPushResponse(BaseModel):
    synced_sessions: int
