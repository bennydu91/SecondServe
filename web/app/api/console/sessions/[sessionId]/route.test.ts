// @vitest-environment node
import { describe, expect, it, vi, afterEach } from "vitest";

vi.mock("next/headers", () => ({ cookies: vi.fn() }));
vi.mock("@/lib/api", () => ({
  updateSession: vi.fn(),
  deleteSession: vi.fn(),
  UnauthorizedError: class UnauthorizedError extends Error {},
}));

import { cookies } from "next/headers";
import { updateSession, deleteSession } from "@/lib/api";
import { PATCH, DELETE } from "./route";

afterEach(() => {
  vi.clearAllMocks();
});

function params(sessionId: string) {
  return { params: Promise.resolve({ sessionId }) };
}

function jsonRequest(method: string, body: unknown): Request {
  return new Request("http://localhost/x", {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

describe("PATCH /api/console/sessions/[sessionId]", () => {
  it("retourne 401 sans cookie", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => undefined } as never);
    const response = await PATCH(jsonRequest("PATCH", {}), params("7"));
    expect(response.status).toBe(401);
  });

  it("relaie vers updateSession et retourne la session mise à jour", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(updateSession).mockResolvedValue({
      id: 7,
      surface: "HARD",
      matchFormat: "BEST_OF_3",
      thirdSetRule: "FULL_ADVANTAGE",
      opponent: "Martin",
      competitionType: null,
      tournament: null,
      status: "COMPLETED",
      sessionType: "MATCH",
      result: "VICTORY",
      scoreText: "6-4 · 6-3",
      scoreSeedJson: null,
      createdAt: 1000,
      updatedAt: 1000,
    });

    const patch = { opponent: "Martin", status: "COMPLETED" as const };
    const response = await PATCH(jsonRequest("PATCH", patch), params("7"));
    expect(response.status).toBe(200);
    const data = await response.json();
    expect(data.opponent).toBe("Martin");
    expect(vi.mocked(updateSession)).toHaveBeenCalledWith("jwt-abc", 7, patch);
  });

  it("retourne 401 si updateSession lève UnauthorizedError", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    const { UnauthorizedError } = await import("@/lib/api");
    vi.mocked(updateSession).mockRejectedValue(new UnauthorizedError());

    const response = await PATCH(jsonRequest("PATCH", {}), params("7"));
    expect(response.status).toBe(401);
  });
});

describe("DELETE /api/console/sessions/[sessionId]", () => {
  it("retourne 401 sans cookie", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => undefined } as never);
    const response = await DELETE(jsonRequest("DELETE", {}), params("7"));
    expect(response.status).toBe(401);
  });

  it("relaie vers deleteSession et retourne 204", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(deleteSession).mockResolvedValue(undefined);

    const response = await DELETE(jsonRequest("DELETE", {}), params("7"));
    expect(response.status).toBe(204);
    expect(vi.mocked(deleteSession)).toHaveBeenCalledWith("jwt-abc", 7);
  });

  it("retourne 401 si deleteSession lève UnauthorizedError", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    const { UnauthorizedError } = await import("@/lib/api");
    vi.mocked(deleteSession).mockRejectedValue(new UnauthorizedError());

    const response = await DELETE(jsonRequest("DELETE", {}), params("7"));
    expect(response.status).toBe(401);
  });
});
