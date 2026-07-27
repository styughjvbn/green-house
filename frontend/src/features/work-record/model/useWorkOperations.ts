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

const INITIAL_PAGE = createEmptyPage<WorkOperation>(20);
const INITIAL_FILTERS = createEmptyWorkOperationFilters();

export function useWorkOperations({
  refreshKey,
  view,
}: {
  refreshKey: number;
  view: "ALL" | "MANAGEMENT" | "HISTORY";
}) {
  const listState = usePagedListQuery({
    createEmptyFilters: createEmptyWorkOperationFilters,
    initialFilters: INITIAL_FILTERS,
    initialPage: INITIAL_PAGE,
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
    pageData: listState.pageData ?? createEmptyPage<WorkOperation>(20),
  };
}

function createEmptyWorkOperationFilters(): WorkOperationFilterState {
  return {
    from: "",
    keyword: "",
    status: "",
    to: "",
  };
}
