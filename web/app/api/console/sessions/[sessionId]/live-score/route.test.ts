// @vitest-environment node
import { describe, expect, it, vi, afterEach } from "vitest";

vi.mock("next/headers", () => ({ cookies: vi.fn() }));
vi.mock("@/lib/api", () => ({
  pushLiveScore: vi.fn(),
  UnauthorizedError: class UnauthorizedError extends Error {},
}));

import { cookies } from "next/headers";
import { pushLiveScore } from "@/lib/api";
import { POST } from "./route";

afterEach(() => {
  vi.clearAllMocks();
});

function params(sessionId: string) {
  return { params: Promise.resolve({ sessionId }) };
}

function jsonRequest(body: unknown): Request {
  return new Request("http://localhost/x", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

const payload = {
  completedSets: [],
  currentSetGamesA: 1,
  currentSetGamesB: 0,
  currentSetPointLog: [],
  currentGamePointsA: "0",
  currentGamePointsB: "0",
  tieBreakPointsA: 0,
  tieBreakPointsB: 0,
  isTieBreak: false,
  isSuperTieBreak: false,
  isMatchOver: false,
  matchWinner: null,
  playerAName: "A",
  playerBName: "B",
  surface: "CLAY",
  tournament: null,
  competitionType: null,
  startedAt: 1000,
};

describe("POST /api/console/sessions/[sessionId]/live-score", () => {
  it("retourne 401 sans cookie", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => undefined } as never);
    const response = await POST(jsonRequest(payload), params("7"));
    expect(response.status).toBe(401);
  });

  it("retourne 401 si pushLiveScore lève UnauthorizedError", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    const { UnauthorizedError } = await import("@/lib/api");
    vi.mocked(pushLiveScore).mockRejectedValue(new UnauthorizedError());

    const response = await POST(jsonRequest(payload), params("7"));
    expect(response.status).toBe(401);
  });

  it("relaie vers pushLiveScore et retourne 204", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(pushLiveScore).mockResolvedValue(undefined);

    const response = await POST(jsonRequest(payload), params("7"));
    expect(response.status).toBe(204);
    expect(vi.mocked(pushLiveScore)).toHaveBeenCalledWith("jwt-abc", 7, payload);
  });

  it("relance les erreurs qui ne sont pas des UnauthorizedError", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(pushLiveScore).mockRejectedValue(new Error("boom"));

    await expect(POST(jsonRequest(payload), params("7"))).rejects.toThrow("boom");
  });
});
