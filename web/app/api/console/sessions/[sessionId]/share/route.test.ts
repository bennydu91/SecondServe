// @vitest-environment node
import { describe, expect, it, vi, afterEach } from "vitest";

vi.mock("next/headers", () => ({ cookies: vi.fn() }));
vi.mock("@/lib/api", () => ({
  getShareForSession: vi.fn(),
  UnauthorizedError: class UnauthorizedError extends Error {},
}));

import { cookies } from "next/headers";
import { getShareForSession } from "@/lib/api";
import { GET } from "./route";

afterEach(() => {
  vi.clearAllMocks();
});

function params(sessionId: string) {
  return { params: Promise.resolve({ sessionId }) };
}

describe("GET /api/console/sessions/[sessionId]/share", () => {
  it("retourne 401 sans cookie", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => undefined } as never);
    const response = await GET(new Request("http://localhost/x"), params("7"));
    expect(response.status).toBe(401);
  });

  it("relaie vers getShareForSession et retourne le partage", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(getShareForSession).mockResolvedValue({ token: "share-token", url: "https://example.com/live/share-token" });

    const response = await GET(new Request("http://localhost/x"), params("7"));
    expect(response.status).toBe(200);
    const data = await response.json();
    expect(data.token).toBe("share-token");
    expect(vi.mocked(getShareForSession)).toHaveBeenCalledWith("jwt-abc", 7);
  });

  it("retourne 401 si getShareForSession lève UnauthorizedError", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    const { UnauthorizedError } = await import("@/lib/api");
    vi.mocked(getShareForSession).mockRejectedValue(new UnauthorizedError());

    const response = await GET(new Request("http://localhost/x"), params("7"));
    expect(response.status).toBe(401);
  });

  it("relance les erreurs qui ne sont pas des UnauthorizedError", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(getShareForSession).mockRejectedValue(new Error("boom"));

    await expect(GET(new Request("http://localhost/x"), params("7"))).rejects.toThrow("boom");
  });
});
