"""add player_profiles and ranking_history

Revision ID: a1b2c3d4e5f6
Revises: 93050bf04cdd
Create Date: 2026-06-16 10:00:00.000000

"""
from typing import Sequence, Union
from alembic import op
import sqlalchemy as sa

revision: str = 'a1b2c3d4e5f6'
down_revision: Union[str, Sequence[str], None] = '93050bf04cdd'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        'player_profiles',
        sa.Column('id', sa.Integer(), primary_key=True),
        sa.Column('current_series', sa.String(), nullable=True),
        sa.Column('current_points', sa.Integer(), nullable=True),
        sa.Column('updated_at', sa.Integer(), nullable=False),
    )
    op.create_table(
        'ranking_history',
        sa.Column('id', sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column('series', sa.String(), nullable=False),
        sa.Column('points', sa.Integer(), nullable=False),
        sa.Column('recorded_at', sa.Integer(), nullable=False),
        sa.Column('updated_at', sa.Integer(), nullable=False),
    )


def downgrade() -> None:
    op.drop_table('ranking_history')
    op.drop_table('player_profiles')
