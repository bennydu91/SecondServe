from sqlalchemy import Column, Integer, String, Text
from app.core.database import Base


class MatchShareModel(Base):
    __tablename__ = "match_shares"

    id = Column(Integer, primary_key=True, autoincrement=True)
    token = Column(String, nullable=False, unique=True, index=True)
    session_id = Column(Integer, nullable=False, unique=True, index=True)
    created_at = Column(Integer, nullable=False)  # epoch ms
    expires_at = Column(Integer, nullable=True)  # epoch ms, null tant que le match est en cours
    score_snapshot = Column(Text, nullable=True)  # JSON — dernier état connu (score + contexte)
