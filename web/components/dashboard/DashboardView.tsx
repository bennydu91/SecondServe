import type { AggregatedStats, MonthlyWinRate, PlayTime } from "@/lib/stats";
import { computeWinRateTrend } from "@/lib/stats";
import type { SessionDto } from "@/lib/types";
import { KpiCard } from "./KpiCard";
import { MonthlyWinRateChart } from "./MonthlyWinRateChart";
import { SurfaceBreakdown } from "./SurfaceBreakdown";
import { RecentMatchesTable } from "./RecentMatchesTable";
import { ThemeToggle } from "./ThemeToggle";
import styles from "./DashboardView.module.css";

type Props = {
  stats: AggregatedStats;
  monthlyWinRate: MonthlyWinRate[];
  playTime: PlayTime;
  recentMatches: SessionDto[];
};

function formatPercent(value: number | null): string {
  return value === null ? "—" : `${Math.round(value * 100)}`;
}

export function DashboardView({ stats, monthlyWinRate, playTime, recentMatches }: Props) {
  const trend = computeWinRateTrend(monthlyWinRate);
  const streakLabel =
    stats.activeStreak === null
      ? null
      : `${stats.activeStreak.count} ${stats.activeStreak.result === "VICTORY" ? "V" : "D"}`;

  return (
    <div>
      <div className={styles.header}>
        <h1 className={styles.title}>Tableau de bord</h1>
        <ThemeToggle />
      </div>

      <div className={styles.kpiGrid}>
        <KpiCard
          label="Win rate global"
          value={formatPercent(stats.winRateGlobal)}
          unit="%"
          subtext={trend === null ? undefined : `${trend >= 0 ? "↑" : "↓"} ${Math.abs(Math.round(trend * 100))}% vs mois dernier`}
          subtextPositive={trend !== null && trend >= 0}
        />
        <KpiCard
          label="Victoires · Défaites"
          value={`${stats.victories}·${stats.defeats}`}
          subtext={`${stats.completedMatchSessions} matchs terminés`}
        />
        <KpiCard
          label="Séquence active"
          value={streakLabel ?? "—"}
          subtext={streakLabel ? "en cours" : undefined}
        />
        <KpiCard
          label="Temps de jeu"
          value={String(Math.round(playTime.hours))}
          unit="h"
          subtext={`${playTime.sessionCount} sessions`}
        />
      </div>

      <div className={styles.middleGrid}>
        <MonthlyWinRateChart months={monthlyWinRate} />
        <SurfaceBreakdown bySurface={stats.winRateBySurface} />
      </div>

      <RecentMatchesTable matches={recentMatches} />
    </div>
  );
}
