import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { KpiCard } from "./KpiCard";

describe("KpiCard", () => {
  it("affiche le label et la valeur", () => {
    render(<KpiCard label="Win rate global" value="62" />);
    expect(screen.getByText("Win rate global")).toBeInTheDocument();
    expect(screen.getByText("62")).toBeInTheDocument();
  });

  it("affiche l'unité quand elle est fournie", () => {
    render(<KpiCard label="Temps de jeu" value="12" unit="h" />);
    expect(screen.getByText("h")).toBeInTheDocument();
  });

  it("n'affiche pas de sous-texte quand il est absent", () => {
    render(<KpiCard label="Séquence active" value="—" />);
    expect(screen.queryByText(/vs mois dernier/)).not.toBeInTheDocument();
  });

  it("affiche le sous-texte fourni", () => {
    render(<KpiCard label="Win rate global" value="62" subtext="↑ 5% vs mois dernier" />);
    expect(screen.getByText("↑ 5% vs mois dernier")).toBeInTheDocument();
  });
});
