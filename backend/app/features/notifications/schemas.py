from pydantic import BaseModel
from typing import Optional


class PendingNotificationResponse(BaseModel):
    content: str


class NotFoundResponse(BaseModel):
    detail: str
