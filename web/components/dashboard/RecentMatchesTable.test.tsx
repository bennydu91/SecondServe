import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { RecentMatchesTable } from "./RecentMatchesTable";
import type { SessionDto } from "@/lib/types";

function buildMatch(overrides: Partial<SessionDto> = {}): SessionDto {
  return {
    id: 1,
    surface: "CLAY",
    matchFormat: "BEST_OF_3",
    thirdSetRule: "FULL_ADVANTAGE",
    opponent: "Rafael",
    competitionType: null,
    tournament: null,
    status: "COMPLETED",
    sessionType: "MATCH",
    result: "VICTORY",
    scoreText: "6-4 6-3",
    scoreSeedJson: null,
    createdAt: Date.UTC(2026, 0, 15),
    updatedAt: Date.UTC(2026, 0, 15),
    ...overrides,
  };
}

describe("RecentMatchesTable", () => {
  it("affiche un message quand il n'y a aucun match", () => {
    render(<RecentMatchesTable matches={[]} />);
    expect(screen.getByText("Pas encore de match")).toBeInTheDocument();
  });

  it("affiche l'adversaire, le score et le badge victoire", () => {
    render(<RecentMatchesTable matches={[buildMatch()]} />);
    expect(screen.getByText("Rafael")).toBeInTheDocument();
    expect(screen.getByText("6-4 6-3")).toBeInTheDocument();
    expect(screen.getByText("VICTOIRE")).toBeInTheDocument();
  });

  it("affiche le badge défaite", () => {
    render(<RecentMatchesTable matches={[buildMatch({ result: "DEFEAT" })]} />);
    expect(screen.getByText("DÉFAITE")).toBeInTheDocument();
  });

  it("utilise des valeurs de repli pour l'adversaire et le score manquants", () => {
    render(<RecentMatchesTable matches={[buildMatch({ opponent: null, scoreText: null, result: null })]} />);
    expect(screen.getAllByText("Adversaire")).toHaveLength(2);
    expect(screen.getByText("—")).toBeInTheDocument();
  });
});
