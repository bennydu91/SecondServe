"""add score_text column to sessions table

Revision ID: a7b8c9d0e1f2
Revises: f6a7b8c9d0e1
Create Date: 2026-06-24
"""
from alembic import op
import sqlalchemy as sa

revision = 'a7b8c9d0e1f2'
down_revision = 'f6a7b8c9d0e1'
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("sessions", sa.Column("score_text", sa.String(), nullable=True))


def downgrade() -> None:
    op.drop_column("sessions", "score_text")
