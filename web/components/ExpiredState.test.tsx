import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { ExpiredState } from "./ExpiredState";

describe("ExpiredState", () => {
  it("affiche le message d'expiration du lien", () => {
    render(<ExpiredState />);
    expect(screen.getByText("Ce lien n'est plus disponible")).toBeInTheDocument();
    expect(screen.getByText("Le suivi de ce match a expiré.")).toBeInTheDocument();
  });
});
