export const SURFACE_LABELS: Record<string, string> = {
  CLAY: "Terre battue",
  HARD: "Dur",
  GRASS: "Gazon",
  CARPET: "Indoor",
};

export const SURFACE_COLOR_VARS: Record<string, string> = {
  CLAY: "--ss-surface-clay",
  HARD: "--ss-surface-hard",
  GRASS: "--ss-surface-grass",
  CARPET: "--ss-surface-indoor",
};

export function surfaceLabel(surface: string): string {
  return SURFACE_LABELS[surface] ?? surface;
}

export function surfaceColorVar(surface: string): string {
  return SURFACE_COLOR_VARS[surface] ?? "--ss-faint";
}
