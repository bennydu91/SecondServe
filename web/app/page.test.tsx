// @vitest-environment node
import { describe, expect, it } from "vitest";
import { isRedirectError } from "next/dist/client/components/redirect-error";
import RootPage from "./page";

describe("RootPage", () => {
  it("redirige vers /dashboard", () => {
    try {
      RootPage();
      expect.unreachable("RootPage aurait dû lever une redirection");
    } catch (error) {
      expect(isRedirectError(error)).toBe(true);
      expect((error as { digest: string }).digest).toContain("/dashboard");
    }
  });
});
