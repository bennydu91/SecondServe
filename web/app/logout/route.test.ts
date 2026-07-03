// @vitest-environment node
import { describe, expect, it } from "vitest";
import { POST } from "./route";
import { SESSION_COOKIE } from "@/lib/auth";

describe("POST /logout", () => {
  it("supprime le cookie de session et redirige vers /login", async () => {
    const request = new Request("http://localhost:3000/logout", { method: "POST" });
    const response = await POST(request);

    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toContain("/login");

    const cookieHeader = response.headers.get("set-cookie") ?? "";
    expect(cookieHeader).toContain(`${SESSION_COOKIE}=`);
    expect(cookieHeader.toLowerCase()).toContain("expires=thu, 01 jan 1970");
  });
});
