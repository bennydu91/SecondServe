"""add sessions table

Revision ID: d4e5f6a7b8c9
Revises: c3d4e5f6a7b8
Create Date: 2026-06-17
"""
from alembic import op
import sqlalchemy as sa

revision = 'd4e5f6a7b8c9'
down_revision = 'c3d4e5f6a7b8'
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        'sessions',
        sa.Column('id', sa.Integer(), nullable=False, autoincrement=True),
        sa.Column('surface', sa.String(), nullable=False),
        sa.Column('match_format', sa.String(), nullable=False),
        sa.Column('third_set_rule', sa.String(), nullable=False),
        sa.Column('opponent', sa.String(), nullable=True),
        sa.Column('competition_type', sa.String(), nullable=True),
        sa.Column('tournament', sa.String(), nullable=True),
        sa.Column('status', sa.String(), nullable=False, server_default='ACTIVE'),
        sa.Column('session_type', sa.String(), nullable=False, server_default='MATCH'),
        sa.Column('result', sa.String(), nullable=True),
        sa.Column('created_at', sa.Integer(), nullable=False),
        sa.Column('updated_at', sa.Integer(), nullable=False),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index('idx_sessions_surface', 'sessions', ['surface'])


def downgrade() -> None:
    op.drop_index('idx_sessions_surface', table_name='sessions')
    op.drop_table('sessions')
