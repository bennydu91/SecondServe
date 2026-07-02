import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { SESSION_COOKIE } from "@/lib/auth";
import { getSessions, UnauthorizedError } from "@/lib/api";
import { computeStats, computeMonthlyWinRate, computePlayTime } from "@/lib/stats";
import { DashboardView } from "@/components/dashboard/DashboardView";

const RECENT_MATCHES_LIMIT = 30;

export default async function DashboardPage() {
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

  const stats = computeStats(sessions);
  const monthlyWinRate = computeMonthlyWinRate(sessions);
  const playTime = computePlayTime(sessions);
  const recentMatches = sessions
    .filter((s) => s.sessionType === "MATCH" && s.status === "COMPLETED")
    .slice(0, RECENT_MATCHES_LIMIT);

  return (
    <DashboardView
      stats={stats}
      monthlyWinRate={monthlyWinRate}
      playTime={playTime}
      recentMatches={recentMatches}
    />
  );
}
