import type {
  LiveSnapshot,
  SessionDto,
  SetResult,
  PointDto,
  PointContext,
  ScoreSeed,
} from "./types";

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
  score_seed_json: string | null;
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
    scoreSeedJson: raw.score_seed_json,
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

export type CreateSessionInput = {
  surface: string;
  matchFormat: string;
  thirdSetRule: string;
  opponent?: string | null;
  competitionType?: string | null;
  tournament?: string | null;
  createdAt: number;
};

export async function createSession(token: string, input: CreateSessionInput): Promise<SessionDto> {
  const response = await fetch(`${process.env.API_BASE_URL}/api/v1/sessions`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      surface: input.surface,
      match_format: input.matchFormat,
      third_set_rule: input.thirdSetRule,
      opponent: input.opponent ?? null,
      competition_type: input.competitionType ?? null,
      tournament: input.tournament ?? null,
      created_at: input.createdAt,
    }),
    cache: "no-store",
  });
  if (response.status === 401) throw new UnauthorizedError();
  if (!response.ok) throw new Error(`Erreur inattendue (${response.status})`);
  const raw = (await response.json()) as RawSession;
  return mapSession(raw);
}

type RawPoint = {
  id: number;
  session_id: number;
  scorer: "A" | "B";
  context: string | null;
  sequence_num: number;
  recorded_at: number;
};

function mapPoint(raw: RawPoint): PointDto {
  return {
    id: raw.id,
    sessionId: raw.session_id,
    scorer: raw.scorer,
    context: raw.context as PointContext | null,
    sequenceNum: raw.sequence_num,
    recordedAt: raw.recorded_at,
  };
}

export async function getPoints(token: string, sessionId: number): Promise<PointDto[]> {
  const response = await fetch(`${process.env.API_BASE_URL}/api/v1/sessions/${sessionId}/points`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (response.status === 401) throw new UnauthorizedError();
  if (!response.ok) throw new Error(`Erreur inattendue (${response.status})`);
  const raw = (await response.json()) as { items: RawPoint[] };
  return raw.items.map(mapPoint);
}

export async function postPoint(token: string, sessionId: number, context: PointContext): Promise<PointDto> {
  const response = await fetch(`${process.env.API_BASE_URL}/api/v1/sessions/${sessionId}/points`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ context }),
    cache: "no-store",
  });
  if (response.status === 401) throw new UnauthorizedError();
  if (!response.ok) throw new Error(`Erreur inattendue (${response.status})`);
  const raw = (await response.json()) as RawPoint;
  return mapPoint(raw);
}

export async function deleteLastPoint(token: string, sessionId: number): Promise<void> {
  const response = await fetch(`${process.env.API_BASE_URL}/api/v1/sessions/${sessionId}/points/last`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (response.status === 401) throw new UnauthorizedError();
  if (!response.ok) throw new Error(`Erreur inattendue (${response.status})`);
}

export async function putScoreSeed(token: string, sessionId: number, seed: ScoreSeed): Promise<SessionDto> {
  const response = await fetch(`${process.env.API_BASE_URL}/api/v1/sessions/${sessionId}/score-seed`, {
    method: "PUT",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      completed_sets: seed.completedSets.map((s) => ({ games_a: s.gamesA, games_b: s.gamesB })),
      current_set_games_a: seed.currentSetGamesA,
      current_set_games_b: seed.currentSetGamesB,
      current_game_points_a: seed.currentGamePointsA,
      current_game_points_b: seed.currentGamePointsB,
      tie_break_points_a: seed.tieBreakPointsA,
      tie_break_points_b: seed.tieBreakPointsB,
      is_tie_break: seed.isTieBreak,
      is_super_tie_break: seed.isSuperTieBreak,
    }),
    cache: "no-store",
  });
  if (response.status === 401) throw new UnauthorizedError();
  if (!response.ok) throw new Error(`Erreur inattendue (${response.status})`);
  const raw = (await response.json()) as RawSession;
  return mapSession(raw);
}

export async function getShareForSession(
  token: string,
  sessionId: number
): Promise<{ token: string; url: string } | null> {
  const response = await fetch(`${process.env.API_BASE_URL}/api/v1/live/shares/by-session/${sessionId}`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (response.status === 404) return null;
  if (response.status === 401) throw new UnauthorizedError();
  if (!response.ok) throw new Error(`Erreur inattendue (${response.status})`);
  return (await response.json()) as { token: string; url: string };
}

export type LiveScoreUpdatePayload = {
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
  isMatchOver: boolean;
  matchWinner: "A" | "B" | null;
  playerAName: string;
  playerBName: string;
  surface: string;
  tournament: string | null;
  competitionType: string | null;
  startedAt: number;
};

export async function pushLiveScore(token: string, sessionId: number, payload: LiveScoreUpdatePayload): Promise<void> {
  const response = await fetch(`${process.env.API_BASE_URL}/api/v1/live/sessions/${sessionId}/score`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      completed_sets: payload.completedSets.map((s) => ({ games_a: s.gamesA, games_b: s.gamesB })),
      current_set_games_a: payload.currentSetGamesA,
      current_set_games_b: payload.currentSetGamesB,
      current_set_point_log: payload.currentSetPointLog,
      current_game_points_a: payload.currentGamePointsA,
      current_game_points_b: payload.currentGamePointsB,
      tie_break_points_a: payload.tieBreakPointsA,
      tie_break_points_b: payload.tieBreakPointsB,
      is_tie_break: payload.isTieBreak,
      is_super_tie_break: payload.isSuperTieBreak,
      is_match_over: payload.isMatchOver,
      match_winner: payload.matchWinner,
      player_a_name: payload.playerAName,
      player_b_name: payload.playerBName,
      surface: payload.surface,
      tournament: payload.tournament,
      competition_type: payload.competitionType,
      started_at: payload.startedAt,
    }),
    cache: "no-store",
  });
  if (!response.ok) throw new Error(`Erreur inattendue (${response.status})`);
}

export type FinalizeSessionInput = {
  session: SessionDto;
  status: "COMPLETED" | "ACTIVE";
  result: "VICTORY" | "DEFEAT" | null;
  scoreText: string | null;
  updatedAt: number;
};

export async function finalizeSession(token: string, input: FinalizeSessionInput): Promise<void> {
  const response = await fetch(`${process.env.API_BASE_URL}/api/v1/sync/push`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      sessions: [
        {
          client_id: input.session.id,
          surface: input.session.surface,
          match_format: input.session.matchFormat,
          third_set_rule: input.session.thirdSetRule,
          opponent: input.session.opponent,
          competition_type: input.session.competitionType,
          tournament: input.session.tournament,
          status: input.status,
          session_type: input.session.sessionType,
          result: input.result,
          feeling_rating: null,
          feeling_comment: null,
          created_at: input.session.createdAt,
          updated_at: input.updatedAt,
          scheduled_at: null,
          score_text: input.scoreText,
          first_serve_percent_self: null,
          first_serve_percent_opponent: null,
          winners_self: null,
          winners_opponent: null,
        },
      ],
      deleted_session_ids: [],
    }),
    cache: "no-store",
  });
  if (!response.ok) throw new Error(`Erreur inattendue (${response.status})`);
}
