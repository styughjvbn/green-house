import { farmMutationQueryKeys } from "@/entities/farm/model/farmMutationQueries";
import type { WorkRecordUrlState } from "../lib/workRecordUrlState";

export const workRecordQueryKeys = {
  all: ["workRecords"] as const,
  references: {
    all: ["workRecords", "references"] as const,
    workTypes: ["workRecords", "references", "workTypes"] as const,
    houses: ["workRecords", "references", "houses"] as const,
  },
  operations: {
    all: farmMutationQueryKeys.workOperations,
    page: (state: WorkRecordUrlState) =>
      [
        ...farmMutationQueryKeys.workOperations,
        "page",
        state.scope,
        state.filters,
        state.page,
        state.size,
      ] as const,
    calendar: (state: WorkRecordUrlState) =>
      [
        ...farmMutationQueryKeys.workOperations,
        "calendar",
        state.scope,
        state.month,
        state.filters.status,
      ] as const,
    operation: (workOperationId: number) =>
      [...farmMutationQueryKeys.workOperations, workOperationId] as const,
    details: (workOperationId: number) =>
      [
        ...farmMutationQueryKeys.workOperations,
        workOperationId,
        "details",
      ] as const,
    relations: (workOperationId: number, kind: string) =>
      [
        ...farmMutationQueryKeys.workOperations,
        workOperationId,
        "relations",
        kind,
      ] as const,
    graph: (workOperationId: number, detail: string, depth: number) =>
      [
        ...farmMutationQueryKeys.workOperations,
        workOperationId,
        "graph",
        detail,
        depth,
      ] as const,
  },
};
