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

  it("transmet les jeux du set en cours et le point du jeu sélectionné", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true });
    vi.stubGlobal("fetch", fetchMock);
    render(<ScoreSeedForm sessionId={7} onCancel={vi.fn()} onSeeded={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Jeux (moi)"), { target: { value: "4" } });
    fireEvent.change(screen.getByLabelText("Jeux (adversaire)"), { target: { value: "3" } });
    fireEvent.change(screen.getByLabelText("Point du jeu (moi)"), { target: { value: "FORTY" } });
    fireEvent.change(screen.getByLabelText("Point du jeu (adversaire)"), { target: { value: "THIRTY" } });
    fireEvent.click(screen.getByRole("button", { name: /valider et reprendre/i }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const [, init] = fetchMock.mock.calls[0];
    const body = JSON.parse(init.body as string);
    expect(body).toMatchObject({
      currentSetGamesA: 4,
      currentSetGamesB: 3,
      currentGamePointsA: "FORTY",
      currentGamePointsB: "THIRTY",
      isTieBreak: false,
    });
  });

  it("bascule vers les points de tie-break quand la case est cochée", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true });
    vi.stubGlobal("fetch", fetchMock);
    render(<ScoreSeedForm sessionId={7} onCancel={vi.fn()} onSeeded={vi.fn()} />);

    fireEvent.click(screen.getByLabelText(/est un tie-break/i));
    expect(screen.queryByLabelText("Point du jeu (moi)")).not.toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Points tie-break (moi)"), { target: { value: "8" } });
    fireEvent.change(screen.getByLabelText("Points tie-break (adversaire)"), { target: { value: "6" } });
    fireEvent.click(screen.getByRole("button", { name: /valider et reprendre/i }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const [, init] = fetchMock.mock.calls[0];
    const body = JSON.parse(init.body as string);
    expect(body).toMatchObject({ isTieBreak: true, tieBreakPointsA: 8, tieBreakPointsB: 6 });
  });
});
