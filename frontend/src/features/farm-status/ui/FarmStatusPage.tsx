import type {
  FarmStatusMapData,
  FarmStatusOrchidGroupList,
  FarmStatusZoomData,
} from "@/entities/farm/types";
import { FarmStatusMap } from "./FarmStatusMap";

type FarmStatusPageProps = {
  mapData: FarmStatusMapData;
  initialSelection: FarmStatusOrchidGroupList | null;
  initialZoom: FarmStatusZoomData | null;
};

export function FarmStatusPage({
  mapData,
  initialSelection,
  initialZoom,
}: FarmStatusPageProps) {
  return (
    <main className="h-full min-h-0">
      <FarmStatusMap
        mapData={mapData}
        initialSelection={initialSelection}
        initialZoom={initialZoom}
      />
    </main>
  );
}
