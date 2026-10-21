"use client";

import { useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/shared/ui/primitives/dialog";
import type { SalesSlipPhotoInput } from "../../api/types";
import type { PhotoRegionDraft } from "../../lib/photoRegions";
import { SalesSlipCropper } from "./SalesSlipCropper";

const buttonClass =
  "inline-flex h-9 items-center justify-center rounded-md border border-[#cfd8cc] bg-white px-3 text-sm font-semibold text-[#29422e] disabled:opacity-50";

export function SalesSlipRegionEditor({
  photo,
  regions,
  selectedId,
  busy,
  error,
  onApply,
  onClose,
}: {
  photo: SalesSlipPhotoInput;
  regions: PhotoRegionDraft[];
  selectedId?: string;
  busy: boolean;
  error: string | null;
  onApply: (regions: PhotoRegionDraft[]) => Promise<void>;
  onClose: () => void;
}) {
  const [drafts, setDrafts] = useState(regions);
  const [selected, setSelected] = useState<string | null>(
    selectedId ?? regions[0]?.id ?? null,
  );
  const [drawing, setDrawing] = useState(!selectedId);
  const current = drafts.find((region) => region.id === selected);
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !busy) onClose();
      }}
    >
      <DialogContent
        showCloseButton={false}
        overlayClassName="z-[1050]"
        className="z-[1051] flex max-h-[calc(100dvh-2rem)] max-w-5xl flex-col overflow-hidden bg-white"
        onInteractOutside={(e) => e.preventDefault()}
        onEscapeKeyDown={(e) => {
          e.stopPropagation();
          if (busy) e.preventDefault();
        }}
      >
        <header className="flex items-start justify-between gap-4 border-b border-[#dbe8dc] p-4">
          <div>
            <DialogTitle className="text-lg font-bold">
              {regions.length ? "전표 영역 수정" : "직접 영역 선택"}
            </DialogTitle>
            <DialogDescription className="mt-1 text-sm text-[#526158]">
              전표 전체가 들어오도록 사각형을 그리세요. 사각형 안쪽은 이동,
              모서리는 크기 조절입니다.
            </DialogDescription>
          </div>
          <button
            type="button"
            className={buttonClass}
            disabled={busy}
            onClick={onClose}
          >
            닫기
          </button>
        </header>
        <div className="min-h-0 space-y-3 overflow-y-auto p-4">
          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              aria-pressed={drawing}
              className={buttonClass}
              disabled={busy}
              onClick={() => setDrawing(true)}
            >
              새 영역 추가
            </button>
            <button
              type="button"
              aria-pressed={!drawing}
              className={buttonClass}
              disabled={busy || !drafts.length}
              onClick={() => setDrawing(false)}
            >
              전표 영역 수정
            </button>
            <button
              type="button"
              className={buttonClass}
              disabled={busy || !current || current.locked}
              onClick={() => {
                setDrafts((items) =>
                  items.filter((item) => item.id !== selected),
                );
                setSelected(null);
              }}
            >
              선택 영역 제거
            </button>
            <span className="text-xs text-[#526158]">
              {drafts.length}개 ·{" "}
              {drawing
                ? "빈 곳을 드래그해 전표 영역 선택"
                : "사각형을 선택해 수정"}
            </span>
          </div>
          <SalesSlipCropper
            photo={photo}
            regions={drafts}
            selectedId={selected}
            drawing={drawing}
            disabled={busy}
            onSelect={(id) => {
              setSelected(id ?? null);
              setDrawing(false);
            }}
            onChange={setDrafts}
            onCreated={(id) => {
              setSelected(id);
              setDrawing(false);
            }}
          />
          {current ? (
            <fieldset
              disabled={busy || current.locked}
              className="grid grid-cols-2 gap-3 sm:grid-cols-4"
            >
              <legend className="mb-2 text-sm font-semibold">
                선택 영역 위치·크기 (%)
              </legend>
              {(
                [
                  ["x", "왼쪽"],
                  ["y", "위쪽"],
                  ["width", "너비"],
                  ["height", "높이"],
                ] as const
              ).map(([key, label]) => (
                <label key={key} className="text-xs text-[#526158]">
                  {label}
                  <input
                    type="number"
                    min={0}
                    max={100}
                    step={0.1}
                    value={Math.round(current.region[key] * 1000) / 10}
                    onChange={(event) => {
                      const value = Number(event.target.value) / 100;
                      if (!Number.isFinite(value)) return;
                      setDrafts((items) =>
                        items.map((item) => {
                          if (item.id !== selected) return item;
                          const r = {
                            ...item.region,
                            [key]: Math.max(
                              key === "width" || key === "height" ? 0.005 : 0,
                              Math.min(1, value),
                            ),
                          };
                          r.x = Math.min(r.x, 1 - r.width);
                          r.y = Math.min(r.y, 1 - r.height);
                          return { ...item, region: r };
                        }),
                      );
                    }}
                    className="mt-1 h-9 w-full rounded border border-[#cfd8cc] px-2 text-sm"
                  />
                </label>
              ))}
            </fieldset>
          ) : null}
          {current?.locked ? (
            <p className="text-xs text-[#526158]">
              저장 완료 또는 저장 결과 확인 중인 전표는 영역을 변경할 수
              없습니다.
            </p>
          ) : null}
          {error ? (
            <p role="alert" className="text-sm text-[#9b341e]">
              {error}
            </p>
          ) : null}
        </div>
        <footer className="flex justify-end gap-2 border-t border-[#dbe8dc] p-4">
          <button
            type="button"
            disabled={busy}
            className={buttonClass}
            onClick={onClose}
          >
            취소
          </button>
          <button
            type="button"
            disabled={
              busy ||
              !drafts.length ||
              drafts.some(
                (d) => d.region.width < 0.005 || d.region.height < 0.005,
              )
            }
            className="h-9 rounded-md bg-[#159447] px-4 text-sm font-semibold text-white disabled:opacity-50"
            onClick={() => void onApply(drafts)}
          >
            {busy
              ? "전표 사진 만드는 중…"
              : regions.length
                ? "수정 적용"
                : "선택한 영역 적용"}
          </button>
        </footer>
      </DialogContent>
    </Dialog>
  );
}
