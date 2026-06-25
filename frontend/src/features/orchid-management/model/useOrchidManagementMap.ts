"use client";

import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import type { House, WorkRecord } from "@/entities/farm/types";
import {
  createOrchidGroup,
  createOrchidWorkRecord,
  deleteOrchidGroup,
  getOrchidWorkRecords,
  moveOrchidGroup,
  updateOrchidGroup,
} from "../api/orchidManagementApi";
import { findBedZone, findFirstOrchidGroup, findOrchidGroup } from "../lib/orchidManagementUtils";
import type { DragState, MutationMode, MutationPayload, OrchidSelection, WorkRecordQuickFormState, WorkRecordSummary } from "./types";

export function useOrchidManagementMap(house: House, workTypes: string[]) {
  const router = useRouter();
  const firstOrchidGroup = useMemo(() => findFirstOrchidGroup(house), [house]);

  const [selection, setSelection] = useState<OrchidSelection | null>(
    firstOrchidGroup ? { type: "ORCHID_GROUP", orchidGroupId: firstOrchidGroup.id } : null,
  );
  const [placementEditMode, setPlacementEditMode] = useState(false);
  const [dragState, setDragState] = useState<DragState>(null);
  const [mutationMode, setMutationMode] = useState<MutationMode>(null);
  const [workRecordForm, setWorkRecordForm] = useState<WorkRecordQuickFormState>(() => createInitialWorkRecordForm(workTypes, firstOrchidGroup?.id ?? null));
  const [workRecordSummary, setWorkRecordSummary] = useState<WorkRecordSummary>(() => createEmptyWorkRecordSummary());
  const [workRecordSummaryLoading, setWorkRecordSummaryLoading] = useState(false);
  const [workRecordSummaryVersion, setWorkRecordSummaryVersion] = useState(0);
  const [saving, setSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const selectedOrchidGroup = selection?.type === "ORCHID_GROUP" ? findOrchidGroup(house, selection.orchidGroupId) : null;
  const selectedBedZone = selection?.type === "BED_ZONE" ? findBedZone(house, selection.bedZoneId)?.zone ?? null : null;
  const resolvedZone = selectedOrchidGroup ? findBedZone(house, selectedOrchidGroup.bedZoneId)?.zone ?? null : selectedBedZone;

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
          ...resolvedZone.orchidGroups.map((orchidGroup) => getOrchidWorkRecords("ORCHID_GROUP", orchidGroup.id)),
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
      setMutationMode("MOVE");
      setErrorMessage(null);
    }
  }

  function openWorkRecord() {
    const targetType = selectedOrchidGroup ? "ORCHID_GROUP" : resolvedZone ? "BED_ZONE" : "HOUSE";
    const targetId = selectedOrchidGroup?.id ?? resolvedZone?.id ?? house.id;
    setWorkRecordForm((current) => ({
      ...current,
      workType: current.workType || workTypes[0] || "농약",
      targetType,
      targetId,
    }));
    setMutationMode("WORK_RECORD");
    setErrorMessage(null);
  }

  function updateWorkRecordForm<K extends keyof WorkRecordQuickFormState>(field: K, value: WorkRecordQuickFormState[K]) {
    setWorkRecordForm((current) => ({ ...current, [field]: value }));
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
    if (draggingGroup.bedZoneId === toBedZoneId) {
      setDragState(null);
      return;
    }
    const targetZone = findBedZone(house, toBedZoneId)?.zone;
    const confirmed = window.confirm(`${draggingGroup.varietyName} 난 묶음을 ${targetZone?.name ?? "선택 구역"}으로 이동할까요?`);
    if (!confirmed) {
      setDragState(null);
      return;
    }
    setSelection({ type: "ORCHID_GROUP", orchidGroupId: draggingGroup.id });
    await runMutation(async () => moveOrchidGroup(draggingGroup.id, toBedZoneId, "드래그 이동"));
    setDragState(null);
  }

  async function handleCreate(payload: MutationPayload) {
    if (!resolvedZone) {
      setErrorMessage("난 묶음을 추가할 구역을 선택하세요.");
      return;
    }
    await runMutation(async () => createOrchidGroup({ ...payload, bedZoneId: resolvedZone.id }));
  }

  async function handleUpdate(payload: MutationPayload) {
    if (!selectedOrchidGroup) {
      setErrorMessage("수정할 난 묶음을 선택하세요.");
      return;
    }
    await runMutation(async () => updateOrchidGroup(selectedOrchidGroup.id, payload));
  }

  async function handleMove(toBedZoneId: number, memo: string) {
    if (!selectedOrchidGroup) {
      setErrorMessage("이동할 난 묶음을 선택하세요.");
      return;
    }
    await runMutation(async () => moveOrchidGroup(selectedOrchidGroup.id, toBedZoneId, memo));
  }

  async function handleWorkRecordCreate() {
    await runMutation(async () => {
      await createOrchidWorkRecord({
        workType: workRecordForm.workType,
        workDate: workRecordForm.workDate,
        targetType: workRecordForm.targetType,
        targetId: workRecordForm.targetId,
        materialName: nullableText(workRecordForm.materialName),
        dilutionRatio: nullableText(workRecordForm.dilutionRatio),
        quantity: nullableText(workRecordForm.quantity),
        worker: nullableText(workRecordForm.worker),
        memo: nullableText(workRecordForm.memo),
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
    const confirmed = window.confirm(`${selectedOrchidGroup.varietyName} 난 묶음을 삭제할까요?`);
    if (!confirmed) {
      return;
    }
    setSaving(true);
    setErrorMessage(null);
    try {
      await deleteOrchidGroup(selectedOrchidGroup.id);
      setSelection(resolvedZone ? { type: "BED_ZONE", bedZoneId: resolvedZone.id } : null);
      setMutationMode(null);
      router.refresh();
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.");
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
      setErrorMessage(error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.");
    } finally {
      setSaving(false);
    }
  }

  return {
    errorMessage,
    dragState,
    mutationMode,
    placementEditMode,
    resolvedZone,
    saving,
    selectedBedZone,
    selectedOrchidGroup,
    selection,
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
      move: handleMove,
      openCreate,
      openEdit,
      openMove,
      openWorkRecord,
      selectBedZone,
      selectOrchidGroup,
      startDrag,
      togglePlacementEditMode,
      updateWorkRecordForm,
      workRecordCreate: handleWorkRecordCreate,
    },
  };
}

function createInitialWorkRecordForm(workTypes: string[], orchidGroupId: number | null): WorkRecordQuickFormState {
  return {
    workType: workTypes[0] ?? "농약",
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
      pesticide: sortedRecords.find((record) => record.workType === "농약") ?? null,
      fertilizer: sortedRecords.find((record) => record.workType === "비료") ?? null,
      repot: sortedRecords.find((record) => record.workType === "분갈이") ?? null,
    },
  };
}

function compareWorkRecordsDesc(a: WorkRecord, b: WorkRecord) {
  if (a.workDate !== b.workDate) {
    return b.workDate.localeCompare(a.workDate);
  }
  return b.id - a.id;
}

