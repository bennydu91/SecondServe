import { describe, expect, it, vi, afterEach } from "vitest";
import { THEME_INIT_SCRIPT } from "./theme";

function runInitScript(storedTheme: string | null, prefersDark: boolean) {
  document.documentElement.removeAttribute("data-theme");
  const store: Record<string, string> = {};
  if (storedTheme) store["ss-theme"] = storedTheme;
  vi.stubGlobal("localStorage", {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => {
      store[key] = value;
    },
  });
  vi.stubGlobal("matchMedia", (_query: string) => ({ matches: prefersDark }));
  // eslint-disable-next-line no-new-func
  new Function(THEME_INIT_SCRIPT)();
}

afterEach(() => {
  vi.unstubAllGlobals();
  document.documentElement.removeAttribute("data-theme");
});

describe("THEME_INIT_SCRIPT", () => {
  it("privilégie la préférence explicite stockée sur la préférence système", () => {
    runInitScript("dark", false);
    expect(document.documentElement.getAttribute("data-theme")).toBe("dark");
  });

  it("retombe sur la préférence système si rien n'est stocké", () => {
    runInitScript(null, true);
    expect(document.documentElement.getAttribute("data-theme")).toBe("dark");
    runInitScript(null, false);
    expect(document.documentElement.getAttribute("data-theme")).toBe("light");
  });

  it("ignore une valeur stockée invalide et retombe sur le système", () => {
    runInitScript("blue", true);
    expect(document.documentElement.getAttribute("data-theme")).toBe("dark");
  });
});
