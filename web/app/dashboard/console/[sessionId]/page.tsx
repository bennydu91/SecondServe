import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { SESSION_COOKIE } from "@/lib/auth";
import { getSessions, getPoints, UnauthorizedError } from "@/lib/api";
import { ConsoleScreen } from "@/components/console/ConsoleScreen";

type Props = { params: Promise<{ sessionId: string }> };

export default async function ConsoleSessionPage({ params }: Props) {
  const { sessionId } = await params;
  const store = await cookies();
  const token = store.get(SESSION_COOKIE)?.value;
  if (!token) redirect("/login");

  let sessions;
  try {
    sessions = await getSessions(token);
  } catch (error) {
    if (error instanceof UnauthorizedError) redirect("/login");
    throw error;
  }

  const session = sessions.find((s) => s.id === Number(sessionId));
  if (!session || session.status !== "ACTIVE") {
    redirect("/dashboard/console");
  }

  const points = await getPoints(token, session.id);

  return <ConsoleScreen session={session} initialPoints={points} />;
}
