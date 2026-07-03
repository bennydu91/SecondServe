import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { MonthlyWinRateChart } from "./MonthlyWinRateChart";
import type { MonthlyWinRate } from "@/lib/stats";

describe("MonthlyWinRateChart", () => {
  it("affiche un mois vide sans barre de win rate", () => {
    const months: MonthlyWinRate[] = [{ monthLabel: "Jan", winRatePercent: null, isCurrentMonth: false }];
    render(<MonthlyWinRateChart months={months} />);
    expect(screen.getByText("Jan")).toBeInTheDocument();
    expect(screen.getByTitle("Pas de match ce mois-ci")).toBeInTheDocument();
  });

  it("affiche une barre proportionnelle au win rate pour un mois joué", () => {
    const months: MonthlyWinRate[] = [{ monthLabel: "Fév", winRatePercent: 0.75, isCurrentMonth: true }];
    render(<MonthlyWinRateChart months={months} />);
    const label = screen.getByText("Fév");
    expect(label).toBeInTheDocument();
    expect(screen.queryByTitle("Pas de match ce mois-ci")).not.toBeInTheDocument();
  });
});
