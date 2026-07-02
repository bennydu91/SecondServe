import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { LiveScoreBoard } from "./LiveScoreBoard";
import type { LiveSnapshot } from "@/lib/types";

class FakeEventSource {
  onmessage: ((event: { data: string }) => void) | null = null;
  onopen: (() => void) | null = null;
  static instances: FakeEventSource[] = [];
  constructor(public url: string) {
    FakeEventSource.instances.push(this);
  }
  close() {}
}

const baseSnapshot: LiveSnapshot = {
  status: "LIVE",
  completedSets: [],
  currentSetGamesA: 1,
  currentSetGamesB: 0,
  currentSetPointLog: ["A"],
  currentGamePointsA: "ZERO",
  currentGamePointsB: "ZERO",
  tieBreakPointsA: 0,
  tieBreakPointsB: 0,
  isTieBreak: false,
  isSuperTieBreak: false,
  matchWinner: null,
  playerAName: "Benjamin",
  playerBName: "Marceau",
  surface: "CLAY",
  tournament: "Roland Garros",
  competitionType: null,
  startedAt: null,
};

beforeEach(() => {
  FakeEventSource.instances = [];
  vi.stubGlobal("EventSource", FakeEventSource);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe("LiveScoreBoard", () => {
  it("affiche la durée écoulée quand startedAt est renseigné", () => {
    const startedAt = Date.now() - 12 * 60_000;
    render(<LiveScoreBoard token="token-1" initialSnapshot={{ ...baseSnapshot, startedAt }} />);

    expect(screen.getByText(/\d+ min/)).toBeInTheDocument();
  });

  it("n'affiche pas de durée quand startedAt est null", () => {
    render(<LiveScoreBoard token="token-1" initialSnapshot={{ ...baseSnapshot, startedAt: null }} />);

    expect(screen.queryByText(/\d+ min/)).not.toBeInTheDocument();
  });

  it("ne rend pas le déroulé du set quand le match est terminé", () => {
    render(
      <LiveScoreBoard
        token="token-1"
        initialSnapshot={{ ...baseSnapshot, status: "ENDED", matchWinner: "A", startedAt: Date.now() - 60_000 }}
      />
    );

    expect(screen.queryByText(/DÉROULÉ/)).not.toBeInTheDocument();
    expect(screen.queryByTestId("set-trail-pending")).not.toBeInTheDocument();
  });

  it("rend le déroulé du set quand le match est en direct", () => {
    render(<LiveScoreBoard token="token-1" initialSnapshot={{ ...baseSnapshot, status: "LIVE" }} />);

    expect(screen.getByText(/DÉROULÉ/)).toBeInTheDocument();
    expect(screen.getByTestId("set-trail-pending")).toBeInTheDocument();
  });

  it("rend le déroulé du set quand le match est en attente", () => {
    render(
      <LiveScoreBoard
        token="token-1"
        initialSnapshot={{ ...baseSnapshot, status: "WAITING", currentSetPointLog: [], startedAt: null }}
      />
    );

    expect(screen.getByText(/DÉROULÉ/)).toBeInTheDocument();
    expect(screen.getByTestId("set-trail-pending")).toBeInTheDocument();
  });
});
