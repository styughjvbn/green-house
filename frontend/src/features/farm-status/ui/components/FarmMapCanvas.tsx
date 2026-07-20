"use client";

import dynamic from "next/dynamic";
import type {
  BedZone,
  FarmStatusZoomData,
  FarmZoomLevel,
  HouseStatusSummary,
  PhysicalBed,
} from "@/entities/farm/types";
import type {
  FarmStatusFilterMatches,
  SelectedFarmStatusOrchidGroup,
  SelectedTarget,
} from "../../model/types";

const LeafletFarmMap = dynamic(() => import("./LeafletFarmMap"), {
  ssr: false,
  loading: () => (
    <div className="flex min-h-0 flex-1 items-center justify-center border border-[#cdd9c8] bg-[#95b969] text-sm font-semibold text-white shadow-sm">
      농장 지도를 불러오는 중입니다.
    </div>
  ),
});

type FarmMapCanvasProps = {
  detailPanelOpen: boolean;
  filterMatches: FarmStatusFilterMatches;
  hasActiveSearch: boolean;
  houses: HouseStatusSummary[];
  selectedBedZoneId: number | null;
  selectedHouseId: number | null;
  selectedOrchidGroup: SelectedFarmStatusOrchidGroup | null;
  selectedPhysicalBedId: number | null;
  selectedTarget: SelectedTarget | null;
  zoomData: FarmStatusZoomData | null;
  zoomLevel: FarmZoomLevel;
  onReset: () => void;
  onSelectBedZone: (zone: BedZone) => void;
  onSelectHouse: (house: HouseStatusSummary) => void;
  onSelectOrchidGroup: (group: SelectedFarmStatusOrchidGroup) => void;
  onSelectPhysicalBed: (bed: PhysicalBed) => void;
  onZoomIn: () => void;
  onZoomOut: () => void;
};

export default function FarmMapCanvas(props: FarmMapCanvasProps) {
  return <LeafletFarmMap {...props} />;
}
