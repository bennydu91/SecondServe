// @vitest-environment node
import { describe, expect, it, vi, afterEach } from "vitest";
import { POST } from "./route";
import { SESSION_COOKIE, SESSION_MAX_AGE_SECONDS } from "@/lib/auth";

afterEach(() => {
  vi.unstubAllGlobals();
});

function jsonRequest(body: unknown): Request {
  return new Request("http://localhost:3000/api/auth/callback", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

describe("POST /api/auth/callback", () => {
  it("retourne 400 si le credential est absent", async () => {
    const response = await POST(jsonRequest({}));
    expect(response.status).toBe(400);
  });

  it("retourne 401 sans poser de cookie si le backend rejette le token", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 403 }));
    const response = await POST(jsonRequest({ credential: "bad-token" }));
    expect(response.status).toBe(401);
    expect(response.cookies.get(SESSION_COOKIE)).toBeUndefined();
  });

  it("pose un cookie httpOnly avec le JWT retourné par le backend", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => ({ token: "jwt-abc" }) })
    );
    const response = await POST(jsonRequest({ credential: "good-token" }));
    expect(response.status).toBe(200);
    const cookie = response.cookies.get(SESSION_COOKIE);
    expect(cookie?.value).toBe("jwt-abc");
    expect(cookie?.httpOnly).toBe(true);
    expect(cookie?.secure).toBe(true);
    expect(cookie?.sameSite).toBe("lax");
    expect(cookie?.maxAge).toBe(SESSION_MAX_AGE_SECONDS);
    expect(cookie?.path).toBe("/");
  });

  it("transmet le credential au backend sous google_id_token", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => ({ token: "jwt-abc" }) });
    vi.stubGlobal("fetch", fetchMock);
    await POST(jsonRequest({ credential: "good-token" }));
    const [, init] = fetchMock.mock.calls[0];
    expect(JSON.parse(init.body as string)).toEqual({ google_id_token: "good-token" });
  });
});
