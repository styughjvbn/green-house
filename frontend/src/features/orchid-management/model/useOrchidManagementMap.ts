"use client";

import { useRouter } from "next/navigation";
import { useCallback, useState } from "react";
import type {
  House,
  OrchidGroup,
  WorkRecordTargetType,
  WorkType,
} from "@/entities/farm/types";
import {
  createOrchidGroup,
  deleteOrchidGroup,
  moveOrchidGroup,
  updateOrchidGroup,
  updateOrchidGroupsBatch,
} from "../api/orchidManagementApi";
import {
  findBedZone,
  findOrchidGroup,
  findPhysicalBed,
} from "../lib/orchidManagementUtils";
import { useOrchidClipboard } from "./OrchidClipboardContext";
import { useOrchidManagementHistory } from "./useOrchidManagementHistory";
import { useOrchidManagementSearch } from "./useOrchidManagementSearch";
import { useOrchidWorkRecord } from "./useOrchidWorkRecord";
import type {
  OrchidManagementSearchState,
  MutationMode,
  MutationPayload,
  OrchidListSelection,
  OrchidGroupBatchUpdateItem,
  OrchidSelection,
  PreciseMovePayload,
} from "./types";

export function useOrchidManagementMap(
  house: House,
  navigationHouse: House,
  workTypes: WorkType[],
  initialSelectedOrchidGroupId: number | null,
  initialSelectedPhysicalBedId: number | null,
  initialSelectedBedZoneId: number | null,
  initialSearchFilters?: OrchidManagementSearchState,
) {
  const router = useRouter();
  const [selection, setSelection] = useState<OrchidSelection | null>(
    () =>
      createInitialSelections({
        house,
        navigationHouse,
        initialSelectedOrchidGroupId,
        initialSelectedPhysicalBedId,
        initialSelectedBedZoneId,
      }).selection,
  );
  const [listSelection, setListSelection] = useState<OrchidListSelection>(
    () =>
      createInitialSelections({
        house,
        navigationHouse,
        initialSelectedOrchidGroupId,
        initialSelectedPhysicalBedId,
        initialSelectedBedZoneId,
      }).listSelection,
  );
  const [mutationMode, setMutationMode] = useState<MutationMode>(null);
  const {
    copiedOrchidGroup,
    pasteSourceOrchidGroup,
    copyOrchidGroup: copyToClipboard,
    clearCopiedOrchidGroup: clearClipboard,
    clearPasteSource,
    openPaste: openClipboardPaste,
  } = useOrchidClipboard();
  const search = useOrchidManagementSearch(house, initialSearchFilters);
  const workRecord = useOrchidWorkRecord(workTypes);
  const [saving, setSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const resolvedListSelection: OrchidListSelection =
    listSelection.type === "HOUSE" ||
    (listSelection.type === "PHYSICAL_BED" &&
      Boolean(findPhysicalBed(house, listSelection.physicalBedId))) ||
    (listSelection.type === "BED_ZONE" &&
      Boolean(findBedZone(house, listSelection.bedZoneId)))
      ? listSelection
      : { type: "HOUSE", houseId: house.id };

  const selectedOrchidGroup =
    selection?.type === "ORCHID_GROUP"
      ? findOrchidGroup(navigationHouse, selection.orchidGroupId)
      : null;
  const selectedBedZone =
    selection?.type === "BED_ZONE"
      ? (findBedZone(navigationHouse, selection.bedZoneId)?.zone ?? null)
      : null;
  const selectedPhysicalBed =
    selection?.type === "PHYSICAL_BED"
      ? findPhysicalBed(navigationHouse, selection.physicalBedId)
      : null;
  const resolvedZone = selectedOrchidGroup
    ? (findBedZone(navigationHouse, selectedOrchidGroup.bedZoneId)?.zone ??
      null)
    : selectedBedZone;
  const history = useOrchidManagementHistory(selection, selectedOrchidGroup);

  function selectBedZone(bedZoneId: number) {
    setSelection({ type: "BED_ZONE", bedZoneId });
    setListSelection({ type: "BED_ZONE", bedZoneId });
    setMutationMode(null);
    clearPasteSource();
  }

  function selectPhysicalBed(physicalBedId: number) {
    setSelection({ type: "PHYSICAL_BED", physicalBedId });
    setListSelection({ type: "PHYSICAL_BED", physicalBedId });
    setMutationMode(null);
    clearPasteSource();
  }

  function selectHouse(houseId: number) {
    setSelection({ type: "HOUSE", houseId });
    setListSelection({ type: "HOUSE", houseId });
    setMutationMode(null);
    clearPasteSource();
  }

  function selectOrchidGroup(orchidGroupId: number) {
    setSelection({ type: "ORCHID_GROUP", orchidGroupId });
    setMutationMode(null);
    clearPasteSource();
  }

  function selectOrchidGroupOnMap(orchidGroupId: number) {
    const orchidGroup = findOrchidGroup(navigationHouse, orchidGroupId);
    const bed = orchidGroup
      ? findBedZone(navigationHouse, orchidGroup.bedZoneId)?.bed
      : null;
    if (!orchidGroup || !bed) return;

    setSelection({ type: "ORCHID_GROUP", orchidGroupId });
    setListSelection({ type: "PHYSICAL_BED", physicalBedId: bed.id });
    setMutationMode(null);
    clearPasteSource();
  }

  const selectOrchidGroupForEdit = useCallback(
    (orchidGroupId: number) => {
      setSelection({ type: "ORCHID_GROUP", orchidGroupId });
      setMutationMode("EDIT");
      clearPasteSource();
      setErrorMessage(null);
    },
    [clearPasteSource],
  );

  function openCreate() {
    if (mutationMode === "CREATE" && !pasteSourceOrchidGroup) {
      setMutationMode(null);
      return;
    }
    clearPasteSource();
    setSelection(resolvedListSelection);
    setMutationMode("CREATE");
    setErrorMessage(null);
  }

  function copyOrchidGroup(orchidGroupId: number) {
    const orchidGroup = findOrchidGroup(navigationHouse, orchidGroupId);
    if (orchidGroup) {
      copyToClipboard(orchidGroup);
      setErrorMessage(null);
    }
  }

  function clearCopiedOrchidGroup() {
    clearClipboard();
    if (mutationMode === "CREATE") {
      setMutationMode(null);
    }
  }

  function openPaste() {
    if (mutationMode === "CREATE" && pasteSourceOrchidGroup) {
      setMutationMode(null);
      clearPasteSource();
      return;
    }
    if (openClipboardPaste()) {
      setSelection(resolvedListSelection);
      setMutationMode("CREATE");
      setErrorMessage(null);
      return;
    }
    setErrorMessage("붙여넣을 구역과 복사한 난 묶음을 확인하세요.");
  }

  function openMove() {
    if (selectedOrchidGroup) {
      if (mutationMode === "MOVE") {
        setMutationMode(null);
        return;
      }
      clearPasteSource();
      setMutationMode("MOVE");
      setErrorMessage(null);
    }
  }

  function openWorkRecord() {
    if (mutationMode === "WORK_RECORD") {
      setMutationMode(null);
      return;
    }
    const target = resolveWorkOperationTarget({
      house,
      resolvedZoneId: resolvedZone?.id ?? null,
      selectedOrchidGroupId: selectedOrchidGroup?.id ?? null,
      selection,
    });
    if (target.type === "MANUAL_SELECTION" && target.ids.length === 0) {
      setErrorMessage("현재 화면에 작업 대상으로 등록할 난 묶음이 없습니다.");
      return;
    }
    workRecord.prepare(target);
    setMutationMode("WORK_RECORD");
    clearPasteSource();
    setErrorMessage(null);
  }

  function moveToOrchidGroup(orchidGroup: OrchidGroup) {
    if (findOrchidGroup(navigationHouse, orchidGroup.id)) {
      setSelection({ type: "ORCHID_GROUP", orchidGroupId: orchidGroup.id });
      setMutationMode(null);
      clearPasteSource();
      return;
    }
  }

  async function handleCreate(payload: MutationPayload) {
    if (!payload.bedZoneId) {
      setErrorMessage("난 묶음을 추가할 위치를 선택하세요.");
      return;
    }
    const { bedZoneId, ...createPayload } = payload;
    await runMutation(async () =>
      createOrchidGroup({ ...createPayload, bedZoneId }),
    );
  }

  async function handleUpdate(payload: MutationPayload) {
    if (!selectedOrchidGroup) {
      setErrorMessage("보정할 난 묶음을 선택하세요.");
      return;
    }
    const { bedZoneId, startPosition, endPosition, ...detailsPayload } =
      payload;
    const movedToAnotherZone =
      bedZoneId != null && bedZoneId !== selectedOrchidGroup.bedZoneId;

    await runMutation(async () => {
      await updateOrchidGroup(selectedOrchidGroup.id, {
        ...detailsPayload,
        startPosition: movedToAnotherZone
          ? selectedOrchidGroup.startPosition
          : startPosition,
        endPosition: movedToAnotherZone
          ? selectedOrchidGroup.endPosition
          : endPosition,
      });

      if (movedToAnotherZone) {
        await moveOrchidGroup(selectedOrchidGroup.id, {
          toBedZoneId: bedZoneId,
          startPosition,
          endPosition,
          memo: "",
        });
      }
    });
  }

  async function handleMove(payload: PreciseMovePayload) {
    if (!selectedOrchidGroup) {
      setErrorMessage("이동할 난 묶음을 선택하세요.");
      return;
    }
    await runMutation(async () =>
      moveOrchidGroup(selectedOrchidGroup.id, payload),
    );
  }

  async function handleBatchUpdate(items: OrchidGroupBatchUpdateItem[]) {
    return runMutation(() => updateOrchidGroupsBatch(items));
  }

  async function handleDelete(orchidGroupId?: number) {
    const orchidGroup = orchidGroupId
      ? findOrchidGroup(navigationHouse, orchidGroupId)
      : selectedOrchidGroup;
    if (!orchidGroup) {
      return;
    }
    const confirmed = window.confirm(
      `${orchidGroup.varietyName} 난 묶음을 삭제할까요?`,
    );
    if (!confirmed) {
      return;
    }
    setSaving(true);
    setErrorMessage(null);
    try {
      await deleteOrchidGroup(orchidGroup.id);
      setSelection(
        resolvedZone ? { type: "BED_ZONE", bedZoneId: resolvedZone.id } : null,
      );
      setMutationMode(null);
      router.refresh();
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.",
      );
    } finally {
      setSaving(false);
    }
  }

  async function runMutation(action: () => Promise<void>) {
    setSaving(true);
    setErrorMessage(null);
    try {
      await action();
      history.invalidate();
      setMutationMode(null);
      clearPasteSource();
      router.refresh();
      return true;
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.",
      );
      return false;
    } finally {
      setSaving(false);
    }
  }

  return {
    errorMessage,
    copiedOrchidGroup,
    pasteSourceOrchidGroup,
    filteredOrchidGroupIds: search.filteredOrchidGroupIds,
    hasActiveSearch: search.active,
    listSelection: resolvedListSelection,
    mutationMode,
    resolvedZone,
    saving,
    searchFilters: search.filters,
    searchLoading: search.loading,
    searchResults: search.results,
    selectedBedZone,
    selectedPhysicalBed,
    selectedOrchidGroup,
    selection,
    workRecordForm: workRecord.form,
    workRecordSummary: history.summary,
    workRecordSummaryLoading: history.summaryLoading,
    orchidGroupHistory: history.history,
    orchidGroupHistoryLoading: history.historyLoading,
    orchidGroupHistoryPage: history.historyPage,
    orchidGroupHistoryPageLoading: history.historyPageLoading,
    orchidGroupLineage: history.lineage,
    orchidGroupLineageLoading: history.lineageLoading,
    actions: {
      cancelMutation: () => {
        setMutationMode(null);
        clearPasteSource();
      },
      clearCopiedOrchidGroup,
      copyOrchidGroup,
      create: handleCreate,
      delete: handleDelete,
      edit: handleUpdate,
      editBatch: handleBatchUpdate,
      moveToOrchidGroup,
      move: handleMove,
      openCreate,
      openMove,
      openPaste,
      openWorkRecord,
      selectBedZone,
      selectHouse,
      selectPhysicalBed,
      selectOrchidGroup,
      selectOrchidGroupOnMap,
      selectOrchidGroupForEdit,
      loadOrchidGroupHistoryPage: history.loadPage,
      updateSearchFilter: search.updateFilter,
      updateWorkRecordForm: workRecord.update,
      workRecordCreate: () =>
        workRecord.submit(async (action) => {
          await runMutation(action);
        }),
    },
  };
}

function createInitialSelections({
  house,
  navigationHouse,
  initialSelectedOrchidGroupId,
  initialSelectedPhysicalBedId,
  initialSelectedBedZoneId,
}: {
  house: House;
  navigationHouse: House;
  initialSelectedOrchidGroupId: number | null;
  initialSelectedPhysicalBedId: number | null;
  initialSelectedBedZoneId: number | null;
}): {
  selection: OrchidSelection;
  listSelection: OrchidListSelection;
} {
  const orchidGroup = initialSelectedOrchidGroupId
    ? findOrchidGroup(navigationHouse, initialSelectedOrchidGroupId)
    : null;
  if (orchidGroup) {
    return {
      selection: { type: "ORCHID_GROUP", orchidGroupId: orchidGroup.id },
      listSelection: { type: "BED_ZONE", bedZoneId: orchidGroup.bedZoneId },
    };
  }

  const bedZone = initialSelectedBedZoneId
    ? findBedZone(navigationHouse, initialSelectedBedZoneId)?.zone
    : null;
  if (bedZone) {
    return {
      selection: { type: "BED_ZONE", bedZoneId: bedZone.id },
      listSelection: { type: "BED_ZONE", bedZoneId: bedZone.id },
    };
  }

  const physicalBed = initialSelectedPhysicalBedId
    ? findPhysicalBed(navigationHouse, initialSelectedPhysicalBedId)
    : null;
  if (physicalBed) {
    return {
      selection: { type: "PHYSICAL_BED", physicalBedId: physicalBed.id },
      listSelection: { type: "PHYSICAL_BED", physicalBedId: physicalBed.id },
    };
  }

  return {
    selection: { type: "HOUSE", houseId: house.id },
    listSelection: { type: "HOUSE", houseId: house.id },
  };
}

function collectCurrentHouseOrchidGroupIds(house: House) {
  return new Set(
    house.physicalBeds.flatMap((bed) =>
      bed.bedZones.flatMap((zone) =>
        zone.orchidGroups.map((orchidGroup) => orchidGroup.id),
      ),
    ),
  );
}

function resolveWorkOperationTarget({
  house,
  resolvedZoneId,
  selectedOrchidGroupId,
  selection,
}: {
  house: House;
  resolvedZoneId: number | null;
  selectedOrchidGroupId: number | null;
  selection: OrchidSelection | null;
}): {
  type: WorkRecordTargetType | "MANUAL_SELECTION";
  id: number | null;
  ids: number[];
} {
  if (selectedOrchidGroupId) {
    return { type: "ORCHID_GROUP", id: selectedOrchidGroupId, ids: [] };
  }
  if (
    selection?.type === "BED_ZONE" &&
    resolvedZoneId &&
    findBedZone(house, resolvedZoneId)
  ) {
    return { type: "BED_ZONE", id: resolvedZoneId, ids: [] };
  }
  if (
    selection?.type === "PHYSICAL_BED" &&
    findPhysicalBed(house, selection.physicalBedId)
  ) {
    return { type: "PHYSICAL_BED", id: selection.physicalBedId, ids: [] };
  }
  if (selection?.type === "HOUSE") {
    return { type: "HOUSE", id: selection.houseId, ids: [] };
  }
  return {
    type: "MANUAL_SELECTION",
    id: null,
    ids: Array.from(collectCurrentHouseOrchidGroupIds(house)),
  };
}
