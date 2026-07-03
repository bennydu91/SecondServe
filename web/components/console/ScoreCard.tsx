import type { MatchScore } from "@/lib/scoreEngine";
import styles from "./ScoreCard.module.css";

type Props = {
  score: MatchScore;
  selfName: string;
  opponentName: string;
};

const POINT_LABELS: Record<string, string> = { ZERO: "0", FIFTEEN: "15", THIRTY: "30", FORTY: "40", ADVANTAGE: "AD" };

function pointLabel(points: string, isTieBreak: boolean, isSuperTieBreak: boolean, tieBreakPoints: number): string {
  if (isTieBreak || isSuperTieBreak) return String(tieBreakPoints);
  return POINT_LABELS[points] ?? "0";
}

export function ScoreCard({ score, selfName, opponentName }: Props) {
  const setsWonA = score.completedSets.filter((s) => s.gamesA > s.gamesB).length;
  const setsWonB = score.completedSets.filter((s) => s.gamesB > s.gamesA).length;
  const leadingIsA =
    setsWonA !== setsWonB ? setsWonA > setsWonB : score.currentSetGamesA > score.currentSetGamesB;

  const rows = [
    {
      name: selfName,
      leading: leadingIsA,
      sets: score.completedSets.map((s) => s.gamesA),
      games: score.currentSetGamesA,
      points: pointLabel(score.currentGamePointsA, score.isTieBreak, score.isSuperTieBreak, score.tieBreakPointsA),
    },
    {
      name: opponentName,
      leading: !leadingIsA,
      sets: score.completedSets.map((s) => s.gamesB),
      games: score.currentSetGamesB,
      points: pointLabel(score.currentGamePointsB, score.isTieBreak, score.isSuperTieBreak, score.tieBreakPointsB),
    },
  ];

  return (
    <div className={styles.card}>
      <div className={styles.headerRow}>
        <span className={styles.headerName}>JOUEUR</span>
        <span className={styles.headerSets}>SETS</span>
        <span className={styles.headerGames}>JEUX</span>
        <span className={styles.headerPoints}>POINTS</span>
      </div>
      {rows.map((row, index) => (
        <div key={index}>
          {index > 0 && <div className={styles.divider} />}
          <div className={`${styles.playerRow} ${row.leading ? styles.playerRowLeading : ""}`}>
            <span className={styles.playerName}>{row.name}</span>
            <span className={styles.sets}>
              {row.sets.length === 0
                ? "—"
                : row.sets.map((g, i) => (
                    <span key={i} className={styles.setBox}>
                      {g}
                    </span>
                  ))}
            </span>
            <span className={styles.games}>{row.games}</span>
            <span className={styles.points}>{row.points}</span>
          </div>
        </div>
      ))}
      {score.isMatchOver && (
        <div className={styles.matchOverBanner}>
          Match terminé — {score.matchWinner === "A" ? selfName : opponentName} gagne
        </div>
      )}
    </div>
  );
}
