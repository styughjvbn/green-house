"use client";

import { Download, List, Plus } from "lucide-react";

export function WorkRecordToolbar({ onCreate }: { onCreate: () => void }) {
  return (
    <section className="flex flex-wrap items-center justify-between gap-3">
      <div className="flex gap-2">
        <button
          className="inline-flex h-10 items-center gap-2 rounded-md bg-[#159447] px-4 text-sm font-semibold text-white shadow-sm"
          type="button"
          onClick={onCreate}
        >
          <Plus className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
          작업 이력 등록
        </button>
        <button
          className="inline-flex h-10 items-center gap-2 rounded-md border border-[#dfe5dc] bg-white px-4 text-sm font-semibold text-[#344138] shadow-sm"
          type="button"
        >
          <Download className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
          엑셀 다운로드
        </button>
      </div>

      <div className="flex h-10 overflow-hidden rounded-md border border-[#dfe5dc] bg-white shadow-sm">
        <button
          className="inline-flex w-12 items-center justify-center bg-[#159447] text-white"
          type="button"
        >
          <List className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
        </button>
        <button
          className="inline-flex w-12 items-center justify-center text-[#6a766e]"
          type="button"
        >
          ▦
        </button>
      </div>
    </section>
  );
}
