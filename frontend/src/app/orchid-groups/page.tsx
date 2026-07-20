import {
  getOrchidManagementMap,
  getOrchidManagementViewport,
  getOrchidWorkTypes,
  OrchidManagementPage,
} from "@/features/orchid-management";
import type { VisibleBedCount } from "@/entities/farm/types";

type OrchidGroupsPageProps = {
  searchParams: Promise<{
    startBedId?: string;
    bedCount?: string;
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
  const requestedOrchidGroupId = Number(params.orchidGroupId);
  const requestedPhysicalBedId = Number(params.physicalBedId);
  const requestedBedZoneId = Number(params.bedZoneId);
  const allBeds = mapData.houses.flatMap((house) => house.physicalBeds);
  const requestedStartBedId = Number(params.startBedId);
  const requestedBedCount = Number(params.bedCount);
  const bedCount: VisibleBedCount =
    requestedBedCount === 2 || requestedBedCount === 4 ? requestedBedCount : 3;
  const deepLinkedBed = allBeds.find(
    (bed) =>
      bed.id === requestedPhysicalBedId ||
      bed.bedZones.some(
        (zone) =>
          zone.id === requestedBedZoneId ||
          zone.orchidGroups.some(
            (orchidGroup) => orchidGroup.id === requestedOrchidGroupId,
          ),
      ),
  );
  const startBedId =
    Number.isFinite(requestedStartBedId) && requestedStartBedId > 0
      ? requestedStartBedId
      : (deepLinkedBed?.id ?? allBeds[0]?.id ?? null);
  const viewport = await getOrchidManagementViewport(startBedId, bedCount);
  const house = defaultHouse
    ? {
        id: defaultHouse.houseId,
        number: defaultHouse.houseNumber,
        name: "전체 농장",
        memo: null,
        physicalBeds: allBeds,
      }
    : null;

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
      initialStartBedId={viewport.startBedId}
      initialVisibleBedCount={viewport.bedCount}
      workTypes={workTypes}
    />
  );
}
