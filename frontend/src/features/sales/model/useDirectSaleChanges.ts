import { useState, useSyncExternalStore } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError } from "@/shared/api/client";
import { createUuid } from "@/shared/lib/id";
import { getDirectSaleChanges, submitDirectSaleChange } from "../api/salesApi";
import {
  createDirectChangeRequests,
  type DirectChangeInput,
  type DirectChangePendingRequest,
} from "../lib/directChangeRequests";
import { salesQueryKeys } from "./salesQueryKeys";

export function useDirectSaleChanges(id: number, page: number, enabled = true) {
  const client = useQueryClient();
  const [requests] = useState(() =>
    createDirectChangeRequests(createUuid, () => window.sessionStorage),
  );
  const pending = useSyncExternalStore(
    requests.subscribe,
    () => requests.read(id),
    () => null,
  );
  const query = useQuery({
    enabled,
    queryKey: salesQueryKeys.slips.changes(id, page),
    queryFn: ({ signal }) => getDirectSaleChanges(id, page, signal),
  });
  const mutation = useMutation({
    mutationFn: (request: DirectChangePendingRequest) =>
      submitDirectSaleChange(id, request),
    onSuccess: async (_data, request) => {
      requests.complete(id, request.key);
      await Promise.all([
        client.invalidateQueries({ queryKey: salesQueryKeys.slips.all }),
        client.invalidateQueries({ queryKey: ["sales", "paymentBalance"] }),
        client.invalidateQueries({
          queryKey: ["sales", "allocationWorkspace"],
        }),
        client.invalidateQueries({ queryKey: ["analytics"] }),
        client.invalidateQueries({ queryKey: salesQueryKeys.partners.all }),
      ]);
    },
    onError: (error, request) => {
      if (
        error instanceof ApiError &&
        [400, 404, 409].includes(error.status) &&
        error.code !== "DIRECT_CHANGE_REQUEST_KEY_CONFLICT"
      )
        requests.complete(id, request.key);
    },
  });
  return {
    query,
    pending,
    saving: mutation.isPending,
    error: mutation.error,
    submit: (input: DirectChangeInput) =>
      mutation.mutateAsync(requests.prepare(id, input)),
    retry: () => (pending ? mutation.mutateAsync(pending) : Promise.resolve()),
  };
}
