import {
  getHouse,
  getOrchidManagementMap,
  getOrchidWorkTypes,
  OrchidManagementPage,
} from "@/features/orchid-management";

type OrchidGroupsPageProps = {
  searchParams: Promise<{
    houseId?: string;
    orchidGroupId?: string;
    physicalBedId?: string;
    bedZoneId?: string;
    searchKeyword?: string;
    searchStatus?: string;
  }>;
};

export const dynamic = "force-dynamic";

export default async function Page({ searchParams }: OrchidGroupsPageProps) {
  const params = await searchParams;
  const [mapData, workTypes] = await Promise.all([
    getOrchidManagementMap(),
    getOrchidWorkTypes(),
  ]);
  const defaultHouse =
    mapData.houses.find((house) => house.orchidGroupCount > 0) ??
    mapData.houses[0];
  const requestedHouseId = Number(params.houseId);
  const requestedOrchidGroupId = Number(params.orchidGroupId);
  const requestedPhysicalBedId = Number(params.physicalBedId);
  const requestedBedZoneId = Number(params.bedZoneId);
  const selectedHouseId =
    Number.isFinite(requestedHouseId) && requestedHouseId > 0
      ? requestedHouseId
      : defaultHouse?.houseId;
  const house = selectedHouseId ? await getHouse(selectedHouseId) : null;

  return (
    <OrchidManagementPage
      initialSelectedOrchidGroupId={
        Number.isFinite(requestedOrchidGroupId) && requestedOrchidGroupId > 0
          ? requestedOrchidGroupId
          : null
      }
      initialSelectedPhysicalBedId={
        Number.isFinite(requestedPhysicalBedId) && requestedPhysicalBedId > 0
          ? requestedPhysicalBedId
          : null
      }
      initialSelectedBedZoneId={
        Number.isFinite(requestedBedZoneId) && requestedBedZoneId > 0
          ? requestedBedZoneId
          : null
      }
      initialSearchFilters={{
        keyword: params.searchKeyword ?? "",
        status: params.searchStatus ?? "",
      }}
      mapData={mapData}
      house={house}
      workTypes={workTypes}
    />
  );
}
