"use client";

import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import type {
  House,
  OrchidGroup,
  WorkRecord,
  WorkType,
} from "@/entities/farm/types";
import {
  findWorkType,
  getManualWorkTypes,
  isVisibleWorkRecordField,
} from "@/entities/farm/workTypes";
import {
  createOrchidGroup,
  createOrchidWorkRecord,
  deleteOrchidGroup,
  getOrchidWorkRecords,
  moveOrchidGroup,
  searchOrchidGroups,
  updateOrchidGroup,
} from "../api/orchidManagementApi";
import {
  findBedZone,
  findFirstOrchidGroup,
  findOrchidGroup,
} from "../lib/orchidManagementUtils";
import type {
  DragState,
  OrchidManagementSearchState,
  MutationMode,
  MutationPayload,
  OrchidSelection,
  PreciseMovePayload,
  WorkRecordQuickFormState,
  WorkRecordSummary,
} from "./types";

export function useOrchidManagementMap(
  house: House,
  workTypes: WorkType[],
  initialSelectedOrchidGroupId: number | null,
) {
  const router = useRouter();
  const firstOrchidGroup = useMemo(() => findFirstOrchidGroup(house), [house]);
  const initialOrchidGroup = useMemo(
    () =>
      initialSelectedOrchidGroupId
        ? findOrchidGroup(house, initialSelectedOrchidGroupId)
        : null,
    [house, initialSelectedOrchidGroupId],
  );

  const [selection, setSelection] = useState<OrchidSelection | null>(
    initialOrchidGroup
      ? { type: "ORCHID_GROUP", orchidGroupId: initialOrchidGroup.id }
      : firstOrchidGroup
        ? { type: "ORCHID_GROUP", orchidGroupId: firstOrchidGroup.id }
        : null,
  );
  const [placementEditMode, setPlacementEditMode] = useState(false);
  const [dragState, setDragState] = useState<DragState>(null);
  const [mutationMode, setMutationMode] = useState<MutationMode>(null);
  const [searchFilters, setSearchFilters] =
    useState<OrchidManagementSearchState>({
      keyword: "",
      status: "",
    });
  const [searchResults, setSearchResults] = useState<OrchidGroup[]>([]);
  const [searchLoading, setSearchLoading] = useState(false);
  const [preferredMoveZoneId, setPreferredMoveZoneId] = useState<number | null>(
    null,
  );
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
  const [saving, setSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const selectedOrchidGroup =
    selection?.type === "ORCHID_GROUP"
      ? findOrchidGroup(house, selection.orchidGroupId)
      : null;
  const selectedBedZone =
    selection?.type === "BED_ZONE"
      ? (findBedZone(house, selection.bedZoneId)?.zone ?? null)
      : null;
  const resolvedZone = selectedOrchidGroup
    ? (findBedZone(house, selectedOrchidGroup.bedZoneId)?.zone ?? null)
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
        .filter((orchidGroup) => orchidGroup.houseNumber === house.number)
        .map((orchidGroup) => orchidGroup.id),
    );
  }, [
    currentHouseOrchidGroupIds,
    hasActiveSearch,
    house.number,
    searchResults,
  ]);

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

  useEffect(() => {
    let ignore = false;

    async function loadWorkRecordSummary() {
      if (!resolvedZone) {
        setWorkRecordSummary(createEmptyWorkRecordSummary());
        return;
      }

      setWorkRecordSummaryLoading(true);
      try {
        const recordGroups = await Promise.all([
          getOrchidWorkRecords("BED_ZONE", resolvedZone.id),
          ...resolvedZone.orchidGroups.map((orchidGroup) =>
            getOrchidWorkRecords("ORCHID_GROUP", orchidGroup.id),
          ),
        ]);
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
  }, [resolvedZone, workRecordSummaryVersion]);

  function selectBedZone(bedZoneId: number) {
    setSelection({ type: "BED_ZONE", bedZoneId });
    setMutationMode(null);
  }

  function selectOrchidGroup(orchidGroupId: number) {
    setSelection({ type: "ORCHID_GROUP", orchidGroupId });
    setMutationMode(null);
  }

  function openCreate() {
    if (resolvedZone) {
      setMutationMode("CREATE");
      setErrorMessage(null);
      return;
    }
    setErrorMessage("먼저 구역을 선택하세요.");
  }

  function openEdit() {
    if (selectedOrchidGroup) {
      setMutationMode("EDIT");
      setErrorMessage(null);
    }
  }

  function openMove() {
    if (selectedOrchidGroup) {
      setPreferredMoveZoneId(null);
      setMutationMode("MOVE");
      setErrorMessage(null);
    }
  }

  function openWorkRecord() {
    const targetType = selectedOrchidGroup
      ? "ORCHID_GROUP"
      : resolvedZone
        ? "BED_ZONE"
        : "HOUSE";
    const targetId = selectedOrchidGroup?.id ?? resolvedZone?.id ?? house.id;
    setWorkRecordForm((current) => ({
      ...current,
      workTypeId:
        current.workTypeId ||
        String(getManualWorkTypes(workTypes)[0]?.id ?? workTypes[0]?.id ?? ""),
      targetType,
      targetId,
    }));
    setMutationMode("WORK_RECORD");
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
    router.push(
      `/orchid-groups?houseId=${orchidGroup.houseId}&orchidGroupId=${orchidGroup.id}`,
    );
  }

  function togglePlacementEditMode() {
    setPlacementEditMode((current) => !current);
    setDragState(null);
    setMutationMode(null);
  }

  function startDrag(orchidGroupId: number) {
    if (!placementEditMode || saving) {
      return;
    }
    setSelection({ type: "ORCHID_GROUP", orchidGroupId });
    setDragState({ orchidGroupId, overBedZoneId: null });
    setErrorMessage(null);
  }

  function enterDropZone(bedZoneId: number) {
    if (!dragState) {
      return;
    }
    setDragState({ ...dragState, overBedZoneId: bedZoneId });
  }

  function endDrag() {
    setDragState(null);
  }

  async function dropOnBedZone(toBedZoneId: number) {
    if (!dragState) {
      return;
    }
    const draggingGroup = findOrchidGroup(house, dragState.orchidGroupId);
    if (!draggingGroup) {
      setDragState(null);
      return;
    }
    setSelection({ type: "ORCHID_GROUP", orchidGroupId: draggingGroup.id });
    setPreferredMoveZoneId(toBedZoneId);
    setMutationMode("MOVE");
    setDragState(null);
  }

  async function handleCreate(payload: MutationPayload) {
    if (!resolvedZone) {
      setErrorMessage("난 묶음을 추가할 구역을 선택하세요.");
      return;
    }
    await runMutation(async () =>
      createOrchidGroup({ ...payload, bedZoneId: resolvedZone.id }),
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
      await createOrchidWorkRecord({
        workTypeId: Number(workRecordForm.workTypeId),
        workDate: workRecordForm.workDate,
        targetType: workRecordForm.targetType,
        targetId: workRecordForm.targetId,
        ...toVisibleWorkRecordFields(workRecordForm, workTypes),
      });
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
    dragState,
    filteredOrchidGroupIds,
    hasActiveSearch,
    mutationMode,
    preferredMoveZoneId,
    placementEditMode,
    resolvedZone,
    saving,
    searchFilters,
    searchLoading,
    searchResults,
    selectedBedZone,
    selectedOrchidGroup,
    selection,
    totalOrchidGroupCount,
    workRecordForm,
    workRecordSummary,
    workRecordSummaryLoading,
    actions: {
      cancelMutation: () => setMutationMode(null),
      create: handleCreate,
      delete: handleDelete,
      dropOnBedZone,
      edit: handleUpdate,
      endDrag,
      enterDropZone,
      moveToOrchidGroup,
      move: handleMove,
      openCreate,
      openEdit,
      openMove,
      openWorkRecord,
      resetSearch,
      selectBedZone,
      selectOrchidGroup,
      startDrag,
      togglePlacementEditMode,
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

function createWorkRecordSummary(records: WorkRecord[]): WorkRecordSummary {
  const sortedRecords = [...records].sort(compareWorkRecordsDesc);

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

function compareWorkRecordsDesc(a: WorkRecord, b: WorkRecord) {
  if (a.workDate !== b.workDate) {
    return b.workDate.localeCompare(a.workDate);
  }
  return b.id - a.id;
}
