import { describe, expect, it, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { ThemeToggle } from "./ThemeToggle";

beforeEach(() => {
  document.documentElement.setAttribute("data-theme", "light");
  localStorage.clear();
});

describe("ThemeToggle", () => {
  it("bascule l'attribut data-theme et persiste le choix au clic", () => {
    render(<ThemeToggle />);
    const button = screen.getByRole("button", { name: /changer de thème/i });
    fireEvent.click(button);
    expect(document.documentElement.getAttribute("data-theme")).toBe("dark");
    expect(localStorage.getItem("ss-theme")).toBe("dark");

    fireEvent.click(button);
    expect(document.documentElement.getAttribute("data-theme")).toBe("light");
    expect(localStorage.getItem("ss-theme")).toBe("light");
  });
});
