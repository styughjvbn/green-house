import type { WorkRecordUrlState } from "../lib/workRecordUrlState";

export const workRecordQueryKeys = {
  all: ["workRecords"] as const,
  references: {
    all: ["workRecords", "references"] as const,
    workTypes: ["workRecords", "references", "workTypes"] as const,
    houses: ["workRecords", "references", "houses"] as const,
  },
  operations: {
    all: ["workRecords", "operations"] as const,
    page: (state: WorkRecordUrlState) =>
      [
        "workRecords",
        "operations",
        "page",
        state.scope,
        state.filters,
        state.page,
        state.size,
      ] as const,
    calendar: (state: WorkRecordUrlState) =>
      [
        "workRecords",
        "operations",
        "calendar",
        state.scope,
        state.month,
        state.filters.status,
      ] as const,
  },
};
