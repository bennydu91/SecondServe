"""add fine match stats columns to sessions table

Revision ID: d0e1f2a3b4c5
Revises: c9d0e1f2a3b4
Create Date: 2026-07-01
"""
from alembic import op
import sqlalchemy as sa

revision = 'd0e1f2a3b4c5'
down_revision = 'c9d0e1f2a3b4'
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("sessions", sa.Column("first_serve_percent_self", sa.Integer(), nullable=True))
    op.add_column("sessions", sa.Column("first_serve_percent_opponent", sa.Integer(), nullable=True))
    op.add_column("sessions", sa.Column("winners_self", sa.Integer(), nullable=True))
    op.add_column("sessions", sa.Column("winners_opponent", sa.Integer(), nullable=True))


def downgrade() -> None:
    op.drop_column("sessions", "winners_opponent")
    op.drop_column("sessions", "winners_self")
    op.drop_column("sessions", "first_serve_percent_opponent")
    op.drop_column("sessions", "first_serve_percent_self")
