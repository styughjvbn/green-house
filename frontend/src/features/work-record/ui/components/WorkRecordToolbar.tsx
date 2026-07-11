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
      </div>
    </section>
  );
}
