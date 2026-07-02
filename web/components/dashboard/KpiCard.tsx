import styles from "./KpiCard.module.css";

type Props = {
  label: string;
  value: string;
  unit?: string;
  subtext?: string;
  subtextPositive?: boolean;
};

export function KpiCard({ label, value, unit, subtext, subtextPositive }: Props) {
  return (
    <div className={styles.card}>
      <div className={styles.label}>{label}</div>
      <div className={styles.value}>
        {value}
        {unit && <span className={styles.valueUnit}>{unit}</span>}
      </div>
      {subtext && (
        <div className={`${styles.subtext} ${subtextPositive ? styles.subtextPositive : ""}`}>{subtext}</div>
      )}
    </div>
  );
}
