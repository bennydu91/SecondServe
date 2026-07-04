import { describe, expect, it, vi, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MatchEditForm } from "./MatchEditForm";
import type { SessionDto } from "@/lib/types";

afterEach(() => {
  vi.unstubAllGlobals();
});

function buildMatch(overrides: Partial<SessionDto> = {}): SessionDto {
  return {
    id: 5,
    surface: "CLAY",
    matchFormat: "BEST_OF_3",
    thirdSetRule: "FULL_ADVANTAGE",
    opponent: "Rafael",
    competitionType: null,
    tournament: null,
    status: "COMPLETED",
    sessionType: "MATCH",
    result: "DEFEAT",
    scoreText: "4-6 · 3-6",
    scoreSeedJson: null,
    createdAt: Date.UTC(2026, 0, 15),
    updatedAt: Date.UTC(2026, 0, 15),
    ...overrides,
  };
}

describe("MatchEditForm", () => {
  it("pré-remplit l'adversaire et la surface depuis le match fourni", () => {
    render(<MatchEditForm match={buildMatch()} onCancel={vi.fn()} onSaved={vi.fn()} />);
    expect(screen.getByLabelText("Adversaire")).toHaveValue("Rafael");
    expect(screen.getByLabelText("Surface")).toHaveValue("CLAY");
  });

  it("pré-remplit les sets depuis scoreText", () => {
    render(<MatchEditForm match={buildMatch()} onCancel={vi.fn()} onSaved={vi.fn()} />);
    expect(screen.getByLabelText("Set 1 - jeux moi")).toHaveValue(4);
    expect(screen.getByLabelText("Set 1 - jeux adversaire")).toHaveValue(6);
    expect(screen.getByLabelText("Set 2 - jeux moi")).toHaveValue(3);
    expect(screen.getByLabelText("Set 2 - jeux adversaire")).toHaveValue(6);
  });

  it("appelle onCancel au clic sur Annuler", () => {
    const onCancel = vi.fn();
    render(<MatchEditForm match={buildMatch()} onCancel={onCancel} onSaved={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: /annuler/i }));
    expect(onCancel).toHaveBeenCalled();
  });

  it("soumet le PATCH avec les champs modifiés et le score recalculé, puis appelle onSaved", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ id: 5 }) });
    vi.stubGlobal("fetch", fetchMock);
    const onSaved = vi.fn();

    render(<MatchEditForm match={buildMatch()} onCancel={vi.fn()} onSaved={onSaved} />);
    fireEvent.change(screen.getByLabelText("Adversaire"), { target: { value: "Novak" } });
    fireEvent.change(screen.getByLabelText("Set 1 - jeux moi"), { target: { value: "6" } });
    fireEvent.change(screen.getByLabelText("Set 1 - jeux adversaire"), { target: { value: "4" } });
    fireEvent.change(screen.getByLabelText("Set 2 - jeux moi"), { target: { value: "6" } });
    fireEvent.change(screen.getByLabelText("Set 2 - jeux adversaire"), { target: { value: "3" } });

    fireEvent.click(screen.getByRole("button", { name: /enregistrer/i }));
    await waitFor(() => expect(onSaved).toHaveBeenCalled());

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/console/sessions/5");
    expect(init.method).toBe("PATCH");
    const body = JSON.parse(init.body as string);
    expect(body.opponent).toBe("Novak");
    expect(body.result).toBe("VICTORY");
    expect(body.scoreText).toBe("6-4 · 6-3");
  });

  it("affiche une erreur si le PATCH échoue", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false }));
    render(<MatchEditForm match={buildMatch()} onCancel={vi.fn()} onSaved={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: /enregistrer/i }));
    await waitFor(() => expect(screen.getByText(/échec de la mise à jour/i)).toBeInTheDocument());
  });
});
