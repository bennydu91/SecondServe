import { NextResponse } from "next/server";
import { getSessionToken } from "@/lib/auth";
import { updateSession, deleteSession, UnauthorizedError } from "@/lib/api";
import type { UpdateSessionInput } from "@/lib/api";

type RouteParams = { params: Promise<{ sessionId: string }> };

export async function PATCH(request: Request, { params }: RouteParams) {
  const token = await getSessionToken();
  if (!token) return NextResponse.json({ error: "unauthorized" }, { status: 401 });

  const { sessionId } = await params;
  const body = (await request.json()) as UpdateSessionInput;
  try {
    const session = await updateSession(token, Number(sessionId), body);
    return NextResponse.json(session);
  } catch (error) {
    if (error instanceof UnauthorizedError) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
    throw error;
  }
}

export async function DELETE(_request: Request, { params }: RouteParams) {
  const token = await getSessionToken();
  if (!token) return NextResponse.json({ error: "unauthorized" }, { status: 401 });

  const { sessionId } = await params;
  try {
    await deleteSession(token, Number(sessionId));
    return new NextResponse(null, { status: 204 });
  } catch (error) {
    if (error instanceof UnauthorizedError) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
    throw error;
  }
}
