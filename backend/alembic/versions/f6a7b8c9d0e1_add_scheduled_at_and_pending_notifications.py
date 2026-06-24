"""add scheduled_at to sessions and pending_notifications table

Revision ID: f6a7b8c9d0e1
Revises: e5f6a7b8c9d0
Create Date: 2026-06-23
"""
from alembic import op
import sqlalchemy as sa

revision = 'f6a7b8c9d0e1'
down_revision = 'e5f6a7b8c9d0'
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("sessions", sa.Column("scheduled_at", sa.Integer(), nullable=True))

    op.create_table(
        "pending_notifications",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column(
            "session_id",
            sa.Integer(),
            sa.ForeignKey("sessions.id", ondelete="CASCADE"),
            nullable=False,
            unique=True
        ),
        sa.Column("content", sa.String(), nullable=False),
        sa.Column("generated_at", sa.Integer(), nullable=False),
        sa.Column("expires_at", sa.Integer(), nullable=False),
    )
    op.create_index("idx_pending_notif_session", "pending_notifications", ["session_id"])


def downgrade() -> None:
    op.drop_index("idx_pending_notif_session")
    op.drop_table("pending_notifications")
    op.drop_column("sessions", "scheduled_at")
