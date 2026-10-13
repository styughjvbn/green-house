import { useQuery } from "@tanstack/react-query";
import type { WorkOperationSummary } from "@/entities/farm/types";
import { createEmptyPage } from "@/shared/api/page";
import { useUrlPagedListState } from "@/shared/api/useUrlPagedListState";
import {
  WORK_LIST_FILTER_KEYS,
  writeWorkWorkspaceFilterParams,
  type WorkRecordUrlState,
  type WorkFilterBadgeKey,
  type WorkWorkspaceFilterState,
} from "../../lib/workRecordUrlState";
import { workOperationPageQueryOptions } from "../workRecordQueryOptions";

export function useWorkOperations(routeState: WorkRecordUrlState) {
  const query = useQuery(workOperationPageQueryOptions(routeState));
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
    query,
    pageData:
      query.data ??
      createEmptyPage<WorkOperationSummary>(routeState.size, routeState.page),
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
