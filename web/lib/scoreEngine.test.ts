import { describe, expect, it } from "vitest";
import { TennisScoreEngine, formatScoreText, deriveMatchResult, formatSetsText, computeSetsOutcome } from "./scoreEngine";
import type { SessionFormat, Player, EngineEvent } from "./scoreEngine";

const bestOf1Format: SessionFormat = { matchFormat: "BEST_OF_1", thirdSetRule: "FULL_ADVANTAGE" };
const bestOf3Format: SessionFormat = { matchFormat: "BEST_OF_3", thirdSetRule: "FULL_ADVANTAGE" };
const superTbFormat: SessionFormat = { matchFormat: "BEST_OF_3", thirdSetRule: "SUPER_TIE_BREAK_10" };
const shortSetFormat: SessionFormat = { matchFormat: "BEST_OF_3", thirdSetRule: "SHORT_DECISIVE_SET" };

function winPoints(engine: TennisScoreEngine, scorer: Player, count: number): void {
  for (let i = 0; i < count; i++) engine.recordPoint(scorer);
}

function winGame(engine: TennisScoreEngine, scorer: Player): EngineEvent {
  for (let i = 0; i < 3; i++) engine.recordPoint(scorer);
  return engine.recordPoint(scorer);
}

function winGames(engine: TennisScoreEngine, scorer: Player, count: number): void {
  for (let i = 0; i < count; i++) winGame(engine, scorer);
}

function winSet6_0(engine: TennisScoreEngine, scorer: Player): EngineEvent {
  let event: EngineEvent = { type: "POINT_SCORED", score: engine.currentScore };
  for (let i = 0; i < 6; i++) event = winGame(engine, scorer);
  return event;
}

// Atteint 6-6 dans le set courant en alternant les jeux (A: 1-6, B: 1-6)
function reachSixSixTieBreak(engine: TennisScoreEngine): void {
  for (let i = 0; i < 5; i++) {
    winGame(engine, "A");
    winGame(engine, "B");
  }
  winGame(engine, "A"); // 6-5
  winGame(engine, "B"); // 6-6 → tie-break
}

describe("RegularGameRules", () => {
  it("points progressed correctly from 0 to game", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    expect(engine.currentScore.currentGamePointsA).toBe("ZERO");
    engine.recordPoint("A");
    expect(engine.currentScore.currentGamePointsA).toBe("FIFTEEN");
    engine.recordPoint("A");
    expect(engine.currentScore.currentGamePointsA).toBe("THIRTY");
    engine.recordPoint("A");
    expect(engine.currentScore.currentGamePointsA).toBe("FORTY");
  });

  it("winning game at 40-0 returns GAME_WON event", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    winPoints(engine, "A", 3);
    const event = engine.recordPoint("A");
    expect(event.type).toBe("GAME_WON");
    if (event.type !== "GAME_WON") throw new Error("expected GAME_WON");
    expect(event.winner).toBe("A");
    expect(event.score.currentSetGamesA).toBe(1);
    expect(event.score.currentSetGamesB).toBe(0);
  });

  it("deuce and advantage cycle", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    winPoints(engine, "A", 3);
    winPoints(engine, "B", 3);
    expect(engine.currentScore.currentGamePointsA).toBe("FORTY");
    expect(engine.currentScore.currentGamePointsB).toBe("FORTY");
    engine.recordPoint("A");
    expect(engine.currentScore.currentGamePointsA).toBe("ADVANTAGE");
    expect(engine.currentScore.currentGamePointsB).toBe("FORTY");
    engine.recordPoint("B");
    expect(engine.currentScore.currentGamePointsA).toBe("FORTY");
    expect(engine.currentScore.currentGamePointsB).toBe("FORTY");
    engine.recordPoint("B");
    expect(engine.currentScore.currentGamePointsB).toBe("ADVANTAGE");
    const event = engine.recordPoint("B");
    expect(event.type).toBe("GAME_WON");
    if (event.type !== "GAME_WON") throw new Error("expected GAME_WON");
    expect(event.winner).toBe("B");
  });

  it("multiple deuce-advantage cycles in one game", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    winPoints(engine, "A", 3);
    winPoints(engine, "B", 3);
    engine.recordPoint("A");
    expect(engine.currentScore.currentGamePointsA).toBe("ADVANTAGE");
    engine.recordPoint("B");
    expect(engine.currentScore.currentGamePointsA).toBe("FORTY");
    expect(engine.currentScore.currentGamePointsB).toBe("FORTY");
    engine.recordPoint("B");
    expect(engine.currentScore.currentGamePointsB).toBe("ADVANTAGE");
    engine.recordPoint("A");
    expect(engine.currentScore.currentGamePointsA).toBe("FORTY");
    expect(engine.currentScore.currentGamePointsB).toBe("FORTY");
    engine.recordPoint("A");
    const event = engine.recordPoint("A");
    expect(event.type).toBe("GAME_WON");
    if (event.type !== "GAME_WON") throw new Error("expected GAME_WON");
    expect(event.winner).toBe("A");
  });
});

describe("ChangeoversDetection", () => {
  it("changeover when total games is odd", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    const event = winGame(engine, "A");
    expect(event.type).toBe("GAME_WON");
    if (event.type !== "GAME_WON") throw new Error("expected GAME_WON");
    expect(event.changeover).toBe(true);
  });

  it("no changeover when total games is even", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    winGame(engine, "A");
    const event = winGame(engine, "B");
    expect(event.type).toBe("GAME_WON");
    if (event.type !== "GAME_WON") throw new Error("expected GAME_WON");
    expect(event.changeover).toBe(false);
  });

  it("SetWon changeover false when set total is even (6-0)", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    const event = winSet6_0(engine, "A");
    expect(event.type).toBe("SET_WON");
    if (event.type !== "SET_WON") throw new Error("expected SET_WON");
    expect(event.changeover).toBe(false);
  });

  it("changeover always true after tie-break (13 total games)", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    reachSixSixTieBreak(engine);
    expect(engine.currentScore.isTieBreak).toBe(true);
    winPoints(engine, "A", 6);
    const event = engine.recordPoint("A"); // 7-0 → SET_WON
    expect(event.type).toBe("SET_WON");
    if (event.type !== "SET_WON") throw new Error("expected SET_WON");
    expect(event.changeover).toBe(true);
    const score = engine.currentScore;
    expect(score.isTieBreak).toBe(false);
    expect(score.completedSets).toHaveLength(1);
    expect(score.completedSets[0]).toEqual({ gamesA: 7, gamesB: 6 });
  });
});

describe("SetsAndGames", () => {
  it("win set 6-0", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    const event = winSet6_0(engine, "A");
    expect(event.type).toBe("SET_WON");
    if (event.type !== "SET_WON") throw new Error("expected SET_WON");
    expect(event.winner).toBe("A");
    expect(engine.currentScore.completedSets).toEqual([{ gamesA: 6, gamesB: 0 }]);
  });

  it("win set 6-3", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    winGames(engine, "A", 3);
    winGames(engine, "B", 3);
    winGames(engine, "A", 2);
    const event = winGame(engine, "A");
    expect(event.type).toBe("SET_WON");
    if (event.type !== "SET_WON") throw new Error("expected SET_WON");
    expect(event.winner).toBe("A");
    expect(engine.currentScore.completedSets[0]).toEqual({ gamesA: 6, gamesB: 3 });
  });

  it("win set 6-4", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    winGames(engine, "A", 4);
    winGames(engine, "B", 4);
    winGames(engine, "A", 1);
    const event = winGame(engine, "A");
    expect(event.type).toBe("SET_WON");
    if (event.type !== "SET_WON") throw new Error("expected SET_WON");
    expect(engine.currentScore.completedSets[0]).toEqual({ gamesA: 6, gamesB: 4 });
  });
});

describe("TieBreak", () => {
  it("tie-break triggered at 6-6", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    reachSixSixTieBreak(engine);
    expect(engine.currentScore.isTieBreak).toBe(true);
  });

  it("tie-break won at 7 with 2-point lead", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    reachSixSixTieBreak(engine);
    winPoints(engine, "A", 7);
    const score = engine.currentScore;
    expect(score.isTieBreak).toBe(false);
    expect(score.completedSets).toEqual([{ gamesA: 7, gamesB: 6 }]);
  });

  it("tie-break requires 2-point lead (7-6 not enough, 8-6 enough)", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    reachSixSixTieBreak(engine);
    winPoints(engine, "A", 6);
    winPoints(engine, "B", 6);
    winPoints(engine, "A", 1);
    expect(engine.currentScore.isTieBreak).toBe(true);
    winPoints(engine, "A", 1);
    expect(engine.currentScore.isTieBreak).toBe(false);
  });
});

describe("SuperTieBreak", () => {
  it("super tie-break triggered at 1-1 sets in SUPER_TIE_BREAK_10 format", () => {
    const engine = new TennisScoreEngine(superTbFormat);
    winSet6_0(engine, "A");
    winSet6_0(engine, "B");
    expect(engine.currentScore.isSuperTieBreak).toBe(true);
    expect(engine.currentScore.isTieBreak).toBe(false);
  });

  it("super tie-break won at 10 with 2-point lead", () => {
    const engine = new TennisScoreEngine(superTbFormat);
    winSet6_0(engine, "A");
    winSet6_0(engine, "B");
    winPoints(engine, "A", 10);
    const score = engine.currentScore;
    expect(score.isMatchOver).toBe(true);
    expect(score.matchWinner).toBe("A");
  });

  it("super tie-break requires 2-point lead (10-9 not enough)", () => {
    const engine = new TennisScoreEngine(superTbFormat);
    winSet6_0(engine, "A");
    winSet6_0(engine, "B");
    winPoints(engine, "A", 9);
    winPoints(engine, "B", 9);
    winPoints(engine, "A", 1);
    expect(engine.currentScore.isSuperTieBreak).toBe(true);
    expect(engine.currentScore.isMatchOver).toBe(false);
    winPoints(engine, "A", 1);
    expect(engine.currentScore.isMatchOver).toBe(true);
  });

  it("super tie-break result appears in completedSets", () => {
    const engine = new TennisScoreEngine(superTbFormat);
    winSet6_0(engine, "A");
    winSet6_0(engine, "B");
    winPoints(engine, "A", 10);
    const score = engine.currentScore;
    expect(score.isMatchOver).toBe(true);
    expect(score.completedSets).toHaveLength(3);
    expect(score.completedSets[2]).toEqual({ gamesA: 10, gamesB: 0 });
  });
});

describe("UndoTests", () => {
  it("undo restores previous state", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    engine.recordPoint("A");
    expect(engine.currentScore.currentGamePointsA).toBe("FIFTEEN");
    const result = engine.undo();
    expect(result).toBe(true);
    expect(engine.currentScore.currentGamePointsA).toBe("ZERO");
  });

  it("undo returns false when history empty", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    expect(engine.undo()).toBe(false);
  });

  it("undo works across game boundary", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    winPoints(engine, "A", 3);
    engine.recordPoint("A");
    expect(engine.currentScore.currentSetGamesA).toBe(1);
    engine.undo();
    expect(engine.currentScore.currentSetGamesA).toBe(0);
    expect(engine.currentScore.currentGamePointsA).toBe("FORTY");
  });

  it("undo after match over is possible", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    winSet6_0(engine, "A");
    expect(engine.currentScore.isMatchOver).toBe(true);
    engine.undo();
    expect(engine.currentScore.isMatchOver).toBe(false);
  });

  it("undo works across set boundary", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    winSet6_0(engine, "A");
    expect(engine.currentScore.completedSets).toHaveLength(1);
    engine.undo();
    expect(engine.currentScore.completedSets).toHaveLength(0);
    expect(engine.currentScore.currentSetGamesA).toBe(5);
    expect(engine.currentScore.currentGamePointsA).toBe("FORTY");
  });

  it("undo supports multiple levels", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    engine.recordPoint("A");
    engine.recordPoint("A");
    engine.recordPoint("B");
    engine.undo();
    expect(engine.currentScore.currentGamePointsB).toBe("ZERO");
    expect(engine.currentScore.currentGamePointsA).toBe("THIRTY");
    engine.undo();
    expect(engine.currentScore.currentGamePointsA).toBe("FIFTEEN");
    engine.undo();
    expect(engine.currentScore.currentGamePointsA).toBe("ZERO");
    expect(engine.undo()).toBe(false);
  });
});

describe("MatchFormats", () => {
  it("BEST_OF_1 match over after one set", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    const event = winSet6_0(engine, "A");
    expect(event.type).toBe("MATCH_OVER");
    if (event.type !== "MATCH_OVER") throw new Error("expected MATCH_OVER");
    expect(event.winner).toBe("A");
  });

  it("BEST_OF_3 requires 2 sets to win", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    winSet6_0(engine, "A");
    expect(engine.currentScore.isMatchOver).toBe(false);
    winSet6_0(engine, "A");
    expect(engine.currentScore.isMatchOver).toBe(true);
  });

  it("BEST_OF_3 can reach 3 sets", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    winSet6_0(engine, "A");
    winSet6_0(engine, "B");
    expect(engine.currentScore.isMatchOver).toBe(false);
    expect(engine.currentScore.completedSets).toHaveLength(2);
  });

  it("cannot record point after match over", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    winSet6_0(engine, "A");
    expect(() => engine.recordPoint("A")).toThrow("Cannot record point: match is over");
  });

  it("BEST_OF_3 FULL_ADVANTAGE win in 2 sets", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    winSet6_0(engine, "A");
    const event = winSet6_0(engine, "A");
    expect(event.type).toBe("MATCH_OVER");
    if (event.type !== "MATCH_OVER") throw new Error("expected MATCH_OVER");
    expect(event.winner).toBe("A");
    expect(engine.currentScore.completedSets).toHaveLength(2);
  });

  it("BEST_OF_3 Player B wins in 2 sets", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    winSet6_0(engine, "B");
    expect(engine.currentScore.isMatchOver).toBe(false);
    const event = winSet6_0(engine, "B");
    expect(event.type).toBe("MATCH_OVER");
    if (event.type !== "MATCH_OVER") throw new Error("expected MATCH_OVER");
    expect(event.winner).toBe("B");
    expect(engine.currentScore.completedSets).toHaveLength(2);
  });

  it("BEST_OF_3 FULL_ADVANTAGE win in 3 sets", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    winSet6_0(engine, "A");
    winSet6_0(engine, "B");
    const event = winSet6_0(engine, "A");
    expect(event.type).toBe("MATCH_OVER");
    if (event.type !== "MATCH_OVER") throw new Error("expected MATCH_OVER");
    expect(event.winner).toBe("A");
  });
});

describe("ShortDecisiveSet", () => {
  it("win at 4-0 in third set", () => {
    const engine = new TennisScoreEngine(shortSetFormat);
    winSet6_0(engine, "A");
    winSet6_0(engine, "B");
    winGames(engine, "A", 3);
    const event = winGame(engine, "A");
    expect(event.type).toBe("MATCH_OVER");
    if (event.type !== "MATCH_OVER") throw new Error("expected MATCH_OVER");
    expect(event.winner).toBe("A");
  });

  it("win at 4-2 in third set", () => {
    const engine = new TennisScoreEngine(shortSetFormat);
    winSet6_0(engine, "A");
    winSet6_0(engine, "B");
    winGames(engine, "A", 2);
    winGames(engine, "B", 2);
    winGames(engine, "A", 1);
    const event = winGame(engine, "A");
    expect(event.type).toBe("MATCH_OVER");
    if (event.type !== "MATCH_OVER") throw new Error("expected MATCH_OVER");
    expect(event.winner).toBe("A");
  });

  it("tie-break at 3-3", () => {
    const engine = new TennisScoreEngine(shortSetFormat);
    winSet6_0(engine, "A");
    winSet6_0(engine, "B");
    winGames(engine, "A", 3);
    winGames(engine, "B", 3);
    expect(engine.currentScore.isTieBreak).toBe(true);
  });

  it("tie-break at 3-3 then win match", () => {
    const engine = new TennisScoreEngine(shortSetFormat);
    winSet6_0(engine, "A");
    winSet6_0(engine, "B");
    winGames(engine, "A", 3);
    winGames(engine, "B", 3);
    expect(engine.currentScore.isTieBreak).toBe(true);
    winPoints(engine, "A", 7);
    expect(engine.currentScore.isMatchOver).toBe(true);
    expect(engine.currentScore.matchWinner).toBe("A");
  });

  it("Player B wins third set 4-0", () => {
    const engine = new TennisScoreEngine(shortSetFormat);
    winSet6_0(engine, "A");
    winSet6_0(engine, "B");
    winGames(engine, "B", 3);
    const event = winGame(engine, "B");
    expect(event.type).toBe("MATCH_OVER");
    if (event.type !== "MATCH_OVER") throw new Error("expected MATCH_OVER");
    expect(event.winner).toBe("B");
  });

  it("Player B wins third set 4-2", () => {
    const engine = new TennisScoreEngine(shortSetFormat);
    winSet6_0(engine, "A");
    winSet6_0(engine, "B");
    winGames(engine, "A", 2);
    winGames(engine, "B", 2);
    winGames(engine, "B", 1);
    const event = winGame(engine, "B");
    expect(event.type).toBe("MATCH_OVER");
    if (event.type !== "MATCH_OVER") throw new Error("expected MATCH_OVER");
    expect(event.winner).toBe("B");
  });
});

describe("IllegalStateChecks", () => {
  it("throws after match is over", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    winSet6_0(engine, "A");
    expect(() => engine.recordPoint("A")).toThrow();
  });

  it("throws with correct message", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    winSet6_0(engine, "A");
    expect(() => engine.recordPoint("B")).toThrow("Cannot record point: match is over");
  });
});

describe("PointLogTracking", () => {
  it("recordPoint appends scorer to currentSetPointLog", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    engine.recordPoint("A");
    engine.recordPoint("B");
    engine.recordPoint("A");
    expect(engine.currentScore.currentSetPointLog).toEqual(["A", "B", "A"]);
  });

  it("currentSetPointLog resets when the set is won", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    winSet6_0(engine, "A");
    expect(engine.currentScore.currentSetPointLog).toEqual([]);
  });

  it("currentSetPointLog is restored by undo", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    engine.recordPoint("A");
    engine.recordPoint("B");
    engine.undo();
    expect(engine.currentScore.currentSetPointLog).toEqual(["A"]);
  });
});

describe("formatScoreText", () => {
  it("joins completed sets with ' · '", () => {
    const engine = new TennisScoreEngine(bestOf3Format);
    winSet6_0(engine, "A");
    winGames(engine, "B", 3);
    winGames(engine, "A", 3);
    winGames(engine, "A", 2);
    winGame(engine, "A"); // 6-3 → 2nd set won
    expect(formatScoreText(engine.currentScore)).toBe("6-0 · 6-3");
  });

  it("returns empty string when no set completed", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    expect(formatScoreText(engine.currentScore)).toBe("");
  });
});

describe("deriveMatchResult", () => {
  it("returns VICTORY when self (A) wins", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    winSet6_0(engine, "A");
    expect(deriveMatchResult(engine.currentScore)).toBe("VICTORY");
  });

  it("returns DEFEAT when opponent (B) wins", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    winSet6_0(engine, "B");
    expect(deriveMatchResult(engine.currentScore)).toBe("DEFEAT");
  });

  it("returns null while match is ongoing", () => {
    const engine = new TennisScoreEngine(bestOf1Format);
    expect(deriveMatchResult(engine.currentScore)).toBeNull();
  });
});

describe("formatSetsText", () => {
  it("joins sets with ' · '", () => {
    expect(formatSetsText([{ gamesA: 6, gamesB: 4 }, { gamesA: 6, gamesB: 3 }])).toBe("6-4 · 6-3");
  });

  it("returns empty string for empty array", () => {
    expect(formatSetsText([])).toBe("");
  });
});

describe("computeSetsOutcome", () => {
  it("returns VICTORY when self wins more sets (2-0)", () => {
    const outcome = computeSetsOutcome([{ gamesA: 6, gamesB: 4 }, { gamesA: 6, gamesB: 3 }]);
    expect(outcome.result).toBe("VICTORY");
    expect(outcome.scoreText).toBe("6-4 · 6-3");
  });

  it("returns VICTORY when self wins in 3 sets (2-1)", () => {
    const outcome = computeSetsOutcome([
      { gamesA: 6, gamesB: 4 },
      { gamesA: 3, gamesB: 6 },
      { gamesA: 10, gamesB: 7 },
    ]);
    expect(outcome.result).toBe("VICTORY");
  });

  it("returns DEFEAT when opponent wins more sets", () => {
    const outcome = computeSetsOutcome([{ gamesA: 4, gamesB: 6 }, { gamesA: 3, gamesB: 6 }]);
    expect(outcome.result).toBe("DEFEAT");
  });

  it("returns DEFEAT for a single lost set (BEST_OF_1)", () => {
    const outcome = computeSetsOutcome([{ gamesA: 4, gamesB: 6 }]);
    expect(outcome.result).toBe("DEFEAT");
  });

  it("returns null when sets are equal (aucun set)", () => {
    expect(computeSetsOutcome([]).result).toBeNull();
  });
});
