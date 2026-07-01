from sqlalchemy import Column, Integer, String
from app.core.database import Base


class PlayerProfile(Base):
    __tablename__ = "player_profiles"

    id = Column(Integer, primary_key=True, default=1)
    display_name = Column(String, nullable=True)
    club = Column(String, nullable=True)
    current_series = Column(String, nullable=True)
    current_points = Column(Integer, nullable=True)
    updated_at = Column(Integer, nullable=False)
    play_style = Column(String, nullable=True)
    preferred_surfaces = Column(String, nullable=True)
    coach_instruction_1 = Column(String, nullable=True)
    coach_instruction_2 = Column(String, nullable=True)
    coach_instruction_3 = Column(String, nullable=True)


class RankingHistory(Base):
    __tablename__ = "ranking_history"

    id = Column(Integer, primary_key=True, autoincrement=True)
    series = Column(String, nullable=False)
    points = Column(Integer, nullable=False)
    recorded_at = Column(Integer, nullable=False)
    updated_at = Column(Integer, nullable=False)
