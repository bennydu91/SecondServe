"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import styles from "./NewMatchForm.module.css";

type Props = { onCancel: () => void };

function todayDateInputValue(): string {
  return new Date().toISOString().slice(0, 10);
}

export function NewMatchForm({ onCancel }: Props) {
  const router = useRouter();
  const [surface, setSurface] = useState("CLAY");
  const [matchFormat, setMatchFormat] = useState("BEST_OF_3");
  const [thirdSetRule, setThirdSetRule] = useState("FULL_ADVANTAGE");
  const [opponent, setOpponent] = useState("");
  const [date, setDate] = useState(todayDateInputValue());
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      const response = await fetch("/api/console/sessions", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          surface,
          matchFormat,
          thirdSetRule,
          opponent: opponent || null,
          createdAt: new Date(date).getTime(),
        }),
      });
      if (!response.ok) throw new Error("create_failed");
      const session = await response.json();
      router.push(`/dashboard/console/${session.id}`);
    } catch {
      setError("Échec de la création du match, réessayez.");
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

      {error && <p className={styles.error}>{error}</p>}

      <div className={styles.actions}>
        <button type="button" className={styles.cancelButton} onClick={onCancel} disabled={submitting}>
          Annuler
        </button>
        <button type="submit" className={styles.submitButton} disabled={submitting}>
          {submitting ? "Création..." : "Créer et commencer la saisie"}
        </button>
      </div>
    </form>
  );
}
