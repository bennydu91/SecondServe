import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { SetTrail } from "./SetTrail";

describe("SetTrail", () => {
  it("affiche l'initiale du joueur qui a remporté chaque point", () => {
    render(<SetTrail log={["A", "A", "B"]} playerAInitial="B" playerBInitial="M" />);
    const cells = screen.getAllByTestId("set-trail-point");
    expect(cells.map((c) => c.textContent)).toEqual(["B", "B", "M"]);
  });

  it("affiche une cellule vide pour le point en cours", () => {
    render(<SetTrail log={["A"]} playerAInitial="B" playerBInitial="M" />);
    const pending = screen.getByTestId("set-trail-pending");
    expect(pending).toBeInTheDocument();
  });
});
