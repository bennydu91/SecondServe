import { describe, expect, it } from "vitest";
import {
  computeStats,
  computeMonthlyWinRate,
  computeWinRateTrend,
  computePlayTime,
} from "./stats";
import type { SessionDto } from "./types";

function fakeSession(overrides: Partial<SessionDto> & { id: number }): SessionDto {
  return {
    surface: "CLAY",
    matchFormat: "BEST_OF_3",
    thirdSetRule: "FULL_ADVANTAGE",
    opponent: null,
    competitionType: null,
    tournament: null,
    status: "COMPLETED",
    sessionType: "MATCH",
    result: "VICTORY",
    createdAt: Date.now(),
    updatedAt: Date.now(),
    ...overrides,
  };
}

describe("computeStats", () => {
  it("aucune session : compteurs à zéro, win rate et séquence nuls", () => {
    const stats = computeStats([]);
    expect(stats.totalMatchSessions).toBe(0);
    expect(stats.totalTrainingSessions).toBe(0);
    expect(stats.completedMatchSessions).toBe(0);
    expect(stats.winRateGlobal).toBeNull();
    expect(stats.activeStreak).toBeNull();
    expect(stats.winRateBySurface).toEqual([]);
  });

  it("3 victoires sur terre battue : win rate 100%, surface affichée, séquence de 3 victoires", () => {
    const sessions = [
      fakeSession({ id: 1, result: "VICTORY", surface: "CLAY", createdAt: 3000 }),
      fakeSession({ id: 2, result: "VICTORY", surface: "CLAY", createdAt: 2000 }),
      fakeSession({ id: 3, result: "VICTORY", surface: "CLAY", createdAt: 1000 }),
    ];
    const stats = computeStats(sessions);
    expect(stats.winRateGlobal).toBe(1);
    expect(stats.winRateBySurface).toHaveLength(1);
    expect(stats.winRateBySurface[0].winRatePercent).toBe(1);
    expect(stats.activeStreak).toEqual({ result: "VICTORY", count: 3 });
  });

  it("2 matchs sur une surface : win rate par surface nul (données insuffisantes, seuil 3)", () => {
    const sessions = [
      fakeSession({ id: 1, result: "VICTORY", surface: "HARD" }),
      fakeSession({ id: 2, result: "DEFEAT", surface: "HARD" }),
    ];
    const stats = computeStats(sessions);
    expect(stats.winRateBySurface).toHaveLength(1);
    expect(stats.winRateBySurface[0].winRatePercent).toBeNull();
  });

  it("la séquence se rompt : 1 défaite après 2 victoires -> séquence Defeats(1)", () => {
    const sessions = [
      fakeSession({ id: 1, result: "DEFEAT", createdAt: 3000 }),
      fakeSession({ id: 2, result: "VICTORY", createdAt: 2000 }),
      fakeSession({ id: 3, result: "VICTORY", createdAt: 1000 }),
    ];
    const stats = computeStats(sessions);
    expect(stats.activeStreak).toEqual({ result: "DEFEAT", count: 1 });
  });

  it("les entraînements comptent dans totalTrainingSessions mais pas dans le win rate", () => {
    const sessions = [
      fakeSession({ id: 1, sessionType: "TRAINING", result: null }),
      fakeSession({ id: 2, result: "VICTORY" }),
    ];
    const stats = computeStats(sessions);
    expect(stats.totalTrainingSessions).toBe(1);
    expect(stats.totalMatchSessions).toBe(1);
    expect(stats.completedMatchSessions).toBe(1);
  });

  it("DRAW et ABANDONED ne comptent pas dans le win rate", () => {
    const sessions = [
      fakeSession({ id: 1, result: "DRAW" }),
      fakeSession({ id: 2, result: "ABANDONED" }),
      fakeSession({ id: 3, result: "VICTORY" }),
    ];
    const stats = computeStats(sessions);
    expect(stats.completedMatchSessions).toBe(1);
    expect(stats.winRateGlobal).toBe(1);
  });

  it("win rate 50% avec 2 victoires et 2 défaites sur la même surface", () => {
    const sessions = [
      fakeSession({ id: 1, result: "VICTORY", surface: "HARD", createdAt: 4000 }),
      fakeSession({ id: 2, result: "DEFEAT", surface: "HARD", createdAt: 3000 }),
      fakeSession({ id: 3, result: "VICTORY", surface: "HARD", createdAt: 2000 }),
      fakeSession({ id: 4, result: "DEFEAT", surface: "HARD", createdAt: 1000 }),
    ];
    const stats = computeStats(sessions);
    expect(stats.winRateGlobal).toBe(0.5);
    expect(stats.winRateBySurface[0].winRatePercent).toBe(0.5);
  });

  it("une session INTERRUPTED n'est pas comptée comme terminée", () => {
    const sessions = [fakeSession({ id: 1, status: "INTERRUPTED", result: "VICTORY" })];
    const stats = computeStats(sessions);
    expect(stats.completedMatchSessions).toBe(0);
    expect(stats.winRateGlobal).toBeNull();
  });

  it("les surfaces sont triées par nombre de matchs décroissant", () => {
    const sessions = [
      fakeSession({ id: 1, result: "VICTORY", surface: "CLAY", createdAt: 5000 }),
      fakeSession({ id: 2, result: "VICTORY", surface: "CLAY", createdAt: 4000 }),
      fakeSession({ id: 3, result: "VICTORY", surface: "CLAY", createdAt: 3000 }),
      fakeSession({ id: 4, result: "VICTORY", surface: "HARD", createdAt: 2000 }),
      fakeSession({ id: 5, result: "DEFEAT", surface: "HARD", createdAt: 1000 }),
    ];
    const stats = computeStats(sessions);
    expect(stats.winRateBySurface.map((s) => s.surface)).toEqual(["CLAY", "HARD"]);
  });

  it("une session INTERRUPTED avec résultat DEFEAT rompt quand même une séquence de victoires", () => {
    const sessions = [
      fakeSession({ id: 1, status: "INTERRUPTED", result: "DEFEAT", createdAt: 3000 }),
      fakeSession({ id: 2, result: "VICTORY", createdAt: 2000 }),
      fakeSession({ id: 3, result: "VICTORY", createdAt: 1000 }),
    ];
    const stats = computeStats(sessions);
    expect(stats.activeStreak).toEqual({ result: "DEFEAT", count: 1 });
    expect(stats.completedMatchSessions).toBe(2);
    expect(stats.winRateGlobal).toBe(1);
  });
});

describe("computePlayTime", () => {
  it("additionne la durée des sessions terminées (matchs + entraînements), ignore les autres", () => {
    const oneHourMs = 60 * 60 * 1000;
    const sessions = [
      fakeSession({ id: 1, status: "COMPLETED", createdAt: 0, updatedAt: oneHourMs }),
      fakeSession({ id: 2, sessionType: "TRAINING", status: "COMPLETED", createdAt: 0, updatedAt: 2 * oneHourMs }),
      fakeSession({ id: 3, status: "ACTIVE", createdAt: 0, updatedAt: oneHourMs }),
    ];
    const playTime = computePlayTime(sessions);
    expect(playTime.hours).toBe(3);
    expect(playTime.sessionCount).toBe(2);
  });

  it("aucune session terminée : zéro heure, zéro session", () => {
    const playTime = computePlayTime([]);
    expect(playTime.hours).toBe(0);
    expect(playTime.sessionCount).toBe(0);
  });
});

describe("computeMonthlyWinRate", () => {
  it("retourne 5 mois se terminant au mois courant, le dernier marqué isCurrentMonth", () => {
    const now = new Date(2026, 5, 15); // 15 juin 2026
    const months = computeMonthlyWinRate([], now);
    expect(months).toHaveLength(5);
    expect(months.map((m) => m.monthLabel)).toEqual(["Fév", "Mar", "Avr", "Mai", "Juin"]);
    expect(months[4].isCurrentMonth).toBe(true);
    expect(months[0].isCurrentMonth).toBe(false);
  });

  it("un mois sans match terminé a un winRatePercent nul (pas de donnée fabriquée)", () => {
    const now = new Date(2026, 5, 15);
    const months = computeMonthlyWinRate([], now);
    expect(months.every((m) => m.winRatePercent === null)).toBe(true);
  });

  it("calcule le win rate du mois courant à partir des matchs de ce mois", () => {
    const now = new Date(2026, 5, 15);
    const sessions = [
      fakeSession({ id: 1, result: "VICTORY", createdAt: new Date(2026, 5, 1).getTime() }),
      fakeSession({ id: 2, result: "DEFEAT", createdAt: new Date(2026, 5, 10).getTime() }),
    ];
    const months = computeMonthlyWinRate(sessions, now);
    expect(months[4].winRatePercent).toBe(0.5);
  });
});

describe("computeWinRateTrend", () => {
  it("nul si moins de 2 mois disponibles", () => {
    expect(computeWinRateTrend([{ monthLabel: "Juin", winRatePercent: 1, isCurrentMonth: true }])).toBeNull();
  });

  it("nul si le mois courant ou le précédent n'a pas de donnée", () => {
    const months = [
      { monthLabel: "Mai", winRatePercent: null, isCurrentMonth: false },
      { monthLabel: "Juin", winRatePercent: 0.8, isCurrentMonth: true },
    ];
    expect(computeWinRateTrend(months)).toBeNull();
  });

  it("calcule la différence entre le mois courant et le précédent", () => {
    const months = [
      { monthLabel: "Mai", winRatePercent: 0.5, isCurrentMonth: false },
      { monthLabel: "Juin", winRatePercent: 0.8, isCurrentMonth: true },
    ];
    expect(computeWinRateTrend(months)).toBeCloseTo(0.3);
  });
});
