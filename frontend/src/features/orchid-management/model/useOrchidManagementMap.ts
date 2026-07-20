"use client";

import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import type {
  House,
  OrchidGroup,
  OrchidGroupWorkHistory,
  WorkRecordTargetType,
  WorkType,
} from "@/entities/farm/types";
import {
  findWorkType,
  getManualWorkTypes,
  isVisibleWorkRecordField,
} from "@/entities/farm/workTypes";
import {
  createOrchidGroup,
  createOrchidWorkOperation,
  deleteOrchidGroup,
  getOrchidGroupLineage,
  getOrchidGroupWorkHistory,
  moveOrchidGroup,
  searchOrchidGroups,
  updateOrchidGroup,
} from "../api/orchidManagementApi";
import {
  findBedZone,
  findFirstOrchidGroup,
  findOrchidGroup,
  findPhysicalBed,
} from "../lib/orchidManagementUtils";
import { useOrchidClipboard } from "./OrchidClipboardContext";
import type {
  OrchidManagementSearchState,
  MutationMode,
  MutationPayload,
  OrchidListSelection,
  OrchidGroupLineage,
  OrchidSelection,
  PreciseMovePayload,
  WorkRecordQuickFormState,
  WorkRecordSummary,
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
  const firstOrchidGroup = useMemo(() => findFirstOrchidGroup(house), [house]);
  const initialOrchidGroup = useMemo(
    () =>
      initialSelectedOrchidGroupId
        ? findOrchidGroup(navigationHouse, initialSelectedOrchidGroupId)
        : null,
    [initialSelectedOrchidGroupId, navigationHouse],
  );
  const initialPhysicalBed = useMemo(
    () =>
      initialSelectedPhysicalBedId
        ? findPhysicalBed(navigationHouse, initialSelectedPhysicalBedId)
        : null,
    [initialSelectedPhysicalBedId, navigationHouse],
  );
  const initialBedZone = useMemo(
    () =>
      initialSelectedBedZoneId
        ? (findBedZone(navigationHouse, initialSelectedBedZoneId)?.zone ?? null)
        : null,
    [initialSelectedBedZoneId, navigationHouse],
  );

  const [selection, setSelection] = useState<OrchidSelection | null>(
    initialOrchidGroup
      ? { type: "ORCHID_GROUP", orchidGroupId: initialOrchidGroup.id }
      : initialBedZone
        ? { type: "BED_ZONE", bedZoneId: initialBedZone.id }
        : initialPhysicalBed
          ? { type: "PHYSICAL_BED", physicalBedId: initialPhysicalBed.id }
          : { type: "HOUSE", houseId: house.id },
  );
  const [listSelection, setListSelection] = useState<OrchidListSelection>(() =>
    initialOrchidGroup
      ? { type: "BED_ZONE", bedZoneId: initialOrchidGroup.bedZoneId }
      : initialBedZone
        ? { type: "BED_ZONE", bedZoneId: initialBedZone.id }
        : initialPhysicalBed
          ? { type: "PHYSICAL_BED", physicalBedId: initialPhysicalBed.id }
          : { type: "HOUSE", houseId: house.id },
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
  const [searchFilters, setSearchFilters] =
    useState<OrchidManagementSearchState>({
      keyword: initialSearchFilters?.keyword ?? "",
      status: initialSearchFilters?.status ?? "",
    });
  const [searchResults, setSearchResults] = useState<OrchidGroup[]>([]);
  const [searchLoading, setSearchLoading] = useState(false);
  const [workRecordForm, setWorkRecordForm] =
    useState<WorkRecordQuickFormState>(() =>
      createInitialWorkRecordForm(workTypes, firstOrchidGroup?.id ?? null),
    );
  const [workRecordSummary, setWorkRecordSummary] = useState<WorkRecordSummary>(
    () => createEmptyWorkRecordSummary(),
  );
  const [workRecordSummaryLoading, setWorkRecordSummaryLoading] =
    useState(false);
  const [workRecordSummaryVersion, setWorkRecordSummaryVersion] = useState(0);
  const [orchidGroupHistoryState, setOrchidGroupHistoryState] = useState<{
    orchidGroupId: number;
    items: OrchidGroupWorkHistory[];
  } | null>(null);
  const [orchidGroupLineageState, setOrchidGroupLineageState] = useState<{
    orchidGroupId: number;
    item: OrchidGroupLineage;
  } | null>(null);
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
  const currentHouseOrchidGroupIds = useMemo(
    () => collectCurrentHouseOrchidGroupIds(house),
    [house],
  );
  const hasActiveSearch = useMemo(
    () =>
      searchFilters.keyword.trim().length > 0 ||
      searchFilters.status.length > 0,
    [searchFilters],
  );
  const filteredOrchidGroupIds = useMemo(() => {
    if (!hasActiveSearch) {
      return currentHouseOrchidGroupIds;
    }
    return new Set(
      searchResults
        .filter((orchidGroup) => currentHouseOrchidGroupIds.has(orchidGroup.id))
        .map((orchidGroup) => orchidGroup.id),
    );
  }, [currentHouseOrchidGroupIds, hasActiveSearch, searchResults]);

  useEffect(() => {
    let ignore = false;

    async function loadSearchResults() {
      if (!hasActiveSearch) {
        setSearchResults([]);
        setSearchLoading(false);
        return;
      }

      setSearchLoading(true);
      try {
        const results = await searchOrchidGroups(searchFilters);
        if (!ignore) {
          setSearchResults(results);
        }
      } catch {
        if (!ignore) {
          setSearchResults([]);
        }
      } finally {
        if (!ignore) {
          setSearchLoading(false);
        }
      }
    }

    const timeout = window.setTimeout(() => {
      void loadSearchResults();
    }, 250);

    return () => {
      ignore = true;
      window.clearTimeout(timeout);
    };
  }, [hasActiveSearch, searchFilters]);

  const totalOrchidGroupCount = useMemo(
    () => currentHouseOrchidGroupIds.size,
    [currentHouseOrchidGroupIds],
  );
  const workRecordSummaryOrchidGroupIds = useMemo(
    () =>
      resolveSummaryOrchidGroupIds({
        house,
        resolvedZone,
        selectedOrchidGroup,
        selectedPhysicalBed,
        selection,
      }),
    [house, resolvedZone, selectedOrchidGroup, selectedPhysicalBed, selection],
  );

  useEffect(() => {
    let ignore = false;

    async function loadWorkRecordSummary() {
      if (workRecordSummaryOrchidGroupIds.length === 0) {
        setWorkRecordSummary(createEmptyWorkRecordSummary());
        setWorkRecordSummaryLoading(false);
        return;
      }

      setWorkRecordSummaryLoading(true);
      try {
        const recordGroups = await Promise.all(
          workRecordSummaryOrchidGroupIds.map(getOrchidGroupWorkHistory),
        );
        if (!ignore) {
          setWorkRecordSummary(createWorkRecordSummary(recordGroups.flat()));
        }
      } catch {
        if (!ignore) {
          setWorkRecordSummary(createEmptyWorkRecordSummary());
        }
      } finally {
        if (!ignore) {
          setWorkRecordSummaryLoading(false);
        }
      }
    }

    void loadWorkRecordSummary();

    return () => {
      ignore = true;
    };
  }, [workRecordSummaryOrchidGroupIds, workRecordSummaryVersion]);

  useEffect(() => {
    let ignore = false;
    if (!selectedOrchidGroup) {
      return;
    }

    void getOrchidGroupWorkHistory(selectedOrchidGroup.id)
      .then((history) => {
        if (!ignore) {
          setOrchidGroupHistoryState({
            orchidGroupId: selectedOrchidGroup.id,
            items: history,
          });
        }
      })
      .catch(() => {
        if (!ignore) {
          setOrchidGroupHistoryState({
            orchidGroupId: selectedOrchidGroup.id,
            items: [],
          });
        }
      });

    return () => {
      ignore = true;
    };
  }, [selectedOrchidGroup, workRecordSummaryVersion]);

  useEffect(() => {
    let ignore = false;
    if (!selectedOrchidGroup) return;

    void getOrchidGroupLineage(selectedOrchidGroup.id)
      .then((lineage) => {
        if (!ignore) {
          setOrchidGroupLineageState({
            orchidGroupId: selectedOrchidGroup.id,
            item: lineage,
          });
        }
      })
      .catch(() => {
        if (!ignore) {
          setOrchidGroupLineageState({
            orchidGroupId: selectedOrchidGroup.id,
            item: {
              orchidGroupId: selectedOrchidGroup.id,
              sources: [],
              results: [],
            },
          });
        }
      });

    return () => {
      ignore = true;
    };
  }, [selectedOrchidGroup, workRecordSummaryVersion]);

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

  function selectHouse() {
    setSelection({ type: "HOUSE", houseId: house.id });
    setListSelection({ type: "HOUSE", houseId: house.id });
    setMutationMode(null);
    clearPasteSource();
  }

  function selectOrchidGroup(orchidGroupId: number) {
    setSelection({ type: "ORCHID_GROUP", orchidGroupId });
    setMutationMode(null);
    clearPasteSource();
  }

  function selectOrchidGroupForEdit(orchidGroupId: number) {
    setSelection({ type: "ORCHID_GROUP", orchidGroupId });
    setMutationMode("EDIT");
    clearPasteSource();
    setErrorMessage(null);
  }

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

  function openEdit() {
    if (selectedOrchidGroup) {
      if (mutationMode === "EDIT") {
        setMutationMode(null);
        return;
      }
      clearPasteSource();
      setMutationMode("EDIT");
      setErrorMessage(null);
    }
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
    setWorkRecordForm((current) => ({
      ...current,
      workTypeId:
        current.workTypeId ||
        String(getManualWorkTypes(workTypes)[0]?.id ?? workTypes[0]?.id ?? ""),
      targetType: target.type,
      targetId: target.id,
      targetIds: target.ids,
    }));
    setMutationMode("WORK_RECORD");
    clearPasteSource();
    setErrorMessage(null);
  }

  function updateWorkRecordForm<K extends keyof WorkRecordQuickFormState>(
    field: K,
    value: WorkRecordQuickFormState[K],
  ) {
    setWorkRecordForm((current) => ({ ...current, [field]: value }));
  }

  function updateSearchFilter<K extends keyof OrchidManagementSearchState>(
    field: K,
    value: OrchidManagementSearchState[K],
  ) {
    setSearchFilters((current) => ({ ...current, [field]: value }));
  }

  function resetSearch() {
    setSearchFilters({
      keyword: "",
      status: "",
    });
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
      setErrorMessage("수정할 난 묶음을 선택하세요.");
      return;
    }
    await runMutation(async () =>
      updateOrchidGroup(selectedOrchidGroup.id, payload),
    );
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

  async function handleWorkRecordCreate() {
    await runMutation(async () => {
      const workTypeId = Number(workRecordForm.workTypeId);
      const workType = workTypes.find((item) => item.id === workTypeId);
      await createOrchidWorkOperation(
        {
          workTypeId,
          workDate: workRecordForm.workDate,
          targetType: workRecordForm.targetType,
          targetId: workRecordForm.targetId,
          targetIds: workRecordForm.targetIds,
          ...toVisibleWorkRecordFields(workRecordForm, workTypes),
        },
        workType?.name ?? "작업",
      );
      setWorkRecordForm((current) => ({
        ...current,
        materialName: "",
        dilutionRatio: "",
        quantity: "",
        memo: "",
      }));
      setWorkRecordSummaryVersion((current) => current + 1);
    });
  }

  async function handleDelete() {
    if (!selectedOrchidGroup) {
      return;
    }
    const confirmed = window.confirm(
      `${selectedOrchidGroup.varietyName} 난 묶음을 삭제할까요?`,
    );
    if (!confirmed) {
      return;
    }
    setSaving(true);
    setErrorMessage(null);
    try {
      await deleteOrchidGroup(selectedOrchidGroup.id);
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
      setMutationMode(null);
      clearPasteSource();
      router.refresh();
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.",
      );
    } finally {
      setSaving(false);
    }
  }

  return {
    errorMessage,
    copiedOrchidGroup,
    pasteSourceOrchidGroup,
    filteredOrchidGroupIds,
    hasActiveSearch,
    listSelection: resolvedListSelection,
    mutationMode,
    resolvedZone,
    saving,
    searchFilters,
    searchLoading,
    searchResults,
    selectedBedZone,
    selectedPhysicalBed,
    selectedOrchidGroup,
    selection,
    totalOrchidGroupCount,
    workRecordForm,
    workRecordSummary,
    workRecordSummaryLoading,
    orchidGroupHistory:
      selectedOrchidGroup &&
      orchidGroupHistoryState?.orchidGroupId === selectedOrchidGroup.id
        ? orchidGroupHistoryState.items
        : [],
    orchidGroupHistoryLoading:
      Boolean(selectedOrchidGroup) &&
      orchidGroupHistoryState?.orchidGroupId !== selectedOrchidGroup?.id,
    orchidGroupLineage:
      selectedOrchidGroup &&
      orchidGroupLineageState?.orchidGroupId === selectedOrchidGroup.id
        ? orchidGroupLineageState.item
        : null,
    orchidGroupLineageLoading:
      Boolean(selectedOrchidGroup) &&
      orchidGroupLineageState?.orchidGroupId !== selectedOrchidGroup?.id,
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
      moveToOrchidGroup,
      move: handleMove,
      openCreate,
      openEdit,
      openMove,
      openPaste,
      openWorkRecord,
      resetSearch,
      selectBedZone,
      selectHouse,
      selectPhysicalBed,
      selectOrchidGroup,
      selectOrchidGroupForEdit,
      updateSearchFilter,
      updateWorkRecordForm,
      workRecordCreate: handleWorkRecordCreate,
    },
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
  return {
    type: "MANUAL_SELECTION",
    id: null,
    ids: Array.from(collectCurrentHouseOrchidGroupIds(house)),
  };
}

function resolveSummaryOrchidGroupIds({
  house,
  resolvedZone,
  selectedOrchidGroup,
  selectedPhysicalBed,
  selection,
}: {
  house: House;
  resolvedZone: House["physicalBeds"][number]["bedZones"][number] | null;
  selectedOrchidGroup: OrchidGroup | null;
  selectedPhysicalBed: House["physicalBeds"][number] | null;
  selection: OrchidSelection | null;
}): number[] {
  if (selectedOrchidGroup) {
    return [selectedOrchidGroup.id];
  }
  if (selection?.type === "BED_ZONE" && resolvedZone) {
    return resolvedZone.orchidGroups.map((group) => group.id);
  }
  if (selection?.type === "PHYSICAL_BED" && selectedPhysicalBed) {
    return selectedPhysicalBed.bedZones.flatMap((zone) =>
      zone.orchidGroups.map((group) => group.id),
    );
  }
  return house.physicalBeds.flatMap((bed) =>
    bed.bedZones.flatMap((zone) => zone.orchidGroups.map((group) => group.id)),
  );
}

function createInitialWorkRecordForm(
  workTypes: WorkType[],
  orchidGroupId: number | null,
): WorkRecordQuickFormState {
  const firstWorkType = getManualWorkTypes(workTypes)[0] ?? workTypes[0];

  return {
    workTypeId: firstWorkType ? String(firstWorkType.id) : "",
    workDate: new Date().toISOString().slice(0, 10),
    targetType: orchidGroupId ? "ORCHID_GROUP" : "HOUSE",
    targetId: orchidGroupId,
    targetIds: [],
    materialName: "",
    dilutionRatio: "",
    quantity: "",
    worker: "",
    memo: "",
  };
}

function toVisibleWorkRecordFields(
  form: WorkRecordQuickFormState,
  workTypes: WorkType[],
) {
  const workType = findWorkType(workTypes, Number(form.workTypeId));
  const template = workType?.template ?? null;

  return {
    materialName: isVisibleWorkRecordField(template, "materialName")
      ? nullableText(form.materialName)
      : null,
    dilutionRatio: isVisibleWorkRecordField(template, "dilutionRatio")
      ? nullableText(form.dilutionRatio)
      : null,
    quantity: isVisibleWorkRecordField(template, "quantity")
      ? nullableText(form.quantity)
      : null,
    worker: isVisibleWorkRecordField(template, "worker")
      ? nullableText(form.worker)
      : null,
    memo: isVisibleWorkRecordField(template, "memo")
      ? nullableText(form.memo)
      : null,
  };
}

function nullableText(value: string) {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function createEmptyWorkRecordSummary(): WorkRecordSummary {
  return {
    latestRecords: [],
    latestByType: {
      pesticide: null,
      fertilizer: null,
      repot: null,
    },
  };
}

function createWorkRecordSummary(
  records: OrchidGroupWorkHistory[],
): WorkRecordSummary {
  const uniqueRecords = new Map<string, OrchidGroupWorkHistory>();
  records.forEach((record) => {
    const key = `${record.sourceKind}-${record.workOperationId ?? record.legacyWorkRecordId}`;
    if (!uniqueRecords.has(key)) {
      uniqueRecords.set(key, record);
    }
  });
  const sortedRecords = [...uniqueRecords.values()].sort(
    compareWorkRecordsDesc,
  );

  return {
    latestRecords: sortedRecords.slice(0, 5),
    latestByType: {
      pesticide:
        sortedRecords.find((record) => record.workType === "농약") ?? null,
      fertilizer:
        sortedRecords.find((record) => record.workType === "비료") ?? null,
      repot:
        sortedRecords.find((record) => record.workType === "분갈이") ?? null,
    },
  };
}

function compareWorkRecordsDesc(
  a: OrchidGroupWorkHistory,
  b: OrchidGroupWorkHistory,
) {
  if (a.workDate !== b.workDate) {
    return b.workDate.localeCompare(a.workDate);
  }
  return (
    (b.workOperationId ?? b.legacyWorkRecordId ?? 0) -
    (a.workOperationId ?? a.legacyWorkRecordId ?? 0)
  );
}
