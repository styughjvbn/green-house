import { fetchApi } from "@/shared/api/client";
import type {
  MutationGraph,
  MutationLabFilters,
  MutationPage,
} from "../model/types";

export function getOrchidGroupMutations(
  filters: MutationLabFilters,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams({
    page: String(filters.page),
    size: String(filters.size),
  });
  if (filters.orchidGroupId != null) {
    params.set("orchidGroupId", String(filters.orchidGroupId));
  }
  if (filters.mutationType) {
    params.set("mutationType", filters.mutationType);
  }
  if (filters.sourceDomain) {
    params.set("sourceDomain", filters.sourceDomain);
  }
  return fetchApi<MutationPage>(`/orchid-group-mutations?${params}`, {
    signal,
  });
}

export function getOrchidGroupMutationGraph(
  orchidGroupId: number,
  depth: number,
  maxNodes: number,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams({
    depth: String(depth),
    maxNodes: String(maxNodes),
  });
  return fetchApi<MutationGraph>(
    `/orchid-group-mutations/graph/${orchidGroupId}?${params}`,
    { signal },
  );
}
