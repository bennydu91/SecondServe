import { describe, expect, it } from "vitest";
import { NAV_ITEMS, isNavItemActive } from "./navItems";

describe("NAV_ITEMS", () => {
  it("contient le tableau de bord et la console de saisie", () => {
    expect(NAV_ITEMS.map((item) => item.href)).toEqual(["/dashboard", "/dashboard/console"]);
  });
});

describe("isNavItemActive", () => {
  it("est actif sur /dashboard uniquement pour une correspondance exacte", () => {
    expect(isNavItemActive("/dashboard", "/dashboard")).toBe(true);
    expect(isNavItemActive("/dashboard/console", "/dashboard")).toBe(false);
  });

  it("est actif sur une sous-route pour les autres items", () => {
    expect(isNavItemActive("/dashboard/console/42", "/dashboard/console")).toBe(true);
    expect(isNavItemActive("/dashboard", "/dashboard/console")).toBe(false);
  });
});
