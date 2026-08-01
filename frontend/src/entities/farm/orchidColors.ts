import type { OrchidGroup } from "./types";

export function getOrchidVarietyColor(group: OrchidGroup) {
  if (group.varietyColor) {
    return {
      fill: group.varietyColor,
      border: darkenHexColor(group.varietyColor),
    };
  }
  const key = group.varietyId
    ? `variety:${group.varietyId}`
    : `${group.genus ?? ""}:${group.varietyName}`;
  const hash = hashString(key);

  return ORCHID_VARIETY_PALETTE[hash % ORCHID_VARIETY_PALETTE.length];
}

export function getTextColor(
  r: number,
  g: number,
  b: number,
): "#000000" | "#FFFFFF" {
  const toLinear = (value: number) => {
    const s = value / 255;
    return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  };

  const luminance =
    0.2126 * toLinear(r) + 0.7152 * toLinear(g) + 0.0722 * toLinear(b);

  return luminance > 0.179 ? "#000000" : "#FFFFFF";
}

export function getTextColorForHex(color: string) {
  const value = Number.parseInt(color.slice(1), 16);
  return getTextColor((value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff);
}

function darkenHexColor(color: string) {
  const value = Number.parseInt(color.slice(1), 16);
  const channel = (shift: number) =>
    Math.round(((value >> shift) & 0xff) * 0.72);
  return `#${[channel(16), channel(8), channel(0)]
    .map((item) => item.toString(16).padStart(2, "0"))
    .join("")}`;
}

const ORCHID_VARIETY_PALETTE = [
  { fill: "#4f8f86", border: "#2f6f68" },
  { fill: "#2f7f77", border: "#1f625c" },
  { fill: "#9aa36b", border: "#777f4d" },
  { fill: "#76a66f", border: "#568755" },
  { fill: "#5f9b88", border: "#407a69" },
  { fill: "#3f8f6f", border: "#2a6f53" },
  { fill: "#87a760", border: "#688347" },
  { fill: "#4d7f94", border: "#365f72" },
  { fill: "#6f9c72", border: "#507b54" },
  { fill: "#3f8a84", border: "#2b6863" },
  { fill: "#8f9b58", border: "#71783e" },
  { fill: "#579b73", border: "#3d7655" },
];

function hashString(value: string) {
  let hash = 0;

  for (let index = 0; index < value.length; index += 1) {
    hash = (hash * 31 + value.charCodeAt(index)) | 0;
  }

  return Math.abs(hash);
}
