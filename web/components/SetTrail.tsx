import styles from "./SetTrail.module.css";

type Props = {
  log: ("A" | "B")[];
  playerAInitial: string;
  playerBInitial: string;
};

export function SetTrail({ log, playerAInitial, playerBInitial }: Props) {
  return (
    <div className={styles.trail}>
      {log.map((winner, index) => (
        <span key={index} className={styles.point} data-testid="set-trail-point">
          {winner === "A" ? playerAInitial : playerBInitial}
        </span>
      ))}
      <span className={styles.pending} data-testid="set-trail-pending">
        •
      </span>
    </div>
  );
}
