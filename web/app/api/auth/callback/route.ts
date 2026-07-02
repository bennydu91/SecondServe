import { NextResponse } from "next/server";
import { SESSION_COOKIE, SESSION_MAX_AGE_SECONDS } from "@/lib/auth";

export async function POST(request: Request) {
  const body = (await request.json()) as { credential?: string };
  if (!body.credential) {
    return NextResponse.json({ error: "missing_credential" }, { status: 400 });
  }

  const backendResponse = await fetch(`${process.env.API_BASE_URL}/api/v1/auth/init`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ google_id_token: body.credential }),
  });

  if (!backendResponse.ok) {
    return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  }

  const { token } = (await backendResponse.json()) as { token: string };
  const response = NextResponse.json({ ok: true });
  response.cookies.set(SESSION_COOKIE, token, {
    httpOnly: true,
    secure: true,
    sameSite: "lax",
    maxAge: SESSION_MAX_AGE_SECONDS,
    path: "/",
  });
  return response;
}
