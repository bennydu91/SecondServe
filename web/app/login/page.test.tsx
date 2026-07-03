import { describe, expect, it, vi, afterEach } from "vitest";
import { useEffect } from "react";
import { render, screen, waitFor } from "@testing-library/react";

const pushMock = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: pushMock }) }));
function FakeScript({ onLoad }: { onLoad?: () => void }) {
  useEffect(() => {
    onLoad?.();
  }, [onLoad]);
  return null;
}
vi.mock("next/script", () => ({ default: FakeScript }));

import LoginPage from "./page";

afterEach(() => {
  vi.unstubAllGlobals();
  pushMock.mockClear();
  delete (window as { google?: unknown }).google;
});

describe("LoginPage", () => {
  it("affiche le titre et prépare le conteneur du bouton Google", () => {
    render(<LoginPage />);
    expect(screen.getByText("SecondServe")).toBeInTheDocument();
    expect(screen.getByText("Tableau de bord")).toBeInTheDocument();
  });

  it("initialise le bouton Google Identity au chargement du script", () => {
    const renderButton = vi.fn();
    const initialize = vi.fn();
    (window as { google?: unknown }).google = { accounts: { id: { initialize, renderButton } } };

    render(<LoginPage />);

    expect(initialize).toHaveBeenCalledWith(expect.objectContaining({ callback: expect.any(Function) }));
    expect(renderButton).toHaveBeenCalled();
  });

  it("redirige vers /dashboard quand le callback Google renvoie un credential valide", async () => {
    let credentialCallback: ((response: { credential: string }) => void) | undefined;
    const initialize = vi.fn((config: { callback: (response: { credential: string }) => void }) => {
      credentialCallback = config.callback;
    });
    (window as { google?: unknown }).google = { accounts: { id: { initialize, renderButton: vi.fn() } } };
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true }));

    render(<LoginPage />);
    credentialCallback?.({ credential: "google-credential" });

    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/dashboard"));
  });

  it("ne redirige pas si le backend rejette le credential", async () => {
    let credentialCallback: ((response: { credential: string }) => void) | undefined;
    const initialize = vi.fn((config: { callback: (response: { credential: string }) => void }) => {
      credentialCallback = config.callback;
    });
    (window as { google?: unknown }).google = { accounts: { id: { initialize, renderButton: vi.fn() } } };
    const fetchMock = vi.fn().mockResolvedValue({ ok: false });
    vi.stubGlobal("fetch", fetchMock);

    render(<LoginPage />);
    credentialCallback?.({ credential: "bad-credential" });

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(pushMock).not.toHaveBeenCalled();
  });
});
