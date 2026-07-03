import { NextResponse } from "next/server";
import { getSessionToken } from "@/lib/auth";
import { getShareForSession, UnauthorizedError } from "@/lib/api";

type RouteParams = { params: Promise<{ sessionId: string }> };

export async function GET(request: Request, { params }: RouteParams) {
  const token = await getSessionToken();
  if (!token) return NextResponse.json({ error: "unauthorized" }, { status: 401 });

  const { sessionId } = await params;
  try {
    const share = await getShareForSession(token, Number(sessionId));
    return NextResponse.json(share);
  } catch (error) {
    if (error instanceof UnauthorizedError) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
    throw error;
  }
}
