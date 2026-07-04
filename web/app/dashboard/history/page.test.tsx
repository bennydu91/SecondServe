import { describe, expect, it, vi, afterEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { isRedirectError } from "next/dist/client/components/redirect-error";

vi.mock("next/headers", () => ({ cookies: vi.fn() }));
vi.mock("next/navigation", async (importOriginal) => {
  const actual = await importOriginal<typeof import("next/navigation")>();
  return { ...actual, useRouter: () => ({ refresh: vi.fn() }) };
});
vi.mock("@/lib/api", () => ({ getSessions: vi.fn(), UnauthorizedError: class UnauthorizedError extends Error {} }));

import { cookies } from "next/headers";
import { getSessions, UnauthorizedError } from "@/lib/api";
import HistoryPage from "./page";
import type { SessionDto } from "@/lib/types";

afterEach(() => {
  vi.clearAllMocks();
});

function buildSession(overrides: Partial<SessionDto> = {}): SessionDto {
  return {
    id: 1,
    surface: "CLAY",
    matchFormat: "BEST_OF_3",
    thirdSetRule: "FULL_ADVANTAGE",
    opponent: "Novak",
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

describe("HistoryPage", () => {
  it("redirige vers /login si aucun cookie de session", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => undefined } as never);

    try {
      await HistoryPage();
      expect.unreachable("HistoryPage aurait dû rediriger");
    } catch (error) {
      expect(isRedirectError(error)).toBe(true);
      expect((error as { digest: string }).digest).toContain("/login");
    }
  });

  it("redirige vers /login si getSessions lève UnauthorizedError", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(getSessions).mockRejectedValue(new UnauthorizedError());

    try {
      await HistoryPage();
      expect.unreachable("HistoryPage aurait dû rediriger");
    } catch (error) {
      expect(isRedirectError(error)).toBe(true);
      expect((error as { digest: string }).digest).toContain("/login");
    }
  });

  it("relance les erreurs qui ne sont pas des UnauthorizedError", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(getSessions).mockRejectedValue(new Error("boom"));

    await expect(HistoryPage()).rejects.toThrow("boom");
  });

  it("ne passe que les sessions de type MATCH, triées par date décroissante", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(getSessions).mockResolvedValue([
      buildSession({ id: 1, opponent: "Ancien", createdAt: 1_000 }),
      buildSession({ id: 2, opponent: "Entraînement", sessionType: "TRAINING", createdAt: 2_000 }),
      buildSession({ id: 3, opponent: "Récent", createdAt: 3_000 }),
    ]);

    const element = await HistoryPage();
    render(element);

    expect(screen.getByText("Récent")).toBeInTheDocument();
    expect(screen.getByText("Ancien")).toBeInTheDocument();
    expect(screen.queryByText("Entraînement")).not.toBeInTheDocument();
  });
});
