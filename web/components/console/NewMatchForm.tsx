"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { SetScoreInputs, parseSetEntries } from "./SetScoreInputs";
import type { SetScoreEntry } from "./SetScoreInputs";
import { computeSetsOutcome } from "@/lib/scoreEngine";
import styles from "./NewMatchForm.module.css";

type Props = { onCancel: () => void };
type Mode = "LIVE" | "PAST";

function todayDateInputValue(): string {
  return new Date().toISOString().slice(0, 10);
}

function emptySets(): SetScoreEntry[] {
  return [{ self: "", opponent: "" }, { self: "", opponent: "" }];
}

export function NewMatchForm({ onCancel }: Props) {
  const router = useRouter();
  const [mode, setMode] = useState<Mode>("LIVE");
  const [surface, setSurface] = useState("CLAY");
  const [matchFormat, setMatchFormat] = useState("BEST_OF_3");
  const [thirdSetRule, setThirdSetRule] = useState("FULL_ADVANTAGE");
  const [opponent, setOpponent] = useState("");
  const [date, setDate] = useState(todayDateInputValue());
  const [sets, setSets] = useState<SetScoreEntry[]>(emptySets());
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const maxSets = matchFormat === "BEST_OF_3" ? 3 : 1;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    setError(null);
    const createdAt = new Date(date).getTime();
    try {
      const response = await fetch("/api/console/sessions", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          surface,
          matchFormat,
          thirdSetRule,
          opponent: opponent || null,
          createdAt,
        }),
      });
      if (!response.ok) throw new Error("create_failed");
      const session = await response.json();

      if (mode === "LIVE") {
        router.push(`/dashboard/console/${session.id}`);
        return;
      }

      const parsedSets = parseSetEntries(sets).slice(0, maxSets);
      const { result, scoreText } = computeSetsOutcome(parsedSets);
      const patchResponse = await fetch(`/api/console/sessions/${session.id}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ status: "COMPLETED", result, scoreText, updatedAt: createdAt }),
      });
      if (!patchResponse.ok) throw new Error("finalize_failed");
      router.push("/dashboard");
    } catch {
      setError(
        mode === "LIVE"
          ? "Échec de la création du match, réessayez."
          : "Échec de l'enregistrement du match, réessayez."
      );
      setSubmitting(false);
    }
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit}>
      <div className={styles.modeToggle}>
        <button
          type="button"
          className={mode === "LIVE" ? styles.modeButtonActive : styles.modeButton}
          onClick={() => setMode("LIVE")}
        >
          Match en cours
        </button>
        <button
          type="button"
          className={mode === "PAST" ? styles.modeButtonActive : styles.modeButton}
          onClick={() => setMode("PAST")}
        >
          Match déjà joué
        </button>
      </div>

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
        Règle du 3e set
        <select value={thirdSetRule} onChange={(e) => setThirdSetRule(e.target.value)}>
          <option value="FULL_ADVANTAGE">Set complet</option>
          <option value="SUPER_TIE_BREAK_10">Super tie-break à 10</option>
          <option value="SHORT_DECISIVE_SET">Jeu décisif court</option>
        </select>
      </label>

      <label className={styles.field}>
        Adversaire
        <input type="text" value={opponent} onChange={(e) => setOpponent(e.target.value)} />
      </label>

      <label className={styles.field}>
        Date du match
        <input type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
      </label>

      {mode === "PAST" && (
        <div className={styles.field}>
          Score final
          <SetScoreInputs sets={sets} onChange={setSets} maxSets={maxSets} />
        </div>
      )}

      {error && <p className={styles.error}>{error}</p>}

      <div className={styles.actions}>
        <button type="button" className={styles.cancelButton} onClick={onCancel} disabled={submitting}>
          Annuler
        </button>
        <button type="submit" className={styles.submitButton} disabled={submitting}>
          {submitting
            ? mode === "LIVE"
              ? "Création..."
              : "Enregistrement..."
            : mode === "LIVE"
              ? "Créer et commencer la saisie"
              : "Enregistrer le match"}
        </button>
      </div>
    </form>
  );
}
