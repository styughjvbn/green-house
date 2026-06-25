import { getHouse, getOrchidManagementMap, getOrchidWorkTypes, OrchidManagementPage } from "@/features/orchid-management";

type OrchidGroupsPageProps = {
  searchParams: Promise<{
    houseId?: string;
  }>;
};

export const dynamic = "force-dynamic";

export default async function Page({ searchParams }: OrchidGroupsPageProps) {
  const params = await searchParams;
  const [mapData, workTypes] = await Promise.all([
    getOrchidManagementMap(),
    getOrchidWorkTypes(),
  ]);
  const defaultHouse = mapData.houses.find((house) => house.orchidGroupCount > 0) ?? mapData.houses[0];
  const requestedHouseId = Number(params.houseId);
  const selectedHouseId = Number.isFinite(requestedHouseId) && requestedHouseId > 0 ? requestedHouseId : defaultHouse?.houseId;
  const house = selectedHouseId ? await getHouse(selectedHouseId) : null;

  return <OrchidManagementPage mapData={mapData} house={house} workTypes={workTypes} />;
}
