import { cookies } from "next/headers";

export const SESSION_COOKIE = "ss_session";
export const SESSION_MAX_AGE_SECONDS = 60 * 60 * 24 * 30; // 30 jours — aligné sur JWTManager.create_token() côté backend

export async function getSessionToken(): Promise<string | null> {
  const store = await cookies();
  return store.get(SESSION_COOKIE)?.value ?? null;
}
