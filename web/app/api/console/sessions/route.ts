import { NextResponse } from "next/server";
import { getSessionToken } from "@/lib/auth";
import { createSession, UnauthorizedError } from "@/lib/api";

export async function POST(request: Request) {
  const token = await getSessionToken();
  if (!token) return NextResponse.json({ error: "unauthorized" }, { status: 401 });

  const body = await request.json();
  try {
    const session = await createSession(token, {
      surface: body.surface,
      matchFormat: body.matchFormat,
      thirdSetRule: body.thirdSetRule,
      opponent: body.opponent,
      competitionType: body.competitionType,
      tournament: body.tournament,
      createdAt: body.createdAt,
    });
    return NextResponse.json(session);
  } catch (error) {
    if (error instanceof UnauthorizedError) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
    throw error;
  }
}
