import { describe, expect, it, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { PointButtonGrid } from "./PointButtonGrid";

describe("PointButtonGrid", () => {
  it("affiche les 4 boutons Mon point et les 4 boutons Point adverse", () => {
    render(<PointButtonGrid onSelect={vi.fn()} disabled={false} />);
    for (const label of [
      "Ace",
      "Coup gagnant",
      "Faute provoquée",
      "Faute adverse",
      "Ace adverse",
      "Coup gagnant adverse",
      "Ma faute",
      "Double faute",
    ]) {
      expect(screen.getByRole("button", { name: label })).toBeInTheDocument();
    }
  });

  it("appelle onSelect avec le context correspondant", () => {
    const onSelect = vi.fn();
    render(<PointButtonGrid onSelect={onSelect} disabled={false} />);
    fireEvent.click(screen.getByRole("button", { name: "Ace" }));
    expect(onSelect).toHaveBeenCalledWith("ACE");
    fireEvent.click(screen.getByRole("button", { name: "Double faute" }));
    expect(onSelect).toHaveBeenCalledWith("DOUBLE_FAULT");
  });

  it("désactive tous les boutons quand disabled=true", () => {
    render(<PointButtonGrid onSelect={vi.fn()} disabled={true} />);
    expect(screen.getByRole("button", { name: "Ace" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Double faute" })).toBeDisabled();
  });
});
