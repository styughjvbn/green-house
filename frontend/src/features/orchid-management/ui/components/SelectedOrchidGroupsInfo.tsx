"use client";

import { useState, type ReactNode } from "react";
import type { OrchidGroup } from "@/entities/farm/types";
import {
  ClipboardPlus,
  EllipsisVertical,
  Move,
  Trash2,
  Wrench,
  X,
} from "lucide-react";

export default function SelectedOrchidGroupsInfo({
  orchidGroups,
  onBulkCorrection,
  onRemove,
}: {
  orchidGroups: OrchidGroup[];
  onBulkCorrection: () => void;
  onRemove: (orchidGroupId: number) => void;
}) {
  const [moreMenuOpen, setMoreMenuOpen] = useState(false);
  const totalQuantity = orchidGroups.reduce(
    (sum, orchidGroup) => sum + orchidGroup.quantity,
    0,
  );

  return (
    <section
      className="flex h-[70px] items-center gap-3 rounded-md border border-[#b8d8bf] bg-[#f7fbf7] px-2.5 py-2 shadow-sm"
      data-testid="multi-selection-info"
    >
      <div className="flex shrink-0 items-center gap-2">
        <div>
          <p className="text-sm font-bold whitespace-nowrap text-[#17251b]">
            선택한 난 묶음 {orchidGroups.length}개
          </p>
          <span className="text-xs font-semibold text-[#526057]">
            총 {totalQuantity}분
          </span>
        </div>
      </div>

      {orchidGroups.length > 0 ? (
        <div className="flex min-w-0 flex-1 gap-1.5 overflow-x-auto pb-0.5">
          {orchidGroups.map((orchidGroup) => (
            <div
              className="flex shrink-0 items-center gap-2 rounded-md border border-[#d8e5d8] bg-white px-2.5 py-1"
              key={orchidGroup.id}
            >
              <div>
                <p className="text-xs font-bold text-[#26352b]">
                  {orchidGroup.varietyName} · {orchidGroup.quantity}분
                </p>
                <p className="text-[10px] text-[#6a766e]">
                  {orchidGroup.houseNumber}동 {orchidGroup.physicalBedNumber}
                  다이 {orchidGroup.bedZoneName}
                </p>
              </div>
              <button
                aria-label={`${orchidGroup.varietyName} 선택 해제`}
                className="rounded p-0.5 text-[#77857c] hover:bg-[#eef3ed] hover:text-[#26352b]"
                onClick={() => onRemove(orchidGroup.id)}
                type="button"
              >
                <X aria-hidden className="h-3.5 w-3.5" strokeWidth={2} />
              </button>
            </div>
          ))}
        </div>
      ) : (
        <p className="min-w-0 flex-1 truncate text-xs text-[#667169]">
          목록이나 지도에서 난 묶음을 선택하세요.
        </p>
      )}

      <div className="flex shrink-0 items-center gap-1.5">
        <button
          aria-label="작업 추가"
          className="flex h-8 w-8 cursor-not-allowed items-center justify-center rounded-md border border-[#cfd8cc] bg-white text-[#435047] opacity-45"
          disabled
          title="작업 추가"
          type="button"
        >
          <ClipboardPlus aria-hidden className="h-4 w-4" strokeWidth={1.8} />
        </button>
        <div
          className="relative"
          onBlur={(event) => {
            if (!event.currentTarget.contains(event.relatedTarget)) {
              setMoreMenuOpen(false);
            }
          }}
        >
          <button
            aria-label="더보기"
            aria-expanded={moreMenuOpen}
            aria-haspopup="menu"
            className="flex h-8 w-8 cursor-pointer items-center justify-center rounded-md border border-[#cfd8cc] bg-white text-[#435047] hover:bg-[#f5f7f3]"
            onClick={() => setMoreMenuOpen((current) => !current)}
            title="더보기"
            type="button"
          >
            <EllipsisVertical
              aria-hidden
              className="h-4 w-4"
              strokeWidth={1.8}
            />
          </button>
          {moreMenuOpen ? (
            <div
              className="absolute right-0 bottom-[calc(100%+6px)] z-30 w-40 rounded-md border border-[#d7ddd4] bg-white p-1 shadow-lg"
              role="menu"
            >
              <BulkActionMenuItem
                icon={<Move aria-hidden className="h-4 w-4" />}
                label="자리 이동"
              />
              <BulkActionMenuItem
                icon={<Wrench aria-hidden className="h-4 w-4" />}
                label="일괄 보정"
                disabled={orchidGroups.length === 0}
                onClick={() => {
                  setMoreMenuOpen(false);
                  onBulkCorrection();
                }}
              />
              <BulkActionMenuItem
                destructive
                icon={<Trash2 aria-hidden className="h-4 w-4" />}
                label="일괄 삭제"
              />
            </div>
          ) : null}
        </div>
      </div>
    </section>
  );
}

function BulkActionMenuItem({
  destructive = false,
  disabled = true,
  icon,
  label,
  onClick,
}: {
  destructive?: boolean;
  disabled?: boolean;
  icon: ReactNode;
  label: string;
  onClick?: () => void;
}) {
  return (
    <button
      className={`flex w-full items-center gap-2 rounded px-2.5 py-2 text-left text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-45 ${
        destructive ? "text-[#b42318]" : "text-[#344138]"
      } ${disabled ? "" : "cursor-pointer hover:bg-[#f5f7f3]"}`}
      disabled={disabled}
      onClick={onClick}
      role="menuitem"
      type="button"
    >
      {icon}
      {label}
    </button>
  );
}
