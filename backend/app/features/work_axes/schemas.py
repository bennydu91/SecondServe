from pydantic import BaseModel, field_validator

MAX_WORK_AXES = 3


class WorkAxisRequest(BaseModel):
    title: str
    created_at: int  # epoch ms

    @field_validator("title")
    @classmethod
    def validate_title(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("Le titre ne peut pas être vide")
        if len(v) > 200:
            raise ValueError("Le titre ne peut pas dépasser 200 caractères")
        return v


class WorkAxisResponse(BaseModel):
    id: int
    title: str
    created_at: int
    updated_at: int
    model_config = {"from_attributes": True}


class WorkAxesResponse(BaseModel):
    items: list[WorkAxisResponse]
    total: int
