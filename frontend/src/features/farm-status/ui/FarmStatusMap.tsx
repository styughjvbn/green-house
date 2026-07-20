"use client";

import { useMemo, useState } from "react";
import { Pin, PinOff, X } from "lucide-react";
import type { HouseStatusSummary, OrchidGroup } from "@/entities/farm/types";
import { useFarmStatusMap } from "../model/useFarmStatusMap";
import type { FarmStatusMapProps } from "../model/types";
import FarmMapCanvas from "./components/FarmMapCanvas";
import { FarmStatusSearchPanel } from "./components/FarmStatusSearchPanel";
import { SelectionSummaryPanel } from "./components/StatusPanels";

export function FarmStatusMap(props: FarmStatusMapProps) {
  const { mapData } = props;
  const houses = useMemo(() => hydrateMapHouses(mapData), [mapData]);
  const map = useFarmStatusMap({
    ...props,
    mapData: { ...mapData, houses },
  });
  const [panelOpen, setPanelOpen] = useState(false);
  const [panelPinned, setPanelPinned] = useState(false);

  return (
    <div className="relative h-full min-h-0 overflow-hidden">
      {map.errorMessage ? (
        <div className="absolute top-3 left-1/2 z-[1200] -translate-x-1/2 rounded-md border border-[#c25a3c] bg-[#fff1ec] p-3 text-sm text-[#8f2f19] shadow">
          {map.errorMessage}
        </div>
      ) : null}
      <FarmMapCanvas
        detailPanelOpen={panelOpen}
        filterMatches={map.filterMatches}
        houses={houses}
        hasActiveSearch={map.hasActiveSearch}
        selectedBedZoneId={map.selectedBedZoneId}
        selectedHouseId={map.selectedHouseId}
        selectedPhysicalBedId={map.selectedPhysicalBedId}
        selectedOrchidGroup={map.selectedOrchidGroup}
        selectedTarget={map.selectedTarget}
        zoomData={map.zoomData}
        zoomLevel={map.zoomLevel}
        onReset={() => {
          map.resetToFarm();
          if (!panelPinned) {
            setPanelOpen(false);
          }
        }}
        onSelectBedZone={(zone) => {
          setPanelOpen(true);
          void map.handleSelectBedZone(zone);
        }}
        onSelectHouse={(house) => {
          setPanelOpen(true);
          void map.handleSelectHouse(house);
        }}
        onSelectOrchidGroup={(group) => {
          setPanelOpen(true);
          void map.handleSelectOrchidGroup(group);
        }}
        onSelectPhysicalBed={(bed) => {
          setPanelOpen(true);
          void map.handleSelectPhysicalBed(bed);
        }}
        onZoomIn={() => void map.handleZoomIn()}
        onZoomOut={map.handleZoomOut}
      />

      <div className="pointer-events-none absolute top-14 left-3 z-[1100] w-[min(260px,calc(100%-1.5rem))]">
        <div className="pointer-events-auto">
          <FarmStatusSearchPanel
            currentSelectedOrchidGroupId={
              map.selectedOrchidGroup?.orchidGroupId ?? null
            }
            filters={map.searchFilters}
            hasActiveSearch={map.hasActiveSearch}
            loading={map.searchLoading}
            results={map.searchResults}
            onClear={map.clearSearch}
            onSelectResult={(orchidGroup) => {
              setPanelOpen(true);
              void map.handleSelectSearchResult(orchidGroup);
            }}
            onUpdateFilter={map.updateSearchFilter}
          />
        </div>
      </div>

      {panelOpen ? (
        <aside className="absolute top-3 right-3 bottom-3 z-[1100] flex min-h-0 flex-col">
          <div className="mb-2 flex justify-end gap-2">
            <button
              aria-label={panelPinned ? "패널 고정 해제" : "패널 고정"}
              className={`flex h-9 w-9 items-center justify-center rounded-md shadow ${
                panelPinned
                  ? "bg-[#256ff0] text-white"
                  : "bg-white text-[#34503b]"
              }`}
              onClick={() => setPanelPinned((current) => !current)}
              type="button"
            >
              {panelPinned ? <PinOff size={16} /> : <Pin size={16} />}
            </button>
            <button
              aria-label="상세 패널 닫기"
              className="flex h-9 w-9 items-center justify-center rounded-md bg-white text-[#34503b] shadow"
              onClick={() => setPanelOpen(false)}
              type="button"
            >
              <X size={17} />
            </button>
          </div>
          <SelectionSummaryPanel
            selection={map.selection}
            selectedOrchidGroup={map.selectedOrchidGroup}
            selectedHouse={map.selectedHouse}
            onSelectOrchidGroup={map.handleSelectOrchidGroup}
          />
        </aside>
      ) : (
        <button
          className="absolute right-3 bottom-3 z-[1100] rounded-md bg-white/95 px-3 py-2 text-sm font-semibold text-[#34503b] shadow"
          onClick={() => setPanelOpen(true)}
          type="button"
        >
          선택 요약 열기
        </button>
      )}
    </div>
  );
}

function hydrateMapHouses(mapData: FarmStatusMapProps["mapData"]) {
  const groupsByZone = new Map<number, OrchidGroup[]>();
  const houseById = new Map(
    mapData.houses.map((house) => [house.houseId, house]),
  );

  mapData.orchidGroups.forEach((group) => {
    const house = houseById.get(group.houseId);
    const bed = house?.physicalBeds.find(
      (item) => item.id === group.physicalBedId,
    );
    const zone = bed?.bedZones.find((item) => item.id === group.bedZoneId);
    if (!house || !bed || !zone) return;

    const item: OrchidGroup = {
      id: group.orchidGroupId,
      bedZoneId: group.bedZoneId,
      varietyId: group.varietyId,
      genus: null,
      varietyName: group.varietyName,
      quantity: group.quantity,
      potSize: group.potSize,
      potSizeCode: "UNSPECIFIED",
      ageYear: group.ageYear,
      status: group.status,
      placementType: null,
      trayCount: null,
      splitPlacementAllowed: false,
      startPosition: group.startPosition,
      endPosition: group.endPosition,
      sortOrder: group.sortOrder,
      memo: null,
      houseId: group.houseId,
      houseNumber: house.houseNumber,
      physicalBedNumber: bed.number,
      bedZoneName: zone.name,
    };
    groupsByZone.set(group.bedZoneId, [
      ...(groupsByZone.get(group.bedZoneId) ?? []),
      item,
    ]);
  });

  return mapData.houses.map<HouseStatusSummary>((house) => ({
    ...house,
    physicalBeds: house.physicalBeds.map((bed) => ({
      ...bed,
      bedZones: bed.bedZones.map((zone) => ({
        ...zone,
        orchidGroups: groupsByZone.get(zone.id) ?? [],
      })),
    })),
  }));
}
