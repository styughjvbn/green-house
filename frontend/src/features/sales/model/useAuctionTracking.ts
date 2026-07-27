import { useMemo, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type {
  AuctionLot,
  AuctionLotPage,
  AuctionTrackingSummary,
} from "@/entities/farm/types";
import { usePagedListQuery } from "@/shared/api/usePagedListQuery";
import {
  adjustAuctionQuantity,
  confirmAuctionReturn,
  createAuctionResult,
  getAuctionLots,
  getAuctionTrackingSummary,
} from "../api/salesApi";
import { createInitialAuctionFilters } from "../lib/salesUrlFilters";
import type { AuctionFilterState } from "./types";
import type {
  AuctionQuantityAdjustmentPayload,
  AuctionResultFormPayload,
  AuctionReturnPayload,
} from "../api/types";

const auctionTrackingKeys = {
  all: ["sales", "auctionTracking"] as const,
  lots: (filters: AuctionFilterState, page: number, size: number) =>
    [...auctionTrackingKeys.all, "lots", filters, page, size] as const,
  summary: () => [...auctionTrackingKeys.all, "summary"] as const,
};

export function useAuctionTracking({
  initialPage,
  initialSummary,
  initialFilters,
}: {
  initialPage: AuctionLotPage;
  initialSummary: AuctionTrackingSummary;
  initialFilters: AuctionFilterState;
}) {
  const queryClient = useQueryClient();
  const listState = usePagedListQuery({
    createEmptyFilters: createInitialAuctionFilters,
    initialFilters,
    initialPage,
    queryKey: ({ filters, page, size }) =>
      auctionTrackingKeys.lots(filters, page, size),
    queryFn: ({ filters, page, size }) => getAuctionLots(filters, page, size),
  });
  const {
    filters: queryFilters,
    page: queryPage,
    size: querySize,
  } = listState.queryState;
  const [selectedId, setSelectedId] = useState<number | null>(
    initialPage.content[0]?.id ?? null,
  );
  const lotsQuery = listState.query;
  const summaryQuery = useQuery({
    queryKey: auctionTrackingKeys.summary(),
    queryFn: getAuctionTrackingSummary,
    initialData: initialSummary,
  });

  const pageResult = listState.pageData ?? initialPage;
  const summary = summaryQuery.data;
  const selectedLot = useMemo(
    () =>
      pageResult.content.find((lot) => lot.id === selectedId) ??
      pageResult.content[0] ??
      null,
    [pageResult.content, selectedId],
  );

  async function invalidateAuctionTracking(changed: AuctionLot) {
    setSelectedId(changed.id);
    await queryClient.invalidateQueries({
      queryKey: auctionTrackingKeys.all,
    });
  }

  const createResultMutation = useMutation({
    mutationFn: ({
      lotId,
      payload,
    }: {
      lotId: number;
      payload: AuctionResultFormPayload & { attemptNo: null };
    }) => createAuctionResult(lotId, payload),
    onSuccess: invalidateAuctionTracking,
  });
  const confirmReturnMutation = useMutation({
    mutationFn: ({
      lotId,
      payload,
    }: {
      lotId: number;
      payload: AuctionReturnPayload;
    }) => confirmAuctionReturn(lotId, payload),
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

  async function confirmReturn(returnedQuantity: number, returnDate: string) {
    if (!selectedLot) return;
    const result =
      returnedQuantity === selectedLot.returnConfirmableQuantity
        ? "반환완료"
        : "부분반환";
    if (
      !window.confirm(
        `반환 수량 ${returnedQuantity.toLocaleString()}분, 반환 날짜 ${returnDate}가 맞습니까?\n확인하면 ${result} 상태로 변경됩니다.`,
      )
    )
      return;
    await confirmReturnMutation.mutateAsync({
      lotId: selectedLot.id,
      payload: {
        returnedQuantity,
        returnDate,
        worker: null,
        memo: "판매 관리 화면에서 반환 확인",
      },
    });
  }

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
    createResultMutation.isPending ||
    confirmReturnMutation.isPending ||
    adjustQuantityMutation.isPending;
  const error =
    lotsQuery.error ??
    summaryQuery.error ??
    createResultMutation.error ??
    confirmReturnMutation.error ??
    adjustQuantityMutation.error;

  return {
    lots: pageResult.content,
    page: queryPage,
    pageSize: querySize,
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
    confirmReturn,
    adjustQuantity,
    addResult,
  };
}

function toMessage(error: unknown) {
  return error instanceof Error
    ? error.message
    : "요청 중 문제가 발생했습니다.";
}
