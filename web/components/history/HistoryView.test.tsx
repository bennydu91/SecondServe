import { describe, expect, it, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh: vi.fn() }) }));
import { HistoryView } from "./HistoryView";
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
    scoreText: "6-4 · 6-3",
    scoreSeedJson: null,
    createdAt: Date.UTC(2026, 0, 15),
    updatedAt: Date.UTC(2026, 0, 15),
    ...overrides,
  };
}

describe("HistoryView", () => {
  it("affiche un message quand il n'y a aucun match", () => {
    render(<HistoryView matches={[]} />);
    expect(screen.getByText("Pas encore de match")).toBeInTheDocument();
  });

  it("affiche chaque match avec ses actions Modifier/Supprimer", () => {
    render(<HistoryView matches={[buildMatch()]} />);
    expect(screen.getByText("Rafael")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /modifier/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /supprimer/i })).toBeInTheDocument();
  });

  it("pagine à 20 matchs par page", () => {
    const matches = Array.from({ length: 25 }, (_, i) =>
      buildMatch({ id: i + 1, opponent: `Joueur ${i + 1}`, createdAt: Date.UTC(2026, 0, 1) - i * 1000 })
    );
    render(<HistoryView matches={matches} />);
    expect(screen.getByText("Joueur 1")).toBeInTheDocument();
    expect(screen.queryByText("Joueur 21")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /page suivante/i }));
    expect(screen.getByText("Joueur 21")).toBeInTheDocument();
    expect(screen.queryByText("Joueur 1")).not.toBeInTheDocument();
  });

  it("revient sur la dernière page valide si les matchs restants tiennent sur une page après suppression", () => {
    const matches = Array.from({ length: 21 }, (_, i) =>
      buildMatch({ id: i + 1, opponent: `Joueur ${i + 1}`, createdAt: Date.UTC(2026, 0, 1) - i * 1000 })
    );
    const { rerender } = render(<HistoryView matches={matches} />);

    fireEvent.click(screen.getByRole("button", { name: /page suivante/i }));
    expect(screen.getByText("Joueur 21")).toBeInTheDocument();

    // Simule le router.refresh() après suppression du dernier match de la page 2 :
    // le composant est re-rendu avec moins de matchs, mais son état `page` interne persiste.
    rerender(<HistoryView matches={matches.slice(0, 20)} />);

    expect(screen.getByText("Joueur 1")).toBeInTheDocument();
    expect(screen.queryByText("Joueur 21")).not.toBeInTheDocument();
    expect(screen.queryByText(/pas encore de match/i)).not.toBeInTheDocument();
  });

  it("ne montre pas de bouton page suivante s'il y a moins de 20 matchs", () => {
    render(<HistoryView matches={[buildMatch()]} />);
    expect(screen.queryByRole("button", { name: /page suivante/i })).not.toBeInTheDocument();
  });

  it("ouvre le formulaire d'édition au clic sur Modifier", () => {
    render(<HistoryView matches={[buildMatch()]} />);
    fireEvent.click(screen.getByRole("button", { name: /modifier/i }));
    expect(screen.getByLabelText("Adversaire")).toHaveValue("Rafael");
  });

  it("supprime le match et rafraîchit la liste au clic sur Confirmer", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true });
    vi.stubGlobal("fetch", fetchMock);
    render(<HistoryView matches={[buildMatch()]} />);

    fireEvent.click(screen.getByRole("button", { name: "Supprimer" }));
    fireEvent.click(screen.getByRole("button", { name: /confirmer/i }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith("/api/console/sessions/1", { method: "DELETE" }));
    vi.unstubAllGlobals();
  });
});
