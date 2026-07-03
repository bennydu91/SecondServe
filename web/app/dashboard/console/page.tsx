import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { SESSION_COOKIE } from "@/lib/auth";
import { getSessions, UnauthorizedError } from "@/lib/api";
import { ConsoleSelectionView } from "@/components/console/ConsoleSelectionView";

export default async function ConsolePage() {
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

  const activeSessions = sessions.filter((s) => s.status === "ACTIVE");

  return <ConsoleSelectionView activeSessions={activeSessions} />;
}
