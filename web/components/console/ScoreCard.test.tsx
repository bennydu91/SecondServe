import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { emptyMatchScore } from "@/lib/scoreEngine";
import { ScoreCard } from "./ScoreCard";

describe("ScoreCard", () => {
  it("affiche les deux noms de joueur", () => {
    render(<ScoreCard score={emptyMatchScore()} selfName="Benjamin" opponentName="Marceau" />);
    expect(screen.getByText("Benjamin")).toBeInTheDocument();
    expect(screen.getByText("Marceau")).toBeInTheDocument();
  });

  it("affiche les points au format 15/30/40/AD", () => {
    const score = { ...emptyMatchScore(), currentGamePointsA: "FORTY" as const };
    render(<ScoreCard score={score} selfName="Benjamin" opponentName="Marceau" />);
    expect(screen.getByText("40")).toBeInTheDocument();
  });

  it("affiche le score de tie-break au lieu des points classiques", () => {
    const score = { ...emptyMatchScore(), isTieBreak: true, tieBreakPointsA: 5 };
    render(<ScoreCard score={score} selfName="Benjamin" opponentName="Marceau" />);
    expect(screen.getByText("5")).toBeInTheDocument();
  });

  it("affiche la bannière de fin de match avec le nom du vainqueur", () => {
    const score = { ...emptyMatchScore(), isMatchOver: true, matchWinner: "A" as const };
    render(<ScoreCard score={score} selfName="Benjamin" opponentName="Marceau" />);
    expect(screen.getByText(/match terminé/i)).toHaveTextContent("Benjamin");
  });

  it("met en évidence l'adversaire quand il mène le set en cours, même à sets à égalité", () => {
    // Sets à égalité (0-0), mais Benjamin est mené 3-5 dans le set en cours :
    // c'est bien Marceau qui doit être mis en évidence, pas Benjamin par défaut.
    const score = { ...emptyMatchScore(), currentSetGamesA: 3, currentSetGamesB: 5 };
    render(<ScoreCard score={score} selfName="Benjamin" opponentName="Marceau" />);

    const benjaminRow = screen.getByText("Benjamin").closest(".playerRow");
    const marceauRow = screen.getByText("Marceau").closest(".playerRow");

    expect(benjaminRow?.className).not.toContain("playerRowLeading");
    expect(marceauRow?.className).toContain("playerRowLeading");
  });

  it("met en évidence le joueur qui mène au nombre de sets, même s'il est mené dans le set en cours", () => {
    // Benjamin a déjà gagné 1 set (6-2), et bien qu'il soit mené 1-3 dans le set en cours,
    // il reste le joueur en tête du match.
    const score = {
      ...emptyMatchScore(),
      completedSets: [{ gamesA: 6, gamesB: 2 }],
      currentSetGamesA: 1,
      currentSetGamesB: 3,
    };
    render(<ScoreCard score={score} selfName="Benjamin" opponentName="Marceau" />);

    const benjaminRow = screen.getByText("Benjamin").closest(".playerRow");
    const marceauRow = screen.getByText("Marceau").closest(".playerRow");

    expect(benjaminRow?.className).toContain("playerRowLeading");
    expect(marceauRow?.className).not.toContain("playerRowLeading");
  });
});
