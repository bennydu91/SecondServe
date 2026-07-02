export type SetResult = { gamesA: number; gamesB: number };

export type LiveSnapshot = {
  status: "WAITING" | "LIVE" | "ENDED";
  completedSets: SetResult[];
  currentSetGamesA: number;
  currentSetGamesB: number;
  currentSetPointLog: ("A" | "B")[];
  currentGamePointsA: string;
  currentGamePointsB: string;
  tieBreakPointsA: number;
  tieBreakPointsB: number;
  isTieBreak: boolean;
  isSuperTieBreak: boolean;
  matchWinner: "A" | "B" | null;
  playerAName: string | null;
  playerBName: string | null;
  surface: string | null;
  tournament: string | null;
  competitionType: string | null;
  startedAt: number | null;
};
