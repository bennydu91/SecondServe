import type { PointDto } from "@/lib/types";
import styles from "./PointStatsTiles.module.css";

type Props = { points: PointDto[] };

function count(points: PointDto[], context: string): number {
  return points.filter((p) => p.context === context).length;
}

export function PointStatsTiles({ points }: Props) {
  const tiles = [
    { label: "Aces", value: count(points, "ACE") },
    { label: "Coups gagnants", value: count(points, "WINNER") },
    { label: "Fautes directes", value: count(points, "UNFORCED_ERROR_SELF") },
    { label: "Doubles fautes", value: count(points, "DOUBLE_FAULT") },
  ];

  return (
    <div className={styles.tiles}>
      {tiles.map((tile) => (
        <div key={tile.label} className={styles.tile}>
          <span className={styles.tileValue}>{tile.value}</span>
          <span className={styles.tileLabel}>{tile.label}</span>
        </div>
      ))}
    </div>
  );
}
