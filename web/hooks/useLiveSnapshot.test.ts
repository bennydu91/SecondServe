import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useLiveSnapshot } from "./useLiveSnapshot";
import type { LiveSnapshot } from "@/lib/types";

class FakeEventSource {
  onmessage: ((event: { data: string }) => void) | null = null;
  onopen: (() => void) | null = null;
  static instances: FakeEventSource[] = [];
  constructor(public url: string) {
    FakeEventSource.instances.push(this);
  }
  close() {}
  emit(data: unknown) {
    this.onmessage?.({ data: JSON.stringify(data) });
  }
}

const initialSnapshot: LiveSnapshot = {
  status: "LIVE",
  completedSets: [],
  currentSetGamesA: 0,
  currentSetGamesB: 0,
  currentSetPointLog: [],
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
  tournament: null,
  competitionType: null,
  startedAt: 1000,
};

beforeEach(() => {
  FakeEventSource.instances = [];
  vi.stubGlobal("EventSource", FakeEventSource);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("useLiveSnapshot", () => {
  it("part du snapshot initial puis applique les événements reçus", () => {
    const { result } = renderHook(() => useLiveSnapshot("token-1", initialSnapshot));
    expect(result.current.snapshot.currentSetGamesA).toBe(0);

    act(() => {
      FakeEventSource.instances[0].emit({
        status: "LIVE",
        completed_sets: [],
        current_set_games_a: 1,
        current_set_games_b: 0,
        current_set_point_log: ["A"],
        current_game_points_a: "ZERO",
        current_game_points_b: "ZERO",
        tie_break_points_a: 0,
        tie_break_points_b: 0,
        is_tie_break: false,
        is_super_tie_break: false,
        match_winner: null,
        player_a_name: "Benjamin",
        player_b_name: "Marceau",
        surface: "CLAY",
        tournament: null,
        competition_type: null,
        started_at: 1000,
      });
    });

    expect(result.current.snapshot.currentSetGamesA).toBe(1);
    expect(result.current.snapshot.currentSetPointLog).toEqual(["A"]);
    expect(result.current.connectionState).toBe("live");
  });
});
