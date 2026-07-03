import { describe, expect, it, vi, afterEach } from "vitest";
import { render, screen } from "@testing-library/react";

vi.mock("@/lib/api", () => ({
  getLiveSnapshot: vi.fn(),
  ShareNotFoundError: class ShareNotFoundError extends Error {},
  ShareExpiredError: class ShareExpiredError extends Error {},
}));
vi.mock("@/components/LiveScoreBoard", () => ({
  LiveScoreBoard: ({ token }: { token: string }) => <div>LiveScoreBoard-{token}</div>,
}));

import { getLiveSnapshot, ShareNotFoundError, ShareExpiredError } from "@/lib/api";
import LiveMatchPage, { generateMetadata } from "./page";
import type { LiveSnapshot } from "@/lib/types";

afterEach(() => {
  vi.clearAllMocks();
});

function params(token: string) {
  return { params: Promise.resolve({ token }) };
}

const baseSnapshot: LiveSnapshot = {
  status: "LIVE",
  completedSets: [],
  currentSetGamesA: 3,
  currentSetGamesB: 2,
  currentSetPointLog: [],
  currentGamePointsA: "THIRTY",
  currentGamePointsB: "FIFTEEN",
  tieBreakPointsA: 0,
  tieBreakPointsB: 0,
  isTieBreak: false,
  isSuperTieBreak: false,
  matchWinner: null,
  playerAName: "Benjamin",
  playerBName: "Marceau",
  surface: "CLAY",
  tournament: null,
  competitionType: null,
  startedAt: 1000,
};

describe("LiveMatchPage", () => {
  it("appelle notFound() si le token est inconnu (ShareNotFoundError)", async () => {
    vi.mocked(getLiveSnapshot).mockRejectedValue(new ShareNotFoundError());

    await expect(LiveMatchPage(params("unknown"))).rejects.toMatchObject({
      digest: expect.stringContaining("NEXT_HTTP_ERROR_FALLBACK;404"),
    });
  });

  it("affiche ExpiredState si le partage a expiré (ShareExpiredError)", async () => {
    vi.mocked(getLiveSnapshot).mockRejectedValue(new ShareExpiredError());

    const element = await LiveMatchPage(params("expired"));
    render(element);
    expect(screen.getByText("Ce lien n'est plus disponible")).toBeInTheDocument();
  });

  it("relance les erreurs inattendues", async () => {
    vi.mocked(getLiveSnapshot).mockRejectedValue(new Error("boom"));
    await expect(LiveMatchPage(params("x"))).rejects.toThrow("boom");
  });

  it("affiche le tableau de score live quand le snapshot est disponible", async () => {
    vi.mocked(getLiveSnapshot).mockResolvedValue(baseSnapshot);

    const element = await LiveMatchPage(params("share-token"));
    render(element);
    expect(screen.getByText("LiveScoreBoard-share-token")).toBeInTheDocument();
  });
});

describe("generateMetadata", () => {
  it("indique que le match va commencer quand le statut est WAITING", async () => {
    vi.mocked(getLiveSnapshot).mockResolvedValue({ ...baseSnapshot, status: "WAITING" });
    const metadata = await generateMetadata(params("share-token"));
    expect(metadata.title).toBe("Benjamin vs Marceau — SecondServe");
    expect(metadata.description).toContain("le match va commencer");
  });

  it("affiche le score courant quand le match est en cours", async () => {
    vi.mocked(getLiveSnapshot).mockResolvedValue(baseSnapshot);
    const metadata = await generateMetadata(params("share-token"));
    expect(metadata.description).toContain("3-2");
    expect(metadata.description).toContain("30-15");
  });

  it("retombe sur un titre générique si le snapshot est inaccessible", async () => {
    vi.mocked(getLiveSnapshot).mockRejectedValue(new Error("network"));
    const metadata = await generateMetadata(params("bad-token"));
    expect(metadata.title).toBe("SecondServe — Suivi live");
  });
});
