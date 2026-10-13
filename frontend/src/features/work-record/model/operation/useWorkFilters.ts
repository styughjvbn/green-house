import { useUrlPagedListState } from "@/shared/api/useUrlPagedListState";
import {
  WORK_LIST_FILTER_KEYS,
  writeWorkWorkspaceFilterParams,
  type WorkRecordUrlState,
  type WorkFilterBadgeKey,
  type WorkWorkspaceFilterState,
} from "../../lib/workRecordUrlState";

export function useWorkFilters(routeState: WorkRecordUrlState) {
  const listState = useUrlPagedListState<WorkWorkspaceFilterState>({
    emptyFilters: createEmptyWorkOperationFilters,
    filterKeys: [...WORK_LIST_FILTER_KEYS, "scope"],
    routeFilters: { ...routeState.filters, scope: routeState.scope },
    writeFilterParams: writeWorkWorkspaceFilterParams,
  });
  return {
    ...listState,
    resetPopupFilters: () =>
      listState.removeFilters(["from", "to", "status", "scope"]),
    removeAppliedFilter: (key: WorkFilterBadgeKey) =>
      listState.removeFilters(key === "period" ? ["from", "to"] : [key]),
  };
}

function createEmptyWorkOperationFilters(): WorkWorkspaceFilterState {
  return {
    from: "",
    keyword: "",
    status: "",
    to: "",
    scope: "ALL",
  };
}
