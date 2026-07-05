import { describe, expect, it, vi, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { DeleteMatchButton } from "./DeleteMatchButton";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("DeleteMatchButton", () => {
  it("affiche d'abord juste le bouton Supprimer, sans confirmation", () => {
    render(<DeleteMatchButton sessionId={5} onDeleted={vi.fn()} />);
    expect(screen.getByRole("button", { name: "Supprimer" })).toBeInTheDocument();
    expect(screen.queryByText(/confirmer la suppression/i)).not.toBeInTheDocument();
  });

  it("affiche la confirmation au premier clic, sans appeler l'API", () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    render(<DeleteMatchButton sessionId={5} onDeleted={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: "Supprimer" }));
    expect(screen.getByText(/confirmer la suppression/i)).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("annule la confirmation au clic sur Annuler", () => {
    render(<DeleteMatchButton sessionId={5} onDeleted={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: "Supprimer" }));
    fireEvent.click(screen.getByRole("button", { name: /annuler/i }));
    expect(screen.queryByText(/confirmer la suppression/i)).not.toBeInTheDocument();
  });

  it("appelle DELETE et onDeleted au clic sur Confirmer", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true });
    vi.stubGlobal("fetch", fetchMock);
    const onDeleted = vi.fn();
    render(<DeleteMatchButton sessionId={5} onDeleted={onDeleted} />);

    fireEvent.click(screen.getByRole("button", { name: "Supprimer" }));
    fireEvent.click(screen.getByRole("button", { name: /confirmer/i }));

    await waitFor(() => expect(onDeleted).toHaveBeenCalled());
    expect(fetchMock).toHaveBeenCalledWith("/api/console/sessions/5", { method: "DELETE" });
  });

  it("affiche une erreur si la suppression échoue", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false }));
    render(<DeleteMatchButton sessionId={5} onDeleted={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: "Supprimer" }));
    fireEvent.click(screen.getByRole("button", { name: /confirmer/i }));
    await waitFor(() => expect(screen.getByText(/échec de la suppression/i)).toBeInTheDocument());
  });
});
