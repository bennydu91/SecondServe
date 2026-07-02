import { describe, expect, it, vi, afterEach } from "vitest";
import { getLiveSnapshot, ShareExpiredError, ShareNotFoundError, getSessions, UnauthorizedError } from "./api";

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

const rawSession = {
  id: 1,
  surface: "CLAY",
  match_format: "BEST_OF_3",
  third_set_rule: "FULL_ADVANTAGE",
  opponent: "Marceau",
  competition_type: "CLUB",
  tournament: "Tournoi du club",
  status: "COMPLETED",
  session_type: "MATCH",
  result: "VICTORY",
  score_text: "6-4 · 6-3",
  created_at: 1000,
  updated_at: 2000,
};

describe("getSessions", () => {
  it("mappe la liste snake_case du backend vers des SessionDto camelCase", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({ items: [rawSession], total: 1 }),
      })
    );
    const sessions = await getSessions("jwt-token");
    expect(sessions).toEqual([
      {
        id: 1,
        surface: "CLAY",
        matchFormat: "BEST_OF_3",
        thirdSetRule: "FULL_ADVANTAGE",
        opponent: "Marceau",
        competitionType: "CLUB",
        tournament: "Tournoi du club",
        status: "COMPLETED",
        sessionType: "MATCH",
        result: "VICTORY",
        scoreText: "6-4 · 6-3",
        createdAt: 1000,
        updatedAt: 2000,
      },
    ]);
  });

  it("envoie le JWT en Authorization Bearer", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => ({ items: [], total: 0 }) });
    vi.stubGlobal("fetch", fetchMock);
    await getSessions("jwt-token");
    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers.Authorization).toBe("Bearer jwt-token");
  });

  it("lève UnauthorizedError sur 401", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 401, json: async () => ({}) }));
    await expect(getSessions("expired-token")).rejects.toThrow(UnauthorizedError);
  });
});
