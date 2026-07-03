import { NextResponse } from "next/server";
import { getSessionToken } from "@/lib/auth";
import { pushLiveScore, UnauthorizedError } from "@/lib/api";
import type { LiveScoreUpdatePayload } from "@/lib/api";

type RouteParams = { params: Promise<{ sessionId: string }> };

export async function POST(request: Request, { params }: RouteParams) {
  const token = await getSessionToken();
  if (!token) return NextResponse.json({ error: "unauthorized" }, { status: 401 });

  const { sessionId } = await params;
  const body = (await request.json()) as LiveScoreUpdatePayload;
  try {
    await pushLiveScore(token, Number(sessionId), body);
    return new NextResponse(null, { status: 204 });
  } catch (error) {
    if (error instanceof UnauthorizedError) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
    throw error;
  }
}
