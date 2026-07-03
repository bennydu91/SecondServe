// @vitest-environment node
import { describe, expect, it, vi, afterEach } from "vitest";

vi.mock("next/headers", () => ({ cookies: vi.fn() }));
vi.mock("@/lib/api", () => ({
  putScoreSeed: vi.fn(),
  UnauthorizedError: class UnauthorizedError extends Error {},
}));

import { cookies } from "next/headers";
import { putScoreSeed } from "@/lib/api";
import { PUT } from "./route";

afterEach(() => {
  vi.clearAllMocks();
});

function params(sessionId: string) {
  return { params: Promise.resolve({ sessionId }) };
}

function jsonRequest(body: unknown): Request {
  return new Request("http://localhost/x", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

describe("PUT /api/console/sessions/[sessionId]/score-seed", () => {
  it("retourne 401 sans cookie", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => undefined } as never);
    const response = await PUT(jsonRequest({}), params("7"));
    expect(response.status).toBe(401);
  });

  it("relaie vers putScoreSeed et retourne la session mise à jour", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(putScoreSeed).mockResolvedValue({
      id: 7,
      surface: "CLAY",
      matchFormat: "BEST_OF_3",
      thirdSetRule: "FULL_ADVANTAGE",
      opponent: null,
      competitionType: null,
      tournament: null,
      status: "ACTIVE",
      sessionType: "MATCH",
      result: null,
      scoreText: null,
      scoreSeedJson: null,
      createdAt: 1000,
      updatedAt: 1000,
    });

    const seed = {
      completedSets: [],
      currentSetGamesA: 1,
      currentSetGamesB: 0,
      currentGamePointsA: "0",
      currentGamePointsB: "0",
      tieBreakPointsA: 0,
      tieBreakPointsB: 0,
      isTieBreak: false,
      isSuperTieBreak: false,
    };
    const response = await PUT(jsonRequest(seed), params("7"));
    expect(response.status).toBe(200);
    const data = await response.json();
    expect(data.id).toBe(7);
    expect(vi.mocked(putScoreSeed)).toHaveBeenCalledWith("jwt-abc", 7, seed);
  });

  it("retourne 401 si putScoreSeed lève UnauthorizedError", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    const { UnauthorizedError } = await import("@/lib/api");
    vi.mocked(putScoreSeed).mockRejectedValue(new UnauthorizedError());

    const response = await PUT(jsonRequest({}), params("7"));
    expect(response.status).toBe(401);
  });

  it("relance les erreurs qui ne sont pas des UnauthorizedError", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(putScoreSeed).mockRejectedValue(new Error("boom"));

    await expect(PUT(jsonRequest({}), params("7"))).rejects.toThrow("boom");
  });
});
