"use client";

import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import type { WorkOperation } from "@/entities/farm/types";
import { createUuid } from "@/shared/lib/id";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/shared/ui/primitives/dialog";
import {
  cancelWorkOperation,
  getWorkOperationCancellationEligibility,
} from "../../api/workRecordApi";

export function WorkOperationVoidDialog({
  operation,
  onClose,
  onSaved,
}: {
  operation: WorkOperation;
  onClose: () => void;
  onSaved: (operation: WorkOperation) => void;
}) {
  const [reason, setReason] = useState("");
  const [idempotencyKey] = useState(() =>
    `work-cancel-${operation.id}-${createUuid()}`.slice(0, 100),
  );
  const eligibility = useQuery({
    queryKey: ["work-operations", operation.id, "cancel-eligibility"],
    queryFn: () => getWorkOperationCancellationEligibility(operation.id),
  });
  const voidMutation = useMutation({
    mutationFn: () =>
      cancelWorkOperation(operation.id, {
        idempotencyKey,
        reason: reason.trim(),
      }),
    onSuccess(updated) {
      onSaved(updated);
      onClose();
    },
  });
  const data = eligibility.data;
  const error =
    eligibility.error instanceof Error
      ? eligibility.error.message
      : voidMutation.error instanceof Error
        ? voidMutation.error.message
        : null;

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="flex max-h-[calc(100dvh-2rem)] max-w-xl flex-col overflow-hidden rounded-lg">
        <header className="shrink-0 border-b border-[#e4e9e3] p-5 pr-14">
          <DialogTitle className="text-lg font-bold text-[#17251b]">
            작업 취소
          </DialogTitle>
          <DialogDescription className="mt-1 text-sm text-[#657168]">
            {operation.title}의 기록과 적용 효과를 되돌립니다.
          </DialogDescription>
        </header>

        <div className="min-h-0 space-y-4 overflow-y-auto p-5">
          {eligibility.isPending ? (
            <p className="rounded-md bg-[#f4f7f3] p-4 text-sm text-[#657168]">
              되돌릴 수 있는지 확인하는 중입니다.
            </p>
          ) : data ? (
            <>
              <section className="rounded-md border border-[#dfe7df] bg-[#f8faf7] p-4">
                <h3 className="text-sm font-bold text-[#263d2c]">영향 범위</h3>
                <div className="mt-3 grid grid-cols-3 gap-2 text-center text-xs">
                  <ImpactCount
                    label="Mutation"
                    value={data.mutationIds.length}
                  />
                  <ImpactCount
                    label="원본 묶음"
                    value={data.sourceOrchidGroupIds.length}
                  />
                  <ImpactCount
                    label="결과 묶음"
                    value={data.resultOrchidGroupIds.length}
                  />
                </div>
                {data.relatedWorkOperationIds.length > 0 ? (
                  <p className="mt-3 text-xs text-[#526057]">
                    연관 작업 {data.relatedWorkOperationIds.length}건도 같은
                    트랜잭션에서 함께 취소됩니다.
                  </p>
                ) : null}
              </section>

              {data.cancellable ? (
                <p className="rounded-md border border-[#b8ddc1] bg-[#eff9f1] p-3 text-sm text-[#176b35]">
                  후속 변경이 없어 이 작업을 취소할 수 있습니다.
                </p>
              ) : (
                <section className="rounded-md border border-[#efc4b9] bg-[#fff4ef] p-3">
                  <p className="text-sm font-bold text-[#9b341e]">
                    지금은 취소할 수 없습니다.
                  </p>
                  <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-[#7c4132]">
                    {data.blockers.map((blocker) => (
                      <li key={`${blocker.code}-${blocker.message}`}>
                        {blocker.message}
                        {blocker.count > 1 ? ` (${blocker.count}건)` : ""}
                      </li>
                    ))}
                  </ul>
                </section>
              )}

              <label className="block text-sm font-semibold text-[#435047]">
                취소 사유
                <textarea
                  className="mt-1 min-h-24 w-full resize-y rounded-md border border-[#cfd8cc] p-3 font-normal"
                  maxLength={1000}
                  placeholder="잘못 입력한 작업 내용과 취소 이유를 남겨주세요."
                  required
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                />
              </label>
              <p className="text-xs text-[#69756d]">
                실제로 수행한 작업이 아니라 잘못 등록한 작업을 바로잡을 때만
                사용하세요. 기존 기록과 보상 이력은 삭제되지 않습니다.
              </p>
            </>
          ) : null}

          {error ? (
            <p className="rounded-md border border-[#efc4b9] bg-[#fff4ef] p-3 text-sm text-[#9b341e]">
              {error}
            </p>
          ) : null}
        </div>

        <footer className="flex shrink-0 justify-end gap-2 border-t border-[#e4e9e3] p-4">
          <button
            className="rounded-md border border-[#d7ddd4] px-4 py-2 text-sm font-semibold"
            type="button"
            onClick={onClose}
          >
            닫기
          </button>
          <button
            className="rounded-md bg-[#b43b24] px-4 py-2 text-sm font-bold text-white disabled:opacity-45"
            disabled={
              !data?.cancellable || !reason.trim() || voidMutation.isPending
            }
            type="button"
            onClick={() => voidMutation.mutate()}
          >
            {voidMutation.isPending ? "취소 중…" : "작업 취소"}
          </button>
        </footer>
      </DialogContent>
    </Dialog>
  );
}

function ImpactCount({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-md border border-[#e0e7df] bg-white p-2">
      <strong className="block text-base text-[#17251b]">{value}</strong>
      <span className="text-[#68756c]">{label}</span>
    </div>
  );
}
