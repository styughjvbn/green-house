"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import type { OrchidGroup, WorkOperation } from "@/entities/farm/types";
import type { FarmPlacementSelection } from "@/entities/farm/model/placement";
import {
  executeStructureChangeWorkOperation,
  getOrchidGroups,
  type StructureChangeExecutionPayload,
} from "../../../api/workRecordApi";
import { localDateValue } from "../../../lib/localDateValue";
import {
  collectPriorResultOrchidGroupIds,
  createExecutionPayload,
  inferPlacement,
  inferReleasedPlacement,
  newResultRow,
  savedResultReferencePlacements,
  type ResultRow,
  type StructureChangeOperation,
  validateExecution,
} from "./structureChangeExecutionModel";

export function useStructureChangeExecution({
  closeAfterSubmit,
  onClose,
  onRecordDirty,
  onSaved,
  onSubmitRecord,
  operation,
  orchidGroups,
  recordMode,
}: {
  closeAfterSubmit: boolean;
  onClose: () => void;
  onRecordDirty?: () => void;
  onSaved?: (operation: WorkOperation) => void;
  onSubmitRecord?: (payload: StructureChangeExecutionPayload) => Promise<void>;
  operation: StructureChangeOperation;
  orchidGroups: OrchidGroup[];
  recordMode: boolean;
}) {
  const priorResultOrchidGroupIds = useMemo(
    () => collectPriorResultOrchidGroupIds(operation),
    [operation],
  );
  const [fetchedOrchidGroups, setFetchedOrchidGroups] = useState<OrchidGroup[]>(
    [],
  );
  const orchidGroupsById = useMemo(
    () =>
      new Map(
        [...orchidGroups, ...fetchedOrchidGroups].map((group) => [
          group.id,
          group,
        ]),
      ),
    [fetchedOrchidGroups, orchidGroups],
  );
  const availableSources = useMemo(
    () =>
      operation.targets.flatMap((target) => {
        if (
          target.orchidGroupId == null ||
          target.remainingQuantity < 1 ||
          target.executionStatus === "SKIPPED" ||
          target.executionStatus === "CANCELED"
        ) {
          return [];
        }
        const group = orchidGroupsById.get(target.orchidGroupId);
        if (!group) return [];
        return [
          {
            group,
            target,
            inferredQuantity: Math.min(
              group.quantity,
              target.remainingQuantity,
            ),
          },
        ];
      }),
    [operation.targets, orchidGroupsById],
  );
  const missingPriorResultIdKey = useMemo(
    () =>
      priorResultOrchidGroupIds
        .filter((id) => !orchidGroupsById.has(id))
        .sort((left, right) => left - right)
        .join(","),
    [orchidGroupsById, priorResultOrchidGroupIds],
  );
  const savedResultReferences = useMemo(
    () =>
      savedResultReferencePlacements(
        priorResultOrchidGroupIds.flatMap((id) => {
          const group = orchidGroupsById.get(id);
          return group ? [group] : [];
        }),
      ),
    [orchidGroupsById, priorResultOrchidGroupIds],
  );
  const [selectedSourceIds, setSelectedSourceIds] = useState<Set<number>>(
    () => new Set(availableSources.map(({ group }) => group.id)),
  );
  const [inputQuantities, setInputQuantities] = useState<
    Record<number, string>
  >(() =>
    Object.fromEntries(
      availableSources.map(({ group, inferredQuantity }) => [
        group.id,
        String(inferredQuantity),
      ]),
    ),
  );
  const [releasedPlacements, setReleasedPlacements] = useState<
    Record<number, FarmPlacementSelection | null>
  >(() =>
    Object.fromEntries(availableSources.map(({ group }) => [group.id, null])),
  );
  const [rows, setRows] = useState<ResultRow[]>(() =>
    availableSources.map(({ group, inferredQuantity }) =>
      newResultRow(group, inferredQuantity),
    ),
  );
  const today = localDateValue(new Date());
  const [completedDate, setCompletedDate] = useState(
    recordMode ? operation.plannedStartDate : today,
  );
  const [worker, setWorker] = useState(operation.worker ?? "");
  const [memo, setMemo] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const selectedSources = availableSources.filter(({ group }) =>
    selectedSourceIds.has(group.id),
  );
  const totalInput = selectedSources.reduce(
    (sum, { group }) => sum + Number(inputQuantities[group.id] || 0),
    0,
  );
  const totalResult = rows.reduce(
    (sum, row) => sum + Number(row.quantity || 0),
    0,
  );
  const commonPotSize =
    new Set(rows.map((row) => row.potSize)).size === 1
      ? (rows[0]?.potSize ?? "")
      : "";
  const commonAgeYear =
    new Set(rows.map((row) => row.ageYear)).size === 1
      ? (rows[0]?.ageYear ?? "")
      : "";
  const onRecordDirtyRef = useRef(onRecordDirty);

  useEffect(() => {
    onRecordDirtyRef.current = onRecordDirty;
  }, [onRecordDirty]);

  useEffect(() => {
    if (recordMode) {
      onRecordDirtyRef.current?.();
    }
  }, [
    completedDate,
    inputQuantities,
    memo,
    recordMode,
    releasedPlacements,
    rows,
    worker,
  ]);

  useEffect(() => {
    const missingIds = missingPriorResultIdKey
      ? missingPriorResultIdKey.split(",").map(Number)
      : [];
    if (missingIds.length === 0) return;
    let cancelled = false;
    void getOrchidGroups()
      .then((groups) => {
        if (cancelled) return;
        const missingIdSet = new Set(missingIds);
        setFetchedOrchidGroups((current) => {
          const currentIds = new Set(current.map((group) => group.id));
          const additions = groups.filter(
            (group) => missingIdSet.has(group.id) && !currentIds.has(group.id),
          );
          return additions.length === 0 ? current : [...current, ...additions];
        });
      })
      .catch(() => {
        if (!cancelled) {
          setFetchedOrchidGroups((current) => current);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [missingPriorResultIdKey]);

  function toggleSource(group: OrchidGroup) {
    const selected = selectedSourceIds.has(group.id);
    setSelectedSourceIds((current) => {
      const next = new Set(current);
      if (selected) next.delete(group.id);
      else next.add(group.id);
      return next;
    });
    if (selected) {
      setRows((current) =>
        current.filter(
          (row) =>
            row.sourceOrchidGroupIds.length !== 1 ||
            row.sourceOrchidGroupIds[0] !== group.id,
        ),
      );
    } else {
      const quantity = Number(inputQuantities[group.id] || group.quantity);
      setRows((current) => [...current, newResultRow(group, quantity)]);
    }
  }

  function changeInputQuantity(groupId: number, value: string) {
    const source = availableSources.find(
      ({ group }) => group.id === groupId,
    )?.group;
    const released = source
      ? inferReleasedPlacement(source, Number(value))
      : null;
    setInputQuantities((current) => ({ ...current, [groupId]: value }));
    setReleasedPlacements((current) => ({
      ...current,
      [groupId]: released,
    }));
    setRows((current) =>
      current.map((row) =>
        row.autoQuantity &&
        row.sourceOrchidGroupIds.length === 1 &&
        row.sourceOrchidGroupIds[0] === groupId
          ? {
              ...row,
              quantity: value,
              placement:
                Number(value) < (source?.quantity ?? 0)
                  ? released
                  : source
                    ? inferPlacement(source)
                    : row.placement,
            }
          : row,
      ),
    );
  }

  function changeReleasedPlacement(
    groupId: number,
    placement: FarmPlacementSelection,
  ) {
    setReleasedPlacements((current) => ({
      ...current,
      [groupId]: placement,
    }));
    setRows((current) =>
      current.map((row) =>
        row.autoQuantity &&
        row.sourceOrchidGroupIds.length === 1 &&
        row.sourceOrchidGroupIds[0] === groupId
          ? { ...row, placement }
          : row,
      ),
    );
  }

  function patchRow(key: string, patch: Partial<ResultRow>) {
    setRows((current) =>
      current.map((row) => (row.key === key ? { ...row, ...patch } : row)),
    );
  }

  function addResult() {
    const donor = [...rows]
      .filter((row) => Number(row.quantity) > 1)
      .sort((left, right) => Number(right.quantity) - Number(left.quantity))[0];
    const sourceId = donor?.sourceOrchidGroupIds[0];
    const source =
      availableSources.find(({ group }) => group.id === sourceId)?.group ??
      selectedSources[0]?.group;
    if (!source) return;
    setRows((current) => [
      ...current.map((row) =>
        row.key === donor?.key
          ? { ...row, quantity: String(Number(row.quantity) - 1) }
          : row,
      ),
      {
        ...newResultRow(source, 1),
        placement: null,
        sourceOrchidGroupIds: donor?.sourceOrchidGroupIds ?? [source.id],
        autoQuantity: false,
      },
    ]);
  }

  function removeResult(removed: ResultRow) {
    setRows((current) => {
      const removedIndex = current.findIndex((row) => row.key === removed.key);
      const next = current.filter((row) => row.key !== removed.key);
      if (next.length === 0) return current;
      const receiverIndex = next.findIndex((row) =>
        row.sourceOrchidGroupIds.some((id) =>
          removed.sourceOrchidGroupIds.includes(id),
        ),
      );
      const fallbackReceiverIndex = Math.max(
        0,
        Math.min(removedIndex - 1, next.length - 1),
      );
      const targetIndex =
        receiverIndex >= 0 ? receiverIndex : fallbackReceiverIndex;
      next[targetIndex] = {
        ...next[targetIndex],
        sourceOrchidGroupIds: [
          ...new Set([
            ...next[targetIndex].sourceOrchidGroupIds,
            ...removed.sourceOrchidGroupIds,
          ]),
        ],
        quantity: String(
          Number(next[targetIndex].quantity) + Number(removed.quantity),
        ),
        autoQuantity: false,
      };
      return next;
    });
  }

  function setAllPotSizes(potSize: string) {
    setRows((current) => current.map((row) => ({ ...row, potSize })));
  }

  function setAllAgeYears(ageYear: string) {
    setRows((current) => current.map((row) => ({ ...row, ageYear })));
  }

  async function submit() {
    const validation = validateExecution({
      availableSources,
      completedDate,
      inputQuantities,
      recordMode,
      releasedPlacements,
      rows,
      selectedSourceIds,
      selectedSources,
      workTypeCode: operation.workTypeCode,
    });
    if (validation) {
      setError(validation);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const payload = createExecutionPayload({
        completedDate,
        inputQuantities,
        memo,
        releasedPlacements,
        rows,
        selectedSources,
        worker,
      });
      if (onSubmitRecord) {
        await onSubmitRecord(payload);
      } else {
        const updated = await executeStructureChangeWorkOperation(
          operation.id,
          payload,
        );
        onSaved?.(updated);
      }
      if (closeAfterSubmit) {
        onClose();
      }
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : `${operation.workType} 작업을 실행하지 못했습니다.`,
      );
    } finally {
      setSaving(false);
    }
  }

  function reportError(cause: unknown, fallback: string) {
    setError(cause instanceof Error ? cause.message : fallback);
  }

  return {
    addResult,
    availableSources,
    changeInputQuantity,
    changeReleasedPlacement,
    commonAgeYear,
    commonPotSize,
    completedDate,
    error,
    inputQuantities,
    memo,
    patchRow,
    releasedPlacements,
    removeResult,
    reportError,
    rows,
    savedResultReferences,
    saving,
    selectedSourceIds,
    selectedSources,
    setAllAgeYears,
    setAllPotSizes,
    setCompletedDate,
    setMemo,
    setWorker,
    submit,
    today,
    toggleSource,
    totalInput,
    totalResult,
    worker,
  };
}
