import { divIcon, type LeafletMouseEvent } from "leaflet";
import type { OrchidGroup } from "@/entities/farm/types";

export function stopLeafletClick(event: LeafletMouseEvent) {
  event.originalEvent.preventDefault();
  event.originalEvent.stopPropagation();
}

export function createLabelIcon({
  background,
  text,
  width,
}: {
  background: string;
  text: string;
  width: number;
}) {
  return divIcon({
    className: "",
    html: `<div style="width:${width}px;border-radius:8px;background:${background};color:white;font-size:15px;font-weight:800;line-height:24px;text-align:center;box-shadow:0 2px 6px rgba(20,50,20,.25);">${escapeHtml(text)}</div>`,
    iconAnchor: [width / 2, 12],
    iconSize: [width, 24],
  });
}

export function createSmallLabelIcon({
  background,
  text,
  width,
}: {
  background: string;
  text: string;
  width: number;
}) {
  return divIcon({
    className: "",
    html: `<div style="width:${width}px;border-radius:6px;background:${background};color:white;font-size:10px;font-weight:800;line-height:18px;text-align:center;box-shadow:0 1px 4px rgba(20,50,20,.25);">${escapeHtml(text)}</div>`,
    iconAnchor: [width / 2, 9],
    iconSize: [width, 18],
  });
}

export function createZoneLabelIcon({
  selected,
  text,
}: {
  selected: boolean;
  text: string;
}) {
  const background = selected ? "#246df2" : "#2f7a43";

  return divIcon({
    className: "",
    html: `<div style="width:38px;border-radius:999px;background:${background};color:white;font-size:9px;font-weight:800;line-height:16px;text-align:center;box-shadow:0 1px 4px rgba(20,50,20,.22);">${escapeHtml(text)}</div>`,
    iconAnchor: [19, 8],
    iconSize: [38, 16],
  });
}

export function createTinyBadgeIcon(text: string) {
  return divIcon({
    className: "",
    html: `<div style="border-radius:999px;background:rgba(255,255,255,.92);color:#1e5f32;font-size:10px;font-weight:800;line-height:16px;padding:0 6px;box-shadow:0 1px 4px rgba(20,50,20,.18);white-space:nowrap;">${escapeHtml(text)}</div>`,
    iconAnchor: [18, 8],
    iconSize: [36, 16],
  });
}

export function createBottomMetricIcon({
  text,
  warning,
}: {
  status: string;
  text: string;
  warning: boolean;
}) {
  const dot = warning ? "#f59e0b" : "#20a64d";

  return divIcon({
    className: "",
    html: `<div style="display:flex;align-items:center;gap:4px;border-radius:999px;background:rgba(255,255,255,.94);color:#2f3d34;font-size:10px;font-weight:800;line-height:18px;padding:0 7px;box-shadow:0 1px 4px rgba(20,50,20,.18);white-space:nowrap;"><span style="width:6px;height:6px;border-radius:999px;background:${dot};display:inline-block;"></span>${escapeHtml(text)}</div>`,
    iconAnchor: [36, 9],
    iconSize: [72, 18],
  });
}

export function createOrchidBlockIcon(group: OrchidGroup, matched: boolean) {
  const bg = matched
    ? group.status === "정상"
      ? "#1f8f48"
      : "#f59e0b"
    : "#9aa19a";
  const title =
    group.varietyName.length > 7
      ? `${group.varietyName.slice(0, 7)}...`
      : group.varietyName;

  return divIcon({
    className: "",
    html: `<div style="width:96px;height:32px;border-radius:7px;background:${bg};color:white;padding:4px 7px;box-shadow:0 2px 6px rgba(20,50,20,.22);line-height:1.15;box-sizing:border-box;"><div style="font-size:11px;font-weight:800;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escapeHtml(title)}</div><div style="font-size:10px;font-weight:700;opacity:.95;">${group.quantity}분</div></div>`,
    iconAnchor: [48, 16],
    iconSize: [96, 32],
  });
}

function escapeHtml(value: string) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}
