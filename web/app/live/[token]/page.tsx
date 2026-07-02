import { notFound } from "next/navigation";
import type { Metadata } from "next";
import { getLiveSnapshot, ShareExpiredError, ShareNotFoundError } from "@/lib/api";
import { LiveScoreBoard } from "@/components/LiveScoreBoard";
import { ExpiredState } from "@/components/ExpiredState";
import type { LiveSnapshot } from "@/lib/types";

type Params = { params: Promise<{ token: string }> };

function pointLabel(points: string): string {
  return { ZERO: "0", FIFTEEN: "15", THIRTY: "30", FORTY: "40", ADVANTAGE: "AD" }[points] ?? "0";
}

export async function generateMetadata({ params }: Params): Promise<Metadata> {
  const { token } = await params;
  try {
    const snapshot = await getLiveSnapshot(token);
    const a = snapshot.playerAName ?? "Joueur";
    const b = snapshot.playerBName ?? "Adversaire";
    const description =
      snapshot.status === "WAITING"
        ? `${a} vs ${b} — le match va commencer`
        : `${a} ${snapshot.currentSetGamesA}-${snapshot.currentSetGamesB} ${b} · ${pointLabel(snapshot.currentGamePointsA)}-${pointLabel(snapshot.currentGamePointsB)}`;
    return { title: `${a} vs ${b} — SecondServe`, description };
  } catch {
    return { title: "SecondServe — Suivi live" };
  }
}

export default async function LiveMatchPage({ params }: Params) {
  const { token } = await params;
  let snapshot: LiveSnapshot;
  try {
    snapshot = await getLiveSnapshot(token);
  } catch (error) {
    if (error instanceof ShareNotFoundError) notFound();
    if (error instanceof ShareExpiredError) return <ExpiredState />;
    throw error;
  }
  return <LiveScoreBoard token={token} initialSnapshot={snapshot} />;
}
