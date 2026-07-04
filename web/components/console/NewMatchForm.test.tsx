import { describe, expect, it, vi, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";

const pushMock = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: pushMock }) }));

import { NewMatchForm } from "./NewMatchForm";

afterEach(() => {
  vi.unstubAllGlobals();
  pushMock.mockClear();
});

describe("NewMatchForm", () => {
  it("appelle onCancel au clic sur Annuler", () => {
    const onCancel = vi.fn();
    render(<NewMatchForm onCancel={onCancel} />);
    fireEvent.click(screen.getByRole("button", { name: /annuler/i }));
    expect(onCancel).toHaveBeenCalled();
  });

  it("soumet le formulaire et redirige vers la console de la session créée", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({ ok: true, json: async () => ({ id: 99 }) })
    );
    render(<NewMatchForm onCancel={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: /créer et commencer/i }));
    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/dashboard/console/99"));
  });

  it("affiche une erreur si la création échoue", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false }));
    render(<NewMatchForm onCancel={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: /créer et commencer/i }));
    await waitFor(() => expect(screen.getByText(/échec de la création/i)).toBeInTheDocument());
  });

  it("transmet les valeurs modifiées des champs à la création de session", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ id: 1 }) });
    vi.stubGlobal("fetch", fetchMock);
    render(<NewMatchForm onCancel={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Surface"), { target: { value: "HARD" } });
    fireEvent.change(screen.getByLabelText("Format"), { target: { value: "BEST_OF_1" } });
    fireEvent.change(screen.getByLabelText("Règle du 3e set"), { target: { value: "SUPER_TIE_BREAK_10" } });
    fireEvent.change(screen.getByLabelText("Adversaire"), { target: { value: "Novak" } });
    fireEvent.change(screen.getByLabelText("Date du match"), { target: { value: "2026-01-15" } });

    fireEvent.click(screen.getByRole("button", { name: /créer et commencer/i }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());

    const [, init] = fetchMock.mock.calls[0];
    const body = JSON.parse(init.body as string);
    expect(body).toMatchObject({
      surface: "HARD",
      matchFormat: "BEST_OF_1",
      thirdSetRule: "SUPER_TIE_BREAK_10",
      opponent: "Novak",
    });
  });

  it("affiche le mode « Match en cours » par défaut, sans les champs de score", () => {
    render(<NewMatchForm onCancel={vi.fn()} />);
    expect(screen.queryByLabelText("Set 1 - jeux moi")).not.toBeInTheDocument();
  });

  it("affiche les champs de score en mode « Match déjà joué »", () => {
    render(<NewMatchForm onCancel={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: /match déjà joué/i }));
    expect(screen.getByLabelText("Set 1 - jeux moi")).toBeInTheDocument();
    expect(screen.getByLabelText("Set 2 - jeux moi")).toBeInTheDocument();
  });

  it("en mode PAST, crée la session puis la finalise avec le score des sets, et redirige vers /dashboard", async () => {
    const fetchMock = vi.fn().mockImplementation((url: string) => {
      if (String(url).endsWith("/api/console/sessions")) {
        return Promise.resolve({ ok: true, json: async () => ({ id: 42 }) });
      }
      return Promise.resolve({ ok: true, json: async () => ({ id: 42 }) });
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<NewMatchForm onCancel={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: /match déjà joué/i }));
    fireEvent.change(screen.getByLabelText("Set 1 - jeux moi"), { target: { value: "6" } });
    fireEvent.change(screen.getByLabelText("Set 1 - jeux adversaire"), { target: { value: "4" } });
    fireEvent.change(screen.getByLabelText("Set 2 - jeux moi"), { target: { value: "6" } });
    fireEvent.change(screen.getByLabelText("Set 2 - jeux adversaire"), { target: { value: "3" } });

    fireEvent.click(screen.getByRole("button", { name: /enregistrer le match/i }));
    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/dashboard"));

    const postCall = fetchMock.mock.calls.find(([url]) => String(url).endsWith("/api/console/sessions"));
    const patchCall = fetchMock.mock.calls.find(([url]) => String(url).endsWith("/api/console/sessions/42"));
    expect(postCall).toBeDefined();
    expect(patchCall).toBeDefined();
    const postBody = JSON.parse(postCall![1].body as string);
    const [, init] = patchCall!;
    expect(init.method).toBe("PATCH");
    const body = JSON.parse(init.body as string);
    expect(body).toMatchObject({ status: "COMPLETED", result: "VICTORY", scoreText: "6-4 · 6-3" });
    expect(body.updatedAt).toBe(postBody.createdAt);
  });

  it("affiche une erreur si la finalisation du match déjà joué échoue", async () => {
    const fetchMock = vi.fn().mockImplementation((url: string) => {
      if (String(url).endsWith("/api/console/sessions")) {
        return Promise.resolve({ ok: true, json: async () => ({ id: 42 }) });
      }
      return Promise.resolve({ ok: false });
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<NewMatchForm onCancel={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: /match déjà joué/i }));
    fireEvent.change(screen.getByLabelText("Set 1 - jeux moi"), { target: { value: "6" } });
    fireEvent.change(screen.getByLabelText("Set 1 - jeux adversaire"), { target: { value: "4" } });

    fireEvent.click(screen.getByRole("button", { name: /enregistrer le match/i }));
    await waitFor(() => expect(screen.getByText(/échec de l'enregistrement du match/i)).toBeInTheDocument());
  });
});
