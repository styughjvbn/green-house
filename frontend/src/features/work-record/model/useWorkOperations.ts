import type { WorkOperation, WorkOperationStatus } from "@/entities/farm/types";
import { createEmptyPage } from "@/shared/api/page";
import { usePagedListQuery } from "@/shared/api/usePagedListQuery";
import { getWorkOperations } from "../api/workRecordApi";

export type WorkOperationFilterState = {
  from: string;
  keyword: string;
  status: WorkOperationStatus | "";
  to: string;
};

const workOperationQueryKeys = {
  all: ["workOperations"] as const,
  page: (
    view: "ALL" | "MANAGEMENT" | "HISTORY",
    filters: WorkOperationFilterState,
    page: number,
    size: number,
    refreshKey: number,
  ) =>
    [
      ...workOperationQueryKeys.all,
      view,
      filters,
      page,
      size,
      refreshKey,
    ] as const,
};

export function useWorkOperations({
  initialFilters,
  initialPage,
  initialSize,
  refreshKey,
  view,
}: {
  initialFilters: WorkOperationFilterState;
  initialPage: number;
  initialSize: number;
  refreshKey: number;
  view: "ALL" | "MANAGEMENT" | "HISTORY";
}) {
  const listState = usePagedListQuery({
    createEmptyFilters: createEmptyWorkOperationFilters,
    hasInitialData: false,
    initialFilters,
    initialPage: createEmptyPage<WorkOperation>(initialSize, initialPage),
    queryKey: ({ filters, page, size }) =>
      workOperationQueryKeys.page(view, filters, page, size, refreshKey),
    queryFn: ({ filters, page, size }) =>
      getWorkOperations({
        from: filters.from,
        keyword: filters.keyword,
        status: filters.status,
        to: filters.to,
        view,
        page,
        size,
      }),
  });

  return {
    ...listState,
    pageData:
      listState.pageData ??
      createEmptyPage<WorkOperation>(
        listState.queryState.size,
        listState.queryState.page,
      ),
  };
}

export function createEmptyWorkOperationFilters(): WorkOperationFilterState {
  return {
    from: "",
    keyword: "",
    status: "",
    to: "",
  };
}
