import type { LiveSnapshot, SessionDto, SetResult } from "./types";

export class ShareNotFoundError extends Error {}
export class ShareExpiredError extends Error {}
export class UnauthorizedError extends Error {}

type RawSnapshot = {
  status: "WAITING" | "LIVE" | "ENDED";
  completed_sets: { games_a: number; games_b: number }[];
  current_set_games_a: number;
  current_set_games_b: number;
  current_set_point_log: ("A" | "B")[];
  current_game_points_a: string;
  current_game_points_b: string;
  tie_break_points_a: number;
  tie_break_points_b: number;
  is_tie_break: boolean;
  is_super_tie_break: boolean;
  match_winner: "A" | "B" | null;
  player_a_name: string | null;
  player_b_name: string | null;
  surface: string | null;
  tournament: string | null;
  competition_type: string | null;
  started_at: number | null;
};

export function mapSnapshot(raw: RawSnapshot): LiveSnapshot {
  const completedSets: SetResult[] = raw.completed_sets.map((s) => ({
    gamesA: s.games_a,
    gamesB: s.games_b,
  }));
  return {
    status: raw.status,
    completedSets,
    currentSetGamesA: raw.current_set_games_a,
    currentSetGamesB: raw.current_set_games_b,
    currentSetPointLog: raw.current_set_point_log,
    currentGamePointsA: raw.current_game_points_a,
    currentGamePointsB: raw.current_game_points_b,
    tieBreakPointsA: raw.tie_break_points_a,
    tieBreakPointsB: raw.tie_break_points_b,
    isTieBreak: raw.is_tie_break,
    isSuperTieBreak: raw.is_super_tie_break,
    matchWinner: raw.match_winner,
    playerAName: raw.player_a_name,
    playerBName: raw.player_b_name,
    surface: raw.surface,
    tournament: raw.tournament,
    competitionType: raw.competition_type,
    startedAt: raw.started_at,
  };
}

export async function getLiveSnapshot(token: string): Promise<LiveSnapshot> {
  const baseUrl = typeof window === "undefined" ? process.env.API_BASE_URL : process.env.NEXT_PUBLIC_API_BASE_URL;
  const response = await fetch(`${baseUrl}/api/v1/live/${token}`, { cache: "no-store" });
  if (response.status === 404) throw new ShareNotFoundError();
  if (response.status === 410) throw new ShareExpiredError();
  if (!response.ok) throw new Error(`Erreur inattendue (${response.status})`);
  const raw = (await response.json()) as RawSnapshot;
  return mapSnapshot(raw);
}

type RawSession = {
  id: number;
  surface: string;
  match_format: string;
  third_set_rule: string;
  opponent: string | null;
  competition_type: string | null;
  tournament: string | null;
  status: string;
  session_type: string;
  result: string | null;
  score_text: string | null;
  created_at: number;
  updated_at: number;
};

function mapSession(raw: RawSession): SessionDto {
  return {
    id: raw.id,
    surface: raw.surface,
    matchFormat: raw.match_format,
    thirdSetRule: raw.third_set_rule,
    opponent: raw.opponent,
    competitionType: raw.competition_type,
    tournament: raw.tournament,
    status: raw.status,
    sessionType: raw.session_type === "TRAINING" ? "TRAINING" : "MATCH",
    result: raw.result,
    scoreText: raw.score_text,
    createdAt: raw.created_at,
    updatedAt: raw.updated_at,
  };
}

export async function getSessions(token: string): Promise<SessionDto[]> {
  const response = await fetch(`${process.env.API_BASE_URL}/api/v1/sessions`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (response.status === 401) throw new UnauthorizedError();
  if (!response.ok) throw new Error(`Erreur inattendue (${response.status})`);
  const raw = (await response.json()) as { items: RawSession[]; total: number };
  return raw.items.map(mapSession);
}
