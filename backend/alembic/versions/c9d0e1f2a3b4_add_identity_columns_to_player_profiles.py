"""add display_name and club columns to player_profiles

Revision ID: c9d0e1f2a3b4
Revises: a7b8c9d0e1f2
Create Date: 2026-07-01
"""
from alembic import op
import sqlalchemy as sa

revision = 'c9d0e1f2a3b4'
down_revision = 'a7b8c9d0e1f2'
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("player_profiles", sa.Column("display_name", sa.String(), nullable=True))
    op.add_column("player_profiles", sa.Column("club", sa.String(), nullable=True))


def downgrade() -> None:
    op.drop_column("player_profiles", "club")
    op.drop_column("player_profiles", "display_name")
