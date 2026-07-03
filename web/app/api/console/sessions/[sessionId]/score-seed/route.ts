import { NextResponse } from "next/server";
import { getSessionToken } from "@/lib/auth";
import { putScoreSeed, UnauthorizedError } from "@/lib/api";
import type { ScoreSeed } from "@/lib/types";

type RouteParams = { params: Promise<{ sessionId: string }> };

export async function PUT(request: Request, { params }: RouteParams) {
  const token = await getSessionToken();
  if (!token) return NextResponse.json({ error: "unauthorized" }, { status: 401 });

  const { sessionId } = await params;
  const body = (await request.json()) as ScoreSeed;
  try {
    const session = await putScoreSeed(token, Number(sessionId), body);
    return NextResponse.json(session);
  } catch (error) {
    if (error instanceof UnauthorizedError) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
    throw error;
  }
}
