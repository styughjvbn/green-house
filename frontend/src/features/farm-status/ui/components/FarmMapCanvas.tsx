"use client";

import dynamic from "next/dynamic";
import type {
  BedZone,
  FarmStatusZoomData,
  FarmZoomLevel,
  HouseStatusSummary,
  PhysicalBed,
} from "@/entities/farm/types";
import type { SelectedTarget } from "../../model/types";

const LeafletFarmMap = dynamic(() => import("./LeafletFarmMap"), {
  ssr: false,
  loading: () => (
    <div className="flex min-h-[560px] items-center justify-center rounded-xl border border-[#cdd9c8] bg-[#95b969] text-sm font-semibold text-white shadow-sm">
      농장 지도를 불러오는 중입니다.
    </div>
  ),
});

type FarmMapCanvasProps = {
  houses: HouseStatusSummary[];
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

export default function FarmMapCanvas(props: FarmMapCanvasProps) {
  return <LeafletFarmMap {...props} />;
}
