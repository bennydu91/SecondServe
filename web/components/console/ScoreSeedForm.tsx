"use client";

import { useState, type FormEvent } from "react";
import styles from "./ScoreSeedForm.module.css";

type Props = {
  sessionId: number;
  onCancel: () => void;
  onSeeded: () => void;
};

const GAME_POINT_OPTIONS = ["ZERO", "FIFTEEN", "THIRTY", "FORTY", "ADVANTAGE"];

function parseCompletedSets(text: string): { gamesA: number; gamesB: number }[] {
  return text
    .split(",")
    .map((part) => part.trim())
    .filter((part) => part.length > 0)
    .map((part) => {
      const [a, b] = part.split("-").map((n) => Number(n.trim()));
      return { gamesA: a || 0, gamesB: b || 0 };
    });
}

export function ScoreSeedForm({ sessionId, onCancel, onSeeded }: Props) {
  const [completedSetsText, setCompletedSetsText] = useState("");
  const [currentSetGamesA, setCurrentSetGamesA] = useState(0);
  const [currentSetGamesB, setCurrentSetGamesB] = useState(0);
  const [currentGamePointsA, setCurrentGamePointsA] = useState("ZERO");
  const [currentGamePointsB, setCurrentGamePointsB] = useState("ZERO");
  const [isTieBreak, setIsTieBreak] = useState(false);
  const [tieBreakPointsA, setTieBreakPointsA] = useState(0);
  const [tieBreakPointsB, setTieBreakPointsB] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      const response = await fetch(`/api/console/sessions/${sessionId}/score-seed`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          completedSets: parseCompletedSets(completedSetsText),
          currentSetGamesA,
          currentSetGamesB,
          currentGamePointsA,
          currentGamePointsB,
          tieBreakPointsA,
          tieBreakPointsB,
          isTieBreak,
          isSuperTieBreak: false,
        }),
      });
      if (!response.ok) throw new Error("seed_failed");
      onSeeded();
    } catch {
      setError("Échec de l'enregistrement du score de départ, réessayez.");
      setSubmitting(false);
    }
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit}>
      <p className={styles.hint}>
        Sets déjà terminés (ex. « 6-4, 3-6 »), score du set en cours, et point du jeu en cours si la reprise se
        fait en plein jeu.
      </p>

      <label className={styles.field}>
        Sets terminés
        <input
          type="text"
          value={completedSetsText}
          onChange={(e) => setCompletedSetsText(e.target.value)}
          placeholder="6-4, 3-6"
        />
      </label>

      <div className={styles.row}>
        <label className={styles.field}>
          Jeux (moi)
          <input
            type="number"
            min={0}
            value={currentSetGamesA}
            onChange={(e) => setCurrentSetGamesA(Number(e.target.value))}
          />
        </label>
        <label className={styles.field}>
          Jeux (adversaire)
          <input
            type="number"
            min={0}
            value={currentSetGamesB}
            onChange={(e) => setCurrentSetGamesB(Number(e.target.value))}
          />
        </label>
      </div>

      <label className={styles.field}>
        <input type="checkbox" checked={isTieBreak} onChange={(e) => setIsTieBreak(e.target.checked)} />
        Le set en cours est un tie-break
      </label>

      {isTieBreak ? (
        <div className={styles.row}>
          <label className={styles.field}>
            Points tie-break (moi)
            <input
              type="number"
              min={0}
              value={tieBreakPointsA}
              onChange={(e) => setTieBreakPointsA(Number(e.target.value))}
            />
          </label>
          <label className={styles.field}>
            Points tie-break (adversaire)
            <input
              type="number"
              min={0}
              value={tieBreakPointsB}
              onChange={(e) => setTieBreakPointsB(Number(e.target.value))}
            />
          </label>
        </div>
      ) : (
        <div className={styles.row}>
          <label className={styles.field}>
            Point du jeu (moi)
            <select value={currentGamePointsA} onChange={(e) => setCurrentGamePointsA(e.target.value)}>
              {GAME_POINT_OPTIONS.map((opt) => (
                <option key={opt} value={opt}>
                  {opt}
                </option>
              ))}
            </select>
          </label>
          <label className={styles.field}>
            Point du jeu (adversaire)
            <select value={currentGamePointsB} onChange={(e) => setCurrentGamePointsB(e.target.value)}>
              {GAME_POINT_OPTIONS.map((opt) => (
                <option key={opt} value={opt}>
                  {opt}
                </option>
              ))}
            </select>
          </label>
        </div>
      )}

      {error && <p className={styles.error}>{error}</p>}

      <div className={styles.actions}>
        <button type="button" className={styles.cancelButton} onClick={onCancel} disabled={submitting}>
          Annuler
        </button>
        <button type="submit" className={styles.submitButton} disabled={submitting}>
          {submitting ? "Enregistrement..." : "Valider et reprendre"}
        </button>
      </div>
    </form>
  );
}
