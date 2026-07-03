import { describe, expect, it } from "vitest";
import { surfaceLabel, surfaceColorVar } from "./surfaces";

describe("surfaceLabel", () => {
  it("traduit les codes de surface connus", () => {
    expect(surfaceLabel("CLAY")).toBe("Terre battue");
    expect(surfaceLabel("HARD")).toBe("Dur");
    expect(surfaceLabel("GRASS")).toBe("Gazon");
    expect(surfaceLabel("CARPET")).toBe("Indoor");
  });

  it("retombe sur le code brut pour une surface inconnue", () => {
    expect(surfaceLabel("UNKNOWN")).toBe("UNKNOWN");
  });
});

describe("surfaceColorVar", () => {
  it("retourne la variable CSS associée à une surface connue", () => {
    expect(surfaceColorVar("CLAY")).toBe("--ss-surface-clay");
  });

  it("retombe sur la variable neutre pour une surface inconnue", () => {
    expect(surfaceColorVar("UNKNOWN")).toBe("--ss-faint");
  });
});
