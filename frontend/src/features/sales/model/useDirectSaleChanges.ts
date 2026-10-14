import { useState, useSyncExternalStore } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError } from "@/shared/api/client";
import { createUuid } from "@/shared/lib/id";
import {
  getDirectSaleChanges,
  getDirectReturnArrivals,
  submitDirectSaleChange,
} from "../api/salesApi";
import {
  createDirectChangeRequests,
  type DirectChangeInput,
  type DirectChangePendingRequest,
} from "../lib/directChangeRequests";
import { salesQueryKeys } from "./salesQueryKeys";

export function useDirectSaleChanges(
  id: number,
  page: number,
  enabled = true,
  arrivalPage = 0,
) {
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
  const arrivals = useQuery({
    enabled,
    queryKey: [
      ...salesQueryKeys.slips.all,
      "returnArrivals",
      id,
      arrivalPage,
      10,
    ],
    queryFn: ({ signal }) => getDirectReturnArrivals(id, arrivalPage, signal),
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
        ...(request.operation === "ARRIVAL" ||
        request.operation === "ARRIVAL_CANCEL"
          ? [
              client.invalidateQueries({ queryKey: ["farm-status"] }),
              client.invalidateQueries({ queryKey: ["inventory"] }),
              client.invalidateQueries({
                queryKey: ["workRecords", "references", "houses"],
              }),
              client.invalidateQueries({
                queryKey: ["sales", "auctionReturnHouses"],
              }),
            ]
          : []),
      ]);
    },
    onError: (error, request) => {
      if (
        error instanceof ApiError &&
        [400, 404, 409].includes(error.status) &&
        ![
          "DIRECT_CHANGE_REQUEST_KEY_CONFLICT",
          "DIRECT_ARRIVAL_REQUEST_KEY_CONFLICT",
        ].includes(error.code ?? "")
      )
        requests.complete(id, request.key);
    },
  });
  return {
    query,
    arrivals,
    pending,
    saving: mutation.isPending,
    error: mutation.error,
    submit: (input: DirectChangeInput) =>
      mutation.mutateAsync(requests.prepare(id, input)),
    retry: () => (pending ? mutation.mutateAsync(pending) : Promise.resolve()),
  };
}
