import styles from "./ScoreTable.module.css";
import type { LiveSnapshot } from "@/lib/types";

type Props = { snapshot: LiveSnapshot };

function pointLabel(points: string, isTieBreak: boolean, tieBreakPoints: number): string {
  if (isTieBreak) return String(tieBreakPoints);
  return { ZERO: "0", FIFTEEN: "15", THIRTY: "30", FORTY: "40", ADVANTAGE: "AD" }[points] ?? "0";
}

export function ScoreTable({ snapshot }: Props) {
  const leadingIsA =
    snapshot.currentSetGamesA > snapshot.currentSetGamesB ||
    snapshot.completedSets.filter((s) => s.gamesA > s.gamesB).length >=
      snapshot.completedSets.filter((s) => s.gamesB > s.gamesA).length;

  const rows = [
    {
      name: snapshot.playerAName ?? "Joueur",
      leading: leadingIsA,
      sets: snapshot.completedSets.map((s) => s.gamesA),
      games: snapshot.currentSetGamesA,
      points: pointLabel(snapshot.currentGamePointsA, snapshot.isTieBreak, snapshot.tieBreakPointsA),
    },
    {
      name: snapshot.playerBName ?? "Adversaire",
      leading: !leadingIsA,
      sets: snapshot.completedSets.map((s) => s.gamesB),
      games: snapshot.currentSetGamesB,
      points: pointLabel(snapshot.currentGamePointsB, snapshot.isTieBreak, snapshot.tieBreakPointsB),
    },
  ];

  return (
    <div className={styles.card}>
      <div className={styles.headerRow}>
        <span style={{ flex: 1 }}>JOUEUR</span>
        <span style={{ width: 44, textAlign: "center" }}>S1</span>
        <span style={{ width: 44, textAlign: "center" }}>S2</span>
        <span style={{ width: 62, textAlign: "center" }}>JEUX</span>
        <span style={{ width: 58, textAlign: "center" }}>POINTS</span>
      </div>
      {rows.map((row, index) => (
        <div key={index}>
          {index > 0 && <div className={styles.divider} />}
          <div className={`${styles.playerRow} ${row.leading ? styles.playerRowLeading : ""}`}>
            <span className={styles.playerName}>
              <span className={`${styles.avatar} ${row.leading ? styles.avatarLeading : styles.avatarTrailing}`}>
                {row.name.charAt(0).toUpperCase()}
              </span>
              <span className={styles.playerNameText}>{row.name}</span>
            </span>
            {[0, 1].map((setIndex) => (
              <span key={setIndex} className={`${styles.sets} ${styles.setsMuted}`}>
                {row.sets[setIndex] ?? ""}
              </span>
            ))}
            <span className={styles.games}>{row.games}</span>
            <span className={styles.points}>{row.points}</span>
          </div>
        </div>
      ))}
    </div>
  );
}
