"use client";

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { TabError } from "@/shared/ui/TabLayout";
import type { WorkOperationActions } from "../model/useWorkOperationActions";
import { workHousesQueryOptions } from "../model/workRecordQueryOptions";
import { deriveWorkTargetSelectionOptions } from "../model/workTargetSelectionOptions";
import { OperationResult } from "./components/WorkOperationResult";
import { WorkExecutionDialog } from "./WorkExecutionDialog";

export function WorkOperationDetailPanel({
  actions,
  emptyMessage,
}: {
  actions: WorkOperationActions;
  emptyMessage: string;
}) {
  const housesQuery = useQuery({
    ...workHousesQueryOptions(),
    enabled: actions.executionTarget != null,
  });
  const houses = housesQuery.data ?? [];
  const targetOptions = useMemo(
    () => deriveWorkTargetSelectionOptions(houses),
    [houses],
  );

  return (
    <>
      <div className="min-h-0 overflow-auto">
        <TabError
          message={
            housesQuery.error instanceof Error
              ? housesQuery.error.message
              : null
          }
        />
        {actions.selected ? (
          <OperationResult
            className="h-full"
            operation={actions.selected}
            loading={actions.loading || housesQuery.isFetching}
            onComplete={actions.complete}
            onOperationAction={actions.runOperationAction}
            onTargetAction={actions.runTargetAction}
            onExecuteTarget={actions.openExecution}
          />
        ) : (
          <div className="flex h-full min-h-40 items-center justify-center rounded-md border border-[#dfe5dc] bg-white p-8 text-center text-sm text-[#5c6a60] shadow-sm">
            {emptyMessage}
          </div>
        )}
      </div>

      {actions.selected &&
      actions.executionTarget &&
      housesQuery.data != null ? (
        <WorkExecutionDialog
          bedZones={targetOptions.bedZones}
          houses={houses}
          operation={actions.selected}
          orchidGroups={targetOptions.orchidGroups}
          source={
            actions.executionTarget.orchidGroupId == null
              ? null
              : (targetOptions.orchidGroups.find(
                  (group) =>
                    group.id === actions.executionTarget?.orchidGroupId,
                ) ?? null)
          }
          target={actions.executionTarget}
          onClose={actions.closeExecution}
          onSaved={actions.executionSaved}
        />
      ) : null}
    </>
  );
}
