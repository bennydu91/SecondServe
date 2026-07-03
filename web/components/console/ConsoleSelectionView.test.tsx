import { describe, expect, it, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import type { SessionDto } from "@/lib/types";

const pushMock = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: pushMock }) }));

import { ConsoleSelectionView } from "./ConsoleSelectionView";

function session(overrides: Partial<SessionDto> = {}): SessionDto {
  return {
    id: 1,
    surface: "CLAY",
    matchFormat: "BEST_OF_3",
    thirdSetRule: "FULL_ADVANTAGE",
    opponent: "Marceau",
    competitionType: null,
    tournament: null,
    status: "ACTIVE",
    sessionType: "MATCH",
    result: null,
    scoreText: null,
    scoreSeedJson: null,
    createdAt: 1000,
    updatedAt: 1000,
    ...overrides,
  };
}

describe("ConsoleSelectionView", () => {
  it("affiche un état vide sobre sans session active", () => {
    render(<ConsoleSelectionView activeSessions={[]} />);
    expect(screen.getByText(/aucune session active/i)).toBeInTheDocument();
  });

  it("liste les sessions actives et navigue vers la console au clic sur Reprendre", () => {
    render(<ConsoleSelectionView activeSessions={[session({ id: 7 })]} />);
    expect(screen.getByText(/marceau/i)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /reprendre/i }));
    expect(pushMock).toHaveBeenCalledWith("/dashboard/console/7");
  });

  it("affiche le formulaire Nouveau match au clic sur le bouton", () => {
    render(<ConsoleSelectionView activeSessions={[]} />);
    fireEvent.click(screen.getByRole("button", { name: /^nouveau match$/i }));
    expect(screen.getByText(/date du match/i)).toBeInTheDocument();
  });
});
