from sqlalchemy import Column, Integer, String, ForeignKey
from app.core.database import Base


class PendingNotificationModel(Base):
    __tablename__ = "pending_notifications"

    id = Column(Integer, primary_key=True, autoincrement=True)
    session_id = Column(Integer, ForeignKey("sessions.id", ondelete="CASCADE"), nullable=False, unique=True)
    content = Column(String, nullable=False)
    generated_at = Column(Integer, nullable=False)
    expires_at = Column(Integer, nullable=False)
