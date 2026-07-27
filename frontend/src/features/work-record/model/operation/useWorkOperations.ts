import { useQuery } from "@tanstack/react-query";
import type { WorkOperation } from "@/entities/farm/types";
import { createEmptyPage } from "@/shared/api/page";
import { useUrlPagedListState } from "@/shared/api/useUrlPagedListState";
import {
  WORK_LIST_FILTER_KEYS,
  writeWorkListFilterParams,
  type WorkRecordUrlState,
} from "../../lib/workRecordUrlState";
import type { WorkOperationFilterState } from "../types";
import { workOperationPageQueryOptions } from "../workRecordQueryOptions";

export function useWorkOperations(routeState: WorkRecordUrlState) {
  const query = useQuery(workOperationPageQueryOptions(routeState));
  const listState = useUrlPagedListState({
    emptyFilters: createEmptyWorkOperationFilters,
    filterKeys: WORK_LIST_FILTER_KEYS,
    routeFilters: routeState.filters,
    writeFilterParams: writeWorkListFilterParams,
  });

  return {
    ...listState,
    query,
    pageData:
      query.data ??
      createEmptyPage<WorkOperation>(routeState.size, routeState.page),
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
