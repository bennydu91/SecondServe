import { describe, expect, it, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import DashboardError from "./error";

describe("DashboardError", () => {
  it("affiche le message d'erreur et appelle reset au clic", () => {
    const reset = vi.fn();
    render(<DashboardError error={new Error("boom")} reset={reset} />);
    expect(screen.getByText("Impossible de charger le tableau de bord")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Réessayer" }));
    expect(reset).toHaveBeenCalledTimes(1);
  });
});
