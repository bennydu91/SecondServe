import { describe, expect, it, vi, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { ScoreSeedForm } from "./ScoreSeedForm";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("ScoreSeedForm", () => {
  it("appelle onCancel au clic sur Annuler", () => {
    const onCancel = vi.fn();
    render(<ScoreSeedForm sessionId={7} onCancel={onCancel} onSeeded={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: /annuler/i }));
    expect(onCancel).toHaveBeenCalled();
  });

  it("envoie le score de départ parsé et appelle onSeeded", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true });
    vi.stubGlobal("fetch", fetchMock);
    const onSeeded = vi.fn();
    render(<ScoreSeedForm sessionId={7} onCancel={vi.fn()} onSeeded={onSeeded} />);

    fireEvent.change(screen.getByPlaceholderText("6-4, 3-6"), { target: { value: "6-4" } });
    fireEvent.click(screen.getByRole("button", { name: /valider et reprendre/i }));

    await waitFor(() => expect(onSeeded).toHaveBeenCalled());
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/console/sessions/7/score-seed");
    const body = JSON.parse(init.body as string);
    expect(body.completedSets).toEqual([{ gamesA: 6, gamesB: 4 }]);
  });

  it("affiche une erreur si l'enregistrement échoue", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false }));
    render(<ScoreSeedForm sessionId={7} onCancel={vi.fn()} onSeeded={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: /valider et reprendre/i }));
    await waitFor(() => expect(screen.getByText(/échec de l'enregistrement/i)).toBeInTheDocument());
  });
});
