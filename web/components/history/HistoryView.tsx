"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import type { SessionDto } from "@/lib/types";
import { surfaceLabel } from "@/lib/surfaces";
import { MatchEditForm } from "./MatchEditForm";
import { DeleteMatchButton } from "./DeleteMatchButton";
import styles from "./HistoryView.module.css";

type Props = { matches: SessionDto[] };

const PAGE_SIZE = 20;

function formatDate(timestampMs: number): string {
  return new Date(timestampMs).toLocaleDateString("fr-FR", { day: "numeric", month: "short", year: "numeric" });
}

export function HistoryView({ matches }: Props) {
  const router = useRouter();
  const [page, setPage] = useState(0);
  const [editingId, setEditingId] = useState<number | null>(null);
  const totalPages = Math.ceil(matches.length / PAGE_SIZE);
  const pageMatches = matches.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  return (
    <div className={styles.card}>
      <div className={styles.header}>Historique des matchs</div>
      {matches.length === 0 ? (
        <div className={styles.empty}>Pas encore de match</div>
      ) : (
        <>
          <div className={styles.list}>
            {pageMatches.map((match) => (
              <div key={match.id}>
                <div className={styles.row}>
                  <span className={styles.dateCol}>{formatDate(match.createdAt)}</span>
                  <span className={styles.opponentCol}>{match.opponent ?? "Adversaire"}</span>
                  <span className={styles.surfaceCol}>
                    <span className={styles.surfaceChip}>{surfaceLabel(match.surface)}</span>
                  </span>
                  <span className={styles.scoreCol}>{match.scoreText ?? "—"}</span>
                  <span className={styles.resultCol}>
                    {match.result === "VICTORY" && <span className={`${styles.resultBadge} ${styles.resultVictory}`}>VICTOIRE</span>}
                    {match.result === "DEFEAT" && <span className={`${styles.resultBadge} ${styles.resultDefeat}`}>DÉFAITE</span>}
                  </span>
                  <span className={styles.actionsCol}>
                    <button
                      type="button"
                      className={styles.editButton}
                      onClick={() => setEditingId(editingId === match.id ? null : match.id)}
                    >
                      Modifier
                    </button>
                    <DeleteMatchButton sessionId={match.id} onDeleted={() => router.refresh()} />
                  </span>
                </div>
                {editingId === match.id && (
                  <MatchEditForm
                    match={match}
                    onCancel={() => setEditingId(null)}
                    onSaved={() => {
                      setEditingId(null);
                      router.refresh();
                    }}
                  />
                )}
              </div>
            ))}
          </div>
          {totalPages > 1 && (
            <div className={styles.pagination}>
              <button
                type="button"
                className={styles.pageButton}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
              >
                Page précédente
              </button>
              <span className={styles.pageIndicator}>
                Page {page + 1} / {totalPages}
              </span>
              {page < totalPages - 1 && (
                <button type="button" className={styles.pageButton} onClick={() => setPage((p) => p + 1)}>
                  Page suivante
                </button>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
}
