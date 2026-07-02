import type { SessionDto } from "./types";

export type SurfaceWinRate = {
  surface: string;
  matchCount: number;
  victories: number;
  winRatePercent: number | null;
};

export type ActiveStreak = { result: "VICTORY" | "DEFEAT"; count: number };

export type AggregatedStats = {
  totalMatchSessions: number;
  totalTrainingSessions: number;
  completedMatchSessions: number;
  victories: number;
  defeats: number;
  winRateGlobal: number | null;
  winRateBySurface: SurfaceWinRate[];
  activeStreak: ActiveStreak | null;
};

export type PlayTime = { hours: number; sessionCount: number };

export type MonthlyWinRate = { monthLabel: string; winRatePercent: number | null; isCurrentMonth: boolean };

const SCORED_RESULTS = new Set(["VICTORY", "DEFEAT"]);
const MIN_MATCHES_FOR_SURFACE_RATE = 3;
const MONTH_LABELS = ["Jan", "Fév", "Mar", "Avr", "Mai", "Juin", "Juil", "Août", "Sep", "Oct", "Nov", "Déc"];
const MONTHLY_CHART_SPAN = 5;

function isScored(session: SessionDto): boolean {
  return session.result !== null && SCORED_RESULTS.has(session.result);
}

export function computeStats(sessions: SessionDto[]): AggregatedStats {
  const allMatch = sessions.filter((s) => s.sessionType === "MATCH");
  const allTraining = sessions.filter((s) => s.sessionType === "TRAINING");

  const scored = allMatch.filter((s) => s.status === "COMPLETED" && isScored(s));
  const victories = scored.filter((s) => s.result === "VICTORY").length;
  const defeats = scored.filter((s) => s.result === "DEFEAT").length;
  const winRateGlobal = scored.length === 0 ? null : victories / scored.length;

  const bySurfaceMap = new Map<string, SessionDto[]>();
  for (const session of scored) {
    const key = session.surface || "INCONNUE";
    const list = bySurfaceMap.get(key) ?? [];
    list.push(session);
    bySurfaceMap.set(key, list);
  }
  const winRateBySurface: SurfaceWinRate[] = Array.from(bySurfaceMap.entries())
    .map(([surface, list]) => {
      const surfaceVictories = list.filter((s) => s.result === "VICTORY").length;
      return {
        surface,
        matchCount: list.length,
        victories: surfaceVictories,
        winRatePercent: list.length >= MIN_MATCHES_FOR_SURFACE_RATE ? surfaceVictories / list.length : null,
      };
    })
    .sort((a, b) => b.matchCount - a.matchCount);

  const allWithResult = allMatch
    .filter(isScored)
    .sort((a, b) => b.createdAt - a.createdAt || b.id - a.id);
  const activeStreak = computeStreak(allWithResult);

  return {
    totalMatchSessions: allMatch.length,
    totalTrainingSessions: allTraining.length,
    completedMatchSessions: scored.length,
    victories,
    defeats,
    winRateGlobal,
    winRateBySurface,
    activeStreak,
  };
}

function computeStreak(sortedSessions: SessionDto[]): ActiveStreak | null {
  if (sortedSessions.length === 0) return null;
  const firstResult = sortedSessions[0].result;
  if (firstResult !== "VICTORY" && firstResult !== "DEFEAT") return null;
  let count = 0;
  for (const session of sortedSessions) {
    if (session.result !== firstResult) break;
    count += 1;
  }
  return { result: firstResult, count };
}

export function computePlayTime(sessions: SessionDto[]): PlayTime {
  const completed = sessions.filter((s) => s.status === "COMPLETED");
  const totalMs = completed.reduce((sum, s) => sum + Math.max(0, s.updatedAt - s.createdAt), 0);
  return { hours: totalMs / (1000 * 60 * 60), sessionCount: completed.length };
}

export function computeMonthlyWinRate(sessions: SessionDto[], now: Date = new Date()): MonthlyWinRate[] {
  const scored = sessions.filter((s) => s.sessionType === "MATCH" && s.status === "COMPLETED" && isScored(s));
  const months: MonthlyWinRate[] = [];
  for (let offset = MONTHLY_CHART_SPAN - 1; offset >= 0; offset--) {
    const monthDate = new Date(now.getFullYear(), now.getMonth() - offset, 1);
    const matches = scored.filter((s) => {
      const d = new Date(s.createdAt);
      return d.getFullYear() === monthDate.getFullYear() && d.getMonth() === monthDate.getMonth();
    });
    const victories = matches.filter((s) => s.result === "VICTORY").length;
    months.push({
      monthLabel: MONTH_LABELS[monthDate.getMonth()],
      winRatePercent: matches.length === 0 ? null : victories / matches.length,
      isCurrentMonth: offset === 0,
    });
  }
  return months;
}

export function computeWinRateTrend(monthly: MonthlyWinRate[]): number | null {
  if (monthly.length < 2) return null;
  const current = monthly[monthly.length - 1];
  const previous = monthly[monthly.length - 2];
  if (current.winRatePercent === null || previous.winRatePercent === null) return null;
  return current.winRatePercent - previous.winRatePercent;
}
