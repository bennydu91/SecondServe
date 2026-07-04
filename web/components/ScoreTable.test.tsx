import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { ScoreTable } from "./ScoreTable";
import type { LiveSnapshot } from "@/lib/types";

function buildSnapshot(overrides: Partial<LiveSnapshot> = {}): LiveSnapshot {
  return {
    status: "LIVE",
    completedSets: [],
    currentSetGamesA: 0,
    currentSetGamesB: 0,
    currentSetPointLog: [],
    currentGamePointsA: "ZERO",
    currentGamePointsB: "ZERO",
    tieBreakPointsA: 0,
    tieBreakPointsB: 0,
    isTieBreak: false,
    isSuperTieBreak: false,
    matchWinner: null,
    playerAName: "Benjamin",
    playerBName: "Marceau",
    surface: "CLAY",
    tournament: null,
    competitionType: null,
    startedAt: 1000,
    ...overrides,
  };
}

describe("ScoreTable", () => {
  it("affiche les noms de joueurs et les jeux du set en cours", () => {
    render(<ScoreTable snapshot={buildSnapshot({ currentSetGamesA: 3, currentSetGamesB: 2 })} />);
    expect(screen.getByText("Benjamin")).toBeInTheDocument();
    expect(screen.getByText("Marceau")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
  });

  it("utilise des noms de repli si les joueurs ne sont pas nommés", () => {
    render(<ScoreTable snapshot={buildSnapshot({ playerAName: null, playerBName: null })} />);
    expect(screen.getByText("Joueur")).toBeInTheDocument();
    expect(screen.getByText("Adversaire")).toBeInTheDocument();
  });

  it("traduit les points de jeu en score tennis", () => {
    render(<ScoreTable snapshot={buildSnapshot({ currentGamePointsA: "FORTY", currentGamePointsB: "ADVANTAGE" })} />);
    expect(screen.getByText("40")).toBeInTheDocument();
    expect(screen.getByText("AD")).toBeInTheDocument();
  });

  it("affiche les points de tie-break au lieu du score classique", () => {
    render(<ScoreTable snapshot={buildSnapshot({ isTieBreak: true, tieBreakPointsA: 8, tieBreakPointsB: 6 })} />);
    expect(screen.getByText("8")).toBeInTheDocument();
    expect(screen.getByText("6")).toBeInTheDocument();
  });

  it("désigne le joueur B en tête quand il a gagné plus de sets terminés", () => {
    render(
      <ScoreTable
        snapshot={buildSnapshot({
          currentSetGamesA: 1,
          currentSetGamesB: 1,
          completedSets: [{ gamesA: 4, gamesB: 6 }],
        })}
      />
    );
    expect(screen.getByText("M").className).toContain("avatarLeading");
    expect(screen.getByText("B").className).toContain("avatarTrailing");
  });

  it("affiche les scores des sets déjà terminés", () => {
    render(
      <ScoreTable
        snapshot={buildSnapshot({ completedSets: [{ gamesA: 6, gamesB: 4 }] })}
      />
    );
    expect(screen.getByText("6")).toBeInTheDocument();
    expect(screen.getByText("4")).toBeInTheDocument();
  });

  it("rend le nom du joueur dans un span dédié pour permettre la troncature CSS", () => {
    render(<ScoreTable snapshot={buildSnapshot({ playerAName: "Jean-Baptiste-Alexandre" })} />);
    const nameNode = screen.getByText("Jean-Baptiste-Alexandre");
    expect(nameNode.tagName).toBe("SPAN");
    expect(nameNode.className).toContain("playerNameText");
  });
});
