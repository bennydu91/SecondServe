from typing import Literal, Optional
from pydantic import BaseModel

PointContext = Literal[
    "ACE",
    "WINNER",
    "FORCED_ERROR",
    "UNFORCED_ERROR_OPPONENT",
    "ACE_OPPONENT",
    "WINNER_OPPONENT",
    "UNFORCED_ERROR_SELF",
    "DOUBLE_FAULT",
]


class PointCreateRequest(BaseModel):
    context: PointContext


class PointResponse(BaseModel):
    id: int
    session_id: int
    scorer: str
    context: Optional[str] = None
    sequence_num: int
    recorded_at: int

    model_config = {"from_attributes": True}


class PointsResponse(BaseModel):
    items: list[PointResponse]
