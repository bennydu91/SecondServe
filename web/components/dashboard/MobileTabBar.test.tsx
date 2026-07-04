import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";

const usePathnameMock = vi.fn();
vi.mock("next/navigation", () => ({ usePathname: () => usePathnameMock() }));

import { MobileTabBar } from "./MobileTabBar";

describe("MobileTabBar", () => {
  it("met en avant Tableau de bord sur /dashboard", () => {
    usePathnameMock.mockReturnValue("/dashboard");
    render(<MobileTabBar />);
    expect(screen.getByRole("link", { name: /tableau de bord/i })).toHaveClass("tabActive");
    expect(screen.getByRole("link", { name: /console de saisie/i })).not.toHaveClass("tabActive");
  });

  it("met en avant Console de saisie sur /dashboard/console", () => {
    usePathnameMock.mockReturnValue("/dashboard/console");
    render(<MobileTabBar />);
    expect(screen.getByRole("link", { name: /console de saisie/i })).toHaveClass("tabActive");
  });
});
