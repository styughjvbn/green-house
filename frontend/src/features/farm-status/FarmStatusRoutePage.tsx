import {
  getFarmStatusHouseZoom,
  getFarmStatusMap,
  getFarmStatusOrchidGroups,
} from "./api/farmStatusApi";
import { FarmStatusMap } from "./ui/FarmStatusMap";

export async function FarmStatusPage() {
  const mapData = await getFarmStatusMap();
  const firstHouse =
    mapData.houses.find((house) => house.orchidGroupCount > 0) ??
    mapData.houses[0];
  const [initialSelection, initialZoom] = firstHouse
    ? await Promise.all([
        getFarmStatusOrchidGroups("HOUSE", firstHouse.houseId),
        getFarmStatusHouseZoom(firstHouse.houseId),
      ])
    : [null, null];
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
