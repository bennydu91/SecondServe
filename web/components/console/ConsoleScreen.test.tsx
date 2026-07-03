import { describe, expect, it, vi, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import type { SessionDto, PointDto } from "@/lib/types";
import { ConsoleScreen } from "./ConsoleScreen";

function session(overrides: Partial<SessionDto> = {}): SessionDto {
  return {
    id: 7,
    surface: "CLAY",
    matchFormat: "BEST_OF_1",
    thirdSetRule: "FULL_ADVANTAGE",
    opponent: "Marceau",
    competitionType: null,
    tournament: null,
    status: "ACTIVE",
    sessionType: "MATCH",
    result: null,
    scoreText: null,
    scoreSeedJson: null,
    createdAt: 1000,
    updatedAt: 1000,
    ...overrides,
  };
}

function jsonResponse(body: unknown, ok = true) {
  return Promise.resolve({ ok, json: async () => body });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("ConsoleScreen", () => {
  it("reconstruit l'état à partir des points initiaux (rechargement de page)", () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(null)));
    const initialPoints: PointDto[] = [
      { id: 1, sessionId: 7, scorer: "A", context: "ACE", sequenceNum: 1, recordedAt: 1000 },
    ];
    render(<ConsoleScreen session={session()} initialPoints={initialPoints} />);
    expect(screen.getByText("15")).toBeInTheDocument();
  });

  it("un clic sur un bouton de point poste le point et met à jour le score", async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url.endsWith("/share")) return jsonResponse(null);
      if (url.endsWith("/points") && init?.method === "POST") {
        return jsonResponse({ id: 1, sessionId: 7, scorer: "A", context: "ACE", sequenceNum: 1, recordedAt: 1000 });
      }
      return jsonResponse(null);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<ConsoleScreen session={session()} initialPoints={[]} />);
    fireEvent.click(screen.getByRole("button", { name: "Ace" }));

    await waitFor(() => expect(screen.getByText("15")).toBeInTheDocument());
  });

  it("Annuler retire le dernier point et restaure le score précédent", async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url.endsWith("/share")) return jsonResponse(null);
      if (url.endsWith("/points/last") && init?.method === "DELETE") return jsonResponse(null, true);
      return jsonResponse(null);
    });
    vi.stubGlobal("fetch", fetchMock);

    const initialPoints: PointDto[] = [
      { id: 1, sessionId: 7, scorer: "A", context: "ACE", sequenceNum: 1, recordedAt: 1000 },
    ];
    render(<ConsoleScreen session={session()} initialPoints={initialPoints} />);
    expect(screen.getByText("15")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /annuler le dernier point/i }));
    // "0" seul est ambigu ici (jeux, points de l'adversaire et tuiles de stats affichent
    // aussi "0") : on vérifie plutôt que le score précédent ("15") a bien disparu.
    await waitFor(() => expect(screen.queryByText("15")).not.toBeInTheDocument());
  });

  it("affiche une erreur si l'enregistrement du point échoue", async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url.endsWith("/share")) return jsonResponse(null);
      if (url.endsWith("/points") && init?.method === "POST") return jsonResponse(null, false);
      return jsonResponse(null);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<ConsoleScreen session={session()} initialPoints={[]} />);
    fireEvent.click(screen.getByRole("button", { name: "Ace" }));
    await waitFor(() => expect(screen.getByText(/échec de l'enregistrement/i)).toBeInTheDocument());
  });

  it("désactive les boutons de point et affiche la bannière une fois le match terminé", async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url.endsWith("/share")) return jsonResponse(null);
      if (url.endsWith("/points") && init?.method === "POST") {
        return jsonResponse({ id: 1, sessionId: 7, scorer: "A", context: "ACE", sequenceNum: 1, recordedAt: 1000 });
      }
      return jsonResponse(null, true);
    });
    vi.stubGlobal("fetch", fetchMock);

    // BEST_OF_1, déjà à 5-0 jeux et 40-0 : le prochain point gagne le match.
    const seed = JSON.stringify({
      completed_sets: [],
      current_set_games_a: 5,
      current_set_games_b: 0,
      current_game_points_a: "FORTY",
      current_game_points_b: "ZERO",
      tie_break_points_a: 0,
      tie_break_points_b: 0,
      is_tie_break: false,
      is_super_tie_break: false,
    });
    render(<ConsoleScreen session={session({ scoreSeedJson: seed })} initialPoints={[]} />);
    fireEvent.click(screen.getByRole("button", { name: "Ace" }));

    await waitFor(() => expect(screen.getByText(/match terminé/i)).toBeInTheDocument());
    expect(screen.getByRole("button", { name: "Ace" })).toBeDisabled();
  });

  it("affiche un message distinct (pas 'échec de l'enregistrement') si la clôture automatique échoue après un point gagnant", async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url.endsWith("/share")) return jsonResponse(null);
      if (url.endsWith("/points") && init?.method === "POST") {
        return jsonResponse({ id: 1, sessionId: 7, scorer: "A", context: "ACE", sequenceNum: 1, recordedAt: 1000 });
      }
      if (url.endsWith("/finalize") && init?.method === "POST") return jsonResponse(null, false);
      return jsonResponse(null, true);
    });
    vi.stubGlobal("fetch", fetchMock);

    // BEST_OF_1, déjà à 5-0 jeux et 40-0 : le prochain point gagne le match, donc déclenche finalize().
    const seed = JSON.stringify({
      completed_sets: [],
      current_set_games_a: 5,
      current_set_games_b: 0,
      current_game_points_a: "FORTY",
      current_game_points_b: "ZERO",
      tie_break_points_a: 0,
      tie_break_points_b: 0,
      is_tie_break: false,
      is_super_tie_break: false,
    });
    render(<ConsoleScreen session={session({ scoreSeedJson: seed })} initialPoints={[]} />);
    fireEvent.click(screen.getByRole("button", { name: "Ace" }));

    // Le point a bien été enregistré : le match apparaît terminé côté client...
    await waitFor(() => expect(screen.getByText(/match terminé/i)).toBeInTheDocument());
    // ...mais un message distinct signale l'échec de la clôture automatique...
    expect(screen.getByText(/clôture automatique a échoué/i)).toBeInTheDocument();
    // ...et surtout PAS le message générique d'échec d'enregistrement du point (qui serait trompeur :
    // le point a bel et bien été enregistré).
    expect(screen.queryByText(/échec de l'enregistrement/i)).not.toBeInTheDocument();
  });
});
