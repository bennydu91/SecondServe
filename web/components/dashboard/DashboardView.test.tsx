import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { DashboardView } from "./DashboardView";
import type { AggregatedStats, MonthlyWinRate, PlayTime } from "@/lib/stats";
import type { SessionDto } from "@/lib/types";

function buildStats(overrides: Partial<AggregatedStats> = {}): AggregatedStats {
  return {
    totalMatchSessions: 10,
    totalTrainingSessions: 2,
    completedMatchSessions: 8,
    victories: 5,
    defeats: 3,
    winRateGlobal: 0.625,
    winRateBySurface: [],
    activeStreak: null,
    ...overrides,
  };
}

const playTime: PlayTime = { hours: 12.4, sessionCount: 6 };

describe("DashboardView", () => {
  it("affiche le win rate global arrondi en pourcentage", () => {
    render(<DashboardView stats={buildStats()} monthlyWinRate={[]} playTime={playTime} recentMatches={[]} />);
    expect(screen.getByText("63")).toBeInTheDocument();
    expect(screen.getByText("5·3")).toBeInTheDocument();
    expect(screen.getByText("8 matchs terminés")).toBeInTheDocument();
  });

  it("affiche un tiret quand le win rate global est indisponible", () => {
    render(
      <DashboardView
        stats={buildStats({ winRateGlobal: null, activeStreak: { result: "VICTORY", count: 1 } })}
        monthlyWinRate={[]}
        playTime={playTime}
        recentMatches={[]}
      />
    );
    expect(screen.getByText("—")).toBeInTheDocument();
  });

  it("affiche la séquence active et son sens (victoires ou défaites)", () => {
    render(
      <DashboardView
        stats={buildStats({ activeStreak: { result: "VICTORY", count: 3 } })}
        monthlyWinRate={[]}
        playTime={playTime}
        recentMatches={[]}
      />
    );
    expect(screen.getByText("3 V")).toBeInTheDocument();
    expect(screen.getByText("en cours")).toBeInTheDocument();
  });

  it("affiche un tiret sans mention 'en cours' quand aucune séquence n'est active", () => {
    render(<DashboardView stats={buildStats()} monthlyWinRate={[]} playTime={playTime} recentMatches={[]} />);
    expect(screen.queryByText("en cours")).not.toBeInTheDocument();
  });

  it("affiche la tendance du win rate mensuel par rapport au mois précédent", () => {
    const monthlyWinRate: MonthlyWinRate[] = [
      { monthLabel: "Nov", winRatePercent: 0.4, isCurrentMonth: false },
      { monthLabel: "Déc", winRatePercent: 0.6, isCurrentMonth: true },
    ];
    render(<DashboardView stats={buildStats()} monthlyWinRate={monthlyWinRate} playTime={playTime} recentMatches={[]} />);
    expect(screen.getByText(/↑ 20% vs mois dernier/)).toBeInTheDocument();
  });

  it("arrondit les heures de jeu et affiche le nombre de sessions", () => {
    render(<DashboardView stats={buildStats()} monthlyWinRate={[]} playTime={{ hours: 12.6, sessionCount: 4 }} recentMatches={[]} />);
    expect(screen.getByText("13")).toBeInTheDocument();
    expect(screen.getByText("4 sessions")).toBeInTheDocument();
  });

  it("transmet les derniers matchs au tableau", () => {
    const recentMatches: SessionDto[] = [
      {
        id: 1,
        surface: "CLAY",
        matchFormat: "BEST_OF_3",
        thirdSetRule: "FULL_ADVANTAGE",
        opponent: "Novak",
        competitionType: null,
        tournament: null,
        status: "COMPLETED",
        sessionType: "MATCH",
        result: "VICTORY",
        scoreText: "6-4 6-3",
        scoreSeedJson: null,
        createdAt: Date.UTC(2026, 0, 15),
        updatedAt: Date.UTC(2026, 0, 15),
      },
    ];
    render(<DashboardView stats={buildStats()} monthlyWinRate={[]} playTime={playTime} recentMatches={recentMatches} />);
    expect(screen.getByText("Novak")).toBeInTheDocument();
  });
});
