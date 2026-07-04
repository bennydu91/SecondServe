import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { SESSION_COOKIE } from "@/lib/auth";
import { getSessions, UnauthorizedError } from "@/lib/api";
import { HistoryView } from "@/components/history/HistoryView";

export default async function HistoryPage() {
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

  const matches = sessions
    .filter((s) => s.sessionType === "MATCH")
    .sort((a, b) => b.createdAt - a.createdAt);

  return <HistoryView matches={matches} />;
}
