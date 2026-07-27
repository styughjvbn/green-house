"use client";

import type { ReactNode } from "react";
import { useState } from "react";
import type {
  BedZone,
  House,
  OrchidGroup,
  WorkOperation,
  WorkOperationTarget,
} from "@/entities/farm/types";
import { TabError, TabLayout, TabSplit } from "@/shared/ui/TabLayout";
import {
  completeWorkOperation,
  transitionWorkOperation,
  transitionWorkOperationTarget,
} from "../api/workRecordApi";
import { useWorkOperations } from "../model/useWorkOperations";
import { OperationResult } from "./components/WorkOperationResult";
import { StructureWorkExecutionDialog } from "./components/StructureWorkExecutionDialog";
import { WorkListFilters } from "./list/WorkListFilters";
import { WorkListTable } from "./list/WorkListTable";

export function WorkOperationListView({
  bedZones,
  houses,
  headerActions,
  orchidGroups,
  queryView = "MANAGEMENT",
  refreshKey,
  showCreateAction = true,
  onCreateWork,
}: {
  bedZones: BedZone[];
  houses: House[];
  headerActions?: ReactNode;
  orchidGroups: OrchidGroup[];
  queryView?: "ALL" | "MANAGEMENT";
  refreshKey: number;
  showCreateAction?: boolean;
  onCreateWork: () => void;
}) {
  const list = useWorkOperations({ refreshKey, view: queryView });
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [actionLoading, setActionLoading] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [executionTarget, setExecutionTarget] =
    useState<WorkOperationTarget | null>(null);
  const operations = list.pageData.content;
  const selected =
    operations.find((operation) => operation.id === selectedId) ?? null;
  const loading = list.query.isFetching || actionLoading;
  const error =
    actionError ??
    (list.query.error instanceof Error ? list.query.error.message : null);

  async function run(action: () => Promise<WorkOperation>) {
    setActionLoading(true);
    setActionError(null);
    try {
      const updated = await action();
      setSelectedId(updated.id);
      await list.query.refetch();
    } catch (cause) {
      setActionError(
        cause instanceof Error
          ? cause.message
          : "작업 상태를 변경하지 못했습니다.",
      );
    } finally {
      setActionLoading(false);
    }
  }

  function clearSelection() {
    setSelectedId(null);
    setExecutionTarget(null);
    setActionError(null);
  }

  return (
    <>
      <TabLayout>
        <WorkListFilters
          allStatusLabel={queryView === "ALL" ? "모든 상태" : "관리 대상 전체"}
          filters={list.filters}
          loading={loading}
          onChange={list.updateFilter}
          onReset={() => {
            clearSelection();
            list.reset();
          }}
          onSearch={() => {
            clearSelection();
            list.search();
          }}
        />

        <TabError message={error} />

        <TabSplit columns="grid-rows-[minmax(24rem,1fr)_minmax(20rem,auto)] lg:grid-rows-1 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
          <WorkListTable
            headerActions={headerActions}
            loading={loading}
            operations={operations}
            page={list.queryState.page}
            pageSize={list.queryState.size}
            selectedId={selectedId}
            totalElements={list.pageData.totalElements}
            totalPages={list.pageData.totalPages}
            onCreate={showCreateAction ? onCreateWork : undefined}
            onPageChange={(page) => {
              clearSelection();
              list.changePage(page);
            }}
            onPageSizeChange={(size) => {
              clearSelection();
              list.changePageSize(size);
            }}
            onSelect={setSelectedId}
          />

          <div className="min-h-0 overflow-auto">
            {selected ? (
              <OperationResult
                className="h-full"
                operation={selected}
                loading={loading}
                onComplete={(completedDate) =>
                  void run(() =>
                    completeWorkOperation(selected.id, completedDate),
                  )
                }
                onOperationAction={(action) =>
                  void run(() => transitionWorkOperation(selected.id, action))
                }
                onTargetAction={(targetId, action, completedDate) =>
                  void run(() =>
                    transitionWorkOperationTarget(
                      selected.id,
                      targetId,
                      action,
                      selected.worker,
                      undefined,
                      completedDate,
                    ),
                  )
                }
                onExecuteTarget={setExecutionTarget}
              />
            ) : (
              <div className="flex h-full min-h-40 items-center justify-center rounded-md border border-[#dfe5dc] bg-white p-8 text-center text-sm text-[#5c6a60] shadow-sm">
                상세를 확인할 작업을 선택하세요.
              </div>
            )}
          </div>
        </TabSplit>
      </TabLayout>

      {selected && executionTarget ? (
        <StructureWorkExecutionDialog
          bedZones={bedZones}
          houses={houses}
          operation={selected}
          orchidGroups={orchidGroups}
          source={
            executionTarget.orchidGroupId == null
              ? null
              : (orchidGroups.find(
                  (group) => group.id === executionTarget.orchidGroupId,
                ) ?? null)
          }
          target={executionTarget}
          onClose={() => setExecutionTarget(null)}
          onSaved={(updated) => {
            setSelectedId(updated.id);
            void list.query.refetch();
            setExecutionTarget(null);
          }}
        />
      ) : null}
    </>
  );
}
