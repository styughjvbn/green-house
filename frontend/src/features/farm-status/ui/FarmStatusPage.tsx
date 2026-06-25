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
    <main className="space-y-5">
      <FarmStatusMap
        mapData={mapData}
        initialSelection={initialSelection}
        initialZoom={initialZoom}
      />
    </main>
  );
}
