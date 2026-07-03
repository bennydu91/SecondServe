import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import type { PointDto } from "@/lib/types";
import { PointTrail } from "./PointTrail";

describe("PointTrail", () => {
  it("affiche un état vide sans point", () => {
    render(<PointTrail points={[]} />);
    expect(screen.getByText(/aucun point saisi/i)).toBeInTheDocument();
  });

  it("affiche les points du plus récent au plus ancien", () => {
    const points: PointDto[] = [
      { id: 1, sessionId: 1, scorer: "A", context: "ACE", sequenceNum: 1, recordedAt: 1000 },
      { id: 2, sessionId: 1, scorer: "B", context: "DOUBLE_FAULT", sequenceNum: 2, recordedAt: 2000 },
    ];
    render(<PointTrail points={points} />);
    const items = screen.getAllByRole("listitem");
    expect(items[0]).toHaveTextContent("Double faute");
    expect(items[1]).toHaveTextContent("Ace");
  });
});
