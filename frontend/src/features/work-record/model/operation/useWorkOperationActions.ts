"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type {
  WorkOperation,
  WorkOperationSummary,
  WorkOperationTarget,
} from "@/entities/farm/types";
import {
  completeWorkOperation,
  transitionWorkOperation,
  transitionWorkOperationTarget,
} from "../../api/workRecordApi";
import { useWorkRecordInvalidation } from "../useWorkRecordInvalidation";
import { workOperationQueryOptions } from "../workRecordQueryOptions";
import { workRecordQueryKeys } from "../workRecordQueryKeys";

export function useWorkOperationActions(operations: WorkOperationSummary[]) {
  const { invalidateOperations, invalidateWorkData } =
    useWorkRecordInvalidation();
  const queryClient = useQueryClient();
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [executionTarget, setExecutionTarget] =
    useState<WorkOperationTarget | null>(null);
  const selectedSummary =
    operations.find((operation) => operation.id === selectedId) ?? null;
  const selectedQuery = useQuery({
    ...workOperationQueryOptions(selectedId ?? 0),
    enabled: selectedSummary != null,
  });
  const selected = selectedQuery.data ?? null;
  const actionMutation = useMutation({
    mutationFn: (action: () => Promise<WorkOperation>) => action(),
    onSuccess: async (updated) => {
      setSelectedId(updated.id);
      queryClient.setQueryData(
        workRecordQueryKeys.operations.operation(updated.id),
        updated,
      );
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
          : selectedQuery.error instanceof Error
            ? selectedQuery.error.message
            : selectedQuery.error
              ? "작업 상세를 불러오지 못했습니다."
              : null,
    detailLoading: selectedQuery.isFetching,
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
    selectedId: selectedSummary?.id ?? null,
    executionSaved(updated: WorkOperation) {
      setSelectedId(updated.id);
      setExecutionTarget(null);
      queryClient.setQueryData(
        workRecordQueryKeys.operations.operation(updated.id),
        updated,
      );
      void invalidateWorkData();
    },
  };
}

export type WorkOperationActions = ReturnType<typeof useWorkOperationActions>;
