import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { SurfaceBreakdown } from "./SurfaceBreakdown";
import type { SurfaceWinRate } from "@/lib/stats";

describe("SurfaceBreakdown", () => {
  it("affiche un tiret pour une surface sans win rate calculable", () => {
    const bySurface: SurfaceWinRate[] = [{ surface: "CLAY", matchCount: 1, victories: 1, winRatePercent: null }];
    render(<SurfaceBreakdown bySurface={bySurface} />);
    expect(screen.getByText("Terre battue")).toBeInTheDocument();
    expect(screen.getByText("—")).toBeInTheDocument();
  });

  it("affiche le pourcentage arrondi pour une surface avec win rate", () => {
    const bySurface: SurfaceWinRate[] = [{ surface: "HARD", matchCount: 5, victories: 3, winRatePercent: 0.6 }];
    render(<SurfaceBreakdown bySurface={bySurface} />);
    expect(screen.getByText("Dur")).toBeInTheDocument();
    expect(screen.getByText("60%")).toBeInTheDocument();
  });
});
