import { FormEvent, useMemo, useState } from "react";
import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import type {
  AuctionLot,
  AuctionLotPage,
  AuctionTrackingSummary,
} from "@/entities/farm/types";
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
  AuctionAttemptStatus,
  AuctionInspectionStatus,
} from "@/entities/farm/types";

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
  queryFilters,
  queryPage,
  querySize,
}: {
  initialPage: AuctionLotPage;
  initialSummary: AuctionTrackingSummary;
  initialFilters: AuctionFilterState;
  queryFilters: AuctionFilterState;
  queryPage: number;
  querySize: number;
}) {
  const queryClient = useQueryClient();
  const [filters, setFilters] = useState(initialFilters);
  const [selectedId, setSelectedId] = useState<number | null>(
    initialPage.content[0]?.id ?? null,
  );
  const isInitialLotsQuery =
    queryFilters === initialFilters &&
    queryPage === initialPage.page &&
    querySize === initialPage.size;

  const lotsQuery = useQuery({
    queryKey: auctionTrackingKeys.lots(queryFilters, queryPage, querySize),
    queryFn: () => getAuctionLots(queryFilters, queryPage, querySize),
    initialData: isInitialLotsQuery ? initialPage : undefined,
    placeholderData: keepPreviousData,
  });
  const summaryQuery = useQuery({
    queryKey: auctionTrackingKeys.summary(),
    queryFn: getAuctionTrackingSummary,
    initialData: initialSummary,
  });

  const pageResult = lotsQuery.data ?? initialPage;
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
      payload: {
        auctionDate: string;
        attemptNo: null;
        attemptStatus: AuctionAttemptStatus;
        failedReason: string | null;
        memo: string | null;
        resultLines?: Array<{
          auctionGrade: string | null;
          quantity: number;
          unitPrice: number;
          note: string | null;
          inspectionStatus: AuctionInspectionStatus | null;
        }>;
      };
    }) => createAuctionResult(lotId, payload),
    onSuccess: invalidateAuctionTracking,
  });
  const confirmReturnMutation = useMutation({
    mutationFn: ({
      lotId,
      payload,
    }: {
      lotId: number;
      payload: {
        returnedQuantity: number;
        returnDate: string;
        worker: string | null;
        memo: string | null;
      };
    }) => confirmAuctionReturn(lotId, payload),
    onSuccess: invalidateAuctionTracking,
  });
  const adjustQuantityMutation = useMutation({
    mutationFn: ({
      lotId,
      payload,
    }: {
      lotId: number;
      payload: {
        soldQuantity: number;
        waitingQuantity: number;
        returnedQuantity: number;
        worker: string | null;
        memo: string | null;
      };
    }) => adjustAuctionQuantity(lotId, payload),
    onSuccess: invalidateAuctionTracking,
  });

  function updateFilter<K extends keyof AuctionFilterState>(
    field: K,
    value: AuctionFilterState[K],
  ) {
    setFilters((current) => ({ ...current, [field]: value }));
  }

  function resetFilters() {
    setFilters(createInitialAuctionFilters());
  }

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

  async function addResult(payload: {
    auctionDate: string;
    attemptStatus: AuctionAttemptStatus;
    failedReason: string | null;
    memo: string | null;
    resultLines?: Array<{
      auctionGrade: string | null;
      quantity: number;
      unitPrice: number;
      note: string | null;
      inspectionStatus: AuctionInspectionStatus | null;
    }>;
  }) {
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
    page: pageResult.page + 1,
    pageSize: pageResult.size,
    totalElements: pageResult.totalElements,
    totalPages: Math.max(1, pageResult.totalPages),
    summary,
    filters,
    selectedLot,
    loading: lotsQuery.isFetching || summaryQuery.isFetching || mutationPending,
    error: error == null ? null : toMessage(error),
    updateFilter,
    resetFilters,
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
