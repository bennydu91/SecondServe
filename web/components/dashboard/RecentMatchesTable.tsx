import type { SessionDto } from "@/lib/types";
import { surfaceLabel } from "@/lib/surfaces";
import styles from "./RecentMatchesTable.module.css";

type Props = { matches: SessionDto[] };

function formatDate(timestampMs: number): string {
  return new Date(timestampMs).toLocaleDateString("fr-FR", { day: "numeric", month: "short" });
}

export function RecentMatchesTable({ matches }: Props) {
  return (
    <div className={styles.card}>
      <div className={styles.header}>Derniers matchs</div>
      <div className={styles.columnHeader}>
        <span style={{ width: 90 }}>Date</span>
        <span style={{ flex: 1 }}>Adversaire</span>
        <span style={{ width: 120 }}>Surface</span>
        <span style={{ width: 120 }}>Score</span>
        <span style={{ width: 90, textAlign: "right" }}>Résultat</span>
      </div>
      {matches.length === 0 ? (
        <div className={styles.empty}>Pas encore de match</div>
      ) : (
        <div className={styles.scrollArea}>
          {matches.map((match) => (
            <div key={match.id} className={styles.row}>
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
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
