"use client";

import type { PointContext } from "@/lib/types";
import styles from "./PointButtonGrid.module.css";

type ButtonSpec = { context: PointContext; label: string };

const SELF_BUTTONS: ButtonSpec[] = [
  { context: "ACE", label: "Ace" },
  { context: "WINNER", label: "Coup gagnant" },
  { context: "FORCED_ERROR", label: "Faute provoquée" },
  { context: "UNFORCED_ERROR_OPPONENT", label: "Faute adverse" },
];

const OPPONENT_BUTTONS: ButtonSpec[] = [
  { context: "ACE_OPPONENT", label: "Ace adverse" },
  { context: "WINNER_OPPONENT", label: "Coup gagnant adverse" },
  { context: "UNFORCED_ERROR_SELF", label: "Ma faute" },
  { context: "DOUBLE_FAULT", label: "Double faute" },
];

type Props = {
  onSelect: (context: PointContext) => void;
  disabled: boolean;
};

export function PointButtonGrid({ onSelect, disabled }: Props) {
  return (
    <div className={styles.grid}>
      <div className={styles.group}>
        <h3 className={styles.groupTitle}>Mon point</h3>
        <div className={styles.buttons}>
          {SELF_BUTTONS.map((btn) => (
            <button
              key={btn.context}
              type="button"
              className={styles.selfButton}
              onClick={() => onSelect(btn.context)}
              disabled={disabled}
            >
              {btn.label}
            </button>
          ))}
        </div>
      </div>
      <div className={styles.group}>
        <h3 className={styles.groupTitle}>Point adverse</h3>
        <div className={styles.buttons}>
          {OPPONENT_BUTTONS.map((btn) => (
            <button
              key={btn.context}
              type="button"
              className={styles.opponentButton}
              onClick={() => onSelect(btn.context)}
              disabled={disabled}
            >
              {btn.label}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
