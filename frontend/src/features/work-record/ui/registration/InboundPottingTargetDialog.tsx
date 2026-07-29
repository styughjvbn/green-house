"use client";

import { useState } from "react";
import { X } from "lucide-react";
import type { InboundPottingCandidate } from "../../model/types";

export function InboundPottingTargetDialog({
  candidates,
  initialSelectedIds,
  onClose,
  onConfirm,
}: {
  candidates: InboundPottingCandidate[];
  initialSelectedIds: Set<number>;
  onClose: () => void;
  onConfirm: (selectedIds: Set<number>) => void;
}) {
  const [selectedIds, setSelectedIds] = useState(
    () => new Set(initialSelectedIds),
  );
  const [keyword, setKeyword] = useState("");
  const visible = candidates.filter((candidate) =>
    `${candidate.varietyName} ${candidate.tempLocation ?? ""}`
      .toLowerCase()
      .includes(keyword.trim().toLowerCase()),
  );

  return (
    <div
      className="fixed inset-0 z-[1200] flex items-center justify-center bg-black/45 p-4"
      role="presentation"
      onMouseDown={(event) => {
        event.stopPropagation();
        onClose();
      }}
    >
      <section
        className="flex max-h-[calc(100dvh-2rem)] w-full max-w-2xl flex-col overflow-hidden rounded-lg bg-white shadow-2xl"
        role="dialog"
        aria-modal="true"
        aria-label="포트 작업 대상 선택"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="flex items-center justify-between border-b p-4">
          <div>
            <h3 className="font-bold text-[#17251b]">포트 작업 대상 선택</h3>
            <p className="mt-1 text-xs text-[#6a766e]">
              임시 보관 또는 포트 작업 대기 중인 유리병 모종입니다.
            </p>
          </div>
          <button type="button" aria-label="닫기" onClick={onClose}>
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        </header>
        <div className="border-b p-4">
          <input
            autoFocus
            className="w-full rounded-md border border-[#cfd8cc] px-3 py-2 text-sm"
            placeholder="품종 또는 임시 위치 검색"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
          />
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto p-4">
          <div className="overflow-hidden rounded-md border">
            {visible.map((candidate) => (
              <label
                className="flex cursor-pointer items-center gap-3 border-b px-3 py-3 text-sm last:border-b-0 hover:bg-[#f4f8f3]"
                key={candidate.id}
              >
                <input
                  checked={selectedIds.has(candidate.id)}
                  className="h-4 w-4 accent-[#159447]"
                  type="checkbox"
                  onChange={() =>
                    setSelectedIds((current) => {
                      const next = new Set(current);
                      if (next.has(candidate.id)) next.delete(candidate.id);
                      else next.add(candidate.id);
                      return next;
                    })
                  }
                />
                <span className="min-w-0 flex-1">
                  <span className="block font-semibold">
                    {candidate.varietyName}
                  </span>
                  <span className="block text-xs text-[#6a766e]">
                    {candidate.tempLocation ?? "임시 위치 미지정"} · 예상{" "}
                    {candidate.estimatedQuantity ?? "-"}분
                  </span>
                </span>
                <span className="text-xs text-[#5c6a60]">
                  예정 {candidate.pottingDueDate ?? "미지정"}
                </span>
              </label>
            ))}
            {visible.length === 0 ? (
              <p className="p-8 text-center text-sm text-[#6a766e]">
                선택할 수 있는 입고 기록이 없습니다.
              </p>
            ) : null}
          </div>
        </div>
        <footer className="flex items-center justify-between border-t p-4">
          <span className="text-sm font-semibold">
            {selectedIds.size}건 선택
          </span>
          <button
            className="rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white disabled:opacity-40"
            disabled={selectedIds.size === 0}
            type="button"
            onClick={() => onConfirm(new Set(selectedIds))}
          >
            대상 확정
          </button>
        </footer>
      </section>
    </div>
  );
}
