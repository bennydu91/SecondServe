import type { SurfaceWinRate } from "@/lib/stats";
import { surfaceLabel, surfaceColorVar } from "@/lib/surfaces";
import styles from "./SurfaceBreakdown.module.css";

type Props = { bySurface: SurfaceWinRate[] };

export function SurfaceBreakdown({ bySurface }: Props) {
  return (
    <div className={styles.card}>
      <div className={styles.title}>Par surface</div>
      {bySurface.map((entry) => {
        const percent = entry.winRatePercent === null ? null : Math.round(entry.winRatePercent * 100);
        return (
          <div key={entry.surface} className={styles.row}>
            <div className={styles.rowHeader}>
              <span className={styles.surfaceName}>{surfaceLabel(entry.surface)}</span>
              <span className={styles.surfacePercent}>{percent === null ? "—" : `${percent}%`}</span>
            </div>
            <div className={styles.track}>
              <div
                className={styles.fill}
                style={{
                  width: `${percent ?? 0}%`,
                  background: `var(${surfaceColorVar(entry.surface)})`,
                }}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
}
