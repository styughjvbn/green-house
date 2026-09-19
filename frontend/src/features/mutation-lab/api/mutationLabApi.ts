import { fetchApi } from "@/shared/api/client";
import type { MutationLabFilters, MutationPage } from "../model/types";

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
