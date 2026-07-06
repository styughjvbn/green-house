import { FormEvent, useMemo, useState } from "react";
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
import type { AuctionFilterState } from "./types";
import type {
  AuctionAttemptStatus,
  AuctionInspectionStatus,
} from "@/entities/farm/types";

const emptyFilters: AuctionFilterState = {
  from: "",
  to: "",
  market: "",
  variety: "",
  grade: "",
  status: "",
  keyword: "",
  reviewOnly: false,
  returnOnly: false,
  waitingOnly: false,
};

export function useAuctionTracking(
  initialPage: AuctionLotPage,
  initialSummary: AuctionTrackingSummary,
) {
  const [pageResult, setPageResult] = useState(initialPage);
  const [summary, setSummary] = useState(initialSummary);
  const [filters, setFilters] = useState(emptyFilters);
  const [selectedId, setSelectedId] = useState<number | null>(
    initialPage.content[0]?.id ?? null,
  );
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selectedLot = useMemo(
    () =>
      pageResult.content.find((lot) => lot.id === selectedId) ??
      pageResult.content[0] ??
      null,
    [pageResult.content, selectedId],
  );

  function updateFilter<K extends keyof AuctionFilterState>(
    field: K,
    value: AuctionFilterState[K],
  ) {
    setFilters((current) => ({ ...current, [field]: value }));
  }

  async function refresh(
    nextFilters = filters,
    nextPage = 0,
    nextSize = pageResult.size,
  ) {
    setLoading(true);
    setError(null);
    try {
      const [nextPageResult, nextSummary] = await Promise.all([
        getAuctionLots(nextFilters, nextPage, nextSize),
        getAuctionTrackingSummary(),
      ]);
      setPageResult(nextPageResult);
      setSummary(nextSummary);
      setSelectedId((current) =>
        nextPageResult.content.some((lot) => lot.id === current)
          ? current
          : (nextPageResult.content[0]?.id ?? null),
      );
    } catch (caught) {
      setError(toMessage(caught));
    } finally {
      setLoading(false);
    }
  }

  async function resetFilters() {
    setFilters(emptyFilters);
    await refresh(emptyFilters);
  }

  async function changePage(page: number) {
    await refresh(filters, page - 1, pageResult.size);
  }

  async function changePageSize(size: number) {
    await refresh(filters, 0, size);
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
    await mutate(() =>
      confirmAuctionReturn(selectedLot.id, {
        returnedQuantity,
        returnDate,
        worker: null,
        memo: "판매 관리 화면에서 반환 확인",
      }),
    );
  }

  async function adjustQuantity(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedLot) return;
    const data = new FormData(event.currentTarget);
    await mutate(() =>
      adjustAuctionQuantity(selectedLot.id, {
        soldQuantity: Number(data.get("soldQuantity")),
        waitingQuantity: Number(data.get("waitingQuantity")),
        returnedQuantity: Number(data.get("returnedQuantity")),
        worker: String(data.get("worker") || "") || null,
        memo: String(data.get("memo") || "") || null,
      }),
    );
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
    await mutate(() =>
      createAuctionResult(selectedLot.id, {
        auctionDate: payload.auctionDate,
        attemptNo: null,
        attemptStatus: payload.attemptStatus,
        failedReason: payload.failedReason,
        memo: payload.memo,
        resultLines: payload.resultLines,
      }),
    );
  }

  async function mutate(request: () => Promise<AuctionLot>) {
    setLoading(true);
    setError(null);
    try {
      const changed = await request();
      setPageResult((current) => ({
        ...current,
        content: current.content.map((lot) =>
          lot.id === changed.id ? changed : lot,
        ),
      }));
      setSelectedId(changed.id);
      setSummary(await getAuctionTrackingSummary());
    } catch (caught) {
      setError(toMessage(caught));
    } finally {
      setLoading(false);
    }
  }

  return {
    lots: pageResult.content,
    page: pageResult.page + 1,
    pageSize: pageResult.size,
    totalElements: pageResult.totalElements,
    totalPages: Math.max(1, pageResult.totalPages),
    summary,
    filters,
    selectedLot,
    loading,
    error,
    updateFilter,
    setSelectedId,
    refresh,
    resetFilters,
    changePage,
    changePageSize,
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
