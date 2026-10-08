import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { AuctionLot, AuctionTrackingSummary } from "@/entities/farm/types";
import { createEmptyPage } from "@/shared/api/page";
import { useUrlPagedListState } from "@/shared/api/useUrlPagedListState";
import { useUrlSearchParamsWriter } from "@/shared/lib/useUrlSearchParamsWriter";
import { createUuid } from "@/shared/lib/id";
import { createAuctionRequestKeys } from "../lib/auctionRequestKeys";
import {
  adjustAuctionQuantity,
  getAuctionLot,
  createAuctionResult,
} from "../api/salesApi";
import type { AuctionRouteState } from "../lib/salesRouteParams";
import {
  AUCTION_FILTER_KEYS,
  createInitialAuctionFilters,
  writeAuctionFilterParams,
} from "../lib/salesUrlFilters";
import {
  auctionLotPageQueryOptions,
  auctionSummaryQueryOptions,
} from "./salesQueryOptions";
import { salesQueryKeys } from "./salesQueryKeys";
import type {
  AuctionQuantityAdjustmentPayload,
  AuctionResultFormPayload,
  CreateAuctionResultPayload,
} from "../api/types";

export function useAuctionTracking({
  routeState,
}: {
  routeState: AuctionRouteState;
}) {
  const queryClient = useQueryClient();
  const lotsQuery = useQuery(auctionLotPageQueryOptions(routeState));
  const summaryQuery = useQuery(auctionSummaryQueryOptions());
  const listState = useUrlPagedListState({
    emptyFilters: createInitialAuctionFilters,
    filterKeys: AUCTION_FILTER_KEYS,
    routeFilters: routeState.filters,
    resetParamKeys: ["lotId", "arrivalPage"],
    writeFilterParams: writeAuctionFilterParams,
  });
  const writeUrlParams = useUrlSearchParamsWriter();
  const setSelectedId = (id: number) =>
    writeUrlParams((params) => {
      params.set("lotId", String(id));
      params.delete("arrivalPage");
    }, "push");
  const [requestKeys] = useState(() =>
    createAuctionRequestKeys(createUuid, () => window.sessionStorage),
  );
  const pageResult =
    lotsQuery.data ??
    createEmptyPage<AuctionLot>(routeState.size, routeState.page);
  const summary = summaryQuery.data ?? createEmptyAuctionSummary();
  const selectedId =
    routeState.selectedLotId ?? pageResult.content[0]?.id ?? null;
  const detailQuery = useQuery({
    queryKey: salesQueryKeys.auction.lot(selectedId ?? 0),
    queryFn: ({ signal }) => getAuctionLot(selectedId!, signal),
    enabled: selectedId != null,
  });
  const selectedLot = detailQuery.data ?? null;

  async function invalidateAuctionTracking(changed: AuctionLot) {
    setSelectedId(changed.id);
    await queryClient.invalidateQueries({
      queryKey: salesQueryKeys.auction.all,
    });
  }

  const createResultMutation = useMutation({
    mutationFn: async ({
      lotId,
      payload,
    }: {
      lotId: number;
      payload: CreateAuctionResultPayload;
    }) => {
      const response = await createAuctionResult(lotId, payload);
      requestKeys.complete(lotId, "RESULT", payload.idempotencyKey);
      return response;
    },
    onSuccess: invalidateAuctionTracking,
  });
  const adjustQuantityMutation = useMutation({
    mutationFn: ({
      lotId,
      payload,
    }: {
      lotId: number;
      payload: AuctionQuantityAdjustmentPayload;
    }) => adjustAuctionQuantity(lotId, payload),
    onSuccess: invalidateAuctionTracking,
  });

  async function adjustQuantity(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedLot) return;
    const data = new FormData(event.currentTarget);
    await adjustQuantityMutation.mutateAsync({
      lotId: selectedLot.id,
      payload: {
        soldQuantity: Number(data.get("soldQuantity")),
        waitingQuantity: Number(data.get("waitingQuantity")),
        returnedQuantity: Number(data.get("returnedQuantity")),
        worker: String(data.get("worker") || "") || null,
        memo: String(data.get("memo") || "") || null,
      },
    });
  }

  async function addResult(payload: AuctionResultFormPayload) {
    if (!selectedLot) return;
    await createResultMutation.mutateAsync({
      lotId: selectedLot.id,
      payload: {
        idempotencyKey: requestKeys.get(selectedLot.id, "RESULT"),
        auctionDate: payload.auctionDate,
        attemptNo: null,
        attemptStatus: payload.attemptStatus,
        failedReason: payload.failedReason,
        memo: payload.memo,
        resultLines: payload.resultLines,
      },
    });
  }

  const mutationPending =
    createResultMutation.isPending || adjustQuantityMutation.isPending;
  const error =
    detailQuery.error ??
    lotsQuery.error ??
    summaryQuery.error ??
    createResultMutation.error ??
    adjustQuantityMutation.error;

  return {
    lots: pageResult.content,
    page: routeState.page,
    pageSize: routeState.size,
    totalElements: pageResult.totalElements,
    totalPages: Math.max(1, pageResult.totalPages),
    summary,
    filters: listState.filters,
    selectedLot,
    loading: lotsQuery.isFetching || summaryQuery.isFetching || mutationPending,
    listLoading: lotsQuery.isFetching,
    error: error == null ? null : toMessage(error),
    updateFilter: listState.updateFilter,
    search: listState.search,
    resetFilters: listState.reset,
    setPage: listState.changePage,
    setPageSize: listState.changePageSize,
    setSelectedId,
    adjustQuantity,
    addResult,
  };
}

function createEmptyAuctionSummary(): AuctionTrackingSummary {
  return {
    lotCount: 0,
    shippedQuantity: 0,
    soldQuantity: 0,
    waitingQuantity: 0,
    returnedQuantity: 0,
    reviewRequiredCount: 0,
    totalAmount: 0,
  };
}

function toMessage(error: unknown) {
  return error instanceof Error
    ? error.message
    : "요청 중 문제가 발생했습니다.";
}
