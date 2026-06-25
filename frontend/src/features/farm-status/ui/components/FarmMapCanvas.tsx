"use client";

import type {
  BedZone,
  FarmStatusZoomData,
  FarmZoomLevel,
  HouseStatusSummary,
  PhysicalBed,
} from "@/entities/farm/types";
import type { SelectedTarget } from "../../model/types";
import { zoomLabel } from "../../lib/farmStatusView";
import FarmOverviewLayer from "./FarmOverviewLayer";
import HouseDetailLayer from "./HouseDetailLayer";
import { Legend, MapBackdrop, MapControls } from "./MapChrome";

type FarmMapCanvasProps = {
  houses: HouseStatusSummary[];
  loading: boolean;
  selectedBedZoneId: number | null;
  selectedHouseId: number | null;
  selectedPhysicalBedId: number | null;
  selectedTarget: SelectedTarget | null;
  zoomData: FarmStatusZoomData | null;
  zoomLevel: FarmZoomLevel;
  onReset: () => void;
  onSelectBedZone: (zone: BedZone) => void;
  onSelectHouse: (house: HouseStatusSummary) => void;
  onSelectPhysicalBed: (bed: PhysicalBed) => void;
  onZoomIn: () => void;
  onZoomOut: () => void;
};

export default function FarmMapCanvas({
  houses,
  loading,
  selectedBedZoneId,
  selectedHouseId,
  selectedPhysicalBedId,
  selectedTarget,
  zoomData,
  zoomLevel,
  onReset,
  onSelectBedZone,
  onSelectHouse,
  onSelectPhysicalBed,
  onZoomIn,
  onZoomOut,
}: FarmMapCanvasProps) {
  return (
    <div className="relative min-h-[500px] overflow-hidden rounded-xl border border-[#cdd9c8] bg-[#95b969] p-4 shadow-sm">
      <MapBackdrop />
      <MapControls
        zoomLevel={zoomLevel}
        onReset={onReset}
        onZoomIn={onZoomIn}
        onZoomOut={onZoomOut}
      />
      <div className="absolute top-4 left-4 z-20 flex flex-wrap items-center gap-2 rounded-md bg-white/95 px-3 py-2 text-sm font-semibold text-[#29422e] shadow-sm">
        <span>
          {zoomLevel === "FARM"
            ? "전체 농장 지도"
            : `${zoomData?.houseNumber ?? ""}동 상세 지도`}
        </span>
        <span className="rounded-full bg-[#eef6e9] px-2 py-0.5 text-xs text-[#39713d]">
          {zoomLabel(zoomLevel)}
        </span>
      </div>

      <div className="relative z-10 h-full pt-24">
        {zoomLevel === "FARM" ? (
          <FarmOverviewLayer
            houses={houses}
            selectedTarget={selectedTarget}
            onSelectHouse={onSelectHouse}
          />
        ) : (
          <HouseDetailLayer
            selectedBedZoneId={selectedBedZoneId}
            selectedHouseId={selectedHouseId}
            selectedPhysicalBedId={selectedPhysicalBedId}
            zoomData={zoomData}
            zoomLevel={zoomLevel}
            onSelectBedZone={onSelectBedZone}
            onSelectPhysicalBed={onSelectPhysicalBed}
          />
        )}
      </div>

      <Legend />
      {loading ? (
        <div className="pointer-events-none absolute right-4 bottom-4 z-30 rounded-md bg-white/90 px-3 py-2 text-sm font-semibold text-[#4f6255] shadow-sm">
          불러오는 중
        </div>
      ) : null}
    </div>
  );
}
