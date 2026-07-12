"use client";

import { useState, useSyncExternalStore } from "react";
import { normalizeCellRange } from "../lib/orchidManagementUtils";
import { useOrchidManagementMap } from "../model/useOrchidManagementMap";
import type {
  MapCellRangePick,
  OrchidManagementMapProps,
} from "../model/types";
import BedPrecisionSettings from "./components/BedPrecisionSettings";
import HouseDetailMap from "./components/HouseDetailMap";
import HouseSelectorPanel from "./components/HouseSelectorPanel";
import OrchidSearchPanel from "./components/OrchidSearchPanel";
import OrchidSelectionPanel, {
  SelectedZoneInfo,
} from "./components/OrchidSelectionPanel";

const VARIETY_COLOR_STORAGE_KEY =
  "orchid-management:distinguish-variety-colors";
const VARIETY_COLOR_CHANGE_EVENT =
  "orchid-management:distinguish-variety-colors-change";

export function OrchidManagementMap({
  initialSelectedOrchidGroupId,
  initialSelectedPhysicalBedId,
  initialSelectedBedZoneId,
  initialSearchFilters,
  mapData,
  house,
  workTypes,
}: OrchidManagementMapProps) {
  const orchidManagement = useOrchidManagementMap(
    house,
    workTypes,
    initialSelectedOrchidGroupId,
    initialSelectedPhysicalBedId ?? null,
    initialSelectedBedZoneId ?? null,
    initialSearchFilters,
  );
  const [showScale, setShowScale] = useState(true);
  const distinguishVarietyColors = useSyncExternalStore(
    subscribeVarietyColorPreference,
    getVarietyColorPreference,
    getServerVarietyColorPreference,
  );
  const [mapCellRangePick, setMapCellRangePick] = useState<MapCellRangePick>({
    active: false,
    excludeOrchidGroupId: null,
    targetBedZoneId: null,
    startCell: null,
    endCell: null,
    version: 0,
  });

  function toggleVarietyColors() {
    const next = !distinguishVarietyColors;
    window.localStorage.setItem(VARIETY_COLOR_STORAGE_KEY, String(next));
    window.dispatchEvent(new Event(VARIETY_COLOR_CHANGE_EVENT));
  }

  function startMapCellRangePick({
    targetBedZoneId,
    excludeOrchidGroupId,
  }: {
    endCell: string;
    excludeOrchidGroupId?: number | null;
    maxCell: number;
    startCell: string;
    targetBedZoneId: number | null;
  }) {
    setMapCellRangePick((current) => ({
      active: true,
      excludeOrchidGroupId: excludeOrchidGroupId ?? null,
      targetBedZoneId,
      startCell: null,
      endCell: null,
      version: current.version + 1,
    }));
  }

  function syncMapCellRangePick({
    endCell,
    excludeOrchidGroupId,
    maxCell,
    startCell,
    targetBedZoneId,
  }: {
    endCell: string;
    excludeOrchidGroupId?: number | null;
    maxCell: number;
    startCell: string;
    targetBedZoneId: number;
  }) {
    const range = normalizeCellRange(startCell, endCell, maxCell);
    setMapCellRangePick((current) => ({
      active: false,
      excludeOrchidGroupId: excludeOrchidGroupId ?? null,
      targetBedZoneId,
      startCell: range.startCell,
      endCell: range.endCell,
      version: current.version + 1,
    }));
  }

  function clearMapCellRangePick() {
    setMapCellRangePick((current) => ({
      active: false,
      excludeOrchidGroupId: null,
      targetBedZoneId: null,
      startCell: null,
      endCell: null,
      version: current.version + 1,
    }));
  }

  function pickMapCellRange(bedZoneId: number, cell: number) {
    setMapCellRangePick((current) => {
      if (!current.active) {
        return current;
      }
      const targetBedZoneId = current.targetBedZoneId ?? bedZoneId;
      if (targetBedZoneId !== bedZoneId) {
        return current;
      }

      if (
        current.startCell == null ||
        (current.endCell != null && current.startCell !== current.endCell)
      ) {
        return {
          ...current,
          targetBedZoneId,
          startCell: cell,
          endCell: cell,
          version: current.version + 1,
        };
      }

      return {
        active: false,
        excludeOrchidGroupId: current.excludeOrchidGroupId,
        targetBedZoneId,
        startCell: Math.min(current.startCell, cell),
        endCell: Math.max(current.startCell, cell),
        version: current.version + 1,
      };
    });
  }

  return (
    <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_440px]">
      <section className="space-y-3">
        <HouseSelectorPanel
          distinguishVarietyColors={distinguishVarietyColors}
          house={house}
          houses={mapData.houses}
          placementEditMode={orchidManagement.placementEditMode}
          selected={orchidManagement.selection?.type === "HOUSE"}
          selectedHouseId={house.id}
          showScale={showScale}
          onTogglePlacementEditMode={() => {
            clearMapCellRangePick();
            orchidManagement.actions.togglePlacementEditMode();
          }}
          onToggleVarietyColors={toggleVarietyColors}
          onToggleScale={() => setShowScale((current) => !current)}
          onSelectHouse={() => {
            clearMapCellRangePick();
            orchidManagement.actions.selectHouse();
          }}
        />
        <HouseDetailMap
          distinguishVarietyColors={distinguishVarietyColors}
          dragState={orchidManagement.dragState}
          filteredOrchidGroupIds={orchidManagement.filteredOrchidGroupIds}
          house={house}
          placementEditMode={orchidManagement.placementEditMode}
          saving={orchidManagement.saving}
          selection={orchidManagement.selection}
          showScale={showScale}
          cellRangePick={mapCellRangePick}
          onDragEnd={orchidManagement.actions.endDrag}
          onDragStart={orchidManagement.actions.startDrag}
          onDropOnBedZone={orchidManagement.actions.dropOnBedZone}
          onEnterDropZone={orchidManagement.actions.enterDropZone}
          onPickCellRange={pickMapCellRange}
          onSelectBedZone={(bedZoneId) => {
            clearMapCellRangePick();
            orchidManagement.actions.selectBedZone(bedZoneId);
          }}
          onSelectHouse={() => {
            clearMapCellRangePick();
            orchidManagement.actions.selectHouse();
          }}
          onSelectPhysicalBed={(physicalBedId) => {
            clearMapCellRangePick();
            orchidManagement.actions.selectPhysicalBed(physicalBedId);
          }}
          onSelectOrchidGroup={(orchidGroupId) => {
            clearMapCellRangePick();
            orchidManagement.actions.selectOrchidGroupForEdit(orchidGroupId);
          }}
        />
        <SelectedZoneInfo
          house={house}
          selectedBedZone={orchidManagement.selectedBedZone}
          selectedOrchidGroup={orchidManagement.selectedOrchidGroup}
          selectedPhysicalBed={orchidManagement.selectedPhysicalBed}
          selection={orchidManagement.selection}
          workRecordSummary={orchidManagement.workRecordSummary}
          workRecordSummaryLoading={orchidManagement.workRecordSummaryLoading}
        />
        {/* <BedPrecisionSettings zone={orchidManagement.resolvedZone} /> 26.07.11 비활성화*/}
      </section>
      <div className="space-y-3">
        <OrchidSearchPanel
          currentHouseId={house.id}
          currentSelectedOrchidGroupId={
            orchidManagement.selectedOrchidGroup?.id ?? null
          }
          filteredCount={orchidManagement.searchResults.length}
          filters={orchidManagement.searchFilters}
          hasActiveSearch={orchidManagement.hasActiveSearch}
          loading={orchidManagement.searchLoading}
          results={orchidManagement.searchResults}
          onClear={() => {
            clearMapCellRangePick();
            orchidManagement.actions.resetSearch();
          }}
          onSelectResult={(orchidGroup) => {
            clearMapCellRangePick();
            orchidManagement.actions.moveToOrchidGroup(orchidGroup);
          }}
          onUpdateFilter={orchidManagement.actions.updateSearchFilter}
        />
        <OrchidSelectionPanel
          copiedOrchidGroup={orchidManagement.copiedOrchidGroup}
          errorMessage={orchidManagement.errorMessage}
          filteredOrchidGroupIds={orchidManagement.filteredOrchidGroupIds}
          hasActiveSearch={orchidManagement.hasActiveSearch}
          house={house}
          mutationMode={orchidManagement.mutationMode}
          pasteSourceOrchidGroup={orchidManagement.pasteSourceOrchidGroup}
          preferredMoveZoneId={orchidManagement.preferredMoveZoneId}
          resolvedZone={orchidManagement.resolvedZone}
          saving={orchidManagement.saving}
          selectedBedZone={orchidManagement.selectedBedZone}
          selectedOrchidGroup={orchidManagement.selectedOrchidGroup}
          selectedPhysicalBed={orchidManagement.selectedPhysicalBed}
          selection={orchidManagement.selection}
          workRecordForm={orchidManagement.workRecordForm}
          workTypes={workTypes}
          mapCellRangePick={mapCellRangePick}
          onCancelMutation={() => {
            clearMapCellRangePick();
            orchidManagement.actions.cancelMutation();
          }}
          onClearCopiedOrchidGroup={() => {
            clearMapCellRangePick();
            orchidManagement.actions.clearCopiedOrchidGroup();
          }}
          onCopyOrchidGroup={orchidManagement.actions.copyOrchidGroup}
          onCreate={async (payload) => {
            await orchidManagement.actions.create(payload);
            clearMapCellRangePick();
          }}
          onDelete={async () => {
            await orchidManagement.actions.delete();
            clearMapCellRangePick();
          }}
          onEdit={async (payload) => {
            await orchidManagement.actions.edit(payload);
            clearMapCellRangePick();
          }}
          onMove={async (payload) => {
            await orchidManagement.actions.move(payload);
            clearMapCellRangePick();
          }}
          onOpenCreate={() => {
            clearMapCellRangePick();
            orchidManagement.actions.openCreate();
          }}
          onOpenEdit={() => {
            clearMapCellRangePick();
            orchidManagement.actions.openEdit();
          }}
          onOpenMove={() => {
            clearMapCellRangePick();
            orchidManagement.actions.openMove();
          }}
          onOpenPaste={() => {
            clearMapCellRangePick();
            orchidManagement.actions.openPaste();
          }}
          onOpenWorkRecord={() => {
            clearMapCellRangePick();
            orchidManagement.actions.openWorkRecord();
          }}
          onSelectOrchidGroup={(orchidGroupId) => {
            clearMapCellRangePick();
            orchidManagement.actions.selectOrchidGroup(orchidGroupId);
          }}
          onStartMapCellRangePick={startMapCellRangePick}
          onSyncMapCellRangePick={syncMapCellRangePick}
          onTogglePlacementEditMode={() => {
            clearMapCellRangePick();
            orchidManagement.actions.togglePlacementEditMode();
          }}
          onUpdateWorkRecordForm={orchidManagement.actions.updateWorkRecordForm}
          onWorkRecordCreate={async () => {
            await orchidManagement.actions.workRecordCreate();
            clearMapCellRangePick();
          }}
          placementEditMode={orchidManagement.placementEditMode}
        />
      </div>
    </div>
  );
}

function subscribeVarietyColorPreference(onStoreChange: () => void) {
  window.addEventListener("storage", onStoreChange);
  window.addEventListener(VARIETY_COLOR_CHANGE_EVENT, onStoreChange);

  return () => {
    window.removeEventListener("storage", onStoreChange);
    window.removeEventListener(VARIETY_COLOR_CHANGE_EVENT, onStoreChange);
  };
}

function getServerVarietyColorPreference() {
  return false;
}

function getVarietyColorPreference() {
  return window.localStorage.getItem(VARIETY_COLOR_STORAGE_KEY) === "true";
}
