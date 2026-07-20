import type {
  FarmStatusMapData,
  House,
  VisibleBedCount,
  WorkType,
} from "@/entities/farm/types";
import type { OrchidManagementSearchState } from "../model/types";
import { OrchidManagementMap } from "./OrchidManagementMap";

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

export function OrchidManagementPage({
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
