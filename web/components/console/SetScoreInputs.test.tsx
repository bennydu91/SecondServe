import { describe, expect, it, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { SetScoreInputs, parseSetEntries } from "./SetScoreInputs";
import type { SetScoreEntry } from "./SetScoreInputs";

describe("parseSetEntries", () => {
  it("ignore les sets incomplets ou vides", () => {
    const entries: SetScoreEntry[] = [
      { self: "6", opponent: "4" },
      { self: "", opponent: "" },
      { self: "3", opponent: "6" },
    ];
    expect(parseSetEntries(entries)).toEqual([
      { gamesA: 6, gamesB: 4 },
      { gamesA: 3, gamesB: 6 },
    ]);
  });

  it("retourne un tableau vide si aucun set n'est rempli", () => {
    expect(parseSetEntries([{ self: "", opponent: "" }])).toEqual([]);
  });

  it("ignore les valeurs négatives", () => {
    expect(parseSetEntries([{ self: "-1", opponent: "4" }])).toEqual([]);
  });
});

describe("SetScoreInputs", () => {
  it("affiche un champ par set fourni", () => {
    render(
      <SetScoreInputs
        sets={[{ self: "6", opponent: "4" }, { self: "", opponent: "" }]}
        onChange={vi.fn()}
        maxSets={3}
      />
    );
    expect(screen.getByLabelText("Set 1 - jeux moi")).toHaveValue(6);
    expect(screen.getByLabelText("Set 2 - jeux adversaire")).toHaveValue(null);
  });

  it("appelle onChange avec la valeur modifiée au changement d'un champ", () => {
    const onChange = vi.fn();
    render(<SetScoreInputs sets={[{ self: "", opponent: "" }]} onChange={onChange} maxSets={3} />);
    fireEvent.change(screen.getByLabelText("Set 1 - jeux moi"), { target: { value: "6" } });
    expect(onChange).toHaveBeenCalledWith([{ self: "6", opponent: "" }]);
  });

  it("ajoute un set au clic sur le bouton d'ajout, dans la limite de maxSets", () => {
    const onChange = vi.fn();
    render(
      <SetScoreInputs sets={[{ self: "6", opponent: "4" }, { self: "3", opponent: "6" }]} onChange={onChange} maxSets={3} />
    );
    fireEvent.click(screen.getByRole("button", { name: /ajouter un set/i }));
    expect(onChange).toHaveBeenCalledWith([
      { self: "6", opponent: "4" },
      { self: "3", opponent: "6" },
      { self: "", opponent: "" },
    ]);
  });

  it("n'affiche pas le bouton d'ajout quand maxSets est atteint", () => {
    render(
      <SetScoreInputs
        sets={[{ self: "6", opponent: "4" }, { self: "3", opponent: "6" }]}
        onChange={vi.fn()}
        maxSets={2}
      />
    );
    expect(screen.queryByRole("button", { name: /ajouter un set/i })).not.toBeInTheDocument();
  });

  it("supprime un set au clic sur son bouton de suppression", () => {
    const onChange = vi.fn();
    render(
      <SetScoreInputs sets={[{ self: "6", opponent: "4" }, { self: "3", opponent: "6" }]} onChange={onChange} maxSets={3} />
    );
    fireEvent.click(screen.getByRole("button", { name: /supprimer le set 2/i }));
    expect(onChange).toHaveBeenCalledWith([{ self: "6", opponent: "4" }]);
  });

  it("n'affiche pas de bouton de suppression s'il ne reste qu'un set", () => {
    render(<SetScoreInputs sets={[{ self: "6", opponent: "4" }]} onChange={vi.fn()} maxSets={3} />);
    expect(screen.queryByRole("button", { name: /supprimer le set 1/i })).not.toBeInTheDocument();
  });
});
