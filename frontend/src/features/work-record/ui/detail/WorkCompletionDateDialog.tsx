"use client";

import { useState } from "react";
import { useRuntimeContext } from "@/shared/runtime/RuntimeContext";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/shared/ui/primitives/dialog";

export function WorkCompletionDateDialog({
  title,
  description,
  onClose,
  onConfirm,
}: {
  title: string;
  description: string;
  onClose: () => void;
  onConfirm: (completedDate: string) => void;
}) {
  const { businessDate } = useRuntimeContext();
  const [completedDate, setCompletedDate] = useState(businessDate);

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent
        className="z-[1401] max-w-sm rounded-lg shadow-2xl"
        overlayClassName="z-[1400] bg-black/45"
        showCloseButton={false}
      >
        <header className="flex items-start justify-between gap-3 border-b p-4">
          <div>
            <DialogTitle className="font-bold text-[#17251b]">
              {title}
            </DialogTitle>
            <DialogDescription className="mt-1 text-sm text-[#6a766e]">
              {description}
            </DialogDescription>
          </div>
          <DialogClose
            className="flex h-8 w-8 items-center justify-center rounded-md border border-[#d9dfda] text-[#435047]"
            aria-label="닫기"
          >
            ×
          </DialogClose>
        </header>
        <div className="p-4">
          <label className="block text-sm font-semibold text-[#435047]">
            완료일
            <input
              className="mt-1 w-full rounded-md border border-[#cfd8cc] bg-white px-3 py-2 font-normal"
              max={businessDate}
              required
              type="date"
              value={completedDate}
              onChange={(event) => setCompletedDate(event.target.value)}
            />
          </label>
        </div>
        <footer className="flex justify-end gap-2 border-t p-4">
          <button
            className="rounded-md border px-4 py-2 text-sm"
            type="button"
            onClick={onClose}
          >
            취소
          </button>
          <button
            className="rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
            disabled={!completedDate}
            type="button"
            onClick={() => {
              onConfirm(completedDate);
              onClose();
            }}
          >
            완료 처리
          </button>
        </footer>
      </DialogContent>
    </Dialog>
  );
}
