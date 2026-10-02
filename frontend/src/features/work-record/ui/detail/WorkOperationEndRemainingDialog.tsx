"use client";

import type { WorkOperation } from "@/entities/farm/types";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/shared/ui/primitives/dialog";

export function WorkOperationEndRemainingDialog({
  operation,
  loading,
  onClose,
  onConfirm,
}: {
  operation: WorkOperation;
  loading: boolean;
  onClose: () => void;
  onConfirm: () => void;
}) {
  const preserved = operation.progress.completed + operation.progress.skipped;
  const remaining =
    operation.progress.pending +
    operation.progress.inProgress +
    operation.progress.partial +
    operation.progress.failed;

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-md rounded-lg p-0">
        <header className="border-b border-[#e4e9e3] p-5 pr-14">
          <DialogTitle className="text-lg font-bold text-[#17251b]">
            남은 작업 종료
          </DialogTitle>
          <DialogDescription className="mt-1 text-sm text-[#657168]">
            완료된 기록과 적용 효과는 그대로 유지됩니다.
          </DialogDescription>
        </header>
        <div className="space-y-3 p-5 text-sm text-[#435047]">
          <p className="rounded-md border border-[#dfe7df] bg-[#f8faf7] p-3">
            완료·건너뜀 {preserved}건은 유지하고, 남은 {remaining}건을
            종료합니다.
          </p>
          <p>종료한 뒤에는 이 작업을 추가로 실행할 수 없습니다.</p>
        </div>
        <footer className="flex justify-end gap-2 border-t border-[#e4e9e3] p-4">
          <button
            className="rounded-md border border-[#d7ddd4] px-4 py-2 text-sm font-semibold"
            disabled={loading}
            type="button"
            onClick={onClose}
          >
            닫기
          </button>
          <button
            className="rounded-md bg-[#765f5a] px-4 py-2 text-sm font-bold text-white disabled:opacity-45"
            disabled={loading}
            type="button"
            onClick={onConfirm}
          >
            {loading ? "종료 중…" : "남은 작업 종료"}
          </button>
        </footer>
      </DialogContent>
    </Dialog>
  );
}
