import { describe, expect, it, vi, afterEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { isRedirectError } from "next/dist/client/components/redirect-error";

vi.mock("next/headers", () => ({ cookies: vi.fn() }));
vi.mock("@/lib/api", () => ({ getSessions: vi.fn(), UnauthorizedError: class UnauthorizedError extends Error {} }));
vi.mock("next/navigation", async (importOriginal) => {
  const actual = await importOriginal<typeof import("next/navigation")>();
  return { ...actual, useRouter: () => ({ push: vi.fn() }) };
});

import { cookies } from "next/headers";
import { getSessions, UnauthorizedError } from "@/lib/api";
import ConsolePage from "./page";
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
    status: "ACTIVE",
    sessionType: "MATCH",
    result: null,
    scoreText: null,
    scoreSeedJson: null,
    createdAt: Date.UTC(2026, 0, 15),
    updatedAt: Date.UTC(2026, 0, 15),
    ...overrides,
  };
}

describe("ConsolePage", () => {
  it("redirige vers /login si aucun cookie de session", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => undefined } as never);

    try {
      await ConsolePage();
      expect.unreachable("ConsolePage aurait dû rediriger");
    } catch (error) {
      expect(isRedirectError(error)).toBe(true);
      expect((error as { digest: string }).digest).toContain("/login");
    }
  });

  it("redirige vers /login si getSessions lève UnauthorizedError", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(getSessions).mockRejectedValue(new UnauthorizedError());

    try {
      await ConsolePage();
      expect.unreachable("ConsolePage aurait dû rediriger");
    } catch (error) {
      expect(isRedirectError(error)).toBe(true);
      expect((error as { digest: string }).digest).toContain("/login");
    }
  });

  it("relance les erreurs qui ne sont pas des UnauthorizedError", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(getSessions).mockRejectedValue(new Error("boom"));

    await expect(ConsolePage()).rejects.toThrow("boom");
  });

  it("ne transmet que les sessions actives à la vue de sélection", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(getSessions).mockResolvedValue([
      buildSession({ id: 1, opponent: "Session active" }),
      buildSession({ id: 2, opponent: "Session terminée", status: "COMPLETED" }),
    ]);

    const element = await ConsolePage();
    render(element);

    expect(screen.getByText(/Session active/)).toBeInTheDocument();
    expect(screen.queryByText(/Session terminée/)).not.toBeInTheDocument();
  });
});
