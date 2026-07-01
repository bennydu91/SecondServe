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
    score_text = Column(String, nullable=True)
    feeling_rating = Column(Integer, nullable=True)
    feeling_comment = Column(String, nullable=True)
    created_at = Column(Integer, nullable=False)
    updated_at = Column(Integer, nullable=False)
    scheduled_at = Column(Integer, nullable=True)
    first_serve_percent_self = Column(Integer, nullable=True)
    first_serve_percent_opponent = Column(Integer, nullable=True)
    winners_self = Column(Integer, nullable=True)
    winners_opponent = Column(Integer, nullable=True)
