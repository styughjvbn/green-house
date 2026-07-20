import type { Map as LeafletMap } from "leaflet";
import { getOrchidVarietyColor } from "@/entities/farm/orchidColors";
import type { FarmZoomLevel, HouseStatusSummary } from "@/entities/farm/types";
import type {
  FarmStatusColorMode,
  FarmStatusLayoutMode,
} from "../../../model/types";
import { zoomLabel } from "../../../lib/farmStatusView";
import { MAX_ZOOM, MIN_ZOOM } from "./config";

export function FarmMapOverlay({
  currentLevel,
  colorMode,
  houses,
  layoutMode,
  map,
  mapZoom,
  resetMap,
  onColorModeChange,
  onLayoutModeChange,
}: {
  currentLevel: FarmZoomLevel;
  colorMode: FarmStatusColorMode;
  houses: HouseStatusSummary[];
  layoutMode: FarmStatusLayoutMode;
  map: LeafletMap | null;
  mapZoom: number;
  resetMap: () => void;
  onColorModeChange: (mode: FarmStatusColorMode) => void;
  onLayoutModeChange: (mode: FarmStatusLayoutMode) => void;
}) {
  const zoomIn = () => map?.setZoom(Math.min(MAX_ZOOM, map.getZoom() + 0.45));
  const zoomOut = () => map?.setZoom(Math.max(MIN_ZOOM, map.getZoom() - 0.45));
  const legendItems = getColorLegendItems(colorMode, houses);

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

      <div className="absolute top-4 right-4 z-[1000] flex flex-col items-end gap-2">
        <ToggleGroup
          label="보기"
          options={[
            ["NORMALIZED", "정규화 배치"],
            ["ACTUAL", "실제 배치"],
          ]}
          value={layoutMode}
          onChange={(value) =>
            onLayoutModeChange(value as FarmStatusLayoutMode)
          }
        />
        <ToggleGroup
          label="색상"
          options={[
            ["STATUS", "상태"],
            ["VARIETY", "품종"],
            ["AGE", "년생"],
          ]}
          value={colorMode}
          onChange={(value) => onColorModeChange(value as FarmStatusColorMode)}
        />
        {layoutMode === "NORMALIZED" ? (
          <span className="rounded bg-[#fff8e7]/95 px-2 py-1 text-[11px] font-semibold text-[#865b00] shadow">
            비교용 배치 · 실제 크기 아님
          </span>
        ) : null}
      </div>

      <div className="pointer-events-none absolute bottom-4 left-4 z-[1000] flex max-w-[calc(100%-5rem)] flex-wrap items-center gap-3 rounded-md bg-white/95 px-3 py-2 text-xs shadow">
        <span className="font-semibold text-[#63726a]">범례</span>
        {legendItems.map((item) => (
          <LegendItem key={item.label} color={item.color} label={item.label} />
        ))}
        <span className="h-4 w-px bg-[#dfe5dc]" />
        <LegendItem color="#1976f3" label="선택" outline />
      </div>
    </>
  );
}

function ToggleGroup({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: Array<[string, string]>;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <div className="flex items-center rounded-md bg-white/95 p-1 text-xs shadow">
      <span className="px-2 font-semibold text-[#63726a]">{label}</span>
      {options.map(([option, text]) => (
        <button
          key={option}
          className={`rounded px-2 py-1 font-semibold ${
            value === option
              ? "bg-[#256ff0] text-white"
              : "text-[#34503b] hover:bg-[#eef3ec]"
          }`}
          onClick={() => onChange(option)}
          type="button"
        >
          {text}
        </button>
      ))}
    </div>
  );
}

function LegendItem({
  color,
  label,
  outline = false,
}: {
  color: string;
  label: string;
  outline?: boolean;
}) {
  return (
    <span className="flex items-center gap-1.5">
      <span
        className="inline-block h-2.5 w-2.5 rounded-full"
        style={
          outline
            ? { border: `2px solid ${color}`, background: "white" }
            : { background: color }
        }
      />
      {label}
    </span>
  );
}

function getColorLegendItems(
  colorMode: FarmStatusColorMode,
  houses: HouseStatusSummary[],
) {
  if (colorMode === "STATUS") {
    return [
      { color: "#16853b", label: "정상" },
      { color: "#d97706", label: "주의" },
      { color: "#dc2626", label: "이상·병해충" },
    ];
  }

  if (colorMode === "AGE") {
    return [
      { color: "#c4b5fd", label: "년생 미지정" },
      { color: "#a78bfa", label: "1~2년생" },
      { color: "#8b5cf6", label: "3~4년생" },
      { color: "#6d28d9", label: "5년생 이상" },
    ];
  }

  const groups = houses.flatMap((house) =>
    house.physicalBeds.flatMap((bed) =>
      bed.bedZones.flatMap((zone) => zone.orchidGroups),
    ),
  );
  const uniqueGroups = new Map<string, (typeof groups)[number]>();
  groups.forEach((group) => {
    const key = String(group.varietyId ?? group.varietyName);
    if (!uniqueGroups.has(key)) {
      uniqueGroups.set(key, group);
    }
  });
  const varieties = [...uniqueGroups.values()];
  const visibleVarieties = varieties.slice(0, 6).map((group) => ({
    color: getOrchidVarietyColor(group).fill,
    label: group.varietyName,
  }));
  if (varieties.length > visibleVarieties.length) {
    visibleVarieties.push({
      color: "#94a3b8",
      label: `외 ${varieties.length - visibleVarieties.length}개`,
    });
  }
  return visibleVarieties.length > 0
    ? visibleVarieties
    : [{ color: "#94a3b8", label: "품종 없음" }];
}
