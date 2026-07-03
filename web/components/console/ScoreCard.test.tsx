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
});
