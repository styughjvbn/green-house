"use client";

import { useFarmStatusMap } from "../model/useFarmStatusMap";
import type { FarmStatusMapProps } from "../model/types";
import FarmMapCanvas from "./components/FarmMapCanvas";
import { OrchidGroupTable, RecentWorkSummary, SelectionSummaryPanel } from "./components/StatusPanels";

export function FarmStatusMap(props: FarmStatusMapProps) {
  const { mapData } = props;
  const map = useFarmStatusMap(props);

  return (
    <div className="space-y-4">
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_400px]">
        <section className="space-y-3">
          {map.errorMessage ? (
            <div className="rounded-md border border-[#c25a3c] bg-[#fff1ec] p-3 text-sm text-[#8f2f19]">
              {map.errorMessage}
            </div>
          ) : null}
          {map.loading ? (
            <div className="rounded-md border border-[#d7ddd4] bg-white p-3 text-sm text-[#5c6a60]">
              불러오는 중입니다.
            </div>
          ) : null}

          <FarmMapCanvas
            houses={mapData.houses}
            selectedBedZoneId={map.selectedBedZoneId}
            selectedHouseId={map.selectedHouseId}
            selectedPhysicalBedId={map.selectedPhysicalBedId}
            selectedTarget={map.selectedTarget}
            zoomData={map.zoomData}
            zoomLevel={map.zoomLevel}
            onReset={map.resetToFarm}
            onSelectBedZone={(zone) => void map.handleSelectBedZone(zone)}
            onSelectHouse={(house) => void map.handleSelectHouse(house)}
            onSelectPhysicalBed={(bed) => void map.handleSelectPhysicalBed(bed)}
            onZoomIn={() => void map.handleZoomIn()}
            onZoomOut={map.handleZoomOut}
          />
        </section>

        <SelectionSummaryPanel selection={map.selection} selectedHouse={map.selectedHouse} />
      </div>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_400px]">
        <OrchidGroupTable selection={map.selection} selectedHouse={map.selectedHouse} />
        <RecentWorkSummary />
      </div>
    </div>
  );
}
