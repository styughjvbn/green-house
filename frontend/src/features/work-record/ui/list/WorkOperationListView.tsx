"use client";

import { useMemo, useState, type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { TabError, TabLayout, TabSplit } from "@/shared/ui/TabLayout";
import { FilterResetButton } from "@/shared/ui/FilterControls";
import type { WorkRecordUrlState } from "../../lib/workRecordUrlState";
import type { WorkOperationRelationSelection } from "../../lib/workOperationRelations";
import { workOperationRelationsQueryOptions } from "../../model/workRecordQueryOptions";
import { useWorkOperationActions } from "../../model/operation/useWorkOperationActions";
import { useWorkOperations } from "../../model/operation/useWorkOperations";
import { WorkOperationDetailPanel } from "../detail/WorkOperationDetailPanel";
import { WorkFilters } from "../WorkFilters";
import { WorkListTable } from "./WorkListTable";
import { WorkOperationRelationsCard } from "./WorkOperationRelationsCard";

export function WorkOperationListView({
  headerActions,
  routeState,
  viewControl,
}: {
  headerActions?: ReactNode;
  routeState: WorkRecordUrlState;
  viewControl?: ReactNode;
}) {
  const list = useWorkOperations(routeState);
  const operations = list.pageData.content;
  const actions = useWorkOperationActions();
  const [relationSelection, setRelationSelection] =
    useState<WorkOperationRelationSelection | null>(null);
  const relationsQuery = useQuery({
    ...workOperationRelationsQueryOptions(
      relationSelection?.operationId ?? 0,
      relationSelection?.kind ?? "LINKED",
    ),
    enabled: relationSelection !== null,
  });
  const relatedOperationIds = useMemo(() => {
    const ids = new Set(
      (relationsQuery.data ?? []).map((operation) => operation.id),
    );
    if (relationSelection) ids.add(relationSelection.operationId);
    return ids;
  }, [relationSelection, relationsQuery.data]);

  const loading = list.query.isFetching || actions.loading;
  const error =
    actions.error ??
    (list.query.error instanceof Error ? list.query.error.message : null);

  function resetAllFilters() {
    actions.clearSelection();
    setRelationSelection(null);
    list.reset();
  }

  const toolbar = (columnSettings: ReactNode) => (
    <WorkFilters
      appliedFilters={routeState.filters}
      appliedScope={routeState.scope}
      columnSettings={columnSettings}
      filters={list.filters}
      loading={loading}
      viewControl={viewControl}
      onChange={list.updateFilter}
      onRemove={(key) => {
        actions.clearSelection();
        setRelationSelection(null);
        list.removeAppliedFilter(key);
      }}
      onReset={resetAllFilters}
      onResetFilters={() => {
        actions.clearSelection();
        setRelationSelection(null);
        list.resetPopupFilters();
      }}
      onSearch={() => {
        actions.clearSelection();
        setRelationSelection(null);
        list.search();
      }}
    />
  );

  return (
    <>
      <TabLayout>
        <TabError message={error} />

        <TabSplit columns="grid-rows-[minmax(24rem,1fr)_minmax(20rem,auto)] lg:grid-rows-1 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
          <div className="flex min-h-0 flex-col gap-3">
            <div className="min-h-0 flex-1">
              <WorkListTable
                emptyAction={
                  list.query.isSuccess &&
                  !loading &&
                  !error &&
                  list.pageData.totalElements === 0 ? (
                    <FilterResetButton
                      className="lg:mt-0"
                      label="전체 초기화"
                      onClick={resetAllFilters}
                    />
                  ) : undefined
                }
                activeRelation={relationSelection}
                headerActions={headerActions}
                loading={loading}
                operations={operations}
                page={routeState.page}
                pageSize={routeState.size}
                relatedOperationIds={relatedOperationIds}
                selectedId={actions.selectedId}
                totalElements={list.pageData.totalElements}
                totalPages={list.pageData.totalPages}
                toolbar={toolbar}
                onPageChange={(page) => {
                  actions.clearSelection();
                  setRelationSelection(null);
                  list.changePage(page);
                }}
                onPageSizeChange={(size) => {
                  actions.clearSelection();
                  setRelationSelection(null);
                  list.changePageSize(size);
                }}
                onSelect={(operationId) => {
                  actions.select(operationId);
                  if (
                    relationSelection &&
                    !relatedOperationIds.has(operationId)
                  ) {
                    setRelationSelection(null);
                  }
                }}
                onShowRelations={(selection) => {
                  actions.select(selection.operationId);
                  setRelationSelection((current) =>
                    current?.operationId === selection.operationId &&
                    current.kind === selection.kind
                      ? null
                      : selection,
                  );
                }}
              />
            </div>

            {relationSelection ? (
              <WorkOperationRelationsCard
                selection={relationSelection}
                selectedId={actions.selectedId}
                onClose={() => setRelationSelection(null)}
                onSelect={actions.select}
              />
            ) : null}
          </div>

          <WorkOperationDetailPanel
            actions={actions}
            emptyMessage="상세를 확인할 작업을 선택하세요."
          />
        </TabSplit>
      </TabLayout>
    </>
  );
}
