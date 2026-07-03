// @vitest-environment node
import { describe, expect, it, vi, afterEach } from "vitest";

vi.mock("next/headers", () => ({ cookies: vi.fn() }));
vi.mock("@/lib/api", () => ({
  finalizeSession: vi.fn(),
  UnauthorizedError: class UnauthorizedError extends Error {},
}));

import { cookies } from "next/headers";
import { finalizeSession } from "@/lib/api";
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

const body = {
  session: {
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
  },
  status: "COMPLETED" as const,
  result: "VICTORY" as const,
  scoreText: "6-4 6-3",
  updatedAt: 2000,
};

describe("POST /api/console/sessions/[sessionId]/finalize", () => {
  it("retourne 401 sans cookie", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => undefined } as never);
    const response = await POST(jsonRequest(body), params("7"));
    expect(response.status).toBe(401);
  });

  it("retourne 401 si finalizeSession lève UnauthorizedError", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    const { UnauthorizedError } = await import("@/lib/api");
    vi.mocked(finalizeSession).mockRejectedValue(new UnauthorizedError());

    const response = await POST(jsonRequest(body), params("7"));
    expect(response.status).toBe(401);
  });

  it("relaie vers finalizeSession et retourne 204", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(finalizeSession).mockResolvedValue(undefined);

    const response = await POST(jsonRequest(body), params("7"));
    expect(response.status).toBe(204);
    expect(vi.mocked(finalizeSession)).toHaveBeenCalledWith("jwt-abc", body);
  });

  it("relance les erreurs qui ne sont pas des UnauthorizedError", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(finalizeSession).mockRejectedValue(new Error("boom"));

    await expect(POST(jsonRequest(body), params("7"))).rejects.toThrow("boom");
  });
});
