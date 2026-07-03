from sqlalchemy import Column, Integer, String, ForeignKey
from app.core.database import Base


class PointModel(Base):
    __tablename__ = "points"

    id = Column(Integer, primary_key=True, autoincrement=True)
    session_id = Column(Integer, ForeignKey("sessions.id", ondelete="CASCADE"), nullable=False, index=True)
    scorer = Column(String, nullable=False)
    context = Column(String, nullable=True)
    sequence_num = Column(Integer, nullable=False)
    recorded_at = Column(Integer, nullable=False)
