"use client";

import type { ReactNode } from "react";
import { TabError, TabLayout, TabSplit } from "@/shared/ui/TabLayout";
import type { WorkRecordUrlState } from "../../lib/workRecordUrlState";
import { useWorkOperationActions } from "../../model/operation/useWorkOperationActions";
import { useWorkOperations } from "../../model/operation/useWorkOperations";
import { WorkOperationDetailPanel } from "../detail/WorkOperationDetailPanel";
import { WorkListFilters } from "./WorkListFilters";
import { WorkListTable } from "./WorkListTable";

export function WorkOperationListView({
  headerActions,
  routeState,
  showCreateAction = true,
  onCreateWork,
}: {
  headerActions?: ReactNode;
  routeState: WorkRecordUrlState;
  showCreateAction?: boolean;
  onCreateWork: () => void;
}) {
  const list = useWorkOperations(routeState);
  const operations = list.pageData.content;
  const actions = useWorkOperationActions(operations);
  const loading = list.query.isFetching || actions.loading;
  const error =
    actions.error ??
    (list.query.error instanceof Error ? list.query.error.message : null);

  return (
    <>
      <TabLayout>
        <WorkListFilters
          allStatusLabel={
            routeState.scope === "ALL" ? "모든 상태" : "관리 대상 전체"
          }
          filters={list.filters}
          loading={loading}
          onChange={list.updateFilter}
          onReset={() => {
            actions.clearSelection();
            list.reset();
          }}
          onSearch={() => {
            actions.clearSelection();
            list.search();
          }}
        />

        <TabError message={error} />

        <TabSplit columns="grid-rows-[minmax(24rem,1fr)_minmax(20rem,auto)] lg:grid-rows-1 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
          <WorkListTable
            headerActions={headerActions}
            loading={loading}
            operations={operations}
            page={routeState.page}
            pageSize={routeState.size}
            selectedId={actions.selectedId}
            totalElements={list.pageData.totalElements}
            totalPages={list.pageData.totalPages}
            onCreate={showCreateAction ? onCreateWork : undefined}
            onPageChange={(page) => {
              actions.clearSelection();
              list.changePage(page);
            }}
            onPageSizeChange={(size) => {
              actions.clearSelection();
              list.changePageSize(size);
            }}
            onSelect={actions.select}
          />

          <WorkOperationDetailPanel
            actions={actions}
            emptyMessage="상세를 확인할 작업을 선택하세요."
          />
        </TabSplit>
      </TabLayout>
    </>
  );
}
