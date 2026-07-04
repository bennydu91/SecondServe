import type { SetResult } from "@/lib/types";
import styles from "./SetScoreInputs.module.css";

export type SetScoreEntry = { self: string; opponent: string };

type Props = {
  sets: SetScoreEntry[];
  onChange: (sets: SetScoreEntry[]) => void;
  maxSets: number;
};

export function parseSetEntries(entries: SetScoreEntry[]): SetResult[] {
  return entries
    .filter((e) => e.self.trim() !== "" && e.opponent.trim() !== "")
    .map((e) => ({ gamesA: Number(e.self), gamesB: Number(e.opponent) }))
    .filter((s) => Number.isFinite(s.gamesA) && Number.isFinite(s.gamesB) && s.gamesA >= 0 && s.gamesB >= 0);
}

export function SetScoreInputs({ sets, onChange, maxSets }: Props) {
  function updateSet(index: number, field: "self" | "opponent", value: string) {
    onChange(sets.map((s, i) => (i === index ? { ...s, [field]: value } : s)));
  }

  function addSet() {
    if (sets.length >= maxSets) return;
    onChange([...sets, { self: "", opponent: "" }]);
  }

  function removeSet(index: number) {
    onChange(sets.filter((_, i) => i !== index));
  }

  return (
    <div className={styles.container}>
      {sets.map((set, index) => (
        <div key={index} className={styles.setRow}>
          <span className={styles.setLabel}>Set {index + 1}</span>
          <input
            type="number"
            min={0}
            value={set.self}
            onChange={(e) => updateSet(index, "self", e.target.value)}
            aria-label={`Set ${index + 1} - jeux moi`}
            className={styles.setInput}
          />
          <span className={styles.dash}>-</span>
          <input
            type="number"
            min={0}
            value={set.opponent}
            onChange={(e) => updateSet(index, "opponent", e.target.value)}
            aria-label={`Set ${index + 1} - jeux adversaire`}
            className={styles.setInput}
          />
          {sets.length > 1 && (
            <button
              type="button"
              className={styles.removeButton}
              onClick={() => removeSet(index)}
              aria-label={`Supprimer le set ${index + 1}`}
            >
              ×
            </button>
          )}
        </div>
      ))}
      {sets.length < maxSets && (
        <button type="button" className={styles.addButton} onClick={addSet}>
          + Ajouter un set
        </button>
      )}
    </div>
  );
}
