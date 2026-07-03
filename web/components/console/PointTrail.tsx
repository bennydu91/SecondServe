import type { PointDto } from "@/lib/types";
import styles from "./PointTrail.module.css";

type Props = { points: PointDto[] };

const CONTEXT_LABELS: Record<string, string> = {
  ACE: "Ace",
  WINNER: "Coup gagnant",
  FORCED_ERROR: "Faute provoquée",
  UNFORCED_ERROR_OPPONENT: "Faute adverse",
  ACE_OPPONENT: "Ace adverse",
  WINNER_OPPONENT: "Coup gagnant adverse",
  UNFORCED_ERROR_SELF: "Ma faute",
  DOUBLE_FAULT: "Double faute",
};

export function PointTrail({ points }: Props) {
  if (points.length === 0) {
    return <p className={styles.empty}>Aucun point saisi pour l&apos;instant.</p>;
  }

  return (
    <ul className={styles.list}>
      {[...points].reverse().map((point) => (
        <li key={point.id} className={styles.item}>
          <span className={`${styles.scorerBadge} ${point.scorer === "A" ? styles.scorerA : styles.scorerB}`}>
            {point.scorer}
          </span>
          <span className={styles.label}>{point.context ? CONTEXT_LABELS[point.context] : "Point"}</span>
        </li>
      ))}
    </ul>
  );
}
