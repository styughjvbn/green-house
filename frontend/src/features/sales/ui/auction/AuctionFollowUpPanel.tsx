"use client";

import {
  useState,
  useRef,
  useEffect,
  type ReactNode,
  type MouseEvent,
} from "react";
import type { AuctionLot } from "@/entities/farm/types";
import { useUrlSearchParamsWriter } from "@/shared/lib/useUrlSearchParamsWriter";
import type { AuctionArrival, AuctionFollowUpMethod } from "../../api/types";
import { useAuctionFollowUp } from "../../model/useAuctionFollowUp";
import { AuctionFarmReturnDialog } from "./AuctionFarmReturnDialog";
import { AuctionFollowUpDialog } from "./AuctionFollowUpDialog";

export function AuctionFollowUpPanel({
  lot,
  arrivalPage,
  children,
}: {
  lot: AuctionLot;
  arrivalPage: number;
  children: (actions: {
    openDecision: (event: MouseEvent<HTMLButtonElement>) => void;
    openReturn: (event: MouseEvent<HTMLButtonElement>) => void;
    decisionBlocked: boolean;
    selectedMethod: AuctionFollowUpMethod | null;
  }) => ReactNode;
}) {
  const state = useAuctionFollowUp(lot.id, arrivalPage);
  const writeUrl = useUrlSearchParamsWriter();
  const [dialog, setDialog] = useState<
    "decision" | "arrival" | AuctionArrival | null
  >(null);
  const [trigger, setTrigger] = useState<HTMLButtonElement | null>(null);
  const sectionRef = useRef<HTMLDivElement | null>(null);
  const returnContentRef = useRef<HTMLDivElement | null>(null);
  const [showReturnInfo, setShowReturnInfo] = useState(false);
  const [returnTrigger, setReturnTrigger] = useState<HTMLButtonElement | null>(
    null,
  );
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
  const feedback = (
    <>
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
    </>
  );
  return (
    <div ref={sectionRef} tabIndex={-1}>
      {children({
        openDecision: (event) => {
          setTrigger(event.currentTarget);
          setDialog("decision");
        },
        openReturn: (event) => {
          setReturnTrigger(event.currentTarget);
          setShowReturnInfo(true);
        },
        decisionBlocked: blocked || summary?.decisionChangeAllowed !== true,
        selectedMethod: summary?.method ?? null,
      })}
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
      {!showReturnInfo ? feedback : null}
      {showReturnInfo && summary ? (
        <AuctionFarmReturnDialog
          lot={lot}
          summary={summary}
          arrivals={state.arrivals}
          pageIndex={arrivalPage}
          blocked={blocked}
          feedback={feedback}
          contentRef={returnContentRef}
          onClose={() => {
            if (!state.saving) setShowReturnInfo(false);
          }}
          onReturnFocus={() => {
            if (returnTrigger?.isConnected) returnTrigger.focus();
            else sectionRef.current?.focus();
          }}
          onPageChange={setPage}
          onArrival={(event) => {
            setTrigger(event.currentTarget);
            setDialog("arrival");
          }}
          onCancelArrival={(arrival, event) => {
            setTrigger(event.currentTarget);
            setDialog(arrival);
          }}
        />
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
            if (trigger && !trigger.disabled) trigger.focus();
            else if (showReturnInfo) returnContentRef.current?.focus();
            else sectionRef.current?.focus();
          }}
          onSubmit={async (input) => {
            await state.submit(input);
            setDialog(null);
          }}
        />
      ) : null}
    </div>
  );
}
