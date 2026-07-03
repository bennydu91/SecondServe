"use client";

import { useEffect, useState } from "react";
import type { SessionDto, PointDto, PointContext } from "@/lib/types";
import { parseScoreSeed } from "@/lib/api";
import { TennisScoreEngine, formatScoreText, deriveMatchResult } from "@/lib/scoreEngine";
import type { MatchScore, Player, SessionFormat } from "@/lib/scoreEngine";
import { ScoreCard } from "./ScoreCard";
import { PointButtonGrid } from "./PointButtonGrid";
import { PointStatsTiles } from "./PointStatsTiles";
import { PointTrail } from "./PointTrail";
import styles from "./ConsoleScreen.module.css";

type Props = {
  session: SessionDto;
  initialPoints: PointDto[];
};

const SELF_NAME = "Benjamin";

function buildInitialEngine(session: SessionDto, initialPoints: PointDto[]): TennisScoreEngine {
  const format: SessionFormat = {
    matchFormat: session.matchFormat as SessionFormat["matchFormat"],
    thirdSetRule: session.thirdSetRule as SessionFormat["thirdSetRule"],
  };
  const seed = parseScoreSeed(session.scoreSeedJson);
  const engine = new TennisScoreEngine(format, seed ?? undefined);
  for (const point of initialPoints) {
    engine.recordPoint(point.scorer as Player);
  }
  return engine;
}

export function ConsoleScreen({ session, initialPoints }: Props) {
  const [engine] = useState<TennisScoreEngine>(() => buildInitialEngine(session, initialPoints));

  const [score, setScore] = useState<MatchScore>(() => engine.currentScore);
  const [points, setPoints] = useState<PointDto[]>(initialPoints);
  const [shareToken, setShareToken] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const opponentName = session.opponent ?? "Adversaire";

  useEffect(() => {
    let cancelled = false;
    fetch(`/api/console/sessions/${session.id}/share`)
      .then((res) => (res.ok ? res.json() : null))
      .then((share: { token: string } | null) => {
        if (!cancelled) setShareToken(share?.token ?? null);
      })
      .catch(() => {
        if (!cancelled) setShareToken(null);
      });
    return () => {
      cancelled = true;
    };
  }, [session.id]);

  async function pushLiveScoreIfShared(nextScore: MatchScore) {
    if (shareToken === null) return;
    try {
      await fetch(`/api/console/sessions/${session.id}/live-score`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          completedSets: nextScore.completedSets,
          currentSetGamesA: nextScore.currentSetGamesA,
          currentSetGamesB: nextScore.currentSetGamesB,
          currentSetPointLog: nextScore.currentSetPointLog,
          currentGamePointsA: nextScore.currentGamePointsA,
          currentGamePointsB: nextScore.currentGamePointsB,
          tieBreakPointsA: nextScore.tieBreakPointsA,
          tieBreakPointsB: nextScore.tieBreakPointsB,
          isTieBreak: nextScore.isTieBreak,
          isSuperTieBreak: nextScore.isSuperTieBreak,
          isMatchOver: nextScore.isMatchOver,
          matchWinner: nextScore.matchWinner,
          playerAName: SELF_NAME,
          playerBName: opponentName,
          surface: session.surface,
          tournament: session.tournament,
          competitionType: session.competitionType,
          startedAt: session.createdAt,
        }),
      });
    } catch {
      // best-effort : le live-share ne doit jamais bloquer la saisie
    }
  }

  async function finalize(nextScore: MatchScore, status: "COMPLETED" | "ACTIVE") {
    await fetch(`/api/console/sessions/${session.id}/finalize`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        session,
        status,
        result: status === "COMPLETED" ? deriveMatchResult(nextScore) : null,
        scoreText: status === "COMPLETED" ? formatScoreText(nextScore) : null,
        updatedAt: Date.now(),
      }),
    });
  }

  async function handlePointClick(context: PointContext) {
    if (pending) return;
    setPending(true);
    setError(null);
    try {
      const response = await fetch(`/api/console/sessions/${session.id}/points`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ context }),
      });
      if (!response.ok) throw new Error("point_failed");
      const point: PointDto = await response.json();

      engine.recordPoint(point.scorer as Player);
      const nextScore = engine.currentScore;
      setScore(nextScore);
      setPoints((prev) => [...prev, point]);

      await pushLiveScoreIfShared(nextScore);
      if (nextScore.isMatchOver) {
        await finalize(nextScore, "COMPLETED");
      }
    } catch {
      setError("Échec de l'enregistrement du point, réessayez.");
    } finally {
      setPending(false);
    }
  }

  async function handleUndo() {
    if (pending || points.length === 0) return;
    setPending(true);
    setError(null);
    try {
      const response = await fetch(`/api/console/sessions/${session.id}/points/last`, { method: "DELETE" });
      if (!response.ok) throw new Error("undo_failed");

      const wasMatchOver = engine.currentScore.isMatchOver;
      engine.undo();
      const nextScore = engine.currentScore;
      setScore(nextScore);
      setPoints((prev) => prev.slice(0, -1));

      await pushLiveScoreIfShared(nextScore);
      if (wasMatchOver && !nextScore.isMatchOver) {
        await finalize(nextScore, "ACTIVE");
      }
    } catch {
      setError("Échec de l'annulation, réessayez.");
    } finally {
      setPending(false);
    }
  }

  return (
    <div className={styles.grid}>
      <div className={styles.leftColumn}>
        <ScoreCard score={score} selfName={SELF_NAME} opponentName={opponentName} />
        <button
          type="button"
          className={styles.undoButton}
          onClick={handleUndo}
          disabled={pending || points.length === 0}
        >
          Annuler le dernier point
        </button>
        {error && <p className={styles.error}>{error}</p>}
      </div>

      <div className={styles.centerColumn}>
        <PointButtonGrid onSelect={handlePointClick} disabled={pending || score.isMatchOver} />
        <PointStatsTiles points={points} />
      </div>

      <div className={styles.rightColumn}>
        <h3 className={styles.trailTitle}>Déroulé</h3>
        <PointTrail points={points} />
      </div>
    </div>
  );
}
