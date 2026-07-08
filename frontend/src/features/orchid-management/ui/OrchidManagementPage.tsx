import type { FarmStatusMapData, House, WorkType } from "@/entities/farm/types";
import type { OrchidManagementSearchState } from "../model/types";
import { OrchidManagementMap } from "./OrchidManagementMap";

type OrchidManagementPageProps = {
  mapData: FarmStatusMapData;
  house: House | null;
  initialSelectedOrchidGroupId: number | null;
  initialSearchFilters?: OrchidManagementSearchState;
  workTypes: WorkType[];
};

export function OrchidManagementPage({
  mapData,
  house,
  initialSelectedOrchidGroupId,
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
    <main className="space-y-4">
      <OrchidManagementMap
        key={`${house.id}-${initialSelectedOrchidGroupId ?? "default"}`}
        initialSearchFilters={initialSearchFilters}
        mapData={mapData}
        house={house}
        initialSelectedOrchidGroupId={initialSelectedOrchidGroupId}
        workTypes={workTypes}
      />
    </main>
  );
}
