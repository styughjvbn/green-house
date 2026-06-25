import {
  FarmStatusPage,
  getFarmStatusHouseZoom,
  getFarmStatusMap,
  getFarmStatusOrchidGroups,
} from "@/features/farm-status";

export const dynamic = "force-dynamic";

export default async function Page() {
  const mapData = await getFarmStatusMap();
  const firstHouse =
    mapData.houses.find((house) => house.orchidGroupCount > 0) ??
    mapData.houses[0];
  const initialSelection = firstHouse
    ? await getFarmStatusOrchidGroups("HOUSE", firstHouse.houseId)
    : null;
  const initialZoom = firstHouse
    ? await getFarmStatusHouseZoom(firstHouse.houseId)
    : null;

  return (
    <FarmStatusPage
      mapData={mapData}
      initialSelection={initialSelection}
      initialZoom={initialZoom}
    />
  );
}
