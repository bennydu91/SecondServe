# backend/app/features/monitoring/models.py
from datetime import datetime
from sqlalchemy import Column, Integer, String, DateTime

from app.features.monitoring.database import MonitoringBase


class RequestLog(MonitoringBase):
    __tablename__ = "request_logs"

    id = Column(Integer, primary_key=True, autoincrement=True)
    timestamp = Column(DateTime, nullable=False, default=datetime.utcnow)
    method = Column(String, nullable=False)
    path = Column(String, nullable=False)
    status_code = Column(Integer, nullable=False)
    response_time = Column(Integer, nullable=False)  # ms
    ip = Column(String, nullable=True)


class ErrorLog(MonitoringBase):
    __tablename__ = "error_logs"

    id = Column(Integer, primary_key=True, autoincrement=True)
    timestamp = Column(DateTime, nullable=False, default=datetime.utcnow)
    level = Column(String, nullable=False)
    logger = Column(String, nullable=False)
    message = Column(String, nullable=False)
    traceback = Column(String, nullable=True)


class BusinessEvent(MonitoringBase):
    __tablename__ = "business_events"

    id = Column(Integer, primary_key=True, autoincrement=True)
    timestamp = Column(DateTime, nullable=False, default=datetime.utcnow)
    event_type = Column(String, nullable=False)
    payload = Column(String, nullable=False)   # JSON string
    source = Column(String, nullable=False, default="backend")  # backend | android | wear
