import { NextResponse } from "next/server";
import { getSessionToken } from "@/lib/auth";
import { finalizeSession } from "@/lib/api";
import type { SessionDto } from "@/lib/types";

type RouteParams = { params: Promise<{ sessionId: string }> };
type FinalizeBody = {
  session: SessionDto;
  status: "COMPLETED" | "ACTIVE";
  result: "VICTORY" | "DEFEAT" | null;
  scoreText: string | null;
  updatedAt: number;
};

export async function POST(request: Request, { params }: RouteParams) {
  const token = await getSessionToken();
  if (!token) return NextResponse.json({ error: "unauthorized" }, { status: 401 });

  await params; // sessionId déjà présent dans body.session.id — conservé pour cohérence de route
  const body = (await request.json()) as FinalizeBody;
  await finalizeSession(token, body);
  return new NextResponse(null, { status: 204 });
}
