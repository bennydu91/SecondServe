import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";

const usePathnameMock = vi.fn();
vi.mock("next/navigation", () => ({ usePathname: () => usePathnameMock() }));

import { Sidebar } from "./Sidebar";

describe("Sidebar", () => {
  it("met en avant Tableau de bord sur /dashboard", () => {
    usePathnameMock.mockReturnValue("/dashboard");
    render(<Sidebar />);
    expect(screen.getByRole("link", { name: /tableau de bord/i })).toHaveClass("navItemActive");
    expect(screen.getByRole("link", { name: /console de saisie/i })).not.toHaveClass("navItemActive");
  });

  it("met en avant Console de saisie sur /dashboard/console", () => {
    usePathnameMock.mockReturnValue("/dashboard/console");
    render(<Sidebar />);
    expect(screen.getByRole("link", { name: /console de saisie/i })).toHaveClass("navItemActive");
    expect(screen.getByRole("link", { name: /tableau de bord/i })).not.toHaveClass("navItemActive");
  });

  it("met en avant Console de saisie sur une sous-route /dashboard/console/{id}", () => {
    usePathnameMock.mockReturnValue("/dashboard/console/42");
    render(<Sidebar />);
    expect(screen.getByRole("link", { name: /console de saisie/i })).toHaveClass("navItemActive");
  });
});
