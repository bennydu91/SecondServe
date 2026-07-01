# Partage de lien live — Web (Next.js) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Créer le projet Next.js `web/` qui sert la page publique `/live/[token]` : premier rendu serveur (snapshot + meta Open Graph), puis mise à jour en direct via Server-Sent Events, fidèle au mockup `design/SecondServe Public.dc.html` (variante 4B).

**Architecture:** Next.js (App Router, TypeScript). Un composant serveur récupère l'état courant auprès du backend FastAPI à la requête (pas de cache) pour le premier rendu et les meta OG ; un composant client prend le relais avec `EventSource` pour les mises à jour temps réel. Aucun état global, aucun store — le composant client gère son propre état local.

**Tech Stack:** Next.js 15 (App Router), TypeScript, CSS Modules (pas de framework CSS — fidélité pixel-perfect au mockup avec des tokens de couleur explicites), Vitest + React Testing Library pour les tests.

## Global Constraints

- Ce projet ne couvre **que** la page de suivi live — pas le mode desktop avancé (hors scope, cf. spec).
- Convention JSON du backend : `snake_case` (voir plan backend). La conversion vers des noms TypeScript `camelCase` se fait à la frontière (`lib/api.ts`), jamais ailleurs.
- **Pas de données fabriquées** : pas d'indicateur "qui sert" sur la page (absent du domaine — cf. `docs/design-system.md`, règle non négociable #7 et section GamecastTable : « Pas d'indicateur de serveur, aucune source fiable »).
- Déroulé du set : initiale du joueur qui a remporté chaque point (`B`/`M`), jamais `V`/`E`.
- Déploiement : auto-hébergé sur le VPS (systemd + Cloudflare tunnel), pas Vercel.
- Palette et polices : extraites du mockup HTML (`design/SecondServe Public.dc.html`), pas de token Android réutilisable (le design system Android est dark-only, la page publique est un thème clair distinct — cf. `docs/design-system.md` ligne 33-35).

---

### Task 1: Bootstrap du projet + design tokens

**Files:**
- Create: `web/` (projet Next.js complet via `create-next-app`)
- Create: `web/app/globals.css`
- Create: `web/lib/fonts.ts`
- Create: `web/lib/design-tokens.ts`
- Modify: `web/app/layout.tsx`

**Interfaces:**
- Produces: variables CSS `--ss-*` réutilisées par tous les composants ultérieurs ; polices `barlowSemiCondensed`/`spaceGrotesk` exportées depuis `lib/fonts.ts`.

- [ ] **Step 1: Générer le projet**

Run:
```bash
cd /root/SecondServe
npx create-next-app@latest web --typescript --app --no-tailwind --no-src-dir --eslint --import-alias "@/*" --use-npm
```
Répondre "No" à toute question sur Turbopack si posée (garder la configuration par défaut stable).

- [ ] **Step 2: Vérifier que le projet démarre**

Run: `cd web && npm run dev -- --port 3100 &` puis `curl -s http://localhost:3100 | head -5` puis arrêter le process (`kill %1`).
Expected: la commande curl renvoie du HTML (page d'accueil par défaut de Next.js).

- [ ] **Step 3: Déclarer les polices Google Fonts**

`web/lib/fonts.ts` :

```typescript
import { Barlow_Semi_Condensed, Space_Grotesk } from "next/font/google";

export const barlowSemiCondensed = Barlow_Semi_Condensed({
  subsets: ["latin"],
  weight: ["500", "600", "700", "800"],
  variable: "--font-barlow",
});

export const spaceGrotesk = Space_Grotesk({
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
  variable: "--font-space-grotesk",
});
```

- [ ] **Step 4: Déclarer les tokens de couleur (thème clair, extraits du mockup)**

`web/lib/design-tokens.ts` :

```typescript
export const colors = {
  pageBackground: "#E7E4DA",
  cardBackground: "#F4F4F1",
  panelWhite: "#FFFFFF",
  border: "#E4E5E2",
  text: "#14161A",
  muted: "#6A6F78",
  mutedLight: "#9AA0A8",
  lime: "#C8FF3D",
  live: "#E63958",
  data: "#1F6FE5",
  clay: "#C85A2C",
} as const;
```

- [ ] **Step 5: Écrire les styles globaux**

`web/app/globals.css` :

```css
* {
  box-sizing: border-box;
}

html,
body {
  margin: 0;
  padding: 0;
  background: #e7e4da;
  color: #14161a;
  font-family: var(--font-space-grotesk), sans-serif;
}

@keyframes ssPulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.2;
  }
}
```

- [ ] **Step 6: Appliquer les polices dans le layout racine**

Remplacer le contenu de `web/app/layout.tsx` :

```typescript
import type { Metadata } from "next";
import { barlowSemiCondensed, spaceGrotesk } from "@/lib/fonts";
import "./globals.css";

export const metadata: Metadata = {
  title: "SecondServe — Suivi live",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="fr" className={`${barlowSemiCondensed.variable} ${spaceGrotesk.variable}`}>
      <body>{children}</body>
    </html>
  );
}
```

- [ ] **Step 7: Vérifier que le build passe**

Run: `cd web && npm run build`
Expected: build réussi sans erreur TypeScript.

- [ ] **Step 8: Commit**

```bash
git add web
git commit -m "feat(web): bootstrap du projet Next.js et des tokens de design Broadcast (thème clair)"
```

---

### Task 2: Types, client API et composants d'affichage du score

**Files:**
- Create: `web/lib/types.ts`
- Create: `web/lib/api.ts`
- Create: `web/components/ScoreTable.tsx`
- Create: `web/components/ScoreTable.module.css`
- Create: `web/components/SetTrail.tsx`
- Create: `web/components/SetTrail.module.css`
- Test: `web/components/SetTrail.test.tsx`
- Test: `web/lib/api.test.ts`

**Interfaces:**
- Produces: type `LiveSnapshot` (camelCase, TS) ; `getLiveSnapshot(token: string): Promise<LiveSnapshot>` (lève `ShareNotFoundError` / `ShareExpiredError`) ; composants `<ScoreTable snapshot={LiveSnapshot} />` et `<SetTrail log={("A"|"B")[]} playerAInitial={string} playerBInitial={string} />`.
- Consumes: réponses JSON du backend décrites dans le plan backend (`LiveSnapshotResponse`).

- [ ] **Step 1: Déclarer les types partagés**

`web/lib/types.ts` :

```typescript
export type SetResult = { gamesA: number; gamesB: number };

export type LiveSnapshot = {
  status: "WAITING" | "LIVE" | "ENDED";
  completedSets: SetResult[];
  currentSetGamesA: number;
  currentSetGamesB: number;
  currentSetPointLog: ("A" | "B")[];
  currentGamePointsA: string;
  currentGamePointsB: string;
  tieBreakPointsA: number;
  tieBreakPointsB: number;
  isTieBreak: boolean;
  isSuperTieBreak: boolean;
  matchWinner: "A" | "B" | null;
  playerAName: string | null;
  playerBName: string | null;
  surface: string | null;
  tournament: string | null;
  competitionType: string | null;
  startedAt: number | null;
};
```

- [ ] **Step 2: Écrire le test du client API (mapping + erreurs typées)**

`web/lib/api.test.ts` :

```typescript
import { describe, expect, it, vi, afterEach } from "vitest";
import { getLiveSnapshot, ShareExpiredError, ShareNotFoundError } from "./api";

const rawSnapshot = {
  status: "LIVE",
  completed_sets: [{ games_a: 6, games_b: 4 }],
  current_set_games_a: 2,
  current_set_games_b: 1,
  current_set_point_log: ["A", "B", "A"],
  current_game_points_a: "FORTY",
  current_game_points_b: "THIRTY",
  tie_break_points_a: 0,
  tie_break_points_b: 0,
  is_tie_break: false,
  is_super_tie_break: false,
  match_winner: null,
  player_a_name: "Benjamin",
  player_b_name: "Marceau",
  surface: "CLAY",
  tournament: "Tournoi du club",
  competition_type: "CLUB",
  started_at: 1000,
};

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("getLiveSnapshot", () => {
  it("mappe la réponse snake_case du backend vers le type camelCase", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => rawSnapshot })
    );
    const snapshot = await getLiveSnapshot("abc123");
    expect(snapshot.currentSetGamesA).toBe(2);
    expect(snapshot.playerAName).toBe("Benjamin");
    expect(snapshot.completedSets).toEqual([{ gamesA: 6, gamesB: 4 }]);
    expect(snapshot.currentSetPointLog).toEqual(["A", "B", "A"]);
  });

  it("lève ShareNotFoundError sur 404", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 404, json: async () => ({}) }));
    await expect(getLiveSnapshot("unknown")).rejects.toThrow(ShareNotFoundError);
  });

  it("lève ShareExpiredError sur 410", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 410, json: async () => ({}) }));
    await expect(getLiveSnapshot("expired")).rejects.toThrow(ShareExpiredError);
  });
});
```

- [ ] **Step 3: Installer Vitest et lancer le test (doit échouer — module inexistant)**

Run:
```bash
cd web && npm install -D vitest @vitejs/plugin-react jsdom @testing-library/react @testing-library/jest-dom
```

Créer `web/vitest.config.ts` :

```typescript
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "path";

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    globals: true,
  },
  resolve: {
    alias: { "@": path.resolve(__dirname, ".") },
  },
});
```

Ajouter dans `web/package.json` (section `scripts`) : `"test": "vitest run"`.

Run: `cd web && npm run test -- lib/api.test.ts`
Expected: FAIL — `Cannot find module './api'`.

- [ ] **Step 4: Implémenter le client API**

`web/lib/api.ts` :

```typescript
import type { LiveSnapshot, SetResult } from "./types";

export class ShareNotFoundError extends Error {}
export class ShareExpiredError extends Error {}

type RawSnapshot = {
  status: "WAITING" | "LIVE" | "ENDED";
  completed_sets: { games_a: number; games_b: number }[];
  current_set_games_a: number;
  current_set_games_b: number;
  current_set_point_log: ("A" | "B")[];
  current_game_points_a: string;
  current_game_points_b: string;
  tie_break_points_a: number;
  tie_break_points_b: number;
  is_tie_break: boolean;
  is_super_tie_break: boolean;
  match_winner: "A" | "B" | null;
  player_a_name: string | null;
  player_b_name: string | null;
  surface: string | null;
  tournament: string | null;
  competition_type: string | null;
  started_at: number | null;
};

export function mapSnapshot(raw: RawSnapshot): LiveSnapshot {
  const completedSets: SetResult[] = raw.completed_sets.map((s) => ({
    gamesA: s.games_a,
    gamesB: s.games_b,
  }));
  return {
    status: raw.status,
    completedSets,
    currentSetGamesA: raw.current_set_games_a,
    currentSetGamesB: raw.current_set_games_b,
    currentSetPointLog: raw.current_set_point_log,
    currentGamePointsA: raw.current_game_points_a,
    currentGamePointsB: raw.current_game_points_b,
    tieBreakPointsA: raw.tie_break_points_a,
    tieBreakPointsB: raw.tie_break_points_b,
    isTieBreak: raw.is_tie_break,
    isSuperTieBreak: raw.is_super_tie_break,
    matchWinner: raw.match_winner,
    playerAName: raw.player_a_name,
    playerBName: raw.player_b_name,
    surface: raw.surface,
    tournament: raw.tournament,
    competitionType: raw.competition_type,
    startedAt: raw.started_at,
  };
}

export async function getLiveSnapshot(token: string): Promise<LiveSnapshot> {
  const baseUrl = typeof window === "undefined" ? process.env.API_BASE_URL : process.env.NEXT_PUBLIC_API_BASE_URL;
  const response = await fetch(`${baseUrl}/api/v1/live/${token}`, { cache: "no-store" });
  if (response.status === 404) throw new ShareNotFoundError();
  if (response.status === 410) throw new ShareExpiredError();
  if (!response.ok) throw new Error(`Erreur inattendue (${response.status})`);
  const raw = (await response.json()) as RawSnapshot;
  return mapSnapshot(raw);
}
```

- [ ] **Step 5: Lancer le test et vérifier qu'il passe**

Run: `cd web && npm run test -- lib/api.test.ts`
Expected: 3 tests PASS.

- [ ] **Step 6: Écrire le test du déroulé de set**

`web/components/SetTrail.test.tsx` :

```typescript
import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { SetTrail } from "./SetTrail";

describe("SetTrail", () => {
  it("affiche l'initiale du joueur qui a remporté chaque point", () => {
    render(<SetTrail log={["A", "A", "B"]} playerAInitial="B" playerBInitial="M" />);
    const cells = screen.getAllByTestId("set-trail-point");
    expect(cells.map((c) => c.textContent)).toEqual(["B", "B", "M"]);
  });

  it("affiche une cellule vide pour le point en cours", () => {
    render(<SetTrail log={["A"]} playerAInitial="B" playerBInitial="M" />);
    const pending = screen.getByTestId("set-trail-pending");
    expect(pending).toBeInTheDocument();
  });
});
```

- [ ] **Step 7: Lancer le test (doit échouer — composant inexistant)**

Run: `cd web && npm run test -- components/SetTrail.test.tsx`
Expected: FAIL — `Cannot find module './SetTrail'`.

- [ ] **Step 8: Implémenter `SetTrail`**

`web/components/SetTrail.module.css` :

```css
.trail {
  display: flex;
  gap: 6px;
}

.point {
  flex: 1;
  height: 30px;
  border-radius: 6px;
  background: #c8ff3d;
  color: #14161a;
  font-family: var(--font-barlow), sans-serif;
  font-weight: 800;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pending {
  flex: 1;
  height: 30px;
  border-radius: 6px;
  border: 1px dashed #cfd0cc;
  color: #9aa0a8;
}
```

`web/components/SetTrail.tsx` :

```typescript
import styles from "./SetTrail.module.css";

type Props = {
  log: ("A" | "B")[];
  playerAInitial: string;
  playerBInitial: string;
};

export function SetTrail({ log, playerAInitial, playerBInitial }: Props) {
  return (
    <div className={styles.trail}>
      {log.map((winner, index) => (
        <span key={index} className={styles.point} data-testid="set-trail-point">
          {winner === "A" ? playerAInitial : playerBInitial}
        </span>
      ))}
      <span className={styles.pending} data-testid="set-trail-pending">
        •
      </span>
    </div>
  );
}
```

- [ ] **Step 9: Lancer le test et vérifier qu'il passe**

Run: `cd web && npm run test -- components/SetTrail.test.tsx`
Expected: 2 tests PASS.

- [ ] **Step 10: Implémenter `ScoreTable` (pas de TDD ici — assemblage visuel direct, fidèle au mockup)**

`web/components/ScoreTable.module.css` :

```css
.card {
  width: 100%;
  max-width: 600px;
  background: #ffffff;
  border: 1px solid #e4e5e2;
  border-radius: 22px;
  overflow: hidden;
  box-shadow: 0 20px 50px -30px rgba(20, 22, 26, 0.3);
}

.headerRow {
  display: flex;
  align-items: center;
  padding: 14px 26px;
  font-family: var(--font-barlow), sans-serif;
  font-weight: 700;
  font-size: 12px;
  letter-spacing: 1.5px;
  color: #9aa0a8;
  border-bottom: 1px solid #ededea;
}

.playerRow {
  display: flex;
  align-items: center;
  padding: 20px 26px;
}

.playerRowLeading {
  background: rgba(200, 255, 61, 0.14);
}

.playerName {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  font-weight: 600;
  font-size: 18px;
}

.avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-barlow), sans-serif;
  font-weight: 800;
  font-size: 17px;
}

.avatarLeading {
  background: #14161a;
  color: #c8ff3d;
}

.avatarTrailing {
  background: #f4f4f1;
  border: 1px solid #e4e5e2;
  color: #6a6f78;
}

.sets {
  width: 44px;
  text-align: center;
  font-family: var(--font-barlow), sans-serif;
  font-weight: 800;
  font-size: 28px;
  font-feature-settings: "tnum";
}

.setsMuted {
  color: #9aa0a8;
}

.games {
  width: 62px;
  text-align: center;
  font-family: var(--font-barlow), sans-serif;
  font-weight: 800;
  font-size: 46px;
  color: #14161a;
  line-height: 0.9;
  font-feature-settings: "tnum";
}

.points {
  width: 58px;
  text-align: center;
  font-family: var(--font-barlow), sans-serif;
  font-weight: 800;
  font-size: 32px;
  color: #1f6fe5;
  font-feature-settings: "tnum";
}

.divider {
  height: 1px;
  background: #ededea;
}
```

`web/components/ScoreTable.tsx` :

```typescript
import styles from "./ScoreTable.module.css";
import type { LiveSnapshot } from "@/lib/types";

type Props = { snapshot: LiveSnapshot };

function pointLabel(points: string, isTieBreak: boolean, tieBreakPoints: number): string {
  if (isTieBreak) return String(tieBreakPoints);
  return { ZERO: "0", FIFTEEN: "15", THIRTY: "30", FORTY: "40", ADVANTAGE: "AD" }[points] ?? "0";
}

export function ScoreTable({ snapshot }: Props) {
  const leadingIsA =
    snapshot.currentSetGamesA > snapshot.currentSetGamesB ||
    snapshot.completedSets.filter((s) => s.gamesA > s.gamesB).length >=
      snapshot.completedSets.filter((s) => s.gamesB > s.gamesA).length;

  const rows = [
    {
      name: snapshot.playerAName ?? "Joueur",
      leading: leadingIsA,
      sets: snapshot.completedSets.map((s) => s.gamesA),
      games: snapshot.currentSetGamesA,
      points: pointLabel(snapshot.currentGamePointsA, snapshot.isTieBreak, snapshot.tieBreakPointsA),
    },
    {
      name: snapshot.playerBName ?? "Adversaire",
      leading: !leadingIsA,
      sets: snapshot.completedSets.map((s) => s.gamesB),
      games: snapshot.currentSetGamesB,
      points: pointLabel(snapshot.currentGamePointsB, snapshot.isTieBreak, snapshot.tieBreakPointsB),
    },
  ];

  return (
    <div className={styles.card}>
      <div className={styles.headerRow}>
        <span style={{ flex: 1 }}>JOUEUR</span>
        <span style={{ width: 44, textAlign: "center" }}>S1</span>
        <span style={{ width: 44, textAlign: "center" }}>S2</span>
        <span style={{ width: 62, textAlign: "center" }}>JEUX</span>
        <span style={{ width: 58, textAlign: "center" }}>POINTS</span>
      </div>
      {rows.map((row, index) => (
        <div key={row.name}>
          {index > 0 && <div className={styles.divider} />}
          <div className={`${styles.playerRow} ${row.leading ? styles.playerRowLeading : ""}`}>
            <span className={styles.playerName}>
              <span className={`${styles.avatar} ${row.leading ? styles.avatarLeading : styles.avatarTrailing}`}>
                {row.name.charAt(0).toUpperCase()}
              </span>
              {row.name}
            </span>
            {[0, 1].map((setIndex) => (
              <span key={setIndex} className={`${styles.sets} ${styles.setsMuted}`}>
                {row.sets[setIndex] ?? ""}
              </span>
            ))}
            <span className={styles.games}>{row.games}</span>
            <span className={styles.points}>{row.points}</span>
          </div>
        </div>
      ))}
    </div>
  );
}
```

- [ ] **Step 11: Vérifier que le build TypeScript passe**

Run: `cd web && npm run build`
Expected: build réussi sans erreur.

- [ ] **Step 12: Commit**

```bash
git add web
git commit -m "feat(web): types partagés, client API et composants ScoreTable/SetTrail"
```

---

### Task 3: Page publique avec SSE, meta Open Graph et états d'erreur

**Files:**
- Create: `web/hooks/useLiveSnapshot.ts`
- Create: `web/components/LiveBadge.tsx`
- Create: `web/components/LiveScoreBoard.tsx`
- Create: `web/components/LiveScoreBoard.module.css`
- Create: `web/app/live/[token]/page.tsx`
- Create: `web/app/live/[token]/not-found.tsx`
- Create: `web/components/ExpiredState.tsx`
- Test: `web/hooks/useLiveSnapshot.test.ts`

**Interfaces:**
- Consumes: `getLiveSnapshot`, `LiveSnapshot`, `ScoreTable`, `SetTrail` (Task 2).
- Produces: hook `useLiveSnapshot(token, initialSnapshot)` retournant `{ snapshot, connectionState: "live" | "reconnecting" }` ; route `/live/[token]` complète.

- [ ] **Step 1: Écrire le test du hook SSE (EventSource mocké)**

`web/hooks/useLiveSnapshot.test.ts` :

```typescript
import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useLiveSnapshot } from "./useLiveSnapshot";
import type { LiveSnapshot } from "@/lib/types";

class FakeEventSource {
  onmessage: ((event: { data: string }) => void) | null = null;
  onopen: (() => void) | null = null;
  static instances: FakeEventSource[] = [];
  constructor(public url: string) {
    FakeEventSource.instances.push(this);
  }
  close() {}
  emit(data: unknown) {
    this.onmessage?.({ data: JSON.stringify(data) });
  }
}

const initialSnapshot: LiveSnapshot = {
  status: "LIVE",
  completedSets: [],
  currentSetGamesA: 0,
  currentSetGamesB: 0,
  currentSetPointLog: [],
  currentGamePointsA: "ZERO",
  currentGamePointsB: "ZERO",
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

beforeEach(() => {
  FakeEventSource.instances = [];
  vi.stubGlobal("EventSource", FakeEventSource);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("useLiveSnapshot", () => {
  it("part du snapshot initial puis applique les événements reçus", () => {
    const { result } = renderHook(() => useLiveSnapshot("token-1", initialSnapshot));
    expect(result.current.snapshot.currentSetGamesA).toBe(0);

    act(() => {
      FakeEventSource.instances[0].emit({
        status: "LIVE",
        completed_sets: [],
        current_set_games_a: 1,
        current_set_games_b: 0,
        current_set_point_log: ["A"],
        current_game_points_a: "ZERO",
        current_game_points_b: "ZERO",
        tie_break_points_a: 0,
        tie_break_points_b: 0,
        is_tie_break: false,
        is_super_tie_break: false,
        match_winner: null,
        player_a_name: "Benjamin",
        player_b_name: "Marceau",
        surface: "CLAY",
        tournament: null,
        competition_type: null,
        started_at: 1000,
      });
    });

    expect(result.current.snapshot.currentSetGamesA).toBe(1);
    expect(result.current.snapshot.currentSetPointLog).toEqual(["A"]);
    expect(result.current.connectionState).toBe("live");
  });
});
```

- [ ] **Step 2: Lancer le test (doit échouer — hook inexistant)**

Run: `cd web && npm run test -- hooks/useLiveSnapshot.test.ts`
Expected: FAIL — `Cannot find module './useLiveSnapshot'`.

- [ ] **Step 3: Implémenter le hook**

`web/hooks/useLiveSnapshot.ts` :

```typescript
"use client";

import { useEffect, useRef, useState } from "react";
import { mapSnapshot } from "@/lib/api";
import type { LiveSnapshot } from "@/lib/types";

type ConnectionState = "live" | "reconnecting";

export function useLiveSnapshot(token: string, initialSnapshot: LiveSnapshot) {
  const [snapshot, setSnapshot] = useState<LiveSnapshot>(initialSnapshot);
  const [connectionState, setConnectionState] = useState<ConnectionState>("live");
  const lastMessageAt = useRef(Date.now());

  useEffect(() => {
    if (initialSnapshot.status === "ENDED") return;

    const source = new EventSource(`${process.env.NEXT_PUBLIC_API_BASE_URL}/api/v1/live/${token}/stream`);

    source.onmessage = (event) => {
      lastMessageAt.current = Date.now();
      setConnectionState("live");
      const raw = JSON.parse(event.data);
      const next = mapSnapshot(raw);
      setSnapshot(next);
      if (next.status === "ENDED") source.close();
    };

    const staleCheck = setInterval(() => {
      if (Date.now() - lastMessageAt.current > 15_000) setConnectionState("reconnecting");
    }, 5_000);

    return () => {
      source.close();
      clearInterval(staleCheck);
    };
  }, [token, initialSnapshot.status]);

  return { snapshot, connectionState };
}
```

- [ ] **Step 4: Lancer le test et vérifier qu'il passe**

Run: `cd web && npm run test -- hooks/useLiveSnapshot.test.ts`
Expected: 1 test PASS.

- [ ] **Step 5: Implémenter le badge LIVE/TERMINÉ**

`web/components/LiveBadge.tsx` :

```typescript
type Props = { status: "WAITING" | "LIVE" | "ENDED" };

export function LiveBadge({ status }: Props) {
  if (status === "ENDED") {
    return (
      <span
        style={{
          display: "inline-flex",
          alignItems: "center",
          gap: 7,
          background: "#14161A",
          color: "#F4F4F1",
          fontFamily: "var(--font-barlow), sans-serif",
          fontWeight: 800,
          fontSize: 12,
          letterSpacing: 1.5,
          padding: "6px 12px",
          borderRadius: 100,
        }}
      >
        TERMINÉ
      </span>
    );
  }
  return (
    <span
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: 7,
        background: "#E63958",
        color: "#fff",
        fontFamily: "var(--font-barlow), sans-serif",
        fontWeight: 800,
        fontSize: 12,
        letterSpacing: 1.5,
        padding: "6px 12px",
        borderRadius: 100,
      }}
    >
      <span
        style={{
          width: 7,
          height: 7,
          borderRadius: "50%",
          background: "#fff",
          animation: status === "LIVE" ? "ssPulse 1.4s infinite" : "none",
        }}
      />
      {status === "WAITING" ? "EN ATTENTE" : "EN DIRECT"}
    </span>
  );
}
```

- [ ] **Step 6: Implémenter `LiveScoreBoard` (assemblage — pas de TDD, composition de pièces déjà testées)**

`web/components/LiveScoreBoard.module.css` :

```css
.page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px;
}

.contextRow {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 28px;
}

.contextText {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #6a6f78;
  font-weight: 500;
}

.setTrailWrapper {
  width: 100%;
  max-width: 600px;
  margin-top: 22px;
}

.setTrailLabel {
  font-family: var(--font-barlow), sans-serif;
  font-weight: 700;
  font-size: 12px;
  letter-spacing: 2px;
  color: #9aa0a8;
  margin-bottom: 12px;
}

.reconnecting {
  margin-top: 12px;
  font-size: 12px;
  color: #9aa0a8;
}

.footer {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 24px;
  color: #9aa0a8;
  font-size: 12px;
}
```

`web/components/LiveScoreBoard.tsx` :

```typescript
"use client";

import { useLiveSnapshot } from "@/hooks/useLiveSnapshot";
import { LiveBadge } from "./LiveBadge";
import { ScoreTable } from "./ScoreTable";
import { SetTrail } from "./SetTrail";
import styles from "./LiveScoreBoard.module.css";
import type { LiveSnapshot } from "@/lib/types";

type Props = { token: string; initialSnapshot: LiveSnapshot };

const SURFACE_LABELS: Record<string, string> = {
  CLAY: "Terre battue",
  HARD: "Dur",
  GRASS: "Gazon",
  CARPET: "Moquette",
};

export function LiveScoreBoard({ token, initialSnapshot }: Props) {
  const { snapshot, connectionState } = useLiveSnapshot(token, initialSnapshot);
  const playerAInitial = (snapshot.playerAName ?? "J").charAt(0).toUpperCase();
  const playerBInitial = (snapshot.playerBName ?? "A").charAt(0).toUpperCase();

  return (
    <div className={styles.page}>
      <div className={styles.contextRow}>
        <LiveBadge status={snapshot.status} />
        <span className={styles.contextText}>
          {snapshot.surface ? (SURFACE_LABELS[snapshot.surface] ?? snapshot.surface) : ""}
          {snapshot.tournament ? ` · ${snapshot.tournament}` : ""}
        </span>
      </div>

      <ScoreTable snapshot={snapshot} />

      <div className={styles.setTrailWrapper}>
        <div className={styles.setTrailLabel}>
          DÉROULÉ · SET {snapshot.completedSets.length + 1}
        </div>
        <SetTrail log={snapshot.currentSetPointLog} playerAInitial={playerAInitial} playerBInitial={playerBInitial} />
      </div>

      {connectionState === "reconnecting" && (
        <p className={styles.reconnecting}>Reconnexion…</p>
      )}

      <div className={styles.footer}>Suivi propulsé par SecondServe · se met à jour automatiquement</div>
    </div>
  );
}
```

- [ ] **Step 7: Implémenter l'état "lien expiré"**

`web/components/ExpiredState.tsx` :

```typescript
export function ExpiredState() {
  return (
    <div style={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center", flexDirection: "column", gap: 12 }}>
      <h1 style={{ fontFamily: "var(--font-barlow), sans-serif", fontWeight: 800 }}>Ce lien n&apos;est plus disponible</h1>
      <p style={{ color: "#6A6F78" }}>Le suivi de ce match a expiré.</p>
    </div>
  );
}
```

- [ ] **Step 8: Implémenter la page serveur avec metadata OG et gestion des erreurs**

`web/app/live/[token]/page.tsx` :

```typescript
import { notFound } from "next/navigation";
import type { Metadata } from "next";
import { getLiveSnapshot, ShareExpiredError, ShareNotFoundError } from "@/lib/api";
import { LiveScoreBoard } from "@/components/LiveScoreBoard";
import { ExpiredState } from "@/components/ExpiredState";

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
  try {
    const snapshot = await getLiveSnapshot(token);
    return <LiveScoreBoard token={token} initialSnapshot={snapshot} />;
  } catch (error) {
    if (error instanceof ShareNotFoundError) notFound();
    if (error instanceof ShareExpiredError) return <ExpiredState />;
    throw error;
  }
}
```

`web/app/live/[token]/not-found.tsx` :

```typescript
export default function NotFound() {
  return (
    <div style={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center", flexDirection: "column", gap: 12 }}>
      <h1 style={{ fontFamily: "var(--font-barlow), sans-serif", fontWeight: 800 }}>Lien invalide</h1>
      <p style={{ color: "#6A6F78" }}>Ce lien de suivi n&apos;existe pas.</p>
    </div>
  );
}
```

- [ ] **Step 9: Lancer toute la suite de tests**

Run: `cd web && npm run test`
Expected: tous les tests PASS (Task 2 + Task 3).

- [ ] **Step 10: Vérifier le build**

Run: `cd web && npm run build`
Expected: build réussi, route `/live/[token]` listée en dynamique.

- [ ] **Step 11: Test manuel de bout en bout**

Avec le backend lancé localement (`cd backend && uv run uvicorn app.main:app --port 8000`, `PUBLIC_WEB_BASE_URL=http://localhost:3100`, `WEB_CORS_ORIGIN=http://localhost:3100` dans `.env`) et le web en local (`cd web && API_BASE_URL=http://localhost:8000 NEXT_PUBLIC_API_BASE_URL=http://localhost:8000 npm run dev -- --port 3100`) :

```bash
TOKEN_JWT=$(cd backend && uv run python -c "
import time, jwt
from app.core.config import settings
print(jwt.encode({'iat': int(time.time()), 'exp': int(time.time())+3600}, settings.jwt_secret, algorithm='HS256'))
")
SHARE=$(curl -s -X POST http://localhost:8000/api/v1/live/shares \
  -H "Authorization: Bearer $TOKEN_JWT" -H "Content-Type: application/json" \
  -d '{"session_id": 42}')
echo "$SHARE"
```

Ouvrir l'URL retournée (`http://localhost:3100/live/<token>`) dans un navigateur — la page doit afficher "EN ATTENTE". Puis pousser un score :

```bash
curl -X POST http://localhost:8000/api/v1/live/sessions/42/score \
  -H "Authorization: Bearer $TOKEN_JWT" -H "Content-Type: application/json" \
  -d '{"completed_sets":[],"current_set_games_a":1,"current_set_games_b":0,"current_game_points_a":"THIRTY","current_game_points_b":"ZERO","tie_break_points_a":0,"tie_break_points_b":0,"is_tie_break":false,"is_super_tie_break":false,"is_match_over":false,"match_winner":null,"player_a_name":"Benjamin","player_b_name":"Marceau","surface":"CLAY","tournament":"Tournoi du club","competition_type":"CLUB","started_at":1000}'
```
Expected: la page ouverte dans le navigateur se met à jour automatiquement sans rechargement (score "30-0", jeux "1-0").

- [ ] **Step 12: Commit**

```bash
git add web
git commit -m "feat(web): page publique /live/[token] avec SSE, meta Open Graph et états d'erreur"
```

---

### Task 4: Déploiement VPS

**Files:**
- Create: `web/DEPLOY.md`
- Create: `web/secondserve-web.service`

**Interfaces:**
- Produces: procédure de déploiement complète et fichier de service systemd, sur le modèle de `backend/DEPLOY.md`/`backend/secondserve-backend.service`.

- [ ] **Step 1: Écrire le fichier de service systemd**

`web/secondserve-web.service` :

```ini
[Unit]
Description=SecondServe Web (Next.js — page publique de suivi live)
After=network.target

[Service]
Type=simple
User=www-data
WorkingDirectory=/opt/secondserve-web
EnvironmentFile=/opt/secondserve-web/.env.production.local
ExecStart=/usr/bin/node /opt/secondserve-web/server.js
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

- [ ] **Step 2: Activer le mode `standalone` dans la config Next.js**

Modifier `web/next.config.ts` (ou créer si absent) :

```typescript
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
};

export default nextConfig;
```

- [ ] **Step 3: Écrire la documentation de déploiement**

`web/DEPLOY.md` :

```markdown
# Déploiement SecondServe Web (page publique live) sur VPS

## Prérequis

- Node.js 20+ installé sur le VPS
- Le backend `secondserve-backend` déjà déployé et accessible en local sur le VPS (`http://127.0.0.1:<PORT>`)
- Tunnel Cloudflare déjà configuré pour le backend (`api.<ton-domaine>`)

## Étapes

### 1. Build en local puis transfert du build standalone

```bash
cd web
npm run build
rsync -avz .next/standalone/ user@<vps-ip>:/opt/secondserve-web/
rsync -avz .next/static/ user@<vps-ip>:/opt/secondserve-web/.next/static/
rsync -avz public/ user@<vps-ip>:/opt/secondserve-web/public/
```

### 2. Configurer les variables d'environnement sur le VPS

`/opt/secondserve-web/.env.production.local` :

```
PORT=3000
API_BASE_URL=http://127.0.0.1:8000
NEXT_PUBLIC_API_BASE_URL=https://api.<ton-domaine>
```

> `API_BASE_URL` (sans `NEXT_PUBLIC_`) est utilisé uniquement côté serveur (composant serveur de la page) — appel direct en local sur le VPS, pas via Cloudflare, pour éviter un aller-retour réseau inutile. `NEXT_PUBLIC_API_BASE_URL` est exposé au navigateur pour la connexion SSE et doit donc pointer vers l'URL publique du backend.

### 3. Mettre à jour le backend pour autoriser cette origine en CORS

Dans `/opt/secondserve-backend/.env` : `WEB_CORS_ORIGIN=https://<ton-domaine>`, puis `sudo systemctl restart secondserve-backend`.

### 4. Configurer le service systemd

```bash
sudo cp /opt/secondserve-web/secondserve-web.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable secondserve-web
sudo systemctl start secondserve-web
sudo systemctl status secondserve-web
```

### 5. Configurer le tunnel Cloudflare

Dashboard Cloudflare → **Zero Trust → Networks → Tunnels** → **Public Hostname** :
- **Domain** : `<ton-domaine>` (apex, cohérent avec `secondserve.app/live/{token}`)
- **Service** : `http://localhost:3000`

### 6. Vérifier le déploiement

```bash
curl -s http://localhost:3000/live/does-not-exist | grep -i "Lien invalide"
curl -s https://<ton-domaine>/live/does-not-exist | grep -i "Lien invalide"
```

## Mise à jour

```bash
cd web && npm run build
rsync -avz .next/standalone/ user@<vps-ip>:/opt/secondserve-web/
rsync -avz .next/static/ user@<vps-ip>:/opt/secondserve-web/.next/static/
ssh user@<vps-ip> "sudo systemctl restart secondserve-web"
```
```

- [ ] **Step 4: Commit**

```bash
git add web/DEPLOY.md web/secondserve-web.service web/next.config.ts
git commit -m "docs(web): documenter le déploiement systemd + Cloudflare de la page publique"
```
