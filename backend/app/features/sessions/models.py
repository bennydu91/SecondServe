from sqlalchemy import Column, Integer, String
from app.core.database import Base


class SessionModel(Base):
    __tablename__ = "sessions"

    id = Column(Integer, primary_key=True, autoincrement=True)
    surface = Column(String, nullable=False)
    match_format = Column(String, nullable=False)
    third_set_rule = Column(String, nullable=False)
    opponent = Column(String, nullable=True)
    competition_type = Column(String, nullable=True)
    tournament = Column(String, nullable=True)
    status = Column(String, nullable=False, default="ACTIVE")
    session_type = Column(String, nullable=False, default="MATCH")
    result = Column(String, nullable=True)
    created_at = Column(Integer, nullable=False)
    updated_at = Column(Integer, nullable=False)
