import type { VisibleBedCount } from "@/entities/farm/types";
import { getOrchidManagementViewport } from "./api/orchidManagementApi";
import type { OrchidManagementSearchState } from "./model/types";
import { OrchidManagementMap } from "./ui/OrchidManagementMap";

type OrchidManagementPageProps = {
  initialViewport: Awaited<ReturnType<typeof getOrchidManagementViewport>>;
  initialSelectedOrchidGroupId: number | null;
  initialSelectedPhysicalBedId?: number | null;
  initialSelectedBedZoneId?: number | null;
  initialSearchFilters?: OrchidManagementSearchState;
};

export async function OrchidManagementRoutePage({
  resolvedSearchParams,
}: {
  resolvedSearchParams: Record<string, string | string[] | undefined>;
}) {
  const routeState = readOrchidManagementRouteState(resolvedSearchParams);
  const startBedId =
    routeState.startBedId ?? routeState.selectedPhysicalBedId ?? null;
  const viewport = await getOrchidManagementViewport(
    startBedId,
    routeState.bedCount,
  );
  return (
    <OrchidManagementPage
      initialSearchFilters={routeState.searchFilters}
      initialSelectedBedZoneId={routeState.selectedBedZoneId}
      initialSelectedOrchidGroupId={routeState.selectedOrchidGroupId}
      initialSelectedPhysicalBedId={routeState.selectedPhysicalBedId}
      initialViewport={viewport}
    />
  );
}

function OrchidManagementPage({
  initialViewport,
  initialSelectedOrchidGroupId,
  initialSelectedPhysicalBedId,
  initialSelectedBedZoneId,
  initialSearchFilters,
}: OrchidManagementPageProps) {
  if (initialViewport.bedOrder.length === 0) {
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
          initialViewport.startBedId ?? "empty",
          initialSelectedOrchidGroupId ?? "group-default",
          initialSelectedPhysicalBedId ?? "bed-default",
          initialSelectedBedZoneId ?? "zone-default",
        ].join("-")}
        initialSelectedBedZoneId={initialSelectedBedZoneId}
        initialSelectedPhysicalBedId={initialSelectedPhysicalBedId}
        initialSearchFilters={initialSearchFilters}
        initialViewport={initialViewport}
        initialSelectedOrchidGroupId={initialSelectedOrchidGroupId}
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
