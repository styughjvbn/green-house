"use client";

import { useMemo, useState, useSyncExternalStore } from "react";
import { normalizeCellRange } from "../lib/orchidManagementUtils";
import { useOrchidManagementMap } from "../model/useOrchidManagementMap";
import type {
  MapCellRangePick,
  OrchidManagementMapProps,
} from "../model/types";
import BedPrecisionSettings from "./components/BedPrecisionSettings";
import HouseDetailMap from "./components/HouseDetailMap";
import HouseSelectorPanel from "./components/HouseSelectorPanel";
import MultiCreateOrchidGroupForm from "./components/MultiCreateOrchidGroupForm";
import RepotWorkOperationForm from "./components/RepotWorkOperationForm";
import WorkOperationCorrectionForm from "./components/WorkOperationCorrectionForm";
import OrchidSearchPanel from "./components/OrchidSearchPanel";
import OrchidSelectionPanel from "./components/OrchidSelectionPanel";
import SelectedZoneInfo from "./components/SelectedZoneInfo";

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
  const [showMultiCreate, setShowMultiCreate] = useState(false);
  const [repotSource, setRepotSource] = useState(
    orchidManagement.selectedOrchidGroup,
  );
  const [showRepot, setShowRepot] = useState(false);
  const [correctionOperationId, setCorrectionOperationId] = useState<
    number | null
  >(null);
  const placementHouses = useMemo(
    () =>
      mapData.houses.map((item) => ({
        id: item.houseId,
        number: item.houseNumber,
        name: item.houseName,
        memo: null,
        physicalBeds: item.physicalBeds,
      })),
    [mapData.houses],
  );
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
    <div className="grid h-full min-h-0 gap-4 lg:grid-cols-[minmax(0,1fr)_clamp(280px,28%,440px)]">
      <section className="flex h-full min-h-0 flex-col gap-3">
        <HouseSelectorPanel
          createActive={
            orchidManagement.mutationMode === "CREATE" &&
            !orchidManagement.pasteSourceOrchidGroup
          }
          distinguishVarietyColors={distinguishVarietyColors}
          house={house}
          houses={mapData.houses}
          selected={orchidManagement.selection?.type === "HOUSE"}
          selectedHouseId={house.id}
          showScale={showScale}
          onToggleVarietyColors={toggleVarietyColors}
          onToggleScale={() => setShowScale((current) => !current)}
          onOpenCreate={() => {
            setShowMultiCreate(false);
            setShowRepot(false);
            clearMapCellRangePick();
            orchidManagement.actions.openCreate();
          }}
          onOpenMultiCreate={() => {
            clearMapCellRangePick();
            orchidManagement.actions.cancelMutation();
            setShowRepot(false);
            setShowMultiCreate(true);
          }}
          onSelectHouse={() => {
            clearMapCellRangePick();
            orchidManagement.actions.selectHouse();
          }}
        />
        <div className="min-h-0 flex-1">
          <HouseDetailMap
            distinguishVarietyColors={distinguishVarietyColors}
            filteredOrchidGroupIds={orchidManagement.filteredOrchidGroupIds}
            house={house}
            selection={orchidManagement.selection}
            showScale={showScale}
            cellRangePick={mapCellRangePick}
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
        </div>
        <SelectedZoneInfo
          house={house}
          selectedBedZone={orchidManagement.selectedBedZone}
          selectedOrchidGroup={orchidManagement.selectedOrchidGroup}
          selectedPhysicalBed={orchidManagement.selectedPhysicalBed}
          selection={orchidManagement.selection}
          workRecordSummary={orchidManagement.workRecordSummary}
          workRecordSummaryLoading={orchidManagement.workRecordSummaryLoading}
          orchidGroupHistory={orchidManagement.orchidGroupHistory}
          orchidGroupHistoryLoading={orchidManagement.orchidGroupHistoryLoading}
          orchidGroupLineage={orchidManagement.orchidGroupLineage}
          orchidGroupLineageLoading={orchidManagement.orchidGroupLineageLoading}
          onOpenCorrection={(workOperationId) => {
            if (!orchidManagement.selectedOrchidGroup) return;
            clearMapCellRangePick();
            setShowMultiCreate(false);
            setShowRepot(false);
            setCorrectionOperationId(workOperationId);
          }}
        />
        {/* <BedPrecisionSettings zone={orchidManagement.resolvedZone} /> 26.07.11 비활성화*/}
      </section>
      <div className="flex h-full min-h-0 flex-col gap-3">
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
        {correctionOperationId && orchidManagement.selectedOrchidGroup ? (
          <WorkOperationCorrectionForm
            key={`${correctionOperationId}-${orchidManagement.selectedOrchidGroup.id}`}
            originalWorkOperationId={correctionOperationId}
            orchidGroup={orchidManagement.selectedOrchidGroup}
            onClose={() => setCorrectionOperationId(null)}
          />
        ) : showRepot && repotSource ? (
          <RepotWorkOperationForm
            houses={placementHouses}
            source={repotSource}
            onClose={() => setShowRepot(false)}
          />
        ) : showMultiCreate ? (
          <MultiCreateOrchidGroupForm
            house={house}
            onClose={() => setShowMultiCreate(false)}
          />
        ) : (
          <OrchidSelectionPanel
            copiedOrchidGroup={orchidManagement.copiedOrchidGroup}
            errorMessage={orchidManagement.errorMessage}
            filteredOrchidGroupIds={orchidManagement.filteredOrchidGroupIds}
            hasActiveSearch={orchidManagement.hasActiveSearch}
            house={house}
            placementHouses={placementHouses}
            listSelection={orchidManagement.listSelection}
            mutationMode={orchidManagement.mutationMode}
            pasteSourceOrchidGroup={orchidManagement.pasteSourceOrchidGroup}
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
            onOpenRepot={() => {
              if (!orchidManagement.selectedOrchidGroup) return;
              clearMapCellRangePick();
              orchidManagement.actions.cancelMutation();
              setShowMultiCreate(false);
              setRepotSource(orchidManagement.selectedOrchidGroup);
              setShowRepot(true);
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
            onUpdateWorkRecordForm={
              orchidManagement.actions.updateWorkRecordForm
            }
            onWorkRecordCreate={async () => {
              await orchidManagement.actions.workRecordCreate();
              clearMapCellRangePick();
            }}
          />
        )}
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
