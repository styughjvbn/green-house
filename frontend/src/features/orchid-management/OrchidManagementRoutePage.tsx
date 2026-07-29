import type {
  FarmStatusMapData,
  House,
  VisibleBedCount,
  WorkType,
} from "@/entities/farm/types";
import {
  getOrchidManagementMap,
  getOrchidManagementViewport,
  getOrchidWorkTypes,
} from "./api/orchidManagementApi";
import type { OrchidManagementSearchState } from "./model/types";
import { OrchidManagementMap } from "./ui/OrchidManagementMap";

type OrchidManagementPageProps = {
  mapData: FarmStatusMapData;
  house: House | null;
  initialStartBedId: number | null;
  initialVisibleBedCount: VisibleBedCount;
  initialSelectedOrchidGroupId: number | null;
  initialSelectedPhysicalBedId?: number | null;
  initialSelectedBedZoneId?: number | null;
  initialSearchFilters?: OrchidManagementSearchState;
  workTypes: WorkType[];
};

export async function OrchidManagementRoutePage({
  resolvedSearchParams,
}: {
  resolvedSearchParams: Record<string, string | string[] | undefined>;
}) {
  const routeState = readOrchidManagementRouteState(resolvedSearchParams);
  const [mapData, workTypes] = await Promise.all([
    getOrchidManagementMap(),
    getOrchidWorkTypes(),
  ]);
  const defaultHouse =
    mapData.houses.find((house) => house.orchidGroupCount > 0) ??
    mapData.houses[0];
  const allBeds = mapData.houses.flatMap((house) => house.physicalBeds);
  const deepLinkedBed = allBeds.find(
    (bed) =>
      bed.id === routeState.selectedPhysicalBedId ||
      bed.bedZones.some(
        (zone) =>
          zone.id === routeState.selectedBedZoneId ||
          zone.orchidGroups.some(
            (orchidGroup) =>
              orchidGroup.id === routeState.selectedOrchidGroupId,
          ),
      ),
  );
  const startBedId =
    routeState.startBedId ?? deepLinkedBed?.id ?? allBeds[0]?.id ?? null;
  const viewport = await getOrchidManagementViewport(
    startBedId,
    routeState.bedCount,
  );
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
      initialSearchFilters={routeState.searchFilters}
      initialSelectedBedZoneId={routeState.selectedBedZoneId}
      initialSelectedOrchidGroupId={routeState.selectedOrchidGroupId}
      initialSelectedPhysicalBedId={routeState.selectedPhysicalBedId}
      initialStartBedId={viewport.startBedId}
      initialVisibleBedCount={viewport.bedCount}
      mapData={mapData}
      house={house}
      workTypes={workTypes}
    />
  );
}

function OrchidManagementPage({
  mapData,
  house,
  initialStartBedId,
  initialVisibleBedCount,
  initialSelectedOrchidGroupId,
  initialSelectedPhysicalBedId,
  initialSelectedBedZoneId,
  initialSearchFilters,
  workTypes,
}: OrchidManagementPageProps) {
  if (!house) {
    return (
      <main className="space-y-4">
        <div className="rounded-md border border-[#d7ddd4] bg-white p-5 text-sm text-[#5c6a60]">
          표시할 데이터가 없습니다.
        </div>
      </main>
    );
  }

  return (
    <main className="h-full min-h-0">
      <OrchidManagementMap
        key={[
          house.id,
          initialSelectedOrchidGroupId ?? "group-default",
          initialSelectedPhysicalBedId ?? "bed-default",
          initialSelectedBedZoneId ?? "zone-default",
        ].join("-")}
        initialSelectedBedZoneId={initialSelectedBedZoneId}
        initialSelectedPhysicalBedId={initialSelectedPhysicalBedId}
        initialSearchFilters={initialSearchFilters}
        mapData={mapData}
        house={house}
        initialStartBedId={initialStartBedId}
        initialVisibleBedCount={initialVisibleBedCount}
        initialSelectedOrchidGroupId={initialSelectedOrchidGroupId}
        workTypes={workTypes}
      />
    </main>
  );
}

function readOrchidManagementRouteState(
  resolvedSearchParams: Record<string, string | string[] | undefined>,
) {
  return {
    bedCount: readVisibleBedCount(resolvedSearchParams.bedCount),
    searchFilters: {
      keyword: readFirstValue(resolvedSearchParams.searchKeyword) ?? "",
      status: readFirstValue(resolvedSearchParams.searchStatus) ?? "",
    },
    selectedBedZoneId: readPositiveInteger(resolvedSearchParams.bedZoneId),
    selectedOrchidGroupId: readPositiveInteger(
      resolvedSearchParams.orchidGroupId,
    ),
    selectedPhysicalBedId: readPositiveInteger(
      resolvedSearchParams.physicalBedId,
    ),
    startBedId: readPositiveInteger(resolvedSearchParams.startBedId),
  };
}

function readVisibleBedCount(
  value: string | string[] | undefined,
): VisibleBedCount {
  const bedCount = Number(readFirstValue(value));
  return bedCount === 2 || bedCount === 4 ? bedCount : 3;
}

function readPositiveInteger(value: string | string[] | undefined) {
  const parsed = Number(readFirstValue(value));
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

function readFirstValue(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}
