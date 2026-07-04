import { describe, expect, it, vi, afterEach } from "vitest";
import { getLiveSnapshot, ShareExpiredError, ShareNotFoundError, getSessions, UnauthorizedError } from "./api";
import {
  createSession,
  getPoints,
  postPoint,
  deleteLastPoint,
  putScoreSeed,
  getShareForSession,
  pushLiveScore,
  finalizeSession,
  parseScoreSeed,
  updateSession,
  deleteSession,
} from "./api";

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

describe("createSession", () => {
  it("envoie les champs surface/format/date en snake_case et mappe la réponse", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 201,
      json: async () => ({ ...rawSession, id: 42 }),
    });
    vi.stubGlobal("fetch", fetchMock);

    const result = await createSession("jwt-token", {
      surface: "CLAY",
      matchFormat: "BEST_OF_3",
      thirdSetRule: "FULL_ADVANTAGE",
      createdAt: 5_000_000,
    });

    expect(result.id).toBe(42);
    const [, init] = fetchMock.mock.calls[0];
    const body = JSON.parse(init.body as string);
    expect(body).toEqual({
      surface: "CLAY",
      match_format: "BEST_OF_3",
      third_set_rule: "FULL_ADVANTAGE",
      opponent: null,
      competition_type: null,
      tournament: null,
      created_at: 5_000_000,
    });
  });
});

describe("points client", () => {
  it("getPoints mappe la liste snake_case en PointDto[]", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({
          items: [
            { id: 1, session_id: 7, scorer: "A", context: "ACE", sequence_num: 1, recorded_at: 1000 },
          ],
        }),
      })
    );
    const points = await getPoints("jwt-token", 7);
    expect(points).toEqual([
      { id: 1, sessionId: 7, scorer: "A", context: "ACE", sequenceNum: 1, recordedAt: 1000 },
    ]);
  });

  it("postPoint envoie { context } et mappe la réponse", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 201,
      json: async () => ({ id: 2, session_id: 7, scorer: "B", context: "DOUBLE_FAULT", sequence_num: 2, recorded_at: 2000 }),
    });
    vi.stubGlobal("fetch", fetchMock);
    const point = await postPoint("jwt-token", 7, "DOUBLE_FAULT");
    expect(point.scorer).toBe("B");
    const [, init] = fetchMock.mock.calls[0];
    expect(JSON.parse(init.body as string)).toEqual({ context: "DOUBLE_FAULT" });
  });

  it("deleteLastPoint appelle DELETE sans lever d'erreur sur 204", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true, status: 204 }));
    await expect(deleteLastPoint("jwt-token", 7)).resolves.toBeUndefined();
  });
});

describe("putScoreSeed", () => {
  it("envoie le seed en snake_case et mappe la SessionDto retournée", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ ...rawSession, score_seed_json: '{"a":1}' }),
    });
    vi.stubGlobal("fetch", fetchMock);

    const result = await putScoreSeed("jwt-token", 7, {
      completedSets: [{ gamesA: 6, gamesB: 4 }],
      currentSetGamesA: 2,
      currentSetGamesB: 1,
      currentGamePointsA: "FORTY",
      currentGamePointsB: "THIRTY",
      tieBreakPointsA: 0,
      tieBreakPointsB: 0,
      isTieBreak: false,
      isSuperTieBreak: false,
    });

    expect(result.scoreSeedJson).toBe('{"a":1}');
    const [, init] = fetchMock.mock.calls[0];
    const body = JSON.parse(init.body as string);
    expect(body.completed_sets).toEqual([{ games_a: 6, games_b: 4 }]);
    expect(body.current_set_games_a).toBe(2);
  });
});

describe("live share client", () => {
  it("getShareForSession retourne null sur 404", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 404 }));
    expect(await getShareForSession("jwt-token", 7)).toBeNull();
  });

  it("getShareForSession retourne { token, url } quand le partage existe", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => ({ token: "abc", url: "https://x/live/abc" }) })
    );
    expect(await getShareForSession("jwt-token", 7)).toEqual({ token: "abc", url: "https://x/live/abc" });
  });

  it("pushLiveScore convertit le payload camelCase en snake_case", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 204 });
    vi.stubGlobal("fetch", fetchMock);
    await pushLiveScore("jwt-token", 7, {
      completedSets: [{ gamesA: 6, gamesB: 4 }],
      currentSetGamesA: 1,
      currentSetGamesB: 0,
      currentSetPointLog: ["A"],
      currentGamePointsA: "FIFTEEN",
      currentGamePointsB: "ZERO",
      tieBreakPointsA: 0,
      tieBreakPointsB: 0,
      isTieBreak: false,
      isSuperTieBreak: false,
      isMatchOver: false,
      matchWinner: null,
      playerAName: "Benjamin",
      playerBName: "Marceau",
      surface: "CLAY",
      tournament: null,
      competitionType: null,
      startedAt: 1000,
    });
    const [, init] = fetchMock.mock.calls[0];
    const body = JSON.parse(init.body as string);
    expect(body.current_set_point_log).toEqual(["A"]);
    expect(body.player_a_name).toBe("Benjamin");
    expect(body.completed_sets).toEqual([{ games_a: 6, games_b: 4 }]);
  });
});

describe("finalizeSession", () => {
  it("enveloppe la session dans un SyncSessionDto avec les champs post-completion à null", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => ({ synced_sessions: 1 }) });
    vi.stubGlobal("fetch", fetchMock);

    await finalizeSession("jwt-token", {
      session: {
        id: 7,
        surface: "CLAY",
        matchFormat: "BEST_OF_3",
        thirdSetRule: "FULL_ADVANTAGE",
        opponent: "Marceau",
        competitionType: null,
        tournament: null,
        status: "ACTIVE",
        sessionType: "MATCH",
        result: null,
        scoreText: null,
        scoreSeedJson: null,
        createdAt: 1000,
        updatedAt: 1000,
      },
      status: "COMPLETED",
      result: "VICTORY",
      scoreText: "6-4 · 6-3",
      updatedAt: 9999,
    });

    const [, init] = fetchMock.mock.calls[0];
    const body = JSON.parse(init.body as string);
    expect(body.sessions[0]).toEqual({
      client_id: 7,
      surface: "CLAY",
      match_format: "BEST_OF_3",
      third_set_rule: "FULL_ADVANTAGE",
      opponent: "Marceau",
      competition_type: null,
      tournament: null,
      status: "COMPLETED",
      session_type: "MATCH",
      result: "VICTORY",
      feeling_rating: null,
      feeling_comment: null,
      created_at: 1000,
      updated_at: 9999,
      scheduled_at: null,
      score_text: "6-4 · 6-3",
      first_serve_percent_self: null,
      first_serve_percent_opponent: null,
      winners_self: null,
      winners_opponent: null,
    });
  });
});

describe("parseScoreSeed", () => {
  it("retourne null quand scoreSeedJson est null", () => {
    expect(parseScoreSeed(null)).toBeNull();
  });

  it("parse le JSON snake_case du backend vers un MatchScore camelCase", () => {
    const json = JSON.stringify({
      completed_sets: [{ games_a: 6, games_b: 4 }],
      current_set_games_a: 2,
      current_set_games_b: 1,
      current_game_points_a: "FORTY",
      current_game_points_b: "THIRTY",
      tie_break_points_a: 0,
      tie_break_points_b: 0,
      is_tie_break: false,
      is_super_tie_break: false,
    });

    const score = parseScoreSeed(json);

    expect(score).toEqual({
      completedSets: [{ gamesA: 6, gamesB: 4 }],
      currentSetGamesA: 2,
      currentSetGamesB: 1,
      currentSetPointLog: [],
      currentGamePointsA: "FORTY",
      currentGamePointsB: "THIRTY",
      tieBreakPointsA: 0,
      tieBreakPointsB: 0,
      isTieBreak: false,
      isSuperTieBreak: false,
      isMatchOver: false,
      matchWinner: null,
    });
  });
});

describe("updateSession", () => {
  it("envoie uniquement les champs fournis en snake_case et mappe la SessionDto retournée", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        id: 7,
        surface: "HARD",
        match_format: "BEST_OF_3",
        third_set_rule: "FULL_ADVANTAGE",
        opponent: "Martin",
        competition_type: null,
        tournament: null,
        status: "COMPLETED",
        session_type: "MATCH",
        result: "VICTORY",
        score_text: "6-4 · 6-3",
        score_seed_json: null,
        created_at: 1000,
        updated_at: 1000,
      }),
    });
    vi.stubGlobal("fetch", fetchMock);

    const result = await updateSession("jwt-token", 7, { opponent: "Martin", surface: "HARD" });

    expect(result.opponent).toBe("Martin");
    expect(result.surface).toBe("HARD");
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toContain("/sessions/7");
    expect(init.method).toBe("PATCH");
    expect(JSON.parse(init.body as string)).toEqual({ opponent: "Martin", surface: "HARD" });
  });

  it("lève UnauthorizedError sur 401", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 401 }));
    await expect(updateSession("jwt-token", 7, { opponent: "Martin" })).rejects.toThrow(UnauthorizedError);
  });
});

describe("deleteSession", () => {
  it("appelle DELETE sans lever d'erreur sur 204", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 204 });
    vi.stubGlobal("fetch", fetchMock);

    await deleteSession("jwt-token", 7);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toContain("/sessions/7");
    expect(init.method).toBe("DELETE");
  });

  it("lève UnauthorizedError sur 401", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 401 }));
    await expect(deleteSession("jwt-token", 7)).rejects.toThrow(UnauthorizedError);
  });
});
