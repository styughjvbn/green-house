import { useState, useSyncExternalStore } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError } from "@/shared/api/client";
import { createUuid } from "@/shared/lib/id";
import {
  createAuctionFollowUpRequests,
  type AuctionFollowUpInput,
  type AuctionFollowUpRequest,
} from "../lib/auctionFollowUpRequest";
import {
  cancelAuctionArrival,
  decideAuctionFollowUp,
  getAuctionArrivals,
  getAuctionFollowUp,
  recordAuctionArrival,
} from "../api/salesApi";
import { salesQueryKeys } from "./salesQueryKeys";

export function useAuctionFollowUp(lotId: number, page: number) {
  const client = useQueryClient();
  const [requests] = useState(() =>
    createAuctionFollowUpRequests(createUuid, () => window.sessionStorage),
  );
  const pendingRequest = useSyncExternalStore(
    requests.subscribe,
    () => requests.read(lotId),
    () => null,
  );
  const followUp = useQuery({
    queryKey: salesQueryKeys.auction.followUp(lotId),
    queryFn: ({ signal }) => getAuctionFollowUp(lotId, signal),
  });
  const arrivals = useQuery({
    queryKey: salesQueryKeys.auction.arrivals(lotId, page),
    queryFn: ({ signal }) => getAuctionArrivals(lotId, page, signal),
  });
  const mutation = useMutation({
    mutationFn: async (request: AuctionFollowUpRequest) => {
      switch (request.operation) {
        case "FOLLOW_UP":
          return decideAuctionFollowUp(lotId, request.payload);
        case "ARRIVAL":
          return recordAuctionArrival(lotId, request.payload);
        case "ARRIVAL_CANCEL":
          return cancelAuctionArrival(
            lotId,
            request.arrivalId,
            request.payload,
          );
      }
    },
    onSuccess: async (_response, request) => {
      requests.complete(lotId, request.payload.idempotencyKey);
      // Receipt replay returns the original response. Refetch the current facts instead of caching it.
      await Promise.all([
        client.invalidateQueries({ queryKey: salesQueryKeys.auction.all }),
        ...(request.operation === "FOLLOW_UP"
          ? []
          : [
              client.invalidateQueries({ queryKey: ["farm-status"] }),
              client.invalidateQueries({ queryKey: ["inventory", "houses"] }),
              client.invalidateQueries({
                queryKey: ["inventory", "varieties"],
              }),
              client.invalidateQueries({
                queryKey: ["workRecords", "references", "houses"],
              }),
              client.invalidateQueries({
                queryKey: ["sales", "auctionReturnHouses"],
              }),
            ]),
      ]);
    },
    onError: (error, request) => {
      // Definite validation/transaction rejection may be corrected. Transport/5xx stays replayable.
      if (
        error instanceof ApiError &&
        [400, 404, 409].includes(error.status) &&
        error.code !== "AUCTION_REQUEST_KEY_CONFLICT"
      ) {
        requests.complete(lotId, request.payload.idempotencyKey);
      }
    },
  });
  async function submit(input: AuctionFollowUpInput) {
    const request = requests.prepare(lotId, input);
    await mutation.mutateAsync(request);
  }
  return {
    followUp,
    arrivals,
    submit,
    pendingRequest,
    saving: mutation.isPending,
    mutationError: mutation.error,
    retry: async () => {
      if (pendingRequest) await mutation.mutateAsync(pendingRequest);
    },
  };
}
