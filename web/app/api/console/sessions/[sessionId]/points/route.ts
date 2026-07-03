import { NextResponse } from "next/server";
import { getSessionToken } from "@/lib/auth";
import { getPoints, postPoint, UnauthorizedError } from "@/lib/api";
import type { PointContext } from "@/lib/types";

type RouteParams = { params: Promise<{ sessionId: string }> };

export async function GET(request: Request, { params }: RouteParams) {
  const token = await getSessionToken();
  if (!token) return NextResponse.json({ error: "unauthorized" }, { status: 401 });

  const { sessionId } = await params;
  try {
    const items = await getPoints(token, Number(sessionId));
    return NextResponse.json({ items });
  } catch (error) {
    if (error instanceof UnauthorizedError) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
    throw error;
  }
}

export async function POST(request: Request, { params }: RouteParams) {
  const token = await getSessionToken();
  if (!token) return NextResponse.json({ error: "unauthorized" }, { status: 401 });

  const { sessionId } = await params;
  const body = (await request.json()) as { context: PointContext };
  try {
    const point = await postPoint(token, Number(sessionId), body.context);
    return NextResponse.json(point);
  } catch (error) {
    if (error instanceof UnauthorizedError) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
    throw error;
  }
}
