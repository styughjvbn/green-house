"use client";

import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { useCallback, useMemo, useState, useSyncExternalStore } from "react";
import {
  useWorkRecordInvalidation,
  WorkOperationRegistrationDialog,
} from "@/features/work-record";
import { normalizeCellRange } from "../lib/orchidManagementUtils";
import { useBedViewport } from "../model/useBedViewport";
import { useOrchidManagementMap } from "../model/useOrchidManagementMap";
import { useOrchidMultiSelection } from "../model/useOrchidMultiSelection";
import type {
  MapCellRangePick,
  OrchidManagementMapProps,
} from "../model/types";
import BedNavigationToolbar from "./components/BedNavigationToolbar";
import BulkOrchidGroupCorrectionPanel from "./components/BulkOrchidGroupCorrectionPanel";
import ContinuousBedMap from "./components/ContinuousBedMap";
import WorkOperationCorrectionForm from "./components/WorkOperationCorrectionForm";
import OrchidSelectionPanel from "./components/OrchidSelectionPanel";
import SelectedOrchidGroupsInfo from "./components/SelectedOrchidGroupsInfo";
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
  initialViewport,
  initialBedOrder,
}: OrchidManagementMapProps) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const bedViewport = useBedViewport(initialViewport, initialBedOrder);
  const currentBedOrder = bedViewport.bedOrder[bedViewport.startBedIndex];
  const navigationHouse = useMemo(
    () => ({
      id: currentBedOrder?.houseId ?? 0,
      number: currentBedOrder?.houseNumber ?? 0,
      name: "전체 농장",
      memo: null,
      physicalBeds: bedViewport.loadedBeds,
    }),
    [bedViewport.loadedBeds, currentBedOrder],
  );
  const scopedHouse = useMemo(
    () => ({ ...navigationHouse, physicalBeds: bedViewport.visibleBeds }),
    [bedViewport.visibleBeds, navigationHouse],
  );
  const orchidManagement = useOrchidManagementMap(
    scopedHouse,
    navigationHouse,
    initialSelectedOrchidGroupId,
    initialSelectedPhysicalBedId ?? null,
    initialSelectedBedZoneId ?? null,
    initialSearchFilters,
  );
  const { invalidateWorkData } = useWorkRecordInvalidation();
  const multiSelection = useOrchidMultiSelection(navigationHouse);
  const selectedHistoryHouse = useMemo(() => {
    if (orchidManagement.selection?.type !== "HOUSE") return null;
    const selectedHouseId = orchidManagement.selection.houseId;
    const physicalBeds = bedViewport.loadedBeds.filter(
      (bed) => bed.houseId === selectedHouseId,
    );
    return {
      ...navigationHouse,
      id: selectedHouseId,
      number: physicalBeds[0]?.houseNumber ?? 0,
      name: `${physicalBeds[0]?.houseNumber ?? ""}동`,
      physicalBeds,
    };
  }, [bedViewport.loadedBeds, navigationHouse, orchidManagement.selection]);
  const historyHouse = selectedHistoryHouse ?? scopedHouse;
  const [showScale, setShowScale] = useState(true);
  const [correctionOperationId, setCorrectionOperationId] = useState<
    number | null
  >(null);
  const [showBulkCorrection, setShowBulkCorrection] = useState(false);
  const [workRegistrationPreset, setWorkRegistrationPreset] = useState<{
    orchidGroupIds: number[];
    workTypeCode?: string;
  } | null>(null);
  const [searchGroupOrchidGroupIds, setSearchGroupOrchidGroupIds] =
    useState<Set<number> | null>(null);
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
  function toggleVarietyColors() {
    const next = !distinguishVarietyColors;
    window.localStorage.setItem(VARIETY_COLOR_STORAGE_KEY, String(next));
    window.dispatchEvent(new Event(VARIETY_COLOR_CHANGE_EVENT));
  }

  function openWorkRegistration(
    orchidGroupIds: number[],
    workTypeCode?: string,
  ) {
    if (orchidGroupIds.length === 0) return;
    clearMapCellRangePick();
    setShowBulkCorrection(false);
    orchidManagement.actions.cancelMutation();
    setWorkRegistrationPreset({ orchidGroupIds, workTypeCode });
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

  const selectOrchidGroupOnMap =
    orchidManagement.actions.selectOrchidGroupOnMap;
  const handleSelectOrchidGroup = useCallback(
    (orchidGroupId: number) => {
      clearMapCellRangePick();
      if (multiSelection.enabled) {
        multiSelection.toggleOrchidGroup(orchidGroupId);
        return;
      }
      selectOrchidGroupOnMap(orchidGroupId);
    },
    [clearMapCellRangePick, multiSelection, selectOrchidGroupOnMap],
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
      {workRegistrationPreset ? (
        <WorkOperationRegistrationDialog
          presetOrchidGroupIds={workRegistrationPreset.orchidGroupIds}
          presetWorkTypeCode={workRegistrationPreset.workTypeCode}
          onClose={() => setWorkRegistrationPreset(null)}
          onSaved={() => {
            orchidManagement.actions.invalidateHistory();
            void invalidateWorkData();
            void queryClient.invalidateQueries({
              queryKey: ["farm-status", "orchid-management-viewport"],
            });
            router.refresh();
          }}
        />
      ) : null}
      <section className="flex h-full min-h-0 flex-col gap-3">
        <BedNavigationToolbar
          createActive={
            orchidManagement.mutationMode === "CREATE" &&
            !orchidManagement.pasteSourceOrchidGroup
          }
          distinguishVarietyColors={distinguishVarietyColors}
          houses={bedViewport.bedOrder}
          startHouseId={currentBedOrder?.houseId ?? null}
          visibleBedCount={bedViewport.visibleBedCount}
          hasPreviousHouse={bedViewport.hasPreviousHouse}
          hasNextHouse={bedViewport.hasNextHouse}
          showScale={showScale}
          onToggleVarietyColors={toggleVarietyColors}
          onToggleScale={() => setShowScale((current) => !current)}
          onOpenCreate={() => {
            clearMapCellRangePick();
            orchidManagement.actions.openCreate();
          }}
          onPrevious={bedViewport.actions.previousHouse}
          onNext={bedViewport.actions.nextHouse}
          onGoToHouse={(houseId) => {
            bedViewport.actions.goToHouse(houseId);
            orchidManagement.actions.selectHouse(houseId);
          }}
          onVisibleBedCountChange={bedViewport.actions.setVisibleBedCount}
        />
        <div className="min-h-0 flex-1">
          <ContinuousBedMap
            bedOrder={bedViewport.bedOrder}
            bedsById={bedViewport.bedsById}
            startBedIndex={bedViewport.startBedIndex}
            visibleBedCount={bedViewport.visibleBedCount}
            distinguishVarietyColors={distinguishVarietyColors}
            filteredOrchidGroupIds={
              searchGroupOrchidGroupIds ??
              orchidManagement.filteredOrchidGroupIds
            }
            multiSelectEnabled={multiSelection.enabled}
            selectedOrchidGroupIds={multiSelection.selectedIds}
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
        {multiSelection.enabled ? (
          <SelectedOrchidGroupsInfo
            orchidGroups={multiSelection.selectedOrchidGroups}
            onBulkCorrection={() => setShowBulkCorrection(true)}
            onCreateMovement={() =>
              openWorkRegistration(
                multiSelection.selectedOrchidGroups.map(
                  (orchidGroup) => orchidGroup.id,
                ),
                "MOVEMENT",
              )
            }
            onCreateWork={() =>
              openWorkRegistration(
                multiSelection.selectedOrchidGroups.map(
                  (orchidGroup) => orchidGroup.id,
                ),
              )
            }
            onRemove={multiSelection.toggleOrchidGroup}
          />
        ) : (
          <SelectedZoneInfo
            house={historyHouse}
            selectedBedZone={orchidManagement.selectedBedZone}
            selectedOrchidGroup={orchidManagement.selectedOrchidGroup}
            selectedPhysicalBed={orchidManagement.selectedPhysicalBed}
            selection={orchidManagement.selection}
            workRecordSummary={orchidManagement.workRecordSummary}
            workRecordSummaryLoading={orchidManagement.workRecordSummaryLoading}
            orchidGroupHistory={orchidManagement.orchidGroupHistory}
            orchidGroupHistoryLoading={
              orchidManagement.orchidGroupHistoryLoading
            }
            orchidGroupHistoryPage={orchidManagement.orchidGroupHistoryPage}
            orchidGroupHistoryPageLoading={
              orchidManagement.orchidGroupHistoryPageLoading
            }
            orchidGroupLineage={orchidManagement.orchidGroupLineage}
            orchidGroupLineageLoading={
              orchidManagement.orchidGroupLineageLoading
            }
            onOrchidGroupHistoryPageChange={
              orchidManagement.actions.loadOrchidGroupHistoryPage
            }
            onOpenCorrection={(workOperationId) => {
              if (!orchidManagement.selectedOrchidGroup) return;
              clearMapCellRangePick();
              setCorrectionOperationId(workOperationId);
            }}
          />
        )}
        {/* <BedPrecisionSettings zone={orchidManagement.resolvedZone} /> 26.07.11 비활성화*/}
      </section>
      <div className="flex h-full min-h-0 flex-col gap-3">
        {showBulkCorrection &&
        multiSelection.enabled &&
        multiSelection.selectedOrchidGroups.length > 0 ? (
          <BulkOrchidGroupCorrectionPanel
            key={multiSelection.selectedOrchidGroups
              .map((orchidGroup) => orchidGroup.id)
              .join("-")}
            errorMessage={orchidManagement.errorMessage}
            orchidGroups={multiSelection.selectedOrchidGroups}
            saving={orchidManagement.saving}
            onCancel={() => setShowBulkCorrection(false)}
            onSubmit={async (items) => {
              const saved = await orchidManagement.actions.editBatch(items);
              if (saved) setShowBulkCorrection(false);
              return saved;
            }}
          />
        ) : correctionOperationId && orchidManagement.selectedOrchidGroup ? (
          <WorkOperationCorrectionForm
            key={`${correctionOperationId}-${orchidManagement.selectedOrchidGroup.id}`}
            originalWorkOperationId={correctionOperationId}
            orchidGroup={orchidManagement.selectedOrchidGroup}
            onClose={() => setCorrectionOperationId(null)}
          />
        ) : (
          <OrchidSelectionPanel
            copiedOrchidGroup={orchidManagement.copiedOrchidGroup}
            errorMessage={orchidManagement.errorMessage}
            hasActiveSearch={orchidManagement.hasActiveSearch}
            house={scopedHouse}
            listSelection={orchidManagement.listSelection}
            mutationMode={orchidManagement.mutationMode}
            pasteSourceOrchidGroup={orchidManagement.pasteSourceOrchidGroup}
            resolvedZone={orchidManagement.resolvedZone}
            saving={orchidManagement.saving}
            selectedOrchidGroup={orchidManagement.selectedOrchidGroup}
            searchFilters={orchidManagement.searchFilters}
            searchLoading={orchidManagement.searchLoading}
            searchResults={orchidManagement.searchResults}
            mapCellRangePick={mapCellRangePick}
            multiSelectEnabled={multiSelection.enabled}
            selectedOrchidGroupIds={multiSelection.selectedIds}
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
            onDelete={async (orchidGroupId) => {
              await orchidManagement.actions.delete(orchidGroupId);
              clearMapCellRangePick();
            }}
            onEdit={async (payload) => {
              await orchidManagement.actions.edit(payload);
              clearMapCellRangePick();
            }}
            onOpenEdit={(orchidGroupId) => {
              clearMapCellRangePick();
              orchidManagement.actions.selectOrchidGroupForEdit(orchidGroupId);
            }}
            onOpenMovementRecord={() => {
              const orchidGroupId = orchidManagement.selectedOrchidGroup?.id;
              if (orchidGroupId) {
                openWorkRegistration([orchidGroupId], "MOVEMENT");
              }
            }}
            onOpenPaste={() => {
              clearMapCellRangePick();
              orchidManagement.actions.openPaste();
            }}
            onOpenWorkRecord={() => {
              const orchidGroupId = orchidManagement.selectedOrchidGroup?.id;
              if (orchidGroupId) openWorkRegistration([orchidGroupId]);
            }}
            onSelectOrchidGroup={(orchidGroupId) => {
              clearMapCellRangePick();
              if (multiSelection.enabled) {
                multiSelection.toggleOrchidGroup(orchidGroupId);
                return;
              }
              orchidManagement.actions.selectOrchidGroup(orchidGroupId);
            }}
            onSelectSearchResult={(orchidGroup) => {
              clearMapCellRangePick();
              const targetBed = bedViewport.bedOrder.find(
                (bed) =>
                  bed.houseId === orchidGroup.houseId &&
                  bed.number === orchidGroup.physicalBedNumber,
              );
              if (
                targetBed &&
                !bedViewport.visibleBedIds.includes(targetBed.id)
              ) {
                bedViewport.actions.goToBed(targetBed.id);
              }
              orchidManagement.actions.moveToOrchidGroup(orchidGroup);
            }}
            onSearchGroupSelectionChange={(orchidGroupIds) =>
              setSearchGroupOrchidGroupIds(
                orchidGroupIds ? new Set(orchidGroupIds) : null,
              )
            }
            onStartMapCellRangePick={startMapCellRangePick}
            onSyncMapCellRangePick={syncMapCellRangePick}
            onToggleMultiSelect={() => {
              clearMapCellRangePick();
              setShowBulkCorrection(false);
              orchidManagement.actions.cancelMutation();
              multiSelection.toggleEnabled();
            }}
            onUpdateSearchFilter={orchidManagement.actions.updateSearchFilter}
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
