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
});
