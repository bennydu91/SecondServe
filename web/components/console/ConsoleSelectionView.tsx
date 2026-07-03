"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import type { SessionDto } from "@/lib/types";
import { surfaceLabel } from "@/lib/surfaces";
import { NewMatchForm } from "./NewMatchForm";
import { ScoreSeedForm } from "./ScoreSeedForm";
import styles from "./ConsoleSelectionView.module.css";

type Props = { activeSessions: SessionDto[] };

export function ConsoleSelectionView({ activeSessions }: Props) {
  const router = useRouter();
  const [showNewMatchForm, setShowNewMatchForm] = useState(false);
  const [resumingSessionId, setResumingSessionId] = useState<number | null>(null);

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>Console de saisie</h1>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Reprendre une session active</h2>
        {activeSessions.length === 0 ? (
          <p className={styles.empty}>Aucune session active à reprendre.</p>
        ) : (
          <ul className={styles.list}>
            {activeSessions.map((session) => (
              <li key={session.id} className={styles.listItem}>
                <span className={styles.listItemLabel}>
                  {session.opponent ?? "Adversaire"} · {surfaceLabel(session.surface)}
                </span>
                <button
                  type="button"
                  className={styles.resumeButton}
                  onClick={() => setResumingSessionId(session.id)}
                >
                  Reprendre
                </button>
              </li>
            ))}
          </ul>
        )}
        {resumingSessionId !== null && (
          <div className={styles.seedFormWrapper}>
            <ScoreSeedForm
              key={resumingSessionId}
              sessionId={resumingSessionId}
              onCancel={() => setResumingSessionId(null)}
              onSeeded={() => router.push(`/dashboard/console/${resumingSessionId}`)}
            />
          </div>
        )}
      </section>

      <section className={styles.section}>
        {showNewMatchForm ? (
          <NewMatchForm onCancel={() => setShowNewMatchForm(false)} />
        ) : (
          <button type="button" className={styles.newMatchButton} onClick={() => setShowNewMatchForm(true)}>
            Nouveau match
          </button>
        )}
      </section>
    </div>
  );
}
