export const FONT_SCALE_STORAGE_KEY = "greenhouse-font-scale";

export const FONT_SCALE_OPTIONS = [
  { label: "작게", value: 0.9 },
  { label: "기본", value: 1 },
  { label: "크게", value: 1.1 },
  { label: "아주 크게", value: 1.2 },
] as const;

export const DEFAULT_FONT_SCALE = 1;
export const MIN_FONT_SCALE = 0.85;
export const MAX_FONT_SCALE = 1.25;

export function normalizeFontScale(value: number) {
  if (!Number.isFinite(value)) {
    return DEFAULT_FONT_SCALE;
  }
  return Math.min(MAX_FONT_SCALE, Math.max(MIN_FONT_SCALE, value));
}

export function formatFontScale(value: number) {
  return `${Math.round(value * 100)}%`;
}
