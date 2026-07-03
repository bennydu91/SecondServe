import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import NotFound from "./not-found";

describe("NotFound (live/[token])", () => {
  it("affiche le message de lien invalide", () => {
    render(<NotFound />);
    expect(screen.getByText("Lien invalide")).toBeInTheDocument();
    expect(screen.getByText("Ce lien de suivi n'existe pas.")).toBeInTheDocument();
  });
});
