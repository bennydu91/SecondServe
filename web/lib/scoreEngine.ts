export type Player = "A" | "B";
export type GamePoint = "ZERO" | "FIFTEEN" | "THIRTY" | "FORTY" | "ADVANTAGE";
export type MatchFormatValue = "BEST_OF_1" | "BEST_OF_3";
export type ThirdSetRuleValue = "FULL_ADVANTAGE" | "SUPER_TIE_BREAK_10" | "SHORT_DECISIVE_SET";

export type SessionFormat = {
  matchFormat: MatchFormatValue;
  thirdSetRule: ThirdSetRuleValue;
};

export type SetResult = { gamesA: number; gamesB: number };

export type MatchScore = {
  completedSets: SetResult[];
  currentSetGamesA: number;
  currentSetGamesB: number;
  currentSetPointLog: Player[];
  currentGamePointsA: GamePoint;
  currentGamePointsB: GamePoint;
  tieBreakPointsA: number;
  tieBreakPointsB: number;
  isTieBreak: boolean;
  isSuperTieBreak: boolean;
  isMatchOver: boolean;
  matchWinner: Player | null;
};

export function emptyMatchScore(): MatchScore {
  return {
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
    isMatchOver: false,
    matchWinner: null,
  };
}

export function isDeuce(score: MatchScore): boolean {
  return (
    !score.isTieBreak &&
    !score.isSuperTieBreak &&
    score.currentGamePointsA === "FORTY" &&
    score.currentGamePointsB === "FORTY"
  );
}

export function formatSetsText(sets: SetResult[]): string {
  return sets.map((s) => `${s.gamesA}-${s.gamesB}`).join(" · ");
}

export function formatScoreText(score: MatchScore): string {
  return formatSetsText(score.completedSets);
}

export function computeSetsOutcome(sets: SetResult[]): { result: "VICTORY" | "DEFEAT" | null; scoreText: string } {
  const setsWonA = sets.filter((s) => s.gamesA > s.gamesB).length;
  const setsWonB = sets.filter((s) => s.gamesB > s.gamesA).length;
  const result: "VICTORY" | "DEFEAT" | null = setsWonA === setsWonB ? null : setsWonA > setsWonB ? "VICTORY" : "DEFEAT";
  return { result, scoreText: formatSetsText(sets) };
}

export function deriveMatchResult(score: MatchScore): "VICTORY" | "DEFEAT" | null {
  if (score.matchWinner === "A") return "VICTORY";
  if (score.matchWinner === "B") return "DEFEAT";
  return null;
}

export type EngineEvent =
  | { type: "POINT_SCORED"; score: MatchScore }
  | { type: "GAME_WON"; score: MatchScore; winner: Player; changeover: boolean }
  | { type: "SET_WON"; score: MatchScore; winner: Player; changeover: boolean }
  | { type: "MATCH_OVER"; score: MatchScore; winner: Player };

const GAME_POINT_PROGRESSION: Record<GamePoint, GamePoint | null> = {
  ZERO: "FIFTEEN",
  FIFTEEN: "THIRTY",
  THIRTY: "FORTY",
  FORTY: null,
  ADVANTAGE: null,
};

export class TennisScoreEngine {
  readonly format: SessionFormat;
  private state: MatchScore;
  private history: MatchScore[] = [];

  constructor(format: SessionFormat, seed?: MatchScore) {
    this.format = format;
    this.state = seed ?? emptyMatchScore();
  }

  get currentScore(): MatchScore {
    return this.state;
  }

  recordPoint(scorer: Player): EngineEvent {
    if (this.state.isMatchOver) {
      throw new Error("Cannot record point: match is over");
    }
    this.history.push(this.state);
    this.state = { ...this.state, currentSetPointLog: [...this.state.currentSetPointLog, scorer] };
    if (this.state.isSuperTieBreak) return this.processSuperTieBreakPoint(scorer);
    if (this.state.isTieBreak) return this.processTieBreakPoint(scorer);
    return this.processRegularPoint(scorer);
  }

  undo(): boolean {
    const previous = this.history.pop();
    if (previous === undefined) return false;
    this.state = previous;
    return true;
  }

  private processRegularPoint(scorer: Player): EngineEvent {
    const pA = this.state.currentGamePointsA;
    const pB = this.state.currentGamePointsB;

    if (pA === "ADVANTAGE" || pB === "ADVANTAGE") {
      if ((scorer === "A" && pA === "ADVANTAGE") || (scorer === "B" && pB === "ADVANTAGE")) {
        return this.awardGame(scorer);
      }
      this.state = { ...this.state, currentGamePointsA: "FORTY", currentGamePointsB: "FORTY" };
      return { type: "POINT_SCORED", score: this.state };
    }

    if (isDeuce(this.state)) {
      this.state =
        scorer === "A"
          ? { ...this.state, currentGamePointsA: "ADVANTAGE" }
          : { ...this.state, currentGamePointsB: "ADVANTAGE" };
      return { type: "POINT_SCORED", score: this.state };
    }

    const currentPoints = scorer === "A" ? pA : pB;
    const nextPoints = GAME_POINT_PROGRESSION[currentPoints];
    if (nextPoints === null) {
      return this.awardGame(scorer);
    }

    this.state =
      scorer === "A"
        ? { ...this.state, currentGamePointsA: nextPoints }
        : { ...this.state, currentGamePointsB: nextPoints };
    return { type: "POINT_SCORED", score: this.state };
  }

  private processTieBreakPoint(scorer: Player): EngineEvent {
    const newA = this.state.tieBreakPointsA + (scorer === "A" ? 1 : 0);
    const newB = this.state.tieBreakPointsB + (scorer === "B" ? 1 : 0);
    this.state = { ...this.state, tieBreakPointsA: newA, tieBreakPointsB: newB };

    const winner: Player | null = newA >= 7 && newA - newB >= 2 ? "A" : newB >= 7 && newB - newA >= 2 ? "B" : null;
    return winner !== null ? this.awardTieBreakGame(winner) : { type: "POINT_SCORED", score: this.state };
  }

  private processSuperTieBreakPoint(scorer: Player): EngineEvent {
    const newA = this.state.tieBreakPointsA + (scorer === "A" ? 1 : 0);
    const newB = this.state.tieBreakPointsB + (scorer === "B" ? 1 : 0);
    this.state = { ...this.state, tieBreakPointsA: newA, tieBreakPointsB: newB };

    const winner: Player | null = newA >= 10 && newA - newB >= 2 ? "A" : newB >= 10 && newB - newA >= 2 ? "B" : null;
    if (winner !== null) {
      const superTbResult: SetResult = { gamesA: newA, gamesB: newB };
      this.state = {
        ...this.state,
        completedSets: [...this.state.completedSets, superTbResult],
        isMatchOver: true,
        matchWinner: winner,
        isSuperTieBreak: false,
      };
      return { type: "MATCH_OVER", score: this.state, winner };
    }
    return { type: "POINT_SCORED", score: this.state };
  }

  private awardGame(winner: Player): EngineEvent {
    const newGamesA = this.state.currentSetGamesA + (winner === "A" ? 1 : 0);
    const newGamesB = this.state.currentSetGamesB + (winner === "B" ? 1 : 0);
    const totalGames = newGamesA + newGamesB;
    const changeover = totalGames % 2 === 1;

    this.state = {
      ...this.state,
      currentSetGamesA: newGamesA,
      currentSetGamesB: newGamesB,
      currentGamePointsA: "ZERO",
      currentGamePointsB: "ZERO",
      isTieBreak: false,
      tieBreakPointsA: 0,
      tieBreakPointsB: 0,
    };

    if (newGamesA === 6 && newGamesB === 6) {
      return this.startTieBreak(changeover, winner);
    }

    return this.checkSetWon(winner, changeover);
  }

  private awardTieBreakGame(winner: Player): EngineEvent {
    const newGamesA = this.state.currentSetGamesA + (winner === "A" ? 1 : 0);
    const newGamesB = this.state.currentSetGamesB + (winner === "B" ? 1 : 0);

    this.state = {
      ...this.state,
      currentSetGamesA: newGamesA,
      currentSetGamesB: newGamesB,
      isTieBreak: false,
      tieBreakPointsA: 0,
      tieBreakPointsB: 0,
    };
    // Gagner le tie-break gagne toujours le set (7-6, ou 4-3 en SHORT_DECISIVE_SET)
    return this.awardSet(winner);
  }

  private startTieBreak(changeover: boolean, lastGameWinner: Player): EngineEvent {
    this.state = {
      ...this.state,
      isTieBreak: true,
      tieBreakPointsA: 0,
      tieBreakPointsB: 0,
      currentGamePointsA: "ZERO",
      currentGamePointsB: "ZERO",
    };
    return { type: "GAME_WON", score: this.state, winner: lastGameWinner, changeover };
  }

  private checkSetWon(winner: Player, gameChangeover: boolean): EngineEvent {
    const gA = this.state.currentSetGamesA;
    const gB = this.state.currentSetGamesB;

    if (this.format.thirdSetRule === "SHORT_DECISIVE_SET" && this.isFinalSet() && gA === 3 && gB === 3) {
      return this.startTieBreak(gameChangeover, winner);
    }

    const isShortDecisive = this.format.thirdSetRule === "SHORT_DECISIVE_SET" && this.isFinalSet();
    const setWinner: Player | null =
      gA >= 6 && gA - gB >= 2
        ? "A"
        : gB >= 6 && gB - gA >= 2
          ? "B"
          : isShortDecisive && gA >= 4 && gA - gB >= 2
            ? "A"
            : isShortDecisive && gB >= 4 && gB - gA >= 2
              ? "B"
              : null;

    if (setWinner === null) {
      return { type: "GAME_WON", score: this.state, winner, changeover: gameChangeover };
    }

    return this.awardSet(setWinner);
  }

  private awardSet(winner: Player): EngineEvent {
    const totalGamesInSet = this.state.currentSetGamesA + this.state.currentSetGamesB;
    const setChangeover = totalGamesInSet % 2 === 1;

    const completedSet: SetResult = { gamesA: this.state.currentSetGamesA, gamesB: this.state.currentSetGamesB };
    const newCompletedSets = [...this.state.completedSets, completedSet];

    const setsWonA = newCompletedSets.filter((s) => s.gamesA > s.gamesB).length;
    const setsWonB = newCompletedSets.filter((s) => s.gamesB > s.gamesA).length;
    const setsToWin = this.format.matchFormat === "BEST_OF_1" ? 1 : 2;

    this.state = {
      ...this.state,
      completedSets: newCompletedSets,
      currentSetGamesA: 0,
      currentSetGamesB: 0,
      currentSetPointLog: [],
      currentGamePointsA: "ZERO",
      currentGamePointsB: "ZERO",
      tieBreakPointsA: 0,
      tieBreakPointsB: 0,
      isTieBreak: false,
    };

    if (setsWonA >= setsToWin || setsWonB >= setsToWin) {
      this.state = { ...this.state, isMatchOver: true, matchWinner: winner };
      return { type: "MATCH_OVER", score: this.state, winner };
    }

    if (
      this.format.matchFormat === "BEST_OF_3" &&
      this.format.thirdSetRule === "SUPER_TIE_BREAK_10" &&
      setsWonA === 1 &&
      setsWonB === 1
    ) {
      this.state = { ...this.state, isSuperTieBreak: true, tieBreakPointsA: 0, tieBreakPointsB: 0 };
      return { type: "SET_WON", score: this.state, winner, changeover: setChangeover };
    }

    return { type: "SET_WON", score: this.state, winner, changeover: setChangeover };
  }

  private isFinalSet(): boolean {
    if (this.format.matchFormat === "BEST_OF_1") return true;
    const setsWonA = this.state.completedSets.filter((s) => s.gamesA > s.gamesB).length;
    const setsWonB = this.state.completedSets.filter((s) => s.gamesB > s.gamesA).length;
    return setsWonA === 1 && setsWonB === 1;
  }
}
