import { queryOptions } from "@tanstack/react-query";
import {
  getOrchidGroupMutationGraph,
  getOrchidGroupMutations,
} from "../api/mutationLabApi";
import type { MutationLabFilters } from "./types";

export function mutationLabQueryOptions(filters: MutationLabFilters) {
  return queryOptions({
    queryKey: ["mutationLab", filters] as const,
    queryFn: ({ signal }) => getOrchidGroupMutations(filters, signal),
  });
}

export function mutationGraphQueryOptions(
  orchidGroupId: number,
  depth: number,
  maxNodes: number,
) {
  return queryOptions({
    queryKey: ["mutationLab", "graph", orchidGroupId, depth, maxNodes] as const,
    queryFn: ({ signal }) =>
      getOrchidGroupMutationGraph(orchidGroupId, depth, maxNodes, signal),
  });
}
