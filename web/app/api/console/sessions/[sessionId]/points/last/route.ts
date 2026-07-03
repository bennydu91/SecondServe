import { NextResponse } from "next/server";
import { getSessionToken } from "@/lib/auth";
import { deleteLastPoint, UnauthorizedError } from "@/lib/api";

type RouteParams = { params: Promise<{ sessionId: string }> };

export async function DELETE(request: Request, { params }: RouteParams) {
  const token = await getSessionToken();
  if (!token) return NextResponse.json({ error: "unauthorized" }, { status: 401 });

  const { sessionId } = await params;
  try {
    await deleteLastPoint(token, Number(sessionId));
    return new NextResponse(null, { status: 204 });
  } catch (error) {
    if (error instanceof UnauthorizedError) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
    throw error;
  }
}
