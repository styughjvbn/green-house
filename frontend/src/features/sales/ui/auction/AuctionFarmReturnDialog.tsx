"use client";

import type { MouseEvent, ReactNode, Ref } from "react";
import type { UseQueryResult } from "@tanstack/react-query";
import type { AuctionLot } from "@/entities/farm/types";
import type { Page } from "@/shared/api/page";
import { formatShortDate } from "@/shared/lib/dateFormat";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/shared/ui/primitives/dialog";
import type { AuctionArrival, AuctionFollowUp } from "../../api/types";

export function AuctionFarmReturnDialog({
  lot,
  summary,
  arrivals,
  pageIndex,
  blocked,
  feedback,
  contentRef,
  onClose,
  onReturnFocus,
  onPageChange,
  onArrival,
  onCancelArrival,
}: {
  lot: AuctionLot;
  summary: AuctionFollowUp;
  arrivals: UseQueryResult<Page<AuctionArrival>, Error>;
  pageIndex: number;
  blocked: boolean;
  feedback: ReactNode;
  contentRef: Ref<HTMLDivElement>;
  onClose: () => void;
  onReturnFocus: () => void;
  onPageChange: (page: number) => void;
  onArrival: (event: MouseEvent<HTMLButtonElement>) => void;
  onCancelArrival: (
    arrival: AuctionArrival,
    event: MouseEvent<HTMLButtonElement>,
  ) => void;
}) {
  const page = arrivals.data;
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent
        ref={contentRef}
        className="max-h-[90dvh] max-w-2xl overflow-y-auto rounded-lg p-5"
        onCloseAutoFocus={(event) => {
          event.preventDefault();
          onReturnFocus();
        }}
      >
        <DialogTitle className="pr-10 text-lg font-bold">농장 반환</DialogTitle>
        <DialogDescription className="mt-2 text-sm text-[#68756c]">
          LOT #{lot.id} · {lot.varietyName}
        </DialogDescription>
        <div className="mt-4 space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-sm">
              도착 대기 {summary.pendingQuantity.toLocaleString()}분
            </p>
            <button
              type="button"
              className="rounded-md bg-[#159447] px-3 py-2 text-sm font-semibold text-white disabled:opacity-50"
              disabled={blocked || summary.arrivalAllowed !== true}
              onClick={onArrival}
            >
              반환분 배치
            </button>
          </div>
          {summary.inferredReturnQuantity > 0 ? (
            <p className="text-xs text-[#68756c]">
              과거 반환 추정 {summary.inferredReturnQuantity.toLocaleString()}분
              · 실제 도착과 구분됩니다.
            </p>
          ) : null}
          {feedback}
          <h3 className="text-sm font-semibold">실제 도착 이력</h3>
          {arrivals.isPending ? (
            <p role="status">도착 이력을 불러오는 중입니다.</p>
          ) : null}
          {arrivals.error ? (
            <p role="alert" className="text-sm text-red-700">
              도착 이력을 조회하지 못했습니다.{" "}
              <button type="button" onClick={() => void arrivals.refetch()}>
                다시 조회
              </button>
            </p>
          ) : null}
          {page ? (
            <>
              {page.content.length === 0 ? (
                <p className="text-sm text-[#68756c]">
                  기록된 실제 도착이 없습니다.
                </p>
              ) : (
                <ul className="space-y-2">
                  {page.content.map((arrival) => (
                    <li
                      key={arrival.id}
                      className="rounded border bg-white p-3 text-sm"
                    >
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <span>
                          {formatShortDate(arrival.arrivalDate)} ·{" "}
                          {arrival.quantity.toLocaleString()}분 ·{" "}
                          {arrival.canceledAt ? "도착 취소됨" : "실제 도착"}
                        </span>
                        <button
                          type="button"
                          disabled={
                            blocked || arrival.cancellationAllowed !== true
                          }
                          className="rounded border px-2 py-1 disabled:opacity-50"
                          onClick={(event) => onCancelArrival(arrival, event)}
                        >
                          도착 취소 정정
                        </button>
                      </div>
                      <p className="mt-1 text-xs text-[#68756c]">
                        생성 난 묶음 #{arrival.orchidGroupId} ·{" "}
                        {arrival.worker || "담당자 미지정"}
                      </p>
                      {arrival.cancellationReason ? (
                        <p className="mt-1 text-xs">
                          취소 사유: {arrival.cancellationReason}
                        </p>
                      ) : null}
                    </li>
                  ))}
                </ul>
              )}
              <nav
                className="flex items-center justify-end gap-3 text-sm"
                aria-label="도착 이력 페이지"
              >
                <button
                  type="button"
                  disabled={pageIndex <= 0 || arrivals.isFetching}
                  onClick={() => onPageChange(pageIndex - 1)}
                >
                  이전
                </button>
                <span>
                  {pageIndex + 1} / {Math.max(1, page.totalPages)}
                </span>
                <button
                  type="button"
                  disabled={
                    pageIndex + 1 >= page.totalPages || arrivals.isFetching
                  }
                  onClick={() => onPageChange(pageIndex + 1)}
                >
                  다음
                </button>
              </nav>
            </>
          ) : null}
        </div>
      </DialogContent>
    </Dialog>
  );
}
