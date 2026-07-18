"use client";

import { useState } from "react";
import { X } from "lucide-react";

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
  const today = localDateValue(new Date());
  const [completedDate, setCompletedDate] = useState(today);

  return (
    <div
      className="fixed inset-0 z-[1400] flex items-center justify-center bg-black/45 p-4"
      role="presentation"
      onMouseDown={onClose}
    >
      <section
        className="w-full max-w-sm rounded-lg bg-white shadow-2xl"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="flex items-start justify-between gap-3 border-b p-4">
          <div>
            <h3 className="font-bold text-[#17251b]">{title}</h3>
            <p className="mt-1 text-sm text-[#6a766e]">{description}</p>
          </div>
          <button type="button" aria-label="닫기" onClick={onClose}>
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        </header>
        <div className="p-4">
          <label className="block text-sm font-semibold text-[#435047]">
            완료일
            <input
              className="mt-1 w-full rounded-md border border-[#cfd8cc] bg-white px-3 py-2 font-normal"
              max={today}
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
      </section>
    </div>
  );
}

export function localDateValue(date: Date) {
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 10);
}
