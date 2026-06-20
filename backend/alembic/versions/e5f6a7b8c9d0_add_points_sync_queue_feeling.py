"""add points sync_queue feeling columns

Revision ID: e5f6a7b8c9d0
Revises: d4e5f6a7b8c9
Create Date: 2026-06-19
"""
from alembic import op
import sqlalchemy as sa

revision = 'e5f6a7b8c9d0'
down_revision = 'd4e5f6a7b8c9'
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "points",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column(
            "session_id",
            sa.Integer(),
            sa.ForeignKey("sessions.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("scorer", sa.String(), nullable=False),
        sa.Column("sequence_num", sa.Integer(), nullable=False),
        sa.Column("recorded_at", sa.Integer(), nullable=False),
    )
    op.create_index("idx_points_session", "points", ["session_id"])

    op.create_table(
        "sync_queue",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column("entity_type", sa.String(), nullable=False),
        sa.Column("entity_id", sa.Integer(), nullable=False),
        sa.Column("operation", sa.String(), nullable=False),
        sa.Column("status", sa.String(), nullable=False, server_default="PENDING"),
        sa.Column("created_at", sa.Integer(), nullable=False),
        sa.Column("retry_count", sa.Integer(), nullable=False, server_default="0"),
    )
    op.create_index("idx_sync_queue_status", "sync_queue", ["status"])

    op.add_column("sessions", sa.Column("feeling_rating", sa.Integer(), nullable=True))
    op.add_column("sessions", sa.Column("feeling_comment", sa.String(), nullable=True))


def downgrade() -> None:
    op.drop_column("sessions", "feeling_comment")
    op.drop_column("sessions", "feeling_rating")
    op.drop_index("idx_sync_queue_status", table_name="sync_queue")
    op.drop_table("sync_queue")
    op.drop_index("idx_points_session", table_name="points")
    op.drop_table("points")
