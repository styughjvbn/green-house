"use client";

import { useState, useRef, useEffect } from "react";
import type { AuctionLot } from "@/entities/farm/types";
import { formatShortDate } from "@/shared/lib/dateFormat";
import { useUrlSearchParamsWriter } from "@/shared/lib/useUrlSearchParamsWriter";
import type { AuctionArrival } from "../../api/types";
import { useAuctionFollowUp } from "../../model/useAuctionFollowUp";
import { followUpLabels } from "../../lib/auctionDisplay";
import { AuctionFollowUpDialog } from "./AuctionFollowUpDialog";

export function AuctionFollowUpPanel({
  lot,
  arrivalPage,
}: {
  lot: AuctionLot;
  arrivalPage: number;
}) {
  const state = useAuctionFollowUp(lot.id, arrivalPage);
  const writeUrl = useUrlSearchParamsWriter();
  const [dialog, setDialog] = useState<
    "decision" | "arrival" | AuctionArrival | null
  >(null);
  const triggerRef = useRef<HTMLButtonElement | null>(null);
  const sectionRef = useRef<HTMLElement | null>(null);
  const summary = state.followUp.data;
  const page = state.arrivals.data;
  useEffect(() => {
    if (page && arrivalPage >= Math.max(1, page.totalPages)) {
      writeUrl((params) =>
        params.set("arrivalPage", String(Math.max(0, page.totalPages - 1))),
      );
    }
  }, [arrivalPage, page, writeUrl]);
  const blocked =
    state.saving || state.pendingRequest != null || state.followUp.isFetching;
  function setPage(value: number) {
    writeUrl((params) => {
      params.set("lotId", String(lot.id));
      params.set("arrivalPage", String(value));
    }, "push");
  }
  return (
    <section
      className="space-y-3 border-b border-[#e7ebe5] bg-[#f8faf7] p-4"
      aria-label="유찰 잔량 후속 처리"
      ref={sectionRef}
      tabIndex={-1}
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-sm font-bold">유찰 잔량 후속 처리</h3>
        <div className="flex gap-2">
          <button
            type="button"
            className="rounded-md border bg-white px-3 py-2 text-sm font-semibold disabled:opacity-50"
            disabled={blocked || summary?.decisionChangeAllowed !== true}
            onClick={(event) => {
              triggerRef.current = event.currentTarget;
              setDialog("decision");
            }}
          >
            처리 방법 선택
          </button>
          <button
            type="button"
            className="rounded-md bg-[#159447] px-3 py-2 text-sm font-semibold text-white disabled:opacity-50"
            disabled={blocked || summary?.arrivalAllowed !== true}
            onClick={(event) => {
              triggerRef.current = event.currentTarget;
              setDialog("arrival");
            }}
          >
            실제 도착 기록
          </button>
        </div>
      </div>
      {state.followUp.isPending ? (
        <p role="status">후속 처리를 불러오는 중입니다.</p>
      ) : null}
      {state.followUp.error ? (
        <p role="alert" className="text-sm text-red-700">
          {state.followUp.error.message}{" "}
          <button type="button" onClick={() => void state.followUp.refetch()}>
            다시 조회
          </button>
        </p>
      ) : null}
      {summary ? (
        <div className="space-y-1 text-sm">
          <p>
            처리 방법:{" "}
            <strong>
              {summary.method ? followUpLabels[summary.method] : "미선택"}
            </strong>{" "}
            · 도착 대기 {summary.pendingQuantity.toLocaleString()}분
          </p>
          {summary.method === "AUCTION_DISPOSAL" ? (
            <p>
              경매장 처리 완료 {summary.disposedQuantity.toLocaleString()}분
            </p>
          ) : null}
          {summary.inferredReturnQuantity > 0 ? (
            <p>
              과거 반환 추정 {summary.inferredReturnQuantity.toLocaleString()}분
              · 실제 도착과 구분됩니다.
            </p>
          ) : null}
          <p className="text-xs text-[#68756c]">
            처리 방법은 유찰 잔량 전체에 적용합니다. 농장 반환 결정 후 실제
            도착품을 별도로 기록합니다.
          </p>
          {!summary.decisionChangeAllowed &&
          summary.method === "FARM_RETURN" ? (
            <p className="text-xs">
              도착 기록이 있으면 먼저 도착 취소 정정을 완료한 뒤 처리 방법을
              다시 선택하세요.
            </p>
          ) : null}
        </div>
      ) : null}
      {state.pendingRequest ? (
        <div
          className="rounded border border-amber-300 bg-amber-50 p-3 text-sm"
          role="status"
        >
          <p>
            완료 여부를 확인하지 못한 요청이 있습니다. 같은 내용으로 다시
            확인하세요.
          </p>
          <button
            type="button"
            disabled={state.saving}
            className="mt-2 rounded border bg-white px-3 py-2 font-semibold disabled:opacity-50"
            onClick={() => void state.retry().catch(() => undefined)}
          >
            {state.saving ? "확인 중…" : "이전 요청 다시 확인"}
          </button>
        </div>
      ) : null}
      {state.mutationError ? (
        <p role="alert" className="text-sm whitespace-pre-line text-red-700">
          {state.mutationError.message}
        </p>
      ) : null}
      <h4 className="text-sm font-semibold">실제 도착 이력</h4>
      {state.arrivals.isPending ? (
        <p role="status">도착 이력을 불러오는 중입니다.</p>
      ) : null}
      {state.arrivals.error ? (
        <p role="alert" className="text-sm text-red-700">
          도착 이력을 조회하지 못했습니다.{" "}
          <button type="button" onClick={() => void state.arrivals.refetch()}>
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
                      disabled={blocked || arrival.cancellationAllowed !== true}
                      className="rounded border px-2 py-1 disabled:opacity-50"
                      onClick={(event) => {
                        triggerRef.current = event.currentTarget;
                        setDialog(arrival);
                      }}
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
              disabled={arrivalPage <= 0 || state.arrivals.isFetching}
              onClick={() => setPage(arrivalPage - 1)}
            >
              이전
            </button>
            <span>
              {arrivalPage + 1} / {Math.max(1, page.totalPages)}
            </span>
            <button
              type="button"
              disabled={
                arrivalPage + 1 >= page.totalPages || state.arrivals.isFetching
              }
              onClick={() => setPage(arrivalPage + 1)}
            >
              다음
            </button>
          </nav>
        </>
      ) : null}
      {dialog && summary ? (
        <AuctionFollowUpDialog
          key={typeof dialog === "string" ? dialog : dialog.id}
          mode={dialog}
          lot={lot}
          summary={summary}
          blocked={blocked}
          saving={state.saving}
          onClose={() => setDialog(null)}
          onReturnFocus={() => {
            if (triggerRef.current && !triggerRef.current.disabled)
              triggerRef.current.focus();
            else sectionRef.current?.focus();
          }}
          onSubmit={async (input) => {
            await state.submit(input);
            setDialog(null);
          }}
        />
      ) : null}
    </section>
  );
}
