"use client";

import { useFarmStatusMap } from "../model/useFarmStatusMap";
import type { FarmStatusMapProps } from "../model/types";
import FarmMapCanvas from "./components/FarmMapCanvas";
import { FarmStatusSearchPanel } from "./components/FarmStatusSearchPanel";
import { SelectionSummaryPanel } from "./components/StatusPanels";

export function FarmStatusMap(props: FarmStatusMapProps) {
  const { mapData } = props;
  const map = useFarmStatusMap(props);

  return (
    <div className="h-full min-h-0">
      <div className="grid h-full gap-4 lg:grid-cols-[minmax(0,1fr)_clamp(280px,28%,440px)]">
        <section className="flex min-h-0 flex-col gap-3">
          {map.errorMessage ? (
            <div className="rounded-md border border-[#c25a3c] bg-[#fff1ec] p-3 text-sm text-[#8f2f19]">
              {map.errorMessage}
            </div>
          ) : null}
          <FarmMapCanvas
            filterMatches={map.filterMatches}
            houses={mapData.houses}
            hasActiveSearch={map.hasActiveSearch}
            selectedBedZoneId={map.selectedBedZoneId}
            selectedHouseId={map.selectedHouseId}
            selectedPhysicalBedId={map.selectedPhysicalBedId}
            selectedOrchidGroup={map.selectedOrchidGroup}
            selectedTarget={map.selectedTarget}
            zoomData={map.zoomData}
            zoomLevel={map.zoomLevel}
            onReset={map.resetToFarm}
            onSelectBedZone={(zone) => void map.handleSelectBedZone(zone)}
            onSelectHouse={(house) => void map.handleSelectHouse(house)}
            onSelectOrchidGroup={map.handleSelectOrchidGroup}
            onSelectPhysicalBed={(bed) => void map.handleSelectPhysicalBed(bed)}
            onZoomIn={() => void map.handleZoomIn()}
            onZoomOut={map.handleZoomOut}
          />
        </section>

        <aside className="flex min-h-0 flex-col gap-3">
          <FarmStatusSearchPanel
            currentSelectedOrchidGroupId={
              map.selectedOrchidGroup?.orchidGroupId ?? null
            }
            filters={map.searchFilters}
            hasActiveSearch={map.hasActiveSearch}
            loading={map.searchLoading}
            results={map.searchResults}
            onClear={map.clearSearch}
            onSelectResult={(orchidGroup) =>
              void map.handleSelectSearchResult(orchidGroup)
            }
            onUpdateFilter={map.updateSearchFilter}
          />
          <SelectionSummaryPanel
            selection={map.selection}
            selectedOrchidGroup={map.selectedOrchidGroup}
            selectedHouse={map.selectedHouse}
            onSelectOrchidGroup={map.handleSelectOrchidGroup}
          />
        </aside>
      </div>
    </div>
  );
}
