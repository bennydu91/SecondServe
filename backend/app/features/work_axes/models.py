from sqlalchemy import Column, Integer, String
from app.core.database import Base


class WorkAxis(Base):
    __tablename__ = "work_axes"

    id = Column(Integer, primary_key=True, autoincrement=True)
    title = Column(String, nullable=False)
    created_at = Column(Integer, nullable=False)  # epoch ms
    updated_at = Column(Integer, nullable=False)  # epoch ms
