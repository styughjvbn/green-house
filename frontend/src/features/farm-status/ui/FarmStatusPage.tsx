import type {
  DashboardSummary,
  FarmStatusMapData,
  FarmStatusOrchidGroupList,
  FarmStatusZoomData,
} from "@/entities/farm/types";
import { FarmStatusMap } from "./FarmStatusMap";

type FarmStatusPageProps = {
  summary: DashboardSummary;
  mapData: FarmStatusMapData;
  initialSelection: FarmStatusOrchidGroupList | null;
  initialZoom: FarmStatusZoomData | null;
};

export function FarmStatusPage({
  summary,
  mapData,
  initialSelection,
  initialZoom,
}: FarmStatusPageProps) {
  return (
    <main className="space-y-5">
      <FarmStatusMap
        mapData={mapData}
        initialSelection={initialSelection}
        initialZoom={initialZoom}
      />
    </main>
  );
}