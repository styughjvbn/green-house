"use client";

import { useCallback, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { WorkOperation, WorkOperationTarget } from "@/entities/farm/types";
import {
  completeWorkOperation,
  transitionWorkOperation,
  transitionWorkOperationTarget,
  updateWorkOperationTitle,
} from "../../api/workRecordApi";
import { useWorkRecordInvalidation } from "../useWorkRecordInvalidation";
import { workOperationQueryOptions } from "../workRecordQueryOptions";
import { workRecordQueryKeys } from "../workRecordQueryKeys";

export function useWorkOperationActions() {
  const { invalidateOperations, invalidateWorkData } =
    useWorkRecordInvalidation();
  const queryClient = useQueryClient();
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detailSelection, setDetailSelection] = useState<{
    operationId: number | null;
    initialTab: "overview" | "graph";
    revision: number;
  }>({ operationId: null, initialTab: "overview", revision: 0 });
  const [executionTarget, setExecutionTarget] =
    useState<WorkOperationTarget | null>(null);
  const selectedQuery = useQuery({
    ...workOperationQueryOptions(selectedId ?? 0),
    enabled: selectedId != null,
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
  const select = useCallback((operationId: number) => {
    setSelectedId(operationId);
    setDetailSelection((current) => ({
      operationId,
      initialTab: "overview",
      revision: current.revision + 1,
    }));
  }, []);
  const selectFromGraph = useCallback((operationId: number) => {
    setSelectedId(operationId);
    setDetailSelection((current) => ({
      operationId,
      initialTab: "graph",
      revision: current.revision + 1,
    }));
  }, []);

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
          ? "작업을 변경하지 못했습니다."
          : selectedQuery.error instanceof Error
            ? selectedQuery.error.message
            : selectedQuery.error
              ? "작업 상세를 불러오지 못했습니다."
              : null,
    detailLoading: selectedQuery.isFetching,
    detailInitialTab:
      detailSelection.operationId === selectedId
        ? detailSelection.initialTab
        : "overview",
    detailSelectionKey: detailSelection.revision,
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
    updateTitle(title: string) {
      if (!selected)
        return Promise.reject(new Error("수정할 작업이 없습니다."));
      return actionMutation.mutateAsync(() =>
        updateWorkOperationTitle(selected.id, title),
      );
    },
    select,
    selectFromGraph,
    selected,
    selectedId,
    executionSaved(updated: WorkOperation) {
      setSelectedId(updated.id);
      setExecutionTarget(null);
      queryClient.setQueryData(
        workRecordQueryKeys.operations.operation(updated.id),
        updated,
      );
      void invalidateWorkData();
    },
    voidSaved(updated: WorkOperation) {
      setSelectedId(updated.id);
      queryClient.setQueryData(
        workRecordQueryKeys.operations.operation(updated.id),
        updated,
      );
      void invalidateWorkData();
    },
  };
}

export type WorkOperationActions = ReturnType<typeof useWorkOperationActions>;
