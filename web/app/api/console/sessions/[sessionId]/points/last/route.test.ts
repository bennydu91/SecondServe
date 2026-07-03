// @vitest-environment node
import { describe, expect, it, vi, afterEach } from "vitest";

vi.mock("next/headers", () => ({ cookies: vi.fn() }));
vi.mock("@/lib/api", () => ({
  deleteLastPoint: vi.fn(),
  UnauthorizedError: class UnauthorizedError extends Error {},
}));

import { cookies } from "next/headers";
import { deleteLastPoint } from "@/lib/api";
import { DELETE } from "./route";

afterEach(() => {
  vi.clearAllMocks();
});

function params(sessionId: string) {
  return { params: Promise.resolve({ sessionId }) };
}

describe("DELETE /api/console/sessions/[sessionId]/points/last", () => {
  it("retourne 401 sans cookie", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => undefined } as never);
    const response = await DELETE(new Request("http://localhost/x"), params("7"));
    expect(response.status).toBe(401);
  });

  it("relaie vers deleteLastPoint et retourne 204", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(deleteLastPoint).mockResolvedValue(undefined);

    const response = await DELETE(new Request("http://localhost/x"), params("7"));
    expect(response.status).toBe(204);
    expect(vi.mocked(deleteLastPoint)).toHaveBeenCalledWith("jwt-abc", 7);
  });

  it("retourne 401 si deleteLastPoint lève UnauthorizedError", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    const { UnauthorizedError } = await import("@/lib/api");
    vi.mocked(deleteLastPoint).mockRejectedValue(new UnauthorizedError());

    const response = await DELETE(new Request("http://localhost/x"), params("7"));
    expect(response.status).toBe(401);
  });

  it("relance les erreurs qui ne sont pas des UnauthorizedError", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => ({ value: "jwt-abc" }) } as never);
    vi.mocked(deleteLastPoint).mockRejectedValue(new Error("boom"));

    await expect(DELETE(new Request("http://localhost/x"), params("7"))).rejects.toThrow("boom");
  });
});
