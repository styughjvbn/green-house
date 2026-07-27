"use client";

import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import type { WorkOperation, WorkOperationTarget } from "@/entities/farm/types";
import {
  completeWorkOperation,
  transitionWorkOperation,
  transitionWorkOperationTarget,
} from "../api/workRecordApi";
import { useWorkRecordInvalidation } from "./useWorkRecordInvalidation";

export function useWorkOperationActions(operations: WorkOperation[]) {
  const { invalidateOperations, invalidateWorkData } =
    useWorkRecordInvalidation();
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [executionTarget, setExecutionTarget] =
    useState<WorkOperationTarget | null>(null);
  const selected =
    operations.find((operation) => operation.id === selectedId) ?? null;
  const actionMutation = useMutation({
    mutationFn: (action: () => Promise<WorkOperation>) => action(),
    onSuccess: async (updated) => {
      setSelectedId(updated.id);
      await invalidateOperations();
    },
  });

  return {
    clearSelection() {
      setSelectedId(null);
      setExecutionTarget(null);
      actionMutation.reset();
    },
    closeExecution: () => setExecutionTarget(null),
    complete: (completedDate: string) => {
      if (!selected) return;
      actionMutation.mutate(() =>
        completeWorkOperation(selected.id, completedDate),
      );
    },
    error:
      actionMutation.error instanceof Error
        ? actionMutation.error.message
        : actionMutation.error
          ? "작업 상태를 변경하지 못했습니다."
          : null,
    executionTarget,
    loading: actionMutation.isPending,
    openExecution: setExecutionTarget,
    runOperationAction(action: "start" | "pause" | "resume" | "cancel") {
      if (!selected) return;
      actionMutation.mutate(() => transitionWorkOperation(selected.id, action));
    },
    runTargetAction(
      targetId: number,
      action: "start" | "complete" | "skip",
      completedDate?: string,
    ) {
      if (!selected) return;
      actionMutation.mutate(() =>
        transitionWorkOperationTarget(
          selected.id,
          targetId,
          action,
          selected.worker,
          undefined,
          completedDate,
        ),
      );
    },
    select: setSelectedId,
    selected,
    selectedId: selected?.id ?? null,
    executionSaved(updated: WorkOperation) {
      setSelectedId(updated.id);
      setExecutionTarget(null);
      void invalidateWorkData();
    },
  };
}

export type WorkOperationActions = ReturnType<typeof useWorkOperationActions>;
