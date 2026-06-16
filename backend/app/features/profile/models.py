from sqlalchemy import Column, Integer, String
from app.core.database import Base


class PlayerProfile(Base):
    __tablename__ = "player_profiles"

    id = Column(Integer, primary_key=True, default=1)
    current_series = Column(String, nullable=True)
    current_points = Column(Integer, nullable=True)
    updated_at = Column(Integer, nullable=False)


class RankingHistory(Base):
    __tablename__ = "ranking_history"

    id = Column(Integer, primary_key=True, autoincrement=True)
    series = Column(String, nullable=False)
    points = Column(Integer, nullable=False)
    recorded_at = Column(Integer, nullable=False)
    updated_at = Column(Integer, nullable=False)
