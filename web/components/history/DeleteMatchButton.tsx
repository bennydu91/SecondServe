"use client";

import { useState } from "react";
import styles from "./DeleteMatchButton.module.css";

type Props = {
  sessionId: number;
  onDeleted: () => void;
};

export function DeleteMatchButton({ sessionId, onDeleted }: Props) {
  const [confirming, setConfirming] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleConfirm() {
    if (pending) return;
    setPending(true);
    setError(null);
    try {
      const response = await fetch(`/api/console/sessions/${sessionId}`, { method: "DELETE" });
      if (!response.ok) throw new Error("delete_failed");
      onDeleted();
    } catch {
      setError("Échec de la suppression, réessayez.");
      setPending(false);
    }
  }

  if (!confirming) {
    return (
      <button type="button" className={styles.deleteButton} onClick={() => setConfirming(true)}>
        Supprimer
      </button>
    );
  }

  return (
    <span className={styles.confirmGroup}>
      <span className={styles.confirmText}>Confirmer la suppression ?</span>
      <button type="button" className={styles.confirmButton} onClick={handleConfirm} disabled={pending}>
        {pending ? "Suppression..." : "Confirmer"}
      </button>
      <button
        type="button"
        className={styles.cancelButton}
        onClick={() => {
          setConfirming(false);
          setError(null);
        }}
        disabled={pending}
      >
        Annuler
      </button>
      {error && <span className={styles.error}>{error}</span>}
    </span>
  );
}
