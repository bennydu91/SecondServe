"use client";

import { useLiveSnapshot } from "@/hooks/useLiveSnapshot";
import { LiveBadge } from "./LiveBadge";
import { ScoreTable } from "./ScoreTable";
import { SetTrail } from "./SetTrail";
import styles from "./LiveScoreBoard.module.css";
import type { LiveSnapshot } from "@/lib/types";

type Props = { token: string; initialSnapshot: LiveSnapshot };

const SURFACE_LABELS: Record<string, string> = {
  CLAY: "Terre battue",
  HARD: "Dur",
  GRASS: "Gazon",
  CARPET: "Moquette",
};

export function LiveScoreBoard({ token, initialSnapshot }: Props) {
  const { snapshot, connectionState } = useLiveSnapshot(token, initialSnapshot);
  const playerAInitial = (snapshot.playerAName ?? "J").charAt(0).toUpperCase();
  const playerBInitial = (snapshot.playerBName ?? "A").charAt(0).toUpperCase();

  return (
    <div className={styles.page}>
      <div className={styles.contextRow}>
        <LiveBadge status={snapshot.status} />
        <span className={styles.contextText}>
          {snapshot.surface ? (SURFACE_LABELS[snapshot.surface] ?? snapshot.surface) : ""}
          {snapshot.tournament ? ` · ${snapshot.tournament}` : ""}
        </span>
      </div>

      <ScoreTable snapshot={snapshot} />

      <div className={styles.setTrailWrapper}>
        <div className={styles.setTrailLabel}>
          DÉROULÉ · SET {snapshot.completedSets.length + 1}
        </div>
        <SetTrail log={snapshot.currentSetPointLog} playerAInitial={playerAInitial} playerBInitial={playerBInitial} />
      </div>

      {connectionState === "reconnecting" && (
        <p className={styles.reconnecting}>Reconnexion…</p>
      )}

      <div className={styles.footer}>Suivi propulsé par SecondServe · se met à jour automatiquement</div>
    </div>
  );
}
