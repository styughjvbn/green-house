import type { Map as LeafletMap } from "leaflet";
import type { FarmZoomLevel } from "@/entities/farm/types";
import { zoomLabel } from "../../../lib/farmStatusView";
import { MAX_ZOOM, MIN_ZOOM } from "./config";

export function FarmMapOverlay({
  currentLevel,
  map,
  mapZoom,
  resetMap,
  distinguishVarietyColors,
  onToggleVarietyColors,
}: {
  currentLevel: FarmZoomLevel;
  map: LeafletMap | null;
  mapZoom: number;
  resetMap: () => void;
  distinguishVarietyColors: boolean;
  onToggleVarietyColors: () => void;
}) {
  const zoomIn = () => map?.setZoom(Math.min(MAX_ZOOM, map.getZoom() + 0.45));
  const zoomOut = () => map?.setZoom(Math.max(MIN_ZOOM, map.getZoom() - 0.45));

  return (
    <>
      <div className="pointer-events-none absolute top-4 left-4 z-[1000] flex flex-wrap items-center gap-2 rounded-md bg-white/95 px-3 py-2 text-sm font-semibold text-[#29422e] shadow-sm">
        <span>전체 농장 지도</span>
        <span className="rounded-full bg-[#eef6e9] px-2 py-0.5 text-xs text-[#39713d]">
          {zoomLabel(currentLevel)}
        </span>
        <span className="rounded-full bg-[#f3f6f1] px-2 py-0.5 text-xs text-[#63726a]">
          scale {mapZoom.toFixed(2)}
        </span>
      </div>

      <div className="absolute bottom-16 left-4 z-[1000] flex flex-col gap-2">
        <button
          aria-label="확대"
          className="flex h-9 w-9 items-center justify-center rounded-md bg-white text-xl font-semibold text-[#2b3a2f] shadow transition hover:bg-[#f4f7f2]"
          onClick={zoomIn}
          type="button"
        >
          +
        </button>
        <button
          aria-label="축소"
          className="flex h-9 w-9 items-center justify-center rounded-md bg-white text-xl font-semibold text-[#2b3a2f] shadow transition hover:bg-[#f4f7f2]"
          onClick={zoomOut}
          type="button"
        >
          -
        </button>
        <button
          aria-label="전체 보기"
          className="flex h-9 w-9 items-center justify-center rounded-md bg-white text-sm font-semibold text-[#2b3a2f] shadow transition hover:bg-[#f4f7f2]"
          onClick={resetMap}
          type="button"
        >
          ⌂
        </button>
      </div>

      <button
        aria-pressed={distinguishVarietyColors}
        className={`absolute top-4 right-4 z-[1000] rounded-md px-3 py-2 text-sm font-semibold shadow-sm transition ${
          distinguishVarietyColors
            ? "bg-[#2f7f77] text-white hover:bg-[#286d66]"
            : "bg-white/95 text-[#29422e] hover:bg-[#f4f7f2]"
        }`}
        onClick={onToggleVarietyColors}
        type="button"
      >
        난 묶음 색상 구별 {distinguishVarietyColors ? "끄기" : "켜기"}
      </button>

      <div className="pointer-events-none absolute bottom-4 left-16 z-[1000] flex flex-wrap gap-3 rounded-md bg-white/95 px-3 py-2 text-xs shadow">
        <LegendItem color="bg-[#20a64d]" label="정상" />
        <LegendItem color="bg-[#f59e0b]" label="주의" />
        <LegendItem color="bg-[#ef4444]" label="이상" />
        <LegendItem color="bg-[#1976f3]" label="선택" />
      </div>
    </>
  );
}

function LegendItem({ color, label }: { color: string; label: string }) {
  return (
    <span className="flex items-center gap-1.5">
      <span className={`inline-block h-2.5 w-2.5 rounded-full ${color}`} />
      {label}
    </span>
  );
}
