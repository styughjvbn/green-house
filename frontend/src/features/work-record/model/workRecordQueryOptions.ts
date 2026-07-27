import { queryOptions } from "@tanstack/react-query";
import {
  getCalendarWorkOperations,
  getWorkHouses,
  getWorkOperations,
  getWorkTypes,
} from "../api/workRecordApi";
import type { WorkRecordUrlState } from "../lib/workRecordUrlState";
import { workRecordQueryKeys } from "./workRecordQueryKeys";

export function workTypesQueryOptions() {
  return queryOptions({
    queryKey: workRecordQueryKeys.references.workTypes,
    queryFn: getWorkTypes,
  });
}

export function workHousesQueryOptions() {
  return queryOptions({
    queryKey: workRecordQueryKeys.references.houses,
    queryFn: getWorkHouses,
  });
}

export function workOperationPageQueryOptions(state: WorkRecordUrlState) {
  return queryOptions({
    queryKey: workRecordQueryKeys.operations.page(state),
    queryFn: () =>
      getWorkOperations({
        from: state.filters.from,
        keyword: state.filters.keyword,
        status: state.filters.status,
        to: state.filters.to,
        view: state.scope,
        page: state.page,
        size: state.size,
      }),
  });
}

export function workOperationCalendarQueryOptions(state: WorkRecordUrlState) {
  const range = workMonthRange(state.month);
  return queryOptions({
    queryKey: workRecordQueryKeys.operations.calendar(state),
    queryFn: () =>
      getCalendarWorkOperations({
        from: range.from,
        to: range.to,
        status: state.filters.status,
        view: state.scope,
      }),
  });
}

function workMonthRange(month: string) {
  const [year, monthNumber] = month.split("-").map(Number);
  const lastDay = new Date(Date.UTC(year, monthNumber, 0)).getUTCDate();
  return {
    from: `${month}-01`,
    to: `${month}-${String(lastDay).padStart(2, "0")}`,
  };
}
