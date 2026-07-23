"use client";

import { useCallback, useMemo, useState, useSyncExternalStore } from "react";
import { normalizeCellRange } from "../lib/orchidManagementUtils";
import { useBedViewport } from "../model/useBedViewport";
import { useOrchidManagementMap } from "../model/useOrchidManagementMap";
import type {
  MapCellRangePick,
  OrchidManagementMapProps,
} from "../model/types";
import BedNavigationToolbar from "./components/BedNavigationToolbar";
import ContinuousBedMap from "./components/ContinuousBedMap";
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
  initialStartBedId,
  initialVisibleBedCount,
  mapData,
  house,
  workTypes,
}: OrchidManagementMapProps) {
  const bedViewport = useBedViewport(
    house.physicalBeds,
    initialStartBedId,
    initialVisibleBedCount,
  );
  const scopedHouse = useMemo(
    () => ({ ...house, physicalBeds: bedViewport.visibleBeds }),
    [bedViewport.visibleBeds, house],
  );
  const orchidManagement = useOrchidManagementMap(
    scopedHouse,
    house,
    workTypes,
    initialSelectedOrchidGroupId,
    initialSelectedPhysicalBedId ?? null,
    initialSelectedBedZoneId ?? null,
    initialSearchFilters,
  );
  const selectedHistoryHouse = useMemo(() => {
    if (orchidManagement.selection?.type !== "HOUSE") return null;
    const selectedHouseId = orchidManagement.selection.houseId;
    const physicalBeds = house.physicalBeds.filter(
      (bed) => bed.houseId === selectedHouseId,
    );
    const selectedSummary = mapData.houses.find(
      (item) => item.houseId === selectedHouseId,
    );
    return {
      ...house,
      id: selectedHouseId,
      number: selectedSummary?.houseNumber ?? physicalBeds[0]?.houseNumber ?? 0,
      name: selectedSummary?.houseName ?? house.name,
      physicalBeds,
    };
  }, [house, mapData.houses, orchidManagement.selection]);
  const historyHouse = selectedHistoryHouse ?? scopedHouse;
  const [showScale, setShowScale] = useState(true);
  const [orchidGroupSelection, setOrchidGroupSelection] = useState<{
    houseId: number;
    ids: Set<number>;
  }>(() => ({ houseId: house.id, ids: new Set() }));
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
    completed: false,
    excludeOrchidGroupId: null,
    targetBedZoneId: null,
    startCell: null,
    endCell: null,
    version: 0,
  });
  const selectedOrchidGroupIds =
    orchidGroupSelection.houseId === house.id
      ? orchidGroupSelection.ids
      : new Set<number>();

  function toggleSelectedOrchidGroup(orchidGroupId: number) {
    setOrchidGroupSelection((current) => {
      const next = new Set(current.houseId === house.id ? current.ids : []);
      if (next.has(orchidGroupId)) next.delete(orchidGroupId);
      else next.add(orchidGroupId);
      return { houseId: house.id, ids: next };
    });
  }

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
    const nextExcludeOrchidGroupId = excludeOrchidGroupId ?? null;
    setMapCellRangePick((current) => {
      const sameTarget =
        (targetBedZoneId == null ||
          current.targetBedZoneId === targetBedZoneId) &&
        current.excludeOrchidGroupId === nextExcludeOrchidGroupId;

      if (current.active && sameTarget) {
        return {
          active: false,
          completed: false,
          excludeOrchidGroupId: null,
          targetBedZoneId: null,
          startCell: null,
          endCell: null,
          version: current.version + 1,
        };
      }

      return {
        active: true,
        completed: false,
        excludeOrchidGroupId: nextExcludeOrchidGroupId,
        targetBedZoneId,
        startCell: null,
        endCell: null,
        version: current.version + 1,
      };
    });
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
      active: true,
      completed: true,
      excludeOrchidGroupId: excludeOrchidGroupId ?? null,
      targetBedZoneId,
      startCell: range.startCell,
      endCell: range.endCell,
      version: current.version + 1,
    }));
  }

  const clearMapCellRangePick = useCallback(() => {
    setMapCellRangePick((current) => ({
      active: false,
      completed: false,
      excludeOrchidGroupId: null,
      targetBedZoneId: null,
      startCell: null,
      endCell: null,
      version: current.version + 1,
    }));
  }, []);

  const selectOrchidGroupForEdit =
    orchidManagement.actions.selectOrchidGroupForEdit;
  const handleSelectOrchidGroup = useCallback(
    (orchidGroupId: number) => {
      clearMapCellRangePick();
      selectOrchidGroupForEdit(orchidGroupId);
    },
    [clearMapCellRangePick, selectOrchidGroupForEdit],
  );

  function pickMapCellRange(bedZoneId: number, cell: number) {
    setMapCellRangePick((current) => {
      if (!current.active) {
        return current;
      }
      const targetBedZoneId = current.targetBedZoneId ?? bedZoneId;
      if (targetBedZoneId !== bedZoneId && !current.completed) {
        return current;
      }

      if (
        current.startCell == null ||
        current.completed ||
        (current.endCell != null && current.startCell !== current.endCell)
      ) {
        return {
          ...current,
          completed: false,
          targetBedZoneId: bedZoneId,
          startCell: cell,
          endCell: cell,
          version: current.version + 1,
        };
      }

      return {
        active: true,
        completed: true,
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
        <BedNavigationToolbar
          createActive={
            orchidManagement.mutationMode === "CREATE" &&
            !orchidManagement.pasteSourceOrchidGroup
          }
          distinguishVarietyColors={distinguishVarietyColors}
          houses={mapData.houses}
          startHouseId={bedViewport.visibleBeds[0]?.houseId ?? null}
          visibleBedCount={bedViewport.visibleBedCount}
          hasPrevious={bedViewport.hasPrevious}
          hasNext={bedViewport.hasNext}
          showScale={showScale}
          onToggleVarietyColors={toggleVarietyColors}
          onToggleScale={() => setShowScale((current) => !current)}
          onOpenCreate={() => {
            setShowMultiCreate(false);
            setShowRepot(false);
            clearMapCellRangePick();
            orchidManagement.actions.openCreate();
          }}
          onPrevious={bedViewport.actions.previous}
          onNext={bedViewport.actions.next}
          onGoToHouse={(houseId) => {
            bedViewport.actions.goToHouse(houseId);
            orchidManagement.actions.selectHouse(houseId);
          }}
          onVisibleBedCountChange={bedViewport.actions.setVisibleBedCount}
        />
        <div className="min-h-0 flex-1">
          <ContinuousBedMap
            beds={house.physicalBeds}
            startBedIndex={bedViewport.startBedIndex}
            visibleBedCount={bedViewport.visibleBedCount}
            distinguishVarietyColors={distinguishVarietyColors}
            filteredOrchidGroupIds={orchidManagement.filteredOrchidGroupIds}
            selectedOrchidGroupIds={selectedOrchidGroupIds}
            selection={orchidManagement.selection}
            showScale={showScale}
            cellRangePick={mapCellRangePick}
            onStartBedIndexChange={bedViewport.actions.setStartIndex}
            onPickCellRange={pickMapCellRange}
            onSelectBedZone={(bedZoneId) => {
              clearMapCellRangePick();
              orchidManagement.actions.selectBedZone(bedZoneId);
            }}
            onSelectPhysicalBed={(physicalBedId) => {
              clearMapCellRangePick();
              orchidManagement.actions.selectPhysicalBed(physicalBedId);
            }}
            onSelectOrchidGroup={handleSelectOrchidGroup}
          />
        </div>
        <SelectedZoneInfo
          house={historyHouse}
          selectedBedZone={orchidManagement.selectedBedZone}
          selectedOrchidGroup={orchidManagement.selectedOrchidGroup}
          selectedPhysicalBed={orchidManagement.selectedPhysicalBed}
          selection={orchidManagement.selection}
          workRecordSummary={orchidManagement.workRecordSummary}
          workRecordSummaryLoading={orchidManagement.workRecordSummaryLoading}
          orchidGroupHistory={orchidManagement.orchidGroupHistory}
          orchidGroupHistoryLoading={orchidManagement.orchidGroupHistoryLoading}
          orchidGroupHistoryPage={orchidManagement.orchidGroupHistoryPage}
          orchidGroupHistoryPageLoading={
            orchidManagement.orchidGroupHistoryPageLoading
          }
          orchidGroupLineage={orchidManagement.orchidGroupLineage}
          orchidGroupLineageLoading={orchidManagement.orchidGroupLineageLoading}
          onOrchidGroupHistoryPageChange={
            orchidManagement.actions.loadOrchidGroupHistoryPage
          }
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
            const targetBed = house.physicalBeds.find(
              (bed) =>
                bed.houseId === orchidGroup.houseId &&
                bed.number === orchidGroup.physicalBedNumber,
            );
            if (targetBed) {
              bedViewport.actions.goToBed(targetBed.id);
            }
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
            house={scopedHouse}
            onClose={() => setShowMultiCreate(false)}
          />
        ) : (
          <OrchidSelectionPanel
            copiedOrchidGroup={orchidManagement.copiedOrchidGroup}
            errorMessage={orchidManagement.errorMessage}
            filteredOrchidGroupIds={orchidManagement.filteredOrchidGroupIds}
            hasActiveSearch={orchidManagement.hasActiveSearch}
            house={scopedHouse}
            placementHouses={placementHouses}
            listSelection={orchidManagement.listSelection}
            mutationMode={orchidManagement.mutationMode}
            pasteSourceOrchidGroup={orchidManagement.pasteSourceOrchidGroup}
            resolvedZone={orchidManagement.resolvedZone}
            saving={orchidManagement.saving}
            selectedBedZone={orchidManagement.selectedBedZone}
            selectedOrchidGroupIds={selectedOrchidGroupIds}
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
            onClearSelectedOrchidGroups={() =>
              setOrchidGroupSelection({ houseId: house.id, ids: new Set() })
            }
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
            onSelectOrchidGroups={(orchidGroupIds) =>
              setOrchidGroupSelection({
                houseId: house.id,
                ids: new Set(orchidGroupIds),
              })
            }
            onStartMapCellRangePick={startMapCellRangePick}
            onSyncMapCellRangePick={syncMapCellRangePick}
            onToggleSelectedOrchidGroup={toggleSelectedOrchidGroup}
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
