// @vitest-environment node
import { describe, expect, it } from "vitest";
import { NextRequest } from "next/server";
import { middleware } from "./middleware";
import { SESSION_COOKIE } from "@/lib/auth";

describe("middleware", () => {
  it("redirige vers /login si le cookie de session est absent", () => {
    const request = new NextRequest(new URL("http://localhost:3000/dashboard"));
    const response = middleware(request);
    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toContain("/login");
  });

  it("laisse passer si le cookie de session est présent", () => {
    const request = new NextRequest(new URL("http://localhost:3000/dashboard"), {
      headers: { cookie: `${SESSION_COOKIE}=some-jwt` },
    });
    const response = middleware(request);
    expect(response.status).toBe(200);
  });
});
