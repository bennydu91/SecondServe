import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import type { PointDto } from "@/lib/types";
import { PointStatsTiles } from "./PointStatsTiles";

function point(context: PointDto["context"]): PointDto {
  return { id: 1, sessionId: 1, scorer: "A", context, sequenceNum: 1, recordedAt: 1000 };
}

describe("PointStatsTiles", () => {
  it("compte les aces, coups gagnants, fautes directes et doubles fautes", () => {
    render(<PointStatsTiles points={[point("ACE"), point("ACE"), point("WINNER"), point("DOUBLE_FAULT")]} />);
    expect(screen.getAllByText(/^\d+$/).map((el) => el.textContent)).toEqual(["2", "1", "0", "1"]);
  });
});
