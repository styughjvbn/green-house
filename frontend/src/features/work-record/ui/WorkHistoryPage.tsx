"use client";

import { useState } from "react";
import { TabError, TabLayout, TabSplit } from "@/shared/ui/TabLayout";
import { useWorkOperations } from "../model/useWorkOperations";
import { OperationResult } from "./components/WorkOperationResult";
import { WorkHistoryFilters } from "./history/WorkHistoryFilters";
import { WorkHistoryList } from "./history/WorkHistoryList";

export function WorkHistoryPage({ refreshKey }: { refreshKey: number }) {
  const history = useWorkOperations({ refreshKey, view: "HISTORY" });
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const operations = history.pageData.content;
  const selected =
    operations.find((operation) => operation.id === selectedId) ?? null;
  const error =
    history.query.error instanceof Error ? history.query.error.message : null;

  return (
    <main className="h-full min-h-0">
      <TabLayout>
        <WorkHistoryFilters
          filters={history.filters}
          loading={history.query.isFetching}
          onChange={history.updateFilter}
          onReset={() => {
            setSelectedId(null);
            history.reset();
          }}
          onSearch={() => {
            setSelectedId(null);
            history.search();
          }}
        />

        <TabError message={error} />

        <TabSplit columns="grid-rows-[minmax(24rem,1fr)_minmax(20rem,auto)] lg:grid-rows-1 lg:grid-cols-[minmax(0,0.88fr)_minmax(0,1.12fr)]">
          <WorkHistoryList
            loading={history.query.isFetching}
            operations={operations}
            page={history.queryState.page}
            pageSize={history.queryState.size}
            selectedId={selectedId}
            totalElements={history.pageData.totalElements}
            totalPages={history.pageData.totalPages}
            onPageChange={(page) => {
              setSelectedId(null);
              history.changePage(page);
            }}
            onPageSizeChange={(size) => {
              setSelectedId(null);
              history.changePageSize(size);
            }}
            onSelect={setSelectedId}
          />

          <div className="min-h-0 overflow-auto">
            {selected ? (
              <OperationResult
                className="h-full"
                loading={history.query.isFetching}
                operation={selected}
                onComplete={() => undefined}
                onOperationAction={() => undefined}
                onTargetAction={() => undefined}
              />
            ) : (
              <div className="flex h-full min-h-40 items-center justify-center rounded-md border border-[#dfe5dc] bg-white p-8 text-center text-sm text-[#5c6a60] shadow-sm">
                상세를 확인할 작업 이력을 선택하세요.
              </div>
            )}
          </div>
        </TabSplit>
      </TabLayout>
    </main>
  );
}
