// @vitest-environment node
import { describe, expect, it, vi, afterEach } from "vitest";

vi.mock("next/headers", () => ({ cookies: vi.fn() }));
vi.mock("@/lib/api", () => ({ createSession: vi.fn(), UnauthorizedError: class UnauthorizedError extends Error {} }));

import { cookies } from "next/headers";
import { createSession } from "@/lib/api";
import { POST } from "./route";

afterEach(() => {
  vi.clearAllMocks();
});

function jsonRequest(body: unknown): Request {
  return new Request("http://localhost:3000/api/console/sessions", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

describe("POST /api/console/sessions", () => {
  it("retourne 401 si aucun cookie de session", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => undefined } as never);
    const response = await POST(jsonRequest({ surface: "CLAY" }));
    expect(response.status).toBe(401);
  });

  it("relaie vers createSession avec le token du cookie et retourne la session créée", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(createSession).mockResolvedValue({
      id: 5,
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

    const response = await POST(
      jsonRequest({ surface: "CLAY", matchFormat: "BEST_OF_3", thirdSetRule: "FULL_ADVANTAGE", createdAt: 1000 })
    );

    expect(response.status).toBe(200);
    const data = await response.json();
    expect(data.id).toBe(5);
    expect(vi.mocked(createSession)).toHaveBeenCalledWith("jwt-abc", {
      surface: "CLAY",
      matchFormat: "BEST_OF_3",
      thirdSetRule: "FULL_ADVANTAGE",
      opponent: undefined,
      competitionType: undefined,
      tournament: undefined,
      createdAt: 1000,
    });
  });
});
