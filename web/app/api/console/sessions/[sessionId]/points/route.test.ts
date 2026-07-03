// @vitest-environment node
import { describe, expect, it, vi, afterEach } from "vitest";

vi.mock("next/headers", () => ({ cookies: vi.fn() }));
vi.mock("@/lib/api", () => ({
  getPoints: vi.fn(),
  postPoint: vi.fn(),
  UnauthorizedError: class UnauthorizedError extends Error {},
}));

import { cookies } from "next/headers";
import { getPoints, postPoint } from "@/lib/api";
import { GET, POST } from "./route";

afterEach(() => {
  vi.clearAllMocks();
});

function params(sessionId: string) {
  return { params: Promise.resolve({ sessionId }) };
}

describe("GET /api/console/sessions/[sessionId]/points", () => {
  it("retourne 401 sans cookie", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => undefined } as never);
    const response = await GET(new Request("http://localhost/x"), params("7"));
    expect(response.status).toBe(401);
  });

  it("relaie vers getPoints et retourne { items }", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(getPoints).mockResolvedValue([
      { id: 1, sessionId: 7, scorer: "A", context: "ACE", sequenceNum: 1, recordedAt: 1000 },
    ]);
    const response = await GET(new Request("http://localhost/x"), params("7"));
    expect(response.status).toBe(200);
    const data = await response.json();
    expect(data.items).toHaveLength(1);
    expect(vi.mocked(getPoints)).toHaveBeenCalledWith("jwt-abc", 7);
  });

  it("retourne 401 si getPoints lève UnauthorizedError", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    const { UnauthorizedError } = await import("@/lib/api");
    vi.mocked(getPoints).mockRejectedValue(new UnauthorizedError());

    const response = await GET(new Request("http://localhost/x"), params("7"));
    expect(response.status).toBe(401);
  });

  it("relance les erreurs qui ne sont pas des UnauthorizedError", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(getPoints).mockRejectedValue(new Error("boom"));

    await expect(GET(new Request("http://localhost/x"), params("7"))).rejects.toThrow("boom");
  });
});

describe("POST /api/console/sessions/[sessionId]/points", () => {
  it("retourne 401 sans cookie", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => undefined } as never);
    const request = new Request("http://localhost/x", {
      method: "POST",
      body: JSON.stringify({ context: "ACE" }),
    });
    const response = await POST(request, params("7"));
    expect(response.status).toBe(401);
  });

  it("retourne 401 si postPoint lève UnauthorizedError", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    const { UnauthorizedError } = await import("@/lib/api");
    vi.mocked(postPoint).mockRejectedValue(new UnauthorizedError());

    const request = new Request("http://localhost/x", {
      method: "POST",
      body: JSON.stringify({ context: "ACE" }),
    });
    const response = await POST(request, params("7"));
    expect(response.status).toBe(401);
  });

  it("relaie le context vers postPoint", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(postPoint).mockResolvedValue({
      id: 2,
      sessionId: 7,
      scorer: "B",
      context: "DOUBLE_FAULT",
      sequenceNum: 2,
      recordedAt: 2000,
    });
    const request = new Request("http://localhost/x", {
      method: "POST",
      body: JSON.stringify({ context: "DOUBLE_FAULT" }),
    });
    const response = await POST(request, params("7"));
    expect(response.status).toBe(200);
    expect(vi.mocked(postPoint)).toHaveBeenCalledWith("jwt-abc", 7, "DOUBLE_FAULT");
  });

  it("relance les erreurs qui ne sont pas des UnauthorizedError", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(postPoint).mockRejectedValue(new Error("boom"));

    const request = new Request("http://localhost/x", {
      method: "POST",
      body: JSON.stringify({ context: "ACE" }),
    });
    await expect(POST(request, params("7"))).rejects.toThrow("boom");
  });
});
