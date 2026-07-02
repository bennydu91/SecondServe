import { describe, expect, it, vi, afterEach } from "vitest";
import { getLiveSnapshot, ShareExpiredError, ShareNotFoundError } from "./api";

const rawSnapshot = {
  status: "LIVE",
  completed_sets: [{ games_a: 6, games_b: 4 }],
  current_set_games_a: 2,
  current_set_games_b: 1,
  current_set_point_log: ["A", "B", "A"],
  current_game_points_a: "FORTY",
  current_game_points_b: "THIRTY",
  tie_break_points_a: 0,
  tie_break_points_b: 0,
  is_tie_break: false,
  is_super_tie_break: false,
  match_winner: null,
  player_a_name: "Benjamin",
  player_b_name: "Marceau",
  surface: "CLAY",
  tournament: "Tournoi du club",
  competition_type: "CLUB",
  started_at: 1000,
};

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("getLiveSnapshot", () => {
  it("mappe la réponse snake_case du backend vers le type camelCase", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => rawSnapshot })
    );
    const snapshot = await getLiveSnapshot("abc123");
    expect(snapshot.currentSetGamesA).toBe(2);
    expect(snapshot.playerAName).toBe("Benjamin");
    expect(snapshot.completedSets).toEqual([{ gamesA: 6, gamesB: 4 }]);
    expect(snapshot.currentSetPointLog).toEqual(["A", "B", "A"]);
  });

  it("lève ShareNotFoundError sur 404", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 404, json: async () => ({}) }));
    await expect(getLiveSnapshot("unknown")).rejects.toThrow(ShareNotFoundError);
  });

  it("lève ShareExpiredError sur 410", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 410, json: async () => ({}) }));
    await expect(getLiveSnapshot("expired")).rejects.toThrow(ShareExpiredError);
  });
});
