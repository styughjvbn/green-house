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
  getWorkOperationVoidEligibility,
  voidWorkOperation,
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
    `work-void-${operation.id}-${createUuid()}`.slice(0, 100),
  );
  const eligibility = useQuery({
    queryKey: ["work-operations", operation.id, "void-eligibility"],
    queryFn: () => getWorkOperationVoidEligibility(operation.id),
  });
  const voidMutation = useMutation({
    mutationFn: () =>
      voidWorkOperation(operation.id, {
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
            작업 무효화
          </DialogTitle>
          <DialogDescription className="mt-1 text-sm text-[#657168]">
            {operation.title}의 구조 변경
            {data?.relatedWorkOperationIds.length
              ? "과 연관된 이동 전 선별 폐기"
              : ""}
            를 반대 방향 Mutation으로 되돌립니다.
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
                    트랜잭션에서 함께 무효화됩니다.
                  </p>
                ) : null}
              </section>

              {data.voidable ? (
                <p className="rounded-md border border-[#b8ddc1] bg-[#eff9f1] p-3 text-sm text-[#176b35]">
                  상쇄되지 않은 후속 변경이 없어 이 작업을 무효화할 수 있습니다.
                </p>
              ) : (
                <section className="rounded-md border border-[#efc4b9] bg-[#fff4ef] p-3">
                  <p className="text-sm font-bold text-[#9b341e]">
                    지금은 무효화할 수 없습니다.
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
                무효화 사유
                <textarea
                  className="mt-1 min-h-24 w-full resize-y rounded-md border border-[#cfd8cc] p-3 font-normal"
                  maxLength={1000}
                  placeholder="잘못 입력한 작업 내용과 무효화 이유를 남겨주세요."
                  required
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                />
              </label>
              <p className="text-xs text-[#69756d]">
                기존 기록은 삭제되지 않습니다. 무효화 Mutation과 사유가 새
                이력으로 남고 revision은 계속 증가합니다.
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
              !data?.voidable || !reason.trim() || voidMutation.isPending
            }
            type="button"
            onClick={() => voidMutation.mutate()}
          >
            {voidMutation.isPending ? "무효화 중…" : "작업 무효화"}
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
