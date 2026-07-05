"""add client_id column to sessions table

Revision ID: c1d2e3f4a5b6
Revises: b4c5d6e7f8a9
Create Date: 2026-07-05
"""
from alembic import op
import sqlalchemy as sa

revision = 'c1d2e3f4a5b6'
down_revision = 'b4c5d6e7f8a9'
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("sessions", sa.Column("client_id", sa.Integer(), nullable=True))
    op.create_index("idx_sessions_client_id", "sessions", ["client_id"])


def downgrade() -> None:
    op.drop_index("idx_sessions_client_id", table_name="sessions")
    op.drop_column("sessions", "client_id")
