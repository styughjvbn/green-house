import { queryOptions } from "@tanstack/react-query";
import { getOrchidGroupMutations } from "../api/mutationLabApi";
import type { MutationLabFilters } from "./types";

export function mutationLabQueryOptions(filters: MutationLabFilters) {
  return queryOptions({
    queryKey: ["mutationLab", filters] as const,
    queryFn: ({ signal }) => getOrchidGroupMutations(filters, signal),
  });
}
