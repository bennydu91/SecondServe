import { describe, expect, it, vi, afterEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { isRedirectError } from "next/dist/client/components/redirect-error";

vi.mock("next/headers", () => ({ cookies: vi.fn() }));
vi.mock("@/lib/api", () => ({
  getSessions: vi.fn(),
  getPoints: vi.fn(),
  UnauthorizedError: class UnauthorizedError extends Error {},
}));
vi.mock("@/components/console/ConsoleScreen", () => ({
  ConsoleScreen: ({ session }: { session: { opponent: string | null } }) => (
    <div>ConsoleScreen-{session.opponent}</div>
  ),
}));

import { cookies } from "next/headers";
import { getSessions, getPoints, UnauthorizedError } from "@/lib/api";
import ConsoleSessionPage from "./page";
import type { SessionDto } from "@/lib/types";

afterEach(() => {
  vi.clearAllMocks();
});

function params(sessionId: string) {
  return { params: Promise.resolve({ sessionId }) };
}

function buildSession(overrides: Partial<SessionDto> = {}): SessionDto {
  return {
    id: 7,
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
    createdAt: 1000,
    updatedAt: 1000,
    ...overrides,
  };
}

async function expectRedirect(promise: Promise<unknown>, target: string) {
  try {
    await promise;
    expect.unreachable(`aurait dû rediriger vers ${target}`);
  } catch (error) {
    expect(isRedirectError(error)).toBe(true);
    expect((error as { digest: string }).digest).toContain(target);
  }
}

describe("ConsoleSessionPage", () => {
  it("redirige vers /login si aucun cookie de session", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => undefined } as never);
    await expectRedirect(ConsoleSessionPage(params("7")), "/login");
  });

  it("redirige vers /login si getSessions lève UnauthorizedError", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(getSessions).mockRejectedValue(new UnauthorizedError());
    await expectRedirect(ConsoleSessionPage(params("7")), "/login");
  });

  it("relance les erreurs qui ne sont pas des UnauthorizedError", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(getSessions).mockRejectedValue(new Error("boom"));
    await expect(ConsoleSessionPage(params("7"))).rejects.toThrow("boom");
  });

  it("redirige vers /dashboard/console si la session est introuvable", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(getSessions).mockResolvedValue([]);
    await expectRedirect(ConsoleSessionPage(params("999")), "/dashboard/console");
  });

  it("redirige vers /dashboard/console si la session n'est plus active", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(getSessions).mockResolvedValue([buildSession({ status: "COMPLETED" })]);
    await expectRedirect(ConsoleSessionPage(params("7")), "/dashboard/console");
  });

  it("affiche ConsoleScreen avec la session active et ses points", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(getSessions).mockResolvedValue([buildSession()]);
    vi.mocked(getPoints).mockResolvedValue([]);

    const element = await ConsoleSessionPage(params("7"));
    render(element);

    expect(screen.getByText("ConsoleScreen-Novak")).toBeInTheDocument();
    expect(vi.mocked(getPoints)).toHaveBeenCalledWith("jwt-abc", 7);
  });
});
