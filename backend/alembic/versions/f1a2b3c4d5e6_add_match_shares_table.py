"""add match_shares table

Revision ID: f1a2b3c4d5e6
Revises: d0e1f2a3b4c5
Create Date: 2026-07-01
"""
from alembic import op
import sqlalchemy as sa

revision = 'f1a2b3c4d5e6'
down_revision = 'd0e1f2a3b4c5'
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        'match_shares',
        sa.Column('id', sa.Integer(), nullable=False, autoincrement=True),
        sa.Column('token', sa.String(), nullable=False),
        sa.Column('session_id', sa.Integer(), nullable=False),
        sa.Column('created_at', sa.Integer(), nullable=False),
        sa.Column('expires_at', sa.Integer(), nullable=True),
        sa.Column('score_snapshot', sa.Text(), nullable=True),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index('idx_match_shares_token', 'match_shares', ['token'], unique=True)
    op.create_index('idx_match_shares_session_id', 'match_shares', ['session_id'], unique=True)


def downgrade() -> None:
    op.drop_index('idx_match_shares_session_id', table_name='match_shares')
    op.drop_index('idx_match_shares_token', table_name='match_shares')
    op.drop_table('match_shares')
