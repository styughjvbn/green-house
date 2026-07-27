"use client";

import { X } from "lucide-react";
import {
  PottingExecutionForm,
  type PottingExecutionValues,
} from "@/entities/farm/ui/PottingExecutionForm";
import { transitionWorkOperationTarget } from "../../../api/workRecordApi";
import type { WorkExecutionDialogProps } from "../../../model/operation/workExecution";

export function PottingExecutionDialog({
  houses,
  operation,
  target,
  onClose,
  onSaved,
}: WorkExecutionDialogProps) {
  async function complete(values: PottingExecutionValues) {
    if (target.id == null) {
      throw new Error("포트 작업 대상을 찾을 수 없습니다.");
    }
    const updated = await transitionWorkOperationTarget(
      operation.id,
      target.id,
      "complete",
      values.worker ?? null,
      {
        ...values,
        growthStage: null,
      },
      values.pottingDate,
    );
    onSaved(updated);
    onClose();
  }

  return (
    <div
      className="fixed inset-0 z-[1300] flex items-center justify-center bg-black/45 p-4"
      role="presentation"
      onMouseDown={onClose}
    >
      <section
        className="max-h-[calc(100dvh-2rem)] w-full max-w-3xl overflow-y-auto rounded-lg bg-white p-5 shadow-2xl"
        role="dialog"
        aria-modal="true"
        aria-label="포트 작업 실행 입력"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="flex items-start justify-between gap-3 border-b pb-4">
          <div>
            <h3 className="font-bold text-[#17251b]">포트 작업 실행 입력</h3>
            <p className="mt-1 text-xs text-[#6a766e]">
              {target.varietyName} · 계획 #{operation.id}
            </p>
          </div>
          <button type="button" aria-label="닫기" onClick={onClose}>
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        </header>
        <PottingExecutionForm
          houses={houses}
          initialActualQuantity={Math.max(1, target.quantitySnapshot)}
          initialAgeYear={target.ageYearSnapshot}
          initialPotSize={target.potSizeSnapshot}
          initialWorker={operation.worker}
          subject={target.varietyName}
          onCancel={onClose}
          onSubmit={complete}
        />
      </section>
    </div>
  );
}
