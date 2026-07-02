import type { MonthlyWinRate } from "@/lib/stats";
import styles from "./MonthlyWinRateChart.module.css";

type Props = { months: MonthlyWinRate[] };

export function MonthlyWinRateChart({ months }: Props) {
  return (
    <div className={styles.card}>
      <div className={styles.title}>Win rate par mois</div>
      <div className={styles.chart}>
        {months.map((month) =>
          month.winRatePercent === null ? (
            <div key={month.monthLabel} className={styles.column}>
              <div className={styles.barEmpty} title="Pas de match ce mois-ci" />
              <span className={styles.monthLabel}>{month.monthLabel}</span>
            </div>
          ) : (
            <div key={month.monthLabel} className={styles.column}>
              <div
                className={`${styles.bar} ${month.isCurrentMonth ? styles.barCurrent : ""}`}
                style={{ height: `${Math.round(month.winRatePercent * 100)}%` }}
              />
              <span className={`${styles.monthLabel} ${month.isCurrentMonth ? styles.monthLabelCurrent : ""}`}>
                {month.monthLabel}
              </span>
            </div>
          )
        )}
      </div>
    </div>
  );
}
