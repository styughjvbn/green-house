import { useQuery } from "@tanstack/react-query";
import type { WorkOperationSummary } from "@/entities/farm/types";
import { createEmptyPage } from "@/shared/api/page";
import type { WorkRecordUrlState } from "../../lib/workRecordUrlState";
import { useWorkFilters } from "./useWorkFilters";
import { workOperationPageQueryOptions } from "../workRecordQueryOptions";

export function useWorkOperations(routeState: WorkRecordUrlState) {
  const query = useQuery(workOperationPageQueryOptions(routeState));
  const listState = useWorkFilters(routeState);

  return {
    ...listState,
    query,
    pageData:
      query.data ??
      createEmptyPage<WorkOperationSummary>(routeState.size, routeState.page),
  };
}
