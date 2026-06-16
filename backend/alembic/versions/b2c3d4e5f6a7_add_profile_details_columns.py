"""add profile details columns to player_profiles

Revision ID: b2c3d4e5f6a7
Revises: a1b2c3d4e5f6
Create Date: 2026-06-16
"""
from alembic import op
import sqlalchemy as sa

revision = 'b2c3d4e5f6a7'
down_revision = 'a1b2c3d4e5f6'
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column('player_profiles', sa.Column('play_style', sa.String(), nullable=True))
    op.add_column('player_profiles', sa.Column('preferred_surfaces', sa.String(), nullable=True))
    op.add_column('player_profiles', sa.Column('coach_instruction_1', sa.String(), nullable=True))
    op.add_column('player_profiles', sa.Column('coach_instruction_2', sa.String(), nullable=True))
    op.add_column('player_profiles', sa.Column('coach_instruction_3', sa.String(), nullable=True))


def downgrade() -> None:
    op.drop_column('player_profiles', 'coach_instruction_3')
    op.drop_column('player_profiles', 'coach_instruction_2')
    op.drop_column('player_profiles', 'coach_instruction_1')
    op.drop_column('player_profiles', 'preferred_surfaces')
    op.drop_column('player_profiles', 'play_style')
