"use client";

import { useState, type FormEvent } from "react";
import type { SessionDto, SetResult } from "@/lib/types";
import { SetScoreInputs, parseSetEntries } from "@/components/console/SetScoreInputs";
import type { SetScoreEntry } from "@/components/console/SetScoreInputs";
import { computeSetsOutcome } from "@/lib/scoreEngine";
import styles from "./MatchEditForm.module.css";

type Props = {
  match: SessionDto;
  onCancel: () => void;
  onSaved: () => void;
};

function parseScoreTextToSets(scoreText: string | null): SetScoreEntry[] {
  if (!scoreText) return [{ self: "", opponent: "" }, { self: "", opponent: "" }];
  const parts = scoreText.split(" · ").map((part) => {
    const [self, opponent] = part.split("-");
    return { self: self ?? "", opponent: opponent ?? "" };
  });
  return parts.length > 0 ? parts : [{ self: "", opponent: "" }, { self: "", opponent: "" }];
}

export function MatchEditForm({ match, onCancel, onSaved }: Props) {
  const [surface, setSurface] = useState(match.surface);
  const [matchFormat, setMatchFormat] = useState(match.matchFormat);
  const [opponent, setOpponent] = useState(match.opponent ?? "");
  const [sets, setSets] = useState<SetScoreEntry[]>(parseScoreTextToSets(match.scoreText));
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const maxSets = matchFormat === "BEST_OF_3" ? 3 : 1;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      const parsedSets: SetResult[] = parseSetEntries(sets).slice(0, maxSets);
      const { result, scoreText } = computeSetsOutcome(parsedSets);
      const response = await fetch(`/api/console/sessions/${match.id}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          surface,
          matchFormat,
          opponent: opponent || null,
          result,
          scoreText,
        }),
      });
      if (!response.ok) throw new Error("update_failed");
      onSaved();
    } catch {
      setError("Échec de la mise à jour du match, réessayez.");
      setSubmitting(false);
    }
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit}>
      <label className={styles.field}>
        Surface
        <select value={surface} onChange={(e) => setSurface(e.target.value)}>
          <option value="CLAY">Terre battue</option>
          <option value="HARD">Dur</option>
          <option value="GRASS">Gazon</option>
          <option value="CARPET">Indoor</option>
        </select>
      </label>

      <label className={styles.field}>
        Format
        <select value={matchFormat} onChange={(e) => setMatchFormat(e.target.value)}>
          <option value="BEST_OF_1">1 set</option>
          <option value="BEST_OF_3">3 sets</option>
        </select>
      </label>

      <label className={styles.field}>
        Adversaire
        <input type="text" value={opponent} onChange={(e) => setOpponent(e.target.value)} />
      </label>

      <div className={styles.field}>
        Score final
        <SetScoreInputs sets={sets} onChange={setSets} maxSets={maxSets} />
      </div>

      {error && <p className={styles.error}>{error}</p>}

      <div className={styles.actions}>
        <button type="button" className={styles.cancelButton} onClick={onCancel} disabled={submitting}>
          Annuler
        </button>
        <button type="submit" className={styles.submitButton} disabled={submitting}>
          {submitting ? "Enregistrement..." : "Enregistrer"}
        </button>
      </div>
    </form>
  );
}
